# Especificación de Funcionalidad: Consultar Reservas

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Búsqueda de reservas y consulta de datos de estadía para Check-In (Prioridad: P1)

Como Recepcionista, quiero consultar el listado de reservas del día o buscar reservas mediante criterios acordados con Módulo 2 (código de reserva, o alternativamente por documento o nombre del titular), para obtener y verificar oportunamente los datos contractuales y el estado de la unidad en el flujo de Check-In.

**Por qué esta prioridad**: Es el punto de partida indispensable en la validación inicial de Check-In (reservas en estado `ACTIVE` con fecha de inicio hoy). Permite localizar la estancia en Módulo 2, verificar el estado del contrato (`ACTIVE`) y contrastarlo con el estado físico de la habitación asignada en Módulo 1 (`Reserved`). Las salidas (Check-Out) no requieren consultar a Módulo 2, pues se gestionan a partir de la información persistida localmente en Módulo 1 (`Stay` + `Room` + `RoomGuest`).

**Prueba Independiente**: Se prueba invocando la consulta desde el listado general de Llegadas de hoy (reservas en estado `ACTIVE` con `startDate = hoy`) o mediante la barra de búsqueda de Check-In, comprobando que el sistema consulta síncronamente a Módulo 2 y suministra: código de reserva (`reservationRef`), datos del huésped titular (`fullName`, `documentType`, `documentNumber`, `nationality`), cantidad de huéspedes contratada (`guestCount`), fechas de estadía (solo fechas: `startDate`, `endDate` y número de noches), canal de origen (`source`: "Directo" u "OTA"), estado de la reserva (`status`: `ACTIVE`), y la habitación asignada con su estado físico actual en Módulo 1.

**Escenarios de Aceptación**:

1. **Escenario**: Búsqueda o selección exitosa de reserva para Check-In (Happy Path Check-In)
   - **Dado** una reserva en Módulo 2 en estado "ACTIVE" cuya fecha de inicio coincide con la fecha de hoy, con habitación asignada en estado físico "Reserved" en Módulo 1
   - **Cuando** el Recepcionista localiza la reserva desde el listado de Llegadas del día (que presenta habitación, huésped, estadía en noches, cantidad de personas de la reserva, tipo de habitación y canal) o mediante la barra de búsqueda (por nombre del titular, documento de identidad o código de reserva `reservationRef`)
   - **Entonces** el sistema consulta síncronamente a Módulo 2 mediante REST GET y presenta la información de la estadía mostrando: habitación asignada (número, tipo y estado actual "Reserved"), huésped titular (nombre y documento), estadía (rango de fechas y noches, sin horas), número de huéspedes de la reserva (`guestCount`), canal de origen ("Directo" u "OTA") y estado en Módulo 2 ("Activa" / `ACTIVE`), habilitando continuar con el Check-In sin desplegar avisos o banners eliminados.

2. **Escenario**: Búsqueda para Check-In por número de identificación del titular
   - **Dado** una reserva en Módulo 2 en estado "ACTIVE" con fecha de inicio hoy asociada a un titular con documento "10203040"
   - **Cuando** el Recepcionista realiza la búsqueda en la barra de Check-In ingresando el número de documento
   - **Entonces** el sistema retorna la reserva correspondiente de Módulo 2 presentando los datos del titular y de la estadía para continuar el flujo.

3. **Escenario**: Búsqueda para Check-In por nombre completo del titular
   - **Dado** una reserva en Módulo 2 en estado "ACTIVE" con fecha de inicio hoy a nombre de "Carlos Gómez"
   - **Cuando** el Recepcionista busca en la barra de Check-In por el texto "Carlos Gómez"
   - **Entonces** el sistema localiza la reserva en Módulo 2 y presenta los datos contractuales del titular y de la estadía.

---

### Historia de Usuario 2 - Manejo de múltiples coincidencias y tolerancia a fallos externos (Prioridad: P2)

