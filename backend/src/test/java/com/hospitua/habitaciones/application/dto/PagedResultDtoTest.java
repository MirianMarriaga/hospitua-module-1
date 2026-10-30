package com.hospitua.habitaciones.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PagedResultDtoTest {

    private static final List<Integer> TWENTY_FOUR =
            IntStream.rangeClosed(1, 24).boxed().toList();

    @Test
    @DisplayName("T008: 24 elementos de 10 en 10 dan 3 páginas; la tercera trae los 4 restantes")
    void of_splitsInPagesOfTen() {
        PagedResultDto<Integer> first = PagedResultDto.of(TWENTY_FOUR, 1, 10);
        PagedResultDto<Integer> third = PagedResultDto.of(TWENTY_FOUR, 3, 10);

        assertThat(first.items()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(first.totalElements()).isEqualTo(24);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(third.items()).containsExactly(21, 22, 23, 24);
        assertThat(third.page()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una página posterior a la última y una lista vacía devuelven items vacío sin error")
    void of_outOfRangeOrEmpty_returnsEmptyItems() {
        assertThat(PagedResultDto.of(TWENTY_FOUR, 9, 10).items()).isEmpty();

        PagedResultDto<Integer> empty = PagedResultDto.of(List.of(), 1, 10);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
    }

    @Test
    @DisplayName("map conserva los totales de la página")
    void map_keepsTotals() {
        PagedResultDto<String> mapped = PagedResultDto.of(TWENTY_FOUR, 2, 10).map(String::valueOf);

        assertThat(mapped.items()).first().isEqualTo("11");
        assertThat(mapped)
                .extracting(PagedResultDto::page, PagedResultDto::totalElements, PagedResultDto::totalPages)
                .containsExactly(2, 24L, 3);
    }
}
