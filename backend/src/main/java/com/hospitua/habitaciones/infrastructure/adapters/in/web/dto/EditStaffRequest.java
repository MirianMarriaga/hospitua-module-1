package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/staff/{username}} (plan-gestionar-personal.md T027). No lleva {@code username}: sale de
 * la ruta y, si llega en el cuerpo, se ignora (FR-008). {@code password} es opcional: ausente, vacía o solo con
 * espacios conserva la actual (esc. 7); si llega, debe tener entre 8 caracteres y 72 bytes, el límite de BCrypt
 * (FR-004).
 */
public record EditStaffRequest(
        @NotNull(message = RegisterStaffRequest.REQUIRED) Integer version,

        @NotBlank(message = RegisterStaffRequest.REQUIRED)
        @Size(max = 150, message = RegisterStaffRequest.FULL_NAME_RULE)
        String fullName,

        @NotNull(message = RegisterStaffRequest.REQUIRED)
        @Pattern(regexp = RegisterStaffRequest.AREAS, message = RegisterStaffRequest.AREA_RULE)
        String area,

        @Pattern(regexp = PASSWORD_OR_BLANK, message = RegisterStaffRequest.PASSWORD_RULE)
        String password) {

    /** En blanco (conserva la actual) o de al menos {@link StaffAccount#MIN_PASSWORD_LENGTH} caracteres. */
    private static final String PASSWORD_OR_BLANK = "\\s*|(?s).{" + StaffAccount.MIN_PASSWORD_LENGTH + ",}";

    /** Como en {@link RegisterStaffRequest#isPassword()}: el límite de BCrypt, con {@code details.field = password}. */
    @AssertTrue(message = RegisterStaffRequest.PASSWORD_LIMIT_RULE)
    public boolean isPassword() {
        return StaffAccount.fitsPasswordLimit(password);
    }

    public EditStaffAccountCommand toCommand(String username, String administratorId) {
        return new EditStaffAccountCommand(
                username, version, fullName, StaffArea.valueOf(area), password, administratorId);
    }

    /** Nunca muestra la contraseña, ni en un log que imprima la petición (FR-004; SC-002). */
    @Override
    public String toString() {
        return "EditStaffRequest[version=" + version + ", fullName=" + fullName + ", area=" + area + "]";
    }
}
