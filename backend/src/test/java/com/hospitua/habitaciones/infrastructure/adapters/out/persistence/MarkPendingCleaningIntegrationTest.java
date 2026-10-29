package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
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
import java.util.concurrent.atomic.AtomicInteger;
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
class MarkPendingCleaningIntegrationTest {

    @Autowired
    private MarkPendingCleaningUseCase markPendingCleaningUseCase;

    @Autowired
    private RoomRepositoryPort roomRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Occupied -> PendingCleaning persiste estado y periodo en room_state_history")
    void shouldPersistPendingCleaningStateAndHistory() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "CLN-701",
                    7,
                    RoomCategory.SENCILLA,
                    1,
                    BigDecimal.valueOf(100000),
                    RoomStatus.Occupied,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        tx.executeWithoutResult(status -> {
            MarkPendingCleaningResult result = markPendingCleaningUseCase.markPendingCleaning(
                    new MarkPendingCleaningCommand(roomId, SourceFlow.CHECK_OUT, "recepcion1"));
            assertThat(result.previousStatus()).isEqualTo(RoomStatus.Occupied);
        });

        Room updated = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomStatus.PendingCleaning);

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT status, previous_status, source_flow, actor_id FROM room_state_history WHERE room_id = ? ORDER BY start_date_time ASC",
                roomId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("status")).isEqualTo("PendingCleaning");
        assertThat(history.get(0).get("previous_status")).isEqualTo("Occupied");
        assertThat(history.get(0).get("source_flow")).isEqualTo("CHECK_OUT");
        assertThat(history.get(0).get("actor_id")).isEqualTo("recepcion1");
    }

    @Test
    @DisplayName("T005: Rollback en la transacción invocadora revierte la transición y no deja registro en historial")
    void shouldRollbackWhenCallerTransactionFails() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "CLN-702",
                    7,
                    RoomCategory.DOBLE,
                    2,
                    BigDecimal.valueOf(150000),
                    RoomStatus.Occupied,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    markPendingCleaningUseCase.markPendingCleaning(
                            new MarkPendingCleaningCommand(roomId, SourceFlow.CHECK_OUT, "recepcion1"));
                    throw new RuntimeException("Error simulado tras check-out");
                }))
                .isInstanceOf(RuntimeException.class);

        Room roomAfterRollback = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(roomAfterRollback.getStatus()).isEqualTo(RoomStatus.Occupied);

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ? AND status = 'PendingCleaning'",
                Integer.class,
                roomId);
        assertThat(historyCount).isEqualTo(0);
    }

    @Test
    @DisplayName("T006: Dos invocaciones concurrentes desde check-out: solo una prospera y la segunda falla")
    void shouldHandleConcurrentCheckOutsWithOptimisticLockingOrValidation()
            throws ExecutionException, InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "CLN-703",
                    7,
                    RoomCategory.SUITE,
                    3,
                    BigDecimal.valueOf(250000),
                    RoomStatus.Occupied,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable checkoutTask = () -> {
            try {
                startLatch.await();
                tx.executeWithoutResult(status -> {
                    markPendingCleaningUseCase.markPendingCleaning(new MarkPendingCleaningCommand(
                            roomId,
                            SourceFlow.CHECK_OUT,
                            "actor-" + Thread.currentThread().getName()));
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        CompletableFuture<Void> f1 = CompletableFuture.runAsync(checkoutTask);
        CompletableFuture<Void> f2 = CompletableFuture.runAsync(checkoutTask);

        startLatch.countDown();
        CompletableFuture.allOf(f1, f2).join();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        Room finalRoom = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(finalRoom.getStatus()).isEqualTo(RoomStatus.PendingCleaning);

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ? AND status = 'PendingCleaning'",
                Integer.class,
                roomId);
        assertThat(historyCount).isEqualTo(1);
    }
}
