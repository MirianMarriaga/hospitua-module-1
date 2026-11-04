package com.hospitua.habitaciones.domain.model;

import java.nio.charset.StandardCharsets;
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

    /** Longitud mínima de la contraseña en texto plano, antes de cifrarla (FR-004). */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** BCrypt solo cifra 72 bytes y rechaza una contraseña más larga; la ñ y las tildes ocupan dos (FR-004). */
    public static final int MAX_PASSWORD_BYTES = 72;

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

    /** La contraseña no pasa de {@link #MAX_PASSWORD_BYTES} en UTF-8; una nula no tiene largo que revisar. */
    public static boolean fitsPasswordLimit(String rawPassword) {
        return rawPassword == null || rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
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

    /**
     * Cambia el nombre, el área y, si llega un hash nuevo, la contraseña; el usuario no cambia (plan-gestionar-personal.md
     * T027; FR-008). Registra quién y cuándo (FR-013).
     *
     * @param newPasswordHash la contraseña nueva ya cifrada, o {@code null} para conservar la actual
     */
    public void edit(
            String rawFullName, StaffArea newArea, String newPasswordHash, OffsetDateTime now, String editedBy) {
        this.fullName = validateFullName(rawFullName);
        this.area = Objects.requireNonNull(newArea, "area no puede ser nula");
        if (newPasswordHash != null) {
            if (newPasswordHash.isBlank()) {
                throw new IllegalArgumentException("passwordHash no puede estar en blanco");
            }
            this.passwordHash = newPasswordHash;
        }
        this.updatedAt = Objects.requireNonNull(now, "updatedAt no puede ser nulo");
        this.updatedBy = editedBy;
    }

    /**
     * Deja la cuenta desactivada sin borrarla: el historial sigue mostrando su nombre (plan-gestionar-personal.md T034;
     * FR-009, FR-013). Las reglas de la propia cuenta y del último Administrador las valida el servicio, que conoce al
     * responsable y las demás cuentas.
     */
    public void deactivate(OffsetDateTime now, String deactivatedBy) {
        this.active = false;
        this.deactivatedAt = Objects.requireNonNull(now, "deactivatedAt no puede ser nulo");
        this.deactivatedBy = deactivatedBy;
    }

    private static String validateFullName(String rawFullName) {
        if (rawFullName == null || rawFullName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio.");
        }
        return rawFullName.trim();
    }
}
