# Especificación de Funcionalidad: Generar reporte de estados

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Generar reporte de historial de estados de una habitación (Prioridad: P1)

Como **Gerente**, quiero generar un reporte que muestre la evolución histórica de los estados de una habitación a lo largo del tiempo, para analizar tendencias operativas y tomar decisiones basadas en el comportamiento pasado.

**Por qué esta prioridad**: Conocer el historial de estados permite detectar cuellos de botella, períodos de inactividad o problemas recurrentes en limpieza y mantenimiento. El reporte es de solo lectura y no afecta la operación del sistema.

**Prueba Independiente**: Iniciar sesión como Gerente, solicitar el reporte del historial para la habitación 203 y verificar que se muestra la secuencia de estados con sus rangos temporales.

**Escenarios de Aceptación**:

1. **Escenario**: Generación de reporte completo para una habitación específica
   - **Dado** que la habitación 203 tiene múltiples transiciones registradas
   - **Cuando** el Gerente solicita el reporte del historial para la habitación 203
   - **Entonces** el sistema muestra una tabla con cada estado, fecha/hora de inicio y de finalización (cuando exista), ordenados cronológicamente.

2. **Escenario**: Aplicación de filtros al reporte
   - **Dado** que el Gerente desea ver solo transiciones del estado **"InCleaning"** entre el 01/01/2026 y el 31/01/2026
   - **Cuando** solicita el reporte con dichos filtros
   - **Entonces** el sistema devuelve únicamente las transiciones que cumplan los criterios, mostrando el mismo formato de tabla.

---

### Casos Borde

- **Historial vacío**: La habitación nunca ha cambiado de estado (solo está en "Available"). El reporte muestra una única fila con estado "Available" y sin fecha de finalización.
- **Transición sin fin**: La última transición está en curso (no tiene fecha de finalización). El reporte indica "Actual" o deja el campo de fin vacío.
- **Filtros sin resultados**: No existen transiciones que coincidan con los filtros aplicados; el reporte muestra mensaje "No se encontraron resultados".
- **Gran rango de fechas**: El historial contiene miles de entradas; el sistema sigue generando el reporte en menos de 5 s.
- **Concurrente**: Mientras se generan transiciones en otras habitaciones, el reporte se basa en una snapshot consistente y no bloquea esas operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor **"Manager"** generar el reporte de historial de estados.
- **FR-002**: El reporte DEBE basarse en la entidad **StateTransition** que registra cada cambio de estado de una habitación (roomId, fromState, toState, startTime, endTime).
- **FR-003**: El sistema DEBE permitir filtrar el historial por:
  - habitación (identificador o número)
  - tipo de habitación
  - estado
  - rango de fechas (startTime / endTime).
- **FR-004**: Cada fila del reporte DEBE mostrar: habitación, estado, fecha/hora de inicio, fecha/hora de finalización (cuando exista).
- **FR-005**: Cuando una transición no tiene fecha de finalización, el reporte DEBE marcarla como **"Actual"**.
- **FR-006**: El reporte DEBE presentarse en pantalla de forma clara y ordenada cronológicamente.
- **FR-007**: El reporte DEBE ser **solo lectura**; no debe modificar datos ni ejecutar transiciones de estado.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 7 estados del ciclo de vida).
- **StateTransition**: Representa una transición de estado de una habitación. Atributos: roomId (FK a Room), fromState, toState, startTime, endTime (nullable para estado actual).
- **Manager**: Actor que realiza la consulta y generación del reporte de historial de estados.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El reporte se genera y visualiza en pantalla en menos de **5 segundos** para cualquier combinación de filtros.
- **SC-002**: El reporte incluye **todas** las transiciones del historial, ordenadas cronológicamente, y muestra correctamente los campos solicitados.
- **SC-003**: Los filtros por habitación, tipo, estado y rango de fechas funcionan y devuelven únicamente los registros que cumplen los criterios.
- **SC-005**: La generación del reporte no interfiere ni bloquea otras operaciones de transición de estado en el sistema.
