package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("Registro exitoso cifra la contraseña y crea la cuenta activa")
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
    @DisplayName("Rechaza área nula")
    void shouldRejectNullArea() {
        RegisterStaffAccountCommand command =
                new RegisterStaffAccountCommand("jramirez", "Limpieza2026", "Jorge Ramírez", null);

        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("área");
    }
}
