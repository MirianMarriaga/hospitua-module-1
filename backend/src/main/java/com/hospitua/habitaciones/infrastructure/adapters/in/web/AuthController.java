package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.ports.in.LoginUseCase;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.LoginRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el inicio de sesión del personal (T020, regla 14).
 * Endpoint: POST /api/auth/login
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    /**
     * Inicia sesión y devuelve el usuario, nombre completo y área.
     *
     * @param request Credenciales (username, password).
     * @return 200 con username, fullName y area.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        StaffAccount account = loginUseCase.login(request.username(), request.password());
        LoginResponse response = new LoginResponse(
                account.getUsername(), account.getFullName(), account.getArea().name());
        return ResponseEntity.ok(response);
    }
}
