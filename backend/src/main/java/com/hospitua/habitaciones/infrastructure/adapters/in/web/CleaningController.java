package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.CleaningPanelDto;
import com.hospitua.habitaciones.application.dto.CleaningTaskStartedDto;
import com.hospitua.habitaciones.application.ports.in.CompleteCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.GetCleaningPanelUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskUseCase;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.CompleteCleaningRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.StartCleaningRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del Personal de limpieza (plan-marcar-habitacion-en-limpieza.md T004, T013; plan-consultar-panel-limpieza.md
 * T008; plan-confirmar-fin-de-limpieza.md T018).
 */
@RestController
@RequestMapping("/api/cleaning")
@RequiredArgsConstructor
public class CleaningController {

    private final StartCleaningUseCase startCleaningUseCase;
    private final GetCleaningPanelUseCase getCleaningPanelUseCase;
    private final CompleteCleaningUseCase completeCleaningUseCase;
    private final ReleaseCleaningTaskUseCase releaseCleaningTaskUseCase;
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

    /** Panel del usuario actual: su tarea activa o las habitaciones que puede atender (FR-001). */
    @GetMapping("/panel")
    public CleaningPanelDto panel(@RequestHeader(value = "X-User", required = false) String xUser) {
        return getCleaningPanelUseCase.getPanel(currentUserResolver.resolve(xUser));
    }

    /** Confirmar fin: el cuerpo con el daño es opcional; responde 204 y el frontend vuelve al panel. */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeCleaning(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody(required = false) CompleteCleaningRequest request) {
        String memberId = currentUserResolver.resolve(xUser);
        completeCleaningUseCase.completeCleaning(CompleteCleaningRequest.toCommand(request, taskId, memberId));
        return ResponseEntity.noContent().build();
    }

    /** Liberar tarea: la habitación vuelve a pendiente de limpieza; responde 204. */
    @PostMapping("/tasks/{taskId}/release")
    public ResponseEntity<Void> releaseTask(
            @PathVariable UUID taskId, @RequestHeader(value = "X-User", required = false) String xUser) {
        releaseCleaningTaskUseCase.releaseTask(taskId, currentUserResolver.resolve(xUser));
        return ResponseEntity.noContent().build();
    }
}
