# Implementation Plan: Base del Módulo 1 (Plataforma Compartida - Habitaciones, Inventario, Check-In y Check-Out)

**Date**: 2026-10-06  
**Spec**: [diccionario.md](../../contexto/diccionario.md), [hospitua.md](../../contexto/hospitua.md), [maquina-estados-habitacion.md](../../SPEC/referencias/maquina-estados-habitacion.md) y los `spec-*.md` de Módulo 1 en `documentos/SPEC/`. Este plan no implementa una feature específica: deja lista la plataforma técnica base que todos los planes de feature de Módulo 1 reutilizan.

---

## Summary

El **Módulo 1 (Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out)** digitaliza la infraestructura física del hotel, controla la disponibilidad en tiempo real y gestiona directamente la admisión (`Check-In`) y salida (`Check-Out`) física de los huéspedes. Es la fuente de verdad del inventario habitacional (`Room`), de los 8 estados canónicos de su ciclo de vida y de las ocupaciones físicas reales (`Stay` y ocupantes inmutables `RoomGuest`).

Opera el **Panel de Recepción** al inicio de la jornada, consultando la copia local de la lista diaria de reservas (alimentada por la cola `m1.reservas.diarias.queue`) para las llegadas del día y las estancias activas locales para las salidas. Durante el Check-In formaliza la ocupación transicionando atómicamente la habitación de `Reserved` a `Occupied`, captura los datos de identidad de los huéspedes (titular precargado e inmutable, acompañantes) y notifica de forma asíncrona por cola a Módulo 2 con datos migratorios consolidados si existen extranjeros (alimentando `MigratoryMovement` para el reporte SIRE). Durante el Check-Out consulta síncronamente a Módulo 3 la liquidación financiera informativa (`SettlementSummary`), formaliza la salida transicionando la habitación a `PendingCleaning` (pasando de inmediato a la bandeja del Personal de limpieza) y notifica de forma asíncrona a Módulo 2 para el cierre de la reserva a `CHECKED_OUT`. Además, atiende solicitudes síncronas de Módulo 2 (consultar inventario por categoría o estado y consultar información de mantenimientos) y de Módulo 3 (consultar tarifa base).

Gestiona además el inventario (registro, edición, baja y reactivación de habitaciones por el Administrador; consulta del inventario y del historial de estados por el Gerente) y el ciclo operativo de limpieza y mantenimiento: el Personal de limpieza toma habitaciones desde su panel y confirma o libera su tarea activa; el Personal de mantenimiento reporta daños, programa bloqueos técnicos que un trabajo autónomo aplica en su fecha de inicio e interviene las habitaciones mediante tareas de reparación. Toda transición de estado queda registrada por periodos en el historial de estados (`room_state_history`).

Este plan unifica el stack tecnológico, arquitectura hexagonal, modelo de persistencia relacional, contratos de integración (REST y colas RabbitMQ), política de resiliencia "HTTP a Cola", manejo de errores uniforme (siempre 4xx, nunca 500 no controlado) y la infraestructura transversal de pruebas.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Web, Spring Data JPA, Bean Validation (Hibernate Validator), Lombok, Spring AMQP (RabbitMQ), Spring Security, Flyway
- **Storage**: PostgreSQL 16+
- **Build Tool**: Maven 3.9+
- **Messaging**: RabbitMQ 3.13+ (Exchange topic `hospitua.events`, colas durables, Dead-Letter Exchange)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockRestServiceServer
- **Target Platform**: Servidor Linux/Windows + Navegador Web moderno
- **Project Type**: Web Application monorepo estructurado (`backend/` + `frontend/`)
- **API**: REST JSON (conforme a RFC 7807 / `ApiError`)
- **Frontend**: React 18+ con JavaScript puro (JSX) y Vite (componentes basados en las vistas prototipo en `documentos/vistas/recepcionista/`)
- **Version Control**: Git + GitHub (estrategia Gitflow)
- **Performance Goals**:
  - Transición física atómica de habitación (`Room.status`) < 50 ms p95.
  - Despacho de notificaciones asíncronas vía Outbox < 100 ms.
  - Carga del Panel de Recepción < 500 ms (consulta 100% local).
  - Consulta reactiva de liquidación a Módulo 3 en Check-Out < 800 ms.
  - Flujo de Check-In en mostrador: < 2 min (nacionales), < 3 min (extranjeros).
  - Flujo de Check-Out en mostrador: < 1 min desde recepción de liquidación.
- **Constraints**:
  - Ningún fallo externo (caída de Módulo 2 o Módulo 3) debe bloquear la entrega física o liberación de una habitación en el mostrador.
  - Cero operaciones financieras, cálculos de penalizaciones, IVA o cobros en pasarela dentro de Módulo 1 (responsabilidad exclusiva de Módulo 3).
  - Cero mutabilidad en la lista de `RoomGuest` tras completarse el Check-In.
  - Máquina de estados habitacional estricta: solo se admiten las transiciones canónicas descritas en `maquina-estados-habitacion.md`. Módulo 1 es el dueño exclusivo del ciclo de vida habitacional (decisiones B7 y B8: sin órdenes de bloqueo ni secuencias externas).
