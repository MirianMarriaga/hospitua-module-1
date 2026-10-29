package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.Stay;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StayAndGuestRepositoryAdapterTest {

    @Autowired
    private StayRepositoryPort stayRepositoryPort;

    @Autowired
    private RoomGuestRepositoryPort roomGuestRepositoryPort;

    @Test
    @DisplayName("Guardar y leer Stay (ida y vuelta)")
    void shouldSaveAndFindStay() {
        UUID stayId = UUID.randomUUID();
        // Habitación 101 existe en V2
        UUID roomId = UUID.fromString("a0000000-0000-0000-0000-000000000101");

        Stay stay = new Stay(
                stayId,
                "RES-2026-001",
                roomId,
                "DIRECTA",
                LocalDate.of(2026, 10, 27),
                null,
                LocalDate.of(2026, 10, 27),
                LocalDate.of(2026, 10, 30),
                "Carlos",
                "Gómez",
                "1020304050",
                0);

        Stay saved = stayRepositoryPort.save(stay);
        assertThat(saved.getId()).isEqualTo(stayId);

        Optional<Stay> found = stayRepositoryPort.findById(stayId);
        assertThat(found).isPresent();
        assertThat(found.get().getReservationRef()).isEqualTo("RES-2026-001");
        assertThat(found.get().getRoomId()).isEqualTo(roomId);
        assertThat(found.get().getSource()).isEqualTo("DIRECTA");
        assertThat(found.get().getCheckInDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(found.get().getCheckOutDate()).isNull();
        assertThat(found.get().getTitularFirstName()).isEqualTo("Carlos");
        assertThat(found.get().getTitularLastName()).isEqualTo("Gómez");
        assertThat(found.get().getTitularDocumentNumber()).isEqualTo("1020304050");
    }

    @Test
    @DisplayName("Guardar y leer RoomGuest (ida y vuelta) con campos migratorios nullable")
    void shouldSaveAndFindRoomGuests() {
        UUID stayId = UUID.randomUUID();
        UUID roomId = UUID.fromString("a0000000-0000-0000-0000-000000000102");

        Stay stay = new Stay(
                stayId,
                "RES-2026-002",
                roomId,
                "BOOKING",
                LocalDate.of(2026, 10, 27),
                null,
                LocalDate.of(2026, 10, 27),
                LocalDate.of(2026, 10, 29),
                "John",
                "Doe",
                "P12345678",
                0);
        stayRepositoryPort.save(stay);

        UUID guest1Id = UUID.randomUUID();
        UUID guest2Id = UUID.randomUUID();

        RoomGuest nationalGuest = new RoomGuest(
                guest1Id,
                stayId,
                "María",
                "Pérez",
                "CC",
                "50607080",
                "Colombia",
                LocalDate.of(1995, 5, 12),
                null,
                null,
                true);

        RoomGuest foreignGuest = new RoomGuest(
                guest2Id,
                stayId,
                "John",
                "Doe",
                "PAS",
                "P12345678",
                "Estados Unidos",
                LocalDate.of(1990, 8, 20),
                "Miami, EE.UU.",
                "Bogotá, Colombia",
                false);

        List<RoomGuest> saved = roomGuestRepositoryPort.saveAll(List.of(nationalGuest, foreignGuest));
        assertThat(saved).hasSize(2);

        List<RoomGuest> found = roomGuestRepositoryPort.findByStayId(stayId);
        assertThat(found).hasSize(2);

        RoomGuest foundNational = found.stream()
                .filter(g -> g.getId().equals(guest1Id))
                .findFirst()
                .orElseThrow();
        assertThat(foundNational.getFirstName()).isEqualTo("María");
        assertThat(foundNational.getLastName()).isEqualTo("Pérez");
        assertThat(foundNational.getOriginPlace()).isNull();
        assertThat(foundNational.getDestinationPlace()).isNull();
        assertThat(foundNational.isReservationGuest()).isTrue();

        RoomGuest foundForeign = found.stream()
                .filter(g -> g.getId().equals(guest2Id))
                .findFirst()
                .orElseThrow();
        assertThat(foundForeign.getFirstName()).isEqualTo("John");
        assertThat(foundForeign.getLastName()).isEqualTo("Doe");
        assertThat(foundForeign.getOriginPlace()).isEqualTo("Miami, EE.UU.");
        assertThat(foundForeign.getDestinationPlace()).isEqualTo("Bogotá, Colombia");
        assertThat(foundForeign.isReservationGuest()).isFalse();
    }

    @Test
    @DisplayName(
            "Inmutabilidad de RoomGuest (regla 4): la clase de dominio no expone setters ni el puerto operaciones de modificación")
    void roomGuestMustBeImmutable() {
        // 1. La clase de dominio RoomGuest no debe tener métodos setter
        List<Method> setterMethods = Arrays.stream(RoomGuest.class.getMethods())
                .filter(m -> m.getName().startsWith("set") && Modifier.isPublic(m.getModifiers()))
                .toList();
        assertThat(setterMethods).isEmpty();

        // 2. El puerto RoomGuestRepositoryPort no debe tener métodos de update o delete
        List<Method> portMethods = Arrays.stream(RoomGuestRepositoryPort.class.getMethods())
                .filter(m -> m.getName().contains("update") || m.getName().contains("delete"))
                .toList();
        assertThat(portMethods).isEmpty();
    }
}
