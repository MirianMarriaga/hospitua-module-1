package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.ports.in.LoginUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidCredentialsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación que implementa el inicio de sesión (T020, regla 14).
 * Lee la cuenta con StaffAccountRepositoryPort, valida la contraseña con PasswordEncoderPort
 * y rechaza las cuentas con active = false, siempre con el mismo mensaje genérico.
 */
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final StaffAccountRepositoryPort staffAccountRepository;
    private final PasswordEncoderPort passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public StaffAccount login(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            throw new InvalidCredentialsException();
        }

        String normalizedUsername = StaffAccount.normalizeUsername(username);

        StaffAccount account =
                staffAccountRepository.findByUsername(normalizedUsername).orElseThrow(InvalidCredentialsException::new);

        if (!account.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return account;
    }
}