- **Scale/Scope**: Operación continua 24/7 en recepción, soporte para decenas/cientos de habitaciones, múltiples recepcionistas concurrentes, personal de limpieza y mantenimiento.

### Dependencias y herramientas transversales aprobadas

| Necesidad | Propuesta | Justificación |
| --- | --- | --- |
| Herramienta de compilación | Maven | Estándar empresarial con Spring Boot y soporte multi-módulo |
| Migraciones de Base de Datos | Flyway | Versionamiento determinista del esquema SQL en `src/main/resources/db/migration/` |
| Autenticación y Autorización | Spring Security + JWT | Control de acceso basado en roles (`RECEPTIONIST`, `ADMINISTRATOR`, `MANAGER`, `CLEANING_STAFF`, `MAINTENANCE_STAFF`) e intercomunicación segura entre servicios |
| Resiliencia y Timeouts REST | `RestClient` con timeouts + Resilience4j | Evitar bloqueos ante lentitud o caída de Módulo 2 o Módulo 3 |
| Patrón Outbox Transaccional | Tabla `outbox_notification` + `@Scheduled` worker | Garantiza entrega eventual y consistencia atómica entre la base de datos y RabbitMQ |
| Paquete base backend | `com.hospitua.habitaciones` | Convención estándar del dominio |

---

## Comunicación entre módulos

Regla arquitectónica de HOSPITUA:

- **Proactiva** (el módulo avisa la ocurrencia de un evento de negocio y no requiere esperar respuesta sincrónica) → **Cola RabbitMQ**.
- **Reactiva** (solicitud bajo demanda que requiere un dato o confirmación síncrona inmediata) → **REST**.

### Matriz de Integración de Módulo 1

| Interacción | Dirección | Mecanismo | Tipo | Feature / Caso de Uso |
| --- | --- | --- | --- | --- |
| **Notificación de Check-In** | M1 → M2 | Cola RabbitMQ (`m2.habitacion.checkin.queue` / routing key `habitacion.checkin`) | Proactiva | `plan-registrar-check-in.md` |
| **Notificación de Check-Out** | M1 → M2 | Cola RabbitMQ (`m2.habitacion.checkout.queue` / routing key `habitacion.checkout`) | Proactiva | `plan-registrar-check-out.md` |
| **Datos de Huéspedes Extranjeros** | M1 → M2 | Cola RabbitMQ (`m2.huespedes.extranjeros.queue`) — 1 mensaje por extranjero | Proactiva | `plan-enviar-datos-huespedes-extranjeros.md` |
| **Ingestión de Reservas Diarias** | M2 → M1 | Cola RabbitMQ (`m1.reservas.diarias.queue` / routing keys `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`) | Reactiva (push) | `plan-consultar-reservas.md` |
| **Consultar Inventario de Habitaciones** | M2 → M1 | REST GET (`/api/rooms`) | Reactiva | `spec-consultar-inventario-habitaciones.md` |
| **Consultar Disponibilidad de Reservas** | M1 → M2 | REST GET (`/api/reservations?roomId={id}&startDate={d1}&endDate={d2}`) — solo para Mantenimiento/Administración | Reactiva | `plan-consultar-reservas.md` |
| **Consultar Liquidación** | M1 → M3 | REST GET (`/api/settlements?reservationRef={ref}&checkInDate={in}&checkOutDate={out}&source={src}&roomId={room}`) | Reactiva | `plan-consultar-liquidacion.md`, `plan-registrar-check-out.md` |
| **Consultar Tarifa Base** | M3 → M1 | REST GET (`/api/rooms/{roomId}/base-rate`) | Reactiva | `spec-consultar-tarifa-base.md` |
| **Consultar Información de Mantenimientos** | M2 → M1 | REST GET (`/api/rooms/{roomId}/maintenance-availability?startDate={d1}&endDate={d2}`) — M2 la consulta antes de asignar una habitación a una reserva | Reactiva | `spec-consultar-informacion-mantenimientos.md` |

### Convenciones de Mensajería (RabbitMQ)

