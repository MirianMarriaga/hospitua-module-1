package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.CleaningPanelDto;
import com.hospitua.habitaciones.application.dto.CleaningPanelDto.CleaningRoomDto;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CleaningPanelQueryServiceTest {

    private static final String MEMBER = "jramirez";
    private static final Set<RoomStatus> PANEL_STATUSES = Set.of(RoomStatus.PendingCleaning, RoomStatus.Available);
    private static final OffsetDateTime LAST_CLEANING = OffsetDateTime.parse("2026-10-07T16:40:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    private CleaningPanelQueryService service;

    @BeforeEach
    void setUp() {
        service = new CleaningPanelQueryService(roomRepository, cleaningTaskRepository);
    }

    @Test
    @DisplayName(
            "T003: consulta solo PendingCleaning y Available; primero las pendientes, cada grupo en orden del puerto")
    void getPanel_pendingFirstKeepingPortOrder() {
        Room available101 = room("101", RoomStatus.Available);
        Room pending102 = room("102", RoomStatus.PendingCleaning);
        Room available103 = room("103", RoomStatus.Available);
        Room pending104 = room("104", RoomStatus.PendingCleaning);
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());
        when(roomRepository.findByStatusIn(PANEL_STATUSES))
                .thenReturn(List.of(available101, pending102, available103, pending104));
        when(cleaningTaskRepository.findLastCleaningEndByRoomIds(anyCollection(), any()))
                .thenReturn(Map.of());

        CleaningPanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.activeTask()).isNull();
        assertThat(panel.rooms()).extracting(CleaningRoomDto::roomNumber).containsExactly("102", "104", "101", "103");
        assertThat(panel.rooms())
                .extracting(CleaningRoomDto::status)
                .containsExactly(
                        RoomStatus.PendingCleaning,
                        RoomStatus.PendingCleaning,
                        RoomStatus.Available,
                        RoomStatus.Available);
    }

    @Test
    @DisplayName("T004: la última limpieza sale de una sola consulta por lotes sin Released; nula sin registro")
    void getPanel_lastCleaningFromOneBatchQuery() {
        Room cleaned = room("101", RoomStatus.PendingCleaning);
        Room neverCleaned = room("102", RoomStatus.Available);
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());
        when(roomRepository.findByStatusIn(PANEL_STATUSES)).thenReturn(List.of(cleaned, neverCleaned));
        when(cleaningTaskRepository.findLastCleaningEndByRoomIds(
                        List.of(cleaned.getId(), neverCleaned.getId()), CleaningTaskOutcome.countsAsCleaning()))
                .thenReturn(Map.of(cleaned.getId(), LAST_CLEANING));

        CleaningPanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.rooms())
                .extracting(CleaningRoomDto::lastCleaningDateTime)
                .containsExactly(LAST_CLEANING, null);
        assertThat(CleaningTaskOutcome.countsAsCleaning()).doesNotContain(CleaningTaskOutcome.Released);
        verify(cleaningTaskRepository).findOpenByMember(MEMBER);
        verify(cleaningTaskRepository).findLastCleaningEndByRoomIds(anyCollection(), any());
        verifyNoMoreInteractions(cleaningTaskRepository);
    }

    @Test
    @DisplayName("T005: con tarea abierta devuelve activeTask con la fila de su habitación y rooms vacío")
    void getPanel_activeTask() {
        Room room = room("205", RoomStatus.InCleaning);
        CleaningTask task = CleaningTask.start(room.getId(), MEMBER, OffsetDateTime.parse("2026-10-08T09:15:00-05:00"));
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.of(task));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(cleaningTaskRepository.findLastCleaningEndByRoomIds(
                        List.of(room.getId()), CleaningTaskOutcome.countsAsCleaning()))
                .thenReturn(Map.of(room.getId(), LAST_CLEANING));

        CleaningPanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.rooms()).isEmpty();
        assertThat(panel.activeTask().taskId()).isEqualTo(task.getId());
        assertThat(panel.activeTask().startDateTime()).isEqualTo(task.getStartDateTime());
        assertThat(panel.activeTask().room())
                .isEqualTo(new CleaningRoomDto(
                        room.getId(), "205", RoomCategory.DOBLE, RoomStatus.InCleaning, LAST_CLEANING));
        verify(roomRepository, never()).findByStatusIn(any());
    }

    @Test
    @DisplayName("Sin habitaciones que atender, rooms llega vacío")
    void getPanel_empty() {
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());
        when(roomRepository.findByStatusIn(PANEL_STATUSES)).thenReturn(List.of());
        when(cleaningTaskRepository.findLastCleaningEndByRoomIds(anyCollection(), any()))
                .thenReturn(Map.of());

        CleaningPanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.activeTask()).isNull();
        assertThat(panel.rooms()).isEmpty();
    }

    private static Room room(String roomNumber, RoomStatus status) {
        return new Room(
                UUID.randomUUID(), roomNumber, 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0);
    }
}
