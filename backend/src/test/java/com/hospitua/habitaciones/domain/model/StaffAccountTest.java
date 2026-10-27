package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StaffAccountTest {

    private final OffsetDateTime now = OffsetDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneOffset.ofHours(-5));

    @Test
    @DisplayName("Normaliza usuario eliminando espacios y convirtiendo a minúsculas")
    void shouldNormalizeUsername() {
        assertThat(StaffAccount.normalizeUsername("  JRamirez  ")).isEqualTo("jramirez");
        assertThat(StaffAccount.normalizeUsername("Admin.Hotel_01")).isEqualTo("admin.hotel_01");
        assertThat(StaffAccount.normalizeUsername(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jramirez", "admin", "recepcion.01", "user_123", "a.b_c"})
    @DisplayName("Acepta nombres de usuario válidos con entre 3 y 30 caracteres, letras, números, punto y guion bajo")
    void shouldAcceptValidUsernames(String username) {
        String normalized = StaffAccount.validateAndNormalizeUsername(username);
        assertThat(normalized).isEqualTo(username.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ab",
                "este_usuario_tiene_mas_de_treinta_caracteres_invalidos",
                "usuario con espacio",
                "user@hotel",
                "user#1",
                "áñóuser"
            })
    @DisplayName("Rechaza nombres de usuario inválidos por longitud o caracteres especiales")
    void shouldRejectInvalidUsernames(String invalidUsername) {
        assertThatThrownBy(() -> StaffAccount.validateAndNormalizeUsername(invalidUsername))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 3 y 30 caracteres");
    }

    @Test
    @DisplayName("Creación exitosa de cuenta nueva mediante createNew")
    void shouldCreateNewActiveStaffAccount() {
        StaffAccount account = StaffAccount.createNew(
                "  Carlos.Perez  ", "$2a$10$hashedPasswordSample", "Carlos Pérez", StaffArea.RECEPCION, now, "admin");

        assertThat(account.getUsername()).isEqualTo("carlos.perez");
        assertThat(account.getPasswordHash()).isEqualTo("$2a$10$hashedPasswordSample");
        assertThat(account.getFullName()).isEqualTo("Carlos Pérez");
        assertThat(account.getArea()).isEqualTo(StaffArea.RECEPCION);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getCreatedAt()).isEqualTo(now);
        assertThat(account.getCreatedBy()).isEqualTo("admin");
        assertThat(account.getUpdatedAt()).isEqualTo(now);
        assertThat(account.getUpdatedBy()).isEqualTo("admin");
        assertThat(account.getDeactivatedAt()).isNull();
        assertThat(account.getDeactivatedBy()).isNull();
        assertThat(account.getVersion()).isNull();
    }

    @Test
    @DisplayName("Rechaza nombre completo nulo o en blanco")
    void shouldRejectEmptyFullName() {
        assertThatThrownBy(
                        () -> StaffAccount.createNew("carlos", "$2a$10$hash", "   ", StaffArea.RECEPCION, now, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre completo");
    }
}
