package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.CheckInValidationDto;
import com.hospitua.habitaciones.application.ports.in.ValidateCheckInReservationUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.CheckInEligibility;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paso 1 del Check-In, sin escrituras (plan-registrar-check-in.md T015). Lee la copia local con
 * {@link DailyReservationRepositoryPort}, el mismo puerto que la ingesta, y aplica las reglas de
 * {@link CheckInEligibility}; "hoy" sale del reloj de Bogotá (FR-005).
 */
@Service
@RequiredArgsConstructor
public class CheckInValidationService implements ValidateCheckInReservationUseCase {

    private final DailyReservationRepositoryPort reservationRepository;
    private final StayRepositoryPort stayRepository;
    private final RoomRepositoryPort roomRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CheckInValidationDto validate(String reservationRef, UUID roomId) {
        CheckInEligibility eligibility = CheckInEligibility.verify(
                reservationRef,
                roomId,
                reservationRepository.findByReservationRef(reservationRef).orElse(null),
                roomRepository.findById(roomId).orElse(null),
                stayRepository.existsByReservationRefAndRoomId(reservationRef, roomId),
                LocalDate.now(clock));
        return CheckInValidationDto.of(eligibility);
    }
}
