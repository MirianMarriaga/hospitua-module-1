package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.SettlementAvailabilityStatus;
import com.hospitua.habitaciones.domain.model.SettlementSummary;
import com.hospitua.habitaciones.domain.model.SettlementType;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Respuesta de {@code GET /api/stays/{stayId}/settlement} (consultar-liquidacion Endpoint 2, "Reglas de composición").
 * Los montos son los de Módulo 3 sin tocar; {@code source} sale de la estancia. En {@code UNAVAILABLE} solo van el
 * estado y la causa.
 */
public record SettlementResponseDto(
        SettlementAvailabilityStatus status,
        SettlementType settlementType,
        ReservationInfo reservationInfo,
        GuestSummary guestSummary,
        StayData stayData,
        String unavailableCause) {

    /** {@code checkOutDate} es hoy: solo sirve para mostrar las noches, que no son un dato financiero. */
    public static SettlementResponseDto available(Stay stay, SettlementSummary summary, LocalDate today) {
        return new SettlementResponseDto(
                SettlementAvailabilityStatus.AVAILABLE,
                summary.settlementType(),
                new ReservationInfo(
                        stay.getSource(),
                        summary.otaCommissionPercentage(),
                        summary.otaCommissionAmount(),
                        summary.netIncome()),
                new GuestSummary(summary.lodgingAmount()),
                new StayData(ChronoUnit.DAYS.between(stay.getCheckInDate(), today), stay.getCheckInDate(), today),
                null);
    }

    public static SettlementResponseDto unavailable(String cause) {
        return new SettlementResponseDto(SettlementAvailabilityStatus.UNAVAILABLE, null, null, null, null, cause);
    }

    /** {@code otaCommissionPercentage} es nulo en una reserva directa ("No aplica"). */
    public record ReservationInfo(
            String source, BigDecimal otaCommissionPercentage, BigDecimal otaCommissionAmount, BigDecimal netIncome) {}

    public record GuestSummary(BigDecimal lodgingAmount) {}

    public record StayData(long nights, LocalDate checkInDate, LocalDate checkOutDate) {}
}
