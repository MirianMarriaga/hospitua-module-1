package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.AccountInactiveException;
import com.hospitua.habitaciones.domain.exception.CannotDeactivateSelfException;
import com.hospitua.habitaciones.domain.exception.LastAdministratorException;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lo que pasa con la habitación al liberar cada tarea lo deciden los puertos de limpieza y de reparación (L21, L30);
 * aquí se prueba que el servicio los invoca con los datos correctos, y {@code DeactivateStaffIntegrationTest} el
 * resultado real sobre las habitaciones (T029).
 */
@ExtendWith(MockitoExtension.class)
class DeactivateStaffAccountServiceTest {

    private static final String ADMIN = "lmendez";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T15:05:00Z"), ZoneId.of("America/Bogota"));
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-01T08:00:00-05:00");

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    @Mock
    private ReleaseCleaningTaskOnDeactivationUseCase releaseCleaningTask;

    @Mock
    private ReleaseRepairTaskOnDeactivationUseCase releaseRepairTask;

    private DeactivateStaffAccountService service;

    @BeforeEach
    void setUp() {
        service = new DeactivateStaffAccountService(
                staffAccountRepository, releaseCleaningTask, releaseRepairTask, CLOCK);
    }

    @Test
    @DisplayName(
            "T029: bloquea los Administradores y la cuenta, la desactiva sin borrarla con el responsable y la hora,"
                    + " y libera sus tareas de limpieza y de reparación")
    void deactivate_releasesTasks() {
        givenAdministrators(
                account("admin", StaffArea.ADMINISTRACION, true), account(ADMIN, StaffArea.ADMINISTRACION, true));
        StaffAccount account = givenLocked(account("jramirez", StaffArea.LIMPIEZA, true));
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StaffAccountDto deactivated = service.deactivate(" JRamirez ", ADMIN);

        InOrder order = inOrder(staffAccountRepository, releaseCleaningTask, releaseRepairTask);
        order.verify(staffAccountRepository).findActiveAdministratorsForUpdate();
        order.verify(staffAccountRepository).lockByUsername("jramirez");
        order.verify(staffAccountRepository).save(account);
        order.verify(releaseCleaningTask).releaseOpenTaskOf("jramirez", ADMIN);
        order.verify(releaseRepairTask).releaseOpenTaskOf("jramirez");
        assertThat(account.isActive()).isFalse();
        assertThat(account.getDeactivatedBy()).isEqualTo(ADMIN);
        assertThat(account.getDeactivatedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
        assertThat(account.getFullName()).isEqualTo("Nombre de jramirez");
        assertThat(deactivated)
                .isEqualTo(new StaffAccountDto("jramirez", "Nombre de jramirez", StaffArea.LIMPIEZA, false, null));
    }

    @Test
    @DisplayName("T029: un Administrador se puede desactivar si queda otro activo")
    void deactivate_administratorWithAnotherActive() {
        StaffAccount other = account("lrios", StaffArea.ADMINISTRACION, true);
        givenAdministrators(account(ADMIN, StaffArea.ADMINISTRACION, true), other);
        givenLocked(other);
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate("lrios", ADMIN);

        ArgumentCaptor<StaffAccount> saved = ArgumentCaptor.forClass(StaffAccount.class);
        verify(staffAccountRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("T030: la propia cuenta es CANNOT_DEACTIVATE_SELF, sin desactivar ni liberar nada")
    void deactivate_self() {
        givenAdministrators(
                account("admin", StaffArea.ADMINISTRACION, true), account(ADMIN, StaffArea.ADMINISTRACION, true));
        givenLocked(account(ADMIN, StaffArea.ADMINISTRACION, true));

        assertThatThrownBy(() -> service.deactivate(ADMIN, ADMIN))
                .isInstanceOf(CannotDeactivateSelfException.class)
                .hasMessage("No puedes desactivar tu propia cuenta.");
        assertNothingChanged();
    }

    @Test
    @DisplayName("T030: la única cuenta activa de Administración es LAST_ADMINISTRATOR")
    void deactivate_lastAdministrator() {
        StaffAccount last = account("admin", StaffArea.ADMINISTRACION, true);
        givenAdministrators(last);
        givenLocked(last);

        assertThatThrownBy(() -> service.deactivate("admin", "recepcion1"))
                .isInstanceOf(LastAdministratorException.class)
                .hasMessage("No se puede desactivar: es la única cuenta activa de Administración.");
        assertNothingChanged();
    }

    @Test
    @DisplayName("T030: una cuenta ya desactivada es ACCOUNT_INACTIVE")
    void deactivate_alreadyInactive() {
        givenAdministrators(account(ADMIN, StaffArea.ADMINISTRACION, true));
        givenLocked(account("jramirez", StaffArea.LIMPIEZA, false));

        assertThatThrownBy(() -> service.deactivate("jramirez", ADMIN))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessage("La cuenta ya está desactivada.");
        assertNothingChanged();
    }

    @Test
    @DisplayName("Una cuenta inexistente es STAFF_NOT_FOUND")
    void deactivate_notFound() {
        givenAdministrators(account(ADMIN, StaffArea.ADMINISTRACION, true));
        when(staffAccountRepository.lockByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate("nadie", ADMIN)).isInstanceOf(StaffAccountNotFoundException.class);
        assertNothingChanged();
    }

    private void givenAdministrators(StaffAccount... administrators) {
        when(staffAccountRepository.findActiveAdministratorsForUpdate()).thenReturn(List.of(administrators));
    }

    private StaffAccount givenLocked(StaffAccount account) {
        when(staffAccountRepository.lockByUsername(account.getUsername())).thenReturn(Optional.of(account));
        return account;
    }

    private void assertNothingChanged() {
        verify(staffAccountRepository, never()).save(any());
        verifyNoInteractions(releaseCleaningTask, releaseRepairTask);
    }

    private static StaffAccount account(String username, StaffArea area, boolean active) {
        return new StaffAccount(
                username,
                "$2a$10$hash",
                "Nombre de " + username,
                area,
                active,
                CREATED_AT,
                "admin",
                CREATED_AT,
                "admin",
                active ? null : CREATED_AT,
                active ? null : "admin",
                2);
    }
}
