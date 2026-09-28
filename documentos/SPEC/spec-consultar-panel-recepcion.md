# Especificación de Funcionalidad: Panel de Recepción — Vista de Inicio de Jornada

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-28

> **Nota**: Esta especificación describe una vista de consulta e inicio operativo, no un caso de uso transaccional. No registra entidades, no transiciona estados de habitaciones y no tiene caso de uso `<<includes>>` ni `<<extends>>`. Actúa como punto de despacho hacia los flujos transaccionales de Check-In (`spec-registrar-check-in.md`) y Check-Out (`spec-registrar-check-out.md`).

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del listado de llegadas del día y búsqueda para Check-In (Prioridad: P1)

Como Recepcionista, quiero ver al iniciar mi jornada el listado de reservas activas con llegada hoy y buscar rápidamente cualquier reserva por nombre, documento o código, para localizar al huésped y acceder directamente al flujo de Check-In con la reserva ya identificada.

**Por qué esta prioridad**: Es el punto de entrada operativo del turno. El listado de llegadas y el buscador de Check-In evitan pérdidas de tiempo en el mostrador, centralizando en una sola pantalla la consulta a Módulo 2 que permite despachar al recepcionista hacia el proceso de admisión con la reserva ya identificada.

**Prueba Independiente**: Se prueba cargando el panel con la pestaña Llegadas activa, verificando que los 4 KPIs se calculen correctamente, que la tabla muestre las columnas requeridas (Hab. · Huésped · Estadía en noches · Personas · Tipo habitación · Fuente · Acción) con los datos correspondientes de Módulo 2, que el buscador de Llegadas filtre en tiempo real por nombre, documento o código de reserva, y que al pulsar "Check-in" el flujo de admisión inicie con la reserva ya pre-cargada sin solicitar ningún criterio de búsqueda adicional.

**Escenarios de Aceptación**:

1. **Escenario**: Carga del panel con llegadas del día (Happy Path)
   - **Dado** que el Recepcionista inicia sesión en el sistema
   - **Cuando** accede al panel de inicio
   - **Entonces** el sistema muestra el saludo personalizado ("Bienvenida, [nombre del Recepcionista]", con subtítulo "Estancias · llegadas y salidas del día"), los 4 KPIs calculados, la pestaña Llegadas activa por defecto y la tabla con todas las reservas en estado `ACTIVE` con fecha de inicio igual a hoy, en columnas: Hab. · Huésped (nombre y documento debajo) · Estadía (noches) · Personas (`guestCount`) · Tipo habitación · Fuente (Directo u OTA) · botón de acción "Check-in".

2. **Escenario**: Búsqueda de reserva para Check-In por nombre del titular
   - **Dado** que el Recepcionista se encuentra en la pestaña Llegadas
   - **Cuando** ingresa el nombre del titular en el buscador de Llegadas
   - **Entonces** el sistema consulta a Módulo 2 y filtra en tiempo real la tabla mostrando únicamente las reservas cuyo titular coincida con el texto ingresado, manteniendo las columnas definidas y el botón de acción "Check-in" disponible.

3. **Escenario**: Búsqueda de reserva para Check-In por número de documento
   - **Dado** que el Recepcionista se encuentra en la pestaña Llegadas
   - **Cuando** ingresa el número de documento del titular en el buscador de Llegadas
   - **Entonces** el sistema filtra la tabla en tiempo real mostrando la reserva cuyo titular tiene ese documento en Módulo 2, con el botón de acción "Check-in" disponible.

4. **Escenario**: Búsqueda de reserva para Check-In por código de reserva
   - **Dado** que el Recepcionista se encuentra en la pestaña Llegadas
   - **Cuando** ingresa el código de reserva (ej. "RES-12345") en el buscador de Llegadas
   - **Entonces** el sistema localiza la reserva en Módulo 2, la presenta en la tabla con sus columnas completas y el botón de acción "Check-in" disponible.

5. **Escenario**: Inicio del proceso de Check-In desde el listado
   - **Dado** que el Recepcionista ha localizado o visualizado la reserva deseada en la pestaña Llegadas
   - **Cuando** pulsa el botón "Check-in" de la fila correspondiente
   - **Entonces** el sistema navega al flujo de Registrar Check-In transfiriendo la referencia de reserva ya seleccionada, de modo que el proceso de admisión inicie directamente en el paso 1 (Validar reserva) sin solicitar ningún criterio de búsqueda adicional.

---

