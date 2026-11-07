package com.hospitua.habitaciones.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.hospitua.habitaciones.application.ports.in.CheckRoomReservationConflictsUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.out.Module2ReservationConflictClientPort;
import com.hospitua.habitaciones.application.service.CompleteCleaningService;
import com.hospitua.habitaciones.application.service.DailyReservationIngestionService;
import com.hospitua.habitaciones.application.service.DecommissionRoomService;
import com.hospitua.habitaciones.application.service.MarkRoomReservedService;
import com.hospitua.habitaciones.application.service.RoomReservationConflictService;
import com.hospitua.habitaciones.application.service.RoomStateTransitionService;
import com.hospitua.habitaciones.application.service.ScheduleTechnicalBlockService;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module2ReservationConflictRestAdapter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.LocalDate;
import org.springframework.web.client.RestClient;

/**
 * Verificaciones transversales de los planes que se pueden automatizar (L38): quién aparta habitaciones, quién consulta
 * a Módulo 2 y que las fechas no lleven hora. Las que no se pueden expresar como regla quedan en las notas de cada plan.
 */
@AnalyzeClasses(packages = "com.hospitua.habitaciones", importOptions = ImportOption.DoNotIncludeTests.class)
class CrossFlowRulesTest {

    /**
     * plan-marcar-habitacion-reservada.md T016 (FR-001, FR-009; SC-005): solo la ingesta de reservas y el fin de
     * limpieza apartan habitaciones, a través de su puerto. Ningún controlador, ni el registro, la edición o la
     * reactivación, lo usa.
     */
    @ArchTest
    static final ArchRule onlyIngestionAndCleaningReserveRooms = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(DailyReservationIngestionService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(CompleteCleaningService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(MarkRoomReservedService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(MarkRoomReservedUseCase.class.getName())
            .should()
            .dependOnClassesThat()
            .areAssignableTo(MarkRoomReservedUseCase.class)
            .because("solo la ingesta y el fin de limpieza apartan habitaciones (marcar-reservada T016)");

    /**
     * plan-marcar-habitacion-reservada.md T016: la referencia de la reserva solo se asigna en la transición
     * centralizada, que es la que usa el puerto de apartar.
     */
    @ArchTest
    static final ArchRule onlyTheTransitionReservesTheRoom = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(RoomStateTransitionService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(Room.class.getName())
            .should()
            .callMethod(Room.class, "reserveFor", String.class)
            .because("Reserved solo se asigna en la transición centralizada (marcar-reservada T016)");

    /**
     * plan-marcar-habitacion-reservada.md T017 (FR-002), plan-registrar-check-in.md T028 y
     * plan-consultar-panel-recepcion.md T026: solo la baja y la programación de bloqueos técnicos consultan a Módulo 2.
     * Ningún trabajo programado, ni el Check-In, ni el panel de Recepción lo hacen.
     */
    @ArchTest
    static final ArchRule onlyDecommissionAndTechnicalBlocksAskModule2 = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(DecommissionRoomService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(ScheduleTechnicalBlockService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(RoomReservationConflictService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(CheckRoomReservationConflictsUseCase.class.getName())
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CheckRoomReservationConflictsUseCase.class)
            .because("solo la baja y los bloqueos técnicos consultan reservas a Módulo 2");

    /** Igual que la anterior, para el puerto de salida: solo lo usa el servicio de conflictos. */
    @ArchTest
    static final ArchRule onlyTheConflictServiceUsesTheModule2Client = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(RoomReservationConflictService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(Module2ReservationConflictRestAdapter.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(Module2ReservationConflictClientPort.class.getName())
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Module2ReservationConflictClientPort.class)
            .because("las consultas a Módulo 2 pasan por CheckRoomReservationConflictsUseCase");

    /** Las llamadas REST salen solo de los adaptadores REST y de su configuración. */
    @ArchTest
    static final ArchRule restCallsOnlyFromRestAdapters = noClasses()
            .that()
            .resideOutsideOfPackages("..adapters.out.rest..", "..infrastructure.config..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(RestClient.class)
            .because("ningún flujo llama por REST a otro módulo fuera de sus adaptadores");

    /**
     * plan-consultar-panel-recepcion.md T027 y plan-registrar-check-out.md T019: un campo de fecha ({@code …Date}) no
     * lleva hora; los instantes se llaman {@code …DateTime}. Los parámetros de consulta que llegan como texto
     * ({@code …Query} de la API) se convierten a {@code LocalDate} antes de usarlos.
     */
    @ArchTest
    static final ArchRule datesHaveNoTime = fields().that()
            .haveNameMatching(".*Date")
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(".*\\.adapters\\.in\\.web\\.dto\\..*Query")
            .should()
            .haveRawType(LocalDate.class)
            .because("las fechas expuestas no llevan hora (panel-recepcion T027)");
}
