# Especificación de Funcionalidad: Enviar Datos de Huéspedes Extranjeros

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía extensión en "Procesar datos de huéspedes")
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Asignación automática de datos migratorios para extranjeros (Prioridad: P1)

Como Recepcionista, quiero que el sistema asigne automáticamente los datos de control migratorio (tipo de movimiento "ENTRADA" y fecha `checkInTime`) a todo ocupante de nacionalidad extranjera capturado, sin solicitármelos manualmente, para conformar el paquete de datos migratorios exigido por las autoridades (SIRE) sin fricción operativa ni errores de digitación.

**Por qué esta prioridad**: Garantiza el cumplimiento de la legislación migratoria sin entorpecer el flujo ágil de recepción ni desvirtuar el modelo de datos de Módulo 1. Al activarse condicionalmente desde el Check-In, el tipo de movimiento es forzosamente "ENTRADA" y la fecha corresponde a la hora real de llegada (`checkInTime`); ambos son valores deterministas que el sistema ya posee, justificando que no se soliciten manualmente y el sistema informe oportunamente el envío a Módulo 2 (SIRE).

**Prueba Independiente**: Se prueba de forma aislada registrando a un ocupante con nacionalidad extranjera en "Procesar datos de huéspedes", verificando que el sistema informe el envío a Módulo 2 (SIRE) y estructure internamente los parámetros migratorios (`movementType = "ENTRADA"`, `fecha = checkInTime`) sin requerir datos adicionales al Recepcionista.

**Escenarios de Aceptación**:

1. **Escenario**: Asignación automática de datos migratorios al detectar extranjero (Happy Path)
   - **Dado** que durante la captura de ocupantes se registra a una persona con nacionalidad extranjera
   - **Cuando** se validan los datos y se avanza hacia la formalización del Check-In
   - **Entonces** el sistema informa al Recepcionista que los datos migratorios se enviarán a Módulo 2 (SIRE) y asocia automáticamente al ocupante extranjero el tipo de movimiento "ENTRADA" y la fecha correspondiente al `checkInTime` de la Estancia, sin requerir estos datos al Recepcionista y sin persistirlos en la entidad local RoomGuest.

2. **Escenario**: Supresión de armado migratorio ante ausencia de `checkInTime`
   - **Dado** que por una interrupción interna no se ha generado una marca de tiempo válida de llegada (`checkInTime`)
   - **Cuando** el sistema prepara el paquete migratorio para su consolidación
   - **Entonces** el sistema bloquea el despacho del paquete migratorio hacia Módulo 2 hasta contar con el `checkInTime` definitivo, previniendo notificaciones con fechas incompletas o inconsistentes.

3. **Escenario**: Coexistencia de huéspedes nacionales y extranjeros
   - **Dado** una habitación con un ocupante nacional y un ocupante extranjero
   - **Cuando** se completa la captura de ocupantes
   - **Entonces** el sistema activa la extensión migratoria filtrando exclusivamente los datos del ocupante extranjero para el paquete de Módulo 2, preservando a ambos localmente como `RoomGuest` para aforo.

---

### Historia de Usuario 2 - Consolidación en la notificación a Módulo 2 y resiliencia (Prioridad: P2)

Como Recepcionista, quiero que los datos migratorios de los extranjeros se consoliden en la notificación de Check-In despachada a Módulo 2 (SIRE), asegurando que cualquier lentitud o falla externa no impida ni retrase la entrega física de la habitación.

**Por qué esta prioridad**: Asegura la sincronización con Módulo 2 consolidando en una única petición el cambio de estado de la reserva y el registro migratorio (según lo definido en el contrato de Módulo 2), manteniendo la resiliencia operativa para que una indisponibilidad externa nunca deje al huésped esperando en el mostrador.

**Prueba Independiente**: Se ejecuta un Check-In con huéspedes extranjeros simulando caída temporal o falta de respuesta de Módulo 2, comprobando que la habitación pase de inmediato a estado "Occupied" en Módulo 1, que se complete la admisión física y que el paquete migratorio se registre para reintento en segundo plano.

**Escenarios de Aceptación**:

1. **Escenario**: Consolidación y envío exitoso en la notificación de Check-In a Módulo 2
   - **Dado** que se han registrado ocupantes con uno o más extranjeros
   - **Cuando** el Recepcionista confirma la admisión física del Check-In
   - **Entonces** el sistema despacha hacia el servicio de Módulo 2 la notificación de Check-In consolidando en la misma comunicación la `reservationRef` y los datos migratorios de los extranjeros (identidad, tipo de movimiento "ENTRADA" y fecha `checkInTime`), recibiendo confirmación exitosa de Módulo 2.

