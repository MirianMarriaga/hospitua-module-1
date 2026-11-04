package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.DeactivateStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.domain.exception.LastAdministratorException;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Dos desactivaciones simultáneas de las dos únicas cuentas activas de Administración (plan-gestionar-personal.md
 * T031, FR-010). Usa su propio contexto, y con él su propia base, para que las únicas cuentas de Administración sean
 * el {@code admin} del arranque y la que crea la prueba; así no deja sin Administrador a las demás pruebas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.application.name=hospitua-ultimo-administrador")
class DeactivateLastAdministratorIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String OTHER = "lrios";

    @Autowired
    private RegisterStaffAccountUseCase registerStaffAccountUseCase;

    @Autowired
    private DeactivateStaffAccountUseCase deactivateStaffAccountUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName(
            "T031: cada Administrador desactiva al otro a la vez; uno tiene éxito y el otro recibe LAST_ADMINISTRATOR")
    void deactivate_concurrently_keepsOneAdministrator() throws Exception {
        registerStaffAccountUseCase.register(
                new RegisterStaffAccountCommand(OTHER, "Administra2026", "Laura Ríos", StaffArea.ADMINISTRACION),
                ADMIN);
        assertThat(activeAdministrators()).containsExactlyInAnyOrder(ADMIN, OTHER);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (String[] pair : List.of(new String[] {OTHER, ADMIN}, new String[] {ADMIN, OTHER})) {
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return deactivateStaffAccountUseCase.deactivate(pair[0], pair[1]);
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
                    .filteredOn(LastAdministratorException.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(RuntimeException.class::isInstance).hasSize(1);
            assertThat(activeAdministrators()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<String> activeAdministrators() {
        return jdbcTemplate.queryForList(
                "SELECT username FROM user_account WHERE area = 'ADMINISTRACION' AND active", String.class);
    }
}
