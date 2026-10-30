package com.hospitua.habitaciones.infrastructure.adapters.in.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.event.DailyReservationListIngestedEvent;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cola diaria de punta a punta con RabbitMQ y PostgreSQL reales (plan-consultar-reservas.md T010 y T015). Cada prueba
 * usa días operativos posteriores a los de la anterior, porque la copia local descarta los días ya superados.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DailyReservationRabbitListenerIntegrationTest {

    private static final AtomicInteger DAY_OFFSET = new AtomicInteger();
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ConfigurableApplicationContext context;

    private final List<LocalDate> ingestedAfterCommit = new CopyOnWriteArrayList<>();
    private ApplicationListener<PayloadApplicationEvent<DailyReservationListIngestedEvent>> afterCommitListener;

    private LocalDate day;

    @BeforeEach
    void setUp() {
        day = LocalDate.of(2200, 1, 1).plusDays(DAY_OFFSET.addAndGet(10));
        amqpAdmin.purgeQueue(RabbitConfig.DAILY_RESERVATIONS_DLQ, false);
        afterCommitListener = TransactionalApplicationListener.forPayload(
                TransactionPhase.AFTER_COMMIT,
                (DailyReservationListIngestedEvent event) -> ingestedAfterCommit.add(event.operationalDate()));
        context.addApplicationListener(afterCommitListener);
    }

    @AfterEach
    void tearDown() {
        context.removeApplicationListener(afterCommitListener);
    }

    @Test
    @DisplayName("T010: la lista del día guarda la copia, aparta con DAILY_LIST, avisa tras el commit y descarta el"
            + " duplicado")
    void dailyList_reservesRooms_andDiscardsDuplicate() {
        Room first = room(RoomStatus.Available);
        Room second = room(RoomStatus.Available);
        String ref = ref();
        String messageId = UUID.randomUUID().toString();

        send(RabbitConfig.ROUTING_KEY_DAILY_LIST, list(messageId, day, reservation(ref, day, first, second)));

        await().atMost(WAIT).untilAsserted(() -> {
            assertReservedBy(first, ref);
            assertReservedBy(second, ref);
        });
        assertThat(reservationRepository.findByReservationRef(ref)).isPresent();
        await().atMost(WAIT).untilAsserted(() -> assertThat(ingestedAfterCommit).contains(day));

        // Mismo messageId con otro contenido; el ADDED posterior marca que ya se procesó
        send(RabbitConfig.ROUTING_KEY_DAILY_LIST, list(messageId, day));
        String marker = ref();
        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, added(2, marker, day, room(RoomStatus.Available)));
        await().atMost(WAIT)
                .until(() -> reservationRepository.findByReservationRef(marker).isPresent());

        assertThat(reservationRepository.findByReservationRef(ref)).isPresent();
        assertReservedBy(first, ref);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM daily_reservation_message_log WHERE message_id = ?",
                        Integer.class,
                        messageId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T010: la lista nueva libera primero la habitación de la reserva que ya no figura y luego la aparta")
    void dailyList_releasesBeforeReserving() {
        Room shared = room(RoomStatus.Available);
        String oldRef = ref();
        String newRef = ref();
        send(
                RabbitConfig.ROUTING_KEY_DAILY_LIST,
                list(UUID.randomUUID().toString(), day, reservation(oldRef, day, shared)));
        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(shared, oldRef));

        LocalDate nextDay = day.plusDays(1);
        send(
                RabbitConfig.ROUTING_KEY_DAILY_LIST,
                list(UUID.randomUUID().toString(), nextDay, reservation(newRef, nextDay, shared)));

        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(shared, newRef));
        assertThat(reservationRepository.findByReservationRef(oldRef)).isEmpty();
    }

    @Test
    @DisplayName("T010: ADDED aparta, UPDATED que cambia la habitación libera la anterior y REMOVED libera la nueva")
    void updates_reserveAndRelease() {
        Room before = room(RoomStatus.Available);
        Room after = room(RoomStatus.Available);
        String ref = ref();

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, added(2, ref, day, before));
        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(before, ref));

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, update(3, "UPDATED", ref, reservation(ref, day, after)));
        await().atMost(WAIT).untilAsserted(() -> {
            assertStatus(before, RoomStatus.Available);
            assertReservedBy(after, ref);
        });

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, removed(4, ref, day));
        await().atMost(WAIT).untilAsserted(() -> assertStatus(after, RoomStatus.Available));
        assertThat(reservationRepository.findByReservationRef(ref)).isEmpty();
    }

    @Test
    @DisplayName("T010: ante una habitación ocupada o desconocida sigue con la siguiente")
    void added_continuesAfterConflictAndUnknownRoom() {
        Room occupied = room(RoomStatus.Occupied);
        Room available = room(RoomStatus.Available);
        String ref = ref();
        Map<String, Object> reservation = reservation(ref, day, occupied, available);
        rooms(reservation).add(roomPayload(UUID.randomUUID(), "X-999"));

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, update(2, "ADDED", ref, reservation));

        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(available, ref));
        assertStatus(occupied, RoomStatus.Occupied);
        assertThat(reservationRepository.findByReservationRef(ref))
                .hasValueSatisfying(saved -> assertThat(saved.rooms()).hasSize(3));
    }

    @Test
    @DisplayName(
            "T010: un REMOVED de una reserva cuya habitación está en limpieza se aplica sin revertir ni ir a la DLQ")
    void removed_withRoomInCleaning_isApplied() {
        Room room = room(RoomStatus.Available);
        String ref = ref();
        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, added(2, ref, day, room));
        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(room, ref));
        jdbcTemplate.update(
                "UPDATE room SET status = 'InCleaning', reserved_by_reservation_ref = NULL WHERE id = ?", room.getId());
        String removedId = UUID.randomUUID().toString();

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, removed(removedId, 3, ref, day));

        await().atMost(WAIT).until(() -> reservationRepository.isMessageLogged(removedId));
        assertThat(reservationRepository.findByReservationRef(ref)).isEmpty();
        assertStatus(room, RoomStatus.InCleaning);
        assertThat(rabbitTemplate.receive(RabbitConfig.DAILY_RESERVATIONS_DLQ, 1_000))
                .isNull();
    }

    @Test
    @DisplayName("T015: un mensaje de un día anterior se registra sin aplicarse; uno con hueco de secuencia se aplica")
    void previousDayIsDiscarded_andSequenceGapIsApplied() {
        Room room = room(RoomStatus.Available);
        String ref = ref();
        send(RabbitConfig.ROUTING_KEY_DAILY_LIST, list(UUID.randomUUID().toString(), day, reservation(ref, day, room)));
        await().atMost(WAIT).untilAsserted(() -> assertReservedBy(room, ref));
        String lateId = UUID.randomUUID().toString();

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, removed(lateId, 9, ref, day.minusDays(1)));
        String gapRef = ref();
        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, added(50, gapRef, day, room(RoomStatus.Available)));

        await().atMost(WAIT)
                .until(() -> reservationRepository.findByReservationRef(gapRef).isPresent());
        assertThat(reservationRepository.isMessageLogged(lateId)).isTrue();
        assertThat(reservationRepository.findByReservationRef(ref)).isPresent();
        assertReservedBy(room, ref);
    }

    @Test
    @DisplayName("T015: un mensaje ilegible o sin messageId o reservationRef llega a la DLQ sin aplicarse")
    void invalidMessages_goToDlq() {
        String ref = ref();
        Map<String, Object> withoutMessageId = list(null, day, reservation(ref, day, room(RoomStatus.Available)));
        Map<String, Object> withoutRef = removed(2, null, day);

        sendRaw(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, "{no es json".getBytes(StandardCharsets.UTF_8));
        send(RabbitConfig.ROUTING_KEY_DAILY_LIST, withoutMessageId);
        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, withoutRef);

        List<Message> deadLetters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message deadLetter = rabbitTemplate.receive(RabbitConfig.DAILY_RESERVATIONS_DLQ, WAIT.toMillis());
            assertThat(deadLetter).as("mensaje %d en la DLQ", i + 1).isNotNull();
            deadLetters.add(deadLetter);
        }
        assertThat(deadLetters).hasSize(3);
        assertThat(reservationRepository.findByReservationRef(ref)).isEmpty();
    }

    @Test
    @DisplayName(
            "T014: un fallo al aplicar se reintenta y, agotados los reintentos, el mensaje va a la DLQ sin aplicarse")
    void failingMessage_isRetriedThenSentToDlq() {
        String ref = ref();
        Map<String, Object> reservation = reservation(ref, day, room(RoomStatus.Available));
        // Más largo que guest_document_type VARCHAR(20): falla al guardar, no al leer el mensaje
        guest(reservation).put("documentType", "X".repeat(30));
        String messageId = UUID.randomUUID().toString();

        send(RabbitConfig.ROUTING_KEY_DAILY_UPDATE, update(messageId, 2, "ADDED", ref, day, reservation));

        Message deadLetter = rabbitTemplate.receive(RabbitConfig.DAILY_RESERVATIONS_DLQ, WAIT.toMillis());
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains(messageId);
        assertThat(reservationRepository.isMessageLogged(messageId)).isFalse();
        assertThat(reservationRepository.findByReservationRef(ref)).isEmpty();
    }

    private void assertReservedBy(Room room, String ref) {
        Room current = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(current.getReservedByReservationRef()).isEqualTo(ref);
    }

    private void assertStatus(Room room, RoomStatus status) {
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(status);
    }

    private Room room(RoomStatus status) {
        Room room = new Room(
                UUID.randomUUID(),
                "Q" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                5,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                status,
                null,
                0);
        roomRepository.save(room);
        return room;
    }

    private void send(String routingKey, Map<String, Object> body) {
        sendRaw(routingKey, jsonMapper.writeValueAsBytes(body));
    }

    private void sendRaw(String routingKey, byte[] body) {
        rabbitTemplate.send(
                RabbitConfig.EVENTS_EXCHANGE,
                routingKey,
                MessageBuilder.withBody(body).setContentType("application/json").build());
    }

    private static String ref() {
        return "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @SafeVarargs
    private static Map<String, Object> list(String messageId, LocalDate day, Map<String, Object>... reservations) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", messageId);
        message.put("sequenceNumber", 1);
        message.put("operationalDate", day.toString());
        message.put("generatedAt", day + "T00:00:02-05:00");
        message.put("totalReservations", reservations.length);
        message.put("reservations", List.of(reservations));
        return message;
    }

    private Map<String, Object> added(long sequence, String ref, LocalDate startDate, Room... rooms) {
        return update(sequence, "ADDED", ref, reservation(ref, startDate, rooms));
    }

    private Map<String, Object> update(long sequence, String type, String ref, Map<String, Object> reservation) {
        return update(UUID.randomUUID().toString(), sequence, type, ref, day, reservation);
    }

    private static Map<String, Object> update(
            String messageId,
            long sequence,
            String type,
            String ref,
            LocalDate operationalDate,
            Map<String, Object> reservation) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", messageId);
        message.put("sequenceNumber", sequence);
        message.put("operationalDate", operationalDate.toString());
        message.put("updateType", type);
        message.put("occurredAt", operationalDate + "T09:15:00-05:00");
        message.put("reservationRef", ref);
        message.put("reservation", reservation);
        return message;
    }

    private static Map<String, Object> removed(long sequence, String ref, LocalDate operationalDate) {
        return removed(UUID.randomUUID().toString(), sequence, ref, operationalDate);
    }

    private static Map<String, Object> removed(String messageId, long sequence, String ref, LocalDate operationalDate) {
        Map<String, Object> message = update(messageId, sequence, "REMOVED", ref, operationalDate, null);
        message.remove("reservation");
        message.put("removalReason", "CANCELLED");
        return message;
    }

    private static Map<String, Object> reservation(String ref, LocalDate startDate, Room... rooms) {
        Map<String, Object> guest = new LinkedHashMap<>();
        guest.put("guestRef", UUID.randomUUID().toString());
        guest.put("firstName", "Ana");
        guest.put("lastName", "Pérez");
        guest.put("documentType", "CC");
        guest.put("documentNumber", "123456");
        guest.put("nationality", "Colombia");
        List<Map<String, Object>> roomPayloads = new ArrayList<>();
        for (Room room : rooms) {
            roomPayloads.add(roomPayload(room.getId(), room.getRoomNumber()));
        }
        Map<String, Object> reservation = new LinkedHashMap<>();
        reservation.put("reservationRef", ref);
        reservation.put("status", "ACTIVE");
        reservation.put("source", "BOOKING");
        reservation.put("startDate", startDate.toString());
        reservation.put("endDate", startDate.plusDays(3).toString());
        reservation.put("guestCount", 2);
        reservation.put("notes", "Llegada tarde");
        reservation.put("updatedAt", "2026-10-08T15:42:10.123456-05:00");
        reservation.put("rooms", roomPayloads);
        reservation.put("guest", guest);
        return reservation;
    }

    private static Map<String, Object> roomPayload(UUID roomId, String roomNumber) {
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("roomId", roomId.toString());
        room.put("roomNumber", roomNumber);
        room.put("categoryRoom", "DOBLE");
        room.put("guestCount", 2);
        return room;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rooms(Map<String, Object> reservation) {
        return (List<Map<String, Object>>) reservation.get("rooms");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> guest(Map<String, Object> reservation) {
        return (Map<String, Object>) reservation.get("guest");
    }
}
