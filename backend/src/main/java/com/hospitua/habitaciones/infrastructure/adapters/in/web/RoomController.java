package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterRoomRequest;
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
 * Controlador REST de habitaciones: registro (plan-registrar-habitacion.md). El inventario, la edición y la baja se
 * agregan en sus planes.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RegisterRoomUseCase registerRoomUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Registra una habitación en Available y abre su primer periodo en el historial (201). */
    @PostMapping
    public ResponseEntity<RoomDto> register(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody RegisterRoomRequest request) {
        String administratorId = currentUserResolver.resolve(xUser);
        RoomDto room = registerRoomUseCase.register(request.toCommand(administratorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }
}