2. **Escenario**: Resiliencia ante falla o lentitud externa de Módulo 2
   - **Dado** que se confirma el Check-In pero el servicio de Módulo 2 no responde o experimenta demoras
   - **Cuando** el sistema intenta despachar la notificación migratoria consolidada
   - **Entonces** el sistema no detiene la operación física, transiciona la habitación a "Occupied", crea la Estancia local y programa el reintento de la notificación en segundo plano sin bloquear la operación física ni emitir errores no controlados al Recepcionista.

---

### Casos Borde

- **Múltiples ocupantes extranjeros en la misma habitación**: Si tanto el Huésped Titular como los acompañantes son extranjeros, el sistema consolida a todos en el paquete migratorio, asignando a cada uno sus datos individuales de identidad con el mismo `movementType = "ENTRADA"` y la misma fecha (`checkInTime`).
- **Reenvío ante notificaciones previas**: Si la notificación consolidada se retransmite hacia una reserva que ya figura en `IN_PROGRESS` en Módulo 2 con `MigratoryMovement` en estado `INCOMPLETE`, Módulo 2 actualiza el movimiento a `COMPLETE` y confirma la operación sin generar registros duplicados.
- **Pendiente de confirmación contractual sobre `guestRef` para acompañantes extranjeros**: En el modelo de Módulo 2, `MigratoryMovement.guestRef` referencia a una entidad `Guest`. Dado que en Módulo 2 `Guest` representa históricamente al titular de la reserva, queda registrado como pendiente coordinar si Módulo 2 implementará la creación o actualización automática de `Guest` para los acompañantes extranjeros o si vinculará el movimiento directamente a la reserva.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso condicional que extiende (`<<extend>>`) a "Procesar datos de huéspedes", activándose automáticamente cuando la nacionalidad de uno o más ocupantes registrados sea extranjera.
- **FR-002**: Para cada ocupante extranjero detectado en "Procesar datos de huéspedes", el sistema DEBE asignar automáticamente:
  - Tipo de movimiento migratorio: `"ENTRADA"`
  - Fecha del movimiento: `checkInTime` de la Estancia
  sin solicitar ninguno de estos dos parámetros al Recepcionista.
- **FR-003**: Los datos de identidad del extranjero (Nombre completo, tipo y número de documento, nacionalidad) DEBEN reutilizarse directamente desde los datos capturados en "Procesar datos de huéspedes", sin volver a solicitarlos.
- **FR-004**: El sistema DEBE estructurar el paquete migratorio de extranjeros para ser remitido y consolidado dentro de la misma petición de notificación de Check-In despachada hacia el servicio de Módulo 2 (SIRE).
- **FR-005**: El sistema DEBE condicionar el armado y envío del paquete migratorio a la generación exitosa de un `checkInTime` válido generado en la confirmación local de la Estancia.
- **FR-006**: La entrega o respuesta del servicio de Módulo 2 NO DEBE ser una precondición bloqueante para la creación de la `Estancia` ni para la transición síncrona de la habitación a "Occupied" en el inventario de Módulo 1.
- **FR-007**: Si Módulo 2 experimenta fallas de conectividad, demoras o fallas de comunicación, el sistema DEBE registrar la notificación migratoria para reintento automático en segundo plano, garantizando que el Recepcionista culmine la entrega física de la unidad sin demoras.
- **FR-008**: El sistema DEBE registrar en la bitácora de auditoría la referencia de la reserva, los documentos de los extranjeros notificados y la confirmación o programación de reintento del envío migratorio.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **RoomGuest**: Entidad conceptual del ocupante en Módulo 1 evaluada en su nacionalidad para activar esta extensión.
- **ForeignMigratoryData**: Estructura conceptual efímera de control migratorio conteniendo: tipo de movimiento (`ENTRADA`), fecha (`checkInTime`), y los datos de identidad requeridos por el reporte SIRE.
- **Module2 (MigratoryValidation / SIRE)**: Componente externo en Módulo 2 encargado de recibir los datos migratorios consolidados, registrarlos en `MigratoryMovement` y generar el reporte plano `.TXT` para Migración Colombia.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de los ocupantes extranjeros capturados cuentan con sus datos migratorios ("ENTRADA" y `checkInTime`) asignados automáticamente sin intervención manual del Recepcionista.
- **SC-002**: El paquete migratorio se consolida y despacha en la petición de notificación de Check-In hacia Módulo 2 de forma inmediata tras la confirmación física.
- **SC-003**: Cero bloqueos de entrega de llaves o de ocupación de habitaciones físicas en Módulo 1 ocasionados por lentitud o caídas de red al transmitir datos migratorios a Módulo 2.
- **SC-004**: El 100% de las notificaciones que no pudieron ser entregadas en el primer intento quedan programadas para reintento en segundo plano.
