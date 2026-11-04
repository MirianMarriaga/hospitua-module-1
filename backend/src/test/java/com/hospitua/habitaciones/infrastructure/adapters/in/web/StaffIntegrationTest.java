package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.out.PasswordEncoderPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.jayway.jsonpath.JsonPath;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code /api/staff} sobre la aplicación completa y PostgreSQL real (plan-gestionar-personal.md T010, T012, T016,
 * T018, T019, T021). La base es compartida y ya tiene otras cuentas: cada prueba crea usuarios propios y busca por una
 * marca única en el nombre.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class StaffIntegrationTest {

    private static final String ADMIN = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterStaffAccountUseCase registerStaffAccountUseCase;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private PasswordEncoderPort passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T016 y T012: responde 201 con la cuenta activa y sin contraseña; se guarda cifrada, con el"
            + " responsable, y el login la acepta; la contraseña no aparece en ninguna respuesta ni en los logs")
    void register_createsActiveAccount(CapturedOutput output) throws Exception {
        String username = newUsername();
        String password = "Clave-" + UUID.randomUUID();

        String body = register("{\"username\":\"  " + username.toUpperCase() + " \",\"password\":\"" + password
                        + "\",\"fullName\":\" Jorge Ramírez \",\"area\":\"LIMPIEZA\"}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<Map<String, Object>>read(body, "$"))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("username", username, "fullName", "Jorge Ramírez", "area", "LIMPIEZA", "active", true));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password_hash, active, created_by, created_at FROM user_account WHERE username = ?", username);
        assertThat(row).containsEntry("active", true).containsEntry("created_by", ADMIN);
        assertThat(row.get("created_at")).isNotNull();
        String hash = (String) row.get("password_hash");
        assertThat(hash).isNotEqualTo(password).startsWith("$2");
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String detail = account(username).andReturn().getResponse().getContentAsString();
        String list = list("query=" + username).andReturn().getResponse().getContentAsString();
        assertThat(List.of(body, login, detail, list)).noneMatch(response -> response.contains(password));
        assertThat(output.getAll()).doesNotContain(password);
    }

    @Test
    @DisplayName("T016: un usuario existente, aunque llegue con otras mayúsculas, responde 409 USERNAME_ALREADY_EXISTS")
    void register_duplicate() throws Exception {
        String username = newUsername();
        register(body(username, "Limpieza2026", "Jorge Ramírez", "LIMPIEZA")).andExpect(status().isCreated());

        register(body(username.toUpperCase(), "Otra2026x", "Otro nombre", "RECEPCION"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con el usuario " + username + "."));
    }

    @Test
    @DisplayName("T016: campos faltantes, usuario, contraseña o área inválidos y la falta de X-User responden 400")
    void register_invalid() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Completa los campos obligatorios."))
                .andExpect(jsonPath("$.details.fields.length()").value(4));
        register(body("jorge ramirez", "Limpieza2026", "Jorge", "LIMPIEZA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("username"))
                .andExpect(jsonPath("$.message")
                        .value("El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o"
                                + " guion bajo."));
        register(body(newUsername(), "1234567", "Jorge", "LIMPIEZA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña debe tener al menos 8 caracteres."));
        register(body(newUsername(), "Limpieza2026", "Jorge", "COCINA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecciona un área válida."));
        mockMvc.perform(post("/api/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(newUsername(), "Limpieza2026", "Jorge", "LIMPIEZA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    @Test
    @DisplayName("T010: dos registros simultáneos del mismo usuario; solo uno se guarda y el otro recibe"
            + " USERNAME_ALREADY_EXISTS")
    void register_concurrently_oneWins() throws Exception {
        String username = newUsername();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (String name : List.of("Primera", "Segunda")) {
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return registerStaffAccountUseCase.register(
                                new RegisterStaffAccountCommand(username, "Limpieza2026", name, StaffArea.LIMPIEZA),
                                ADMIN);
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

            assertThat(outcomes).filteredOn(StaffAccount.class::isInstance).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(UsernameAlreadyExistsException.class::isInstance)
                    .hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM user_account WHERE username = ?", Integer.class, username))
                    .isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("T018: sin filtros de estado incluye las desactivadas, ordenadas por nombre y de 10 en 10")
    void list_paginatedByName() throws Exception {
        String mark = newMark();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String name = "Ángela " + mark + " " + (char) ('a' + 11 - i);
            names.add(name);
            // Las dos primeras quedan al final por nombre y están desactivadas
            save(newUsername(), name, i % 2 == 0 ? StaffArea.LIMPIEZA : StaffArea.RECEPCION, i >= 2);
        }
        List<String> sorted = names.stream().sorted().toList();

        String first = list("query=" + mark).andReturn().getResponse().getContentAsString();
        String second =
                list("query=" + mark + "&page=2").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(first, "$.items[*].fullName")).isEqualTo(sorted.subList(0, 10));
        assertThat(JsonPath.<List<String>>read(second, "$.items[*].fullName")).isEqualTo(sorted.subList(10, 12));
        assertThat(JsonPath.<Integer>read(first, "$.totalElements")).isEqualTo(12);
        assertThat(JsonPath.<Integer>read(first, "$.totalPages")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(first, "$.size")).isEqualTo(10);
        assertThat(JsonPath.<List<Boolean>>read(first, "$.items[*].active")).containsOnly(true);
        assertThat(JsonPath.<List<Boolean>>read(second, "$.items[*].active")).containsOnly(false);
        assertThat(JsonPath.<List<Object>>read(first, "$.items[*].version")).isEmpty();
        // Sin filtros también aparecen las cuentas de esta prueba: la lista general incluye todo el personal
        assertThat(JsonPath.<Integer>read(list("").andReturn().getResponse().getContentAsString(), "$.totalElements"))
                .isGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("T019: los filtros por área y estado y la búsqueda por usuario o nombre, sin mayúsculas ni tildes, se"
            + " combinan")
    void list_filtersCombine() throws Exception {
        String mark = newMark();
        String activeCleaner = save(newUsername(), "Jorge Ramírez " + mark, StaffArea.LIMPIEZA, true);
        String inactiveCleaner = save(newUsername(), "Rosa Ramírez " + mark, StaffArea.LIMPIEZA, false);
        String receptionist = save(newUsername(), "Luis Ramirez " + mark, StaffArea.RECEPCION, true);

        assertThat(usernames("query=RAMIREZ " + mark))
                .containsExactlyInAnyOrder(activeCleaner, inactiveCleaner, receptionist);
        assertThat(usernames("query=" + mark + "&area=LIMPIEZA"))
                .containsExactlyInAnyOrder(activeCleaner, inactiveCleaner);
        assertThat(usernames("query=" + mark + "&area=LIMPIEZA&active=true")).containsExactly(activeCleaner);
        assertThat(usernames("query=" + mark + "&active=false")).containsExactly(inactiveCleaner);
        assertThat(usernames("query=" + receptionist.toUpperCase())).containsExactly(receptionist);
        assertThat(usernames("query=" + mark + "&area=GERENCIA")).isEmpty();
    }

    @Test
    @DisplayName("T021: filtros inválidos responden 400 VALIDATION_ERROR con el mensaje de la lista")
    void list_invalidFilters() throws Exception {
        for (String params : List.of("area=COCINA", "active=quizas", "page=0")) {
            list(params)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Revisa los filtros aplicados."));
        }
    }

    @Test
    @DisplayName("T021: una cuenta responde con su versión, sin contraseña; una inexistente 404 STAFF_NOT_FOUND")
    void get_account() throws Exception {
        String username = save(newUsername(), "Marta Gómez", StaffArea.MANTENIMIENTO, true);

        account(username.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Marta Gómez"))
                .andExpect(jsonPath("$.area").value("MANTENIMIENTO"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        account("nadie_" + newMark())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró la cuenta."));
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/staff")
                .header("X-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions list(String params) throws Exception {
        return mockMvc.perform(get("/api/staff?" + params));
    }

    private ResultActions account(String username) throws Exception {
        return mockMvc.perform(get("/api/staff/{username}", username));
    }

    private List<String> usernames(String params) throws Exception {
        return JsonPath.read(
                list(params)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.items[*].username");
    }

    /** Cuenta guardada por el puerto; las desactivadas quedan como las dejaría la desactivación. */
    private String save(String username, String fullName, StaffArea area, boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        staffAccountRepository.save(new StaffAccount(
                username,
                "$2a$10$hash",
                fullName,
                area,
                active,
                now,
                ADMIN,
                now,
                ADMIN,
                active ? null : now,
                active ? null : ADMIN,
                null));
        return username;
    }

    private static String body(String username, String password, String fullName, String area) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"fullName\":\"" + fullName
                + "\",\"area\":\"" + area + "\"}";
    }

    private static String newUsername() {
        return "per_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Marca única (solo letras) que aparece en el nombre de las cuentas de una prueba. */
    private static String newMark() {
        StringBuilder mark = new StringBuilder("zq");
        for (int i = 0; i < 8; i++) {
            mark.append((char) ThreadLocalRandom.current().nextInt('a', 'z' + 1));
        }
        return mark.toString();
    }
}
