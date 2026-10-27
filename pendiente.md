# Reporte de Pendientes y Decisiones Técnicas

## Lista de `TODO(pendiente)` dejados en el código
- `backend/src/main/java/com/hospitua/habitaciones/application/service/RoomStateTransitionService.java`:
  `// TODO(pendiente): Se utilizó IllegalArgumentException para rechazar el destino Reserved sin reservationRef válido; evaluar si se requiere una excepción de dominio específica en T018.`

## Decisiones Técnicas Tomadas
1. **Matriz de Transiciones Canónicas como Dato (T010 - OCP)**:
   - En `Room.java` se definió la matriz de transiciones estrictas de la máquina de estados según `maquina-estados-habitacion.md` como una estructura inmutable de datos (`Map<RoomStatus, Set<RoomStatus>>`), permitiendo exactamente las 14 transiciones canónicas válidas entre los 8 estados del sistema.
   - Cualquier intento de transición no permitida arroja `InvalidRoomTransitionException` con el estado actual y el intentado.
2. **Encapsulamiento del Ciclo de Vida de `reservedByReservationRef` (T010)**:
   - `Room.reserveFor(reservationRef)` es el único método y camino habilitado para asignar la referencia de reserva y transicionar a `Reserved`.
   - En `Room.transitionTo(target)`, si el estado destino es diferente de `Reserved`, la referencia de reserva se limpia a `null` de forma automática.
3. **Inmutabilidad y Cierre Único de Periodos de Historial (T016 - Regla 8)**:
   - `RoomStateHistory` modela los periodos como inmutables: una vez creados solo admiten el cierre de periodo (`close(OffsetDateTime)`), el cual lanza `IllegalStateException` si ya se encontraba cerrado.
   - `RoomStateHistoryRecorder` localiza el periodo actualmente abierto para la habitación (si existe), lo cierra con la marca de tiempo de `Clock`, y abre el nuevo periodo con el estado y `sourceFlow` correspondiente.
4. **Mapeo de Bitácora y Columna JSONB (T016)**:
   - En `RoomAuditLogJpaEntity`, el mapa `details` (`Map<String, Object>`) se mapea utilizando `@JdbcTypeCode(SqlTypes.JSON)` de Hibernate 6, integrándose de forma limpia con la columna nativa `jsonb` de PostgreSQL creada en `V1__init_schema.sql` y validada en `ddl-auto: validate`.
   - La bitácora soporta `actorId` nulo para eventos autónomos del sistema y toma el timestamp del servidor a través de `Clock` (Regla 6).
5. **Transaccionalidad Atómica (T010 + T016 - Regla 8 y 13)**:
   - `RoomStateTransitionService` utiliza `@Transactional(propagation = Propagation.REQUIRED)` para unirse a la transacción del caso de uso invocador. Dentro de la misma transacción se actualiza la entidad `Room` y se registran los cambios en `room_state_history`. Si ocurre una falla en cualquier punto del flujo, la transacción revierte tanto el estado de la habitación como el historial.
6. **Topología Declarativa de RabbitMQ y Publisher Confirms (T011 - Regla 3)**:
   - Exchange de eventos: `hospitua.events` (Topic).
   - Colas de salida durables: `m2.habitacion.checkin.queue` (binding `habitacion.checkin`), `m2.habitacion.checkout.queue` (binding `habitacion.checkout`), `m2.habitacion.mantenimiento.queue` (binding `habitacion.mantenimiento`).
   - M1 no declara la cola `modulo3.checkout`, respetando la separación de contratos con Módulo 3.
   - Cola de entrada: `m1.reservas.diarias.queue` con bindings `reserva.lista-del-dia` y `reserva.lista-del-dia.actualizacion`, configurada con Dead-Letter Exchange (`hospitua.events.dlx`) y DLQ durable `m1.reservas.diarias.dlq` (`x-dead-letter-exchange` y `x-dead-letter-routing-key`).
   - `RabbitTemplate` configurado con `setMandatory(true)`, confirm type correlated (`setConfirmCallback`) y `setReturnsCallback` para capturar mensajes unroutable devueltos por el broker.
