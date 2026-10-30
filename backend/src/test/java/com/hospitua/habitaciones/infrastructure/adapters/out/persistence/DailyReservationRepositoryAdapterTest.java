package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.PendingArrival;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Copia local de reservas contra PostgreSQL real (plan-consultar-reservas.md T004, T005 y T011). Cada prueba usa
 * referencias y un día pasado propio, porque comparten la base con la ingesta de la cola diaria.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DailyReservationRepositoryAdapterTest {

    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-08T15:42:10-05:00");

    @Autowired
    private DailyReservationRepositoryPort repository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StayRepositoryPort stayRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate day;

    @BeforeEach
    void pickDay() {
        // Días pasados: la lista del día que registran estas pruebas no debe dejar la copia local en el futuro
        day = LocalDate.of(1900, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(0, 30_000));
    }

    @Test
    @DisplayName("T011: una reserva se guarda y se lee con sus habitaciones, sin columna de noches")
    void save_andFind_roundTrip() {
        DailyReservationRoom r202 = new DailyReservationRoom(UUID.randomUUID(), "202", "SENCILLA", 1);
        DailyReservationRoom r201 = new DailyReservationRoom(UUID.randomUUID(), "201", "DOBLE", 2);
        DailyReservation reservation = reservation(ref(), List.of(r202, r201));

        repository.save(reservation);

        DailyReservation found =
                repository.findByReservationRef(reservation.reservationRef()).orElseThrow();
        assertThat(found)
                .usingRecursiveComparison()
                .ignoringFields("rooms", "updatedAt")
                .isEqualTo(reservation);
        assertThat(found.updatedAt().toInstant()).isEqualTo(UPDATED_AT.toInstant());
        assertThat(found.rooms()).containsExactly(r201, r202);
        assertThat(found.nights()).isEqualTo(3);
        assertThat(repository.findByReservationRef("RSV-NO-EXISTE")).isEmpty();
    }

    @Test
    @DisplayName("T011: guardar de nuevo reemplaza la reserva y sus habitaciones (UPDATED)")
    void save_existing_replacesRooms() {
        String ref = ref();
        UUID previousRoom = UUID.randomUUID();
        repository.save(reservation(ref, List.of(new DailyReservationRoom(previousRoom, "201", "DOBLE", 2))));
        DailyReservationRoom newRoom = new DailyReservationRoom(UUID.randomUUID(), "305", "SUITE", 3);

        repository.save(new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123456",
                "Colombia",
                "BOOKING",
                day,
                day.plusDays(4),
                3,
                UPDATED_AT,
                List.of(newRoom)));

        DailyReservation found = repository.findByReservationRef(ref).orElseThrow();
        assertThat(found.endDate()).isEqualTo(day.plusDays(4));
        assertThat(found.rooms()).containsExactly(newRoom);
        assertThat(countRooms(ref)).isEqualTo(1);
    }

    @Test
    @DisplayName("T011: quitar una reserva borra también sus habitaciones; una inexistente no falla")
    void deleteByReservationRef_removesRooms() {
        String ref = ref();
        repository.save(reservation(ref, List.of(room("201"), room("202"))));

        repository.deleteByReservationRef(ref);
        repository.deleteByReservationRef("RSV-NO-EXISTE");

        assertThat(repository.findByReservationRef(ref)).isEmpty();
        assertThat(countRooms(ref)).isZero();
    }

    @Test
    @DisplayName("T011: la purga vacía la copia local y conserva la bitácora de mensajes")
    void purge_emptiesCopyButKeepsLog() {
        repository.save(reservation(ref(), List.of(room("201"))));
        repository.save(reservation(ref(), List.of(room("301"), room("302"))));
        String messageId = UUID.randomUUID().toString();
        repository.logMessage(log(messageId, 1, DailyReservationMessageType.DAILY_LIST, null));

        repository.purge();

        assertThat(repository.findAll()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_reservation_room", Integer.class))
                .isZero();
        assertThat(repository.isMessageLogged(messageId)).isTrue();
    }

    @Test
    @DisplayName("T011: findAll devuelve cada reserva con sus habitaciones")
    void findAll_groupsRooms() {
        String first = ref();
        String second = ref();
        repository.save(reservation(first, List.of(room("201"), room("202"))));
        repository.save(reservation(second, List.of(room("301"))));

        List<DailyReservation> all = repository.findAll();

        assertThat(all)
                .filteredOn(reservation -> reservation.reservationRef().equals(first))
                .singleElement()
                .satisfies(reservation -> assertThat(reservation.rooms()).hasSize(2));
        assertThat(all)
                .filteredOn(reservation -> reservation.reservationRef().equals(second))
                .singleElement()
                .satisfies(reservation -> assertThat(reservation.rooms()).hasSize(1));
    }

    @Test
    @DisplayName("T011: la bitácora da la idempotencia, el día de la copia y la última secuencia por día y reserva")
    void messageLog_queries() {
        String ref = ref();
        String listId = UUID.randomUUID().toString();
        repository.logMessage(log(listId, 1, DailyReservationMessageType.DAILY_LIST, null));
        repository.logMessage(new DailyReservationMessageLog(
                UUID.randomUUID().toString(),
                1,
                day.minusDays(1),
                DailyReservationMessageType.DAILY_LIST,
                null,
                OffsetDateTime.now()));
        repository.logMessage(log(UUID.randomUUID().toString(), 2, DailyReservationMessageType.UPDATE, ref));
        repository.logMessage(log(UUID.randomUUID().toString(), 5, DailyReservationMessageType.UPDATE, ref));
        repository.logMessage(log(UUID.randomUUID().toString(), 7, DailyReservationMessageType.UPDATE, ref()));

        assertThat(repository.isMessageLogged(listId)).isTrue();
        assertThat(repository.isMessageLogged(UUID.randomUUID().toString())).isFalse();
        assertThat(repository.findLatestDailyListDate())
                .hasValueSatisfying(date -> assertThat(date).isAfterOrEqualTo(day));
        assertThat(repository.findLastSequence(day)).hasValue(7);
        assertThat(repository.findLastSequenceForReservation(day, ref)).hasValue(5);
        assertThat(repository.findLastSequenceForReservation(day.plusDays(1), ref))
                .isEmpty();
        assertThat(repository.findLastSequence(day.plusDays(1))).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT message_type FROM daily_reservation_message_log WHERE message_id = ?",
                        String.class,
                        listId))
                .isEqualTo("reserva.lista-del-dia");
    }

    @Test
    @DisplayName("T007: la llegada pendiente es la reserva del día de la habitación sin estancia para ella")
    void findPendingArrivalReservationRef() {
        Room room = room();
        Room otherRoom = room();
        String ref = ref();
        repository.save(reservation(ref, List.of(roomOf(room), roomOf(otherRoom))));
        // Reserva de otro día para la misma habitación: no es llegada de hoy
        repository.save(new DailyReservation(
                ref(),
                "Luis",
                "Rojas",
                "CC",
                "987",
                "Colombia",
                "DIRECTA",
                day.plusDays(1),
                day.plusDays(2),
                1,
                UPDATED_AT,
                List.of(roomOf(room))));

        assertThat(repository.findPendingArrivalReservationRef(room.getId(), day))
                .hasValue(ref);

        stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "BOOKING",
                day,
                null,
                day,
                day.plusDays(3),
                "Ana",
                "Pérez",
                "123456",
                0));

        assertThat(repository.findPendingArrivalReservationRef(room.getId(), day))
                .isEmpty();
        assertThat(repository.findPendingArrivalReservationRef(otherRoom.getId(), day))
                .hasValue(ref);
        assertThat(repository.findPendingArrivalReservationRef(UUID.randomUUID(), day))
                .isEmpty();
    }

    @Test
    @DisplayName("FR-011: la lista del día cuenta solo si se registró una lista de las 00:00 de ese día")
    void isDailyListReceived_onlyWithThatDaysList() {
        repository.logMessage(log(UUID.randomUUID().toString(), 3, DailyReservationMessageType.UPDATE, ref()));
        assertThat(repository.isDailyListReceived(day)).isFalse();

        repository.logMessage(log(UUID.randomUUID().toString(), 1, DailyReservationMessageType.DAILY_LIST, null));

        assertThat(repository.isDailyListReceived(day)).isTrue();
        assertThat(repository.isDailyListReceived(day.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("T018: llegadas del día con al menos una habitación sin estancia, con todas sus habitaciones")
    void searchPendingArrivals_onlyPendingReservationsOfTheDay() {
        Room checkedIn = room();
        DailyReservationRoom pending = room("402");
        String partial = ref();
        repository.save(reservation(partial, List.of(roomOf(checkedIn), pending)));
        Room alone = room();
        String allCheckedIn = ref();
        repository.save(reservation(allCheckedIn, List.of(roomOf(alone))));
        String tomorrow = ref();
        repository.save(new DailyReservation(
                tomorrow,
                "Ana",
                "Pérez",
                "CC",
                "123456",
                "Colombia",
                "BOOKING",
                day.plusDays(1),
                day.plusDays(3),
                1,
                UPDATED_AT,
                List.of(room("403"))));
        checkIn(partial, checkedIn);
        checkIn(allCheckedIn, alone);

        List<PendingArrival> arrivals = repository.searchPendingArrivals(day, null);

        assertThat(arrivals)
                .extracting(arrival -> arrival.reservation().reservationRef())
                .contains(partial)
                .doesNotContain(allCheckedIn, tomorrow);
        PendingArrival arrival = arrivals.stream()
                .filter(candidate -> candidate.reservation().reservationRef().equals(partial))
                .findFirst()
                .orElseThrow();
        assertThat(arrival.reservation().rooms()).hasSize(2);
        assertThat(arrival.pendingRoomIds()).containsExactly(pending.roomId());
    }

    @Test
    @DisplayName("T018/T030: busca por referencia o documento exactos y por parte del nombre, sin mayúsculas")
    void searchPendingArrivals_byReferenceDocumentOrName() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String ref = ref();
        String document = "DOC" + suffix;
        repository.save(new DailyReservation(
                ref,
                "María José",
                "González" + suffix,
                "CC",
                document,
                "Colombia",
                "DIRECTA",
                day,
                day.plusDays(2),
                2,
                UPDATED_AT,
                List.of(room("501"))));

        assertThat(refsMatching(ref.toLowerCase())).contains(ref);
        assertThat(refsMatching(document.toLowerCase())).contains(ref);
        assertThat(refsMatching("josé")).contains(ref);
        assertThat(refsMatching(("zález" + suffix).toLowerCase())).contains(ref);
        assertThat(refsMatching(("maría josé gonzález" + suffix).toLowerCase())).contains(ref);

        assertThat(refsMatching(ref.substring(0, 6).toLowerCase())).doesNotContain(ref);
        assertThat(refsMatching(document.substring(0, 4).toLowerCase())).doesNotContain(ref);
        assertThat(refsMatching("%")).doesNotContain(ref);
        assertThat(refsMatching("_")).doesNotContain(ref);
    }

    private List<String> refsMatching(String normalizedQuery) {
        return repository.searchPendingArrivals(day, normalizedQuery).stream()
                .map(arrival -> arrival.reservation().reservationRef())
                .toList();
    }

    private void checkIn(String ref, Room room) {
        stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "BOOKING",
                day,
                null,
                day,
                day.plusDays(3),
                "Ana",
                "Pérez",
                "123456",
                0));
    }

    private DailyReservation reservation(String ref, List<DailyReservationRoom> rooms) {
        return new DailyReservation(
                ref, "Ana", "Pérez", "CC", "123456", "Colombia", "BOOKING", day, day.plusDays(3), 3, UPDATED_AT, rooms);
    }

    private DailyReservationMessageLog log(
            String messageId, long sequence, DailyReservationMessageType type, String reservationRef) {
        return new DailyReservationMessageLog(messageId, sequence, day, type, reservationRef, OffsetDateTime.now());
    }

    private Room room() {
        Room room = new Room(
                UUID.randomUUID(),
                "R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                3,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                RoomStatus.Reserved,
                null,
                0);
        roomRepository.save(room);
        return room;
    }

    private static DailyReservationRoom roomOf(Room room) {
        return new DailyReservationRoom(
                room.getId(), room.getRoomNumber(), room.getCategoryRoom().name(), 2);
    }

    private static DailyReservationRoom room(String roomNumber) {
        return new DailyReservationRoom(UUID.randomUUID(), roomNumber, "DOBLE", 2);
    }

    private int countRooms(String ref) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_reservation_room WHERE reservation_ref = ?", Integer.class, ref);
    }

    private static String ref() {
        return "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
