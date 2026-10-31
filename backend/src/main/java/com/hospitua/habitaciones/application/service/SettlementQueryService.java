package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.SettlementResponseDto;
import com.hospitua.habitaciones.application.ports.in.QuerySettlementUseCase;
import com.hospitua.habitaciones.application.ports.out.SettlementRestQueryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Consulta de la liquidación (consultar-liquidacion T012, T024). No es transaccional a propósito: la llamada a Módulo 3
 * puede tardar hasta 3 s y no debe retener una conexión de la base; tampoco escribe nada (T017, T021). El log lleva la
 * reserva, la fuente, el estado y la latencia, nunca montos (T018).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementQueryService implements QuerySettlementUseCase {

    private final StayRepositoryPort stayRepository;
    private final SettlementRestQueryPort settlementPort;
    private final Clock clock;

    @Override
    public SettlementResponseDto querySettlement(UUID stayId) {
        Stay stay =
                stayRepository.findById(stayId).filter(Stay::isActive).orElseThrow(ActiveStayNotFoundException::new);
        long start = System.nanoTime();
        SettlementResponseDto response;
        try {
            response = SettlementResponseDto.available(
                    stay, settlementPort.querySettlement(SettlementRequest.of(stay)), LocalDate.now(clock));
        } catch (ExternalModule3UnavailableException ex) {
            response = SettlementResponseDto.unavailable(ex.unavailableCause());
        }
        log.info(
                "Liquidación de la reserva {} (fuente {}): {} en {} ms",
                stay.getReservationRef(),
                stay.getSource(),
                response.status(),
                (System.nanoTime() - start) / 1_000_000);
        return response;
    }
}
