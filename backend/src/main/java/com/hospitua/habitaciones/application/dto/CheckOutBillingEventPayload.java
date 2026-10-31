package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code payload} del evento de check-out para facturación (plan-registrar-check-out.md Contrato 2; FR-016). El sobre
 * ({@code eventId}, {@code occurredAt}) lo pone {@code CheckOutBillingEventSerializer}. Sin canal, importes ni datos
 * de los huéspedes: la factura va siempre a nombre del titular.
 */
public record CheckOutBillingEventPayload(
        UUID stayId,
        String reservationRef,
        UUID roomId,
        RoomCategory categoryRoom,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BillingCustomer billingCustomer) {

    /** {@code closedStay} ya tiene su {@code checkOutDate}. */
    public static CheckOutBillingEventPayload of(Stay closedStay, RoomCategory categoryRoom) {
        return new CheckOutBillingEventPayload(
                closedStay.getId(),
                closedStay.getReservationRef(),
                closedStay.getRoomId(),
                categoryRoom,
                closedStay.getCheckInDate(),
                closedStay.getCheckOutDate(),
                new BillingCustomer(
                        closedStay.getTitularFirstName() + " " + closedStay.getTitularLastName(),
                        closedStay.getTitularDocumentNumber()));
    }

    public record BillingCustomer(String name, String taxId) {}
}
