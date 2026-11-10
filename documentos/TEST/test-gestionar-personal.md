# Test Case: Gestionar Personal

**Created**: 2026-11-07  
**Spec/Plan Link**: [spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) | [plan-gestionar-personal.md](../PLAN/plan-gestionar-personal.md)  
**Status**: Draft  

---

## Test Overview

| Field | Value |
| --- | --- |
| **Test Case ID** | TC-023-GestionarPersonal |
| **Product Name & Version** | Hospitua Módulo 1 v0.1 |
| **Author** | Equipo Módulo 1 |
| **Current Owner** | Por asignar |
| **Execution Type** | Manual |
| **Priority** | P2 |

---

## Test Objectives and Document References

### Objectives
Verificar la capacidad del Administrador para registrar nuevas cuentas de usuario del personal en sus diferentes áreas operativas, consultar y filtrar la lista de personal, editar datos y contraseñas de cuentas activas, desactivar cuentas de forma segura protegiendo al último administrador y a la propia cuenta activa, y garantizar el aprovisionamiento de la cuenta inicial de administración en el arranque del sistema.

### Document References
| Document Type | Document Name & Link |
| --- | --- |
| Functional Specification | [spec-gestionar-personal.md](../SPEC/spec-gestionar-personal.md) |
| Implementation Plan | [plan-gestionar-personal.md](../PLAN/plan-gestionar-personal.md) |

---

## Prerequisites and Test Environment

### Assumptions and Limitations
- Requiere el sistema Hospitua Módulo 1 con sus servicios activos y la base de datos PostgreSQL inicializada con la tabla `user_account`.
- Las pantallas de gestión de personal (`/administracion/personal`, `/administracion/personal/nuevo`, `/administracion/personal/:username/editar` y el diálogo de desactivación) están completamente implementadas en React y conectadas a los endpoints REST `/api/staff`.

### Dependencies
- El servicio de autenticación (`POST /api/auth/login`) debe estar configurado para validar las credenciales registradas.

### Test Environment Requirements
- Navegador web moderno con acceso a la aplicación Hospitua.
- Base de datos con al menos dos cuentas de Administrador activas (ej. `admin` y `lmendez`).

### Test Data and Accounts
- Cuenta Administrador de prueba: `lmendez` (Administración).
- Cuenta a registrar: `jramirez`, nombre "Jorge Ramírez", área "Limpieza", contraseña "Limpieza2026".
- Credenciales de pruebas del equipo.

---

## Test Execution Flow

### Section 1: Flujo Principal - Registro y Consulta de Personal

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Iniciar sesión como Administrador y navegar a Personal | Menú principal > Administración > Personal `/administracion/personal` | Usuario `lmendez` | Se visualiza la pantalla "Personal" con el subtítulo "Registra, edita o desactiva las cuentas con las que el personal inicia sesión.", el botón "Registrar personal" y la tabla con columnas: Usuario, Nombre completo, Área, Estado, Acciones. | [ ] | |
| 2 | Abrir formulario de registro | Pantalla de Personal > Clic en botón "Registrar personal" | - | Se abre la pantalla "Registrar personal" (`/administracion/personal/nuevo`) con los campos obligatorios: Usuario, Contraseña, Nombre completo y Área. | [ ] | |
| 3 | Completar formulario de registro y confirmar | Formulario de registro > Diligenciar campos > Clic en botón "Registrar personal" | Usuario: "jramirez", Contraseña: "Limpieza2026", Nombre: "Jorge Ramírez", Área: "Limpieza" | El sistema valida la información, cifra la contraseña con BCrypt, crea la cuenta activa y muestra la tarjeta de confirmación con los datos registrados y botón para volver a la lista. | [ ] | |
| 4 | Verificar cuenta recién creada en el listado | Pantalla de Personal `/administracion/personal` | Usuario `jramirez` | La cuenta `jramirez` figura en la tabla con Nombre "Jorge Ramírez", Área "Limpieza", badge verde "Activa" y los botones de acción "Editar" y "Desactivar". | [ ] | |
| 5 | Probar inicio de sesión con la cuenta creada | Cerrar sesión > Ir a login > Ingresar credenciales de `jramirez` | Usuario: "jramirez", Contraseña: "Limpieza2026" | Inicio de sesión exitoso; el usuario accede al área correspondiente a Limpieza (`/limpieza`). | [ ] | |

