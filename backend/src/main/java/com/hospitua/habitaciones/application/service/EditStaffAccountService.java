package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.in.EditStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.AccountInactiveException;
import com.hospitua.habitaciones.domain.exception.ConcurrentStaffUpdateException;
import com.hospitua.habitaciones.domain.exception.LastAdministratorException;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editar cuenta del personal (plan-gestionar-personal.md T027). Bloquea la fila de la cuenta, como la edición de
 * habitaciones: dos ediciones con la misma {@code version} quedan en fila y la segunda encuentra la versión nueva. El
 * orden de las validaciones es: existencia, cuenta activa (una cuenta desactivada también cambió su versión, y este
 * mensaje explica mejor el rechazo), versión, que no se quede el sistema sin Administración (FR-010) y, por último,
 * los datos.
 *
 * <p>Si el área nueva no es Administración, primero se bloquean las cuentas activas de Administración y después la de
 * la edición (ya incluida si es una de ellas). El plan pide el mismo orden para la desactivación (T034), así dos
 * operaciones simultáneas no se bloquean entre sí ni dejan el sistema sin Administrador.
 */
@Service
@RequiredArgsConstructor
public class EditStaffAccountService implements EditStaffAccountUseCase {

    static final String INACTIVE = "La cuenta está desactivada; no se puede editar.";
    static final String LAST_ADMINISTRATOR =
            "No se puede cambiar el área: es la única cuenta activa de Administración.";

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public StaffAccountDto edit(EditStaffAccountCommand command) {
        boolean leavesAdministration = command.area() != StaffArea.ADMINISTRACION;
        List<StaffAccount> activeAdministrators =
                leavesAdministration ? staffAccountRepository.findActiveAdministratorsForUpdate() : List.of();
        StaffAccount account = staffAccountRepository
                .lockByUsername(StaffAccount.normalizeUsername(command.username()))
                .orElseThrow(StaffAccountNotFoundException::new);
        if (!account.isActive()) {
            throw new AccountInactiveException(INACTIVE);
        }
        if (!Objects.equals(command.version(), account.getVersion())) {
            throw new ConcurrentStaffUpdateException();
        }
        if (leavesAdministration && account.getArea() == StaffArea.ADMINISTRACION && activeAdministrators.size() <= 1) {
            throw new LastAdministratorException(LAST_ADMINISTRATOR);
        }
        account.edit(
                command.fullName(),
                command.area(),
                newPasswordHash(command.rawPassword()),
                OffsetDateTime.now(clock),
                command.administratorId());
        return StaffAccountDto.detail(staffAccountRepository.save(account));
    }

    /**
     * Sin contraseña nueva (ausente, vacía o solo con espacios) se conserva la actual (esc. 7); una nueva se cifra si
     * cumple FR-004 y el límite de BCrypt.
     */
    private String newPasswordHash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        StaffAccount.requireValidPassword(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }
}
