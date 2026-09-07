# Especificación de Funcionalidad: Generar reporte de estados

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Visualización del reporte de estados (Prioridad: P1)

Como Gerente, quiero generar un resumen del inventario de habitaciones agrupado por cada uno de los 7 estados de la máquina de estados, para tener una revisión operativa del momento y tomar decisiones basadas en la disponibilidad general.

**Por qué esta prioridad**: Es fundamental para la dirección del hotel conocer en tiempo real la situación global de su inventario, permitiendo identificar cuellos de botella (ej. muchas habitaciones en mantenimiento o limpieza). No modifica ningún dato ni ejecuta transiciones; es una vista agregada de solo lectura.

**Prueba Independiente**: Puede probarse iniciando sesión como Gerente y accediendo a la sección de reportes para solicitar el reporte de estados, verificando que la información se despliegue con precisión según los datos actuales.

**Escenarios de Aceptación**:

1. **Escenario**: Generación de reporte exitoso sin filtros
   - **Dado** que hay habitaciones en diversos estados en el sistema
   - **Cuando** el Gerente solicita el reporte general de estados
   - **Entonces** el sistema muestra un reporte agrupado por los 7 estados posibles, con el conteo respectivo de habitaciones para cada estado

2. **Escenario**: Estado sin habitaciones
   - **Dado** que en el sistema no hay ninguna habitación en el estado "DisabledForRepairs"
   - **Cuando** el Gerente solicita el reporte
   - **Entonces** el sistema muestra el estado "DisabledForRepairs" con un conteo de cero, asegurando que los 7 estados siempre estén presentes en el reporte

3. **Escenario**: Filtrado por tipo de habitación
   - **Dado** que el Gerente desea ver la distribución de estados solo para las habitaciones tipo "Suite"
   - **Cuando** solicita el reporte indicando el filtro por tipo de habitación
   - **Entonces** el sistema genera el reporte agrupado por los 7 estados contemplando exclusivamente las habitaciones que coinciden con el filtro

---

### Casos Borde

- Reporte generado con inventario vacío (hotel recién configurado): el sistema muestra el reporte con los 7 estados listados y todos los conteos en cero, de forma consistente.
- Generación mientras hay transiciones de estado ocurriendo simultáneamente en otras habitaciones: el reporte toma una foto (snapshot) consistente de la base de datos en el momento exacto de la generación, sin bloquear las otras operaciones en curso ni presentar estados inconsistentes.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor "Manager" generar el reporte de estados.
- **FR-002**: El sistema DEBE presentar la información obligatoriamente agrupada por los 7 estados del ciclo de vida de la habitación (Available, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive) definidos en documentos/SPEC/referencias/maquina-estados-habitacion.md.
- **FR-003**: El sistema DEBE mostrar el conteo de habitaciones correspondiente a cada estado.
- **FR-004**: El sistema DEBE incluir siempre los 7 estados en el reporte, mostrando un conteo de cero si no hay habitaciones en un estado particular, sin omitir la categoría.
- **FR-005**: El sistema DEBE permitir filtrar opcionalmente el reporte por tipo de habitación.
- **FR-006**: El sistema DEBE entregar el resultado visualizándolo en pantalla de forma clara, con la opción de exportarlo en formato PDF o Excel.
- **FR-007**: El sistema NO DEBE modificar ningún dato ni ejecutar transiciones de estado al generar este reporte.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Representa una unidad habitacional del hotel. Sus estados (Available, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive, según maquina-estados-habitacion.md) son agrupados y contabilizados.
- **Manager**: Actor encargado de solicitar y visualizar el reporte.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El reporte se genera y visualiza en pantalla en menos de 5 segundos.
- **SC-002**: El 100% de los reportes generados incluye obligatoriamente la lista completa de los 7 estados, independientemente de que su conteo sea mayor a cero.
- **SC-003**: Cero interrupciones o bloqueos ocurren en operaciones de transición de estados mientras se ejecuta la generación del reporte.
