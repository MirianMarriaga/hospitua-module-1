package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RoomBaseRateResponseDto;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomBaseRateQueryServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);

    @Mock
    private RoomRepositoryPort roomRepository;

    private RoomBaseRateQueryService service;

    @BeforeEach
    void setUp() {
        service = new RoomBaseRateQueryService(roomRepository);
    }

    @Test
    @DisplayName("T005: devuelve roomId, la fecha recibida, la tarifa con dos decimales y COP")
    void getBaseRate_mapsRoom() {
        givenRoom(RoomStatus.Available, new BigDecimal("250000"));

        assertThat(service.getBaseRate(ROOM_ID, DATE))
                .isEqualTo(new RoomBaseRateResponseDto(ROOM_ID, DATE, "250000.00", "COP"));
    }

    @ParameterizedTest
    @EnumSource(RoomStatus.class)
    @DisplayName("T007: responde en los 8 estados y solo lee la habitación, sin bloquearla ni guardarla")
    void getBaseRate_anyStatus_readOnly(RoomStatus status) {
        Room room = givenRoom(status, new BigDecimal("180000.50"));

        assertThat(service.getBaseRate(ROOM_ID, DATE).baseRate()).isEqualTo("180000.50");
        assertThat(room.getStatus()).isEqualTo(status);
        verify(roomRepository).findById(ROOM_ID);
        verifyNoMoreInteractions(roomRepository);
    }

    @Test
    @DisplayName("T006: fechas distintas devuelven la misma tarifa")
    void getBaseRate_dateDoesNotChangeRate() {
        givenRoom(RoomStatus.Occupied, new BigDecimal("320000.00"));

        assertThat(service.getBaseRate(ROOM_ID, LocalDate.of(2026, 12, 31)).baseRate())
                .isEqualTo(service.getBaseRate(ROOM_ID, DATE).baseRate());
    }

    @Test
    @DisplayName("T010: una habitación inexistente responde ROOM_NOT_FOUND")
    void getBaseRate_unknownRoom() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBaseRate(ROOM_ID, DATE))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessage("No se encontró la habitación.");
    }

    private Room givenRoom(RoomStatus status, BigDecimal baseRate) {
        Room room = new Room(ROOM_ID, "305", 3, RoomCategory.SUITE, 4, baseRate, status, null, 0);
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        return room;
    }
}
