# Implementation Plan: Procesar Datos de Huéspedes

**Date**: 2026-10-06  
**Spec**: [spec-procesar-datos-huespedes.md](../SPEC/spec-procesar-datos-huespedes.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso interno de dominio **Procesar Datos de Huéspedes**, invocado de manera obligatoria como inclusión (`<<includes>>`) por el Paso 2 de **Registrar Check-In** (`plan-registrar-check-in.md`).

Responsabilidades:
1. **Precarga e inmutabilidad del Titular**: Inicializar los datos del Huésped Titular a partir de la reserva de Módulo 2 (`fullName`, `documentType`, `documentNumber`, `nationality`), presentándolo en modo de solo lectura (con selector de país bloqueado) e inamovible (`isReservationGuest = true`).
2. **Captura de acompañantes**: Permitir agregar y editar acompañantes solicitando exactamente los 4 campos obligatorios (Nombre completo, Tipo de documento, Número de documento y Nacionalidad desde catálogo) hasta completar el `guestCount` de la reserva.
3. **Validaciones de aforo y unicidad**:
   - Validar coincidencia estricta: la cantidad de ocupantes registrados debe ser igual a `guestCount` de la reserva.
   - Validar capacidad física: la cantidad de ocupantes no puede superar `room.maxCapacity`. Al alcanzarse, se deshabilita el botón "+ Agregar huésped" y se despliega la nota informativa.
   - Validar duplicidad interna: impedir que dos ocupantes en la misma habitación compartan el mismo tipo y número de documento.
4. **Activación reactiva de SIRE**: Si cualquier ocupante tiene nacionalidad distinta de "Colombia", desplegar de inmediato el banner informativo de reporte migratorio y activar la extensión a `plan-enviar-datos-huespedes-extranjeros.md` (`<<extend>>`), sin solicitar al recepcionista campos técnicos migratorios (tipo o fecha de movimiento).
5. **Persistencia inmutable**: Tras la confirmación en Check-In, persistir a los ocupantes como registros inmutables `RoomGuest` vinculados a la `Stay`.

---

## Technical Context

- **Language/Version**: Java 21 (LTS)
- **Primary Dependencies**: Spring Boot 3.3+, Spring Data JPA, Hibernate Validator, Lombok
- **Storage**: PostgreSQL 16+ (Tabla `room_guest`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers
- **Target Platform**: Servidor Linux/Windows + UI Web en navegador (React 18 / JSX)
- **Project Type**: Web Application monorepo (`backend/` + `frontend/`)
- **Performance Goals**:
  - Validación de reglas de ocupantes en memoria < 10 ms.
  - Tiempo de diligenciamiento en mostrador < 1 min para 2 personas.
- **Constraints**:
  - Titular inmodificable: no permitir borrar ni editar al titular de la reserva.
  - Inmutabilidad pos-confirmación: una vez creado el Check-In, no se admiten modificaciones a `RoomGuest`.
  - Cero campos técnicos migratorios en pantalla (tipo o fecha de movimiento son asignados automáticamente).

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   ├── spec-procesar-datos-huespedes.md
│   ├── spec-registrar-check-in.md
│   └── spec-enviar-datos-huespedes-extranjeros.md
└── PLAN/
    ├── base/
    │   └── plan.md
    ├── plan-procesar-datos-huespedes.md
    ├── plan-registrar-check-in.md
    └── plan-enviar-datos-huespedes-extranjeros.md
```

### Source Code

```text
backend/src/
├── main/java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── RoomGuest.java
│   │   │   ├── DocumentType.java
│   │   │   └── GuestValidationResult.java
│   │   ├── exception/
│   │   │   ├── GuestCountMismatchException.java
│   │   │   ├── RoomCapacityExceededException.java
│   │   │   └── DuplicateGuestDocumentException.java
│   │   └── ports/
│   │       ├── in/
│   │       │   └── ProcessGuestsUseCase.java
│   │       └── out/
│   │           └── RoomGuestPersistencePort.java
│   ├── application/
│   │   ├── service/
│   │   │   └── GuestProcessingService.java
│   │   └── dto/
│   │       ├── GuestRegistrationDto.java
│   │       ├── ValidateGuestsCommand.java
│   │       └── GuestValidationSummary.java
│   └── infrastructure/
│       └── adapters/
│           ├── in/
│           │   └── web/
│           │       └── GuestValidationController.java
│           └── out/
│               └── persistence/
│                   ├── RoomGuestRepositoryAdapter.java
│                   └── SpringDataRoomGuestRepository.java
frontend/src/
├── components/guests/
│   ├── GuestFormList.jsx
│   ├── GuestCard.jsx
│   ├── CountrySelect.jsx
│   └── SireAlertBanner.jsx
└── services/
    └── guestValidationService.js
```

**Structure Decision**: El servicio `GuestProcessingService` actúa en la capa de aplicación ejecutando validaciones de dominio sin acoplamiento a la base de datos hasta que el caso de uso padre (`CheckInExecutionService`) comanda la persistencia atómica.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Verificar la tabla `room_guest` y el enum `DocumentType` en [PLAN/base/plan.md](base/plan.md).
- [ ] T002 Crear el catálogo estático de países homologado (`countries.json`) accesible por frontend y backend.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T003 Implementar la entidad JPA y modelo de dominio `RoomGuest` con anotaciones `@Immutable`.
- [ ] T004 Implementar el puerto de persistencia `RoomGuestPersistencePort` y su adaptador Spring Data JPA.
- [ ] T005 Configurar las excepciones `GuestCountMismatchException`, `RoomCapacityExceededException` y `DuplicateGuestDocumentException` en `GlobalExceptionHandler`.

---

## Phase 3: User Story 1 - Captura de datos de ocupantes y titular fijo (Priority: P1)

**Goal**: Permitir la precarga del titular de solo lectura y la captura ágil de acompañantes con sus 4 datos obligatorios.

**Independent Test**: Invocar el validador con el titular precargado y un acompañante completo, verificando que se asigna `isReservationGuest = true` al titular y `false` al acompañante, permitiendo avanzar al siguiente paso.

### Tests for User Story 1

- [ ] T006 [P] [US1] Unit test: Verificar que el titular queda marcado con `isReservationGuest = true` y campos de solo lectura.
- [ ] T007 [P] [US1] Unit test: Rechazar avance si algún campo requerido de un acompañante está en blanco o nulo.
- [ ] T008 [P] [US1] Component test frontend para `GuestCard.jsx`: verificar que la tarjeta del titular no tiene botón de eliminar y la lista desplegable de país está deshabilitada.

### Implementation for User Story 1

- [ ] T009 [P] [US1] Implementar en `GuestProcessingService` el método de ensamblado y validación de lista de ocupantes `validateGuests(ValidateGuestsCommand command)`.
- [ ] T010 [US1] Implementar el endpoint REST `POST /api/guests/validate` en `GuestValidationController.java` para validaciones en vivo desde frontend.
- [ ] T011 [US1] Construir los componentes frontend `GuestFormList.jsx` y `GuestCard.jsx` con soporte para precarga del titular y adición dinámica de acompañantes.

---

## Phase 4: User Story 2 - Coincidencia con `guestCount`, control de capacidad física y notificación SIRE (Priority: P2)

**Goal**: Validar discrepancias con `guestCount`, bloqueos por capacidad máxima física de la habitación y despliegue del banner reactivo de SIRE.

**Independent Test**: Intentar validar 1 huésped cuando `guestCount = 2` (debe lanzar excepción); intentar registrar 3 huéspedes en habitación con capacidad 2 (debe bloquear); registrar un huésped con nacionalidad "España" y comprobar que se marca `hasForeignGuests = true` y la UI despliega el banner informativo.

### Tests for User Story 2

- [ ] T012 [P] [US2] Unit test: Arrojar `GuestCountMismatchException` si `guests.size() != guestCount`.
- [ ] T013 [P] [US2] Unit test: Arrojar `RoomCapacityExceededException` si `guests.size() > maxCapacity`.
- [ ] T014 [P] [US2] Unit test: Arrojar `DuplicateGuestDocumentException` si dos ocupantes tienen el mismo número de documento.
- [ ] T015 [P] [US2] Unit test: Verificar que si `nationality != 'Colombia'` se marca la bandera `hasForeignGuests = true`.
- [ ] T016 [P] [US2] Component test frontend para `SireAlertBanner.jsx`: validar visibilidad inmediata al seleccionar país extranjero.

### Implementation for User Story 2

- [ ] T017 [P] [US2] Implementar las reglas de aforo y unicidad de documentos en `GuestProcessingService.java`.
- [ ] T018 [US2] Implementar la deshabilitación del botón "+ Agregar huésped" en `GuestFormList.jsx` al alcanzar `maxCapacity` o `guestCount`.
- [ ] T019 [US2] Integrar el banner reactivo `SireAlertBanner.jsx` en la pantalla de datos de huéspedes.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T020 Sanitización de nombres: eliminación de espacios múltiples accidentales con soporte para caracteres internacionales y tildes.
- [ ] T021 Verificar que en ningún momento la pantalla solicite tipo de movimiento o fechas migratorias al recepcionista.

---

## Dependencies & Execution Order

- **Foundational**: Requiere `RoomGuest` y `Room` de [PLAN/base/plan.md](base/plan.md).
- **Consumidores**: Invocado por [PLAN/plan-registrar-check-in.md](plan-registrar-check-in.md) en el Paso 2.
- **Extensión**: Dispara [PLAN/plan-enviar-datos-huespedes-extranjeros.md](plan-enviar-datos-huespedes-extranjeros.md) si `hasForeignGuests = true`.
