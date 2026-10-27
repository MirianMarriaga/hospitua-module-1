# Reporte de Pendientes y Decisiones Técnicas

## Lista de `TODO(pendiente)` dejados en el código
Actualmente **no se dejaron comentarios `// TODO(pendiente)`** en el código fuente. Todas las tareas de este lote (T007 y T008) fueron implementadas en su totalidad conforme a las especificaciones y lineamientos arquitectónicos.

## Decisiones Técnicas Tomadas
1. **YAGNI en Entidades JPA (T008)**: Se crearon únicamente las entidades JPA requeridas por este lote (`RoomJpaEntity`, `StayJpaEntity`, `RoomGuestJpaEntity`). Las entidades JPA para las demás tablas creadas en V1 (`daily_reservation`, `outbox_notification`, `user_account`, etc.) se crearán en los lotes específicos que implementen sus puertos y servicios (T011, T012, T020, T022), respetando estrictamente el principio YAGNI y evitando código muerto.
2. **Ordenamiento Natural de Habitaciones (`RoomRepositoryAdapter`)**: Para garantizar el orden natural requerido por el plan (`101, 101A, 102, 1001`), se implementó un comparador robusto que descompone el número de habitación extrayendo primero la parte numérica entera y luego el sufijo alfanumérico.
3. **Desacoplamiento Relacional (V1 Flyway)**: Conforme a las reglas de integración y arquitectura, `daily_reservation_room.room_id` no tiene restricción de clave foránea hacia `room` para evitar que una habitación inexistente enviada por Módulo 2 aborte la ingesta diaria. Del mismo modo, los campos de usuario (`actor_id`, `created_by`, etc.) no tienen FK a `user_account`, manejándose como cadenas de texto simples con el `username`.
4. **Inmutabilidad de Ocupantes Físicos (`RoomGuest`)**: La clase de dominio `RoomGuest` no posee métodos setter y `RoomGuestRepositoryPort` únicamente expone `saveAll` y `findByStayId` (sin métodos de modificación ni borrado), asegurando el cumplimiento de la Regla Transversal 4.
5. **Contexto Transaccional en `lockById`**: Se anotó `lockById` en `RoomRepositoryAdapter` con `@Transactional` para satisfacer el requisito de JPA/Hibernate de una transacción activa al ejecutar `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`), permitiendo su uso tanto dentro de transacciones de casos de uso (vía propagación `REQUIRED`) como en invocaciones atómicas aisladas.

## Archivos Creados y Modificados
### Migraciones de Base de Datos (Flyway - T007):
- `backend/src/main/resources/db/migration/V1__init_schema.sql`: Creación de las 11 tablas base (`room`, `room_state_history`, `room_audit_log`, `room_decommission`, `stay`, `room_guest`, `daily_reservation`, `daily_reservation_room`, `daily_reservation_message_log`, `outbox_notification`, `user_account`), índices, restricciones (`floor > 0`, `room_number` único) y las 4 secuencias de PostgreSQL para el Outbox (`outbox_seq_habitacion_checkin`, `outbox_seq_habitacion_checkout`, `outbox_seq_facturacion_checkout`, `outbox_seq_habitacion_mantenimiento`).
- `backend/src/main/resources/db/migration/V2__seed_rooms.sql`: Semilla sintética de prueba con 14 habitaciones en 3 pisos (categorías SENCILLA, DOBLE, SUITE y BOUTIQUE), estados `Available` e `Inactive`, con sus periodos iniciales abiertos en `room_state_history` (`REGISTER_ROOM`) y las bajas vigentes en `room_decommission` para las habitaciones inactivas.

### Dominio y Puertos de Salida (T008):
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomCategory.java`: Enum canónico de categorías (`SENCILLA`, `DOBLE`, `SUITE`, `BOUTIQUE`).
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/Room.java`: Entidad de dominio pura de habitación física con control de concurrencia optimista (`version`).
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/Stay.java`: Entidad de dominio de estancia física formalizada en Check-In.
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomGuest.java`: Entidad inmutable de ocupante físico con campos migratorios opcionales.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/RoomRepositoryPort.java`: Contrato de persistencia y consulta con bloqueo pesimista y orden natural.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/StayRepositoryPort.java`: Contrato de persistencia de estancias.
- `backend/src/main/java/com/hospitua/habitaciones/application/ports/out/RoomGuestRepositoryPort.java`: Contrato de persistencia inmutable de huéspedes.

### Adaptadores de Persistencia e Infraestructura JPA (T008):
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/RoomJpaEntity.java`: Entidad JPA para `room` con `@Version`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/StayJpaEntity.java`: Entidad JPA para `stay` con `@Version`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/entity/RoomGuestJpaEntity.java`: Entidad JPA para `room_guest`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataRoomJpaRepository.java`: Interfaz Spring Data con `@Lock(PESSIMISTIC_WRITE)`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataStayJpaRepository.java`: Interfaz Spring Data para estancias.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/repository/SpringDataRoomGuestJpaRepository.java`: Interfaz Spring Data para huéspedes.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/RoomMapper.java`: Mapper dominio <-> JPA para Room.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/StayMapper.java`: Mapper dominio <-> JPA para Stay.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/mapper/RoomGuestMapper.java`: Mapper dominio <-> JPA para RoomGuest.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/RoomRepositoryAdapter.java`: Adaptador que implementa `RoomRepositoryPort`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/StayRepositoryAdapter.java`: Adaptador que implementa `StayRepositoryPort`.
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/adapter/RoomGuestRepositoryAdapter.java`: Adaptador que implementa `RoomGuestRepositoryPort`.

### Configuración y Plan Modificados:
- `backend/src/test/java/com/hospitua/habitaciones/TestcontainersConfiguration.java`: Ajustada visibilidad a `public` para reutilización en pruebas de persistencia.
- `documentos/PLAN/base/plan.md`: Marcadas como completadas `[x]` las tareas T007 y T008.

### Pruebas Automatizadas (Testcontainers PostgreSQL):
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/FlywayMigrationAndSchemaTest.java`: Verifica la aplicación de V1 y V2, el conteo de 14 habitaciones (12 Available, 2 Inactive), los periodos iniciales abiertos en `room_state_history`, las bajas activas en `room_decommission`, el cumplimiento de restricciones (`floor > 0` y `room_number` único), y la existencia de las secuencias del Outbox.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/RoomRepositoryAdapterTest.java`: Verifica guardado y lectura, orden natural estricto (`101, 101A, 102, 1001`), exclusión de estados con `findByStatusNot`, bloqueo pesimista concurrente con `lockById`, y detección de conflictos con `ObjectOptimisticLockingFailureException`.
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/out/persistence/StayAndGuestRepositoryAdapterTest.java`: Verifica guardado y lectura de `Stay` y `RoomGuest` (con campos migratorios nulos y no nulos), y valida por introspección la inmutabilidad de `RoomGuest`.

## Resultado de `mvnw.cmd verify`
- **Docker / Testcontainers**: Contenedor PostgreSQL 16 y RabbitMQ 3.13 inicializados correctamente con `@ServiceConnection`.
- **Modo Hibernate**: Validado en modo `validate` contra el esquema creado por Flyway (cero discrepancias de esquema).
- **Formateo Spotless**: 36 archivos analizados y 100% limpios (`BUILD SUCCESS`).
- **Pruebas Totales**: **27 tests ejecutados, 0 fallos, 0 errores, 0 omitidos**.
- **Resultado final**: `BUILD SUCCESS` en `mvnw.cmd verify`.
