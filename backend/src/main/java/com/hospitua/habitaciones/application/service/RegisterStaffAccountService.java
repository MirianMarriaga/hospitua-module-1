package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación que implementa el registro de cuentas de personal (T022).
 * Normaliza el usuario, valida unicidad, cifra la contraseña y persiste la cuenta activa.
 */
@Service
@RequiredArgsConstructor
public class RegisterStaffAccountService implements RegisterStaffAccountUseCase {

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public StaffAccount register(RegisterStaffAccountCommand command, String createdBy) {
        Objects.requireNonNull(command, "El comando de registro no puede ser nulo");

        String normalizedUsername = StaffAccount.validateAndNormalizeUsername(command.username());

        if (staffAccountRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }

        if (command.rawPassword() == null || command.rawPassword().length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }

        if (command.area() == null) {
            throw new IllegalArgumentException("El área es obligatoria.");
        }

        String passwordHash = passwordEncoder.encode(command.rawPassword());
        OffsetDateTime now = OffsetDateTime.now(clock);

        StaffAccount account = StaffAccount.createNew(
                normalizedUsername, passwordHash, command.fullName(), command.area(), now, createdBy);

        return staffAccountRepository.save(account);
    }
}
