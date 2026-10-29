package com.hospitua.habitaciones.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.hospitua.habitaciones.application.service.RoomStateHistoryRecorder;
import com.hospitua.habitaciones.application.service.RoomStateTransitionService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Reglas de arquitectura del plan base que el compilador no garantiza (regla 1, arquitectura hexagonal, y regla 8).
 *
 * <p>{@code lombok..} se admite en todas las capas: Lombok es una dependencia aprobada del plan base y solo deja en el
 * bytecode la marca {@code @lombok.Generated}, sin dependencias en ejecución.
 */
@AnalyzeClasses(packages = "com.hospitua.habitaciones", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Regla 1: el dominio no depende de ningún framework ni de las otras capas. */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "lombok..", "..domain..");

    /** Regla 1: de Spring, la aplicación solo usa @Service, @Transactional y los eventos de aplicación. */
    @ArchTest
    static final ArchRule applicationUsesOnlyAllowedSpring = classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat(resideInAnyPackage(
                            "java..",
                            "lombok..",
                            "..domain..",
                            "..application..",
                            "org.springframework.stereotype..",
                            "org.springframework.transaction.annotation..",
                            "org.slf4j..")
                    .or(type(ApplicationEventPublisher.class)));

    /** Regla 1: los adaptadores de entrada hablan con los de salida solo a través de puertos. */
    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("..adapters.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapters.out..");

    /** Regla 1 (nombres): los puertos de entrada terminan en UseCase. */
    @ArchTest
    static final ArchRule inputPortsAreUseCases = classes()
            .that()
            .resideInAPackage("..application.ports.in..")
            .should()
            .haveSimpleNameEndingWith("UseCase");

    /** Regla 8: el historial de estados solo se registra desde la transición centralizada. */
    @ArchTest
    static final ArchRule historyIsRecordedOnlyByTransitions = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(RoomStateTransitionService.class.getName())
            .and()
            .doNotHaveFullyQualifiedName(RoomStateHistoryRecorder.class.getName())
            .should()
            .dependOnClassesThat()
            .areAssignableTo(RoomStateHistoryRecorder.class);
}
