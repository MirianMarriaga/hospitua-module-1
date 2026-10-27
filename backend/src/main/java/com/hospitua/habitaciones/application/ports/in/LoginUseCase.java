package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.domain.model.StaffAccount;

/**
 * Puerto de entrada para el inicio de sesión del personal (T020, regla 14).
 */
public interface LoginUseCase {

    /**
     * Valida las credenciales y devuelve la cuenta si son correctas y la cuenta está activa.
     *
     * @param username     Usuario en texto plano (se normaliza internamente).
     * @param rawPassword  Contraseña en texto plano.
     * @return La cuenta del personal autenticada.
     * @throws com.hospitua.habitaciones.domain.exception.InvalidCredentialsException si el usuario no existe,
     *         la contraseña es incorrecta o la cuenta está desactivada.
     */
    StaffAccount login(String username, String rawPassword);
}
