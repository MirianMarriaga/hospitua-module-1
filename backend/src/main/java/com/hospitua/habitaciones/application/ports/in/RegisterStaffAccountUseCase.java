package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.domain.model.StaffAccount;

/**
 * Puerto de entrada para el registro de una cuenta de personal (T022).
 */
public interface RegisterStaffAccountUseCase {

    /**
     * Registra una cuenta de personal activa con contraseña cifrada.
     *
     * @param command   Datos del registro (usuario, contraseña en texto plano, nombre, área).
     * @param createdBy Usuario del Administrador responsable, o null para la cuenta inicial del sistema.
     * @return La cuenta de personal registrada y activa.
     */
    StaffAccount register(RegisterStaffAccountCommand command, String createdBy);
}
