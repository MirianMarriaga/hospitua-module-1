package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
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
     * Guarda o actualiza una cuenta del personal. Si al insertar otra cuenta con el mismo usuario se registró al mismo
     * tiempo, la clave primaria lo rechaza como {@code UsernameAlreadyExistsException} (FR-003).
     */
    StaffAccount save(StaffAccount account);

    /**
     * Página {@code page} (desde 1) de las cuentas que cumplen los filtros, activas y desactivadas, ordenadas por nombre
     * sin distinguir mayúsculas ni tildes y, a igual nombre, por usuario (plan-gestionar-personal.md T005; FR-007).
     */
    PagedResultDto<StaffAccount> search(StaffAccountFilter filter, int page, int size);

    /**
     * Consulta con bloqueo pesimista las cuentas activas del área Administración.
     */
    List<StaffAccount> findActiveAdministratorsForUpdate();
}
