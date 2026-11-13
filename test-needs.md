# Necesidades de Revisión y Pruebas Manuales — Hospitua Módulo 1

## 1. Resumen Ejecutivo
Se verificaron y corrigieron los **23 documentos formales de casos de prueba** del Módulo 1 en `documentos/TEST/` con un total de **378 pasos** (221 de interfaz gráfica, 142 de API REST y 15 de mensajería asíncrona en RabbitMQ), eliminando artefactos inventados y alineando endpoints, parámetros, routing keys y códigos de error con el código real y el plan base.
En total se identificaron **41 puntos concretos que requieren revisión manual** en navegador y clientes REST por parte del equipo de QA.
Estos puntos se concentran en validar la resiliencia ante caídas de Módulo 2 y Módulo 3, concurrencia física entre operarios, comportamiento de modales y la verificación de colas en RabbitMQ.

---

## 2. Detalle por Caso de Prueba

### 2.1. TC-001: Consultar Reservas (`test-consultar-reservas.md`)
* **Verificado contra el código**:
  * Ingesta asíncrona mediante `DailyReservationRabbitListener` consumiendo de la cola durable `m1.reservas.diarias.queue` vinculada a `hospitua.events` con routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`.
  * Consulta local de llegadas del día vía `GET /api/reception/arrivals` sobre la tabla `daily_reservation` (sin llamadas REST a Módulo 2 en tiempo de carga).
  * Adaptador REST `Module2ReservationConflictRestAdapter` consumiendo `GET /api/reservations` de Módulo 2 para verificación de conflictos; captura de fallos con `ExternalModule2UnavailableException` (HTTP 424 `MODULE_2_UNAVAILABLE`).
* **Requiere revisión manual**:
  1. Detener el simulador/mock de Módulo 2 durante la programación de un bloqueo o baja y verificar que la interfaz presenta el mensaje: *"No fue posible verificar las reservas de la habitación con el módulo de reservas. Intenta más tarde."*
  2. Publicar manualmente en RabbitMQ un mensaje con formato JSON inválido en `m1.reservas.diarias.queue` y verificar en la consola de RabbitMQ que es enrutado al Dead-Letter Exchange `hospitua.events.dlx` y depositado en `m1.reservas.diarias.dlq`.
* **Diferencias entre el spec o el plan y el sistema**:
  * En el panel de recepción, las llegadas se leen de la tabla local `daily_reservation` y no invocan a Módulo 2 en tiempo real. Módulo 2 solo es invocado internamente por adaptadores REST al verificar conflictos de reservas para bloqueos técnicos y bajas. `GET /api/reception/arrivals` no requiere encabezado `X-User`.
* **Datos o cuentas que debo preparar antes de probar**:
  * RabbitMQ activo, mock de Módulo 2 en puerto configurado, usuario de Recepción (`recepcionista`).

---

### 2.2. TC-002: Consultar Panel de Recepción (`test-consultar-panel-recepcion.md`)
* **Verificado contra el código**:
  * Endpoints REST desacoplados: `GET /api/reception/arrivals` (llegadas pendientes), `GET /api/reception/departures` (salidas de hoy y vencidas) y `GET /api/reception/panel/kpis` (contadores del turno: `arrivalsToday`, `activeStays`, `departuresToday`, `pendingCheckIns`).
  * Validación de longitud máxima de 100 caracteres en parámetro `query` con HTTP 400 `VALIDATION_ERROR`.
  * Lectura exclusiva desde persistencia local (`daily_reservation`, `stay`) sin encabezado `X-User` obligatorio.
* **Requiere revisión manual**:
  1. Comprobar que en la pestaña Salidas, las estancias vencidas (fecha de salida previa a hoy) se ordenan visualmente de forma prioritaria sobre las salidas programadas para el día.
  2. Verificar que al escribir más de 100 caracteres en el buscador de Llegadas o Salidas, la interfaz valida la longitud e impide la consulta con error de validación.
* **Diferencias entre el spec o el plan y el sistema**:
  * El backend expone tres endpoints independientes (`/arrivals`, `/departures`, `/panel/kpis`) en lugar de un único endpoint consolidado `/panel` con parámetro de pestaña. Ninguno de estos endpoints requiere `X-User`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Semilla de reservas cargada en `daily_reservation` y al menos una estancia activa con fecha vencida en `stay`. Cuenta de recepción (`recepcionista`).

---

### 2.3. TC-003: Registrar Check-In (`test-registrar-check-in.md`)
* **Verificado contra el código**:
  * Flujo wizard de 4 pasos en frontend (`CheckInPage.tsx`): 1. Validar reserva, 2. Datos de huéspedes, 3. Confirmación, 4. Check-In (éxito `CheckInSuccess`).
  * Validación previa sin llamadas a M2 vía `GET /api/check-in/validate?reservationRef={ref}&roomId={uuid}`.
  * Mutación atómica vía `POST /api/check-in` con `X-User: {username}`, creación de `Stay`, `RoomGuest`, cambio a `Occupied` y registro en `outbox_notification`.
  * Despacho por Outbox hacia exchange `hospitua.events` con routing key `habitacion.checkin` hacia la cola `m2.habitacion.checkin.queue` con `CheckInMessagePayload` plano.
* **Requiere revisión manual**:
  1. Abrir el proceso de Check-in para la misma habitación en dos navegadores diferentes e intentar confirmar casi al mismo tiempo; verificar que una sesión confirma exitosamente y la otra recibe HTTP 409 `ROOM_INVALID_STATE` con aviso en pantalla.
  2. Probar el registro intentando ingresar 2 huéspedes en la habitación 101 (sencilla, capacidad máxima: 1) y comprobar el rechazo con HTTP 400 `ROOM_CAPACITY_EXCEEDED`.
* **Diferencias entre el spec o el plan y el sistema**:
  * No existe "firma de aceptación" física o digital en el paso de confirmación; el paso 3 es una confirmación de resumen operativo. La cola de salida hacia Módulo 2 es `m2.habitacion.checkin.queue` con routing key `habitacion.checkin` (no `reserva.check-in-registrado`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación 101 en estado `Reserved` vinculada a `RSV-HAB-101`. Cuenta de recepción (`recepcionista`).

---

### 2.4. TC-004: Consultar Liquidación (`test-consultar-liquidacion.md`)
* **Verificado contra el código**:
  * Endpoint REST `GET /api/stays/{stayId}/settlement` implementado en `SettlementController` con encabezado `Cache-Control: no-store`.
  * Módulo 1 consulta al Módulo 3 a través de `BillingClientPort` y traslada los montos intactos (`lodgingAmount`, comisiones OTA e ingreso neto). Módulo 1 no calcula impuestos localmente.
  * Ante indisponibilidad de Módulo 3, el endpoint responde HTTP 200 OK con `{ "status": "UNAVAILABLE", "unavailableCause": "No fue posible conectar con Facturación." }`.
* **Requiere revisión manual**:
  1. Apagar el simulador de Facturación (Módulo 3) y verificar que en el paso de liquidación de Check-out la pantalla muestra el aviso de contingencia permitiendo continuar sin bloquear la salida física.
  2. Verificar visualmente el formato monetario de los montos de hospedaje en pantalla.
* **Diferencias entre el spec o el plan y el sistema**:
  * La ruta real del endpoint es `GET /api/stays/{stayId}/settlement` (no `/api/billing/settlement/{stayId}`). M1 no calcula impuestos. Cuando Módulo 3 no responde, el endpoint retorna HTTP 200 con `status: "UNAVAILABLE"`, no un HTTP 424.
* **Datos o cuentas que debo preparar antes de probar**:
  * Estancia activa en `stay` con habitación en `Occupied`.

---

### 2.5. TC-005: Registrar Check-Out (`test-registrar-check-out.md`)
* **Verificado contra el código**:
  * Consulta de estancia activa local vía `GET /api/check-out/active-stay?reservationRef={ref}&roomId={uuid}`.
  * Formalización de salida vía `POST /api/check-out` con `X-User: {username}` y body `{"reservationRef": "...", "roomId": "..."}`.
  * Cierre de estancia, transición de `Occupied` a `PendingCleaning` y registro de dos mensajes en Outbox:
    1. Routing key `habitacion.checkout` hacia `m2.habitacion.checkout.queue`.
    2. Routing key `facturacion.checkout` en `hospitua.events` hacia Módulo 3.
  * Pantalla de éxito `CheckOutSuccess` con aviso de liberación a limpieza y botón "Volver al inicio".
* **Requiere revisión manual**:
  1. Ejecutar el check-out y verificar en el Panel de Limpieza (`/limpieza`) que la tarjeta de la habitación liberada aparece de inmediato en estado `PendingCleaning`.
  2. Inspeccionar en la cola de RabbitMQ que se emitieron los dos mensajes con sus routing keys correspondientes (`habitacion.checkout` y `facturacion.checkout`).
* **Diferencias entre el spec o el plan y el sistema**:
  * En la pantalla de éxito (`CheckOutSuccess`), no existe botón ni acción de imprimir comprobante; solo se informa que la habitación pasó a limpieza y se ofrece el enlace de retorno a recepción. No existe routing key `habitacion.pendiente-a-limpieza` en RabbitMQ (el cambio es local).
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación ocupada con estancia activa. Cuenta de recepcionista (`recepcionista`).

---

### 2.6. TC-006: Consultar Tarifa Base (`test-consultar-tarifa-base.md`)
* **Verificado contra el código**:
  * Endpoint REST único `GET /api/rooms/{roomId}/base-rate` con parámetro opcional `date=YYYY-MM-DD`.
  * Integración intermódulo para Módulo 3: no requiere encabezado `X-User`.
  * Respuesta serializada con `roomId`, `roomNumber`, `categoryRoom`, `baseRate` (con precisión decimal) y `effectiveDate`.
* **Requiere revisión manual**:
  1. Invocar el endpoint mediante Postman o cURL con un UUID inexistente y comprobar la respuesta HTTP 404 Not Found con `errorCode: "ROOM_NOT_FOUND"`.
* **Diferencias entre el spec o el plan y el sistema**:
  * El endpoint solo consulta por `roomId` (`GET /api/rooms/{roomId}/base-rate`); no existe endpoint de consulta de tarifa base agrupada por categoría (`GET /api/categories/{category}/base-rate`). No requiere `X-User`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Semilla V2 cargada con habitaciones en diversos pisos.

---

### 2.7. TC-007: Marcar Habitación como Reservada (`test-marcar-habitacion-reservada.md`)
* **Verificado contra el código**:
  * Ingesta por RabbitMQ en `m1.reservas.diarias.queue` procesando `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion` con `action: "ADDED"`.
  * Transición atómica de `Available` a `Reserved`, asignación de `reservedByReservationRef` y registro en `room_state_history` con `sourceFlow: "ASSIGN_RESERVATION"`.
  * Idempotencia comprobada contra `daily_reservation_message_log`.
* **Requiere revisión manual**:
  1. Publicar un mensaje `reserva.lista-del-dia.actualizacion` con acción `ADDED` para la habitación 103 y verificar en `/administracion/inventario` que el badge cambia inmediatamente a "Reservada".
  2. Reenviar el mismo mensaje y verificar en base de datos que no se duplica la fila en `room_state_history`.
* **Diferencias entre el spec o el plan y el sistema**:
  * Módulo 1 no emite ningún mensaje hacia RabbitMQ al reservar una habitación; el apartado es un proceso interno consumidor.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación 103 en estado `Available`. RabbitMQ activo.

---

### 2.8. TC-008: Marcar Habitación como Disponible (`test-marcar-habitacion-como-disponible.md`)
* **Verificado contra el código**:
  * Transición a `Available` disparada por: cancelación en lista diaria (`REMOVED`), fin de limpieza ordinaria sin reserva para hoy, o reactivación administrativa vía `POST /api/rooms/{roomId}/reactivation`.
  * Endpoint `GET /api/rooms/{roomId}/decommission` para consultar la baja antes de reactivar.
* **Requiere revisión manual**:
  1. En `/administracion/inventario`, filtrar por "Inactiva", seleccionar la habitación 104, navegar a `/reactivacion`, diligenciar justificación y pulsar reactivar; comprobar que retorna a estado "Disponible".
* **Diferencias entre el spec o el plan y el sistema**:
  * Sin diferencias.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación 104 en estado `Inactive` (semilla V2). Cuenta de Administrador (`admin`).

---

### 2.9. TC-009: Marcar Pendiente a Limpieza (`test-marcar-pendiente-a-limpieza.md`)
* **Verificado contra el código**:
  * Transición de dominio autónoma disparada por `POST /api/check-out` (`Occupied` → `PendingCleaning`) o `POST /api/maintenance/tasks/{taskId}/complete` (`DisabledForRepairs`/`TechnicalBlock` → `PendingCleaning`).
  * Inserción automática en `room_state_history`.
* **Requiere revisión manual**:
  1. Completar un Check-Out desde recepción y verificar en el Panel de Limpieza (`/limpieza`) que la habitación ingresa inmediatamente en la lista de pendientes.
* **Diferencias entre el spec o el plan y el sistema**:
  * No existe endpoint directo ni evento de RabbitMQ para esta transición; es un cambio de estado interno en la misma transacción que el check-out o fin de reparación.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación ocupada o en reparación.

---

### 2.10. TC-010: Marcar Habitación en Limpieza (`test-marcar-habitacion-en-limpieza.md`)
* **Verificado contra el código**:
  * Endpoint `POST /api/cleaning/tasks` con `X-User: {username}` y body `{"roomId": "..."}`.
  * Creación de tarea `CleaningTaskStartedDto`, habitación pasa a `InCleaning`.
  * Restricción de una sola tarea activa por miembro (`ACTIVE_TASK_EXISTS`) y una sola por habitación (`ROOM_TASK_EXISTS`).
* **Requiere revisión manual**:
  1. Iniciar sesión con `limpiador1` e iniciar limpieza en habitación 101; en otra ventana con `limpiador2` intentar tomar la misma habitación y verificar el mensaje: *"Otro compañero ya está limpiando esta habitación."*
  2. Verificar que al tener una tarea activa, si el usuario navega a `/limpieza` es redirigido defensivamente a `/limpieza/tarea`.
* **Diferencias entre el spec o el plan y el sistema**:
  * El sistema no implementa permisos por área (no hay 403 `FORBIDDEN_AREA`); la validación de `X-User` comprueba existencia y cuenta activa.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación en `PendingCleaning`. Dos cuentas de personal de limpieza (`limpiador1`, `limpiador2`).

---

### 2.11. TC-011: Confirmar Fin de Limpieza (`test-confirmar-fin-de-limpieza.md`)
* **Verificado contra el código**:
  * Endpoints `POST /api/cleaning/tasks/{taskId}/complete` y `POST /api/cleaning/tasks/{taskId}/release` respondiendo ambos HTTP 204 No Content.
  * Transición condicional: si la habitación tiene reserva asignada para hoy pasa a `Reserved`; si no tiene reserva, pasa a `Available`.
  * Validación de pertenencia de tarea con HTTP 403 `TASK_NOT_OWNED` si otro miembro intenta cerrarla.
* **Requiere revisión manual**:
  1. Probar en pantalla la confirmación de fin en una habitación con reserva para hoy (hab. 101) y constatar que muestra el aviso: *"Limpieza terminada. La habitación quedó reservada para la llegada de hoy."*
  2. Probar la opción "Liberar tarea" en `/limpieza/tarea` y verificar que la habitación vuelve a figurar como pendiente en el panel.
* **Diferencias entre el spec o el plan y el sistema**:
  * Los endpoints de completar y liberar responden HTTP 204 No Content (sin cuerpo JSON). Módulo 1 no publica eventos de fin de limpieza a RabbitMQ.
* **Datos o cuentas que debo preparar antes de probar**:
  * Tarea de limpieza activa asignada al usuario autenticado.

---

### 2.12. TC-012: Consultar Panel de Limpieza (`test-consultar-panel-limpieza.md`)
* **Verificado contra el código**:
  * Endpoint `GET /api/cleaning/panel` con `X-User: {username}`.
  * Retorno de `CleaningPanelDto` conteniendo `activeTask` (si el operario tiene una tarea en curso) y `pendingRooms`.
  * Filtros por piso implementados en frontend React.
* **Requiere revisión manual**:
  1. Probar el selector de filtros por piso en `/limpieza` y verificar que las tarjetas se filtran correctamente sin recargar la página.
  2. Verificar el mensaje de estado vacío cuando no existen habitaciones pendientes de aseo.
* **Diferencias entre el spec o el plan y el sistema**:
  * No existe rechazo por rol de área (`403 FORBIDDEN_AREA`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Usuario con cuenta activa de limpieza (`limpiador1`).

---

### 2.13. TC-013: Marcar Habitación Inhabilitada por Reparaciones (`test-marcar-habitacion-inhabilitada-por-reparaciones.md`)
* **Verificado contra el código**:
  * Endpoints REST reales bajo `/api/rooms/{roomId}/damage-reports`:
    * `POST /api/rooms/{roomId}/damage-reports` con body `{"damageDescription": "..."}` y `X-User: {username}` -> HTTP 201 Created con `DamageReportCreatedDto`.
    * `GET /api/rooms/{roomId}/damage-reports/latest` -> HTTP 200 OK con `DamageReportViewDto`.
  * Cancelación de tarea de limpieza activa y paso de habitación a `DisabledForRepairs`.
  * Emisión de aviso por Outbox en `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue`.
* **Requiere revisión manual**:
  1. Abrir diálogo de reporte de daño desde una tarea de limpieza activa, completar descripción y confirmar; verificar que la habitación desaparece de limpieza y aparece en `/mantenimiento` con estado `DisabledForRepairs`.
  2. En el panel de mantenimiento, hacer clic en "Ver detalle del daño" y verificar la correcta visualización de la descripción ingresada.
* **Diferencias entre el spec o el plan y el sistema**:
  * El endpoint de reporte es `POST /api/rooms/{roomId}/damage-reports` (con `roomId` en el path y body con solo la descripción) y no `/api/maintenance/reports/damage`. El routing key en RabbitMQ es `habitacion.mantenimiento` (no `habitacion.bloqueada-por-mantenimiento`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación en `InCleaning` con tarea abierta. Personal de limpieza y mantenimiento.

---

### 2.14. TC-014: Confirmar Reparación Finalizada (`test-confirmar-reparacion-finalizada.md`)
* **Verificado contra el código**:
  * Endpoints bajo `/api/maintenance`:
    * `POST /api/maintenance/tasks` con body `{"roomId": "..."}` -> HTTP 201 Created con `RepairTaskStartedDto`.
    * `POST /api/maintenance/tasks/{taskId}/complete` con `X-User` -> HTTP 204 No Content.
    * `POST /api/maintenance/tasks/{taskId}/release` con `X-User` -> HTTP 204 No Content.
  * Transición de habitación reparada a `PendingCleaning`. Si es bloqueo técnico, cierre del informe a `Completed`.
* **Requiere revisión manual**:
  1. Iniciar y completar una reparación desde `/mantenimiento/tarea` y comprobar que la habitación pasa a limpieza sin parpadeos ni errores de navegación.
  2. Probar la liberación de la tarea técnica y comprobar que la habitación permanece en `DisabledForRepairs` en el panel general.
* **Diferencias entre el spec o el plan y el sistema**:
  * Los endpoints de completar y liberar reparación devuelven HTTP 204 No Content (sin cuerpo). No se emite evento `habitacion.reparada` a RabbitMQ.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación 101 en `DisabledForRepairs`. Técnico de mantenimiento (`mtorres`).

---

### 2.15. TC-015: Programar Bloqueo Técnico para Habitación (`test-programar-bloqueo-tecnico-para-habitacion.md`)
* **Verificado contra el código**:
  * Componente modal `ScheduleTechnicalBlockDialog.tsx` y endpoints:
    * `POST /api/rooms/{roomId}/technical-blocks` con body `{"reason": "...", "startDate": "YYYY-MM-DD", "estimatedEndDate": "YYYY-MM-DD"}` y `X-User`.
    * `GET /api/rooms/{roomId}/technical-blocks/current` -> HTTP 200 OK con `TechnicalBlockViewDto`.
  * Emisión de aviso por Outbox en `hospitua.events` con routing key `habitacion.mantenimiento` hacia `m2.habitacion.mantenimiento.queue`.
* **Requiere revisión manual**:
  1. Intentar programar un bloqueo en fechas donde Módulo 2 reporta reserva activa y verificar el rechazo con el mensaje exacto en el diálogo: *"La habitación tiene reservas confirmadas en las fechas seleccionadas."*
  2. Programar un bloqueo técnico con fecha de inicio hoy y verificar que la habitación pasa de inmediato a `TechnicalBlock` con informe `Applied`.
* **Diferencias entre el spec o el plan y el sistema**:
  * El routing key de RabbitMQ es `habitacion.mantenimiento`. El endpoint de consulta del bloqueo actual es `GET /api/rooms/{roomId}/technical-blocks/current`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación en `Available`. Mantenimiento (`mtorres`).

---

### 2.16. TC-016: Consultar Información de Mantenimientos (`test-consultar-informacion-mantenimientos.md`)
* **Verificado contra el código**:
  * Endpoint REST `GET /api/rooms/{roomId}/maintenances` con parámetros de query `startDate` y `endDate`.
  * Integración Módulo 2 → Módulo 1 sin `X-User`.
  * Respuesta serializada con `roomId`, `available: boolean` y `conflicts: [{ maintenanceStart, maintenanceEnd }]`.
* **Requiere revisión manual**:
  1. Probar en Postman/cURL llamadas con fechas donde no hay bloqueos (devuelve `available: true, conflicts: []`) y fechas solapadas con un bloqueo técnico (devuelve `available: false` y el rango del bloqueo).
* **Diferencias entre el spec o el plan y el sistema**:
  * Los parámetros son `startDate` y `endDate` (no `firstDay`/`lastDay`), y la lista devuelta se llama `conflicts` con `maintenanceStart` y `maintenanceEnd`. Actualmente no existe pantalla dedicada de calendario fuera del diálogo modal de programación (`ScheduleTechnicalBlockDialog.tsx`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación con bloqueo programado en la base de datos.

---

### 2.17. TC-017: Consultar Panel de Mantenimiento (`test-consultar-panel-mantenimiento.md`)
* **Verificado contra el código**:
  * Ruta `/mantenimiento` (`MaintenancePanelPage.tsx`) consumiendo `GET /api/maintenance/panel` con `X-User: {username}`.
  * Entrega de `MaintenancePanelDto` con `activeTask` (si el técnico tiene una reparación abierta) y `pendingRooms` (habitaciones en `DisabledForRepairs` y `TechnicalBlock`).
* **Requiere revisión manual**:
  1. Iniciar sesión teniendo una reparación activa y verificar la redirección automática a `/mantenimiento/tarea`.
  2. Comprobar la visualización del estado vacío cuando no existen habitaciones pendientes de intervención técnica.
* **Diferencias entre el spec o el plan y el sistema**:
  * No existe restricción por área (no hay `403 FORBIDDEN_AREA`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Personal de mantenimiento (`mtorres`).

---

### 2.18. TC-018: Registrar Habitación (`test-registrar-habitacion.md`)
* **Verificado contra el código**:
  * Ruta `/administracion/habitaciones/nueva` (`RegisterRoomPage.tsx`) y endpoint `POST /api/rooms` con `X-User: {username}`.
  * Validaciones de unicidad de número (`ROOM_NUMBER_ALREADY_EXISTS`), piso > 0, capacidad > 0, categorías (SENCILLA, DOBLE, SUITE, BOUTIQUE) y tarifa con hasta dos decimales.
* **Requiere revisión manual**:
  1. En `/administracion/habitaciones/nueva`, ingresar número de habitación "101" y verificar que al guardar el sistema muestra el mensaje: *"Ya existe una habitación con el número 101."*
  2. Registrar la habitación "407" y comprobar que aparece de inmediato en `/administracion/inventario` en estado "Disponible".
* **Diferencias entre el spec o el plan y el sistema**:
  * La base de datos contiene una semilla V2 con 14 habitaciones iniciales (pisos 1, 2 y 3: 12 Available y 2 Inactive: 104 y 204), no 10.
* **Datos o cuentas que debo preparar antes de probar**:
  * Administrador (`admin`). Número libre para registrar (ej. "407").

---

### 2.19. TC-019: Editar Habitación (`test-editar-habitacion.md`)
* **Verificado contra el código**:
  * Ruta `/administracion/habitaciones/:roomId/editar` (`EditRoomPage.tsx`) con diálogo modal `EditRoomConfirmation.tsx`.
  * Endpoint `PATCH /api/rooms/{roomId}` con `X-User: {username}`, cuerpo con `version` obligatoria y atributos a modificar.
  * Control optimista de concurrencia: si la versión no coincide, responde HTTP 409 `CONCURRENT_UPDATE`.
* **Requiere revisión manual**:
  1. Abrir la edición de la habitación 102 en dos pestañas; guardar un cambio de tarifa en la primera pestaña y luego intentar guardar en la segunda pestaña para verificar el mensaje: *"La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales."*
  2. Comprobar que el modal de confirmación resalta adecuadamente la comparativa "Antes / Ahora".
* **Diferencias entre el spec o el plan y el sistema**:
  * El errorCode de colisión concurrente en la edición de habitación es `CONCURRENT_UPDATE` (mapeado desde `ConcurrentUpdateException`).
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación en `Available` (`version = 0`). Dos sesiones de administrador.

---

### 2.20. TC-020: Dar de Baja Habitación (`test-dar-de-baja-habitacion.md`)
* **Verificado contra el código**:
  * Ruta `/administracion/habitaciones/:roomId/baja` (`DecommissionPage.tsx`).
  * Endpoints REST:
    * `POST /api/rooms/{roomId}/decommission` con `X-User: {username}` y body `{"reasonCategory": "...", "reasonDetail": "..."}`.
    * `GET /api/rooms/{roomId}/decommission` para consultar la baja vigente.
  * Verificación interna con Módulo 2 para comprobar ausencia de reservas en 365 días.
* **Requiere revisión manual**:
  1. Intentar dar de baja una habitación que tiene reserva activa en Módulo 2 y verificar el rechazo con el mensaje: *"No se puede dar de baja la habitación: tiene reservas confirmadas en los próximos 365 días."*
  2. Confirmar la baja de una habitación libre y verificar que en el inventario su badge pasa a "Inactiva" y se habilita la opción de reactivación.
* **Diferencias entre el spec o el plan y el sistema**:
  * El endpoint de consulta de la baja actual es `GET /api/rooms/{roomId}/decommission`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Habitación disponible libre y habitación con reserva futura simulada en M2. Administrador (`admin`).

---

### 2.21. TC-021: Consultar Inventario de Habitaciones (`test-consultar-inventario-habitaciones.md`)
* **Verificado contra el código**:
  * Rutas `/administracion/inventario` (administración) y `/gerencia/inventario` (gerencia).
  * Endpoints REST:
    * `GET /api/rooms/inventory?page=1&size=10` (no requiere `X-User`).
    * `GET /api/rooms/inventory/{roomId}` (no requiere `X-User`).
    * `GET /api/rooms?categoryRoom={cat}` y `GET /api/rooms/{roomId}` (para Módulo 2, excluyen habitaciones Inactive).
* **Requiere revisión manual**:
  1. Iniciar sesión como Gerente y constatar que en `/gerencia/inventario` no se renderizan los botones "Nueva habitación", "Editar" ni "Dar de baja".
  2. Probar los filtros por tipo y piso verificando que la paginación conserva los filtros activos.
* **Diferencias entre el spec o el plan y el sistema**:
  * Los endpoints de inventario general (`/api/rooms/inventory` e `/api/rooms/inventory/{roomId}`) son de solo lectura y no exigen el encabezado `X-User`. La semilla V2 contiene 14 habitaciones iniciales.
* **Datos o cuentas que debo preparar antes de probar**:
  * Administrador (`admin`) y Gerente (`gerente`).

---

### 2.22. TC-022: Consultar Historial de Estados (`test-consultar-historial-de-estados.md`)
* **Verificado contra el código**:
  * Ruta `/gerencia/historial` (`RoomStateHistoryPage.tsx`) consumiendo `GET /api/room-state-history`.
  * Parámetros de consulta: `page`, `from`, `to`, `roomId`, `roomNumber`, `categoryRoom`, `status`, `sort`, `direction`. No exige `X-User`.
  * Paginación en bloques de 15 registros (`size = 15`).
* **Requiere revisión manual**:
  1. Filtrar por rango de fechas ingresando una fecha "Desde" posterior a "Hasta" y verificar el mensaje: *"La fecha "Desde" no puede ser posterior a "Hasta"."*
  2. Verificar que los periodos activos con `endDateTime == null` muestran el badge "En curso" sin fallar en el cálculo de duración.
* **Diferencias entre el spec o el plan y el sistema**:
  * Los parámetros de fechas en la URL son `from` y `to` (no `startDateFrom`/`startDateTo`). El tamaño de página por defecto en el backend es 15 (no 10). La consulta no exige encabezado `X-User`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Gerente (`gerente`). Habitación con historial de transiciones.

---

### 2.23. TC-023: Gestionar Personal (`test-gestionar-personal.md`)
* **Verificado contra el código**:
  * Rutas `/administracion/personal`, `/administracion/personal/nuevo` y `/administracion/personal/:username/editar`.
  * Endpoints REST bajo `/api/staff`:
    * `POST /api/staff` (requiere `X-User`).
    * `GET /api/staff?page=1` (no requiere `X-User`).
    * `GET /api/staff/{username}` (no requiere `X-User`).
    * `PATCH /api/staff/{username}` (requiere `X-User`).
    * `POST /api/staff/{username}/deactivation` (requiere `X-User`).
  * Endpoint `POST /api/auth/login`: cuando una cuenta está desactivada (`active = false`), `LoginService` lanza `InvalidCredentialsException` retornando HTTP 401 `INVALID_CREDENTIALS` con el mensaje genérico idéntico a contraseña incorrecta: *"Usuario o contraseña incorrectos."*.
* **Requiere revisión manual**:
  1. En `/administracion/personal`, comprobar que el botón "Desactivar" se encuentra deshabilitado para el usuario administrador conectado actualmente (`admin`).
  2. Desactivar una cuenta de pruebas e intentar iniciar sesión en `/login` con sus credenciales válidas; verificar que el sistema rechaza el ingreso con el mensaje genérico: *"Usuario o contraseña incorrectos."*
  3. Intentar desactivar o cambiar el área de la única cuenta con área `ADMINISTRACION` y constatar el rechazo con el mensaje: *"No se puede desactivar: es la única cuenta activa de Administración."*
* **Diferencias entre el spec o el plan y el sistema**:
  * Cuando una cuenta está desactivada, el login responde exactamente con el mismo código y mensaje que unas credenciales incorrectas (HTTP 401 `INVALID_CREDENTIALS`, *"Usuario o contraseña incorrectos."*) para evitar la enumeración de usuarios. Las consultas `GET /api/staff` no requieren encabezado `X-User`.
* **Datos o cuentas que debo preparar antes de probar**:
  * Cuenta de Administrador principal (`admin`).

---

## 3. Lista Final de Prioridades de Prueba Manual

El equipo de QA debe abordar las pruebas manuales siguiendo este orden riguroso de prioridad operativa:

### Prioridad 1 — Flujos Críticos de Operación Diaria (P1)
1. **TC-003 Registrar Check-In**: Probar en navegador la concurrencia de dos recepcionistas sobre la misma habitación y la validación de capacidad de huéspedes.
2. **TC-005 Registrar Check-Out & TC-004 Consultar Liquidación**: Probar el comportamiento del flujo ante la desconexión simulada de Módulo 3 y la emisión de los dos eventos en RabbitMQ (`habitacion.checkout` y `facturacion.checkout`).
3. **TC-010 Marcar Habitación en Limpieza & TC-011 Confirmar Fin de Limpieza**: Probar la concurrencia entre dos camareros intentando iniciar la misma habitación y la transición atómica hacia `Reserved` cuando hay reserva para el día.
4. **TC-013 Reportar Daño & TC-014 Confirmar Reparación**: Probar el flujo completo de inhabilitación por daño (`POST /api/rooms/{roomId}/damage-reports`), su aparición en el panel de mantenimiento y su posterior retorno a limpieza respondiendo 204 No Content.
5. **TC-015 Programar Bloqueo Técnico**: Probar la validación de conflictos con reservas activas provenientes de Módulo 2 y la aplicación inmediata cuando la fecha es hoy.
6. **TC-001 Consultar Reservas & TC-007 Marcar Habitación Reservada**: Probar la resiliencia en recepción ante la caída de Módulo 2 y la ingesta de eventos de RabbitMQ (`reserva.lista-del-dia`).

### Prioridad 2 — Administración, Inventario y Personal (P2)
7. **TC-019 Editar Habitación**: Probar la colisión por control optimista de `version` (`CONCURRENT_UPDATE`) en dos navegadores simultáneos y la pantalla "Antes / Ahora".
8. **TC-020 Dar de Baja Habitación**: Probar el rechazo por reservas futuras dentro de los 365 días y la captura del error 424 si Módulo 2 no responde.
9. **TC-023 Gestionar Personal**: Probar la protección contra auto-desactivación, la regla del último administrador y la respuesta unificada de error en login para cuentas inactivas.
10. **TC-018 Registrar Habitación**: Probar validaciones de tipos de datos en el formulario y unicidad de número.
11. **TC-021 Consultar Inventario & TC-002 Panel de Recepción**: Probar usabilidad de filtros, atajos de búsqueda y visualización diferenciada por rol (Admin vs Gerencia).
12. **TC-017 Consultar Panel de Mantenimiento & TC-012 Consultar Panel de Limpieza**: Probar visualización en dispositivos móviles o ventanas compactas y refresco de estado.
13. **TC-022 Consultar Historial de Estados**: Probar renderizado de periodos activos "En curso" y validaciones de rango de fechas con parámetros `from` y `to`.

### Prioridad 3 — Consultas Secundarias y Validaciones de Solo Lectura (P3)
14. **TC-006 Consultar Tarifa Base & TC-016 Consultar Mantenimientos**: Validar contratos REST en Postman (`startDate`/`endDate` en mantenimientos y consulta exclusiva por `roomId` en tarifa base).

*(Nota: Todos los casos cuentan con cobertura exhaustiva automatizada tanto en backend Spring Boot como en frontend Vitest; los 41 puntos listados representan estrictamente validaciones exploratorias, de concurrencia física y de experiencia de usuario).*
