package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.application.ports.in.LoginUseCase;
import com.hospitua.habitaciones.domain.exception.InvalidCredentialsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Pruebas unitarias MockMvc del AuthController con GlobalExceptionHandler (T020).
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private LoginUseCase loginUseCase;
    private Clock fixedClock;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T10:00:00-05:00");

    @BeforeEach
    void setUp() {
        loginUseCase = Mockito.mock(LoginUseCase.class);
        fixedClock = Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("America/Bogota"));
        AuthController controller = new AuthController(loginUseCase);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ApiErrorFactory(fixedClock));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login exitoso devuelve 200 con username, fullName y area")
    void login_success_returns200() throws Exception {
        StaffAccount account = new StaffAccount(
                "jramirez",
                "$2a$10$hashed",
                "Jorge Ramírez",
                StaffArea.LIMPIEZA,
                true,
                NOW,
                "admin",
                NOW,
                "admin",
                null,
                null,
                1);
        when(loginUseCase.login("jramirez", "password123")).thenReturn(account);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jramirez\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jramirez"))
                .andExpect(jsonPath("$.fullName").value("Jorge Ramírez"))
                .andExpect(jsonPath("$.area").value("LIMPIEZA"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login con credenciales inválidas devuelve 401 INVALID_CREDENTIALS")
    void login_invalidCredentials_returns401() throws Exception {
        when(loginUseCase.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jramirez\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
    }

    @Test
    @DisplayName("POST /api/auth/login sin body devuelve 400 VALIDATION_ERROR")
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/login sin username devuelve 400 VALIDATION_ERROR")
    void login_missingUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
