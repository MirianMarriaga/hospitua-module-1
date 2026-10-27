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

### Planes y Documentación:
- `documentos/PLAN/base/plan.md`: Marcadas con `[x]` las tareas T010 y T016 (T019 se mantiene en `[ ]`).
- `pendiente.md`: Actualizado con el registro detallado de las tareas ejecutadas.

## Resultado de `mvnw.cmd verify`
- **Docker / Testcontainers**: Contenedores PostgreSQL 16 y RabbitMQ 3.13 activos con `@ServiceConnection`.
- **Modo Hibernate**: Esquema completamente validado en modo `validate` contra Flyway V1.
- **Spotless**: 57 archivos formateados y 100% limpios (`BUILD SUCCESS`).
- **Pruebas Automatizadas**: **56 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidos**.
- **Empaquetado JAR**: Generación exitosa de `target/habitaciones-0.0.1-SNAPSHOT.jar`.
- **Resultado General**: `BUILD SUCCESS`.
