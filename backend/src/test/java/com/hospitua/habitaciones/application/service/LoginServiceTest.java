package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidCredentialsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias del servicio de inicio de sesión (T020).
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    private LoginService loginService;

    private static final String USERNAME = "jramirez";
    private static final String RAW_PASSWORD = "password123";
    private static final String PASSWORD_HASH = "$2a$10$hashed";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T10:00:00-05:00");

    @BeforeEach
    void setUp() {
        loginService = new LoginService(staffAccountRepository, passwordEncoder);
    }

    private StaffAccount activeAccount() {
        return new StaffAccount(
                USERNAME,
                PASSWORD_HASH,
                "Jorge Ramírez",
                StaffArea.LIMPIEZA,
                true,
                NOW,
                "admin",
                NOW,
                "admin",
                null,
                null,
                1);
    }

    private StaffAccount inactiveAccount() {
        return new StaffAccount(
                USERNAME,
                PASSWORD_HASH,
                "Jorge Ramírez",
                StaffArea.LIMPIEZA,
                false,
                NOW,
                "admin",
                NOW,
                "admin",
                NOW,
                "admin",
                2);
    }

    @Test
    @DisplayName("Login exitoso con credenciales válidas y cuenta activa")
    void login_validCredentialsActiveAccount_returnsAccount() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(activeAccount()));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

        StaffAccount result = loginService.login(USERNAME, RAW_PASSWORD);

        assertThat(result.getUsername()).isEqualTo(USERNAME);
        assertThat(result.getFullName()).isEqualTo("Jorge Ramírez");
        assertThat(result.getArea()).isEqualTo(StaffArea.LIMPIEZA);
    }

    @Test
    @DisplayName("Login con usuario inexistente lanza InvalidCredentialsException")
    void login_nonExistentUser_throwsInvalidCredentials() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(USERNAME, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login con contraseña incorrecta lanza InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentials() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(activeAccount()));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(USERNAME, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login con cuenta desactivada lanza InvalidCredentialsException")
    void login_inactiveAccount_throwsInvalidCredentials() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(inactiveAccount()));

        assertThatThrownBy(() -> loginService.login(USERNAME, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login con username null lanza InvalidCredentialsException")
    void login_nullUsername_throwsInvalidCredentials() {
        assertThatThrownBy(() -> loginService.login(null, RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login con password null lanza InvalidCredentialsException")
    void login_nullPassword_throwsInvalidCredentials() {
        assertThatThrownBy(() -> loginService.login(USERNAME, null)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Login normaliza el usuario antes de buscar")
    void login_normalizesUsername() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(activeAccount()));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);

        StaffAccount result = loginService.login("  JRamirez  ", RAW_PASSWORD);

        assertThat(result.getUsername()).isEqualTo(USERNAME);
    }
}
