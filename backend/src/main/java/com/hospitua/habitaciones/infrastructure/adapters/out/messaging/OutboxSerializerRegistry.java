package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.OutboxMessageSerializer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Serializadores del Outbox por {@code event_type}. Dos serializadores para el mismo tipo impiden arrancar la
 * aplicación, en lugar de descubrirse al publicar.
 */
@Component
public class OutboxSerializerRegistry {

    private final Map<String, OutboxMessageSerializer> byEventType;

    public OutboxSerializerRegistry(List<OutboxMessageSerializer> serializers) {
        this.byEventType = serializers.stream()
                .collect(Collectors.toUnmodifiableMap(OutboxMessageSerializer::eventType, Function.identity()));
    }

    public Optional<OutboxMessageSerializer> forType(String eventType) {
        return Optional.ofNullable(byEventType.get(eventType));
    }
}
