package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.StaffAccount;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia y consulta de cuentas del personal (T022).
 */
public interface StaffAccountRepositoryPort {

    /**
     * Consulta una cuenta por su nombre de usuario normalizado.
     */
    Optional<StaffAccount> findByUsername(String username);

    /**
     * Guarda o actualiza una cuenta del personal.
     */
    StaffAccount save(StaffAccount account);

    /**
     * Consulta con bloqueo pesimista las cuentas activas del área Administración.
     */
    List<StaffAccount> findActiveAdministratorsForUpdate();
}
