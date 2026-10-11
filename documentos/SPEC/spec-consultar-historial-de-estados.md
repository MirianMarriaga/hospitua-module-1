# Especificación de Funcionalidad: Consultar historial de estados de una habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Gerente
**Creado**: 2026-09-07
## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el historial de estados de una habitación (Prioridad: P1)

Como **Gerente**, quiero consultar la evolución histórica de los estados de una habitación a lo largo del tiempo, para analizar tendencias operativas y tomar decisiones basadas en el comportamiento pasado.

**Por qué esta prioridad**: Conocer el historial de estados permite detectar cuellos de botella, períodos de inactividad o problemas recurrentes en limpieza y mantenimiento. La consulta es de solo lectura y no afecta la operación del sistema.

**Prueba Independiente**: Iniciar sesión como Gerente, seleccionar la habitación 203 y verificar que se muestra en pantalla la secuencia de estados con sus rangos temporales.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta del historial completo para una habitación específica
   - **Dado** que la habitación 203 tiene múltiples transiciones registradas
   - **Cuando** el Gerente consulta el historial de la habitación 203
   - **Entonces** el sistema muestra en pantalla una tabla con cada estado, fecha/hora de inicio y de finalización (cuando exista), ordenados cronológicamente.

2. **Escenario**: Aplicación de filtros a la consulta
   - **Dado** que el Gerente desea ver solo transiciones del estado **"InCleaning"** entre el 01/01/2026 y el 31/01/2026
   - **Cuando** aplica dichos filtros en la vista de historial
   - **Entonces** el sistema muestra únicamente las transiciones que cumplan los criterios, con el mismo formato de tabla.

3. **Escenario**: Acceso al historial desde el inventario
   - **Dado** que el Gerente está consultando el inventario de habitaciones
   - **Cuando** selecciona la opción "Ver historial" de la habitación 203
   - **Entonces** el sistema abre la vista de historial con el filtro de habitación ya aplicado a la 203.

4. **Escenario**: Rango de fechas inválido
   - **Dado** que el Gerente está aplicando filtros en la vista de historial
   - **Cuando** selecciona una fecha "Desde" posterior a la fecha "Hasta"
   - **Entonces** el sistema informa que la fecha "Desde" no puede ser posterior a "Hasta" y no muestra resultados hasta que el rango sea válido.

---

### Casos Borde

- **Historial vacío**: La habitación nunca ha cambiado de estado (solo está en "Available"). La vista muestra una única fila con estado "Available" y sin fecha de finalización.
- **Transición sin fin**: La última transición está en curso (no tiene fecha de finalización). La vista deja el campo de fin como "En curso".
- **Filtros sin resultados**: No existen transiciones que coincidan con los filtros aplicados; la vista muestra el mensaje "No se encontraron resultados".
- **Gran rango de fechas**: El historial contiene miles de entradas; el sistema muestra la vista paginada (FR-008) en menos de 10 s.
- **Concurrente**: Mientras se registran transiciones en otras habitaciones, la vista se basa en una snapshot consistente y no bloquea esas operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor **Gerente** (`Manager`) consultar el historial de estados de una habitación.
- **FR-002**: El sistema DEBE permitir filtrar el historial por:
  - habitación (identificador o número)
  - tipo de habitación
  - estado
  - rango de fechas (startTime / endTime).
- **FR-003**: Cada fila de la vista DEBE mostrar: habitación, tipo de habitación, estado, fecha/hora de inicio, fecha/hora de finalización (cuando exista), responsable (usuario que provocó la transición, o "Sistema" cuando la transición es autónoma) y flujo de origen (nombre del caso de uso que provocó la transición). Así el Gerente puede auditar quién limpió o reparó una habitación y cuánto tardó.
- **FR-004**: Cuando una transición no tiene fecha de finalización, la vista DEBE mostrar "En curso" como fecha de finalización.
- **FR-005**: La consulta DEBE ser **solo lectura**; no debe modificar datos ni ejecutar transiciones de estado.
- **FR-006**: El sistema DEBE validar que la fecha "Desde" no sea posterior a la fecha "Hasta"; si lo es, DEBE informar el error y no mostrar resultados hasta que el rango sea válido.
- **FR-007**: El sistema DEBE permitir abrir la vista de historial con el filtro de habitación ya aplicado cuando el Gerente accede desde la opción "Ver historial" de una habitación en "Consultar inventario de habitaciones".
- **FR-008**: El sistema DEBE paginar los resultados en páginas de 15 registros, mostrando el rango y el total ("Mostrando X–Y de Z") y los controles "Anterior", "Siguiente" y número de página. Al cambiar un filtro, los resultados vuelven a la primera página.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida).
- **RoomStateHistory**: Historial común de transiciones de estado. Es la fuente de datos de esta consulta: cada fila corresponde a un periodo de la habitación en un estado (estado, inicio, fin, actor y flujo de origen).
- **Manager**: Nombre de código del actor Gerente, que realiza la consulta del historial de estados.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La vista del historial se carga en pantalla en menos de **10 segundos** para cualquier combinación de filtros.
- **SC-002**: La vista incluye **todas** las transiciones del historial, ordenadas cronológicamente, y muestra correctamente los campos solicitados.
- **SC-003**: Los filtros por habitación, tipo, estado y rango de fechas funcionan y muestran únicamente los registros que cumplen los criterios.
- **SC-004**: La consulta del historial no interfiere ni bloquea otras operaciones de transición de estado en el sistema.
