package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.in.DeactivateStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.AccountInactiveException;
import com.hospitua.habitaciones.domain.exception.CannotDeactivateSelfException;
import com.hospitua.habitaciones.domain.exception.LastAdministratorException;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desactivar cuenta del personal (plan-gestionar-personal.md T034). Primero bloquea las cuentas activas de
 * Administración ({@code SELECT … FOR UPDATE}): de dos desactivaciones simultáneas de las dos últimas, la segunda
 * espera y ve que solo queda una (FR-010). Después bloquea la cuenta, valida y la desactiva, y libera sus tareas
 * abiertas por los puertos de limpieza y de reparación, en esta misma transacción ({@code MANDATORY}): si una
 * liberación falla, la desactivación se revierte. No captura los errores de esos puertos (FR-011).
 */
@Service
@RequiredArgsConstructor
public class DeactivateStaffAccountService implements DeactivateStaffAccountUseCase {

    static final String ALREADY_INACTIVE = "La cuenta ya está desactivada.";
    static final String LAST_ADMINISTRATOR = "No se puede desactivar: es la única cuenta activa de Administración.";

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final ReleaseCleaningTaskOnDeactivationUseCase releaseCleaningTask;
    private final ReleaseRepairTaskOnDeactivationUseCase releaseRepairTask;
    private final Clock clock;

    @Override
    @Transactional
    public StaffAccountDto deactivate(String username, String administratorId) {
        List<StaffAccount> activeAdministrators = staffAccountRepository.findActiveAdministratorsForUpdate();
        StaffAccount account = staffAccountRepository
                .lockByUsername(StaffAccount.normalizeUsername(username))
                .orElseThrow(StaffAccountNotFoundException::new);
        if (!account.isActive()) {
            throw new AccountInactiveException(ALREADY_INACTIVE);
        }
        if (account.getUsername().equals(administratorId)) {
            throw new CannotDeactivateSelfException();
        }
        if (account.getArea() == StaffArea.ADMINISTRACION && activeAdministrators.size() <= 1) {
            throw new LastAdministratorException(LAST_ADMINISTRATOR);
        }
        account.deactivate(OffsetDateTime.now(clock), administratorId);
        StaffAccount saved = staffAccountRepository.save(account);
        releaseCleaningTask.releaseOpenTaskOf(saved.getUsername(), administratorId);
        releaseRepairTask.releaseOpenTaskOf(saved.getUsername());
        return StaffAccountDto.summary(saved);
    }
}
