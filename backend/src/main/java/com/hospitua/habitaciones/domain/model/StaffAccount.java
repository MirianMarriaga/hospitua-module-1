package com.hospitua.habitaciones.domain.model;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Entidad de dominio pura que representa una cuenta del personal (T022).
 * Contiene las reglas de normalización y validación de formato del usuario.
 */
@Getter
public class StaffAccount {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._]{3,30}$");

    private final String username;
    private String passwordHash;
    private String fullName;
    private StaffArea area;
    private boolean active;
    private final OffsetDateTime createdAt;
    private final String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private OffsetDateTime deactivatedAt;
    private String deactivatedBy;
    private final Integer version;

    public StaffAccount(
            String username,
            String passwordHash,
            String fullName,
            StaffArea area,
            boolean active,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            OffsetDateTime deactivatedAt,
            String deactivatedBy,
            Integer version) {
        this.username = validateAndNormalizeUsername(username);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash no puede ser nulo");
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash no puede estar en blanco");
        }
        this.fullName = validateFullName(fullName);
        this.area = Objects.requireNonNull(area, "area no puede ser nula");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.createdBy = createdBy;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.updatedBy = updatedBy;
        this.deactivatedAt = deactivatedAt;
        this.deactivatedBy = deactivatedBy;
        this.version = version;
    }

    /**
     * Fábrica para crear una cuenta nueva y activa.
     */
    public static StaffAccount createNew(
            String rawUsername,
            String passwordHash,
            String rawFullName,
            StaffArea area,
            OffsetDateTime now,
            String createdBy) {
        return new StaffAccount(
                rawUsername, passwordHash, rawFullName, area, true, now, createdBy, now, createdBy, null, null, null);
    }

    /**
     * Normaliza el usuario eliminando espacios al inicio y al final y convirtiendo a minúsculas.
     */
    public static String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            return null;
        }
        return rawUsername.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Valida el formato del usuario (entre 3 y 30 caracteres, letras sin tilde, números, punto o guion bajo).
     */
    public static String validateAndNormalizeUsername(String rawUsername) {
        String normalized = normalizeUsername(rawUsername);
        if (normalized == null || !USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo.");
        }
        return normalized;
    }

    private static String validateFullName(String rawFullName) {
        if (rawFullName == null || rawFullName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio.");
        }
        return rawFullName.trim();
    }
}
