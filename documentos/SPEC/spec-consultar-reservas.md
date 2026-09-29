# Especificación de Funcionalidad: Consultar Reservas

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Búsqueda de reservas y consulta de datos de estadía (Prioridad: P1)

Como Recepcionista, quiero buscar y consultar reservas en Módulo 2 mediante el código de reserva (o alternativamente por documento o nombre del titular), para obtener y consultar oportunamente los datos contractuales y el estado de la unidad en los flujos de Check-In y Check-Out.

**Por qué esta prioridad**: Es el punto de partida indispensable tanto en la validación inicial de Check-In como en la consulta inicial de Check-Out. Permite localizar la estadía en Módulo 2, verificar el estado del contrato (`ACTIVE` para admisión o `IN_PROGRESS` para salida) y contrastarlo con el estado físico de la habitación asignada en Módulo 1.

**Prueba Independiente**: Se prueba invocando la búsqueda con un código de reserva válido y comprobando que el sistema consulta a Módulo 2 y suministra: código de reserva, fechas de estadía, estado de reserva, huésped principal, canal de origen, y habitación asignada con su estado físico actual.

**Escenarios de Aceptación**:

1. **Escenario**: Búsqueda exitosa de reserva para Check-In por código exacto (Happy Path Check-In)
   - **Dado** una reserva activa en Módulo 2 en estado "ACTIVE", cuya habitación asignada está en estado "Reserved" en Módulo 1
   - **Cuando** el Recepcionista consulta por el código de reserva al iniciar el Check-In
   - **Entonces** el sistema consulta síncronamente a Módulo 2 y presenta la información de la estadía detallando: código de reserva, fechas de estadía (rango de fechas y noches), estado ("ACTIVE"), huésped principal, canal de origen y la habitación asignada indicando su estado actual ("Reserved"), habilitando continuar con el Check-In.

2. **Escenario**: Búsqueda exitosa de reserva para Check-Out por código exacto (Happy Path Check-Out)
   - **Dado** una reserva en curso en Módulo 2 en estado "CHECKED_IN" (estado "IN_PROGRESS" en Módulo 2), cuya habitación figura en estado "Occupied" en Módulo 1
   - **Cuando** el Recepcionista consulta por el código de reserva al iniciar el Check-Out
   - **Entonces** el sistema consulta a Módulo 2 y presenta la información de la reserva confirmando el estado "CHECKED_IN", el titular, el canal y que la habitación actual se encuentra en estado "Occupied", habilitando continuar hacia la liquidación de la estadía.

3. **Escenario**: Búsqueda alternativa por número de identificación del titular
   - **Dado** una reserva asociada a un titular cuyo documento es "10203040"
   - **Cuando** el Recepcionista realiza la búsqueda ingresando el número de documento
   - **Entonces** el sistema retorna la reserva correspondiente de Módulo 2 y presenta la información detallada para continuar el flujo.

4. **Escenario**: Búsqueda alternativa por nombre completo del titular
   - **Dado** una reserva a nombre de "Carlos Gómez" en Módulo 2
   - **Cuando** el Recepcionista busca por el texto "Carlos Gómez"
   - **Entonces** el sistema localiza la reserva en Módulo 2 y presenta los datos contractuales obtenidos.

---

### Historia de Usuario 2 - Manejo de múltiples coincidencias y tolerancia a fallos externos (Prioridad: P2)

Como Recepcionista, quiero que el sistema me obligue a seleccionar la reserva adecuada cuando un criterio devuelva más de un resultado y maneje con alertas claras las caídas de conexión con Módulo 2, para prevenir asignaciones equivocadas y mantener la estabilidad de la aplicación.

**Por qué esta prioridad**: En el mostrador un cliente puede tener múltiples reservas pasadas o futuras bajo el mismo documento; el sistema nunca debe asumir una reserva por defecto. Asimismo, ante lentitud o caída de Módulo 2, el sistema debe informar la situación de forma controlada sin generar caídas o fallos técnicos no controlados.

**Prueba Independiente**: Se prueba buscando un documento que posea dos o más registros en Módulo 2, verificando que se requiera seleccionar obligatoriamente entre las coincidencias listadas con referencia, fechas y estados; y desconectando el servicio de Módulo 2 para comprobar la emisión de un mensaje controlado.

**Escenarios de Aceptación**:

1. **Escenario**: Selección obligatoria ante múltiples resultados por documento o nombre
   - **Dado** que un huésped con documento "10203040" cuenta con 2 o más reservas en Módulo 2
   - **Cuando** el Recepcionista realiza la búsqueda por dicho documento
   - **Entonces** el sistema presenta un listado con todas las coincidencias mostrando código (`reservationRef`), rango de fechas de estadía y estado (`status`), bloqueando el avance hasta que el Recepcionista seleccione explícitamente una de ellas.

