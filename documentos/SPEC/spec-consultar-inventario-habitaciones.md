# Especificación de Funcionalidad: Consultar inventario de habitaciones

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Gerente / Administrador / Módulo 2 (sistema externo)
**Creado**: 2026-09-07
## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del inventario general (Prioridad: P1)

Como Gerente, Administrador o Módulo 2, quiero consultar las habitaciones del hotel con sus atributos actuales completos (identificación, categorización, tarifa base, estado actual), para tener una visión detallada de cada unidad habitacional, poder auditar la oferta o validar datos para otros procesos operativos.

**Por qué esta prioridad**: Permite a la gerencia auditar el inventario completo y es el punto de consulta del listado general de habitaciones del módulo, fundamental para que otros procesos (como los del Módulo 2) validen información sin modificar datos.

**Prueba Independiente**: Puede probarse iniciando sesión como Gerente, Administrador o Módulo 2, accediendo al listado general de habitaciones, comprobando que se incluyan todos los atributos clave y validando el correcto funcionamiento de los filtros y el ordenamiento.

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
   - **Dado** un identificador único (ID) de habitación válido
   - **Cuando** el Módulo 2 consulta filtrando por ese ID
   - **Entonces** el sistema retorna únicamente esa habitación con sus atributos completos y estado actual

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

---

### Casos Borde

- Inventario vacío (hotel recién configurado): el sistema muestra la estructura del listado (cabeceras) acompañada de un mensaje claro indicando que no hay habitaciones registradas.
- Consulta con filtros que no arrojan resultados: el sistema no produce un error, sino que presenta una lista vacía con un mensaje informativo de que ninguna habitación cumple los criterios actuales.
- Consulta por un ID de habitación inexistente: el sistema retorna un resultado vacío, no un error.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir a los actores "Gerente", "Módulo 2" y "Administrador" consultar el listado completo de habitaciones del inventario.
- **FR-002**: El sistema DEBE mostrar para cada habitación sus atributos completos: ID único, número, piso, tipo, capacidad máxima, tarifa base y estado actual.
- **FR-003**: El sistema DEBE incluir por defecto en el listado todas las habitaciones, incluso aquellas que se encuentran en estado "Inactive", a menos que se filtre explícitamente para excluirlas.
- **FR-004**: El sistema DEBE permitir filtrar el listado por identificador único (ID), por número de habitación, por tipo, por piso y por estado. Una consulta filtrada por ID o por número de habitación devuelve como máximo un resultado.
- **FR-005**: El sistema DEBE permitir ordenar el listado de resultados (por ejemplo, por número de habitación, por tarifa o por estado).
- **FR-006**: El sistema NO DEBE modificar ningún dato ni ejecutar transiciones de estado como parte de esta consulta.
- **FR-007**: En la vista del Gerente, el sistema DEBE ofrecer en cada habitación del listado la opción "Ver historial", que abre "Consultar historial de estados" filtrado por esa habitación.
- **FR-008**: En la vista del Administrador, el sistema DEBE ofrecer el botón "Registrar habitación" sobre el listado y, en cada habitación, las acciones de los casos de uso correspondientes según su estado: "Editar" y "Dar de baja" cuando está en "Available", "Marcar como disponible" cuando está en "Inactive", y ninguna acción en los demás estados.
- **FR-009**: El sistema DEBE paginar el listado en páginas de 10 habitaciones, mostrando el rango y el total de resultados ("Mostrando X–Y de Z") y los controles "Anterior", "Siguiente" y número de página. Al cambiar un filtro, el listado vuelve a la primera página.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su registro, edición y baja.
- **Manager**: Nombre de código del actor Gerente, que realiza la consulta del inventario para propósitos de auditoría y revisión general.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El listado de habitaciones, con filtros u ordenamiento aplicados, se carga en menos de 5 segundos para inventarios de hasta 5000 unidades.
- **SC-002**: El 100% de las consultas por defecto muestran habitaciones en estado "Inactive" junto con las operativas.
