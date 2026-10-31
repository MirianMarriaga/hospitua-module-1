package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.SettlementResponseDto;
import java.util.UUID;

/** Liquidación informativa de una estancia activa para los pasos 2 y 3 del Check-Out (consultar-liquidacion T005). */
public interface QuerySettlementUseCase {

    /**
     * Consulta a Módulo 3 sin escribir nada. Una falla de Módulo 3 no es un error: devuelve {@code UNAVAILABLE} con su
     * causa.
     *
     * @throws com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException si la estancia no existe o ya
     *     está cerrada
     */
    SettlementResponseDto querySettlement(UUID stayId);
}