- **Exchange**: `hospitua.events` (Tipo: `topic`, durable).
- **Colas / Routing Keys emitidas por Módulo 1 hacia Módulo 2**:
  - `m2.habitacion.checkin.queue` (routing key `habitacion.checkin`): Disparada al confirmar el paso 4 de Check-In. Emite `eventId`, `reservationRef`, `roomId`, `checkInDate` (sin hora) y `foreignGuestCount` (número de extranjeros; los datos individuales viajan por su propia cola).
  - `m2.habitacion.checkout.queue` (routing key `habitacion.checkout`): Disparada al confirmar el paso 5 de Check-Out. Emite `eventId`, `reservationRef`, `roomId`, `checkOutDate` (sin hora) y `foreignGuestCount`.
  - `m2.huespedes.extranjeros.queue`: Para reporte SIRE. Un mensaje independiente por cada huésped extranjero con `messageId`, `sequenceNumber`, `reservationRef`, `roomId` y los 10 campos migratorios: `firstName`, `lastName`, `documentType`, `documentNumber`, `birthDate`, `nationality`, `movementType` (`ENTRY` en Check-In / `DEPARTURE` en Check-Out), `movementDate` (`checkInDate` / `checkOutDate`), `originPlace` y `destinationPlace` (texto libre `"Ciudad, País"`).
- **Cola recibida por Módulo 1 desde Módulo 2**:
  - `m1.reservas.diarias.queue` (routing key `reserva.lista-del-dia`): Lista diaria de reservas `ACTIVE` ingestada a las 00:00 para alimentar la copia local. Routing key `reserva.lista-del-dia.actualizacion`: actualizaciones continuas del día (`ADDED`, `UPDATED`, `REMOVED`).
- **Estructura estándar del mensaje (`EventEnvelope<T>`)**:

  ```json
  {
    "eventId": "UUIDv4",
    "eventType": "CHECK_IN | CHECK_OUT | FOREIGN_GUESTS_DATA",
    "occurredAt": "2026-10-06T10:30:00Z",
    "sourceModule": "MODULE_1",
    "payload": { ... }
  }
  ```

### Traducción de Respuestas HTTP a Cola ("HTTP a Cola") y Resiliencia

De acuerdo con el diagrama arquitectónico oficial `mod-1-2-3.drawio`:

1. **Desacoplamiento asíncrono**: Check-In y Check-Out son eventos físicos que se consolidan **localmente y de forma inmediata** en la base de datos de Módulo 1 (`Stay`, `Room.status = Occupied` / `PendingCleaning`, `RoomGuest`).
2. **Patrón Transaccional Outbox**: La notificación a Módulo 2 se inserta en la tabla `outbox_notification` dentro de la **misma transacción ACID** que el cambio físico de la habitación.
3. **Despacho no bloqueante**: Un publicador asíncrono toma los registros pendientes y los deposita en RabbitMQ con acuse de recibo del broker (Publisher Confirms).
4. **Resiliencia ante caídas externas**: Si Módulo 2 está caído, con alta latencia o con fallos de base de datos, **el huésped no se retiene en el mostrador**. La habitación física queda entregada o enviada a limpieza, y la notificación se reintenta automáticamente en segundo plano con backoff exponencial.

---

## Contratos REST

### 1. Endpoints que Módulo 1 EXPONE (servicios propios)

| Método y Ruta | Consumidor | Propósito |
| --- | --- | --- |
| `GET /api/reception/panel` | Recepcionista (Frontend M1) | Consulta consolidada de llegadas (copia local de la lista diaria) y salidas (M1) para el Panel de Recepción |
| `POST /api/check-in` | Recepcionista (Frontend M1) | Formalización del Check-In (4 pasos: validación, captura `RoomGuest`, transición atómica a `Occupied`, outbox) |
| `POST /api/check-out` | Recepcionista (Frontend M1) | Formalización del Check-Out (5 pasos: consulta estancia, liquidación M3, resumen pago, transición a `PendingCleaning`, outbox) |
| `GET /api/rooms` | Módulo 2, Recepcionista, Gerente | Consulta del inventario de habitaciones (filtrado por `category`, `status`, ala/piso) |
| `GET /api/rooms/{roomId}` | Módulo 2, Módulo 3, Recepcionista | Detalle físico y estado actual de una habitación específica |
| `GET /api/rooms/{roomId}/base-rate` | Módulo 3 | Consulta reactiva de la tarifa base configurada para la habitación |
| `GET /api/stays/{stayId}` | Recepcionista, Auditoría | Detalle de la estancia física, fechas reales y ocupantes registrados |
| `GET /api/rooms/{roomId}/maintenance-availability?startDate={d1}&endDate={d2}` | Módulo 2, Personal de mantenimiento (vía "Programar bloqueo técnico") | Verificación de solapamiento con mantenimientos programados o aplicados de una habitación; responde si el evento es factible o no, o "habitación no encontrada" (`spec-consultar-informacion-mantenimientos.md`) |

### 2. Endpoints que Módulo 1 CONSUME (clientes de otros módulos)

| Servicio Externo | Método y Ruta | Propósito |
| --- | --- | --- |
| **Módulo 2 (Reservas)** | `GET /api/reservations?roomId={id}&startDate={d1}&endDate={d2}` | Verificación de conflicto de reservas para el Personal de mantenimiento/Administrador antes de bloquear o dar de baja una habitación |
| **Módulo 3 (Liquidación)** | `GET /api/settlements?reservationRef={ref}&checkInDate={in}&checkOutDate={out}&source={src}&roomId={room}` | Consulta reactiva síncrona de la liquidación final y desglose financiero en el paso 2 de Check-Out |

