package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationMessageLogJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationRoomJpaEntity;
import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la copia local de reservas del dominio y sus entidades JPA.
 */
@Component
public class DailyReservationMapper {

    public DailyReservation toDomain(DailyReservationJpaEntity entity, List<DailyReservationRoomJpaEntity> rooms) {
        return new DailyReservation(
                entity.getReservationRef(),
                entity.getGuestFirstName(),
                entity.getGuestLastName(),
                entity.getGuestDocumentType(),
                entity.getGuestDocumentNumber(),
                entity.getGuestNationality(),
                entity.getSource(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getGuestCount(),
                entity.getUpdatedAt(),
                rooms.stream().map(DailyReservationMapper::toDomain).toList());
    }

    public DailyReservationJpaEntity toEntity(DailyReservation domain) {
        return new DailyReservationJpaEntity(
                domain.reservationRef(),
                domain.guestFirstName(),
                domain.guestLastName(),
                domain.guestDocumentType(),
                domain.guestDocumentNumber(),
                domain.guestNationality(),
                domain.source(),
                domain.startDate(),
                domain.endDate(),
                domain.guestCount(),
                domain.updatedAt());
    }

    public List<DailyReservationRoomJpaEntity> toRoomEntities(DailyReservation domain) {
        return domain.rooms().stream()
                .map(room -> new DailyReservationRoomJpaEntity(
                        domain.reservationRef(),
                        room.roomId(),
                        room.roomNumber(),
                        room.categoryRoom(),
                        room.guestCount()))
                .toList();
    }

    public DailyReservationMessageLogJpaEntity toEntity(DailyReservationMessageLog domain) {
        return new DailyReservationMessageLogJpaEntity(
                domain.messageId(),
                domain.sequenceNumber(),
                domain.operationalDate(),
                toMessageTypeColumn(domain.messageType()),
                domain.reservationRef(),
                domain.receivedAt());
    }

    /**
     * Valor de {@code message_type}: la routing key del mensaje, que es lo que consulta el panel de recepción para saber
     * si llegó la lista del día (plan-consultar-panel-recepcion.md).
     */
    public static String toMessageTypeColumn(DailyReservationMessageType type) {
        return switch (type) {
            case DAILY_LIST -> RabbitConfig.ROUTING_KEY_DAILY_LIST;
            case UPDATE -> RabbitConfig.ROUTING_KEY_DAILY_UPDATE;
        };
    }

    private static DailyReservationRoom toDomain(DailyReservationRoomJpaEntity entity) {
        return new DailyReservationRoom(
                entity.getRoomId(), entity.getRoomNumber(), entity.getCategoryRoom(), entity.getGuestCount());
    }
}
