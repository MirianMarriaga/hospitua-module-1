package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto;
import com.hospitua.habitaciones.infrastructure.config.ClockConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Consulta del historial de estados (consultar-historial T002); solo la usa {@code RoomStateHistoryRepositoryAdapter}.
 * Une cada periodo con su habitación y, con unión externa, con el usuario de {@code actor_id}, en una sola consulta
 * paginada en la base y otra de conteo.
 */
@Repository
@RequiredArgsConstructor
public class RoomStateHistoryQueryRepository {

    private static final String FROM = " FROM RoomStateHistoryJpaEntity h JOIN RoomJpaEntity r ON r.id = h.roomId";

    private static final String SELECT_ROWS =
            "SELECT new com.hospitua.habitaciones.application.dto.RoomStateHistoryRowDto("
                    + "h.roomId, r.roomNumber, r.categoryRoom, h.status, h.startDateTime, h.endDateTime, u.fullName,"
                    + " h.sourceFlow)"
                    + FROM
                    + " LEFT JOIN UserAccountJpaEntity u ON u.username = h.actorId";

    private static final String ORDER_BY =
            " ORDER BY h.startDateTime, r.roomNumber, h.endDateTime ASC NULLS LAST, h.id";

    private final EntityManager entityManager;

    public PagedResultDto<RoomStateHistoryRowDto> search(RoomStateHistoryFilter filter, int page, int size) {
        Map<String, Object> parameters = new HashMap<>();
        String where = where(filter, parameters);

        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(h)" + FROM + where, Long.class);
        parameters.forEach(count::setParameter);
        long totalElements = count.getSingleResult();

        TypedQuery<RoomStateHistoryRowDto> rows =
                entityManager.createQuery(SELECT_ROWS + where + ORDER_BY, RoomStateHistoryRowDto.class);
        parameters.forEach(rows::setParameter);
        List<RoomStateHistoryRowDto> items =
                rows.setFirstResult((page - 1) * size).setMaxResults(size).getResultList();

        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PagedResultDto<>(items, page, size, totalElements, totalPages);
    }

    /**
     * Condiciones de los filtros presentes. El rango toma días completos en Colombia: un periodo se cruza con él si
     * empieza antes del fin de "Hasta" y termina después del inicio de "Desde" o sigue abierto.
     */
    private static String where(RoomStateHistoryFilter filter, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (filter.roomId() != null) {
            conditions.add("h.roomId = :roomId");
            parameters.put("roomId", filter.roomId());
        }
        if (filter.roomNumber() != null) {
            conditions.add("r.roomNumber = :roomNumber");
            parameters.put("roomNumber", filter.roomNumber());
        }
        if (filter.categoryRoom() != null) {
            conditions.add("r.categoryRoom = :categoryRoom");
            parameters.put("categoryRoom", filter.categoryRoom());
        }
        if (filter.status() != null) {
            conditions.add("h.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.from() != null) {
            conditions.add("(h.endDateTime IS NULL OR h.endDateTime > :fromStart)");
            parameters.put("fromStart", startOf(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add("h.startDateTime < :toEnd");
            parameters.put("toEnd", startOf(filter.to().plusDays(1)));
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(ClockConfig.ZONE_BOGOTA).toOffsetDateTime();
    }
}