### Formato Estándar de Error (`ApiError`)

Toda excepción o rechazo funcional se traduce a un cuerpo JSON estandarizado:

```json
{
  "errorCode": "ROOM_NOT_OCCUPIED | RESERVATION_NOT_FOUND | CAPACITY_EXCEEDED | CONCURRENT_UPDATE",
  "message": "Descripción legible de la regla violada o contingencia.",
  "timestamp": "2026-10-06T10:30:00Z",
  "path": "/api/check-out"
}
```

- **HTTP 400**: Errores de validación o datos faltantes.
- **HTTP 403**: Acción no permitida para el usuario autenticado (ej. operar sobre una tarea de otro miembro del personal o un rol no autorizado).
- **HTTP 404**: Recurso inexistente (ej. habitación no encontrada).
- **HTTP 409**: Conflicto de concurrencia optimista o violación del ciclo de vida de la máquina de estados habitacional.
- **HTTP 500 no controlado PROHIBIDO**: Todo error inesperado es capturado por `@RestControllerAdvice`, registrado con identificador de correlación en logs y devuelto al cliente con código de error controlado.

---

## Project Structure

### Documentation

```text
documentos/
├── contexto/
│   ├── diccionario.md
│   └── hospitua.md
├── diagramas/
│   ├── mod-1-2-3.drawio.xml
│   └── maquina-de-estados.png
├── SPEC/
│   ├── referencias/maquina-estados-habitacion.md
│   └── spec-*.md                     # Un spec por caso de uso de Módulo 1
├── PLAN/
│   ├── base/
│   │   └── plan.md                   # Este archivo (plataforma técnica compartida)
│   └── plan-*.md                     # Un plan por caso de uso (ej. plan-registrar-check-out.md)
└── vistas/
    └── recepcionista/                # Prototipos HTML/CSS de vistas 01 a 11
```

### Source Code (Arquitectura Hexagonal — Puertos y Adaptadores)

