package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.ActiveStayResponseDto;
import com.hospitua.habitaciones.application.ports.in.GetActiveStayUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.CheckOutEligibility;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paso 1 del Check-Out, sin escrituras y solo con datos locales (plan-registrar-check-out.md T009; FR-003): la estancia
 * del par, la habitación y sus huéspedes, con las reglas de {@link CheckOutEligibility}.
 */
@Service
@RequiredArgsConstructor
public class ActiveStayQueryService implements GetActiveStayUseCase {

    private final StayRepositoryPort stayRepository;
    private final RoomRepositoryPort roomRepository;
    private final RoomGuestRepositoryPort roomGuestRepository;

    @Override
    @Transactional(readOnly = true)
    public ActiveStayResponseDto getActiveStay(String reservationRef, UUID roomId) {
        CheckOutEligibility eligibility = CheckOutEligibility.verify(
                reservationRef,
                roomId,
                stayRepository
                        .findByReservationRefAndRoomId(reservationRef, roomId)
                        .orElse(null),
                roomRepository.findById(roomId).orElse(null));
        return ActiveStayResponseDto.of(
                eligibility, roomGuestRepository.findByStayId(eligibility.stay().getId()));
    }
}
