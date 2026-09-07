# Especificación de Funcionalidad: Consultar habitación

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta puntual de habitación para procesos internos (Prioridad: P1)

Como Módulo 2, quiero consultar los datos y el estado físico actual de una habitación puntual (o un pequeño conjunto filtrado), para usar esta información internamente antes de ejecutar procesos lógicos como validar reservas o check-in.

**Por qué esta prioridad**: Es la única vía de comunicación para que el Módulo 2 conozca el estado físico y datos de una habitación, que es central en la lógica de negocio. Es de solo lectura y no altera el estado. Antes este caso de uso se llamaba "Reservar habitación", pero al separarse las responsabilidades, se delimita únicamente a la exposición de datos, dejando la lógica de validación del lado del consumidor.

**Prueba Independiente**: Puede probarse a nivel de integración simulando una petición interna desde el Módulo 2 hacia este caso de uso, verificando que los datos retornados correspondan a la habitación solicitada y contengan el estado actual correcto, sin afectar la base de datos.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa de habitación existente
   - **Dado** que existe una habitación con un identificador válido
   - **Cuando** el Módulo 2 realiza la consulta proporcionando dicho identificador
   - **Entonces** el sistema retorna todos los atributos y el estado actual de la habitación (por ejemplo, "Available")

2. **Escenario**: Consulta filtrada por atributo
   - **Dado** que el Módulo 2 requiere consultar habitaciones según criterios específicos (ej. tipo o piso)
   - **Cuando** realiza la consulta con el filtro pertinente
   - **Entonces** el sistema devuelve las habitaciones que coinciden con esos atributos junto a su información completa y estado actual

3. **Escenario**: Consulta de identificador inexistente
   - **Dado** que el Módulo 2 envía una solicitud de consulta
   - **Cuando** se provee un identificador que no existe en el sistema
   - **Entonces** el sistema retorna un resultado vacío en lugar de un error del sistema

---

### Casos Borde

- Consulta de una habitación en estado Inactive: la consulta responde correctamente con los datos y el estado "Inactive". La habitación se puede consultar, y no se oculta; el Módulo 2 será el encargado de descartarla para fines de disponibilidad según sus propias reglas.
- Consulta simultánea de la misma habitación desde dos solicitudes distintas del Módulo 2: el sistema procesa ambas peticiones en modo lectura, retornando consistentemente el estado físico de la habitación en ese instante sin bloqueos de concurrencia para la consulta.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor interno "Módulo 2" consumir la consulta de la habitación (Ni el Administrador ni el Módulo 3 son consumidores directos de este flujo).
- **FR-002**: El sistema DEBE retornar todos los atributos de la entidad (ID único, número, piso/ala, tipo, capacidad máxima, tarifa base) y su estado físico actual (según documentos/SPEC/referencias/maquina-estados-habitacion.md).
- **FR-003**: El sistema DEBE retornar un resultado vacío si la consulta se realiza por un identificador inexistente, sin lanzar una excepción de aplicación que interrumpa el flujo general.
- **FR-004**: El sistema NO DEBE ejecutar ninguna transición de estado, validación de negocio cruzada ni modificación de datos bajo ninguna circunstancia a partir de esta consulta.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Representa la entidad consultada. Sus posibles valores de estado (Available, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive) provienen del ciclo de vida documentado en maquina-estados-habitacion.md.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: Las peticiones de consulta responden en menos de 200ms para asegurar fluidez en los procesos del Módulo 2.
- **SC-002**: El 100% de las consultas concurrentes sobre la misma habitación resuelven exitosamente sin errores de bloqueo ni deadlocks.
