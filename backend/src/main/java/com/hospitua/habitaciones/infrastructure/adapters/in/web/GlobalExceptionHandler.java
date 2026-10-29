package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce toda excepción al esquema {@link ApiError} (regla 7): nunca responde HTTP 500.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiErrorFactory errors;

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCategoryStatus.of(ex.error().category()))
                .body(errors.create(ex.error().name(), ex.getMessage(), request, ex.details()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        ValidationFailure failure = ValidationFailure.from(ex);
        Map<String, Object> details = new LinkedHashMap<>();
        if (failure.field() != null) {
            details.put("field", failure.field());
        }
        if (ex instanceof MethodArgumentNotValidException invalid) {
            // Todos los campos con error, para señalar a la vez los pendientes del formulario (registrar-habitacion
            // esc. 3)
            details.put(
                    "fields",
                    invalid.getBindingResult().getFieldErrors().stream()
                            .map(FieldError::getField)
                            .distinct()
                            .toList());
        }
        return ResponseEntity.badRequest().body(errors.create("VALIDATION_ERROR", failure.message(), request, details));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentUpdate(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errors.create(
                        "CONCURRENT_UPDATE",
                        "La habitación acaba de cambiar. Vuelve a intentarlo.",
                        request,
                        Map.of()));
    }

    /** Cualquier otra falla: 422 UNEXPECTED_ERROR, con el identificador de correlación solo en el log y el mensaje. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Error no controlado [correlationId={}]", correlationId, ex);
        String message = "Ocurrió un error inesperado. Referencia: " + correlationId;
        return ResponseEntity.unprocessableContent()
                .body(errors.create("UNEXPECTED_ERROR", message, request, Map.of()));
    }
}
