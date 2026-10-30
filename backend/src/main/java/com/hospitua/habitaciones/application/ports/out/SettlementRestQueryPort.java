package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.SettlementSummary;

/** Consulta de la liquidación a Módulo 3 ({@code GET /api/settlements}; consultar-liquidacion T005, regla 1). */
public interface SettlementRestQueryPort {

    /**
     * Devuelve la liquidación de la estancia sin alterar sus montos.
     *
     * @throws com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException ante cualquier falla de
     *     Módulo 3, con la causa que verá el recepcionista
     */
    SettlementSummary querySettlement(SettlementRequest request);
}
