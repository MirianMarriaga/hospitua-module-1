# Reporte de Pendientes y Decisiones Técnicas

## Lista de `TODO(pendiente)` dejados en el código
Actualmente **no se dejaron comentarios `// TODO(pendiente)`** en el código fuente. Todas las tareas de este lote (T023, T009 y T006) fueron implementadas en su totalidad conforme a las especificaciones y lineamientos arquitectónicos.

## Archivos Creados y Modificados
### Código de Producción:
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/config/ClockConfig.java` (T023: Bean `Clock` con zona `America/Bogota`).
- `backend/src/main/java/com/hospitua/habitaciones/domain/model/RoomStatus.java` (T009: Enum de los 8 estados canónicos sin lógica externa).
- `backend/src/main/java/com/hospitua/habitaciones/domain/exception/DomainException.java` (T009: Excepción base de dominio).
- `backend/src/main/java/com/hospitua/habitaciones/domain/exception/RoomNotFoundException.java` (T009: Excepción de habitación no encontrada).
- `backend/src/main/java/com/hospitua/habitaciones/domain/exception/InvalidRoomTransitionException.java` (T009: Excepción de transición inválida con `currentStatus`).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/in/web/ApiError.java` (T009: Record estandarizado de error con `OffsetDateTime` y `details` opcional).
- `backend/src/main/java/com/hospitua/habitaciones/infrastructure/adapters/in/web/GlobalExceptionHandler.java` (T009: `@RestControllerAdvice` con traducción de errores comunes según catálogo).
- `backend/src/main/resources/db/dev-data/.gitkeep` (T006: Directorio para semillas y datos de desarrollo).

### Configuración Modificada:
- `backend/src/main/resources/application.yml` (T006: Eliminado `fallback-apply-time`, agregada ubicación `classpath:db/dev-data` en perfil `dev`, y configuradas variables de entorno en perfiles `dev`, `test` y `prod`).
- `documentos/PLAN/base/plan.md` (Marcadas como completadas `[x]` las tareas T023, T009 y T006).

### Pruebas Automatizadas:
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/config/ClockConfigTest.java` (Verifica zona `America/Bogota` y offset `-05:00`).
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/adapters/in/web/GlobalExceptionHandlerTest.java` (Verifica `MockMvc` standalone para cada excepción, HTTP status, `errorCode`, timestamp con `-05:00` y `details`).
- `backend/src/test/java/com/hospitua/habitaciones/infrastructure/config/ApplicationConfigurationProfilesTest.java` (Verifica perfiles `dev`, `test`, `prod`, exclusión de `fallback-apply-time` y carga de `dev-data`).

## Resultado de `mvnw.cmd verify` y Estado de Docker
- **Docker**: En ejecución activa localmente.
- **Testcontainers**: Contenedores de PostgreSQL 16 y RabbitMQ 3.13 inicializados correctamente con `@ServiceConnection`.
- **Formateo Spotless**: 100% limpio y verificado con `spotless:check` (palantirJavaFormat).
- **Pruebas Totales**: 14 tests ejecutados (13 unitarios y de slice web/configuración + 1 de integración de contexto con Testcontainers), **0 fallos, 0 errores**.
- **Resultado final**: `BUILD SUCCESS` en `mvnw.cmd verify`.

## Verificación de Archivos Duplicados
- Se verificó `src/main/resources`: **no existe archivo `application.properties` duplicado**.
