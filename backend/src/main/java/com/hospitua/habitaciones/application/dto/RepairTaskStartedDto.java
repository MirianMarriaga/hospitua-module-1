package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/** Respuesta 201 de {@code POST /api/maintenance/tasks}: el frontend solo redirige a la vista de tarea activa. */
public record RepairTaskStartedDto(UUID taskId) {}
