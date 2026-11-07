package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.infrastructure.adapters.in.web.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajustes del contrato OpenAPI que publica {@code GET /v3/api-docs} (lote L40, plan base T026). Los errores salen de
 * {@code GlobalExceptionHandler} como {@code ResponseEntity<ApiError>}, que springdoc no detecta por sí solo: aquí se
 * agrega el esquema {@code ApiError} y la respuesta {@code 4XX} en cada operación, porque el backend responde todo
 * error con ese formato y siempre con un 4xx (plan base, "Formato Estándar de Error").
 */
@Configuration
public class OpenApiConfig {

    private static final String API_ERROR = "ApiError";

    @Bean
    public OpenApiCustomizer hospituaOpenApiCustomizer() {
        return openApi -> {
            openApi.info(new Info().title("HOSPITUA Módulo 1 — Habitaciones").version("1"));
            ModelConverters.getInstance(true)
                    .readAll(ApiError.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            ApiResponse error = new ApiResponse()
                    .description("Error de validación o de negocio (ApiError)")
                    .content(new Content()
                            .addMediaType(
                                    "application/json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR))));
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperations()
                            .forEach(operation -> operation.getResponses().addApiResponse("4XX", error)));
        };
    }
}
