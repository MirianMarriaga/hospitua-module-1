package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.TechnicalBlockViewDto;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.TechnicalBlockNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TechnicalBlockQueryServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-10-08T15:00:00-05:00");
    private static final LocalDate START = LocalDate.of(2026, 10, 20);
    private static final LocalDate END = LocalDate.of(2026, 10, 22);
    private static final String REASON = "Cambio preventivo del sistema de aire acondicionado.";

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    private TechnicalBlockQueryService service;

    @BeforeEach
    void setUp() {
        service = new TechnicalBlockQueryService(roomRepository, reportRepository, staffAccountRepository);
    }

    @Test
    @DisplayName("T017: con un Applied devuelve su justificación, rango, fecha de programación y el nombre completo")
    void applied_withFullName() {
        givenRoom();
        TechnicalBlockReport applied = report(START, END);
        applied.apply();
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of(ROOM_ID, applied));
        givenAccount("lperez", "Luis Pérez");

        assertThat(service.getCurrent(ROOM_ID))
                .isEqualTo(new TechnicalBlockViewDto("106", REASON, "Luis Pérez", START, END, SCHEDULED_AT));
    }

    @Test
    @DisplayName("T017: sin Applied devuelve el Scheduled de inicio más temprano; sin cuenta, el username")
    void earliestScheduled_responsibleWithoutAccount() {
        givenRoom();
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of());
        when(reportRepository.findEarliestScheduledByRoomIds(List.of(ROOM_ID)))
                .thenReturn(Map.of(ROOM_ID, report(START.plusDays(5), END.plusDays(5))));
        when(staffAccountRepository.findByUsername("lperez")).thenReturn(Optional.empty());

        TechnicalBlockViewDto view = service.getCurrent(ROOM_ID);

        assertThat(view.startDate()).isEqualTo(START.plusDays(5));
        assertThat(view.estimatedEndDate()).isEqualTo(END.plusDays(5));
        assertThat(view.responsibleName()).isEqualTo("lperez");
    }

    @Test
    @DisplayName("Sin Applied ni Scheduled responde TECHNICAL_BLOCK_NOT_FOUND")
    void noReport_notFound() {
        givenRoom();
        when(reportRepository.findAppliedByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of());
        when(reportRepository.findEarliestScheduledByRoomIds(List.of(ROOM_ID))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.getCurrent(ROOM_ID))
                .isInstanceOf(TechnicalBlockNotFoundException.class)
                .hasMessage("Esta habitación no tiene un mantenimiento programado ni en curso.");
        verifyNoInteractions(staffAccountRepository);
    }

    @Test
    @DisplayName("Si la habitación no existe responde ROOM_NOT_FOUND sin buscar informes")
    void roomNotFound() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(ROOM_ID)).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(reportRepository, staffAccountRepository);
    }

    private void givenRoom() {
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(new Room(
                        ROOM_ID,
                        "106",
                        1,
                        RoomCategory.DOBLE,
                        2,
                        new BigDecimal("150000"),
                        RoomStatus.TechnicalBlock,
                        null,
                        0)));
    }

    private void givenAccount(String username, String fullName) {
        when(staffAccountRepository.findByUsername(username))
                .thenReturn(Optional.of(StaffAccount.createNew(
                        username, "$2a$10$hash", fullName, StaffArea.MANTENIMIENTO, SCHEDULED_AT, "admin")));
    }

    private static TechnicalBlockReport report(LocalDate start, LocalDate end) {
        return TechnicalBlockReport.schedule(ROOM_ID, "lperez", REASON, start, end, SCHEDULED_AT);
    }
}
