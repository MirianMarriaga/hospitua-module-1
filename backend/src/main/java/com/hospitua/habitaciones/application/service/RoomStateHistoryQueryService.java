package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomStateHistoryUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomStateHistoryRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de estados, sin escrituras (consultar-historial T010; FR-002 a FR-006, FR-008). {@code REPEATABLE_READ}
 * lee una instantánea en PostgreSQL: la página y sus totales son coherentes y no bloquean las transiciones.
 */
@Service
@RequiredArgsConstructor
public class RoomStateHistoryQueryService implements ConsultRoomStateHistoryUseCase {

    private final RoomStateHistoryRepositoryPort historyRepository;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PagedResultDto<RoomStateHistoryRowDto> search(RoomStateHistoryFilter filter, int page) {
        if (filter.fromAfterTo()) {
            throw InvalidDateRangeException.fromAfterTo();
        }
        return historyRepository.search(filter, page);
    }
}
