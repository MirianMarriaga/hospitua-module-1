### Test Case Specification: Registrar Check-In
**Created** : 2026-11-07  **Spec/Plan Link** : [spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) / [plan-registrar-check-in.md](../PLAN/plan-registrar-check-in.md)  **Status** : Draft

#### Test Overview

**Test Case ID** : TC-003-RegistrarCheckIn
**Product Name & Version** : Hospitua Módulo 1 v0.1
**Author** : Equipo Módulo 1
**Current Owner** : Por asignar
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context

##### Test Objectives
Este caso de prueba verifica el flujo guiado de 4 pasos para la admisión presencial de huéspedes (Check-In), asegurando la validación local de la reserva, la captura de ocupantes nacionales y extranjeros con panel migratorio, la transición síncrona a Occupied, la creación de la estancia (Stay) y el encolamiento de la notificación asíncrona hacia Módulo 2.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Especificación de Registrar Check-In | Specification Document | [documentos/SPEC/spec-registrar-check-in.md](../SPEC/spec-registrar-check-in.md) |
| Plan de Implementación de Registrar Check-In | Plan Document | [documentos/PLAN/plan-registrar-check-in.md](../PLAN/plan-registrar-check-in.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Requiere backend Spring Boot y frontend en funcionamiento, base de datos PostgreSQL y broker RabbitMQ con la cola `m2.habitacion.checkin.queue` configurada.

##### Dependencies & Assumptions
* TC-001 Consultar reservas (ingesta de lista diaria) y TC-007 Marcar habitación como reservada deben haber dejado la habitación de prueba en estado Reserved apartada para la reserva.
* El recepcionista debe contar con una cuenta activa con área Recepción.

##### Preconditions & Default Setup
* **Initial State** : Habitación 201 en estado Reserved asignada a la reserva `RSV-3F9A1C7B` (Titular: Ana Pérez, CC 123456, 2 personas, fecha de inicio hoy). Habitación 101 en estado Available.
* **Environment** : Ambiente de pruebas del equipo / Sandbox.
* **Test Accounts** : Usuario recepcionista autenticado; ver credenciales de pruebas del equipo.

--------------------------------------------------------------------------------

#### Test Execution Flow

##### Section 1: Flujo Principal Exitoso (Check-In con Huésped Nacional y Extranjero)
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Iniciar flujo desde el Panel de Recepción | En `/panel`, localizar la llegada de la habitación 201 y hacer clic en el botón "Check-in" | Reserva: `RSV-3F9A1C7B`, Habitación: 201 | El sistema redirige a `/check-in?reservationRef=RSV-3F9A1C7B&roomId=a0000000-0000-0000-0000-000000000201` y muestra el paso "1. Información de la reserva" con los datos del titular (Ana Pérez, CC 123456), fechas, categoría y estado "Reservada" | [ ] | [ ] |
| 2 | Continuar a datos de huéspedes | En el paso 1, hacer clic en el botón "Continuar" | Clic en botón "Continuar" | El wizard avanza al paso "2. Datos de huéspedes — Habitación 201", mostrando bloques de captura correspondientes a las 2 personas asignadas | [ ] | [ ] |
| 3 | Capturar datos de huésped titular alojado (nacional) | En el bloque 1, marcar "El titular se aloja en esta habitación", verificar datos precargados e ingresar fecha de nacimiento | Fecha nacimiento: `1990-05-15` | Los campos del titular quedan confirmados y no se despliega panel migratorio por ser nacionalidad Colombia | [ ] | [ ] |
| 4 | Capturar datos de acompañante extranjero | En el bloque 2, ingresar nombres, apellidos, tipo de documento, número de documento, nacionalidad extranjera, fecha de nacimiento, procedencia y destino | Nombres: John, Apellidos: Doe, Tipo: PAS, Número: `E98765432`, Nac: Estados Unidos, Nacimiento: `1988-10-20`, Procedencia: `Miami, Estados Unidos`, Destino: `Medellín, Colombia` | Al seleccionar nacionalidad extranjera se abre el panel migratorio solicitando obligatoriamente lugar de procedencia y destino | [ ] | [ ] |
| 5 | Continuar a confirmación | Hacer clic en el botón "Continuar" del formulario de huéspedes | Clic en botón "Continuar" | El wizard avanza al paso "3. Resumen y confirmación", resumiendo 2 huéspedes, habitación 201, fechas de estadía y el aviso informativo de cambio a Ocupada | [ ] | [ ] |
| 6 | Confirmar el Check-In | Hacer clic en el botón "Confirmar Check-In" | Clic en "Confirmar Check-In" | El botón muestra "Registrando…", se registra la estancia, la habitación transiciona a Occupied, se encola la notificación hacia Módulo 2 y se avanza a la pantalla "Check-in completado" con insignias "Habitación: Ocupada" y "Reserva: En curso" | [ ] | [ ] |
| 7 | Regresar al inicio | En la pantalla de éxito, hacer clic en "Volver al inicio" | Clic en "Volver al inicio" | El sistema navega a la pantalla principal de recepción y la llegada de la habitación 201 ya no aparece como pendiente | [ ] | [ ] |

--------------------------------------------------------------------------------

##### Section 2: Errores, Validaciones y Rechazos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intento de Check-In con habitación no reservada | Intentar validar Check-In para una habitación en estado Available | Habitación 101 (Available) | El paso 1 muestra la alerta de error: "La habitación está Disponible; solo se puede hacer el check-in de una habitación Reservada para esta reserva." y el botón "Continuar" permanece deshabilitado | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx` y `CheckInIntegrationTest`) |
| 2 | Intento de Check-In con reserva de fecha futura | Intentar validar Check-In con una reserva cuya fecha de inicio es posterior a hoy | Reserva con `startDate = hoy + 1 día` | Se muestra el mensaje de error: "Esta reserva no llega hoy. El check-in solo se hace el día de llegada." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx` y `CheckInIntegrationTest`) |
| 3 | Intento de Check-In duplicado para la misma habitación | Intentar iniciar Check-In para una habitación que ya tiene estancia registrada (habitación 201 procesada en la sección 1) | Reserva y habitación 201 ya admitidas | Se despliega el mensaje de error: "Esta habitación ya tiene el check-in registrado." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx` y `CheckInIntegrationTest`) |
| 4 | Discrepancia en cantidad de huéspedes | En el paso 2, intentar avanzar con un número de huéspedes distinto de `guestCount` | Discrepancia en cantidad de ocupantes | El sistema bloquea el avance mostrando: "La cantidad de huéspedes no coincide con la reserva." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx`) |
| 5 | Superar capacidad máxima de la habitación | Intentar ingresar más huéspedes que la capacidad máxima física de la unidad | Cantidad de ocupantes > `maxCapacity` | Se bloquea la acción indicando: "La cantidad de huéspedes supera la capacidad de la habitación." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx` y `CheckInIntegrationTest`) |
| 6 | Documentos duplicados en la misma habitación | Registrar dos ocupantes con el mismo tipo y número de documento | Dos huéspedes con CC 123456 | El sistema bloquea la confirmación mostrando: "Dos huéspedes de la habitación tienen el mismo documento." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx` y `CheckInIntegrationTest`) |
| 7 | Datos migratorios incompletos o fecha de nacimiento inválida | Omitir el lugar de procedencia o ingresar fecha de nacimiento futura en un huésped | Fecha nacimiento: fecha de mañana, o procedencia vacía | Se resalta el campo inválido y se muestra el mensaje: "Revisa los datos de los huéspedes." | [Pass] | Verificado por test automatizado (`CheckInPage.test.tsx`) |

--------------------------------------------------------------------------------

##### Edge Cases & Boundary Conditions
* **EC-001** : Caída temporal del broker RabbitMQ al momento de confirmar el Check-In: la transacción local de base de datos se completa (estancia creada, habitación transiciona a Occupied) y el mensaje queda guardado en la tabla de outbox para entrega diferida por reintentos en segundo plano.
* **EC-002** : Titular de la reserva que no se aloja en la habitación: se ingresan los datos de los ocupantes reales sin marcar al titular; la estancia guarda al titular para facturación pero la nómina de huéspedes (`RoomGuest`) y la notificación a Módulo 2 contienen únicamente a los ocupantes presentes.
* **EC-003** : Concurrencia de recepción: dos recepcionistas abren simultáneamente el flujo para la misma habitación reservada; el primero confirma exitosamente y el segundo recibe control optimista informando que el estado de la habitación cambió.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | System crash, data corruption, or complete block of critical user journey with no workaround. |
| **Major** | **M** | Core feature broken or significant functionality loss, but an awkward workaround exists. |
| **Small / Minor** | **S** | Cosmetic issue, minor UI alignment, typo, or non-blocking defect. |
