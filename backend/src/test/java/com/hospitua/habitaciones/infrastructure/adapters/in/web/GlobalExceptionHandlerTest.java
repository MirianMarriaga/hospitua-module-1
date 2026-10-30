package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.domain.exception.ExternalModule2UnavailableException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationConflictException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"), ZoneId.of("America/Bogota"));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ApiErrorFactory(fixedClock));

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
        mockMvc.perform(post("/test/not-readable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"2026-02-31\"}"))
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
    @DisplayName("Excepción general traduce a UNEXPECTED_ERROR (422), nunca 500 (regla 7)")
    void shouldHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode", is("UNEXPECTED_ERROR")))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    @Test
    @DisplayName("CurrentUserRequiredException traduce a CURRENT_USER_REQUIRED (400)")
    void shouldHandleCurrentUserRequiredException() throws Exception {
        mockMvc.perform(get("/test/current-user-required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("CURRENT_USER_REQUIRED")));
    }

    @Test
    @DisplayName("InvalidCredentialsException traduce a INVALID_CREDENTIALS (401)")
    void shouldHandleInvalidCredentialsException() throws Exception {
        mockMvc.perform(get("/test/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("ReservationConflictException traduce a RESERVATION_CONFLICT (409) con details.reservations")
    void shouldHandleReservationConflictException() throws Exception {
        mockMvc.perform(get("/test/reservation-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("RESERVATION_CONFLICT")))
                .andExpect(jsonPath("$.message", is("La habitación tiene reservas vigentes.")))
                .andExpect(jsonPath("$.details.reservations[0].reservationRef", is("RES-10502")))
                .andExpect(jsonPath("$.details.reservations[0].startDate", is("2026-10-24")))
                .andExpect(jsonPath("$.details.reservations[0].endDate", is("2026-10-27")))
                .andExpect(jsonPath("$.details.reservations[0].status", is("ACTIVE")));
    }

    @Test
    @DisplayName("ExternalModule2UnavailableException traduce a MODULE_2_UNAVAILABLE (424, regla \"siempre 4xx\")")
    void shouldHandleExternalModule2UnavailableException() throws Exception {
        mockMvc.perform(get("/test/module2-unavailable"))
                .andExpect(status().is(424))
                .andExpect(jsonPath("$.errorCode", is("MODULE_2_UNAVAILABLE")))
                .andExpect(jsonPath("$.message", is("Servicio de reservas temporalmente no disponible.")))
                .andExpect(jsonPath("$.timestamp", endsWith("-05:00")));
    }

    record SampleDto(
            @NotBlank(message = "El nombre no puede estar vacío")
            String name) {}

    record SampleDateDto(LocalDate birthDate) {}

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

        @PostMapping("/test/not-readable")
        void testNotReadable(@RequestBody SampleDateDto dto) {}

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

        @GetMapping("/test/current-user-required")
        void testCurrentUserRequired() {
            throw new com.hospitua.habitaciones.domain.exception.CurrentUserRequiredException();
        }

        @GetMapping("/test/invalid-credentials")
        void testInvalidCredentials() {
            throw new com.hospitua.habitaciones.domain.exception.InvalidCredentialsException();
        }

        @GetMapping("/test/reservation-conflict")
        void testReservationConflict() {
            throw new ReservationConflictException(
                    "La habitación tiene reservas vigentes.",
                    List.of(new ConflictingReservation(
                            "RES-10502", "ACTIVE", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 27))));
        }

        @GetMapping("/test/module2-unavailable")
        void testModule2Unavailable() {
            throw new ExternalModule2UnavailableException(new ResourceAccessException("timeout"));
        }

        @GetMapping("/test/internal-error")
        void testInternalError() {
            throw new RuntimeException("Error inesperado en infraestructura");
        }
    }
}