7. **Patrón Transaccional Outbox y Serializadores Extensibles (T012 - OCP)**:
   - Puerto `OutboxEventPublisherPort` en la capa de aplicación con método `enqueue(routingKey, eventType, payload)`.
   - Implementado en `OutboxNotificationRepositoryAdapter` mediante transacción obligatoria (`@Transactional(propagation = Propagation.MANDATORY)`), enlazando la publicación con la transacción del caso de uso.
   - Asignación atómica de `sequenceNumber` por routing key utilizando las secuencias nativas de PostgreSQL creadas en Flyway V1 (`SELECT nextval(...)`), garantizando orden y unicidad sin contención de locks.
   - Diseño OCP para serializadores: interfaz `OutboxMessageSerializer`, clase abstracta `AbstractModule2FlatMessageSerializer` para el formato plano de Módulo 2 (`CHECK_IN`, `CHECK_OUT`, `MAINTENANCE_NOTICE`) agregando `messageId` y `sequenceNumber`, y `CheckOutBillingEventSerializer` para el sobre canónico `EventEnvelope` de Módulo 3 (`eventId`, `eventType = CHECK_OUT`, `occurredAt` con desfase `-05:00`, `sourceModule = MODULE_1`, `payload`) sin `messageId` ni `sequenceNumber`.
   - `OutboxScheduledWorker`: Proceso programado que obtiene mensajes `PENDING` con `nextAttemptAt <= now()`.
   - `RabbitMqOutboxPublisherAdapter`: Publica con `RabbitTemplate` confirmando entrega asíncrona mediante `CorrelationData`. Mensajes devueltos por falta de binding (unroutable) son tratados como fallo de publicación sin bloquear el resto de la cola.
   - Política de reintentos exponencial `OutboxBackoffPolicy`: intervalos de 1 s, 2 s, 4 s, 8 s... limitados a un máximo de 5 minutos (300 s). Al superar `max-retries` (10 por defecto), el estado cambia a `FAILED` y se emite la alerta `OUTBOX_PUBLISH_FAILED` en el logger de errores.
8. **Configuración de RestClient y Timeouts Diferenciados (T013 - Regla 5)**:
   - Propiedades de configuración mapeadas con `@ConfigurationProperties`: `Module2Properties` (`hospitua.module2.base-url`, connect-timeout 1s, read-timeout 2s) y `Module3Properties` (`hospitua.module3.base-url`, connect-timeout 1s, read-timeout 3s) utilizando `java.time.Duration`.
   - `RestClientConfig` instancia los clientes mediante `RestClient.builder()` con `JdkClientHttpRequestFactory`, inyectando de forma estricta los timeouts respectivos. No se crean adaptadores de feature prematuros, reservándolos para sus planes respectivos (`plan-consultar-reservas.md` y `plan-consultar-liquidacion.md`).

## Archivos Creados y Modificados
### Tarea 1 (Pieza mínima T019):
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/SourceFlow.java`: Enum con los 12 flujos de origen y método `displayName()` con el nombre legible en español.
- `backend/src/test/java/com/hospitua/habitaciones/domain/model/SourceFlowTest.java`: Pruebas unitarias que validan la existencia de los 12 valores y sus nombres legibles.

### Tarea 2 (T016 - Historial de estados y bitácora):
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomStateHistory.java`: Entidad de dominio inmutable para periodos de historial.
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomAuditEventType.java`: Enum de tipos de eventos de auditoría (`RESERVATION_STATE_CONFLICT`, `ROOM_EDITED`, `CHECK_IN`, `CHECK_OUT`).
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomAuditEntry.java`: Modelo de dominio puro para entradas de bitácora.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/RoomStateHistoryRepositoryPort.java`: Puerto de persistencia y consulta de periodo abierto.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/RoomAuditLogRepositoryPort.java`: Puerto de registro de bitácora.
- `backend/src/main/java/com/hospitua/habitaciones/application/service/RoomStateHistoryRecorder.java`: Servicio de aplicación que coordina el cierre y apertura de periodos con `Clock`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/RoomStateHistoryJpaEntity.java`: Entidad JPA para `room_state_history`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/RoomAuditLogJpaEntity.java`: Entidad JPA para `room_audit_log` con `details jsonb`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataRoomStateHistoryJpaRepository.java`: Interfaz Spring Data JPA para historial.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataRoomAuditLogJpaRepository.java`: Interfaz Spring Data JPA para bitácora.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/RoomStateHistoryMapper.java`: Mapper dominio <-> JPA para historial.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/RoomAuditLogMapper.java`: Mapper dominio <-> JPA para bitácora.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/RoomStateHistoryRepositoryAdapter.java`: Adaptador JPA de historial.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/RoomAuditLogRepositoryAdapter.java`: Adaptador JPA de bitácora.