### Historia de Usuario 2 - Consulta del listado de salidas y búsqueda para Check-Out (Prioridad: P1)

Como Recepcionista, quiero ver el listado de estancias con salida programada para hoy (o con salida vencida) y buscar rápidamente cualquier estancia por nombre, documento, código de reserva o número de habitación, para localizar al huésped y acceder directamente al flujo de Check-Out con la estancia ya identificada, sin necesidad de consultas a Módulo 2.

**Por qué esta prioridad**: El buscador de Salidas opera de forma 100% local sobre los datos persistidos en Módulo 1 (`Stay` + `Room` + `RoomGuest`) durante el Check-In. No requiere conexión con Módulo 2, garantizando la autonomía operativa del turno ante posibles indisponibilidades externas.

**Prueba Independiente**: Se prueba cambiando a la pestaña Salidas, verificando que la tabla muestre únicamente estancias activas locales (habitación en estado `Occupied`) con las columnas requeridas (Hab. · Huésped · Fecha entrada · Fecha salida · Personas · Tipo habitación · Fuente · Acción), que las salidas vencidas aparezcan marcadas visualmente con la etiqueta "Vencida", que el buscador de Salidas filtre en tiempo real sin invocar a Módulo 2, y que al pulsar "Check-out" el flujo de salida inicie con la estancia ya pre-cargada.

**Escenarios de Aceptación**:

1. **Escenario**: Visualización del listado de salidas del día (Happy Path)
   - **Dado** que el Recepcionista cambia a la pestaña Salidas del panel
   - **Cuando** la pestaña se activa
   - **Entonces** el sistema consulta localmente en Módulo 1 las estancias activas (`Stay` con habitación en estado `Occupied`) y muestra la tabla en columnas: Hab. · Huésped (nombre y documento debajo) · Fecha entrada (`checkInDate`) · Fecha salida (`expectedCheckoutTime`) · Personas (total de ocupantes de la estancia) · Tipo habitación · Fuente (`source` de `Stay`) · botón de acción "Check-out". Las estancias cuya fecha de salida ya expiró se resaltan visualmente con la etiqueta "Vencida" junto a la fecha de salida.

2. **Escenario**: Búsqueda de estancia para Check-Out por nombre, documento, código o habitación
   - **Dado** que el Recepcionista se encuentra en la pestaña Salidas
   - **Cuando** ingresa cualquier criterio en el buscador de Salidas (nombre del titular, documento, código de reserva o número de habitación)
   - **Entonces** el sistema filtra en tiempo real la tabla consultando únicamente los datos locales de Módulo 1 (`Stay` + `Room` + `RoomGuest`), sin realizar ninguna petición a Módulo 2, mostrando las estancias coincidentes con sus columnas completas.

3. **Escenario**: Inicio del proceso de Check-Out desde el listado
   - **Dado** que el Recepcionista ha localizado la estancia deseada en la pestaña Salidas
   - **Cuando** pulsa el botón "Check-out" de la fila correspondiente
   - **Entonces** el sistema navega al flujo de Registrar Check-Out transfiriendo la referencia de la estancia ya seleccionada, de modo que el proceso de salida inicie directamente en el paso 1 (Consultar reserva) sin solicitar ningún criterio de búsqueda adicional.

4. **Escenario**: Visualización de salidas vencidas
   - **Dado** que existen estancias activas locales cuya fecha de salida esperada (`expectedCheckoutTime`) es anterior a la fecha actual del sistema
   - **Cuando** el Recepcionista visualiza la pestaña Salidas
   - **Entonces** dichas estancias aparecen en la tabla con la etiqueta "Vencida" destacada visualmente junto a la fecha de salida, para que el Recepcionista las identifique de forma prioritaria.

---

### Historia de Usuario 3 - Indicadores operativos del turno (KPIs) (Prioridad: P2)

Como Recepcionista, quiero ver al inicio de mi jornada un resumen numérico de las operaciones del día (llegadas, salidas, salidas vencidas y habitaciones ocupadas), para tener visibilidad inmediata de la carga de trabajo del turno.

**Por qué esta prioridad**: Los indicadores permiten al Recepcionista priorizar su atención (especialmente las salidas vencidas) sin navegar al listado detallado. Su cálculo es totalmente local para salidas y habitaciones ocupadas; las llegadas se obtienen de Módulo 2.

