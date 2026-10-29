package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomStateHistoryRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RoomStateTransitionIntegrationTest {

    @Autowired
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Autowired
    private RoomRepositoryPort roomRepositoryPort;

    @Autowired
    private RoomStateHistoryRepositoryPort historyRepositoryPort;

    @Autowired
    private RoomAuditLogRepositoryPort auditLogRepositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName(
            "Una transición cambia room.status, cierra el periodo anterior y abre el nuevo en una sola transacción")
    void transitionShouldUpdateRoomAndClosePreviousAndOpenNewHistoryPeriod() {
        UUID roomId = UUID.randomUUID();
        Room room = roomRepositoryPort.save(new Room(
                roomId,
                "501",
                5,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("120000.00"),
                RoomStatus.Available,
                null,
                0));

        // Registrar periodo inicial
        OffsetDateTime t0 = transitionRoomStateUseCase.registerInitialStatus(roomId, "admin", SourceFlow.REGISTER_ROOM);
        assertThat(t0).isNotNull();

        Optional<RoomStateHistory> initialOpen = historyRepositoryPort.findOpenByRoomId(roomId);
        assertThat(initialOpen).isPresent();
        assertThat(initialOpen.get().getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(initialOpen.get().getEndDateTime()).isNull();
        assertThat(initialOpen.get().getPreviousStatus()).isNull();

        // Ejecutar transición Available -> InCleaning
        OffsetDateTime t1 = transitionRoomStateUseCase.transition(
                roomId, RoomStatus.InCleaning, "cleaner1", SourceFlow.START_CLEANING, null);
        assertThat(t1).isNotNull();

        // Verificar habitación actualizada
        Room updatedRoom = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(updatedRoom.getStatus()).isEqualTo(RoomStatus.InCleaning);

        // Verificar periodos en BD
        List<Map<String, Object>> periods = jdbcTemplate.queryForList(
                "SELECT status, previous_status, start_date_time, end_date_time, actor_id, source_flow "
                        + "FROM room_state_history WHERE room_id = ? ORDER BY start_date_time ASC",
                roomId);

        assertThat(periods).hasSize(2);

        // Periodo 1: cerrado
        Map<String, Object> p1 = periods.get(0);
        assertThat(p1.get("status")).isEqualTo("Available");
        assertThat(p1.get("end_date_time")).isNotNull();

        // Periodo 2: abierto
        Map<String, Object> p2 = periods.get(1);
        assertThat(p2.get("status")).isEqualTo("InCleaning");
        assertThat(p2.get("previous_status")).isEqualTo("Available");
        assertThat(p2.get("end_date_time")).isNull();
        assertThat(p2.get("actor_id")).isEqualTo("cleaner1");
        assertThat(p2.get("source_flow")).isEqualTo("START_CLEANING");
    }

    @Test
    @DisplayName("Transición inválida no deja cambios ni en la habitación ni en el historial")
    void invalidTransitionLeavesNoChanges() {
        UUID roomId = UUID.randomUUID();
        roomRepositoryPort.save(new Room(
                roomId, "502", 5, RoomCategory.DOBLE, 2, new BigDecimal("180000.00"), RoomStatus.Available, null, 0));
        transitionRoomStateUseCase.registerInitialStatus(roomId, "admin", SourceFlow.REGISTER_ROOM);

        // Intento inválido: Available -> Occupied (eliminada en la arquitectura actual)
        assertThatThrownBy(() -> transitionRoomStateUseCase.transition(
                        roomId, RoomStatus.Occupied, "receptionist1", SourceFlow.CHECK_IN, null))
                .isInstanceOf(InvalidRoomTransitionException.class);

        // Verificar que la habitación sigue en Available
        Room roomAfter = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(roomAfter.getStatus()).isEqualTo(RoomStatus.Available);

        // Verificar que solo existe el periodo inicial abierto
        List<Map<String, Object>> periods = jdbcTemplate.queryForList(
                "SELECT status, end_date_time FROM room_state_history WHERE room_id = ?", roomId);
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).get("status")).isEqualTo("Available");
        assertThat(periods.get(0).get("end_date_time")).isNull();
    }

    @Test
    @DisplayName("Fallo posterior dentro de la misma transacción revierte tanto el estado como el historial")
    void rollbackRevertsBothRoomStatusAndHistory() {
        UUID roomId = UUID.randomUUID();
        roomRepositoryPort.save(new Room(
                roomId, "503", 5, RoomCategory.SUITE, 4, new BigDecimal("300000.00"), RoomStatus.Available, null, 0));
        transitionRoomStateUseCase.registerInitialStatus(roomId, "admin", SourceFlow.REGISTER_ROOM);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.execute(status -> {
                    transitionRoomStateUseCase.transition(
                            roomId, RoomStatus.InCleaning, "cleaner2", SourceFlow.START_CLEANING, null);
                    throw new RuntimeException("Error simulado forzando rollback");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error simulado");

        // Tras rollback, la habitación debe seguir en Available
        Room room = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.Available);

        // Y en el historial solo debe existir 1 periodo con end_date_time nulo
        List<Map<String, Object>> periods = jdbcTemplate.queryForList(
                "SELECT status, end_date_time FROM room_state_history WHERE room_id = ?", roomId);
        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).get("status")).isEqualTo("Available");
        assertThat(periods.get(0).get("end_date_time")).isNull();
    }

    @Test
    @DisplayName("RoomAuditLogRepositoryPort.record guarda eventos con y sin actor, y timestamp generado por Clock")
    void auditLogShouldRecordEventsWithAndWithoutActor() {
        UUID roomId = UUID.randomUUID();
        roomRepositoryPort.save(new Room(
                roomId,
                "504",
                5,
                RoomCategory.SENCILLA,
                1,
                new BigDecimal("120000.00"),
                RoomStatus.Available,
                null,
                0));
        RoomAuditEntry manualEntry = new RoomAuditEntry(
                roomId,
                RoomAuditEventType.ROOM_EDITED,
                "admin1",
                Map.of("field", "baseRate", "oldValue", 100, "newValue", 120));
        auditLogRepositoryPort.record(manualEntry);

        // 2. Evento autónomo sin actor (conflicto de reserva)
        RoomAuditEntry autonomousEntry = new RoomAuditEntry(
                roomId,
                RoomAuditEventType.RESERVATION_STATE_CONFLICT,
                null,
                Map.of("currentStatus", "DisabledForRepairs", "reservationRef", "RES-999"));
        auditLogRepositoryPort.record(autonomousEntry);

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT event_type, actor_id, details, event_date_time FROM room_audit_log WHERE room_id = ? ORDER BY event_date_time ASC",
                roomId);

        assertThat(logs).hasSize(2);

        Map<String, Object> logWithActor = logs.stream()
                .filter(l -> "ROOM_EDITED".equals(l.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(logWithActor.get("actor_id")).isEqualTo("admin1");
        assertThat(logWithActor.get("event_date_time")).isNotNull();
        assertThat(logWithActor.get("details").toString()).contains("baseRate");

        Map<String, Object> logWithoutActor = logs.stream()
                .filter(l -> "RESERVATION_STATE_CONFLICT".equals(l.get("event_type")))
                .findFirst()
                .orElseThrow();
        assertThat(logWithoutActor.get("actor_id")).isNull();
        assertThat(logWithoutActor.get("event_date_time")).isNotNull();
        assertThat(logWithoutActor.get("details").toString()).contains("DisabledForRepairs");
    }
}
