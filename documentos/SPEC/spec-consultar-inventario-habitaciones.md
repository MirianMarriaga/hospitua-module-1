# Especificación de Funcionalidad: Consultar inventario de habitaciones

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del inventario general (Prioridad: P1)

Como Administrador, Gerente o Módulo 2, quiero consultar las habitaciones del hotel con sus atributos actuales completos (identificación, categorización, tarifa base, estado actual), para tener una visión detallada de cada unidad habitacional, poder auditar la oferta o validar datos para otros procesos operativos.

**Por qué esta prioridad**: Permite a la gerencia auditar el inventario completo y es el único punto de consulta de lectura del módulo, fundamental para que otros procesos (como los del Módulo 2) validen información sin modificar datos.

**Prueba Independiente**: Puede probarse iniciando sesión como Gerente, accediendo al listado general de habitaciones, comprobando que se incluyan todos los atributos clave y validando el correcto funcionamiento de los filtros y el ordenamiento.

**Escenarios de Aceptación**:

1. **Escenario**: Listado de inventario general por defecto
   - **Dado** que existen habitaciones registradas en el sistema
   - **Cuando** el Gerente ingresa a la consulta de inventario sin aplicar filtros
   - **Entonces** el sistema muestra todas las habitaciones registradas con sus atributos completos, incluyendo por defecto aquellas en estado "Inactive"

2. **Escenario**: Filtrado por atributos y estado
   - **Dado** que el Gerente requiere consultar un subconjunto específico del inventario
   - **Cuando** aplica filtros combinados por tipo, por piso/ala y por estado (por ejemplo, "Available")
   - **Entonces** el sistema muestra únicamente las habitaciones que cumplen todos los criterios seleccionados

3. **Escenario**: Ordenamiento del inventario
   - **Dado** que el Gerente visualiza el listado de habitaciones
   - **Cuando** selecciona un criterio de ordenamiento (ej. por número de habitación ascendente)
   - **Entonces** el sistema reordena el listado en base al criterio seleccionado sin alterar los filtros aplicados

4. **Escenario**: Consulta puntual por Módulo 2
   - **Dado** un identificador único (ID) de habitación válido
   - **Cuando** el Módulo 2 consulta filtrando por ese ID
   - **Entonces** el sistema retorna únicamente esa habitación con sus atributos completos y estado actual

---

### Casos Borde

- Inventario vacío (hotel recién configurado): el sistema muestra la estructura del listado (cabeceras) acompañada de un mensaje claro indicando que no hay habitaciones registradas.
- Consulta con filtros que no arrojan resultados: el sistema no produce un error, sino que presenta una lista vacía con un mensaje informativo de que ninguna habitación cumple los criterios actuales.
- Consulta por un ID de habitación inexistente: el sistema retorna un resultado vacío, no un error.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir a los actores "Administrator", "Manager" y "Módulo 2" consultar el listado completo de habitaciones del inventario.
- **FR-002**: El sistema DEBE mostrar para cada habitación sus atributos completos: ID único, número, piso/ala, tipo, capacidad máxima, tarifa base y estado actual.
- **FR-003**: El sistema DEBE incluir por defecto en el listado todas las habitaciones, incluso aquellas que se encuentran en estado "Inactive", a menos que se filtre explícitamente para excluirlas.
- **FR-004**: El sistema DEBE permitir filtrar el listado por identificador único (ID), por tipo, por piso/ala y por estado. Una consulta filtrada por ID devuelve como máximo un resultado.
- **FR-005**: El sistema DEBE permitir ordenar el listado de resultados (por ejemplo, por número de habitación, por tarifa o por estado).
- **FR-006**: El sistema NO DEBE modificar ningún dato ni ejecutar transiciones de estado como parte de esta consulta.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Representa una unidad habitacional del hotel y contiene todos los datos a ser expuestos. Los estados expuestos (Available, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive) provienen del ciclo de vida definido.
- **Administrator**: Actor que puede realizar la consulta del inventario para su gestión y revisión operativa.
- **Manager**: Actor que realiza la consulta del inventario para propósitos de auditoría y revisión general.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El listado de habitaciones, con filtros u ordenamiento aplicados, se carga en menos de 3 segundos para inventarios de hasta 5000 unidades.
- **SC-002**: El 100% de las consultas por defecto muestran habitaciones en estado "Inactive" junto con las operativas.
