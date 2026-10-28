# Reporte de Estado - Base del Módulo 1 (HOSPITUA)

## 1. Resumen
En la rama `feature/m1-base-lote-1` quedó implementada y verificada la base troncal del backend para el Módulo 1. Incluye esquema relacional (Flyway V1/V2), persistencia JPA, máquina de estados canónica, historial de transiciones, mensajería RabbitMQ con patrón transaccional Outbox, clientes REST resilientes, gestión de personal con bootstrap administrativo, autenticación provisional (`X-User`), avisos de mantenimiento y pipeline CI en GitHub Actions.

## 2. Tareas del plan base completadas
| ID | Nombre corto | Lote / Fase |
|---|---|---|
| T002 | Configuración Maven y dependencias aprobadas | Setup |
| T005 | Docker Compose con PostgreSQL 16 y RabbitMQ 3.13 | Setup |
| T006 | Configuración de application.yml y perfiles | Lote 1 |
| T007 | Migración inicial Flyway V1 y semilla V2 | Lote 2 |
| T008 | Modelos de dominio y adaptadores de persistencia JPA | Lote 2 |
| T009 | Infraestructura de manejo de errores (ApiError, GlobalExceptionHandler) | Lote 1 |
| T010 | Servicio y validación de máquina de estados habitacional | Lote 3 |
| T011 | Topología RabbitMQ y Publisher Confirms | Lote 4 |
| T012 | Mecanismo transaccional Outbox y serializadores extensibles | Lote 4 |
| T013 | RestClientConfig y timeouts diferenciados M2/M3 | Lote 4 |
| T014 | Infraestructura de pruebas con Testcontainers y WireMock | Lote 2 / Lote 4 |
| T016 | Historial común de estados y bitácora de auditoría | Lote 3 |
| T019 | Componentes compartidos de limpieza/mantenimiento (SourceFlow, límites, excepciones) | Lote 5 |
| T020 | Inicio de sesión (POST /api/auth/login) y CurrentUserResolver (X-User) | Lote 5 |
| T022 | Cuentas de personal, repositorio, cifrado BCrypt y Bootstrapper | Lote 5 |
| T023 | ClockConfig con zona horaria America/Bogota | Lote 1 |
| T024 | Publicador de avisos de mantenimiento (MaintenanceNoticePublisher) | Lote 5 |
| T025 | Pipeline de integración continua en GitHub Actions (.github/workflows/backend-ci.yml) | Lote 5 |

## 3. Tareas del plan base pendientes
- **T001**: Estructura monorepo `backend/` y `frontend/` (pendiente por inicialización de frontend).
- **T003**: Configuración de `frontend/` con React 18+, TypeScript y Vite (alcance de frontend).
- **T004**: Herramientas de calidad frontend ESLint/Prettier (Spotless de backend ya completado; frontend pendiente).
- **T015**: Esqueleto base del frontend con router, Axios y layouts por área (alcance de frontend).
- **T017**: Migración V3 con tablas de tareas de limpieza y reportes (reservada para la fase de limpieza/mantenimiento).
- **T018**: Puertos transversales de transición (se implementan en sus planes de feature según regla de dueño único).
- **T021**: Vistas de login en frontend `LoginPage.tsx` y `authService.ts` (alcance de frontend).

## 4. Estructura creada
- **`domain/`**: Entidades puras (`Room`, `Stay`, `RoomGuest`, `StaffAccount`, `RoomStateHistory`), objetos de valor (`MaintenanceLimits`), enums (`RoomStatus`, `RoomCategory`, `StaffArea`, `SourceFlow`, `MaintenanceNoticeAction`) y excepciones de negocio sin dependencias externas.
- **`application/`**: Puertos de entrada `*UseCase`, puertos de salida `*Port` (persistencia, mensajería, seguridad), servicios orquestadores (`RoomStateTransitionService`, `LoginService`, `MaintenanceNoticePublisher`, etc.) y DTOs planos desacoplados de frameworks.
- **`infrastructure/`**: Adaptadores web REST y resolución de usuario actual (`adapters/in/web/`), startup runner (`adapters/in/startup/`), adaptadores JPA con mappers MapStruct/manuales (`adapters/out/persistence/`), mensajería RabbitMQ con serializadores planos y sobre `EventEnvelope` (`adapters/out/messaging/`), cifrado BCrypt (`adapters/out/security/`) y configuración centralizada (`config/`).

