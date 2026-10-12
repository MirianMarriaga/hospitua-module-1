# Implementation Plan: Gestionar Personal

**Date**: 2026-10-12  
**Spec**: [spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) y arquitectura base en [PLAN/base/plan.md](base/plan.md)

---

## Summary

Implementar el caso de uso **Gestionar Personal** para el Administrador, desde la sección "Personal" de su vista. Todas las operaciones pasan por el backend mediante puertos de entrada; ninguna cuenta se escribe directamente en la base de datos (FR-001).

1. **Registrar** (FR-002 a FR-006): usuario, contraseña, nombre completo y área. El sistema normaliza y valida el usuario, cifra la contraseña con BCrypt y crea la cuenta activa.
2. **Consultar** (FR-007): lista paginada del personal con filtros por área y estado y búsqueda por usuario o nombre.
3. **Editar** (FR-006, FR-008): nombre, área y, de forma opcional, contraseña de una cuenta activa. El usuario no cambia.
4. **Desactivar** (FR-009, FR-010): previa confirmación, la cuenta queda desactivada sin borrarse. No se permite desactivar la propia cuenta ni la última cuenta activa del área Administración.
5. **Cuenta inicial** (FR-011): si al arrancar no hay ninguna cuenta activa de Administración, un componente de arranque crea la de la configuración llamando al mismo puerto de registro.

Cada operación registra la fecha, la hora y el Administrador responsable (FR-012), que es el usuario actual del encabezado `X-User` (regla 14 del plan base). Las cuentas son las que usa `POST /api/auth/login` del plan base (T021).

---

## Technical Context

- **Storage**: PostgreSQL 16+. Usa la tabla `user_account` del plan base (T007), con sus columnas de registro, edición y desactivación; no crea tablas.
- **Performance Goals**: registrar una cuenta en menos de 2 minutos de interacción (SC-001).
- **Constraints**:
  - La contraseña se cifra con BCrypt en el backend y nunca se devuelve, se registra en logs ni se guarda en texto plano (FR-004; SC-002).
  - El usuario se normaliza (sin espacios al inicio ni al final y en minúsculas) antes de validar su formato y su unicidad. La unicidad se garantiza también con la clave primaria de `user_account.username` (FR-003).
  - El usuario no es editable, porque `room_state_history.actor_id` y las tareas lo referencian (FR-008).
  - Desactivar no borra la fila: `active = false`. El login y `CurrentUserResolver` ya rechazan las cuentas desactivadas (regla 14 del plan base; SC-003).
  - La validación de "propia cuenta" y "último Administrador activo" se hace dentro de la transacción, bloqueando las cuentas activas de Administración con `SELECT … FOR UPDATE`, para que dos desactivaciones simultáneas no dejen el sistema sin Administrador (FR-010).
  - Ediciones simultáneas: control optimista con `user_account.version` (caso borde "Ediciones simultáneas").
  - En esta etapa no hay permisos por rol (regla 14 del plan base): la sección "Personal" solo aparece en la vista del Administrador.

---

## Convenciones de los endpoints

- **Usuario actual**: el Administrador responsable se toma del encabezado `X-User` (regla 14 del plan base).
- **Fechas**: ISO 8601 con zona de Colombia (ej. `2026-10-12T10:05:00-05:00`).
- **Errores**: esquema `ApiError` del plan base, con `details` cuando aporta contexto.
- **Áreas**: la API usa `area` con los valores `RECEPCION`, `ADMINISTRACION`, `GERENCIA`, `LIMPIEZA` y `MANTENIMIENTO`; la interfaz los muestra como "Recepción", "Administración", "Gerencia", "Limpieza" y "Mantenimiento".
- **Contraseña**: viaja solo en el cuerpo de `POST` y `PATCH`; ninguna respuesta la incluye.
- **Errores comunes a todos los endpoints**:

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `CURRENT_USER_REQUIRED` | `CurrentUserRequiredException` | Falta `X-User`, el usuario no existe o su cuenta está desactivada (regla 14 del plan base) | "Tu sesión terminó. Inicia sesión de nuevo." |

