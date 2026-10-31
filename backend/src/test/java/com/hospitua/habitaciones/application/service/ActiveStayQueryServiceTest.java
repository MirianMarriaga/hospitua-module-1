package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.ActiveStayResponseDto;
import com.hospitua.habitaciones.application.dto.ActiveStayResponseDto.GuestDto;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * T004: el Paso 1 del Check-Out sale solo de {@code Stay}, {@code Room} y {@code RoomGuest}; el servicio no tiene
 * ningún puerto hacia Módulo 2, así que no puede consultarlo.
 */
@ExtendWith(MockitoExtension.class)
class ActiveStayQueryServiceTest {

    private static final String REF = "RSV-8D02E5A4";
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID STAY_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 9);

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private RoomGuestRepositoryPort roomGuestRepository;

    private ActiveStayQueryService service;

    @BeforeEach
    void setUp() {
        service = new ActiveStayQueryService(stayRepository, roomRepository, roomGuestRepository);
    }

    @Test
    @DisplayName(
            "T004/T009: devuelve la estancia, la habitación, el titular, la fuente y los huéspedes, titular primero")
    void returnsLocalStayRoomAndGuests() {
        when(stayRepository.findByReservationRefAndRoomId(REF, ROOM_ID)).thenReturn(Optional.of(stay()));
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(new Room(
                        ROOM_ID,
                        "204",
                        2,
                        RoomCategory.DOBLE,
                        3,
                        new BigDecimal("200000"),
                        RoomStatus.Occupied,
                        null,
                        0)));
        when(roomGuestRepository.findByStayId(STAY_ID))
                .thenReturn(List.of(
                        guest("John", "Smith", "PAS", "X99", "Estados Unidos", false),
                        guest("Ana", "Pérez", "CC", "123", "Colombia", true)));

        ActiveStayResponseDto result = service.getActiveStay(REF, ROOM_ID);

        assertThat(result)
                .isEqualTo(new ActiveStayResponseDto(
                        STAY_ID,
                        REF,
                        ROOM_ID,
                        "204",
                        RoomCategory.DOBLE,
                        RoomStatus.Occupied,
                        "Ana",
                        "Pérez",
                        CHECK_IN,
                        CHECK_IN.plusDays(2),
                        "BOOKING",
                        List.of(
                                new GuestDto("Ana", "Pérez", "CC", "123", "Colombia"),
                                new GuestDto("John", "Smith", "PAS", "X99", "Estados Unidos"))));
    }

    @Test
    @DisplayName("Sin estancia activa para el par responde ACTIVE_STAY_NOT_FOUND sin leer los huéspedes")
    void noActiveStay_isNotFound() {
        when(stayRepository.findByReservationRefAndRoomId(REF, ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveStay(REF, ROOM_ID)).isInstanceOf(ActiveStayNotFoundException.class);
        verifyNoInteractions(roomGuestRepository);
    }

    private static Stay stay() {
        return new Stay(
                STAY_ID,
                REF,
                ROOM_ID,
                "BOOKING",
                CHECK_IN,
                null,
                CHECK_IN,
                CHECK_IN.plusDays(2),
                "Ana",
                "Pérez",
                "123",
                0);
    }

    private static RoomGuest guest(
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            boolean reservationGuest) {
        boolean foreigner = !"Colombia".equals(nationality);
        return new RoomGuest(
                UUID.randomUUID(),
                STAY_ID,
                firstName,
                lastName,
                documentType,
                documentNumber,
                nationality,
                LocalDate.of(1990, 5, 12),
                foreigner ? "Miami, Estados Unidos" : null,
                foreigner ? "Cartagena, Colombia" : null,
                reservationGuest);
    }
}
