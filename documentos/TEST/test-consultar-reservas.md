### Test Case Specification: Consultar Reservas
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) / [plan-consultar-reservas.md](../PLAN/plan-consultar-reservas.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-001-ConsultarReservas
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica que la ingesta asíncrona de reservas del día, la búsqueda local de llegadas en mostrador y la consulta síncrona de conflictos con Módulo 2 funcionen correctamente bajo condiciones operativas normales y excepcionales.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Consultar Reservas | Specification Document | [documentos/SPEC/spec-consultar-reservas.md](../SPEC/spec-consultar-reservas.md) |
| Plan de Implementación de Consultar Reservas | Plan Document | [documentos/PLAN/plan-consultar-reservas.md](../PLAN/plan-consultar-reservas.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere los servicios del sistema en marcha: backend Spring Boot, base de datos PostgreSQL, broker RabbitMQ y servicio o simulador de Módulo 2 disponible para pruebas de integración REST.

##### Dependencies & Assumptions
* TC-007 Marcar habitación como reservada y TC-008 Marcar habitación como disponible deben estar operativos para el apartado y liberación de habitaciones.
* Las reservas del día se reciben exclusivamente a través del broker de mensajería; no existen peticiones HTTP síncronas a Módulo 2 para la atención en recepción.

##### Preconditions & Default Setup
* **Initial State** : Base de datos con semilla V2 cargada (habitaciones 101, 102, 103, 105, 201, 202, 203, 205 en Available; 104 y 204 en Inactive).
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario de Recepción con sesión iniciada para consultas en mostrador; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Ingesta de lista diaria y consulta local en recepción)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Publicar lista diaria de reservas a las 00:00 | Simular envío de mensaje asíncrono a la cola `m1.reservas.diarias.queue` con routing key `reserva.lista-del-dia` | `messageId`: UUID nuevo, `sequenceNumber`: 1, `operationalDate`: fecha actual, reserva `RSV-3F9A1C7B` (Titular: Ana Pérez, CC 123456, Colombia, 2 habitaciones: 201 y 202, fecha inicio: hoy, fecha fin: hoy + 3 días, source: BOOKING) | Verificar en el sistema que la copia local anterior se purga, se almacena la nueva reserva en `daily_reservation` y sus habitaciones en `daily_reservation_room`, y las habitaciones 201 y 202 pasan al estado Reserved asignadas a la reserva | [ ] | [ ] |
| 2 | Acceder al Panel de Recepción y ver llegadas | En el navegador, ingresar al Panel de Recepción y ubicarse en la pestaña "Llegadas" | URL: `/panel` | Se despliega la tabla de llegadas mostrando la reserva `RSV-3F9A1C7B`, indicando "Reserva de 2 habitaciones" con "2 de 2 pendientes", habitaciones 201 y 202, titular "Ana Pérez" con documento "123456", "3 noches", categoría y fuente "Booking" | [ ] | [ ] |
| 3 | Buscar reserva por código en la barra de búsqueda | En el campo "Buscar por huésped, documento o referencia de reserva…", ingresar el código de reserva | Texto: `RSV-3F9A1C7B` | La tabla filtra inmediatamente y muestra únicamente las filas correspondientes a las habitaciones 201 y 202 de la reserva buscada con sus botones "Check-in" habilitados | [ ] | [ ] |
| 4 | Buscar reserva por documento del titular | Limpiar el campo de búsqueda e ingresar el número de documento del titular | Texto: `123456` | La tabla presenta de inmediato la reserva asociada a Ana Pérez | [ ] | [ ] |
| 5 | Buscar reserva por nombre del titular | Limpiar el campo de búsqueda e ingresar parte del nombre del titular | Texto: `Ana` | La tabla muestra la coincidencia correspondiente a Ana Pérez | [ ] | [ ] |
| 6 | Consulta REST interna a Módulo 2 sin conflictos para bloqueo técnico | Ejecutar verificación de reservas para una habitación sin reservas en el rango (usado por bloqueo técnico o baja) | Habitación: 101, rango: fecha actual a fecha actual + 2 días | Verificar en el sistema que la consulta hacia Módulo 2 responde lista vacía y el sistema reporta que no existen conflictos de reserva | [Pass] | Verificado por test automatizado (`Module2ReservationClientAdapterTest`) |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Manejo de Conflictos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Búsqueda local sin coincidencias | En la barra de búsqueda del Panel de Recepción, ingresar un término inexistente | Texto: `NOEXISTE999` | La tabla muestra el mensaje central: "No hay reservas de llegada que mostrar." | [Pass] | Verificado por test automatizado (`ReceptionPanel.test.tsx`) |
| 2 | Ingesta de mensaje duplicado | Simular el reenvío del mismo mensaje de lista diaria con el `messageId` ya procesado | Mismo `messageId` utilizado en la Section 1 | Verificar en el sistema que el mensaje es descartado inmediatamente por idempotencia sin duplicar registros ni alterar estados | [Pass] | Verificado por test automatizado (`DailyReservationIngestionServiceTest`) |
| 3 | Ingesta de actualización con secuencia atrasada | Simular el envío de una actualización `UPDATED` con un `sequenceNumber` menor o igual al último procesado para esa reserva | `reservationRef`: `RSV-3F9A1C7B`, `sequenceNumber`: 1 (menor o igual a la última secuencia) | Verificar en el sistema que el mensaje se descarta registrando el evento en la bitácora sin modificar la copia local | [Pass] | Verificado por test automatizado (`DailyReservationIngestionServiceTest`) |
| 4 | Detección de conflicto con reserva futura en consulta REST | Simular verificación REST contra Módulo 2 para una habitación que tiene reserva activa en el rango | Habitación: 201, rango que incluye fecha actual | El sistema detecta el solapamiento con la reserva `RSV-3F9A1C7B` y reporta el conflicto impidiendo la operación | [Pass] | Verificado por test automatizado (`Module2ReservationClientAdapterTest`) |
| 5 | Indisponibilidad o fallo de red con Módulo 2 en consulta REST | Simular caída o timeout del servicio de Módulo 2 durante la verificación de reservas para una habitación | Habitación: 101 | El sistema maneja el fallo de manera controlada y reporta que el servicio externo no está disponible temporalmente sin generar caídas no controladas | [Pass] | Verificado por test automatizado (`Module2ReservationClientAdapterTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Recepción de una actualización con hueco de secuencia (`sequenceNumber` mayor al siguiente esperado): el sistema aplica el cambio y registra la alerta `SEQUENCE_GAP` en la bitácora sin detener la ingesta.
* **EC-002** : Mensaje corrupto o incompleto en la cola (sin `messageId`, `sequenceNumber`, `operationalDate` o con datos incompletos del titular): el sistema envía el mensaje a la cola de mensajes fallidos (`m1.reservas.diarias.dlq`) sin aplicar ninguna reserva y el panel muestra que las llegadas no están disponibles.
* **EC-003** : Actualización `REMOVED` por cancelación o no-show recibida para una habitación en estado de limpieza: la reserva se elimina de la copia local y la habitación se mantiene en su proceso físico sin fallos de sistema.
* **EC-004** : Búsqueda simultánea por múltiples recepcionistas sobre la copia local: el sistema resuelve las consultas de manera autónoma con tiempos de respuesta menores a 50 ms sin contención de recursos externos.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
