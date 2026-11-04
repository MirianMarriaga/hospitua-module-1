package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.in.EditStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.AccountInactiveException;
import com.hospitua.habitaciones.domain.exception.ConcurrentStaffUpdateException;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editar cuenta del personal (plan-gestionar-personal.md T027). Bloquea la fila de la cuenta, como la edición de
 * habitaciones: dos ediciones con la misma {@code version} quedan en fila y la segunda encuentra la versión nueva. El
 * orden de las validaciones es: existencia, cuenta activa (una cuenta desactivada también cambió su versión, y este
 * mensaje explica mejor el rechazo), versión y, por último, los datos.
 */
@Service
@RequiredArgsConstructor
public class EditStaffAccountService implements EditStaffAccountUseCase {

    static final String INACTIVE = "La cuenta está desactivada; no se puede editar.";

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public StaffAccountDto edit(EditStaffAccountCommand command) {
        StaffAccount account = staffAccountRepository
                .lockByUsername(StaffAccount.normalizeUsername(command.username()))
                .orElseThrow(StaffAccountNotFoundException::new);
        if (!account.isActive()) {
            throw new AccountInactiveException(INACTIVE);
        }
        if (!Objects.equals(command.version(), account.getVersion())) {
            throw new ConcurrentStaffUpdateException();
        }
        account.edit(
                command.fullName(),
                command.area(),
                newPasswordHash(command.rawPassword()),
                OffsetDateTime.now(clock),
                command.administratorId());
        return StaffAccountDto.detail(staffAccountRepository.save(account));
    }

    /** Sin contraseña nueva se conserva la actual (esc. 7); una nueva se cifra si cumple FR-004 y el límite de BCrypt. */
    private String newPasswordHash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return null;
        }
        if (rawPassword.length() < StaffAccount.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (!StaffAccount.fitsPasswordLimit(rawPassword)) {
            throw new IllegalArgumentException("La contraseña admite máximo 72 caracteres.");
        }
        return passwordEncoder.encode(rawPassword);
    }
}
