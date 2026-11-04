package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/staff} (plan-gestionar-personal.md T016). Todos los campos son obligatorios. El usuario se
 * valida aquí ya con los espacios de los extremos permitidos, porque el servicio los quita y lo pasa a minúsculas antes
 * de aplicar la misma regla (FR-003); el área llega como texto para responder el mensaje del catálogo (FR-002).
 */
public record RegisterStaffRequest(
        @NotBlank(message = REQUIRED) @Pattern(regexp = "\\s*[A-Za-z0-9._]{3,30}\\s*", message = USERNAME_RULE)
        String username,

        @NotBlank(message = REQUIRED) @Size(min = StaffAccount.MIN_PASSWORD_LENGTH, message = PASSWORD_RULE)
        String password,

        @NotBlank(message = REQUIRED) @Size(max = 150, message = FULL_NAME_RULE)
        String fullName,

        @NotNull(message = REQUIRED) @Pattern(regexp = AREAS, message = AREA_RULE)
        String area) {

    /** El catálogo de áreas (FR-002), compartido con la edición. */
    static final String AREAS = "RECEPCION|ADMINISTRACION|GERENCIA|LIMPIEZA|MANTENIMIENTO";

    static final String PASSWORD_RULE = "La contraseña debe tener al menos 8 caracteres.";
    static final String PASSWORD_LIMIT_RULE = "La contraseña admite máximo 72 caracteres.";
    static final String FULL_NAME_RULE = "El nombre completo admite máximo 150 caracteres.";
    static final String AREA_RULE = "Selecciona un área válida.";

    private static final String REQUIRED = "Completa los campos obligatorios.";
    private static final String USERNAME_RULE =
            "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo.";

    /**
     * Propiedad {@code password} para Bean Validation (como {@code RoomBaseRateQuery.isDate}): no pasa del límite de
     * BCrypt, que se mide en bytes, así que {@code details.field} es {@code password}.
     */
    @AssertTrue(message = PASSWORD_LIMIT_RULE)
    public boolean isPassword() {
        return StaffAccount.fitsPasswordLimit(password);
    }

    public RegisterStaffAccountCommand toCommand() {
        return new RegisterStaffAccountCommand(username, password, fullName, StaffArea.valueOf(area));
    }

    /** Nunca muestra la contraseña, ni en un log que imprima la petición (FR-004; SC-002). */
    @Override
    public String toString() {
        return "RegisterStaffRequest[username=" + username + ", fullName=" + fullName + ", area=" + area + "]";
    }
}
