package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SourceFlowTest {

    @Test
    @DisplayName("Deben existir exactamente los 12 flujos canónicos de SourceFlow")
    void shouldHaveTwelveSourceFlowValues() {
        assertThat(SourceFlow.values()).hasSize(12);
    }

    @ParameterizedTest
    @EnumSource(SourceFlow.class)
    @DisplayName("Cada flujo debe tener un nombre legible no vacío")
    void eachSourceFlowMustHaveNonBlankDisplayName(SourceFlow flow) {
        assertThat(flow.displayName()).isNotBlank();
    }

    @Test
    @DisplayName("Valores legibles específicos según la tabla de T019")
    void shouldMatchSpecificDisplayNames() {
        assertThat(SourceFlow.REGISTER_ROOM.displayName()).isEqualTo("Registrar habitación");
        assertThat(SourceFlow.MARK_RESERVED.displayName()).isEqualTo("Marcar habitación como reservada");
        assertThat(SourceFlow.MARK_AVAILABLE.displayName()).isEqualTo("Marcar habitación como disponible");
        assertThat(SourceFlow.CHECK_IN.displayName()).isEqualTo("Registrar check-in");
        assertThat(SourceFlow.CHECK_OUT.displayName()).isEqualTo("Registrar check-out");
        assertThat(SourceFlow.START_CLEANING.displayName()).isEqualTo("Marcar habitación en limpieza");
        assertThat(SourceFlow.CONFIRM_CLEANING_END.displayName()).isEqualTo("Confirmar fin de limpieza");
        assertThat(SourceFlow.RELEASE_CLEANING_TASK.displayName())
                .isEqualTo("Confirmar fin de limpieza (liberar tarea)");
        assertThat(SourceFlow.REPORT_DAMAGE.displayName()).isEqualTo("Marcar habitación inhabilitada por reparaciones");
        assertThat(SourceFlow.CONFIRM_REPAIR_END.displayName()).isEqualTo("Confirmar reparación finalizada");
        assertThat(SourceFlow.TECHNICAL_BLOCK.displayName()).isEqualTo("Programar bloqueo técnico para habitación");
        assertThat(SourceFlow.DECOMMISSION_ROOM.displayName()).isEqualTo("Dar de baja habitación");
    }
}
