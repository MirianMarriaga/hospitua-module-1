package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.CurrentUserRequiredException;
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
 * Pruebas unitarias del CurrentUserResolver (T020, regla 14).
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    private CurrentUserResolver resolver;

    private static final String USERNAME = "jramirez";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T10:00:00-05:00");

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserResolver(staffAccountRepository);
    }

    private StaffAccount activeAccount() {
        return new StaffAccount(
                USERNAME,
                "$2a$10$hashed",
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
                "$2a$10$hashed",
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
    @DisplayName("Resolve con X-User válido y cuenta activa devuelve el username normalizado")
    void resolve_validActiveUser_returnsUsername() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(activeAccount()));

        String result = resolver.resolve("jramirez");

        assertThat(result).isEqualTo(USERNAME);
    }

    @Test
    @DisplayName("Resolve normaliza el username")
    void resolve_normalizesUsername() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(activeAccount()));

        String result = resolver.resolve("  JRamirez  ");

        assertThat(result).isEqualTo(USERNAME);
    }

    @Test
    @DisplayName("Resolve con X-User null lanza CurrentUserRequiredException")
    void resolve_nullHeader_throws() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(CurrentUserRequiredException.class);
    }

    @Test
    @DisplayName("Resolve con X-User vacío lanza CurrentUserRequiredException")
    void resolve_emptyHeader_throws() {
        assertThatThrownBy(() -> resolver.resolve("")).isInstanceOf(CurrentUserRequiredException.class);
    }

    @Test
    @DisplayName("Resolve con X-User en blanco lanza CurrentUserRequiredException")
    void resolve_blankHeader_throws() {
        assertThatThrownBy(() -> resolver.resolve("   ")).isInstanceOf(CurrentUserRequiredException.class);
    }

    @Test
    @DisplayName("Resolve con usuario inexistente lanza CurrentUserRequiredException")
    void resolve_nonExistentUser_throws() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(USERNAME)).isInstanceOf(CurrentUserRequiredException.class);
    }

    @Test
    @DisplayName("Resolve con cuenta desactivada lanza CurrentUserRequiredException")
    void resolve_inactiveAccount_throws() {
        when(staffAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(inactiveAccount()));

        assertThatThrownBy(() -> resolver.resolve(USERNAME)).isInstanceOf(CurrentUserRequiredException.class);
    }
}
