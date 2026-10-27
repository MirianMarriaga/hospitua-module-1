package com.hospitua.habitaciones.infrastructure.adapters.out.security;

import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de infraestructura para el cifrado y verificación de contraseñas con BCrypt (T022).
 */
@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("La contraseña en texto plano no puede ser nula");
        }
        return delegate.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return delegate.matches(raw, hash);
    }
}
