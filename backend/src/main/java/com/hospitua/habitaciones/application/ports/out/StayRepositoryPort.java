package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.Stay;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia de estancias de huéspedes.
 */
public interface StayRepositoryPort {

    Stay save(Stay stay);

    Optional<Stay> findById(UUID id);
}