## 5. Migraciones y base de datos
- **V1 (`V1__init_schema.sql`)**: Crea las 11 tablas del esquema base (`room`, `room_state_history`, `room_audit_log`, `room_decommission`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log`, `outbox_notification`, `user_account`), índices operativos y 4 secuencias PostgreSQL dedicadas para el Outbox (`outbox_seq_*`).
- **V2 (`V2__seed_rooms.sql`)**: Semilla sintética con 14 habitaciones (12 `Available`, 2 `Inactive`), sus 14 periodos iniciales abiertos en `room_state_history` con `REGISTER_ROOM` y actor nulo, y 2 registros de baja en `room_decommission` para las habitaciones inactivas (104 y 204).

## 6. Decisiones técnicas relevantes
- **Máquina de estados como dato**: `RoomTransitions` declara la matriz (`EnumMap`) con las 14 transiciones válidas entre 8 estados canónicos; `Room.transitionTo` la consulta.
- **Transaccionalidad atómica y Outbox**: `OutboxEventPublisherPort.enqueue` opera con `Propagation.MANDATORY` ligando la emisión del evento a la transacción del hecho.
- **Secuencias nativas por routing key**: Asignación atómica de `sequenceNumber` sin contención de locks en PostgreSQL.
- **Orden natural de habitaciones**: Consultas ordenadas primero por piso, luego por número y sufijo alfanumérico.
- **Aislamiento temporal estricto**: Fechas de calendario en `LocalDate` (`AAAA-MM-DD`) y timestamps vía bean `Clock` (Bogotá UTC-5).
- **Bootstrap administrativo automático**: `StaffAccountBootstrapper` inicializa la cuenta admin configurable si no existen cuentas activas de Administración.
- **Resiliencia REST**: `RestClientConfig` con `JdkClientHttpRequestFactory` y timeouts estrictos para Módulo 2 y Módulo 3.
- **Errores por composición**: cada excepción de dominio compone un `DomainError` (código del catálogo y categoría); `GlobalExceptionHandler` traduce la categoría a HTTP y cualquier otra excepción a `422 UNEXPECTED_ERROR` (regla 7, nunca 500).
- **Outbox**: un `OutboxMessageSerializer` por `event_type` resuelto por `OutboxSerializerRegistry` (un tipo duplicado impide arrancar); el formato plano de Módulo 2 lo arma una sola clase, `Module2FlatMessageSerializer`, instanciada por tipo en `OutboxConfig`. El JSONB se escribe con el `JsonMapper` de la aplicación (`JsonPersistenceConfig`).
- **Configuración validada**: `Module2Properties`, `Module3Properties`, `MaintenanceProperties`, `StaffBootstrapProperties` y `OutboxProperties` son `record` con Bean Validation; una propiedad inválida impide arrancar.
- **Identificación provisional `X-User`**: `CurrentUserResolver` desacoplado que valida la existencia y estado activo de la cuenta sin introducir seguridad prematura.

## 7. Pruebas
- **Total ejecutadas**: 147 pruebas automatizadas, incluidas las 5 reglas de `ArchitectureTest` (ArchUnit).
- **Resultado de `mvnw verify`**: `BUILD SUCCESS` (147 tests pasados, 0 fallos, 0 errores, 0 omitidos).
- **Entorno**: Testcontainers (PostgreSQL 16 y RabbitMQ 3.13), WireMock para contratos REST y validación Spotless en 127 archivos.

## 8. TODO(pendiente) abiertos
Ninguno. El de `RoomStateTransitionService` (referencia de reserva nula) se cerró: la valida solo `Room.reserveFor`, como error de programación, porque ningún usuario puede provocarlo.

## 9. Puntos a revisar a mano
- ~~Comprobar que la matriz canónica cubra exactamente las 14 transiciones de `maquina-estados-habitacion.md`~~: verificado par por par (`RoomTransitions`).
- Validar si T024 (`MaintenanceNoticePublisher`) se acepta consolidada en esta entrega o si requiere división de commits.
- ~~Confirmar si se sustituye `IllegalArgumentException` por `ReservationReferenceRequiredException`~~: se mantiene `IllegalArgumentException` solo en `Room.reserveFor` (ver sección 8).
- Revisar si la migración `V3` (T017) debe añadirse antes de comenzar con los planes específicos de limpieza y mantenimiento.