```text
backend/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/hospitua/habitaciones/
    │   │   ├── domain/                               # CAPA DE DOMINIO (Lógica de negocio pura, sin dependencias externas)
    │   │   │   ├── model/                            # Entidades, Enums y Value Objects del núcleo
    │   │   │   │   ├── Room.java                     # Entidad de habitación física y reglas de aforo
    │   │   │   │   ├── RoomStatus.java               # Enum con los 8 estados canónicos
    │   │   │   │   ├── Stay.java                     # Estancia física formalizada en check-in (source: `DIRECTA` o nombre de la OTA, tal como llega de Módulo 2)
    │   │   │   │   ├── RoomGuest.java                # Ocupante físico inmutable (isReservationGuest, identidad)
    │   │   │   │   ├── ForeignGuestData.java         # Value Object migratorio SIRE (originPlace, destinationPlace)
    │   │   │   │   ├── MovementType.java             # Enum: ENTRY (check-in) / DEPARTURE (check-out)
    │   │   │   │   ├── ReservationSummary.java       # Resumen contractual consumido reactivamente de Módulo 2
    │   │   │   │   ├── SettlementSummary.java        # Resumen informativo de liquidación de Módulo 3 (invoiceNumber)
    │   │   │   │   ├── RoomStateHistory.java         # Periodo de una habitación en un estado (historial común)
    │   │   │   │   ├── CleaningTask.java             # Tarea de limpieza de un miembro del Personal de limpieza
    │   │   │   │   ├── CleaningTaskOutcome.java      # Enum: Completed / DamageReported / Released
    │   │   │   │   ├── ReparationTask.java           # Tarea de reparación de un miembro del Personal de mantenimiento
    │   │   │   │   ├── ReparationTaskOutcome.java    # Enum: Completed / Released
    │   │   │   │   ├── DamageReport.java             # Reporte de daño inmutable
    │   │   │   │   ├── TechnicalBlockReport.java     # Bloqueo técnico programado
    │   │   │   │   └── TechnicalBlockStatus.java     # Enum: Scheduled / Applied / Completed / Expired
    │   │   │   ├── exception/                        # Excepciones de reglas de negocio
    │   │   │   │   ├── DomainException.java
    │   │   │   │   ├── InvalidRoomTransitionException.java
    │   │   │   │   ├── RoomNotAvailableException.java
    │   │   │   │   ├── CapacityExceededException.java
    │   │   │   │   └── GuestValidationException.java
    │   │   │   └── ports/                            # CONTRATOS / INTERFACES DE PUERTOS
    │   │   │       ├── in/                           # Puertos de Entrada (Casos de Uso primarios)
    │   │   │       │   ├── ConsultReceptionPanelUseCase.java
    │   │   │       │   ├── ConsultReservationsUseCase.java
    │   │   │       │   ├── RegisterCheckInUseCase.java
    │   │   │       │   ├── ProcessGuestsUseCase.java
    │   │   │       │   ├── SendForeignGuestsUseCase.java
    │   │   │       │   ├── RegisterCheckOutUseCase.java
    │   │   │       │   ├── ConsultSettlementUseCase.java
    │   │   │       │   └── TransitionRoomStateUseCase.java
    │   │   │       └── out/                          # Puertos de Salida (Persistencia, Clientes, Mensajería)
    │   │   │           ├── RoomRepositoryPort.java
    │   │   │           ├── StayRepositoryPort.java
    │   │   │           ├── RoomGuestRepositoryPort.java
    │   │   │           ├── RoomStateHistoryPort.java
    │   │   │           ├── OutboxEventPublisherPort.java
    │   │   │           ├── Module2ReservationClientPort.java
    │   │   │           └── Module3SettlementClientPort.java
    │   │   ├── application/                          # CAPA DE APLICACIÓN (Orquestación de Casos de Uso)
    │   │   │   ├── service/                          # Implementaciones de Puertos de Entrada
    │   │   │   │   ├── ReceptionPanelService.java    # Orquesta KPIs y consultas para la vista de inicio
    │   │   │   │   ├── ReservationQueryService.java  # Consulta reactiva REST GET a Módulo 2
    │   │   │   │   ├── CheckInService.java           # Orquesta los 4 pasos de admisión y emisión a cola
    │   │   │   │   ├── GuestProcessingService.java   # Valida aforo y estructura datos migratorios SIRE
    │   │   │   │   ├── CheckOutService.java          # Orquesta los 5 pasos de salida y pase a limpieza
    │   │   │   │   ├── SettlementQueryService.java   # Consulta informativa síncrona REST GET a Módulo 3
    │   │   │   │   └── RoomStateTransitionService.java  # Evalúa máquina de estados interna de 8 estados
    │   │   │   └── dto/                              # DTOs de comando y consulta de la capa de aplicación
    │   │   └── infrastructure/                       # CAPA DE INFRAESTRUCTURA (Adaptadores y Configuración)
    │   │       ├── adapters/
    │   │       │   ├── in/                           # Adaptadores Primarios / Driving (Entrada)
    │   │       │   │   ├── web/                      # Controladores REST para la UI de Recepción
    │   │       │   │   │   ├── ReceptionPanelController.java
    │   │       │   │   │   ├── CheckInController.java
    │   │       │   │   │   ├── CheckOutController.java
    │   │       │   │   │   ├── RoomController.java
    │   │       │   │   │   ├── SettlementController.java
    │   │       │   │   │   └── GlobalExceptionHandler.java  # Mapeo a ApiError (RFC 7807)
    │   │       │   │   └── scheduling/               # Trabajos programados (ej. aplicación de bloqueos técnicos tras la ingesta de las 00:00)
    │   │       │   └── out/                          # Adaptadores Secundarios / Driven (Salida)
    │   │       │       ├── persistence/              # Repositorios JPA y entidades de BD (PostgreSQL)
    │   │       │       │   ├── entity/               # Entidades JPA (RoomJpaEntity, StayJpaEntity, RoomGuestJpaEntity)
    │   │       │       │   ├── repository/           # Spring Data JPA Repositories
    │   │       │       │   ├── mapper/               # Mappers Dominio <-> JPA Entity
    │   │       │       │   └── adapter/              # Implementaciones de puertos de persistencia
    │   │       │       │       ├── JpaRoomRepositoryAdapter.java
    │   │       │       │       ├── JpaStayRepositoryAdapter.java
    │   │       │       │       ├── JpaRoomGuestRepositoryAdapter.java
    │   │       │       │       └── JpaOutboxAdapter.java
    │   │       │       ├── rest/                     # Clientes HTTP hacia otros módulos
    │   │       │       │   ├── Module2RestClientAdapter.java  # Implementa Module2ReservationClientPort
    │   │       │       │   └── Module3RestClientAdapter.java  # Implementa Module3SettlementClientPort
    │   │       │       └── messaging/                # Publicadores hacia RabbitMQ
    │   │       │           ├── RabbitMqEventPublisherAdapter.java # Implementa OutboxEventPublisherPort
    │   │       │           └── OutboxScheduledWorker.java         # Worker de sondeo y reintento con backoff
    │   │       └── config/                           # Configuración Spring (Security, AMQP, RestClient, Clock)
    │   │           ├── SecurityConfig.java
    │   │           ├── RabbitConfig.java
    │   │           ├── RestClientConfig.java
    │   │           └── ClockConfig.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/                         # Scripts Flyway (V1__init_schema.sql, V2__seed_rooms.sql)
    └── test/java/com/hospitua/habitaciones/
        ├── domain/                                   # Pruebas unitarias de modelo y reglas de negocio
        ├── application/                              # Pruebas unitarias de servicios de aplicación (Mocks)
        └── infrastructure/                           # Pruebas de integración con Testcontainers
            ├── persistence/                          # Pruebas JPA contra PostgreSQL real
            ├── messaging/                            # Pruebas AMQP contra RabbitMQ real
            └── rest/                                 # Pruebas con MockRestServiceServer

frontend/
├── package.json
├── vite.config.js
├── index.html
└── src/
    ├── api/                          # Clientes HTTP (Axios / Fetch) y contratos de DTOs
    ├── assets/                       # Estilos y recursos visuales
    ├── components/                   # Navbar, Sidebar, Badges de estado, Modales
    ├── pages/
    │   ├── panel/                    # Vista 01: Inicio / Panel Recepcionista (Llegadas / Salidas)
    │   ├── checkin/                  # Vistas 02-05: Flujo de Check-In de 4 pasos
    │   ├── checkout/                 # Vistas 07-10: Flujo de Check-Out (liquidación y entrega a limpieza)
    │   ├── limpieza/                 # Panel de limpieza y vista de tarea activa
    │   └── mantenimiento/            # Panel de mantenimiento, vista de tarea activa y diálogos de reporte y programación
    ├── routes/                       # Enrutador de la aplicación
    └── test/                         # Pruebas de componentes con Vitest
```