**Prueba Independiente**: Se prueba cargando el panel y verificando que cada KPI muestre el valor correcto: "Llegadas de hoy" = total de reservas `ACTIVE` con `startDate = hoy` según Módulo 2; "Salidas de hoy" = total de `Stay` activos con `expectedCheckoutTime = hoy`; "Salidas vencidas" = total de `Stay` activos con `expectedCheckoutTime < hoy`; "Habitaciones ocupadas" = total de habitaciones en estado `Occupied` en Módulo 1.

**Escenarios de Aceptación**:

1. **Escenario**: Cálculo y visualización de los 4 KPIs al cargar la pantalla
   - **Dado** que el Recepcionista accede al panel de inicio
   - **Cuando** la vista se carga
   - **Entonces** el sistema muestra en orden: (1) "Llegadas de hoy" con el conteo de reservas activas cuya fecha de inicio es hoy, según Módulo 2; (2) "Salidas de hoy" con el conteo de estancias locales activas cuya fecha de salida esperada es hoy; (3) "Salidas vencidas" con el conteo de estancias locales activas con fecha de salida ya expirada, resaltadas en color de alerta; (4) "Habitaciones ocupadas" con el total de habitaciones en estado `Occupied` en el inventario de Módulo 1.

---

### Casos Borde

- **Sin reservas de llegada para el día**: Cuando no existen reservas activas con fecha de inicio igual a hoy en Módulo 2, la tabla de Llegadas muestra el mensaje "No hay reservas que mostrar." y el KPI "Llegadas de hoy" indica 0.
- **Sin estancias activas para salida**: Cuando no existen estancias activas con salida programada o vencida, la tabla de Salidas muestra el mensaje "No hay reservas que mostrar." y los KPIs "Salidas de hoy" y "Salidas vencidas" indican 0.
- **Indisponibilidad de Módulo 2 al cargar llegadas**: Si Módulo 2 no responde al consultar el listado de reservas para la pestaña Llegadas, el sistema informa de manera controlada la indisponibilidad temporal del servicio externo sin propagar fallos técnicos; el buscador de Salidas y los KPIs locales (salidas, vencidas, ocupadas) continúan operando normalmente.
- **Búsqueda sin coincidencias**: Cuando el texto ingresado en cualquiera de los dos buscadores no coincide con ningún registro, la tabla muestra el mensaje "No hay reservas que mostrar." sin emitir errores técnicos.
- **Tolerancia de formato en la búsqueda**: El sistema elimina espacios iniciales y finales (trim) y es insensible a mayúsculas/minúsculas en ambos buscadores.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE mostrar al Recepcionista autenticado la pantalla de inicio de jornada compuesta por: encabezado corporativo con nombre del sistema ("Hotel Hospitua · Recepción") y chip de sesión del usuario en turno (nombre y rol), saludo personalizado con el nombre del Recepcionista, subtítulo "Estancias · llegadas y salidas del día", espacio institucional para imagen del hotel, cuatro tarjetas de indicadores operativos (KPIs), y un panel de trabajo con pestañas de Llegadas y Salidas.

- **FR-002**: El sistema DEBE calcular y mostrar los siguientes cuatro indicadores operativos al cargar la pantalla:
  1. **Llegadas de hoy**: total de reservas en estado `ACTIVE` con fecha de inicio igual a la fecha actual, obtenidas de Módulo 2.
  2. **Salidas de hoy**: total de estancias activas locales (`Stay`) con `expectedCheckoutTime` igual a la fecha actual.
  3. **Salidas vencidas**: total de estancias activas locales (`Stay`) con `expectedCheckoutTime` anterior a la fecha actual. Este KPI DEBE mostrarse con color de alerta diferenciado.
  4. **Habitaciones ocupadas**: total de habitaciones en estado `Occupied` en el inventario de Módulo 1.

- **FR-003**: El sistema DEBE disponer de un **buscador independiente en la pestaña Llegadas** para localizar reservas antes de iniciar el proceso de Check-In. Este buscador:
  - Opera contra Módulo 2 mediante la lógica definida en `spec-consultar-reservas.md`.
  - Admite búsqueda por nombre del titular, número de documento e identificador de reserva (`reservationRef`).
  - Filtra en tiempo real la tabla de Llegadas.
  - Muestra el listado por defecto con todas las reservas en estado `ACTIVE` cuya `startDate` coincida con la fecha actual.