Como Recepcionista, quiero que el sistema me obligue a seleccionar la reserva adecuada cuando un criterio devuelva más de un resultado y maneje con alertas claras las caídas de conexión con Módulo 2, para prevenir asignaciones equivocadas y mantener la estabilidad de la aplicación.

**Por qué esta prioridad**: En el mostrador un cliente puede tener múltiples reservas pasadas o futuras bajo el mismo documento; el sistema nunca debe asumir una reserva por defecto. Asimismo, ante lentitud o caída de Módulo 2, el sistema debe informar la situación de forma controlada sin generar caídas o fallos técnicos no controlados.

**Prueba Independiente**: Se prueba buscando un documento o nombre que devuelva dos o más registros en Módulo 2, verificando que se requiera seleccionar obligatoriamente entre las coincidencias listadas con referencia, titular, fechas y estados; y simulando una desconexión del servicio de Módulo 2 para comprobar la emisión de un mensaje controlado.

**Escenarios de Aceptación**:

1. **Escenario**: Selección obligatoria ante múltiples resultados por documento o nombre
   - **Dado** que la búsqueda por documento o nombre de huésped arroja 2 o más reservas coincidentes en Módulo 2
   - **Cuando** el Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema presenta un listado con todas las coincidencias mostrando código (`reservationRef`), huésped titular, rango de fechas de estadía y estado (`status`), bloqueando el avance hasta que el Recepcionista seleccione explícitamente una de ellas.

2. **Escenario**: Búsqueda sin coincidencias en Módulo 2
   - **Dado** un criterio de búsqueda que no coincide con ninguna reserva en Módulo 2
   - **Cuando** el Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema informa mediante un mensaje controlado que no se encontraron reservas que coincidan con el criterio, permitiendo ingresar un nuevo término de búsqueda.

3. **Escenario**: Manejo controlado ante demoras o indisponibilidad de Módulo 2
   - **Dado** una interrupción de red o caída temporal del servicio de Módulo 2
   - **Cuando** el Recepcionista realiza una búsqueda o consulta de reserva
   - **Entonces** el sistema intercepta la condición e informa de manera controlada la indisponibilidad temporal del servicio externo de reservas, impidiendo fallos técnicos o caídas de la aplicación.

---

### Casos Borde

- **Espacios en blanco y formato en la consulta**: El sistema elimina automáticamente espacios accidentales iniciales y finales (trim) y tolera prefijos en mayúsculas/minúsculas (ej. "res-12345" es interpretado como "RES-12345").
- **Coincidencias con homónimos en búsqueda por nombre**: Si existen dos titulares distintos con el mismo nombre y apellido, el sistema lista ambas reservas detallando el número de documento y fechas para permitir una selección inequívoca.
- **Tiempos de respuesta lentos en Módulo 2**: El sistema implementa un tiempo límite de espera controlado, informando al usuario en caso de demora excesiva para permitir el reintento de la consulta.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso de consulta incluido (`<<includes>>`) tanto por el **Panel de Recepción** (`spec-consultar-panel-recepcion.md`, buscador de Llegadas) como por "Registrar Check-In" (paso 1: Validar reserva), encapsulando la integración de consulta con Módulo 2. El proceso de Check-Out NO invoca este caso de uso externo, ya que se alimenta exclusivamente de la información local persistida en Módulo 1 (`Stay` + `Room` + `RoomGuest`).
- **FR-002**: Conforme al contrato de interfaces con Módulo 2 (`mod-1-2-3.drawio`), el sistema DEBE consultar de forma reactiva mediante una petición sincrónica REST GET a Módulo 2 las reservas requeridas durante el Check-In, sin alterar ningún dato en el módulo destino y esperando respuesta inmediata.
- **FR-003**: El sistema DEBE proveer los siguientes mecanismos de consulta y búsqueda para el flujo de Check-In acordados con Módulo 2, accesibles desde el **buscador de Llegadas del Panel de Recepción** (`spec-consultar-panel-recepcion.md`):
  1. *Listado general de Llegadas*: reservas en estado `ACTIVE` con fecha de inicio igual a la fecha actual (`startDate = hoy`), visualizando en columnas: habitación, huésped titular, estadía en noches, cantidad de personas de la reserva, tipo de habitación, canal de origen y botón de acción "Check-in".
  2. *Búsqueda en el buscador de Llegadas*: búsqueda por nombre del huésped titular (`ACTIVE` + fecha de inicio hoy), por documento del titular (`ACTIVE` + fecha de inicio hoy), o por código de reserva (`reservationRef`).