### Tarea 3 (T010 - Máquina de estados):
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/Room.java`: Actualizado con matriz de transiciones canónicas (`ALLOWED_TRANSITIONS`), `transitionTo(target)` y `reserveFor(ref)`.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/in/TransitionRoomStateUseCase.java`: Puerto de entrada para transiciones habitacionales y registro de estado inicial.
- `backend/src/main/java/com/hospitua/habitaciones/application/service/RoomStateTransitionService.java`: Servicio `@Transactional(propagation = Propagation.REQUIRED)` que orquesta validación, transición, guardado y registro en historial.
- `backend/src/test/java/com/hospitua/habitaciones/domain/model/RoomTest.java`: Pruebas exhaustivas de las 64 combinaciones origen-destino entre los 8 estados canónicos, asignación de reserva y limpieza de referencias.
- `backend/src/test/java/com/hospitua/habitaciones/application/service/RoomStateTransitionServiceTest.java`: Pruebas unitarias de Mockito para el servicio de transición.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/RoomStateTransitionIntegrationTest.java`: Pruebas de integración con Testcontainers PostgreSQL que validan atomicidad, reversión ante errores, persistencia de historial y bitácora con y sin actor.

### Tarea 4 (T011 - Configuración y topología de RabbitMQ):
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/config/RabbitConfig.java`: Topología declarativa completa (`hospitua.events`, colas y bindings de Módulo 2, cola de entrada `m1.reservas.diarias.queue` con DLX/DLQ `m1.reservas.diarias.dlq`, conversor Jackson JSON y `RabbitTemplate` con publisher confirms y mandatory).
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/config/RabbitTopologyIntegrationTest.java`: Pruebas de integración con Testcontainers RabbitMQ que verifican la existencia de las colas, exchanges, bindings y la entrega de mensajes.

### Tarea 5 (T012 - Mecanismo Transaccional Outbox):
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/OutboxMessage.java`: Record con `messageId` y `sequenceNumber`.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/OutboxStatus.java`: Enum de estados (`PENDING`, `PUBLISHED`, `FAILED`).
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/OutboxEventPublisherPort.java`: Puerto de encolado del Outbox.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/OutboxNotification.java`: Modelo de dominio del Outbox.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/OutboxNotificationRepositoryPort.java`: Puerto de persistencia y consulta del Outbox.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/OutboxNotificationJpaEntity.java`: Entidad JPA para `outbox_notification`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataOutboxNotificationJpaRepository.java`: Interfaz Spring Data JPA para consultar notificaciones pendientes con `nextAttemptAt <= now()`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/OutboxNotificationMapper.java`: Mapper dominio <-> JPA para Outbox.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/OutboxNotificationRepositoryAdapter.java`: Adaptador con `@Transactional(propagation = Propagation.MANDATORY)` y obtención de secuencia PostgreSQL por routing key (`SELECT nextval(...)`).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/OutboxMessageSerializer.java`: Interfaz extensible OCP para serialización de eventos.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/AbstractModule2FlatMessageSerializer.java`: Clase base para formato plano Módulo 2 con `messageId` y `sequenceNumber`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/CheckInMessageSerializer.java`: Serializador para `CHECK_IN`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/CheckOutMessageSerializer.java`: Serializador para `CHECK_OUT`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/MaintenanceNoticeMessageSerializer.java`: Serializador para `MAINTENANCE_NOTICE`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/CheckOutBillingEventSerializer.java`: Serializador para `CHECK_OUT_BILLING` con sobre `EventEnvelope` de Módulo 3.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/RabbitMqOutboxPublisherAdapter.java`: Adaptador de publicación hacia RabbitMQ con verificación de confirms y control de devueltos (`mandatory = true`).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/OutboxBackoffPolicy.java`: Estrategia de reintentos exponenciales con tope de 5 minutos.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/OutboxScheduledWorker.java`: Worker programado con `@Scheduled` para procesar el Outbox y reportar alertas `OUTBOX_PUBLISH_FAILED`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/config/SchedulingConfig.java`: Configuración condicional de `@EnableScheduling` para pruebas.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/serializer/OutboxMessageSerializerTest.java`: Pruebas unitarias para serializadores OCP y sobre `EventEnvelope`.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/messaging/OutboxBackoffPolicyTest.java`: Pruebas unitarias de política de backoff.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/OutboxTransactionalIntegrationTest.java`: Pruebas de integración con Testcontainers PostgreSQL + RabbitMQ (reversión transaccional en enqueue, worker exitoso y marcado `PUBLISHED`, manejo de fallos y reintentos, tope `FAILED` con alerta).

### Tarea 6 (T013 - RestClient y timeouts):
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/rest/Module2Properties.java`: Propiedades de conexión y timeouts para Módulo 2 (connect: 1s, read: 2s).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/rest/Module3Properties.java`: Propiedades de conexión y timeouts para Módulo 3 (connect: 1s, read: 3s).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/config/RestClientConfig.java`: Configuración de los beans `module2RestClient` y `module3RestClient` con `JdkClientHttpRequestFactory`.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/config/RestClientTimeoutWireMockTest.java`: Pruebas con WireMock validando respuestas rápidas exitosas y corte exacto de `read-timeout` para Módulo 2 (2s) y Módulo 3 (3s).

### Planes y Documentación:
- `documentos/PLAN/base/plan.md`: Marcadas con `[x]` las tareas T010, T011, T012, T013, T014 y T016.
- `pendiente.md`: Actualizado con el registro detallado de las tareas ejecutadas.

## Resultado de `mvnw.cmd verify`
- **Docker / Testcontainers**: Contenedores PostgreSQL 16 y RabbitMQ 3.13 activos y conectados de forma dinámica.
- **Modo Hibernate**: Esquema completamente validado en modo `validate` contra Flyway V1.
- **Spotless**: 85 archivos Java limpios y formateados según el estándar del proyecto (`BUILD SUCCESS`).
- **Pruebas Automatizadas**: **74 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidos**.
- **Empaquetado JAR**: Generación exitosa de `target/habitaciones-0.0.1-SNAPSHOT.jar`.
- **Resultado General**: `BUILD SUCCESS`.
