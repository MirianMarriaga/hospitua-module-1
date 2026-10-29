package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationAndSchemaTest {

    // Todas las pruebas de integración comparten la base y otras confirman habitaciones propias: las aserciones
    // sobre la semilla se limitan a sus ids (prefijo fijo de V2) para no depender del orden de ejecución.
    private static final String SEEDED_ROOM_ID = "'a0000000-0000-0000-0000-%'";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V1 y V2 deben sembrar 14 habitaciones con sus estados canónicos")
    void shouldHaveSeededFourteenRooms() {
        Integer roomCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE id::text LIKE " + SEEDED_ROOM_ID, Integer.class);
        assertThat(roomCount).isEqualTo(14);

        Integer availableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE status = 'Available' AND id::text LIKE " + SEEDED_ROOM_ID,
                Integer.class);
        Integer inactiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room WHERE status = 'Inactive' AND id::text LIKE " + SEEDED_ROOM_ID,
                Integer.class);

        assertThat(availableCount).isEqualTo(12);
        assertThat(inactiveCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Cada habitación sembrada tiene exactamente un periodo inicial abierto en room_state_history")
    void eachSeededRoomMustHaveOpenPeriodInHistory() {
        List<Map<String, Object>> histories = jdbcTemplate.queryForList(
                "SELECT room_id, status, previous_status, end_date_time, actor_id, source_flow FROM room_state_history "
                        + "WHERE room_id::text LIKE " + SEEDED_ROOM_ID);

        assertThat(histories).hasSize(14);

        for (Map<String, Object> row : histories) {
            assertThat(row.get("previous_status")).isNull();
            assertThat(row.get("end_date_time")).isNull();
            assertThat(row.get("actor_id")).isNull();
            assertThat(row.get("source_flow")).isEqualTo("REGISTER_ROOM");
        }
    }

    @Test
    @DisplayName("Cada habitación Inactive tiene una baja vigente en room_decommission")
    void eachInactiveRoomMustHaveActiveDecommission() {
        List<Map<String, Object>> decommissions = jdbcTemplate.queryForList(
                "SELECT room_id, decommissioned_by, reactivated_by, reactivation_date_time FROM room_decommission "
                        + "WHERE room_id::text LIKE " + SEEDED_ROOM_ID);

        assertThat(decommissions).hasSize(2);

        for (Map<String, Object> row : decommissions) {
            assertThat(row.get("decommissioned_by")).isNull();
            assertThat(row.get("reactivated_by")).isNull();
            assertThat(row.get("reactivation_date_time")).isNull();
        }
    }

    @Test
    @DisplayName("Restricción room_number único debe impedir duplicados")
    void shouldEnforceUniqueRoomNumberConstraint() {
        UUID newId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO room (id, room_number, floor, category_room, max_capacity, base_rate, status, version) "
                                + "VALUES (?, '101', 1, 'SENCILLA', 2, 100000.00, 'Available', 0)",
                        newId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Restricción floor > 0 debe impedir pisos menores o iguales a cero")
    void shouldEnforceFloorGreaterThanZeroConstraint() {
        UUID newId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO room (id, room_number, floor, category_room, max_capacity, base_rate, status, version) "
                                + "VALUES (?, '999', 0, 'SENCILLA', 2, 100000.00, 'Available', 0)",
                        newId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Restricción base_rate > 0 debe impedir tarifas iguales a cero")
    void shouldEnforceBaseRateGreaterThanZeroConstraint() {
        UUID newId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO room (id, room_number, floor, category_room, max_capacity, base_rate, status, version) "
                                + "VALUES (?, '998', 1, 'SENCILLA', 2, 0.00, 'Available', 0)",
                        newId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Secuencias de Outbox deben existir y generar identificadores")
    void outboxSequencesMustExist() {
        Long seq1 = jdbcTemplate.queryForObject("SELECT nextval('outbox_seq_habitacion_checkin')", Long.class);
        Long seq2 = jdbcTemplate.queryForObject("SELECT nextval('outbox_seq_habitacion_checkout')", Long.class);
        Long seq3 = jdbcTemplate.queryForObject("SELECT nextval('outbox_seq_facturacion_checkout')", Long.class);
        Long seq4 = jdbcTemplate.queryForObject("SELECT nextval('outbox_seq_habitacion_mantenimiento')", Long.class);

        assertThat(seq1).isNotNull().isPositive();
        assertThat(seq2).isNotNull().isPositive();
        assertThat(seq3).isNotNull().isPositive();
        assertThat(seq4).isNotNull().isPositive();
    }
}
