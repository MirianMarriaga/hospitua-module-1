package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.infrastructure.config.IntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Llaves de Módulo 2 y Módulo 3 activadas por sus variables de entorno, como en el despliegue (plan de integración,
 * Fase 1): cada ruta exige la llave de su cliente y las rutas del frontend siguen igual.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {"INTEGRATION_MODULE3_KEY=llave-de-prueba-m3", "INTEGRATION_MODULE2_KEY=llave-de-prueba-m2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationKeyIntegrationTest {

    /** Habitación 105 de la semilla. */
    private static final String SUITE_105 = "a0000000-0000-0000-0000-000000000105";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("La tarifa base responde 401 sin llave y 200 con la llave de Módulo 3")
    void baseRateRequiresModule3Key() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105).param("date", "2026-11-20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INTEGRATION_UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/rooms/" + SUITE_105 + "/base-rate"));

        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105)
                        .param("date", "2026-11-20")
                        .header(IntegrationProperties.KEY_HEADER, "llave-de-prueba-m3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseRate").value("320000.00"));
    }

    @Test
    @DisplayName("Las consultas de Módulo 2 exigen su llave, y la de Módulo 3 no les sirve")
    void module2RoutesRequireModule2Key() throws Exception {
        for (String path : new String[] {
            "/api/rooms?categoryRoom=SUITE",
            "/api/rooms/" + SUITE_105,
            "/api/rooms/" + SUITE_105 + "/maintenances?startDate=2026-11-20&endDate=2026-11-22"
        }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header(IntegrationProperties.KEY_HEADER, "llave-de-prueba-m3"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header(IntegrationProperties.KEY_HEADER, "llave-de-prueba-m2"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Las rutas del frontend no piden llave (inventario y detalle para el Administrador)")
    void frontendRoutesStayOpen() throws Exception {
        mockMvc.perform(get("/api/rooms/inventory")).andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/inventory/{roomId}", SUITE_105)).andExpect(status().isOk());
    }
}
