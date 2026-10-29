package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.CurrentUserRequiredException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve el encabezado X-User a un usuario actual validado (T020, regla 14).
 *
 * <p>Si el encabezado falta, está vacío, el usuario no existe o la cuenta está desactivada,
 * lanza {@link CurrentUserRequiredException} (400 CURRENT_USER_REQUIRED).</p>
 *
 * <p>Las consultas de Módulo 2 y Módulo 3 no envían X-User; los controllers que no
 * requieren usuario actual simplemente no invocan este componente.</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final StaffAccountRepositoryPort staffAccountRepository;

    /**
     * Resuelve y valida el valor del encabezado X-User.
     *
     * @param xUser Valor del encabezado X-User (puede ser null o vacío).
     * @return El username normalizado de la cuenta activa.
     * @throws CurrentUserRequiredException si falta, no existe o está desactivada.
     */
    public String resolve(String xUser) {
        if (xUser == null || xUser.isBlank()) {
            throw new CurrentUserRequiredException();
        }

        String normalizedUsername = StaffAccount.normalizeUsername(xUser);

        StaffAccount account = staffAccountRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(CurrentUserRequiredException::new);

        if (!account.isActive()) {
            throw new CurrentUserRequiredException();
        }

        return account.getUsername();
    }
}
