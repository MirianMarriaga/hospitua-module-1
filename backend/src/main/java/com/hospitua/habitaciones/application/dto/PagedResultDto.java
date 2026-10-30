package com.hospitua.habitaciones.application.dto;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados con sus totales; {@code page} empieza en 1. La usan el inventario, el historial y el
 * personal.
 */
public record PagedResultDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PagedResultDto {
        items = List.copyOf(items);
    }

    /** Toma la página {@code page} (desde 1) de una lista ya filtrada y ordenada. */
    public static <T> PagedResultDto<T> of(List<T> all, int page, int size) {
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (all.size() + size - 1) / size;
        return new PagedResultDto<>(all.subList(from, to), page, size, all.size(), totalPages);
    }

    public <R> PagedResultDto<R> map(Function<T, R> mapper) {
        return new PagedResultDto<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