---

## Diseño Técnico Base

### Modelo de Datos Relacional (PostgreSQL)

| Tabla | Entidad JPA | Descripción y Atributos Clave |
| --- | --- | --- |
| `room` | `RoomJpaEntity` | Unidad habitacional física. `id` (UUID PK), `room_number` (VARCHAR único), `floor_wing`, `room_category` (Sencilla, Doble, Suite, Boutique), `max_capacity` (INT), `base_rate` (NUMERIC), `status` (VARCHAR: 8 estados canónicos), `reserved_by_reservation_ref` (VARCHAR nullable: reserva que aparta la habitación, solo en `Reserved`), `version` (optimistic lock). |
| `room_state_history` | `RoomStateHistoryJpaEntity` | Historial común de estados por periodos (ver `maquina-estados-habitacion.md`). `id`, `room_id`, `status`, `previous_status` (nullable), `start_date_time`, `end_date_time` (nullable mientras el periodo está abierto), `actor_id` (nullable en transiciones autónomas), `source_flow`, `reservation_ref` (nullable). |
| `stay` | `StayJpaEntity` | Estancia física real. `id` (UUID PK), `reservation_ref` (VARCHAR), `room_id` (FK a `room`), `source` (VARCHAR: `DIRECTA` o nombre de la OTA, ej. `BOOKING`, almacenado tal como llega de Módulo 2), `check_in_date` (DATE), `check_out_date` (DATE nullable), `expected_checkin_time` (DATE), `expected_checkout_time` (DATE), `receptionist_id_check_in`, `receptionist_id_check_out`, `version`. |
| `room_guest` | `RoomGuestJpaEntity` | Ocupante físico inmutable. `id` (UUID PK), `stay_id` (FK a `stay`), `full_name`, `document_type`, `document_number`, `nationality`, `birth_date` (DATE nullable), `origin_place` (VARCHAR nullable), `destination_place` (VARCHAR nullable), `is_reservation_guest` (BOOLEAN). |
| `outbox_notification` | `OutboxNotificationJpaEntity` | Mensajes asíncronos pendientes de envío hacia RabbitMQ. `id` (UUID), `event_type`, `routing_key`, `payload` (JSONB), `status` (`PENDING`, `PUBLISHED`, `FAILED`), `retry_count`, `created_at`, `published_at`. |
| `user_account` | `UserAccountJpaEntity` | Usuarios del sistema (`username`, `password_hash`, `full_name`, `role`). |

### Diagrama Entidad-Relación (PostgreSQL)

```mermaid
erDiagram
  ROOM ||--o{ STAY : aloja
  ROOM ||--o{ ROOM_STATE_HISTORY : registra
  STAY ||--o{ ROOM_GUEST : tiene
  USER_ACCOUNT ||..o{ STAY : atiende
  USER_ACCOUNT ||..o{ ROOM_STATE_HISTORY : cambia

  ROOM {
    uuid id PK
    string room_number UK
    string floor_wing
    string room_category
    int max_capacity
    decimal base_rate
    string status
    string reserved_by_reservation_ref
    int version
  }
  ROOM_STATE_HISTORY {
    uuid id PK
    uuid room_id FK
    string status
    string previous_status
    timestamp start_date_time
    timestamp end_date_time
    string actor_id
    string source_flow
    string reservation_ref
  }
  STAY {
    uuid id PK
    string reservation_ref
    uuid room_id FK
    string source
    date check_in_date
    date check_out_date
    date expected_checkin_time
    date expected_checkout_time
    string receptionist_id_check_in
    string receptionist_id_check_out
    int version
  }
  ROOM_GUEST {
    uuid id PK
    uuid stay_id FK
    string full_name
    string document_type
    string document_number
    string nationality
    date birth_date
    string origin_place
    string destination_place
    boolean is_reservation_guest
  }
  OUTBOX_NOTIFICATION {
    uuid id PK
    string event_type
    string routing_key
    json payload
    string status
    int retry_count
    timestamp created_at
    timestamp published_at
  }
  USER_ACCOUNT {
    string username PK
    string password_hash
    string full_name
    string role
  }
```

