package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.Module2FlatMessageSerializer;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.OutboxMessageSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un serializador por {@code event_type} del formato plano de Módulo 2 (T012). El de facturación de Módulo 3
 * ({@code CHECK_OUT_BILLING}) es su propio componente.
 */
@Configuration
public class OutboxConfig {

    @Bean
    OutboxMessageSerializer checkInMessageSerializer() {
        return new Module2FlatMessageSerializer("CHECK_IN");
    }

    @Bean
    OutboxMessageSerializer checkOutMessageSerializer() {
        return new Module2FlatMessageSerializer("CHECK_OUT");
    }

    @Bean
    OutboxMessageSerializer maintenanceNoticeMessageSerializer() {
        return new Module2FlatMessageSerializer("MAINTENANCE_NOTICE");
    }
}
