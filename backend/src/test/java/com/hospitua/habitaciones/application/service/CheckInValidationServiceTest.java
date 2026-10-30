package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.CheckInValidationDto;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidCheckInDateException;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInValidationServiceTest {

    // 23:30 del 9-10 en Bogotá: en UTC ya es 10-10, así que "hoy" debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final String REF = "RSV-8D02E5A4";
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private DailyReservationRepositoryPort reservationRepository;

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    private CheckInValidationService service;

    @BeforeEach
    void setUp() {
        service = new CheckInValidationService(reservationRepository, stayRepository, roomRepository, CLOCK);
    }

    @Test
    @DisplayName("T008: valida contra la copia local y el inventario y devuelve la reserva, el aforo y el titular")
    void validate_returnsReservationRoomAndHolder() {
        givenReservation(TODAY);

        CheckInValidationDto result = service.validate(REF, ROOM_ID);

        assertThat(result)
                .isEqualTo(new CheckInValidationDto(
                        REF,
                        ROOM_ID,
                        "204",
                        "DOBLE",
                        2,
                        3,
                        TODAY,
                        TODAY.plusDays(2),
                        2,
                        "BOOKING",
                        new CheckInValidationDto.Holder("Ana", "Pérez", "CC", "123", "Colombia")));
    }

    @Test
    @DisplayName("T026: con un reloj fijo, una reserva del día siguiente en UTC no es de hoy en Colombia")
    void validate_usesBogotaToday() {
        givenReservation(TODAY.plusDays(1));

        assertThatThrownBy(() -> service.validate(REF, ROOM_ID)).isInstanceOf(InvalidCheckInDateException.class);
    }

    private void givenReservation(LocalDate startDate) {
        when(reservationRepository.findByReservationRef(REF))
                .thenReturn(Optional.of(new DailyReservation(
                        REF,
                        "Ana",
                        "Pérez",
                        "CC",
                        "123",
                        "Colombia",
                        "BOOKING",
                        startDate,
                        startDate.plusDays(2),
                        2,
                        OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                        List.of(new DailyReservationRoom(ROOM_ID, "204", "DOBLE", 2)))));
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(new Room(
                        ROOM_ID,
                        "204",
                        2,
                        RoomCategory.DOBLE,
                        3,
                        new BigDecimal("200000"),
                        RoomStatus.Reserved,
                        REF,
                        0)));
        when(stayRepository.existsByReservationRefAndRoomId(REF, ROOM_ID)).thenReturn(false);
    }
}
