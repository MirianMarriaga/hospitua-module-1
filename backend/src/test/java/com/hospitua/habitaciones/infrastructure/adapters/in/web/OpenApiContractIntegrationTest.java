package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * El contrato OpenAPI que publica {@code GET /v3/api-docs} es el versionado en {@code documentos/contrato/openapi.json}
 * (lote L40 del plan de fases del backend). El frontend genera sus tipos desde ese archivo, así que un cambio en un
 * endpoint o un DTO hace fallar esta prueba hasta que se actualice el contrato con {@code -Dopenapi.update=true}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIntegrationTest {

    /** Ruta desde {@code backend/}, donde corre Maven. */
    private static final Path CONTRACT = Path.of("..", "documentos", "contrato", "openapi.json");

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("L40: el contrato publicado coincide con documentos/contrato/openapi.json")
    void publishedContract_matchesVersionedContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ObjectNode published = (ObjectNode) JSON.readTree(body);
        // La URL del servidor depende de cómo se levanta la aplicación; no es parte del contrato.
        published.remove("servers");

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, JSON.writeValueAsString(published) + "\n", StandardCharsets.UTF_8);
        }

        assertThat(CONTRACT)
                .as("Falta el contrato; generarlo con ./mvnw test -Dtest=OpenApiContractIntegrationTest "
                        + "-Dopenapi.update=true")
                .exists();
        JsonNode versioned = JSON.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(published)
                .as(
                        "El contrato cambió. Si el cambio es intencional, regenerarlo con -Dopenapi.update=true, "
                                + "revisar el diff de documentos/contrato/openapi.json y avisar al frontend (npm run api:types)")
                .isEqualTo(versioned);
    }
}
