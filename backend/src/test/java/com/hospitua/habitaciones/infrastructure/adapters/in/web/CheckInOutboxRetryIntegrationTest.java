package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.OutboxProperties;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.OutboxScheduledWorker;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.RabbitMqOutboxPublisherAdapter;
import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Un Check-In no depende de RabbitMQ: si el broker no responde al publicar, la estancia y la habitación
 * {@code Occupied} quedan en firme y el worker del Outbox reintenta el mismo mensaje
 * (plan-registrar-check-in.md T023 y T024; plan base T012). El publicador real falla solo para los {@code messageId}
 * marcados, como si el broker estuviera caído para ellos, y publica de verdad los demás. El worker está apagado en el
 * perfil de pruebas, así que cada prueba lo ejecuta a mano.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CheckInOutboxRetryIntegrationTest {

    private static final String RECEPTIONIST = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private OutboxScheduledWorker worker;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private RabbitMqOutboxPublisherAdapter publisher;

    /** {@code messageId} para los que el broker "no responde". */
    private final Set<UUID> brokerDownFor = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void brokerFailsForMarkedMessages() {
        brokerDownFor.clear();
        doAnswer(invocation -> {
                    if (brokerDownFor.contains(invocation.<UUID>getArgument(2))) {
                        throw new AmqpConnectException(new ConnectException("Connection refused"));
                    }
                    return invocation.callRealMethod();
                })
                .when(publisher)
                .publish(any(), any(), any());
    }

    @Test
    @DisplayName("T023/T024: con el broker caído el Check-In queda en firme y se reintenta con el mismo messageId y"
            + " sequenceNumber, sin frenar a los demás")
    void brokerDown_checkInCommitsAndIsRetried() throws Exception {
        Room failingRoom = checkIn("F");
        Map<String, Object> failing = outboxRow(failingRoom);
        UUID failingId = (UUID) failing.get("message_id");
        brokerDownFor.add(failingId);
        Room otherRoom = checkIn("O");

        worker.processPendingNotifications();

        assertThat(roomRepository.findById(failingRoom.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.Occupied);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM stay WHERE room_id = ? AND check_out_date IS NULL",
                        Integer.class,
                        failingRoom.getId()))
                .isEqualTo(1);
        Map<String, Object> afterFailure = outboxRow(failingRoom);
        assertThat(afterFailure)
                .containsEntry("status", "PENDING")
                .containsEntry("retry_count", 1)
                .containsEntry("message_id", failingId)
                .containsEntry("sequence_number", failing.get("sequence_number"));
        assertThat(afterFailure.get("last_error")).isNotNull();
        assertThat(outboxRow(otherRoom)).containsEntry("status", "PUBLISHED");
        assertThat(receiveCheckInFor(otherRoom)).isNotNull();

        brokerDownFor.clear();
        jdbcTemplate.update(
                "UPDATE outbox_notification SET next_attempt_at = NOW() - INTERVAL '1 minute' WHERE message_id = ?",
                failingId);
        worker.processPendingNotifications();

        assertThat(outboxRow(failingRoom))
                .containsEntry("status", "PUBLISHED")
                .containsEntry("retry_count", 1)
                .containsEntry("message_id", failingId);
        Map<String, Object> received = receiveCheckInFor(failingRoom);
        assertThat(received).isNotNull();
        assertThat(received.get("messageId")).isEqualTo(failingId.toString());
        assertThat(((Number) received.get("sequenceNumber")).longValue())
                .isEqualTo(((Number) failing.get("sequence_number")).longValue());
    }

    @Test
    @DisplayName("T024: agotados los reintentos queda FAILED con la alerta OUTBOX_PUBLISH_FAILED y no llega a la cola")
    void brokerDown_exhaustedRetries_failedWithAlert(CapturedOutput output) throws Exception {
        Room room = checkIn("X");
        UUID messageId = (UUID) outboxRow(room).get("message_id");
        brokerDownFor.add(messageId);
        int maxRetries = outboxProperties.maxRetries();
        jdbcTemplate.update(
                "UPDATE outbox_notification SET retry_count = ?, next_attempt_at = NOW() - INTERVAL '1 minute'"
                        + " WHERE message_id = ?",
                maxRetries - 1,
                messageId);

        worker.processPendingNotifications();

        Map<String, Object> row = outboxRow(room);
        assertThat(row)
                .containsEntry("status", "FAILED")
                .containsEntry("retry_count", maxRetries)
                .containsEntry("message_id", messageId);
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(output).contains("[OUTBOX_PUBLISH_FAILED] Mensaje " + messageId);
        assertThat(receiveCheckInFor(room)).isNull();
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.Occupied);
    }

    /** Registra el Check-In de una reserva de hoy con su habitación propia y devuelve la habitación. */
    private Room checkIn(String prefix) throws Exception {
        LocalDate today = LocalDate.now(clock);
        String ref =
                "RSV-" + prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Room room = roomRepository.save(new Room(
                UUID.randomUUID(),
                prefix + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                RoomStatus.Reserved,
                ref,
                0));
        reservationRepository.save(new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "DIRECTA",
                today,
                today.plusDays(1),
                1,
                OffsetDateTime.now(clock),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 1))));
        mockMvc.perform(post("/api/check-in")
                        .header("X-User", RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + room.getId() + "\","
                                + "\"guests\":[{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"documentType\":\"CC\","
                                + "\"documentNumber\":\"123\",\"nationality\":\"Colombia\","
                                + "\"birthDate\":\"1990-05-20\",\"isReservationGuest\":true}]}"))
                .andExpect(status().isCreated());
        return room;
    }

    private Map<String, Object> outboxRow(Room room) {
        return jdbcTemplate.queryForMap(
                "SELECT message_id, sequence_number, status, retry_count, last_error, next_attempt_at"
                        + " FROM outbox_notification WHERE routing_key = ? AND payload->>'roomId' = ?",
                RabbitConfig.ROUTING_KEY_CHECKIN,
                room.getId().toString());
    }

    /** Lee la cola de Check-In hasta encontrar el mensaje de la habitación; nulo si no llega. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> receiveCheckInFor(Room room) {
        Map<String, Object> message;
        while ((message = (Map<String, Object>) rabbitTemplate.receiveAndConvert(RabbitConfig.CHECKIN_QUEUE, 2000))
                != null) {
            if (room.getId().toString().equals(message.get("roomId"))) {
                return message;
            }
        }
        return null;
    }
}
