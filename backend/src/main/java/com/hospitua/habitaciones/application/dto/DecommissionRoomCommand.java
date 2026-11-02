package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DecommissionReason;
import java.util.UUID;

/** Datos de una baja (plan-dar-de-baja-habitacion.md): el Administrador es el usuario actual (FR-007). */
public record DecommissionRoomCommand(
        UUID roomId, DecommissionReason reasonCategory, String reasonDetail, String administratorId) {}
