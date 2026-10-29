package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

/**
 * Campo y mensaje de un error de validación de entrada (400 VALIDATION_ERROR). El campo puede ser nulo.
 */
record ValidationFailure(String field, String message) {

    private static final String DEFAULT_MESSAGE = "Error de validación.";

    static ValidationFailure from(Exception ex) {
        return switch (ex) {
            case MethodArgumentNotValidException e ->
                fromFieldError(e.getBindingResult().getFieldError());
            case ConstraintViolationException e ->
                fromViolation(e.getConstraintViolations().stream().findFirst().orElse(null));
            case MissingServletRequestParameterException e ->
                new ValidationFailure(
                        e.getParameterName(),
                        "El parámetro obligatorio '" + e.getParameterName() + "' no fue proporcionado.");
            case MethodArgumentTypeMismatchException e ->
                new ValidationFailure(e.getName(), "Tipo de dato inválido para el campo: " + e.getName());
            case HttpMessageNotReadableException e ->
                new ValidationFailure(jacksonField(e), "Cuerpo de la petición inválido o mal formado.");
            default -> new ValidationFailure(null, DEFAULT_MESSAGE);
        };
    }

    private static ValidationFailure fromFieldError(FieldError error) {
        if (error == null) {
            return new ValidationFailure(null, DEFAULT_MESSAGE);
        }
        if (error.isBindingFailure()) {
            // Valor que no se pudo convertir al tipo del campo (p. ej. un enum fuera de catálogo en un filtro)
            return new ValidationFailure(error.getField(), "Tipo de dato inválido para el campo: " + error.getField());
        }
        String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : DEFAULT_MESSAGE;
        return new ValidationFailure(error.getField(), message);
    }

    private static ValidationFailure fromViolation(ConstraintViolation<?> violation) {
        if (violation == null) {
            return new ValidationFailure(null, DEFAULT_MESSAGE);
        }
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return new ValidationFailure(field, violation.getMessage());
    }

    /** Último campo de la ruta de Jackson, p. ej. "birthDate" en una fecha inexistente. */
    private static String jacksonField(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                List<JacksonException.Reference> path = jackson.getPath();
                return path.getLast().getPropertyName();
            }
        }
        return null;
    }
}