**Cómo leer el diagrama:**

- **Línea continua**: clave foránea real en la base de datos (`stay.room_id → room`, `room_guest.stay_id → stay`, `room_state_history.room_id → room`).
- **Línea punteada**: relación lógica hacia `user_account`. Los campos `receptionist_id_check_in`, `receptionist_id_check_out` y `actor_id` se almacenan como cadenas simples, sin FK declarada en el esquema.
- **`outbox_notification`**: no tiene FK. Se vincula lógicamente con la estancia y la habitación a través del campo `payload` (JSONB con `reservationRef` y `roomId`) y se inserta en la misma transacción ACID que el cambio físico de estado (Regla 3).
- **`room_guest`**: registro inmutable tras el Check-In (Regla 4). Los campos `origin_place` y `destination_place` son **nullable** — solo aplican a extranjeros y van en el payload migratorio hacia Módulo 2 (SIRE), no como FK. Aceptan texto libre `"Ciudad, País"` (ej. `"Madrid, España"`).
- **`room_sequence` eliminada**: Módulo 1 es dueño exclusivo de la máquina de estados habitacional (Decisión B7/B8). No existe tabla de secuencias ni órdenes externas de bloqueo.
- **Tipos de dato**: los tipos reales son UUID, DATE, NUMERIC, JSONB, BOOLEAN, INT y VARCHAR; en el diagrama se simplifican como `uuid`, `date`, `decimal`, `json`, `boolean`, `int` y `string`.

### Reglas Transversales de Arquitectura

1. **Aislamiento de Dominio**: El dominio no depende de ningún framework ni biblioteca externa (Spring, Hibernate, etc.). Todo acceso a infraestructura se realiza mediante interfaces de puertos.
2. **Máquina de estados inquebrantable**: Todo cambio de estado en `Room.status` debe ejecutarse a través de un único método centralizado en el dominio (`Room.transitionTo(targetStatus)`) que valida la matriz de transiciones permitidas según `maquina-estados-habitacion.md`. Cualquier intento inválido arroja de inmediato una excepción de negocio que se traduce en HTTP 409 Conflict.
3. **Atomismo físico**: La creación de `Stay`, el registro de `RoomGuest`, el cambio de `Room.status` a `Occupied` y la inserción del evento en `outbox_notification` ocurren en una **única transacción `@Transactional`** local.
4. **Inmutabilidad de ocupantes**: La entidad `RoomGuest` no expone operaciones de actualización (`UPDATE`) en repositorios o APIs; una vez formalizado el Check-In, el registro queda sellado para auditoría y reportes migratorios.
5. **Cliente Módulo 2 y Módulo 3 con timeouts estrictos**:
   - `Module2RestClientAdapter`: Connect timeout 1s, Read timeout 2s. Si Módulo 2 no responde a la consulta de reservas (bloqueo técnico o baja de habitación), se informa "Servicio de reservas temporalmente no disponible" sin caerse y sin modificar la habitación.
   - `Module3RestClientAdapter`: Connect timeout 1s, Read timeout 3s. Si Módulo 3 no responde durante el Check-Out, se activa la vía de contingencia contemplada en el SPEC para liberar la habitación sin bloquear al huésped.
6. **Aislamiento de fechas**: En Módulo 1, las fechas de estadía, reserva y mantenimiento programado se tratan estrictamente como fechas de calendario (`LocalDate`, formato `AAAA-MM-DD`, sin componente de hora en pantalla ni en mensajes). Las marcas operativas (inicio y fin de tareas, reportes y periodos del historial de estados) son timestamps generados exclusivamente por el servidor mediante `Clock` (hora de Colombia, UTC-5).
7. **Manejo de errores uniforme**: Ningún fallo no controlado produce código HTTP 500; todos los errores son traducidos por `GlobalExceptionHandler` al esquema `ApiError`.
8. **Historial de estados**: Toda transición de `Room.status` registra su periodo en `room_state_history` dentro de la misma transacción, mediante un único componente (`RoomStateHistoryRecorder`) que cierra el periodo abierto de la habitación y abre el nuevo.
9. **Tareas operativas**: Un miembro del personal y una habitación tienen como máximo una tarea abierta (`end_date_time` nulo) a la vez, garantizado con índices únicos parciales en `cleaning_task` y `reparation_task`. Solo el dueño de la tarea la cierra, asignando su `outcome`.
10. **Orden de las 00:00**: Primero se procesa la ingesta de la lista diaria de reservas (que libera y luego aparta habitaciones) y después el trabajo de aplicación de bloqueos técnicos programados.

