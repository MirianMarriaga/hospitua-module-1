package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.CheckOutBillingEventSerializer;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.Module2FlatMessageSerializer;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.OutboxMessageSerializer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxSerializerRegistryTest {

    @Test
    @DisplayName("Resuelve el serializador por event_type y no encuentra los tipos sin serializador")
    void shouldResolveSerializerByEventType() {
        var checkIn = new Module2FlatMessageSerializer("CHECK_IN");
        var billing = new CheckOutBillingEventSerializer();
        var registry = new OutboxSerializerRegistry(List.of(checkIn, billing));

        assertThat(registry.forType("CHECK_IN")).containsSame(checkIn);
        assertThat(registry.forType("CHECK_OUT_BILLING")).containsSame(billing);
        assertThat(registry.forType("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("Dos serializadores para el mismo event_type impiden crear el registro (falla al arrancar)")
    void duplicatedEventTypeShouldFailFast() {
        List<OutboxMessageSerializer> serializers =
                List.of(new Module2FlatMessageSerializer("CHECK_IN"), new Module2FlatMessageSerializer("CHECK_IN"));

        assertThatThrownBy(() -> new OutboxSerializerRegistry(serializers)).isInstanceOf(IllegalStateException.class);
    }
}