2. **Escenario**: Búsqueda sin coincidencias en Módulo 2
   - **Dado** un código o documento inexistente en la base de datos de Módulo 2
   - **Cuando** el Recepcionista ejecuta la búsqueda
   - **Entonces** el sistema informa mediante un mensaje controlado que no se encontraron reservas con ese criterio, permitiendo ingresar un nuevo criterio de búsqueda.

3. **Escenario**: Manejo controlado ante demoras o indisponibilidad de Módulo 2
   - **Dado** una interrupción de red o caída temporal del servicio de Módulo 2
   - **Cuando** el Recepcionista realiza una búsqueda de reserva
   - **Entonces** el sistema intercepta la condición e informa de manera controlada la indisponibilidad temporal del servicio externo de reservas, impidiendo fallos técnicos o caídas de la aplicación.

---

### Casos Borde

- **Espacios en blanco y formato en la consulta**: El sistema elimina automáticamente espacios accidentales iniciales y finales (trim) y tolera prefijos en mayúsculas/minúsculas (ej. "res-12345" es interpretado como "RES-12345").
- **Coincidencias con homónimos en búsqueda por nombre**: Si existen dos titulares distintos con el mismo nombre y apellido, el sistema lista ambas reservas detallando el número de documento y fechas para permitir una selección inequívoca.
- **Tiempos de respuesta lentos en Módulo 2**: El sistema implementa un tiempo límite de espera controlado, informando al usuario en caso de demora excesiva para permitir el reintento de la consulta.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) tanto por "Registrar Check-In" como por "Registrar Check-Out", encapsulando la integración de consulta con Módulo 2.
- **FR-002**: El sistema DEBE permitir localizar la reserva teniendo como criterio principal el código de reserva (`reservationRef`), y admitiendo como criterios alternativos el número de documento del huésped (`documentNumber`) o el nombre completo del titular (`fullName`).
- **FR-003**: El sistema DEBE recibir desde Módulo 2 la siguiente estructura contractual:
  - Código de reserva (`reservationRef`)
  - Datos del huésped titular (`guestRef`, `documentNumber`, `fullName`, `nationality`, `documentType`)
  - Habitación asignada (`roomId` / número de habitación)
  - Fechas de estadía (`startDate`, `endDate`) y cantidad de noches
  - Canal de origen (`source`, ej. "Canal Directo" u "OTA")
  - Estado de la reserva (`status`)
- **FR-004**: Al localizar una reserva, el sistema DEBE proveer y presentar los siguientes datos de la estancia:
  - Código de reserva
  - Fechas de estadía (fechas de inicio y fin, número de noches)
  - Estado de la reserva
  - Huésped principal
  - Canal de origen
  - Detalles de la habitación asignada y su estado físico actual en Módulo 1 (Available, Reserved, Occupied, etc.)
- **FR-005**: Si la búsqueda por documento o nombre retorna más de una reserva, el sistema DEBE listar todas las coincidencias exhibiendo su código, fechas y estado, exigiendo la selección explícita del Recepcionista antes de presentar el detalle y continuar el flujo.
- **FR-006**: El sistema DEBE capturar cualquier falla de red, lentitud o caída de Módulo 2, traduciéndola en una respuesta controlada de indisponibilidad temporal para el Recepcionista, impidiendo la generación de fallos técnicos no controlados.
- **FR-007**: Este caso de uso NO DEBE validar por sí mismo si el estado de la reserva es apto para admisión o salida, ni si las fechas coinciden con la llegada real, ni transicionar habitaciones; dichas validaciones de negocio son responsabilidad exclusiva del caso de uso consumidor ("Registrar Check-In" o "Registrar Check-Out").

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **ReservationQuery**: Objeto conceptual de búsqueda que encapsula los criterios de consulta (`reservationRef`, `documentNumber`, `fullName`).
- **ReservationSummary**: Estructura conceptual consumida desde Módulo 2 con los atributos contractuales esenciales: `reservationRef`, `guestRef`, `fullName`, `documentNumber`, `documentType`, `nationality`, `roomId`, `startDate`, `endDate`, `source` y `status`.
- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Module2 (Operación de Reservas)**: Sistema externo responsable del ciclo de vida contractual de las reservas y fuente de verdad de sus datos.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: Las consultas de reservas por código retornan y presentan los resultados en menos de 1.5 segundos en condiciones normales de red.
- **SC-002**: El 100% de las búsquedas con resultados múltiples presentan el listado de selección obligatoria sin asumir selecciones por defecto.
- **SC-003**: En el 100% de los casos de Check-In y Check-Out, la consulta refleja de forma fidedigna tanto el estado contractual de Módulo 2 como el estado físico real de la habitación en Módulo 1.
- **SC-004**: Cero caídas o fallos técnicos no controlados propagados al Recepcionista ante indisponibilidad o respuestas anómalas de Módulo 2.
