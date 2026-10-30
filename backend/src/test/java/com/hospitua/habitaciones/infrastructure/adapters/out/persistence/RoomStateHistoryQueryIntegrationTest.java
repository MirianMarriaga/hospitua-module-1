package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomStateHistoryUseCase;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Historial de estados contra PostgreSQL real (plan-consultar-historial-de-estados.md T002 a T005, T007 y T008). Cada
 * prueba ubica sus periodos en un día lejano propio, porque las demás pruebas de integración comparten la base y sus
 * periodos abiertos empiezan en el presente.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RoomStateHistoryQueryIntegrationTest {

    private static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);

    @Autowired
    private ConsultRoomStateHistoryUseCase history;

    @Autowired
    private TransitionRoomStateUseCase transitionRoomState;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Día de la prueba: los periodos de las habitaciones A y B se cruzan en él. */
    private LocalDate day;

    private Room roomA;
    private Room roomB;
    private String actorId;

    @BeforeEach
    void loadHistory() {
        day = LocalDate.of(1900, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(0, 30_000));
        actorId = "h-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO user_account (username, password_hash, full_name, area, active, created_at, updated_at)"
                        + " VALUES (?, 'x', 'Ana Gómez', 'Gerencia', TRUE, now(), now())",
                actorId);
        roomA = room(RoomCategory.BOUTIQUE);
        roomB = room(RoomCategory.BOUTIQUE);

        // A: Available hasta las 09:15 del día, InCleaning autónoma hasta las 10:05 y Available abierta, con un actor
        // que no está en user_account. B: Available abierta desde las 09:15 del mismo día.
        period(roomA, RoomStatus.Available, at(-4, 8, 0), at(0, 9, 15), actorId, SourceFlow.REGISTER_ROOM);
        period(roomA, RoomStatus.InCleaning, at(0, 9, 15), at(0, 10, 5), null, SourceFlow.START_CLEANING);
        period(roomA, RoomStatus.Available, at(0, 10, 5), null, "desconocido", SourceFlow.CONFIRM_CLEANING_END);
        period(roomB, RoomStatus.Available, at(0, 9, 15), null, actorId, SourceFlow.REGISTER_ROOM);
    }

    /** Cierra los periodos abiertos de A y B para que no se crucen con el día de las pruebas siguientes. */
    @AfterEach
    void closeOpenPeriods() {
        jdbcTemplate.update(
                "UPDATE room_state_history SET end_date_time = ? WHERE room_id IN (?, ?) AND end_date_time IS NULL",
                Timestamp.from(at(1, 0, 0).toInstant()),
                roomA.getId(),
                roomB.getId());
    }

    @Test
    @DisplayName("T003: el historial de una habitación sale en orden cronológico con número, tipo, responsable y flujo")
    void search_byRoomNumber_returnsChronologicalRows() {
        PagedResultDto<RoomStateHistoryRowDto> page =
                history.search(byNumber(roomA.getRoomNumber().toLowerCase()), 1);

        assertThat(page.items())
                .extracting(RoomStateHistoryRowDto::status)
                .containsExactly(RoomStatus.Available, RoomStatus.InCleaning, RoomStatus.Available);
        RoomStateHistoryRowDto first = page.items().getFirst();
        assertThat(first.roomId()).isEqualTo(roomA.getId());
        assertThat(first.roomNumber()).isEqualTo(roomA.getRoomNumber());
        assertThat(first.categoryRoom()).isEqualTo(RoomCategory.BOUTIQUE);
        assertThat(first.startDateTime().toInstant()).isEqualTo(at(-4, 8, 0).toInstant());
        assertThat(first.endDateTime().toInstant()).isEqualTo(at(0, 9, 15).toInstant());
        assertThat(first.actorName()).isEqualTo("Ana Gómez");
        assertThat(first.sourceFlowName()).isEqualTo("Registrar habitación");
        // Autónoma y actor sin cuenta: sin responsable ("Sistema" en la interfaz)
        assertThat(page.items()).extracting(RoomStateHistoryRowDto::actorName).containsExactly("Ana Gómez", null, null);
        // T005: el periodo abierto llega sin fin
        assertThat(page.items().getLast().endDateTime()).isNull();
    }

    @Test
    @DisplayName("T002: con el mismo inicio desempata el número de habitación")
    void search_sameStart_ordersByRoomNumber() {
        String a = roomA.getRoomNumber();
        String b = roomB.getRoomNumber();
        boolean aFirst = a.compareTo(b) < 0;

        PagedResultDto<RoomStateHistoryRowDto> page = history.search(range(day, day, null), 1);

        // A empieza cuatro días antes; a las 09:15 empiezan A y B; a las 10:05, A
        assertThat(page.items())
                .extracting(RoomStateHistoryRowDto::roomNumber)
                .containsExactly(a, aFirst ? a : b, aFirst ? b : a, a);
    }

    @Test
    @DisplayName("T004: el rango incluye los periodos que se cruzan con él, también los abiertos")
    void search_dateRange_includesOverlappingPeriods() {
        assertThat(history.search(range(day, day, roomA.getId()), 1).totalElements())
                .isEqualTo(3);
        // Al día siguiente solo sigue el periodo abierto; el que terminó a las 10:05 queda fuera
        assertThat(history.search(range(day.plusDays(1), day.plusDays(2), roomA.getId()), 1)
                        .items())
                .singleElement()
                .extracting(RoomStateHistoryRowDto::endDateTime)
                .isNull();
        // Antes de que empezara el primer periodo, nada
        assertThat(history.search(range(day.minusDays(10), day.minusDays(5), roomA.getId()), 1)
                        .items())
                .isEmpty();
        // "Hasta" el día anterior: solo el periodo que empezó cuatro días antes
        assertThat(history.search(range(null, day.minusDays(1), roomA.getId()), 1)
                        .items())
                .extracting(RoomStateHistoryRowDto::sourceFlow)
                .containsExactly(SourceFlow.REGISTER_ROOM);
    }

    @Test
    @DisplayName("T004: los filtros por habitación, tipo, estado y rango se combinan")
    void search_combinesFilters() {
        assertThat(history.search(
                                new RoomStateHistoryFilter(
                                        null, roomA.getRoomNumber(), null, RoomStatus.Available, null, null),
                                1)
                        .items())
                .extracting(RoomStateHistoryRowDto::sourceFlow)
                .containsExactly(SourceFlow.REGISTER_ROOM, SourceFlow.CONFIRM_CLEANING_END);
        assertThat(history.search(
                                new RoomStateHistoryFilter(roomB.getId(), null, RoomCategory.BOUTIQUE, null, day, day),
                                1)
                        .items())
                .singleElement()
                .extracting(RoomStateHistoryRowDto::roomId)
                .isEqualTo(roomB.getId());
        assertThat(history.search(new RoomStateHistoryFilter(null, null, RoomCategory.BOUTIQUE, null, day, day), 1)
                        .totalElements())
                .isEqualTo(4);
        assertThat(history.search(new RoomStateHistoryFilter(null, null, RoomCategory.SUITE, null, day, day), 1)
                        .items())
                .isEmpty();
        assertThat(history.search(
                                new RoomStateHistoryFilter(
                                        null, roomA.getRoomNumber(), null, RoomStatus.InCleaning, day, day),
                                1)
                        .items())
                .singleElement()
                .extracting(RoomStateHistoryRowDto::actorName)
                .isNull();
    }

    @Test
    @DisplayName("T007: 20 periodos dan 2 páginas de 15 con los totales correctos")
    void search_paginatesByFifteen() {
        Room room = room(RoomCategory.DOBLE);
        for (int i = 0; i < 20; i++) {
            period(room, RoomStatus.Available, at(10, i, 0), at(10, i, 30), actorId, SourceFlow.MARK_AVAILABLE);
        }

        PagedResultDto<RoomStateHistoryRowDto> first = history.search(byId(room), 1);
        PagedResultDto<RoomStateHistoryRowDto> second = history.search(byId(room), 2);

        assertThat(first.items()).hasSize(15);
        assertThat(first.size()).isEqualTo(15);
        assertThat(second.items()).hasSize(5);
        assertThat(second.totalElements()).isEqualTo(20);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.items().getFirst().startDateTime().toInstant())
                .isEqualTo(at(10, 15, 0).toInstant());
        assertThat(history.search(byId(room), 3).items()).isEmpty();
    }

    @Test
    @DisplayName("T008: la consulta no modifica datos")
    void search_doesNotWrite() {
        Map<String, Object> before = snapshot();

        history.search(RoomStateHistoryFilter.none(), 1);
        history.search(range(day, day, null), 1);
        history.search(byNumber(roomA.getRoomNumber()), 1);

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("T008: con una consulta en curso, una transición de otra habitación termina y la consulta mantiene"
            + " su instantánea")
    void search_doesNotBlockConcurrentTransition() throws Exception {
        Room other = room(RoomCategory.DOBLE);
        transitionRoomState.registerInitialStatus(other.getId(), actorId, SourceFlow.REGISTER_ROOM);
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        readOnly.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        long[] counts = readOnly.execute(status -> {
            long before = history.search(byId(other), 1).totalElements();
            CompletableFuture<OffsetDateTime> transition =
                    CompletableFuture.supplyAsync(() -> transitionRoomState.transition(
                            other.getId(), RoomStatus.InCleaning, actorId, SourceFlow.START_CLEANING, null));
            try {
                transition.get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("La transición quedó bloqueada por la consulta", ex);
            }
            return new long[] {before, history.search(byId(other), 1).totalElements()};
        });

        assertThat(counts[0]).isEqualTo(1);
        // Misma instantánea dentro de la consulta; fuera de ella ya se ve el periodo nuevo
        assertThat(counts[1]).isEqualTo(1);
        assertThat(history.search(byId(other), 1).items())
                .extracting(RoomStateHistoryRowDto::status)
                .containsExactly(RoomStatus.Available, RoomStatus.InCleaning);
    }

    private Room room(RoomCategory category) {
        String number = "H" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
        Room room = new Room(
                UUID.randomUUID(), number, 7, category, 2, new BigDecimal("200000"), RoomStatus.Available, null, 0);
        roomRepository.save(room);
        return room;
    }

    private void period(
            Room room,
            RoomStatus status,
            OffsetDateTime start,
            OffsetDateTime end,
            String actor,
            SourceFlow sourceFlow) {
        jdbcTemplate.update(
                "INSERT INTO room_state_history (id, room_id, status, start_date_time, end_date_time, actor_id,"
                        + " source_flow) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                room.getId(),
                status.name(),
                Timestamp.from(start.toInstant()),
                end == null ? null : Timestamp.from(end.toInstant()),
                actor,
                sourceFlow.name());
    }

    /** Hora local de Colombia {@code days} días después del día de la prueba. */
    private OffsetDateTime at(int days, int hour, int minute) {
        return day.plusDays(days).atTime(hour, minute).atOffset(BOGOTA);
    }

    private static RoomStateHistoryFilter byNumber(String roomNumber) {
        return new RoomStateHistoryFilter(null, roomNumber, null, null, null, null);
    }

    private static RoomStateHistoryFilter byId(Room room) {
        return new RoomStateHistoryFilter(room.getId(), null, null, null, null, null);
    }

    private static RoomStateHistoryFilter range(LocalDate from, LocalDate to, UUID roomId) {
        return new RoomStateHistoryFilter(roomId, null, null, null, from, to);
    }

    private Map<String, Object> snapshot() {
        return jdbcTemplate.queryForMap("SELECT (SELECT COUNT(*) FROM room) AS rooms,"
                + " (SELECT COALESCE(SUM(version), 0) FROM room) AS versions,"
                + " (SELECT COUNT(*) FROM room_state_history) AS periods,"
                + " (SELECT COUNT(*) FROM room_state_history WHERE end_date_time IS NULL) AS open_periods");
    }
}
