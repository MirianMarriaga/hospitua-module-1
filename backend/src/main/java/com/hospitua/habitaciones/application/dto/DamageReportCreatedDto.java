package com.hospitua.habitaciones.application.dto;

import java.util.UUID;

/** Respuesta 201 de {@code POST /api/rooms/{roomId}/damage-reports}: el diálogo solo muestra el éxito. */
public record DamageReportCreatedDto(UUID damageReportId) {}
