package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
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
class MarkRoomAvailableIntegrationTest {

    @Autowired
    private MarkRoomAvailableUseCase markRoomAvailableUseCase;

    @Autowired
    private RoomRepositoryPort roomRepositoryPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("InCleaning -> Available persiste estado Available y un único periodo en room_state_history")
    void markAvailable_fromInCleaning_persistsStateAndHistory() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "AVA-901",
                    9,
                    RoomCategory.SENCILLA,
                    1,
                    BigDecimal.valueOf(100000),
                    RoomStatus.InCleaning,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        tx.executeWithoutResult(status -> {
            MarkRoomAvailableResult result = markRoomAvailableUseCase.markAvailable(
                    new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, "cleanerMember", null));
            assertThat(result.previousStatus()).isEqualTo(RoomStatus.InCleaning);
        });

        Room updated = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomStatus.Available);

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ? AND status = 'Available' AND source_flow = 'CONFIRM_CLEANING_END'",
                Integer.class,
                roomId);
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Reserved -> Available limpia reserved_by_reservation_ref automáticamente")
    void markAvailable_fromReserved_clearsReservationRef() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "AVA-902",
                    9,
                    RoomCategory.DOBLE,
                    2,
                    BigDecimal.valueOf(150000),
                    RoomStatus.Reserved,
                    "RES-TEST-1",
                    0);
            roomRepositoryPort.save(room);
        });

        tx.executeWithoutResult(status -> {
            markRoomAvailableUseCase.markAvailable(
                    new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, "RES-TEST-1"));
        });

        Room updated = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(updated.getReservedByReservationRef()).isNull();
    }

    @Test
    @DisplayName("Rollback en la transacción invocadora revierte la transición y no deja periodo en room_state_history")
    void rollbackInCallerTransactionRevertsStateAndHistory() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID roomId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            Room room = new Room(
                    roomId,
                    "AVA-903",
                    9,
                    RoomCategory.SUITE,
                    4,
                    BigDecimal.valueOf(300000),
                    RoomStatus.InCleaning,
                    null,
                    0);
            roomRepositoryPort.save(room);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    markRoomAvailableUseCase.markAvailable(
                            new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, "cleaner1", null));
                    throw new RuntimeException("Forzar rollback para prueba");
                }))
                .isInstanceOf(RuntimeException.class);

        Room roomAfterRollback = roomRepositoryPort.findById(roomId).orElseThrow();
        assertThat(roomAfterRollback.getStatus()).isEqualTo(RoomStatus.InCleaning);

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ? AND status = 'Available'",
                Integer.class,
                roomId);
        assertThat(historyCount).isEqualTo(0);
    }
}