---

## POST /api/staff

**Descripción:** Registra una cuenta del personal, activa y con la contraseña cifrada (FR-001 a FR-006, FR-012).

### Petición (Request)

**Headers:**

```http
X-User: lmendez
Content-Type: application/json
```

**Body (JSON):** todos los campos son obligatorios.

| Campo | Tipo | Regla | Ejemplo |
| --- | --- | --- | --- |
| `username` | string | Se normaliza a minúsculas sin espacios en los extremos; 3 a 30 caracteres; solo letras sin tilde, números, punto y guion bajo; único (FR-003) | `"jramirez"` |
| `password` | string | Al menos 8 caracteres (FR-004) | `"Limpieza2026"` |
| `fullName` | string | No vacío | `"Jorge Ramírez"` |
| `area` | string | `RECEPCION`, `ADMINISTRACION`, `GERENCIA`, `LIMPIEZA` o `MANTENIMIENTO` (FR-002) | `"LIMPIEZA"` |

```json
{
  "username": "jramirez",
  "password": "Limpieza2026",
  "fullName": "Jorge Ramírez",
  "area": "LIMPIEZA"
}
```

### Respuesta (Response)

**Status Code:** `201 Created`

**Campos de la Respuesta:** los que muestra la confirmación (FR-006).

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `username` | string | Usuario normalizado | `"jramirez"` |
| `fullName` | string | Nombre completo | `"Jorge Ramírez"` |
| `area` | string | Área | `"LIMPIEZA"` |
| `active` | boolean | Siempre `true` (FR-005) | `true` |

**Body (JSON):**

