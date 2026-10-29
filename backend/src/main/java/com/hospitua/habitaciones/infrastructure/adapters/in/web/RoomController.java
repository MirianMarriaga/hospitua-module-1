package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterRoomRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RoomInventoryQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de habitaciones: registro (plan-registrar-habitacion.md) e inventario
 * (plan-consultar-inventario-habitaciones.md). La edición y la baja se agregan en sus planes.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RegisterRoomUseCase registerRoomUseCase;
    private final ConsultRoomInventoryUseCase consultRoomInventoryUseCase;
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

    /** Inventario de las vistas del Gerente y del Administrador, con filtros, orden y paginación (incluye Inactive). */
    @GetMapping("/inventory")
    public PagedResultDto<RoomDto> inventory(@Valid @ModelAttribute RoomInventoryQuery query) {
        return consultRoomInventoryUseCase.search(query.toFilter(), query.pageOrDefault(), query.sizeOrDefault());
    }

    /** Detalle de una habitación con su version, para los formularios de edición y baja. */
    @GetMapping("/inventory/{roomId}")
    public RoomDto inventoryDetail(@PathVariable UUID roomId) {
        return consultRoomInventoryUseCase.getById(roomId);
    }
}
