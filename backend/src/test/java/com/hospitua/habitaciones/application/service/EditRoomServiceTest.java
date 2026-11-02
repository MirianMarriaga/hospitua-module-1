package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.EditRoomCommand;
import com.hospitua.habitaciones.application.dto.EditRoomResultDto;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ConcurrentRoomUpdateException;
import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.InvalidRoomDataException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAttributeChange;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EditRoomServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String ADMIN = "lmendez";
    private static final int VERSION = 4;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private RoomAuditLogRepositoryPort auditLog;

    private EditRoomService service;

    @BeforeEach
    void setUp() {
        service = new EditRoomService(roomRepository, auditLog);
    }

    @Test
    @DisplayName(
            "T004 y T006: editar la tarifa persiste solo ese campo y audita ROOM_EDITED con el cambio y el Administrador")
    void edit_baseRate_persistsAndAudits() {
        givenRoom(RoomStatus.Available);
        when(roomRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EditRoomResultDto result = service.edit(command("101", "1", "SENCILLA", "2", "195000"));

        RoomAttributeChange change =
                new RoomAttributeChange("baseRate", new BigDecimal("180000.00"), new BigDecimal("195000.00"));
        assertThat(result.changed()).isTrue();
        assertThat(result.changes()).containsExactly(change);
        assertThat(result.room().baseRate()).isEqualByComparingTo("195000");
        assertThat(result.room().status()).isEqualTo(RoomStatus.Available);
        ArgumentCaptor<RoomAuditEntry> audit = ArgumentCaptor.forClass(RoomAuditEntry.class);
        InOrder order = inOrder(roomRepository, auditLog);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(roomRepository).update(any());
        order.verify(auditLog).record(audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo(RoomAuditEventType.ROOM_EDITED);
        assertThat(audit.getValue().actorId()).isEqualTo(ADMIN);
        assertThat(audit.getValue().details())
                .isEqualTo(Map.of(
                        "changes",
                        List.of(Map.of(
                                "field",
                                "baseRate",
                                "previousValue",
                                new BigDecimal("180000.00"),
                                "newValue",
                                new BigDecimal("195000.00")))));
        verify(roomRepository, never()).existsByRoomNumber(any());
    }

    @Test
    @DisplayName("T005: cambiar el número a uno libre conserva el UUID y los demás atributos")
    void edit_roomNumber_keepsIdentity() {
        givenRoom(RoomStatus.Available);
        when(roomRepository.existsByRoomNumber("110")).thenReturn(false);
        when(roomRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EditRoomResultDto result = service.edit(command("110", null, null, null, null));

        assertThat(result.changes()).containsExactly(new RoomAttributeChange("roomNumber", "101", "110"));
        assertThat(result.room().roomId()).isEqualTo(ROOM_ID);
        assertThat(result.room().roomNumber()).isEqualTo("110");
        assertThat(result.room().floor()).isEqualTo(1);
        assertThat(result.room().categoryRoom()).isEqualTo(RoomCategory.SENCILLA);
    }

    @Test
    @DisplayName("T007: sin cambios devuelve changed false sin guardar ni auditar")
    void edit_noChanges() {
        givenRoom(RoomStatus.Available);

        EditRoomResultDto result = service.edit(command("101", "1", "SENCILLA", "2", "180000"));

        assertThat(result.changed()).isFalse();
        assertThat(result.changes()).isEmpty();
        assertThat(result.room().version()).isEqualTo(VERSION);
        verify(roomRepository, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    @DisplayName("T008: conservar el propio número o cambiar solo el piso no consulta la unicidad")
    void edit_ownNumberOrFloor_noDuplicateCheck() {
        givenRoom(RoomStatus.Available);
        when(roomRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.edit(command(" 101 ", "5", null, null, null));

        verify(roomRepository, never()).existsByRoomNumber(any());
    }

    @Test
    @DisplayName(
            "T017: un número que ya tiene otra habitación, aunque esté inactiva, responde ROOM_NUMBER_ALREADY_EXISTS")
    void edit_duplicateNumber() {
        givenRoom(RoomStatus.Available);
        when(roomRepository.existsByRoomNumber("204")).thenReturn(true);

        assertThatThrownBy(() -> service.edit(command("204", null, null, null, null)))
                .isInstanceOfSatisfying(RoomNumberAlreadyExistsException.class, ex -> {
                    assertThat(ex.error()).isEqualTo(DomainError.ROOM_NUMBER_ALREADY_EXISTS);
                    assertThat(ex.getMessage())
                            .isEqualTo("Ya existe una habitación con el número 204 (aunque esté inactiva).");
                });
        verify(roomRepository, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = "Available", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T013 y T015: fuera de Available responde ROOM_INVALID_STATE antes de validar los datos")
    void edit_notAvailable_rejectedBeforeData(RoomStatus status) {
        givenRoom(status);

        assertThatThrownBy(() -> service.edit(command(null, "0", "PREMIUM", null, null)))
                .isInstanceOfSatisfying(InvalidRoomTransitionException.class, ex -> {
                    assertThat(ex.getCurrentStatus()).isEqualTo(status);
                    assertThat(ex.getMessage())
                            .isEqualTo("La habitación no está disponible para edición (estado actual: "
                                    + status.displayName() + ").");
                });
        verify(roomRepository, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    @DisplayName("T016: un dato inválido se rechaza sin guardar ni auditar")
    void edit_invalidData() {
        givenRoom(RoomStatus.Available);

        assertThatThrownBy(() -> service.edit(command(null, null, null, "0", null)))
                .isInstanceOf(InvalidRoomDataException.class)
                .hasMessage("La capacidad máxima debe ser un número entero mayor a 0.");
        verify(roomRepository, never()).update(any());
        verifyNoInteractions(auditLog);
    }

    @Test
    @DisplayName("Una version distinta de la actual responde CONCURRENT_UPDATE antes de mirar el estado")
    void edit_staleVersion() {
        givenRoom(RoomStatus.Occupied);

        assertThatThrownBy(() -> service.edit(
                        new EditRoomCommand(ROOM_ID, VERSION - 1, null, null, null, null, new BigDecimal("1"), ADMIN)))
                .isInstanceOfSatisfying(ConcurrentRoomUpdateException.class, ex -> {
                    assertThat(ex.error()).isEqualTo(DomainError.CONCURRENT_UPDATE);
                    assertThat(ex.getMessage())
                            .isEqualTo(
                                    "La información de la habitación ya fue actualizada por otro usuario. Recarga para"
                                            + " ver los datos actuales.");
                });
        verify(roomRepository, never()).update(any());
    }

    @Test
    @DisplayName("Una habitación inexistente responde ROOM_NOT_FOUND")
    void edit_unknownRoom() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(command("110", null, null, null, null)))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessage("No se encontró ninguna habitación con ese identificador.");
        verifyNoInteractions(auditLog);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.lockById(ROOM_ID))
                .thenReturn(Optional.of(new Room(
                        ROOM_ID,
                        "101",
                        1,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("180000.00"),
                        status,
                        null,
                        VERSION)));
    }

    private static EditRoomCommand command(String number, String floor, String category, String capacity, String rate) {
        return new EditRoomCommand(
                ROOM_ID,
                VERSION,
                number,
                floor == null ? null : new BigDecimal(floor),
                category,
                capacity == null ? null : new BigDecimal(capacity),
                rate == null ? null : new BigDecimal(rate),
                ADMIN);
    }
}