### Section 2: Edición y Desactivación de Cuenta

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | Abrir edición de cuenta activa | Iniciar sesión como Administrador > Personal > Fila de `jramirez` > Clic en "Editar" | Cuenta `jramirez` | Se despliega la pantalla "Editar personal" (`/administracion/personal/jramirez/editar`) con el campo Usuario bloqueado en solo lectura, el nombre y área precargados y el campo de contraseña vacío para ingreso opcional. | [ ] | |
| 7 | Modificar área y actualizar contraseña | Pantalla de edición > Cambiar área a "Mantenimiento" > Ingresar nueva contraseña > Clic en "Guardar cambios" | Área: "Mantenimiento", Nueva contraseña: "Mantenimiento2026" | El sistema guarda los cambios, actualiza el área y cifra la nueva contraseña, mostrando la confirmación exitosa con botón para volver al personal. | [ ] | |
| 8 | Solicitar desactivación de la cuenta | Lista de Personal > Fila de `jramirez` > Clic en botón "Desactivar" | Cuenta `jramirez` | Se despliega el diálogo modal "Desactivar cuenta" mostrando el usuario, nombre y área, junto con la advertencia: "La persona no podrá iniciar sesión. La cuenta no se borra: el historial seguirá mostrando su nombre." | [ ] | |
| 9 | Confirmar desactivación | Diálogo modal > Clic en botón "Desactivar" | - | La cuenta pasa a estado "Desactivada" (active: false). En el listado se muestra con badge gris "Desactivada" y la columna de acciones muestra "—" sin botones de edición ni desactivación. | [ ] | |
| 10 | Verificar bloqueo de inicio de sesión de cuenta desactivada | Cerrar sesión > Intentar login con `jramirez` | `jramirez` y su contraseña | El sistema rechaza el acceso informando que las credenciales son inválidas o la cuenta está desactivada. | [ ] | |

### Section 3: Validaciones, Unicidad y Reglas de Seguridad

| Step # | Step Description | Screen / Path / Action | Test Data | Expected Result | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| 11 | Intentar registrar usuario duplicado | Formulario "Registrar personal" > Ingresar usuario existente | Usuario: "jramirez" | El sistema rechaza el registro y muestra el mensaje: "Ya existe una cuenta con el usuario jramirez." | [Pass] | Verificado por test automatizado (`StaffIntegrationTest` y `RegisterStaffPage.test.tsx`) |
| 12 | Intentar registrar con contraseña menor a 8 caracteres | Formulario "Registrar personal" > Contraseña de 6 caracteres | Contraseña: "12345" | El sistema bloquea el guardado y muestra el mensaje de validación: "La contraseña debe tener al menos 8 caracteres." | [Pass] | Verificado por test automatizado (`RegisterStaffPage.test.tsx`) |
| 13 | Intentar desactivar la propia cuenta en sesión | Fila de la cuenta activa en sesión (`lmendez`) > Intentar desactivar | Usuario en sesión | El sistema rechaza la operación y muestra la alerta: "No puedes desactivar tu propia cuenta." | [Pass] | Verificado por test automatizado (`StaffIntegrationTest` y `StaffListPage.test.tsx`) |
| 14 | Intentar desactivar la única cuenta activa de Administración | Intentar desactivar al único Administrador existente | Última cuenta de Administración | El sistema bloquea la acción e informa: "No se puede desactivar: es la única cuenta activa de Administración." | [Pass] | Verificado por test automatizado (`StaffIntegrationTest` y `StaffListPage.test.tsx`) |

---

## Edge Cases & Boundary Conditions

| Case # | Description | Preconditions | Inputs / Actions | Expected Results | Pass/Fail | Comments |
| --- | --- | --- | --- | --- | --- | --- |
| EC-001 | Normalización de usuario con mayúsculas y espacios | Formulario de registro | Usuario: " JRamirez " | El sistema normaliza el valor a "jramirez", comprobando unicidad y guardándolo en minúsculas. | [Pass] | Verificado por test automatizado (`StaffIntegrationTest`) |
| EC-002 | Desactivación de personal con tarea de limpieza abierta | Cuenta de limpieza asignada a una habitación en limpieza | Desactivar la cuenta | El sistema libera la tarea, la habitación regresa a `PendingCleaning` y no se pierde trazabilidad. | [Pass] | Verificado por test automatizado (`StaffIntegrationTest`) |
| EC-003 | Arranque inicial del sistema sin administradores activos | Base de datos limpia sin cuentas | Iniciar aplicación | El sistema crea automáticamente la cuenta inicial `admin` con contraseña predeterminada para el primer acceso. | [Pass] | Verificado por test automatizado (`StaffIntegrationTest`) |
| EC-004 | Edición simultánea de la misma cuenta por dos administradores | Dos sesiones abren la edición de la misma cuenta | Guardar cambios casi en simultáneo | La segunda operación recibe error de concurrencia optimista (`CONCURRENT_UPDATE`) exigiendo recargar los datos. | [Pass] | Verificado por test automatizado (`StaffIntegrationTest`) |

---

## Defect Severity Reference

| Severity | Description | Examples |
| --- | --- | --- |
| **Critical (P1)** | Impide la operación total de una función básica, causa pérdida o corrupción de datos sin solución alternativa inmediata. | Se permite desactivar al último administrador dejando el sistema sin posibilidad de gestión. |
| **Major (P2)** | Falla mayor en una funcionalidad relevante con solución temporal disponible pero no ideal. | Las contraseñas se almacenan en texto plano en la base de datos o se devuelven en los endpoints REST. |
| **Moderate (P3)** | Defecto que afecta un flujo secundario o una validación no crítica. | Filtro de búsqueda por texto sensible a mayúsculas o tildes. |
| **Minor (P4)** | Inconsistencias estéticas menores o errores tipográficos en etiquetas que no impiden la comprensión. | Alineación del selector de área en el formulario de edición. |