---

## Estrategia de Testing Base

- **Tests Unitarios (JUnit 5 + Mockito)**:
  - Validación de la matriz de transiciones de los 8 estados de la habitación.
  - Validación de aforos (`guestCount <= maxCapacity`).
  - Validación de reglas de identidad y nacionalidad de ocupantes.
- **Tests de Integración (Spring Boot Test + Testcontainers)**:
  - Pruebas transaccionales contra PostgreSQL real en contenedor Docker.
  - Verificación del patrón Outbox publicando eventos en un broker RabbitMQ real en Testcontainers.
  - Pruebas de concurrencia optimista sobre `Room` y `Stay`.
- **Tests de Contrato de Clientes REST**:
  - `MockRestServiceServer` simulando respuestas exitosas, respuestas con error y caídas por timeout de Módulo 2 y Módulo 3.
- **Tests con tiempo controlado**:
  - `Clock` fijo para los timestamps generados por el servidor y los trabajos programados de las 00:00.
- **Tests de Frontend (Vitest + React Testing Library)**:
  - Renderizado correcto del panel de llegadas y salidas.
  - Validación de formularios de huéspedes y navegación por pasos del wizard.

---

## Phase 1: Setup (Infraestructura Compartida)

- [ ] T001 Inicializar el repositorio con la estructura monorepo `backend/` y `frontend/`
- [ ] T002 Configurar `backend/pom.xml` con Java 21, Spring Boot 3.3+ y dependencias aprobadas
- [ ] T003 Configurar `frontend/` con React 18+, JavaScript puro (JSX) y Vite
- [ ] T004 [P] Configurar herramientas de formateo y calidad de código (Checkstyle/Spotless en backend, ESLint/Prettier en frontend)
- [ ] T005 [P] Crear `docker-compose.yml` para desarrollo local con PostgreSQL 16 y RabbitMQ 3.13 con interfaz de gestión
- [ ] T006 Configurar `application.yml` y perfiles (`dev`, `test`, `prod`) con variables de entorno

---

## Phase 2: Foundational (Prerrequisitos Bloqueantes)

**CRÍTICO**: Ningún plan de feature puede comenzar su implementación hasta completar satisfactoriamente esta fase base.

- [ ] T007 Diseñar el script de migración inicial Flyway `V1__init_schema.sql` con las tablas base (`room`, `room_state_history`, `stay`, `room_guest`, `outbox_notification`, `user_account`)
- [ ] T008 [P] Implementar modelos de dominio puros (`Room`, `Stay`, `RoomGuest`), puertos de persistencia (`RoomRepositoryPort`, `StayRepositoryPort`, `RoomGuestRepositoryPort`) y adaptadores JPA en `infrastructure/adapters/out/persistence/`
- [ ] T009 [P] Implementar la infraestructura de manejo de errores: `ApiError`, excepciones de dominio (`domain/exception/`) y `GlobalExceptionHandler`
- [ ] T010 Implementar el servicio de validación de máquina de estados habitacional (`RoomStateTransitionService` implementando `TransitionRoomStateUseCase`) garantizando el cumplimiento estricto de los 8 estados canónicos
- [ ] T011 Configurar RabbitMQ: Exchange `hospitua.events`, colas de eventos (`m2.habitacion.checkin.queue`, `m2.habitacion.checkout.queue`, `m2.huespedes.extranjeros.queue`), Dead-Letter Exchange (DLX) y serializador JSON en `config/RabbitConfig`
- [ ] T012 Implementar el mecanismo transaccional Outbox: puerto `OutboxEventPublisherPort`, adaptador `JpaOutboxAdapter` y `OutboxScheduledWorker` para despacho garantizado
- [ ] T013 [P] Configurar `RestClientConfig` con timeouts e implementar los adaptadores de salida `Module2RestClientAdapter` y `Module3RestClientAdapter` implementando sus respectivos puertos
- [ ] T014 Configurar la infraestructura de pruebas automatizadas con Testcontainers (PostgreSQL y RabbitMQ)
- [ ] T015 [P] Configurar Spring Security con autenticación JWT y roles de usuario (`RECEPTIONIST`, `ADMINISTRATOR`, `MANAGER`, `CLEANING_STAFF`, `MAINTENANCE_STAFF`)
- [ ] T016 Configurar el esqueleto base del frontend: router, cliente HTTP (Axios) con interceptores y layouts por rol (recepción, limpieza, mantenimiento, administración y gerencia) con Sidebar y Header
- [ ] T017 Implementar el historial común de estados: modelo `RoomStateHistory`, puerto `RoomStateHistoryPort`, adaptador JPA y `RoomStateHistoryRecorder`, invocado en cada transición de `Room.status` (Regla 8)

**Checkpoint Base**: Plataforma base lista, compilando y con pruebas de infraestructura verdes. Los planes de feature (uno por caso de uso) pueden desarrollarse sobre este cimiento común.
