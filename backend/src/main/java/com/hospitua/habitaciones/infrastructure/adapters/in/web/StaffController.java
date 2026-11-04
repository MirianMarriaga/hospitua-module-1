package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.ports.in.DeactivateStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.EditStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.GetStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.ListStaffAccountsUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.EditStaffRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.RegisterStaffRequest;
import com.hospitua.habitaciones.infrastructure.adapters.in.web.dto.StaffListQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección "Personal" del Administrador bajo {@code /api/staff} (plan-gestionar-personal.md T016, T021, T027 y T035).
 * El registro, la edición y la desactivación toman como responsable al usuario actual ({@code X-User}); las consultas
 * no lo usan (regla 14 del plan base).
 */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final RegisterStaffAccountUseCase registerStaffAccountUseCase;
    private final ListStaffAccountsUseCase listStaffAccountsUseCase;
    private final GetStaffAccountUseCase getStaffAccountUseCase;
    private final EditStaffAccountUseCase editStaffAccountUseCase;
    private final DeactivateStaffAccountUseCase deactivateStaffAccountUseCase;
    private final CurrentUserResolver currentUserResolver;

    /** Registrar: responde 201 con la confirmación, sin contraseña (FR-002 a FR-006). */
    @PostMapping
    public ResponseEntity<StaffAccountDto> register(
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody RegisterStaffRequest request) {
        String administratorId = currentUserResolver.resolve(xUser);
        StaffAccountDto account =
                StaffAccountDto.summary(registerStaffAccountUseCase.register(request.toCommand(), administratorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    /** Lista paginada con filtros por área y estado y búsqueda por usuario o nombre (FR-007). */
    @GetMapping
    public PagedResultDto<StaffAccountDto> list(@Valid @ModelAttribute StaffListQuery query) {
        return listStaffAccountsUseCase.list(query.toFilter(), query.pageOrDefault());
    }

    /** Una cuenta con su {@code version}, para editarla o desactivarla (FR-008, FR-009). */
    @GetMapping("/{username}")
    public StaffAccountDto get(@PathVariable String username) {
        return getStaffAccountUseCase.get(username);
    }

    /** Editar: responde 200 con la cuenta y su {@code version} nueva, sin contraseña (FR-006, FR-008). */
    @PatchMapping("/{username}")
    public StaffAccountDto edit(
            @PathVariable String username,
            @RequestHeader(value = "X-User", required = false) String xUser,
            @Valid @RequestBody EditStaffRequest request) {
        String administratorId = currentUserResolver.resolve(xUser);
        return editStaffAccountUseCase.edit(request.toCommand(username, administratorId));
    }

    /**
     * Desactivar: la confirmación la pide la interfaz antes de enviar (esc. 9); responde 200 con {@code active = false}
     * (FR-009).
     */
    @PostMapping("/{username}/deactivation")
    public StaffAccountDto deactivate(
            @PathVariable String username, @RequestHeader(value = "X-User", required = false) String xUser) {
        return deactivateStaffAccountUseCase.deactivate(username, currentUserResolver.resolve(xUser));
    }
}
