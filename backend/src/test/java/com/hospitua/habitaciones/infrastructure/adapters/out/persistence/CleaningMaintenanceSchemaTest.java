package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invariantes de la migración V3 (regla 9 y modelo de datos del plan base). Cada prueba revierte sus filas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CleaningMaintenanceSchemaTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-28T08:00:00-05:00");
    private static final OffsetDateTime END = START.plusHours(1);
    private static final LocalDate TODAY = LocalDate.parse("2026-10-28");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID roomId;
    private UUID otherRoomId;

    // Habitaciones de la semilla V2: no dependen de lo que confirmen otras pruebas de integración.
    @BeforeEach
    void loadSeededRooms() {
        List<UUID> roomIds = jdbcTemplate.queryForList(
                "SELECT id FROM room WHERE id::text LIKE 'a0000000-0000-0000-0000-%' ORDER BY room_number LIMIT 2",
                UUID.class);
        roomId = roomIds.get(0);
        otherRoomId = roomIds.get(1);
    }

    @Test
    @DisplayName("Una habitación admite una tarea de limpieza cerrada y otra abierta")
    void shouldAcceptClosedAndOpenCleaningTasksOnSameRoom() {
        insertCleaningTask(roomId, "limpieza1", START, END, "Completed");
        insertCleaningTask(roomId, "limpieza2", END, null, null);

        Integer tasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cleaning_task WHERE room_id = ?", Integer.class, roomId);
        assertThat(tasks).isEqualTo(2);
    }

    @Test
    @DisplayName("Un miembro no puede tener dos tareas de limpieza abiertas")
    void shouldRejectSecondOpenCleaningTaskForSameMember() {
        insertCleaningTask(roomId, "limpieza1", START, null, null);

        assertThatThrownBy(() -> insertCleaningTask(otherRoomId, "limpieza1", START, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una habitación no puede tener dos tareas de limpieza abiertas")
    void shouldRejectSecondOpenCleaningTaskForSameRoom() {
        insertCleaningTask(roomId, "limpieza1", START, null, null);

        assertThatThrownBy(() -> insertCleaningTask(roomId, "limpieza2", START, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una tarea de limpieza no puede tener outcome sin fecha de fin")
    void shouldRejectCleaningOutcomeWithoutEnd() {
        assertThatThrownBy(() -> insertCleaningTask(roomId, "limpieza1", START, null, "Completed"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una tarea de limpieza no puede terminar antes de empezar")
    void shouldRejectCleaningEndBeforeStart() {
        assertThatThrownBy(() -> insertCleaningTask(roomId, "limpieza1", END, START, "Released"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un miembro no puede tener dos tareas de reparación abiertas")
    void shouldRejectSecondOpenReparationTaskForSameMember() {
        insertReparationTask(roomId, "mantenimiento1", null, null);

        assertThatThrownBy(() -> insertReparationTask(otherRoomId, "mantenimiento1", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una habitación no puede tener dos tareas de reparación abiertas")
    void shouldRejectSecondOpenReparationTaskForSameRoom() {
        insertReparationTask(roomId, "mantenimiento1", null, null);

        assertThatThrownBy(() -> insertReparationTask(roomId, "mantenimiento2", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una tarea de reparación no puede cerrarse sin outcome")
    void shouldRejectReparationEndWithoutOutcome() {
        assertThatThrownBy(() -> insertReparationTask(roomId, "mantenimiento1", END, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un reporte de daño exige una habitación existente")
    void shouldRejectDamageReportForUnknownRoom() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO damage_report (id, room_id, author_id, damage_description, report_date_time) "
                                + "VALUES (?, ?, 'limpieza1', 'Grifo roto', ?)",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        START))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Una habitación no puede tener dos bloqueos técnicos aplicados")
    void shouldRejectSecondAppliedTechnicalBlockForSameRoom() {
        insertTechnicalBlock(roomId, TODAY, TODAY.plusDays(2), "Applied");
        insertTechnicalBlock(roomId, TODAY.plusDays(5), TODAY.plusDays(6), "Scheduled");

        assertThatThrownBy(() -> insertTechnicalBlock(roomId, TODAY, TODAY.plusDays(1), "Applied"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un bloqueo técnico no puede terminar antes de empezar")
    void shouldRejectTechnicalBlockEndBeforeStart() {
        assertThatThrownBy(() -> insertTechnicalBlock(roomId, TODAY.plusDays(2), TODAY, "Scheduled"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un bloqueo técnico solo admite los estados canónicos")
    void shouldRejectUnknownTechnicalBlockStatus() {
        assertThatThrownBy(() -> insertTechnicalBlock(roomId, TODAY, TODAY, "Cancelled"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertCleaningTask(
            UUID room, String memberId, OffsetDateTime start, OffsetDateTime end, String outcome) {
        jdbcTemplate.update(
                "INSERT INTO cleaning_task (id, room_id, cleaning_staff_member_id, start_date_time, end_date_time, outcome) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                room,
                memberId,
                start,
                end,
                outcome);
    }

    private void insertReparationTask(UUID room, String memberId, OffsetDateTime end, String outcome) {
        jdbcTemplate.update(
                "INSERT INTO reparation_task (id, room_id, maintenance_staff_member_id, start_date_time, end_date_time, "
                        + "outcome) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                room,
                memberId,
                START,
                end,
                outcome);
    }

    private void insertTechnicalBlock(UUID room, LocalDate startDate, LocalDate endDate, String status) {
        jdbcTemplate.update(
                "INSERT INTO technical_block_report (id, room_id, maintenance_staff_member_id, technical_block_reason, "
                        + "technical_block_start_date, estimated_technical_block_end_date, report_date_time, status) "
                        + "VALUES (?, ?, 'mantenimiento1', 'Cambio de tubería', ?, ?, ?, ?)",
                UUID.randomUUID(),
                room,
                startDate,
                endDate,
                START,
                status);
    }
}
