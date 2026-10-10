# Especificación de Funcionalidad: Consultar inventario de habitaciones

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Gerente / Administrador / Módulo 2 (sistema externo)
**Creado**: 2026-09-07
## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del inventario general (Prioridad: P1)

Como Gerente o Administrador, quiero consultar las habitaciones del hotel con sus atributos actuales completos (identificación, categorización, tarifa base, estado actual), y como Módulo 2, las habitaciones vendibles con los datos que necesita para reservar (FR-010), para tener una visión detallada de cada unidad habitacional, poder auditar la oferta o validar datos para otros procesos operativos.

**Por qué esta prioridad**: Permite a la gerencia auditar el inventario completo y es el punto de consulta del listado general de habitaciones del módulo, fundamental para que otros procesos (como los del Módulo 2) validen información sin modificar datos.

**Prueba Independiente**: Puede probarse iniciando sesión como Gerente o Administrador, accediendo al listado general de habitaciones, comprobando que se incluyan todos los atributos clave y validando el correcto funcionamiento de los filtros y el ordenamiento; y consultando como Módulo 2 por categoría y por ID, comprobando que solo se reciben `id`, `roomNumber`, `categoryRoom` y `maxCapacity` y que nunca aparecen habitaciones "Inactive".

**Escenarios de Aceptación**:

1. **Escenario**: Listado de inventario general por defecto
   - **Dado** que existen habitaciones registradas en el sistema
   - **Cuando** el Gerente ingresa a la consulta de inventario sin aplicar filtros
   - **Entonces** el sistema muestra todas las habitaciones registradas con sus atributos completos, incluyendo por defecto aquellas en estado "Inactive"

2. **Escenario**: Filtrado por atributos y estado
   - **Dado** que el Gerente requiere consultar un subconjunto específico del inventario
   - **Cuando** aplica filtros combinados por tipo, por piso y por estado (por ejemplo, "Available")
   - **Entonces** el sistema muestra únicamente las habitaciones que cumplen todos los criterios seleccionados

3. **Escenario**: Ordenamiento del inventario
   - **Dado** que el Administrador visualiza el listado de habitaciones
   - **Cuando** selecciona un criterio de ordenamiento (ej. por número de habitación ascendente)
   - **Entonces** el sistema reordena el listado en base al criterio seleccionado sin alterar los filtros aplicados

4. **Escenario**: Consulta puntual por Módulo 2
   - **Dado** un identificador único (ID) de una habitación que no está en estado "Inactive"
   - **Cuando** el Módulo 2 consulta por ese ID
   - **Entonces** el sistema retorna únicamente esa habitación con `id`, `roomNumber`, `categoryRoom` y `maxCapacity` (FR-010)

5. **Escenario**: Acceso al historial de una habitación desde el inventario
   - **Dado** que el Gerente está consultando el inventario
   - **Cuando** selecciona "Ver historial" en la fila de una habitación
   - **Entonces** el sistema abre "Consultar historial de estados" filtrado por esa habitación

6. **Escenario**: Acciones del Administrador según el estado de la habitación
   - **Dado** que el Administrador está consultando el inventario
   - **Cuando** revisa las habitaciones del listado
   - **Entonces** el sistema muestra "Editar" y "Dar de baja" solo en las habitaciones "Available", "Marcar como disponible" solo en las habitaciones "Inactive", ninguna acción en los demás estados, y el botón "Registrar habitación" sobre el listado

7. **Escenario**: Paginación del listado
   - **Dado** que el inventario tiene más de 10 habitaciones que cumplen los filtros
   - **Cuando** el Gerente o el Administrador consulta el listado
   - **Entonces** el sistema muestra 10 habitaciones por página, indica el rango mostrado y el total ("Mostrando 1–10 de 24") y permite navegar con "Anterior", "Siguiente" y el número de página, conservando los filtros y el orden aplicados

8. **Escenario**: Consulta de habitaciones vendibles por categoría (Módulo 2)
   - **Dado** que la categoría Doble tiene tres habitaciones, una de ellas en estado "Inactive"
   - **Cuando** el Módulo 2 consulta por `categoryRoom` = `DOBLE`
   - **Entonces** el sistema retorna, sin paginar, la lista de las dos habitaciones que no están en "Inactive", cada una con `id`, `roomNumber`, `categoryRoom` y `maxCapacity`

9. **Escenario**: Habitación dada de baja consultada por Módulo 2
   - **Dado** una habitación en estado "Inactive"
   - **Cuando** el Módulo 2 consulta por su ID
   - **Entonces** el sistema responde "habitación no encontrada" (FR-011), porque una habitación dada de baja no se puede ofrecer para una reserva

---

### Casos Borde

