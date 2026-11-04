package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.AccountInactiveException;
import com.hospitua.habitaciones.domain.exception.ConcurrentStaffUpdateException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EditStaffAccountServiceTest {

    private static final String ADMIN = "lmendez";
    private static final String OLD_HASH = "$2a$10$viejo";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T15:05:00Z"), ZoneId.of("America/Bogota"));
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-01T08:00:00-05:00");

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    private EditStaffAccountService service;

    @BeforeEach
    void setUp() {
        service = new EditStaffAccountService(staffAccountRepository, passwordEncoder, CLOCK);
    }

    @Test
    @DisplayName("T023: cambia nombre y área, cifra la contraseña nueva y registra al Administrador y la hora de Clock")
    void edit_changesNameAreaAndPassword() {
        StaffAccount account = givenLocked(true);
        when(passwordEncoder.encode("Mantenimiento2026")).thenReturn("$2a$10$nuevo");
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StaffAccountDto edited = service.edit(command(3, "Mantenimiento2026"));

        StaffAccount saved = saved();
        assertThat(saved).isSameAs(account);
        assertThat(saved.getUsername()).isEqualTo("jramirez");
        assertThat(saved.getFullName()).isEqualTo("Jorge A. Ramírez");
        assertThat(saved.getArea()).isEqualTo(StaffArea.MANTENIMIENTO);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$nuevo");
        assertThat(saved.getUpdatedBy()).isEqualTo(ADMIN);
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(edited)
                .isEqualTo(new StaffAccountDto("jramirez", "Jorge A. Ramírez", StaffArea.MANTENIMIENTO, true, 3));
    }

    @Test
    @DisplayName("FR-010: la única cuenta activa de Administración no puede cambiar de área; se bloquean primero las"
            + " cuentas de Administración y después la de la edición")
    void edit_lastAdministratorKeepsArea() {
        StaffAccount admin = givenLocked(true, StaffArea.ADMINISTRACION);
        when(staffAccountRepository.findActiveAdministratorsForUpdate()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.edit(command(3, null)))
                .isInstanceOf(LastAdministratorException.class)
                .hasMessage("No se puede cambiar el área: es la única cuenta activa de Administración.");
        InOrder locks = inOrder(staffAccountRepository);
        locks.verify(staffAccountRepository).findActiveAdministratorsForUpdate();
        locks.verify(staffAccountRepository).lockByUsername("jramirez");
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR-010: con otra cuenta activa de Administración, el Administrador sí puede cambiar de área")
    void edit_administratorLeavesWhenAnotherRemains() {
        StaffAccount admin = givenLocked(true, StaffArea.ADMINISTRACION);
        when(staffAccountRepository.findActiveAdministratorsForUpdate()).thenReturn(List.of(admin, otherAdmin()));
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.edit(command(3, null));

        assertThat(saved().getArea()).isEqualTo(StaffArea.MANTENIMIENTO);
    }

    @Test
    @DisplayName("FR-010: si el área nueva es Administración no hace falta bloquear las demás cuentas")
    void edit_toAdministration_noAdministratorLock() {
        givenLocked(true, StaffArea.ADMINISTRACION);
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.edit(new EditStaffAccountCommand("jramirez", 3, "Jorge", StaffArea.ADMINISTRACION, null, ADMIN));

        verify(staffAccountRepository, never()).findActiveAdministratorsForUpdate();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "        "})
    @DisplayName("T024: sin contraseña nueva (o solo con espacios) conserva la actual y no cifra nada")
    void edit_keepsPassword(String password) {
        givenLocked(true);
        when(staffAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.edit(command(3, password));

        assertThat(saved().getPasswordHash()).isEqualTo(OLD_HASH);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("T025: una cuenta desactivada es ACCOUNT_INACTIVE, aunque su versión haya cambiado")
    void edit_inactive() {
        givenLocked(false);

        assertThatThrownBy(() -> service.edit(command(2, null)))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessage("La cuenta está desactivada; no se puede editar.");
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("T025: una contraseña nueva de menos de 8 caracteres se rechaza sin cifrarla ni guardar")
    void edit_shortPassword() {
        givenLocked(true);

        assertThatThrownBy(() -> service.edit(command(3, "1234567")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña debe tener al menos 8 caracteres.");
        verifyNoInteractions(passwordEncoder);
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("T025: una contraseña nueva de más de 72 bytes (límite de BCrypt) se rechaza sin cifrarla ni guardar")
    void edit_passwordOverBcryptLimit() {
        givenLocked(true);

        assertThatThrownBy(() -> service.edit(command(3, "a" + "ñ".repeat(36))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña admite máximo 72 caracteres.");
        verifyNoInteractions(passwordEncoder);
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con otra versión es CONCURRENT_UPDATE con el mensaje de la cuenta, sin guardar")
    void edit_staleVersion() {
        givenLocked(true);

        assertThatThrownBy(() -> service.edit(command(2, null)))
                .isInstanceOf(ConcurrentStaffUpdateException.class)
                .hasMessage(
                        "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos"
                                + " actuales.");
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una cuenta inexistente es STAFF_NOT_FOUND; el usuario de la ruta se normaliza antes de buscarlo")
    void edit_notFound() {
        when(staffAccountRepository.lockByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(
                        new EditStaffAccountCommand(" Nadie ", 0, "Nadie", StaffArea.LIMPIEZA, null, ADMIN)))
                .isInstanceOf(StaffAccountNotFoundException.class);
    }

    @Test
    @DisplayName("SC-002: el comando no muestra la contraseña en su texto (logs)")
    void command_toStringHidesPassword() {
        assertThat(command(3, "Mantenimiento2026").toString())
                .contains("JRamirez", "MANTENIMIENTO")
                .doesNotContain("Mantenimiento2026");
    }

    private StaffAccount givenLocked(boolean active) {
        return givenLocked(active, StaffArea.LIMPIEZA);
    }

    private StaffAccount givenLocked(boolean active, StaffArea area) {
        StaffAccount account = new StaffAccount(
                "jramirez",
                OLD_HASH,
                "Jorge Ramírez",
                area,
                active,
                CREATED_AT,
                "admin",
                CREATED_AT,
                "admin",
                active ? null : CREATED_AT,
                active ? null : "admin",
                3);
        when(staffAccountRepository.lockByUsername("jramirez")).thenReturn(Optional.of(account));
        return account;
    }

    private static StaffAccount otherAdmin() {
        return StaffAccount.createNew("lmendez", OLD_HASH, "Laura Méndez", StaffArea.ADMINISTRACION, CREATED_AT, null);
    }

    private static EditStaffAccountCommand command(int version, String password) {
        return new EditStaffAccountCommand(
                "JRamirez", version, " Jorge A. Ramírez ", StaffArea.MANTENIMIENTO, password, ADMIN);
    }

    private StaffAccount saved() {
        ArgumentCaptor<StaffAccount> account = ArgumentCaptor.forClass(StaffAccount.class);
        verify(staffAccountRepository).save(account.capture());
        return account.getValue();
    }
}
