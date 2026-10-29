package com.hospitua.habitaciones.domain.model;

import static com.hospitua.habitaciones.domain.model.RoomStatus.Available;
import static com.hospitua.habitaciones.domain.model.RoomStatus.DisabledForRepairs;
import static com.hospitua.habitaciones.domain.model.RoomStatus.InCleaning;
import static com.hospitua.habitaciones.domain.model.RoomStatus.Inactive;
import static com.hospitua.habitaciones.domain.model.RoomStatus.Occupied;
import static com.hospitua.habitaciones.domain.model.RoomStatus.PendingCleaning;
import static com.hospitua.habitaciones.domain.model.RoomStatus.Reserved;
import static com.hospitua.habitaciones.domain.model.RoomStatus.TechnicalBlock;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Matriz de transiciones permitidas de {@code maquina-estados-habitacion.md} (14 transiciones entre 8 estados).
 * La consulta {@link Room#transitionTo(RoomStatus)}; es el único lugar donde se declaran (regla 2).
 */
final class RoomTransitions {

    private static final Map<RoomStatus, Set<RoomStatus>> ALLOWED = new EnumMap<>(Map.of(
            Available, EnumSet.of(Reserved, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive),
            Reserved, EnumSet.of(Available, Occupied),
            Occupied, EnumSet.of(PendingCleaning),
            PendingCleaning, EnumSet.of(InCleaning),
            InCleaning, EnumSet.of(Available, PendingCleaning),
            DisabledForRepairs, EnumSet.of(PendingCleaning),
            TechnicalBlock, EnumSet.of(PendingCleaning),
            Inactive, EnumSet.of(Available)));

    private RoomTransitions() {}

    static boolean allows(RoomStatus from, RoomStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