- **FR-004**: El sistema DEBE disponer de un **buscador independiente en la pestaña Salidas** para localizar estancias antes de iniciar el proceso de Check-Out. Este buscador:
  - Opera **exclusivamente sobre datos locales de Módulo 1** (`Stay` + `Room` + `RoomGuest`); no realiza ninguna petición a Módulo 2.
  - Admite búsqueda por nombre del titular (`RoomGuest` con `isReservationGuest = true`), número de documento del titular, referencia de reserva (`reservationRef` almacenada en `Stay`) y número de habitación.
  - Filtra en tiempo real la tabla de Salidas.
  - Muestra el listado por defecto con todas las estancias activas cuya `expectedCheckoutTime` sea igual o anterior a la fecha actual (salidas del día y salidas vencidas).

- **FR-005**: La tabla de la pestaña **Llegadas** DEBE presentar las siguientes columnas para cada reserva:
  - Número de habitación asignada.
  - Nombre del huésped titular (con número de documento debajo).
  - Estadía en noches (calculada entre `startDate` y `endDate`).
  - Cantidad de personas de la reserva (`guestCount`).
  - Tipo de habitación.
  - Canal de origen (`source`: "Directo" u "OTA").
  - Botón de acción "Check-in".

- **FR-006**: La tabla de la pestaña **Salidas** DEBE presentar las siguientes columnas para cada estancia:
  - Número de habitación.
  - Nombre del huésped titular (con número de documento debajo), obtenido de `RoomGuest` con `isReservationGuest = true`.
  - Fecha de entrada real (`checkInDate`).
  - Fecha de salida esperada (`expectedCheckoutTime`), con etiqueta visual "Vencida" si la fecha ya expiró.
  - Cantidad total de personas alojadas (total de registros `RoomGuest` vinculados a la `Stay`).
  - Tipo de habitación.
  - Canal de origen (`source` almacenado en `Stay`).
  - Botón de acción "Check-out".

- **FR-007**: Al pulsar el botón "Check-in" en una fila de Llegadas, el sistema DEBE navegar al flujo de Registrar Check-In transfiriendo la referencia de la reserva seleccionada, de modo que el proceso de admisión inicie directamente en el paso 1 (Validar reserva) sin solicitar nuevamente ningún criterio de búsqueda.

- **FR-008**: Al pulsar el botón "Check-out" en una fila de Salidas, el sistema DEBE navegar al flujo de Registrar Check-Out transfiriendo la referencia de la estancia seleccionada, de modo que el proceso de salida inicie directamente en el paso 1 (Consultar reserva) sin solicitar nuevamente ningún criterio de búsqueda.

- **FR-009**: Cuando cualquiera de las dos tablas no tiene registros que mostrar (sin resultados por defecto o sin coincidencias en la búsqueda), el sistema DEBE desplegar el mensaje "No hay reservas que mostrar." en el área de la tabla, sin emitir errores técnicos.

- **FR-010**: Ante indisponibilidad temporal de Módulo 2 al cargar o consultar el listado de Llegadas, el sistema DEBE informar de forma controlada la indisponibilidad del servicio externo sin propagar fallos técnicos y sin afectar la operación de la pestaña Salidas ni los KPIs calculados localmente.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: "Directo" u "OTA"), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `fullName`, `documentType`, `documentNumber`, `nationality` e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ReservationSummary**: Estructura conceptual consumida desde Módulo 2 con los atributos contractuales esenciales: `reservationRef`, `guestRef`, `fullName`, `documentType`, `documentNumber`, `nationality`, `guestCount`, `roomId`, `startDate`, `endDate`, `source` ("Directo" u "OTA") y `status` (`PENDING`, `ACTIVE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`).
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción, consultas, registro de check-in y registro de check-out en el hotel.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El panel de inicio se carga con los 4 KPIs calculados y el listado de Llegadas disponible en menos de 2 segundos en condiciones normales de red.
- **SC-002**: El buscador de Llegadas filtra la tabla en tiempo real mostrando resultados relevantes en menos de 1.5 segundos por consulta a Módulo 2.
- **SC-003**: El buscador de Salidas filtra la tabla en tiempo real de forma instantánea sin necesidad de conexión a Módulo 2.
- **SC-004**: El 100% de los botones "Check-in" transfieren correctamente la referencia de reserva al flujo de admisión sin requerir ningún criterio de búsqueda adicional dentro del proceso.
- **SC-005**: El 100% de los botones "Check-out" transfieren correctamente la referencia de estancia al flujo de salida sin requerir ningún criterio de búsqueda adicional dentro del proceso.
- **SC-006**: Ante indisponibilidad de Módulo 2, el 100% de los KPIs calculados localmente y la pestaña Salidas permanecen operativos sin interrupciones.
