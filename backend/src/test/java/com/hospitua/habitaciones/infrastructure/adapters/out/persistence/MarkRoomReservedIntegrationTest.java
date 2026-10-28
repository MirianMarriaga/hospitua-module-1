package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
class MarkRoomReservedIntegrationTest {

    @Autowired
    private MarkRoomAvailableUseCase markRoomAvailableUseCase;

    @Autowired
    private MarkRoomReservedUseCase markRoomReservedUseCase;

    @Autowired
    private RoomRepositoryPort roomRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T006: Transición encadenada InCleaning -> Available -> Reserved en la misma transacción")
    void shouldExecuteChainedTransitionInSameTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "RES-801",
                    8,
                    RoomCategory.SENCILLA,
                    1,
                    BigDecimal.valueOf(120000),
                    RoomStatus.InCleaning,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        tx.executeWithoutResult(status -> {
            roomRepositoryPort.lockById(roomId);

            markRoomAvailableUseCase.markAvailable(
                    new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, null, null));

            MarkRoomReservedResult result = markRoomReservedUseCase.markReserved(
                    new MarkRoomReservedCommand(roomId, "RES-CHAIN-101", ReservationTrigger.CLEANING_END));

            assertThat(result.outcome()).isEqualTo(MarkRoomReservedOutcome.RESERVED);
        });

        Room updated = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(updated.getReservedByReservationRef()).isEqualTo("RES-CHAIN-101");

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT status, previous_status, source_flow, reservation_ref FROM room_state_history WHERE room_id = ? ORDER BY start_date_time ASC",
                roomId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("status")).isEqualTo("Available");
        assertThat(history.get(0).get("previous_status")).isEqualTo("InCleaning");
        assertThat(history.get(0).get("source_flow")).isEqualTo("CONFIRM_CLEANING_END");

        assertThat(history.get(1).get("status")).isEqualTo("Reserved");
        assertThat(history.get(1).get("previous_status")).isEqualTo("Available");
        assertThat(history.get(1).get("source_flow")).isEqualTo("MARK_RESERVED");
        assertThat(history.get(1).get("reservation_ref")).isEqualTo("RES-CHAIN-101");
    }

    @Test
    @DisplayName(
            "T012: Concurrencia entre dos reservas para la misma habitación: una aparta y la otra registra conflicto")
    void shouldHandleConcurrentReservationsWithLocking() throws ExecutionException, InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "RES-802",
                    8,
                    RoomCategory.DOBLE,
                    2,
                    BigDecimal.valueOf(180000),
                    RoomStatus.Available,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        CountDownLatch startLatch = new CountDownLatch(1);

        CompletableFuture<MarkRoomReservedResult> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return tx.execute(status -> {
                roomRepositoryPort.lockById(roomId);
                return markRoomReservedUseCase.markReserved(
                        new MarkRoomReservedCommand(roomId, "RES-CONC-1", ReservationTrigger.DAILY_LIST));
            });
        });

        CompletableFuture<MarkRoomReservedResult> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return tx.execute(status -> {
                roomRepositoryPort.lockById(roomId);
                return markRoomReservedUseCase.markReserved(
                        new MarkRoomReservedCommand(roomId, "RES-CONC-2", ReservationTrigger.DAILY_LIST));
            });
        });

        startLatch.countDown();

        MarkRoomReservedResult res1 = future1.get();
        MarkRoomReservedResult res2 = future2.get();

        List<MarkRoomReservedOutcome> outcomes = List.of(res1.outcome(), res2.outcome());
        assertThat(outcomes)
                .containsExactlyInAnyOrder(MarkRoomReservedOutcome.RESERVED, MarkRoomReservedOutcome.CONFLICT);

        Integer conflictCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_audit_log WHERE room_id = ? AND event_type = 'RESERVATION_STATE_CONFLICT'",
                Integer.class,
                roomId);
        assertThat(conflictCount).isEqualTo(1);
    }

    @Test
    @DisplayName("T013: Rollback de transacción revierte estado y no deja registro en historial")
    void shouldRollbackStateAndHistoryOnTransactionFailure() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "RES-803",
                    8,
                    RoomCategory.SENCILLA,
                    1,
                    BigDecimal.valueOf(120000),
                    RoomStatus.Available,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    roomRepositoryPort.lockById(roomId);
                    markRoomReservedUseCase.markReserved(
                            new MarkRoomReservedCommand(roomId, "RES-FAIL-1", ReservationTrigger.DAILY_LIST));
                    throw new RuntimeException("Falla simulada de persistencia");
                }))
                .isInstanceOf(RuntimeException.class);

        Room roomAfterRollback = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(roomAfterRollback.getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(roomAfterRollback.getReservedByReservationRef()).isNull();

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ? AND status = 'Reserved'",
                Integer.class,
                roomId);
        assertThat(historyCount).isEqualTo(0);
    }
}
