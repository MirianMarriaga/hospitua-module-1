package com.hospitua.habitaciones.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Liquidación de Módulo 3 tal como llegó (consultar-liquidacion, Estructuras de datos): Módulo 1 no la recalcula ni la
 * redondea. No lleva {@code source}: la fuente se muestra desde {@code Stay.source}. En una reserva directa
 * {@code otaCommissionPercentage} es nulo y {@code otaCommissionAmount} es cero.
 */
public record SettlementSummary(
        SettlementType settlementType,
        BigDecimal lodgingAmount,
        BigDecimal otaCommissionPercentage,
        BigDecimal otaCommissionAmount,
        BigDecimal netIncome) {

    public SettlementSummary {
        Objects.requireNonNull(settlementType, "El tipo de liquidación no puede ser nulo");
        Objects.requireNonNull(lodgingAmount, "El hospedaje no puede ser nulo");
        Objects.requireNonNull(otaCommissionAmount, "La comisión OTA no puede ser nula");
        Objects.requireNonNull(netIncome, "El ingreso neto no puede ser nulo");
    }
}
