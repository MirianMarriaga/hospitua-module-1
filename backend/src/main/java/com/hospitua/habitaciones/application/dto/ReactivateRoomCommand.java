package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/** Reactivación de una habitación: la baja que se mostró en la confirmación y el Administrador actual (FR-010). */
public record ReactivateRoomCommand(UUID roomId, UUID decommissionId, String administratorId) {}
