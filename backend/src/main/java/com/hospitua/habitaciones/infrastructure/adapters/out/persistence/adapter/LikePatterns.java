package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

/** Patrones de {@code LIKE ... ESCAPE '\'} para los buscadores del panel de Recepción. */
final class LikePatterns {

    private LikePatterns() {}

    /** {@code %texto%}, con los comodines del texto escapados para que se compare literal. */
    static String containing(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
