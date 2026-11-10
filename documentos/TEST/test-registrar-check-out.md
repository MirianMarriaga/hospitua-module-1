### Test Case Specification: Registrar Check-Out
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) / [plan-registrar-check-out.md](../PLAN/plan-registrar-check-out.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-005-RegistrarCheckOut
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el flujo guiado de formalización de salida (Check-Out), asegurando la consulta de la estancia activa local, la integración con la liquidación informativa de Módulo 3, la exigencia de confirmación explícita, el cierre de la estancia (`Stay`), la transición física a Pendiente de limpieza (`PendingCleaning`) y la emisión asíncrona de los eventos hacia Módulo 2 y Módulo 3.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Check-Out | Specification Document | [documentos/SPEC/spec-registrar-check-out.md](../SPEC/spec-registrar-check-out.md) |
| Plan de Implementación de Registrar Check-Out | Plan Document | [documentos/PLAN/plan-registrar-check-out.md](../PLAN/plan-registrar-check-out.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y frontend en funcionamiento, base de datos PostgreSQL y broker RabbitMQ con los bindings hacia `m2.habitacion.checkout.queue` y `modulo3.checkout`.

##### Dependencies & Assumptions
* TC-003 Registrar check-in debe haber creado la estancia física activa (`Stay` con habitación en `Occupied`).
* TC-004 Consultar liquidación debe estar operativo para la visualización del desglose financiero.

##### Preconditions & Default Setup
* **Initial State** : Habitación 201 en estado Occupied con estancia activa registrada (`stayId`: UUID, reserva `RSV-3F9A1C7B`, titular Ana Pérez, huésped extranjero registrado).
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario recepcionista autenticado; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Check-Out en fecha y liberación a limpieza)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar flujo desde el Panel de Recepción | En `/panel`, ir a la pestaña "Salidas", ubicar la estancia de la habitación 201 y hacer clic en el botón "Check-out" | Reserva: `RSV-3F9A1C7B`, Habitación: 201 | El sistema redirige a `/check-out?reservationRef=RSV-3F9A1C7B&roomId=...` cargando el paso "1. Información de la reserva y estadía" con los datos del titular, habitación 201 en estado Ocupada y la lista de huéspedes alojados | [ ] | [ ] |
| 2 | Continuar a liquidación | En el paso 1, hacer clic en "Continuar" | Clic en "Continuar" | El wizard avanza al paso 2 consultando síncronamente `GET /api/stays/{stayId}/settlement` y muestra "Información de la liquidación" con desglose de comisión e ingreso neto | [ ] | [ ] |
| 3 | Continuar a revisión de pago | En el paso 2, hacer clic en "Continuar" | Clic en "Continuar" | El wizard avanza al paso 3 "Revisión de pago" mostrando el total de hospedaje liquidado por M3 y la nota "La factura se emite al confirmar la salida." | [ ] | [ ] |
| 4 | Continuar a confirmación | En el paso 3, hacer clic en "Continuar" | Clic en "Continuar" | El wizard avanza al paso "4. Confirmación de salida", mostrando el resumen de la habitación, fechas y la casilla obligatoria de confirmación | [ ] | [ ] |
| 5 | Validar confirmación obligatoria | Marcar la casilla "Confirmo la entrega de la habitación y la salida física de todos los huéspedes" | Casilla marcada (`confirmed = true`) | El botón "Confirmar salida y liberar habitación" se habilita | [ ] | [ ] |
| 6 | Confirmar salida física | Hacer clic en "Confirmar salida y liberar habitación" | Clic en botón de confirmación | Se cierra la estancia con `checkOutDate` = hoy, la habitación transiciona a `PendingCleaning`, se encolan las notificaciones hacia M2 y M3, y se despliega la pantalla "Check-out completado" con la leyenda: "La habitación 201 quedó en estado Pendiente de limpieza y ya está en la bandeja del personal de limpieza." | [ ] | [ ] |
| 7 | Regresar al inicio | En la pantalla de éxito, hacer clic en "Volver al inicio" | Clic en "Volver al inicio" | El sistema navega a la vista principal y la habitación 201 ya no figura en la lista de salidas activas | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Rechazos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de Check-Out sobre habitación no ocupada | Intentar ejecutar el flujo de Check-Out sobre una habitación que está en estado Available o Reserved | Habitación 101 (Available) | El paso 1 bloquea la operación mostrando: "La habitación está Disponible; solo se puede hacer el check-out de una habitación Ocupada." | [Pass] | Verificado por test automatizado (`CheckOutPage.test.tsx` y `CheckOutIntegrationTest`) |
| 2 | Intento de Check-Out sin estancia activa asociada | Intentar consultar Check-Out con una referencia de reserva que no posee estancia activa abierta | Reserva sin estancia activa | El sistema responde mostrando: "No encontramos una estadía activa para esta habitación." | [Pass] | Verificado por test automatizado (`CheckOutPage.test.tsx` y `CheckOutIntegrationTest`) |
| 3 | Intento de confirmación sin marcar casilla obligatoria | En el paso 4, intentar despachar la solicitud sin marcar la casilla de confirmación | `confirmed = false` | El botón de confirmación permanece deshabilitado en la interfaz y el intento directo produce el mensaje: "Marca la casilla para confirmar la salida del huésped." | [Pass] | Verificado por test automatizado (`CheckOutPage.test.tsx`) |
| 4 | Salida en contingencia por indisponibilidad de Módulo 3 | Simular caída o timeout de Módulo 3 durante los pasos de liquidación/pago | Módulo 3 no responde | Se despliega el banner de contingencia advirtiendo que el servicio de facturación no respondió y permitiendo avanzar hasta el paso 4 para liberar la habitación a limpieza sin retener al huésped | [Pass] | Verificado por test automatizado (`CheckOutPage.test.tsx` y `CheckOutIntegrationTest`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Salida anticipada (Early Check-Out): la fecha de salida esperada era posterior a hoy; el sistema registra `checkOutDate` en el día actual y Módulo 3 liquida con el valor cotizado completo sin reducciones.
* **EC-002** : Falla de red o caída de RabbitMQ al momento de confirmar: la transacción local se compromete (habitación pasa a `PendingCleaning`, `checkOutDate` registrado en base de datos) y las notificaciones a Módulo 2 y Módulo 3 se guardan en el outbox para reintento automático.
* **EC-003** : Reutilización de datos migratorios para extranjeros: la notificación de salida hacia Módulo 2 recupera automáticamente la procedencia y el destino registrados en `RoomGuest` durante el Check-In, sin volver a pedirlos en pantalla.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
