package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.EditStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.EditStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ConcurrentStaffUpdateException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code PATCH /api/staff/{username}} sobre la aplicación completa y PostgreSQL real (plan-gestionar-personal.md T026,
 * T027). Cada prueba registra su propia cuenta por el puerto de registro.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditStaffIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String PASSWORD = "Limpieza2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterStaffAccountUseCase registerStaffAccountUseCase;

    @Autowired
    private EditStaffAccountUseCase editStaffAccountUseCase;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T027: cambia área y contraseña; responde 200 con la versión nueva, registra al Administrador y el"
            + " login acepta solo la contraseña nueva; el usuario del cuerpo se ignora")
    void edit_areaAndPassword() throws Exception {
        String username = newAccount();

        edit(
                        username,
                        "{\"version\":0,\"username\":\"otro_usuario\",\"fullName\":\"Jorge Ramírez\","
                                + "\"area\":\"MANTENIMIENTO\",\"password\":\"Mantenimiento2026\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.area").value("MANTENIMIENTO"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(jdbcTemplate.queryForMap(
                        "SELECT updated_by, area, created_by FROM user_account WHERE username = ?", username))
                .containsEntry("updated_by", ADMIN)
                .containsEntry("area", "MANTENIMIENTO")
                .containsEntry("created_by", ADMIN);
        login(username, "Mantenimiento2026").andExpect(status().isOk());
        login(username, PASSWORD).andExpect(status().isUnauthorized());
        assertThat(staffAccountRepository.findByUsername("otro_usuario")).isEmpty();
    }

    @Test
    @DisplayName("T027: con la contraseña vacía solo cambia el nombre y la anterior sigue funcionando")
    void edit_onlyName() throws Exception {
        String username = newAccount();

        edit(username, "{\"version\":0,\"fullName\":\"Jorge A. Ramírez\",\"area\":\"LIMPIEZA\",\"password\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jorge A. Ramírez"));

        login(username, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("T027: una versión vieja responde 409 CONCURRENT_UPDATE con el mensaje de la cuenta")
    void edit_staleVersion() throws Exception {
        String username = newAccount();
        edit(username, body(0, "Primer cambio")).andExpect(status().isOk());

        edit(username, body(0, "Segundo cambio"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"))
                .andExpect(jsonPath("$.message")
                        .value("La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver"
                                + " los datos actuales."));

        assertThat(staffAccountRepository.findByUsername(username).orElseThrow().getFullName())
                .isEqualTo("Primer cambio");
    }

    @Test
    @DisplayName("T027: una cuenta desactivada responde 409 ACCOUNT_INACTIVE y una inexistente 404 STAFF_NOT_FOUND")
    void edit_inactiveAndMissing() throws Exception {
        String username = newAccount();
        StaffAccount account = staffAccountRepository.findByUsername(username).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        staffAccountRepository.save(new StaffAccount(
                username,
                account.getPasswordHash(),
                account.getFullName(),
                account.getArea(),
                false,
                account.getCreatedAt(),
                account.getCreatedBy(),
                now,
                ADMIN,
                now,
                ADMIN,
                account.getVersion()));

        edit(username, body(1, "Jorge"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("La cuenta está desactivada; no se puede editar."));
        edit("nadie_" + UUID.randomUUID().toString().substring(0, 8), body(0, "Nadie"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_FOUND"));
    }

    @Test
    @DisplayName("T027: nombre vacío, área inválida, contraseña corta o de más de 72 bytes, sin versión o sin X-User"
            + " responden 400")
    void edit_invalid() throws Exception {
        String username = newAccount();
        Map<String, String> invalid = Map.of(
                "{\"version\":0,\"fullName\":\"  \",\"area\":\"LIMPIEZA\"}",
                "Completa los campos obligatorios.",
                "{\"version\":0,\"fullName\":\"Jorge\",\"area\":\"COCINA\"}",
                "Selecciona un área válida.",
                "{\"version\":0,\"fullName\":\"Jorge\",\"area\":\"LIMPIEZA\",\"password\":\"1234567\"}",
                "La contraseña debe tener al menos 8 caracteres.",
                "{\"version\":0,\"fullName\":\"Jorge\",\"area\":\"LIMPIEZA\",\"password\":\"a" + "ñ".repeat(36) + "\"}",
                "La contraseña admite máximo 72 caracteres.",
                "{\"fullName\":\"Jorge\",\"area\":\"LIMPIEZA\"}",
                "Completa los campos obligatorios.");
        for (Map.Entry<String, String> request : invalid.entrySet()) {
            edit(username, request.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value(request.getValue()));
        }
        mockMvc.perform(patch("/api/staff/{username}", username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(0, "Jorge")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));

        assertThat(staffAccountRepository.findByUsername(username).orElseThrow().getVersion())
                .isZero();
    }

    @Test
    @DisplayName("T026: dos ediciones simultáneas con la misma versión; la segunda recibe CONCURRENT_UPDATE")
    void edit_concurrently_secondRejected() throws Exception {
        String username = newAccount();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (String name : List.of("Nombre A", "Nombre B")) {
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return editStaffAccountUseCase.edit(
                                new EditStaffAccountCommand(username, 0, name, StaffArea.LIMPIEZA, null, ADMIN));
                    } catch (RuntimeException rejected) {
                        return rejected;
                    }
                };
                results.add(pool.submit(attempt));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes)
                    .filteredOn(ConcurrentStaffUpdateException.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(RuntimeException.class::isInstance).hasSize(1);
            assertThat(staffAccountRepository
                            .findByUsername(username)
                            .orElseThrow()
                            .getVersion())
                    .isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    private String newAccount() {
        String username = "edi_" + UUID.randomUUID().toString().substring(0, 8);
        registerStaffAccountUseCase.register(
                new RegisterStaffAccountCommand(username, PASSWORD, "Jorge Ramírez", StaffArea.LIMPIEZA), ADMIN);
        return username;
    }

    private ResultActions edit(String username, String body) throws Exception {
        return mockMvc.perform(patch("/api/staff/{username}", username)
                .header("X-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private static String body(int version, String fullName) {
        return "{\"version\":" + version + ",\"fullName\":\"" + fullName + "\",\"area\":\"LIMPIEZA\"}";
    }
}