- Inventario vacío (hotel recién configurado): el sistema muestra la estructura del listado (cabeceras) acompañada de un mensaje claro indicando que no hay habitaciones registradas.
- Consulta con filtros que no arrojan resultados: el sistema no produce un error, sino que presenta una lista vacía con un mensaje informativo de que ninguna habitación cumple los criterios actuales.
- Consulta por un número de habitación inexistente en las vistas del Gerente o del Administrador: el sistema presenta una lista vacía con el mensaje informativo, no un error. En la consulta de Módulo 2, un ID inexistente o de una habitación "Inactive" responde "habitación no encontrada" y una categoría sin habitaciones vendibles responde una lista vacía (FR-011).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir a los actores "Gerente" y "Administrador" consultar el listado completo de habitaciones del inventario, y a "Módulo 2" consultar las habitaciones vendibles según FR-010.
- **FR-002**: En las vistas del Gerente y del Administrador, el sistema DEBE mostrar para cada habitación: número, piso, tipo, capacidad máxima, tarifa base y estado actual. El ID único (UUID) no se muestra en el listado; Módulo 2 lo recibe en la respuesta de su consulta.
- **FR-003**: En las vistas del Gerente y del Administrador, el sistema DEBE incluir por defecto en el listado todas las habitaciones, incluso aquellas que se encuentran en estado "Inactive", a menos que se filtre explícitamente para excluirlas. La consulta de Módulo 2 NUNCA incluye habitaciones "Inactive" (FR-010).
- **FR-004**: En las vistas del Gerente y del Administrador, el sistema DEBE permitir filtrar el listado por número de habitación, por tipo, por piso y por estado. Una consulta filtrada por número de habitación devuelve como máximo un resultado. Módulo 2 consulta con sus propios parámetros (FR-010).
- **FR-005**: El sistema DEBE permitir ordenar el listado de resultados (por ejemplo, por número de habitación, por tarifa o por estado).
- **FR-006**: El sistema NO DEBE modificar ningún dato ni ejecutar transiciones de estado como parte de esta consulta.
- **FR-007**: En la vista del Gerente, el sistema DEBE ofrecer en cada habitación del listado la opción "Ver historial", que abre "Consultar historial de estados" filtrado por esa habitación.
- **FR-008**: En la vista del Administrador, el sistema DEBE ofrecer el botón "Registrar habitación" sobre el listado y, en cada habitación, las acciones de los casos de uso correspondientes según su estado: "Editar" y "Dar de baja" cuando está en "Available", "Marcar como disponible" cuando está en "Inactive", y ninguna acción en los demás estados.
- **FR-009**: En las vistas del Gerente y del Administrador, el sistema DEBE paginar el listado en páginas de 10 habitaciones, mostrando el rango y el total de resultados ("Mostrando X–Y de Z") y los controles "Anterior", "Siguiente" y número de página. Al cambiar un filtro, el listado vuelve a la primera página.
- **FR-010**: Consulta de habitaciones vendibles para Módulo 2: el sistema DEBE permitir a Módulo 2 consultar por categoría (`categoryRoom`, con el código del tipo de *Registrar habitación* FR-005: `SENCILLA`, `DOBLE`, `SUITE` o `BOUTIQUE`; la interfaz los muestra como Sencilla, Doble, Suite o Boutique) o por identificador (`roomId`). La consulta por categoría DEBE retornar, sin paginar ni ordenar, la lista de habitaciones de esa categoría; la consulta por identificador DEBE retornar una sola habitación. Cada habitación DEBE incluir únicamente `id`, `roomNumber`, `categoryRoom` y `maxCapacity`. Las habitaciones en estado "Inactive" NO DEBEN incluirse en ningún caso, porque una habitación dada de baja no se puede ofrecer para una reserva; las demás se entregan sin importar su estado de hoy.
- **FR-011**: Errores de la consulta de Módulo 2: el sistema DEBE responder "habitación no encontrada" (HTTP 404, `ROOM_NOT_FOUND`) cuando el `roomId` no corresponde a una habitación registrada y, obligatoriamente, cuando la habitación está en "Inactive", sin revelar sus datos; un error de validación cuando el `roomId` no tiene formato de identificador o la `categoryRoom` no es un tipo válido; y una lista vacía cuando la categoría no tiene habitaciones vendibles.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su registro, edición y baja.
- **Manager**: Nombre de código del actor Gerente, que realiza la consulta del inventario para propósitos de auditoría y revisión general.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El listado de habitaciones, con filtros u ordenamiento aplicados, se carga en menos de 5 segundos para inventarios de hasta 5000 unidades.
- **SC-002**: El 100% de las consultas por defecto del Gerente y del Administrador muestran habitaciones en estado "Inactive" junto con las operativas.
- **SC-003**: El 100% de las respuestas a Módulo 2 excluyen las habitaciones en estado "Inactive" y responden en menos de 1 segundo.
