package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.CleaningTaskStartedDto;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.StartCleaningRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del Personal de limpieza (plan-marcar-habitacion-en-limpieza.md T004, T013). El panel
 * (plan-consultar-panel-limpieza.md) y la tarea activa (plan-confirmar-fin-de-limpieza.md) agregan aquí los suyos.
 */
@RestController
@RequestMapping("/api/cleaning")
@RequiredArgsConstructor
public class CleaningController {

    private final StartCleaningUseCase startCleaningUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Iniciar limpieza: el miembro es el usuario actual; responde 201 con la tarea creada. */
    @PostMapping("/tasks")
    public ResponseEntity<CleaningTaskStartedDto> startCleaning(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody StartCleaningRequest request) {
        String memberId = currentUserResolver.resolve(xUser);
        CleaningTaskStartedDto task = startCleaningUseCase.startCleaning(request.toCommand(memberId));
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }
}
