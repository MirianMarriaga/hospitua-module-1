package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato con Módulo 2 sobre la aplicación completa y PostgreSQL real (plan-consultar-inventario-habitaciones.md
 * T009; FR-010, FR-011; SC-003). Usa las habitaciones Inactive de la semilla V2: la 104 (DOBLE) y la 204 (SUITE).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Module2RoomInventoryContractTest {

    private static final String INACTIVE_DOBLE_ID = "a0000000-0000-0000-0000-000000000104";
    private static final String AVAILABLE_DOBLE_ID = "a0000000-0000-0000-0000-000000000202";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("T009: GET /api/rooms?categoryRoom=DOBLE no incluye la 104 Inactive y solo trae los 4 campos")
    void listByCategory_excludesInactive() throws Exception {
        mockMvc.perform(get("/api/rooms").param("categoryRoom", "DOBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(INACTIVE_DOBLE_ID))))
                .andExpect(jsonPath("$[*].id", hasItem(AVAILABLE_DOBLE_ID)))
                .andExpect(jsonPath("$[*].categoryRoom", everyItem(is("DOBLE"))))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].baseRate").doesNotExist());
    }

    @Test
    @DisplayName("T009: la 104 Inactive responde 404 a Módulo 2 y 200 en el inventario interno")
    void byId_inactiveIsHiddenOnlyFromModule2() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}", INACTIVE_DOBLE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(get("/api/rooms/inventory/{roomId}", INACTIVE_DOBLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"));
        mockMvc.perform(get("/api/rooms/{roomId}", AVAILABLE_DOBLE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("202"));
    }

    @Test
    @DisplayName("T009: sin categoryRoom o con una categoría inválida responde 400 VALIDATION_ERROR")
    void listByCategory_invalidCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/rooms").param("categoryRoom", "PENTHOUSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
