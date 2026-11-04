package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterStaffAccountServiceTest {

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    private Clock clock;
    private RegisterStaffAccountService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("America/Bogota"));
        service = new RegisterStaffAccountService(staffAccountRepository, passwordEncoder, clock);
    }

    @Test
    @DisplayName("T007: registro exitoso normaliza el usuario, cifra la contraseña y crea la cuenta activa con el"
            + " Administrador y la hora de Clock")
    void shouldRegisterAccountSuccessfully() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("  JRamirez  ", "Limpieza2026", "Jorge Ramírez", StaffArea.LIMPIEZA);

        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Limpieza2026")).thenReturn("$2a$10$hashedSecuredVal");
        when(staffAccountRepository.save(any(StaffAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffAccount result = service.register(command, "admin");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("jramirez");
        assertThat(result.getFullName()).isEqualTo("Jorge Ramírez");
        assertThat(result.getArea()).isEqualTo(StaffArea.LIMPIEZA);
        assertThat(result.getPasswordHash()).isEqualTo("$2a$10$hashedSecuredVal");
        assertThat(result.getPasswordHash()).doesNotContain("Limpieza2026");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getCreatedBy()).isEqualTo("admin");
        assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(result.getUpdatedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(result.getDeactivatedAt()).isNull();

        ArgumentCaptor<StaffAccount> captor = ArgumentCaptor.forClass(StaffAccount.class);
        verify(staffAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedSecuredVal");
    }

    @Test
    @DisplayName("Rechaza registro cuando el nombre de usuario ya existe")
    void shouldRejectWhenUsernameAlreadyExists() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "Limpieza2026", "Jorge Ramírez", StaffArea.LIMPIEZA);

        StaffAccount existing = StaffAccount.createNew(
                "jramirez",
                "hash",
                "Otro Jorge",
                StaffArea.LIMPIEZA,
                clock.instant().atZone(clock.getZone()).toOffsetDateTime(),
                "admin");
        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("jramirez");

        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza contraseña menor a 8 caracteres")
    void shouldRejectShortPassword() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "1234567", "Jorge Ramírez", StaffArea.LIMPIEZA);

        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 8 caracteres");

        verify(passwordEncoder, never()).encode(any());
        verify(staffAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza una contraseña de más de 72 bytes (límite de BCrypt) sin cifrarla; 72 bytes se aceptan")
    void shouldRejectPasswordOverBcryptLimit() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "a" + "ñ".repeat(36), "Jorge Ramírez", StaffArea.LIMPIEZA);
        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña admite máximo 72 caracteres.");

        verify(passwordEncoder, never()).encode(any());
        verify(staffAccountRepository, never()).save(any());
        assertThat(StaffAccount.fitsPasswordLimit("a".repeat(72))).isTrue();
    }

    @Test
    @DisplayName("Rechaza área nula")
    void shouldRejectNullArea() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "Limpieza2026", "Jorge Ramírez", null);

        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("área");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("T008: el usuario existente se busca ya normalizado, esté activo o desactivado")
    void shouldRejectExistingUsernameWithOtherCaseOrDeactivated(boolean active) {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand(" JRAMIREZ ", "Limpieza2026", "Jorge Ramírez", StaffArea.LIMPIEZA);
        OffsetDateTime now = OffsetDateTime.now(clock);
        StaffAccount existing = new StaffAccount(
                "jramirez",
                "hash",
                "Jorge Ramírez",
                StaffArea.LIMPIEZA,
                active,
                now,
                "admin",
                now,
                "admin",
                active ? null : now,
                active ? null : "admin",
                1);
        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessage("Ya existe una cuenta con el usuario jramirez.");
        verify(passwordEncoder, never()).encode(any());
        verify(staffAccountRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jr", "jorge ramirez", "jorge-ramirez", "ramírez", "j_r_a_m_i_r_e_z_0123456789_abcde"})
    @DisplayName("T009: rechaza un usuario con caracteres no permitidos o fuera de 3 a 30 caracteres, sin consultar")
    void shouldRejectInvalidUsername(String username) {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand(username, "Limpieza2026", "Jorge Ramírez", StaffArea.LIMPIEZA);

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo.");
        verifyNoInteractions(staffAccountRepository, passwordEncoder);
    }

    @Test
    @DisplayName("T009: rechaza un nombre vacío o una contraseña ausente sin guardar la cuenta")
    void shouldRejectMissingFields() {
        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Limpieza2026")).thenReturn("$2a$10$hash");

        assertThatThrownBy(() -> service.register(
                        new RegisterStaffAccountCommand("jramirez", "Limpieza2026", "   ", StaffArea.LIMPIEZA),
                        "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre completo es obligatorio.");
        assertThatThrownBy(() -> service.register(
                        new RegisterStaffAccountCommand("jramirez", null, "Jorge Ramírez", StaffArea.LIMPIEZA),
                        "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 8 caracteres");
        verify(staffAccountRepository, never()).save(any());
    }
}
