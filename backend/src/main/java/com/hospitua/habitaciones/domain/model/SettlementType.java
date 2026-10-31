package com.hospitua.habitaciones.domain.model;

/**
 * Tipo de liquidación de Módulo 3. Durante el check-out siempre es {@code INFORMATIVE}: la {@code FINAL} y la factura
 * las genera Módulo 3 al procesar el evento de check-out, que se publica después.
 */
public enum SettlementType {
    INFORMATIVE,
    FINAL
}
