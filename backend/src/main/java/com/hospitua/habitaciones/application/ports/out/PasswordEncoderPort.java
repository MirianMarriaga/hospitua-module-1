package com.hospitua.habitaciones.application.ports.out;

/**
 * Puerto de salida para el cifrado y verificación de contraseñas (T022).
 */
public interface PasswordEncoderPort {

    /**
     * Cifra una contraseña en texto plano.
     */
    String encode(String raw);

    /**
     * Comprueba si la contraseña en texto plano coincide con el hash almacenado.
     */
    boolean matches(String raw, String hash);
}
