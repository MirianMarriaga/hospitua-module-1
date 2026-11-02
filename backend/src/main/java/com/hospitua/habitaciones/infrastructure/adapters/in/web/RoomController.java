package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.ReactivationResultDto;
import com.hospitua.habitaciones.application.dto.RoomDecommissionDto;
import com.hospitua.habitaciones.application.dto.RoomDecommissionInfoDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.in.DecommissionRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.GetRoomDecommissionUseCase;
import com.hospitua.habitaciones.application.ports.in.ReactivateRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.DecommissionRoomRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.ReactivateRoomRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterRoomRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RoomInventoryQuery;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de habitaciones: registro (plan-registrar-habitacion.md) e inventario
 * (plan-consultar-inventario-habitaciones.md). Las vistas usan {@code /inventory} (incluye las Inactive); Módulo 2 usa
 * {@code /api/rooms} y {@code /api/rooms/{roomId}}, solo con habitaciones vendibles. La baja es de
 * plan-dar-de-baja-habitacion.md y la reactivación de plan-marcar-habitacion-como-disponible.md; la edición se agrega
 * en su plan.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RegisterRoomUseCase registerRoomUseCase;
    private final ConsultRoomInventoryUseCase consultRoomInventoryUseCase;
    private final DecommissionRoomUseCase decommissionRoomUseCase;
    private final GetRoomDecommissionUseCase getRoomDecommissionUseCase;
    private final ReactivateRoomUseCase reactivateRoomUseCase;
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

    /**
     * Dar de baja: verifica con Módulo 2 que no haya reservas vigentes y deja la habitación Inactive (200). "Reintentar"
     * repite la misma petición (FR-009).
     */
    @PostMapping("/{roomId}/decommission")
    public RoomDecommissionDto decommission(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody DecommissionRoomRequest request) {
        String administratorId = currentUserResolver.resolve(xUser);
        return decommissionRoomUseCase.decommission(request.toCommand(roomId, administratorId));
    }

    /** Confirmación de la reactivación: la habitación Inactive y su baja vigente (FR-009). */
    @GetMapping("/{roomId}/decommission")
    public RoomDecommissionInfoDto currentDecommission(@PathVariable UUID roomId) {
        return getRoomDecommissionUseCase.getCurrent(roomId);
    }

    /** Reactivar: revierte la baja indicada y deja la habitación Available; un reintento no cambia nada (FR-010). */
    @PostMapping("/{roomId}/reactivation")
    public ReactivationResultDto reactivate(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody ReactivateRoomRequest request) {
        String administratorId = currentUserResolver.resolve(xUser);
        return reactivateRoomUseCase.reactivate(request.toCommand(roomId, administratorId));
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

    /** Módulo 2: habitaciones vendibles de la categoría, sin paginar; nunca incluye las Inactive (FR-010). */
    @GetMapping
    public List<Module2RoomDto> sellableByCategory(@RequestParam RoomCategory categoryRoom) {
        return consultRoomInventoryUseCase.listSellableByCategory(categoryRoom);
    }

    /** Módulo 2: una habitación vendible; 404 ROOM_NOT_FOUND si no existe o está Inactive (FR-011). */
    @GetMapping("/{roomId}")
    public Module2RoomDto sellableById(@PathVariable UUID roomId) {
        return consultRoomInventoryUseCase.getSellableById(roomId);
    }
}
