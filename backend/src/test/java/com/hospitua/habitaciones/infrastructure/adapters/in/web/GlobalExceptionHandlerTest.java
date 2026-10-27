package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneId.of("America/Bogota"));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(fixedClock);

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    @DisplayName("MethodArgumentNotValidException traduce a VALIDATION_ERROR (400) con details.field")
    void shouldHandleMethodArgumentNotValidException() throws Exception {
        mockMvc.perform(post("/test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.field", is("name")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")))
                .andExpect(jsonPath("$.path", is("/test/valid-body")));
    }

    @Test
    @DisplayName("ConstraintViolationException traduce a VALIDATION_ERROR (400) con details.field")
    void shouldHandleConstraintViolationException() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.field", is("roomNumber")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("MissingServletRequestParameterException traduce a VALIDATION_ERROR (400) con details.field")
    void shouldHandleMissingServletRequestParameterException() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.field", is("roomId")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException traduce a VALIDATION_ERROR (400) con details.field")
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        mockMvc.perform(get("/test/type-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.field", is("floor")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException traduce a VALIDATION_ERROR (400) con details.field de Jackson")
    void shouldHandleHttpMessageNotReadableException() throws Exception {
        mockMvc.perform(get("/test/not-readable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.field", is("birthDate")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("RoomNotFoundException traduce a ROOM_NOT_FOUND (404) con mensaje estándar")
    void shouldHandleRoomNotFoundException() throws Exception {
        mockMvc.perform(get("/test/room-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("ROOM_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("No se encontró la habitación.")))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("InvalidRoomTransitionException traduce a ROOM_INVALID_STATE (409) con details.currentStatus")
    void shouldHandleInvalidRoomTransitionException() throws Exception {
        mockMvc.perform(get("/test/invalid-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ROOM_INVALID_STATE")))
                .andExpect(jsonPath("$.details.currentStatus", is("Occupied")))
                .andExpect(jsonPath("$.message", is("La habitación está Ocupada; ahora no se puede limpiar.")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException traduce a CONCURRENT_UPDATE (409) con mensaje estándar")
    void shouldHandleOptimisticLockingFailureException() throws Exception {
        mockMvc.perform(get("/test/concurrent-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("CONCURRENT_UPDATE")))
                .andExpect(jsonPath("$.message", is("La habitación acaba de cambiar. Vuelve a intentarlo.")))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("TaskNotOwnedException traduce a TASK_NOT_OWNED (403)")
    void shouldHandleTaskNotOwnedException() throws Exception {
        mockMvc.perform(get("/test/task-not-owned"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("TASK_NOT_OWNED")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")))
                .andExpect(jsonPath("$.path", is("/test/task-not-owned")));
    }

    @Test
    @DisplayName("TaskNotFoundException traduce a TASK_NOT_FOUND (404)")
    void shouldHandleTaskNotFoundException() throws Exception {
        mockMvc.perform(get("/test/task-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("TASK_NOT_FOUND")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("TaskAlreadyClosedException traduce a TASK_ALREADY_CLOSED (409)")
    void shouldHandleTaskAlreadyClosedException() throws Exception {
        mockMvc.perform(get("/test/task-already-closed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("TASK_ALREADY_CLOSED")))
                .andExpect(jsonPath("$.message", is("Esta tarea ya fue cerrada.")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("ActiveTaskExistsException traduce a ACTIVE_TASK_EXISTS (409)")
    void shouldHandleActiveTaskExistsException() throws Exception {
        mockMvc.perform(get("/test/active-task-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ACTIVE_TASK_EXISTS")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("RoomTaskExistsException traduce a ROOM_TASK_EXISTS (409)")
    void shouldHandleRoomTaskExistsException() throws Exception {
        mockMvc.perform(get("/test/room-task-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ROOM_TASK_EXISTS")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("UsernameAlreadyExistsException traduce a USERNAME_ALREADY_EXISTS (409)")
    void shouldHandleUsernameAlreadyExistsException() throws Exception {
        mockMvc.perform(get("/test/username-already-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("USERNAME_ALREADY_EXISTS")))
                .andExpect(jsonPath("$.message", is("Ya existe una cuenta con el usuario jramirez.")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("Excepción general traduce a INTERNAL_ERROR (500) con cuerpo ApiError y sin exponer detalles")
    void shouldHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode", is("INTERNAL_ERROR")))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    record SampleDto(
            @NotBlank(message = "El nombre no puede estar vacío")
            String name) {}

    public static class FakeJacksonReference {
        private final String fieldName;

        public FakeJacksonReference(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    public static class FakeJacksonException extends RuntimeException {
        private final List<FakeJacksonReference> path;

        public FakeJacksonException(List<FakeJacksonReference> path) {
            this.path = path;
        }

        public List<FakeJacksonReference> getPath() {
            return path;
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/test/valid-body")
        void testValidBody(@Valid @RequestBody SampleDto dto) {}

        @GetMapping("/test/constraint-violation")
        void testConstraintViolation() {
            Path.Node node = Mockito.mock(Path.Node.class);
            Mockito.when(node.getName()).thenReturn("roomNumber");
            Path path = Mockito.mock(Path.class);
            Mockito.when(path.iterator()).thenReturn(List.of(node).iterator());

            ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
            Mockito.when(violation.getMessage()).thenReturn("Número de habitación inválido");
            Mockito.when(violation.getPropertyPath()).thenReturn(path);

            throw new ConstraintViolationException(Set.of(violation));
        }

        @GetMapping("/test/missing-param")
        void testMissingParam() throws Exception {
            throw new MissingServletRequestParameterException("roomId", "String");
        }

        @GetMapping("/test/type-mismatch")
        void testTypeMismatch() {
            throw new MethodArgumentTypeMismatchException("abc", Integer.class, "floor", null, null);
        }

        @GetMapping("/test/not-readable")
        void testNotReadable() {
            FakeJacksonException fakeEx = new FakeJacksonException(List.of(new FakeJacksonReference("birthDate")));
            throw new HttpMessageNotReadableException("JSON parse error", fakeEx, null);
        }

        @GetMapping("/test/room-not-found")
        void testRoomNotFound() {
            throw new RoomNotFoundException();
        }

        @GetMapping("/test/invalid-state")
        void testInvalidState() {
            throw new InvalidRoomTransitionException(
                    RoomStatus.Occupied, "La habitación está Ocupada; ahora no se puede limpiar.");
        }

        @GetMapping("/test/concurrent-update")
        void testConcurrentUpdate() {
            throw new ObjectOptimisticLockingFailureException("Room", "room-123");
        }

        @GetMapping("/test/task-not-owned")
        void testTaskNotOwned() {
            throw new com.hospitua.habitaciones.domain.exception.TaskNotOwnedException();
        }

        @GetMapping("/test/task-not-found")
        void testTaskNotFound() {
            throw new com.hospitua.habitaciones.domain.exception.TaskNotFoundException();
        }

        @GetMapping("/test/task-already-closed")
        void testTaskAlreadyClosed() {
            throw new com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException();
        }

        @GetMapping("/test/active-task-exists")
        void testActiveTaskExists() {
            throw new com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException();
        }

        @GetMapping("/test/room-task-exists")
        void testRoomTaskExists() {
            throw new com.hospitua.habitaciones.domain.exception.RoomTaskExistsException();
        }

        @GetMapping("/test/username-already-exists")
        void testUsernameAlreadyExists() {
            throw new com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException("jramirez");
        }

        @GetMapping("/test/internal-error")
        void testInternalError() {
            throw new RuntimeException("Error inesperado en infraestructura");
        }
    }
}