```json
{
  "username": "jramirez",
  "fullName": "Jorge Ramírez",
  "area": "LIMPIEZA",
  "active": true
}
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` | Falta un campo obligatorio; `details.fields` lista los campos pendientes (FR-002, esc. 3) | "Completa los campos obligatorios: {campos}." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` | Usuario con caracteres no permitidos o fuera de 3 a 30 caracteres (FR-003, esc. 3) | "El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` | Contraseña de menos de 8 caracteres (FR-004, esc. 3) | "La contraseña debe tener al menos 8 caracteres." |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` | Área fuera del catálogo (FR-002) | "Selecciona un área válida." |
| 409 | `USERNAME_ALREADY_EXISTS` | `UsernameAlreadyExistsException` | Ya existe una cuenta, activa o desactivada, con ese usuario, incluida una registrada al mismo tiempo (FR-003, esc. 2) | "Ya existe una cuenta con el usuario {usuario}." |

```json
{
  "errorCode": "USERNAME_ALREADY_EXISTS",
  "message": "Ya existe una cuenta con el usuario jramirez.",
  "timestamp": "2026-10-12T10:05:01-05:00",
  "path": "/api/staff"
}
```

---

## GET /api/staff

**Descripción:** Devuelve la lista paginada del personal, con filtros y búsqueda (FR-007).

### Petición (Request)

**Headers:**

```http
X-User: lmendez
Accept: application/json
```

**Parámetros:** todos opcionales y combinables (esc. 5).

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `area` | query | string | No | Una de las 5 áreas | `LIMPIEZA` |
| `active` | query | boolean | No | `true` (activas) o `false` (desactivadas); sin este filtro se incluyen ambas | `true` |
| `query` | query | string | No | Busca por coincidencia parcial en usuario o nombre, sin distinguir mayúsculas ni tildes | `ramirez` |
| `page` | query | integer | No | Página, desde 1 (por defecto 1); 10 cuentas por página | `1` |

**Body (JSON):** No aplica.

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:**

| Campo | Tipo | Descripción | Ejemplo |
| --- | --- | --- | --- |
| `items` | array | Cuentas de la página, ordenadas por nombre | |
| `items[].username` | string | Usuario | `"jramirez"` |
| `items[].fullName` | string | Nombre completo | `"Jorge Ramírez"` |
| `items[].area` | string | Área | `"LIMPIEZA"` |
| `items[].active` | boolean | `true` = "Activa", `false` = "Desactivada" | `true` |
| `page`, `size`, `totalElements`, `totalPages` | integer | Paginación; `size` siempre 10 | `1`, `10`, `12`, `2` |

```json
{
  "items": [
    { "username": "jramirez", "fullName": "Jorge Ramírez", "area": "LIMPIEZA", "active": true }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 12,
  "totalPages": 2
}
```

Una consulta sin coincidencias responde `200` con `items` vacío; el frontend muestra "Ninguna cuenta cumple los filtros aplicados".

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `ConstraintViolationException` o `MethodArgumentTypeMismatchException` | `area` fuera del catálogo, `active` no booleano o `page` no es un entero positivo | "Revisa los filtros aplicados." |

---

## GET /api/staff/{username}

**Descripción:** Devuelve una cuenta para cargar el formulario de edición y la confirmación de desactivación (FR-008, FR-009).

### Petición (Request)

**Headers:**

```http
X-User: lmendez
Accept: application/json
```

**Parámetros:**

| Parámetro | Ubicación | Tipo | Obligatorio | Descripción | Ejemplo |
| --- | --- | --- | --- | --- | --- |
| `username` | path | string | Sí | Usuario de la cuenta | `jramirez` |

### Respuesta (Response)

**Status Code:** `200 OK`

**Campos de la Respuesta:** los de `items[]` en `GET /api/staff`, más `version` (control optimista que envía la edición).

```json
{ "username": "jramirez", "fullName": "Jorge Ramírez", "area": "LIMPIEZA", "active": true, "version": 3 }
```

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 404 | `STAFF_NOT_FOUND` | `StaffAccountNotFoundException` | No existe una cuenta con ese usuario | "No se encontró la cuenta." |

---

## PATCH /api/staff/{username}

**Descripción:** Edita el nombre, el área y, si se envía, la contraseña de una cuenta activa (FR-006, FR-008, FR-012).

### Petición (Request)

**Headers:**

```http
X-User: lmendez
Content-Type: application/json
```

**Body (JSON):** `version` es obligatoria. `password` es opcional: si falta o está vacía, la contraseña actual se conserva (esc. 7). `username` no se acepta: el usuario no cambia.

```json
{
  "version": 3,
  "fullName": "Jorge Ramírez",
  "area": "MANTENIMIENTO",
  "password": "Mantenimiento2026"
}
```

### Respuesta (Response)

**Status Code:** `200 OK`, con los mismos campos que `POST /api/staff` más la `version` nueva.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `MethodArgumentNotValidException` | Nombre vacío, área fuera del catálogo o contraseña nueva de menos de 8 caracteres (FR-004, FR-008) | El mensaje del campo |
| 404 | `STAFF_NOT_FOUND` | `StaffAccountNotFoundException` | No existe la cuenta | "No se encontró la cuenta." |
| 409 | `ACCOUNT_INACTIVE` | `AccountInactiveException` | La cuenta está desactivada (caso borde "Edición de una cuenta desactivada") | "La cuenta está desactivada; no se puede editar." |
| 409 | `CONCURRENT_UPDATE` | `ObjectOptimisticLockingFailureException` | Otro Administrador cambió la cuenta después de abrir el formulario (caso borde "Ediciones simultáneas") | "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales." |

---

## POST /api/staff/{username}/deactivation

**Descripción:** Desactiva una cuenta del personal sin borrarla (FR-009, FR-010, FR-012).

### Petición (Request)

**Headers:**

```http
X-User: lmendez
```

**Body (JSON):** No aplica. La confirmación se pide en la interfaz antes de enviar la petición (esc. 9).

### Respuesta (Response)

**Status Code:** `200 OK`, con los campos de `POST /api/staff` y `active` = `false`.

### Respuestas de error

| Status Code | errorCode | Excepción | Cuándo ocurre | Texto en la interfaz |
| --- | --- | --- | --- | --- |
| 404 | `STAFF_NOT_FOUND` | `StaffAccountNotFoundException` | No existe la cuenta | "No se encontró la cuenta." |
| 409 | `ACCOUNT_INACTIVE` | `AccountInactiveException` | La cuenta ya estaba desactivada (caso borde "Cuenta ya desactivada") | "La cuenta ya está desactivada." |
| 409 | `CANNOT_DEACTIVATE_SELF` | `CannotDeactivateSelfException` | El Administrador intenta desactivar su propia cuenta (FR-010, esc. 10) | "No puedes desactivar tu propia cuenta." |
| 409 | `LAST_ADMINISTRATOR` | `LastAdministratorException` | Es la última cuenta activa del área Administración (FR-010, esc. 10) | "No se puede desactivar: es la única cuenta activa de Administración." |

---

## Project Structure

### Documentation

```text
documentos/
├── SPEC/
│   └── spec-gestionar-personal.md
└── PLAN/
    ├── base/
    │   └── plan.md                                # Regla 14, POST /api/auth/login (T021) y tabla user_account
    └── plan-gestionar-personal.md
```

### Source Code

```text
backend/src/main/
├── java/com/hospitua/habitaciones/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── StaffAccount.java                  # Cuenta del personal y sus reglas (usuario, área, activa)
│   │   │   └── StaffArea.java                     # Enum: RECEPCION, ADMINISTRACION, GERENCIA, LIMPIEZA, MANTENIMIENTO
│   │   ├── exception/
│   │   │   ├── UsernameAlreadyExistsException.java    # 409
│   │   │   ├── StaffAccountNotFoundException.java     # 404
│   │   │   ├── AccountInactiveException.java          # 409
│   │   │   ├── CannotDeactivateSelfException.java     # 409
│   │   │   └── LastAdministratorException.java        # 409
│   │   └── ports/
│   │       ├── in/
│   │       │   ├── RegisterStaffAccountUseCase.java
│   │       │   ├── ListStaffAccountsUseCase.java
│   │       │   ├── GetStaffAccountUseCase.java
│   │       │   ├── EditStaffAccountUseCase.java
│   │       │   └── DeactivateStaffAccountUseCase.java
│   │       └── out/
│   │           ├── StaffAccountRepositoryPort.java
│   │           └── PasswordEncoderPort.java       # Cifrado de contraseñas; lo usa también LoginService (plan base, T021)
│   ├── application/
│   │   ├── service/
│   │   │   ├── RegisterStaffAccountService.java
│   │   │   ├── StaffAccountQueryService.java      # Listar y obtener
│   │   │   ├── EditStaffAccountService.java
│   │   │   └── DeactivateStaffAccountService.java
│   │   └── dto/
│   │       ├── RegisterStaffAccountCommand.java
│   │       ├── EditStaffAccountCommand.java
│   │       └── StaffAccountDto.java               # Sin contraseña
│   └── infrastructure/
│       ├── adapters/
│       │   ├── in/
│       │   │   ├── web/StaffController.java       # /api/staff
│       │   │   └── startup/StaffAccountBootstrapper.java   # Cuenta inicial de Administrador (FR-011)
│       │   └── out/
│       │       ├── persistence/repository/UserAccountJpaRepository.java   # Spring Data sobre UserAccountJpaEntity (plan base)
│       │       ├── persistence/adapter/JpaStaffAccountAdapter.java
│       │       └── security/BCryptPasswordEncoderAdapter.java   # Implementa PasswordEncoderPort con spring-security-crypto
│       └── config/
│           └── StaffBootstrapProperties.java      # hospitua.bootstrap.admin.username, password y full-name (variables de entorno)
frontend/src/
├── components/
│   └── staffAreaLabels.ts                         # RECEPCION → "Recepción", …
├── pages/administracion/
│   ├── StaffListPage.tsx                          # Lista, filtros, búsqueda y "Registrar personal"
│   ├── RegisterStaffPage.tsx
│   ├── EditStaffPage.tsx
│   ├── StaffConfirmation.tsx                      # Confirmación de registro y de edición
│   └── DeactivateStaffDialog.tsx                  # Confirmación de desactivación
└── api/
    └── staffService.ts
```

**Structure Decision**: Las cuatro operaciones y la cuenta inicial entran por puertos de entrada; `StaffAccountBootstrapper` no escribe SQL, sino que llama a `RegisterStaffAccountUseCase` con los datos de `StaffBootstrapProperties` y sin responsable: `created_by` queda nulo, como el `actor_id` de las acciones autónomas, y la interfaz lo muestra como "Sistema" (FR-011, FR-012). El cifrado vive detrás de `PasswordEncoderPort`, que comparten este plan y `LoginService` (plan base, T021), para que el dominio no dependa de la librería de cifrado (regla 1 del plan base). La sección "Personal" se agrega al menú de la vista del Administrador, junto a "Inventario".

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Agregar a `application.yml` las propiedades `hospitua.bootstrap.admin.username`, `hospitua.bootstrap.admin.password` y `hospitua.bootstrap.admin.full-name`, leídas de variables de entorno, sin valores por defecto en el repositorio.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T002 Verificar en `V1__init_schema.sql` (plan base, T007 y T021) la tabla `user_account` con `password_hash`, `area`, `active`, `version` y las columnas de registro, edición y desactivación (`created_at`, `created_by`, `updated_at`, `updated_by`, `deactivated_at`, `deactivated_by`).
- [ ] T003 [P] Implementar `StaffAccount` (normalización y formato del usuario, área, activa) y `StaffArea`.
- [ ] T004 [P] Implementar `PasswordEncoderPort` y `BCryptPasswordEncoderAdapter`, y verificar que `LoginService` (plan base, T021) use el mismo puerto.
- [ ] T005 [P] Implementar `StaffAccountRepositoryPort`, `UserAccountJpaRepository` (Spring Data sobre `UserAccountJpaEntity`) y `JpaStaffAccountAdapter`, incluida la consulta con bloqueo de las cuentas activas de Administración.
- [ ] T006 [P] Registrar en `GlobalExceptionHandler` `USERNAME_ALREADY_EXISTS`, `STAFF_NOT_FOUND`, `ACCOUNT_INACTIVE`, `CANNOT_DEACTIVATE_SELF` y `LAST_ADMINISTRATOR`, y mapear la violación de la clave primaria de `user_account` a `409 USERNAME_ALREADY_EXISTS`.

---

## Phase 3: User Story 1 - Registrar una cuenta del personal (Priority: P1)

**Goal**: Que el Administrador registre cuentas activas con la contraseña cifrada, y que el sistema cree la primera cuenta de Administrador al arrancar.

**Independent Test**: Registrar "jramirez" del área Limpieza y verificar el `201`, la cuenta activa, la contraseña cifrada en la base de datos y que `POST /api/auth/login` lo acepta. Repetir el registro y verificar el `409`. Arrancar con la base de datos vacía y verificar que existe la cuenta inicial de Administrador.

### Tests for User Story 1

- [ ] T007 [P] [US1] Unit test en `RegisterStaffAccountServiceTest`: el registro válido crea la cuenta activa, normaliza el usuario, guarda la contraseña cifrada y registra al Administrador y la hora de `Clock` (esc. 1; FR-002 a FR-005, FR-012).
- [ ] T008 [P] [US1] Unit test: rechazo con `USERNAME_ALREADY_EXISTS` si el usuario existe, incluido uno desactivado o con otras mayúsculas (esc. 2; FR-003).
- [ ] T009 [P] [US1] Unit test: rechazo de campos faltantes, usuario con caracteres no permitidos o fuera de 3 a 30 caracteres, contraseña de menos de 8 y área inválida (esc. 3; FR-002 a FR-004).
- [ ] T010 [US1] Integration test con Testcontainers: dos registros simultáneos con el mismo usuario; solo uno tiene éxito (FR-003).
- [ ] T011 [US1] Integration test: con la base de datos sin cuentas activas de Administración, `StaffAccountBootstrapper` crea la cuenta de la configuración por `RegisterStaffAccountUseCase`; si ya existe una, no crea otra (FR-011).
- [ ] T012 [US1] Integration test: la contraseña no aparece en ninguna respuesta ni en los logs (SC-002).
- [ ] T013 [P] [US1] Component test en `RegisterStaffPage`: señala cada campo con su error, restringe el área a las 5 opciones y muestra la confirmación con usuario, nombre, área y estado, sin contraseña (esc. 1 y 3; FR-006).

### Implementation for User Story 1

- [ ] T014 [US1] Implementar `RegisterStaffAccountService` (`@Transactional`): normalizar y validar el usuario, validar la unicidad, cifrar la contraseña con `PasswordEncoderPort`, crear la cuenta activa y registrar el responsable y la hora (FR-002 a FR-005, FR-012).
- [ ] T015 [US1] Implementar `StaffAccountBootstrapper` (al arrancar la aplicación) y `StaffBootstrapProperties`; la cuenta inicial queda con `created_by` nulo (FR-011, FR-012).
- [ ] T016 [US1] Agregar a `StaffController` el endpoint `POST /api/staff`.
- [ ] T017 [US1] Construir en el frontend `RegisterStaffPage.tsx`, `StaffConfirmation.tsx` y `staffAreaLabels.ts` (FR-006).

**Checkpoint**: El Administrador registra cuentas y el personal puede iniciar sesión.

---

## Phase 4: User Story 2 - Consultar el personal registrado (Priority: P1)

**Goal**: Que el Administrador vea la lista del personal con filtros, búsqueda y paginación.

**Independent Test**: Cargar 12 cuentas de distintas áreas, 2 desactivadas; verificar 2 páginas con las desactivadas incluidas, el filtro por área y estado y la búsqueda por nombre.

### Tests for User Story 2

- [ ] T018 [P] [US2] Unit test en `StaffAccountQueryServiceTest`: sin filtros devuelve todas las cuentas, incluidas las desactivadas, ordenadas por nombre y de 10 en 10 (esc. 4; FR-007).
- [ ] T019 [P] [US2] Unit test: los filtros por área y estado y la búsqueda por usuario o nombre se combinan (esc. 5; FR-007).
- [ ] T020 [P] [US2] Component test en `StaffListPage`: columnas usuario, nombre, área y estado en español; "Editar" y "Desactivar" solo en las cuentas activas; "Registrar personal" sobre la lista; "Mostrando X–Y de Z" (esc. 4 y 5; FR-007).

### Implementation for User Story 2

- [ ] T021 [US2] Implementar `StaffAccountQueryService` (`@Transactional(readOnly = true)`) y agregar a `StaffController` `GET /api/staff` y `GET /api/staff/{username}` (FR-007).
- [ ] T022 [US2] Construir `StaffListPage.tsx`, reutilizando `Pagination.tsx` (`plan-consultar-inventario-habitaciones.md`), y agregar "Personal" al menú de la vista del Administrador (FR-001, FR-007).

**Checkpoint**: La sección "Personal" muestra al personal registrado.

---

## Phase 5: User Story 3 - Editar una cuenta del personal (Priority: P2)

**Goal**: Que el Administrador cambie el nombre, el área o la contraseña de una cuenta activa.

**Independent Test**: Cambiar el área y la contraseña de "jramirez" y verificar que inicia sesión con la contraseña nueva. Editar solo el nombre con la contraseña vacía y verificar que la contraseña anterior sigue funcionando.

### Tests for User Story 3

- [ ] T023 [P] [US3] Unit test en `EditStaffAccountServiceTest`: cambia nombre y área, cifra la contraseña nueva y registra el responsable y la hora (esc. 6; FR-008, FR-012).
- [ ] T024 [P] [US3] Unit test: con la contraseña vacía se conserva la actual (esc. 7; FR-008).
- [ ] T025 [P] [US3] Unit test: rechazo de una cuenta desactivada con `ACCOUNT_INACTIVE` y de una contraseña nueva de menos de 8 caracteres (caso borde "Edición de una cuenta desactivada"; FR-004).
- [ ] T026 [US3] Integration test con Testcontainers: dos ediciones simultáneas con la misma `version`; la segunda recibe `409 CONCURRENT_UPDATE` (caso borde "Ediciones simultáneas").

### Implementation for User Story 3

- [ ] T027 [US3] Implementar `EditStaffAccountService` (`@Transactional`) y agregar a `StaffController` `PATCH /api/staff/{username}` (FR-006, FR-008, FR-012).
- [ ] T028 [US3] Construir `EditStaffPage.tsx`, con el usuario de solo lectura y el campo de contraseña opcional (FR-008).

**Checkpoint**: Las cuentas se pueden corregir y las contraseñas restablecer.

---

## Phase 6: User Story 4 - Desactivar una cuenta del personal (Priority: P2)

**Goal**: Que el Administrador desactive cuentas sin borrarlas, sin poder desactivarse a sí mismo ni al último Administrador.

**Independent Test**: Desactivar "jramirez" y verificar que el login lo rechaza y que el historial de estados sigue mostrando su nombre. Intentar desactivar la propia cuenta y la única cuenta activa de Administración y verificar los rechazos.

### Tests for User Story 4

- [ ] T029 [P] [US4] Unit test en `DeactivateStaffAccountServiceTest`: la cuenta queda con `active = false`, la fila no se borra y se registran el responsable y la hora (esc. 8; FR-009, FR-012).
- [ ] T030 [P] [US4] Unit test: rechazo con `CANNOT_DEACTIVATE_SELF`, con `LAST_ADMINISTRATOR` y con `ACCOUNT_INACTIVE` si ya estaba desactivada (esc. 10, caso borde "Cuenta ya desactivada"; FR-010).
- [ ] T031 [US4] Integration test con Testcontainers: dos desactivaciones simultáneas de las dos únicas cuentas activas de Administración; solo una tiene éxito y la otra recibe `LAST_ADMINISTRATOR` (FR-010).
- [ ] T032 [US4] Integration test: una cuenta desactivada recibe `401 INVALID_CREDENTIALS` en `POST /api/auth/login`, su `X-User` recibe `400 CURRENT_USER_REQUIRED`, y `GET /api/room-state-history` sigue mostrando su nombre como responsable (SC-003).
- [ ] T033 [P] [US4] Component test en `DeactivateStaffDialog`: muestra usuario, nombre y área, advierte que la persona no podrá iniciar sesión y cancelar no envía nada (esc. 9; FR-009).

### Implementation for User Story 4

- [ ] T034 [US4] Implementar `DeactivateStaffAccountService` (`@Transactional`): bloquear las cuentas activas de Administración, validar la propia cuenta y el último Administrador, y desactivar (FR-009, FR-010, FR-012).
- [ ] T035 [US4] Agregar a `StaffController` `POST /api/staff/{username}/deactivation` y construir `DeactivateStaffDialog.tsx` (FR-009).

**Checkpoint**: La gestión del personal queda completa.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T036 Revisar que ninguna clase escriba en `user_account` sin pasar por los puertos de este plan, salvo la migración que crea la tabla (FR-001).
- [ ] T037 Medir que el registro completo responda en menos de 2 segundos (SC-001).

---

## Dependencies & Execution Order

- **Foundational**: Requiere del plan base la tabla `user_account` (T007), el inicio de sesión y `CurrentUserResolver` (regla 14, T021 y T022), `ClockConfig` y `GlobalExceptionHandler`.
- **Planes relacionados**:
  - Plan base, T021: `LoginService` usa `PasswordEncoderPort` de este plan y rechaza las cuentas desactivadas.
  - `plan-consultar-historial-de-estados.md`: muestra `full_name` de `user_account` como responsable, también de las cuentas desactivadas.
  - `plan-consultar-inventario-habitaciones.md`: `Pagination.tsx` y el menú de la vista del Administrador.
- **Alcance provisional**: el manejo de contraseñas de este plan es provisional (regla 14 y punto abierto 1 del plan base). Si se adopta un inicio de sesión compartido, se retiran la contraseña y `PasswordEncoderPort`, y se conservan el registro, la consulta, la edición y la desactivación de las cuentas como perfil local.
- **Orden**: Phase 1 → Phase 2 → Phase 3 (la cuenta inicial debe existir antes que todo lo demás) → Phase 4 → Phase 5 → Phase 6 → Phase 7.
