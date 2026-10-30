package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.StayJpaEntity;

/** Fila de las consultas de salidas: la estancia, el número y la categoría de su habitación y sus huéspedes. */
public record DepartureRow(StayJpaEntity stay, String roomNumber, RoomCategory categoryRoom, Long guestCount) {}
