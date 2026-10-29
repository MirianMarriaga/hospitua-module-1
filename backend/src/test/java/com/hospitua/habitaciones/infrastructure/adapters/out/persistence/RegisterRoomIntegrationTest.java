package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.domain.exception.RoomNumberAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Registro de habitaciones contra PostgreSQL real (plan-registrar-habitacion.md T004, T007 a T009, T016). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RegisterRoomIntegrationTest {

    @Autowired
    private RegisterRoomUseCase registerRoomUseCase;

    @MockitoSpyBean
    private TransitionRoomStateUseCase transitionRoomState;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T007 y T016: la habitación queda Available con su periodo inicial REGISTER_ROOM del Administrador")
    void register_persistsRoomWithInitialPeriod() {
        String roomNumber = uniqueNumber();
        OffsetDateTime before = OffsetDateTime.now();

        RoomDto room = registerRoomUseCase.register(command(roomNumber));

        assertThat(room.roomId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, room.roomId()))
                .isEqualTo("Available");

        Map<String, Object> period = jdbcTemplate.queryForMap(
                "SELECT status, previous_status, end_date_time, actor_id, source_flow, start_date_time"
                        + " FROM room_state_history WHERE room_id = ?",
                room.roomId());
        assertThat(period.get("status")).isEqualTo("Available");
        assertThat(period.get("previous_status")).isNull();
        assertThat(period.get("end_date_time")).isNull();
        assertThat(period.get("actor_id")).isEqualTo("lmendez");
        assertThat(period.get("source_flow")).isEqualTo(SourceFlow.REGISTER_ROOM.name());
        OffsetDateTime start = jdbcTemplate.queryForObject(
                "SELECT start_date_time FROM room_state_history WHERE room_id = ?",
                OffsetDateTime.class,
                room.roomId());
        assertThat(start).isBetween(before.minusSeconds(1), OffsetDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("T004: el número de una habitación Inactive de otro piso también se rechaza")
    void register_rejectsNumberOfInactiveRoomOnOtherFloor() {
        RegisterRoomCommand command =
                new RegisterRoomCommand("104", 9, RoomCategory.SUITE, 2, new BigDecimal("300000"), "lmendez");

        assertThatThrownBy(() -> registerRoomUseCase.register(command))
                .isInstanceOf(RoomNumberAlreadyExistsException.class);
        assertThat(countRooms("104")).isEqualTo(1);
    }

    @Test
    @DisplayName("T008: dos registros simultáneos con el mismo número: uno gana y el otro recibe 409")
    void register_concurrentSameNumber_onlyOneSucceeds() throws Exception {
        String roomNumber = uniqueNumber();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<RoomDto>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return registerRoomUseCase.register(command(roomNumber));
                }));
            }
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<RoomDto> attempt : attempts) {
                try {
                    attempt.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(RoomNumberAlreadyExistsException.class);
                    conflicts++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(countRooms(roomNumber)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T009: si falla el periodo inicial, la habitación no queda creada")
    void register_historyFailure_rollsBackRoom() {
        String roomNumber = uniqueNumber();
        doThrow(new IllegalStateException("fallo forzado"))
                .when(transitionRoomState)
                .registerInitialStatus(any(), eq("lmendez"), eq(SourceFlow.REGISTER_ROOM));

        assertThatThrownBy(() -> registerRoomUseCase.register(command(roomNumber)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(countRooms(roomNumber)).isZero();
    }

    private int countRooms(String roomNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE room_number = ?", Integer.class, roomNumber);
    }

    private static String uniqueNumber() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static RegisterRoomCommand command(String roomNumber) {
        return new RegisterRoomCommand(roomNumber, 4, RoomCategory.DOBLE, 2, new BigDecimal("250000"), "lmendez");
    }
}
