package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomDataException;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterRoomServiceTest {

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TransitionRoomStateUseCase transitionRoomState;

    private RegisterRoomService service;

    @BeforeEach
    void setUp() {
        service = new RegisterRoomService(roomRepository, transitionRoomState);
    }

    @Test
    @DisplayName("T003: el registro válido crea la habitación Available y abre el periodo inicial con REGISTER_ROOM")
    void register_createsRoomAndOpensInitialPeriod() {
        when(roomRepository.existsByRoomNumber("407")).thenReturn(false);
        when(roomRepository.insert(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomDto result = service.register(command("407", 4));

        ArgumentCaptor<Room> inserted = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(result.roomId()).isEqualTo(inserted.getValue().getId());
        assertThat(result.status()).isEqualTo(RoomStatus.Available);
        assertThat(result.roomNumber()).isEqualTo("407");
        assertThat(result.categoryRoom()).isEqualTo(RoomCategory.DOBLE);
        assertThat(result.version()).isZero();
        // T007: el Administrador que registra queda como actor del periodo inicial
        verify(transitionRoomState).registerInitialStatus(result.roomId(), "lmendez", SourceFlow.REGISTER_ROOM);
    }

    @Test
    @DisplayName("T004: un número existente se rechaza con ROOM_NUMBER_ALREADY_EXISTS sin crear nada")
    void register_rejectsDuplicateNumber() {
        when(roomRepository.existsByRoomNumber("204")).thenReturn(true);

        assertThatThrownBy(() -> service.register(command(" 204 ", 2)))
                .isInstanceOf(RoomNumberAlreadyExistsException.class)
                .hasMessage("Ya existe una habitación con el número 204.");

        verify(roomRepository, never()).insert(any());
        verifyNoInteractions(transitionRoomState);
    }

    @Test
    @DisplayName("T006: un dato inválido se rechaza antes de consultar el inventario")
    void register_rejectsInvalidData() {
        assertThatThrownBy(() -> service.register(command("407", 0))).isInstanceOf(InvalidRoomDataException.class);

        verifyNoInteractions(roomRepository, transitionRoomState);
    }

    @Test
    @DisplayName("Si el periodo inicial falla, la excepción sale del servicio para revertir la transacción")
    void register_propagatesHistoryFailure() {
        when(roomRepository.existsByRoomNumber("407")).thenReturn(false);
        when(roomRepository.insert(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transitionRoomState.registerInitialStatus(any(), eq("lmendez"), eq(SourceFlow.REGISTER_ROOM)))
                .thenThrow(new IllegalStateException("fallo forzado"));

        assertThatThrownBy(() -> service.register(command("407", 4))).isInstanceOf(IllegalStateException.class);
    }

    private static RegisterRoomCommand command(String roomNumber, int floor) {
        return new RegisterRoomCommand(roomNumber, floor, RoomCategory.DOBLE, 2, new BigDecimal("250000"), "lmendez");
    }
}
