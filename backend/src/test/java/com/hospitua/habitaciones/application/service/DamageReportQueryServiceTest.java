package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.DamageReportViewDto;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.DamageReportNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DamageReportQueryServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final OffsetDateTime REPORTED_AT = OffsetDateTime.parse("2026-10-08T11:20:00-05:00");
    private static final DamageReport REPORT =
            DamageReport.report(ROOM_ID, "mtorres", "Ventana con el vidrio fisurado.", REPORTED_AT);

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private DamageReportRepositoryPort damageReportRepository;

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    private DamageReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new DamageReportQueryService(roomRepository, damageReportRepository, staffAccountRepository);
    }

    @Test
    @DisplayName("T010: devuelve el último reporte con la descripción, la fecha y el nombre completo del autor")
    void getLatest_withAuthorFullName() {
        givenRoom(RoomStatus.DisabledForRepairs);
        when(damageReportRepository.findLatestByRoom(ROOM_ID)).thenReturn(Optional.of(REPORT));
        when(staffAccountRepository.findByUsername("mtorres"))
                .thenReturn(Optional.of(StaffAccount.createNew(
                        "mtorres", "$2a$10$hash", "Mario Torres", StaffArea.MANTENIMIENTO, REPORTED_AT, "admin")));

        DamageReportViewDto view = service.getLatest(ROOM_ID);

        assertThat(view)
                .isEqualTo(
                        new DamageReportViewDto("101", "Ventana con el vidrio fisurado.", "Mario Torres", REPORTED_AT));
    }

    @Test
    @DisplayName("T010: si la cuenta del autor no existe, authorName es su username")
    void getLatest_authorWithoutAccount() {
        givenRoom(RoomStatus.DisabledForRepairs);
        when(damageReportRepository.findLatestByRoom(ROOM_ID)).thenReturn(Optional.of(REPORT));
        when(staffAccountRepository.findByUsername("mtorres")).thenReturn(Optional.empty());

        assertThat(service.getLatest(ROOM_ID).authorName()).isEqualTo("mtorres");
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"DisabledForRepairs"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T010: fuera de DisabledForRepairs responde DAMAGE_REPORT_NOT_FOUND con el estado actual")
    void getLatest_notDisabled(RoomStatus status) {
        givenRoom(status);

        assertThatThrownBy(() -> service.getLatest(ROOM_ID))
                .isInstanceOfSatisfying(
                        DamageReportNotFoundException.class,
                        ex -> assertThat(ex.details()).containsEntry("currentStatus", status.name()))
                .hasMessage("Esta habitación no tiene un daño reportado.");
        verifyNoInteractions(damageReportRepository, staffAccountRepository);
    }

    @Test
    @DisplayName("Si la habitación no existe responde ROOM_NOT_FOUND")
    void getLatest_roomNotFound() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatest(ROOM_ID)).isInstanceOf(RoomNotFoundException.class);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "101", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0)));
    }
}
