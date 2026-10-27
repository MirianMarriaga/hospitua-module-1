package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejador global de excepciones para traducir errores a la estructura uniforme ApiError.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String field = null;
        String message = "Error de validación.";
        if (ex.getBindingResult().getFieldError() != null) {
            var fieldError = ex.getBindingResult().getFieldError();
            field = fieldError.getField();
            if (fieldError.getDefaultMessage() != null) {
                message = fieldError.getDefaultMessage();
            }
        }
        Map<String, Object> details = field != null ? Map.of("field", field) : null;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String field = null;
        String message = "Error de validación.";
        var violationOpt = ex.getConstraintViolations().stream().findFirst();
        if (violationOpt.isPresent()) {
            var violation = violationOpt.get();
            if (violation.getMessage() != null) {
                message = violation.getMessage();
            }
            if (violation.getPropertyPath() != null) {
                for (var node : violation.getPropertyPath()) {
                    field = node.getName();
                }
            }
        }
        Map<String, Object> details = field != null ? Map.of("field", field) : null;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String field = ex.getParameterName();
        String message = "El parámetro obligatorio '" + field + "' no fue proporcionado.";
        Map<String, Object> details = field != null ? Map.of("field", field) : null;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String field = ex.getName();
        String message = "Tipo de dato inválido para el campo: " + field;
        Map<String, Object> details = field != null ? Map.of("field", field) : null;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String field = extractJsonMappingField(ex);
        String message = "Cuerpo de la petición inválido o mal formado.";
        Map<String, Object> details = field != null ? Map.of("field", field) : null;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_ERROR", message, request, details));
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ApiError> handleRoomNotFound(RoomNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("ROOM_NOT_FOUND", "No se encontró la habitación.", request, null));
    }

    @ExceptionHandler(InvalidRoomTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidRoomTransition(
            InvalidRoomTransitionException ex, HttpServletRequest request) {
        Map<String, Object> details = ex.getCurrentStatus() != null
                ? Map.of("currentStatus", ex.getCurrentStatus().name())
                : null;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("ROOM_INVALID_STATE", ex.getMessage(), request, details));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(
                        "CONCURRENT_UPDATE", "La habitación acaba de cambiar. Vuelve a intentarlo.", request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Error interno no controlado [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        String message = "Ha ocurrido un error inesperado. Identificador de correlación: " + correlationId;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("INTERNAL_ERROR", message, request, null));
    }

    /**
     * Extrae el último campo de la ruta de mapeo JSON si la causa proviene de Jackson.
     * Compatible dinámicamente con JsonMappingException (Jackson 2) y JacksonException (Jackson 3).
     */
    private String extractJsonMappingField(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            try {
                var getPathMethod = cause.getClass().getMethod("getPath");
                Object pathObj = getPathMethod.invoke(cause);
                if (pathObj instanceof List<?> list && !list.isEmpty()) {
                    Object lastRef = list.get(list.size() - 1);
                    for (String methodName : List.of("getFieldName", "getPropertyName")) {
                        try {
                            var method = lastRef.getClass().getMethod(methodName);
                            Object name = method.invoke(lastRef);
                            if (name != null) {
                                return name.toString();
                            }
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            cause = cause.getCause();
        }
        return null;
    }

    private ApiError buildError(
            String errorCode, String message, HttpServletRequest request, Map<String, Object> details) {
        return new ApiError(
                errorCode,
                message,
                OffsetDateTime.now(clock),
                request != null ? request.getRequestURI() : "",
                (details != null && !details.isEmpty()) ? details : null);
    }
}
