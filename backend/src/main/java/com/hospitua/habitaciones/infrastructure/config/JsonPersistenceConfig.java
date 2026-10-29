package com.hospitua.habitaciones.infrastructure.config;

import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las columnas JSONB (el {@code payload} del Outbox) se escriben con el mismo {@link JsonMapper} de la aplicación:
 * fechas {@code AAAA-MM-DD} (regla 6) y sin campos nulos, igual que los mensajes que se publican.
 */
@Configuration
public class JsonPersistenceConfig {

    @Bean
    HibernatePropertiesCustomizer jsonFormatMapperCustomizer(JsonMapper jsonMapper) {
        return properties ->
                properties.put(MappingSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(jsonMapper));
    }
}