- **FR-004**: El sistema DEBE recibir desde Módulo 2 la siguiente estructura contractual (`ReservationSummary`):
  - Código de reserva (`reservationRef`)
  - Datos del huésped titular (`guestRef`, `fullName`, `documentType`, `documentNumber`, `nationality`)
  - Cantidad de huéspedes registrada en la reserva (`guestCount`)
  - Habitación asignada (`roomId` / número de habitación)
  - Fechas de estadía (`startDate`, `endDate`) y cantidad de noches (fechas sin hora)
  - Canal de origen (`source`: clasificado exclusivamente como "Directo" u "OTA")
  - Estado de la reserva (`status`: uno de los 6 estados oficiales: `PENDING`, `ACTIVE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`)
- **FR-005**: Al consultar o localizar una reserva para Check-In, el sistema DEBE proveer y presentar los siguientes datos:
  - Habitación asignada (número, tipo y estado físico en Módulo 1: "Reservada" / `Reserved`)
  - Huésped titular (nombre y documento)
  - Noches de estadía y rango de fechas
  - Número de huéspedes de la reserva (`guestCount`)
  - Canal de origen ("Directo" u "OTA")
  - Estado en Módulo 2 ("Activa" / `ACTIVE`)
  - No se deben mostrar banners ni indicadores residuales de tipo "Reserva encontrada", "Reserva activa" ni marcas de tiempo con horas.
- **FR-006**: Si la búsqueda por documento o nombre retorna más de una reserva, el sistema DEBE listar todas las coincidencias exhibiendo su código, titular, fechas y estado, exigiendo la selección explícita del Recepcionista antes de presentar el detalle y continuar el flujo.
- **FR-007**: El sistema DEBE capturar cualquier falla de red, lentitud o caída de Módulo 2, traduciéndola en una respuesta controlada de indisponibilidad temporal para el Recepcionista, impidiendo la generación de fallos técnicos no controlados.
- **FR-008**: Este caso de uso NO DEBE validar por sí mismo si el estado de la reserva es apto para admisión, ni si las fechas coinciden con la llegada real, ni transicionar habitaciones; dichas validaciones de negocio son responsabilidad exclusiva del caso de uso consumidor ("Registrar Check-In").

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **ReservationQuery**: Objeto conceptual de búsqueda que encapsula los criterios de consulta (`reservationRef`, `documentNumber`, `fullName`, `roomId`, `startDate`, `status`).
- **ReservationSummary**: Estructura conceptual consumida desde Módulo 2 con los atributos contractuales esenciales: `reservationRef`, `guestRef`, `fullName`, `documentType`, `documentNumber`, `nationality`, `guestCount`, `roomId`, `startDate`, `endDate`, `source` ("Directo" u "OTA") y `status` (`PENDING`, `ACTIVE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`).
- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Module2 (Operación de Reservas)**: Sistema externo responsable del ciclo de vida contractual de las reservas y fuente de verdad de sus datos.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: Las consultas de reservas por código retornan y presentan los resultados en menos de 1.5 segundos en condiciones normales de red.
- **SC-002**: El 100% de las búsquedas con resultados múltiples presentan el listado de selección obligatoria sin asumir selecciones por defecto.
- **SC-003**: En el 100% de los casos de Check-In, la consulta refleja de forma fidedigna tanto el estado contractual de Módulo 2 como el estado físico real de la habitación en Módulo 1.
- **SC-004**: Cero caídas o fallos técnicos no controlados propagados al Recepcionista ante indisponibilidad o respuestas anómalas de Módulo 2.

