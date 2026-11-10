# Necesidades de Revisión y Pruebas Manuales — Hospitua Módulo 1

## 1. Resumen Ejecutivo
Se han generado y actualizado los 23 documentos formales de casos de prueba en `documentos/TEST/` cubriendo el 100% de los casos de uso especificados para el Módulo 1 (Gestión de Habitaciones, Check-in y Check-out).
Con la reciente actualización del frontend, todas las áreas (Recepción, Limpieza, Mantenimiento, Administración, Gerencia y Gestión de Personal) se encuentran completamente implementadas en React con el 100% de sus pruebas unitarias y de integración pasando (37 suites de pruebas, 256 tests en Vitest).
Los puntos de revisión manual se centran ahora en la validación funcional en navegador con servicios reales en marcha, la interacción con módulos externos simulados (Módulo 2 de Reservas y Módulo 3 de Facturación), la concurrencia entre navegadores y la experiencia de usuario.

---

## 2. Detalle por Caso de Prueba

### 2.1. TC-001: Consultar Reservas (`test-consultar-reservas.md`)
- **Verificado contra el código**:
  - DTOs y adaptadores de integración en `backend/src/main/java/com/hospitua/habitaciones/domain/model/` y `infrastructure/adapters/out/external/`.
  - Mecanismos de cache local e ingesta programada a las 00:00 (America/Bogota).
  - Manejo de excepciones `ExternalModule2UnavailableException` (`MODULE_2_UNAVAILABLE`).
- **Requiere revisión manual**:
  - Probar la respuesta de la interfaz de recepción cuando el simulador o servicio de Módulo 2 está apagado o responde con timeout (> 3 s).
  - Verificar visualmente que las reservas canceladas no figuren en la pestaña de llegadas de hoy.
  - Comprobar la sincronización manual vía botón de refresco y el tiempo de respuesta.
- **Diferencias entre el spec y el sistema**:
  - Ninguna discrepancia funcional en la lógica de dominio; Módulo 2 es un servicio mockeado en desarrollo local.
- **Datos o cuentas a preparar**:
  - Cuenta con rol de Recepción.
  - Cargar en el simulador de Módulo 2: 1 reserva para hoy (sin check-in), 1 reserva cancelada para hoy, 1 reserva futura (mañana) y 1 reserva con check-in ya registrado.

---

### 2.2. TC-002: Consultar Panel de Recepción (`test-consultar-panel-recepcion.md`)
- **Verificado contra el código**:
  - Componente React `frontend/src/pages/panel/ReceptionPanel.tsx` y hooks de React Query.
  - Pestañas de Llegadas de hoy y Salidas de hoy con contadores numéricos en tiempo real.
  - Alertas visuales para habitaciones no disponibles al momento de la llegada (en limpieza, mantenimiento o inhabilitadas).
- **Requiere revisión manual**:
  - Comprobar el refresco automático o invalidación de cache al completar un check-in o check-out desde otra pestaña del navegador.
  - Revisar el diseño responsive de la tabla y los badges de estado en pantallas de baja resolución (tablets de recepción).
- **Diferencias entre el spec y el sistema**:
  - Mensaje de lista vacía en llegadas: en el frontend es `"No hay reservas de llegada que mostrar."` mientras que en el texto inicial del spec figuraba `"No hay reservas que mostrar."`.
- **Datos o cuentas a preparar**:
  - Cuenta de usuario del área de Recepción.
  - Al menos 3 reservas del día de hoy cargadas (una con habitación Disponible, una con habitación en Limpieza y una con habitación Fuera de Servicio).

---

### 2.3. TC-003: Registrar Check-In (`test-registrar-check-in.md`)
- **Verificado contra el código**:
  - Controlador backend `StayController.java` (`POST /api/stays/checkin`) y servicio de aplicación.
  - Componente frontend `frontend/src/pages/checkin/CheckInPage.tsx` con su modal de confirmación y comprobación de habitación asignada.
  - Transición atómica de la habitación a estado Ocupada (`Occupied`) y registro en `room_state_history`.
- **Requiere revisión manual**:
  - Probar la validación de fecha/hora si se intenta hacer check-in antes de la hora oficial (antes de las 15:00) y verificar si el sistema solicita confirmación o lo admite.
  - Verificar el comportamiento visual del modal de confirmación y el bloqueo del botón "Confirmar check-in" para evitar doble clic.
- **Diferencias entre el spec y el sistema**:
  - Ninguna en la lógica central de negocio; coincidencia total en los mensajes de validación y estados.
- **Datos o cuentas a preparar**:
  - Cuenta de usuario de Recepción.
  - Habitación "101" en estado Disponible con reserva asignada para hoy.
  - Habitación "102" en estado En Limpieza con reserva asignada para hoy (para prueba de rechazo).

---

### 2.4. TC-004: Consultar Liquidación (`test-consultar-liquidacion.md`)
- **Verificado contra el código**:
  - Adaptador de llamada a Módulo 3 (Facturación) y DTOs de liquidación en backend.
  - Pantalla frontend de Check-out (`CheckOutPage.tsx`) que integra el resumen de consumos, saldo pendiente y desglose de cobros.
- **Requiere revisión manual**:
  - Comprobar la visualización del spinner de carga mientras se consulta Módulo 3 y el mensaje de error exacto cuando el servicio externo de facturación no responde.
  - Verificar el redondeo y formateo de valores monetarios en moneda colombiana (COP) con separadores de miles.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Estancia activa con consumos liquidados en cero ($0 saldo pendiente).
  - Estancia activa con consumos pendientes de pago (saldo > $0) para validar el bloqueo de check-out.

---

### 2.5. TC-005: Registrar Check-Out (`test-registrar-check-out.md`)
- **Verificado contra el código**:
  - Endpoint `POST /api/stays/checkout` en `StayController.java`.
  - Frontend `frontend/src/pages/checkout/CheckOutPage.tsx` con verificación previa de liquidación saldada.
  - Cierre del registro de estancia, paso automático de la habitación a `PendingCleaning` y aviso asíncrono a Módulo 3.
- **Requiere revisión manual**:
  - Simular el escenario de checkout tardío (después de las 13:00) y verificar si se informa al recepcionista sobre posibles recargos.
  - Verificar que tras confirmar el check-out, la habitación figure inmediatamente en el panel de limpieza como "Pendiente de limpieza".
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Recepción.
  - Habitación "101" en estado Ocupada con salida programada para hoy y cuenta saldada.

---

### 2.6. TC-006: Consultar Tarifa Base (`test-consultar-tarifa-base.md`)
- **Verificado contra el código**:
  - Endpoint público/interno `GET /api/rooms/{roomId}/base-rate` y DTOs asociados.
  - Almacenamiento del campo `baseRate` con tipo `NUMERIC(12,2)` en tabla `room`.
- **Requiere revisión manual**:
  - Verificar la consulta para todas las categorías de habitaciones (Sencilla, Doble, Suite, Boutique) asegurando que no se expongan datos sensibles del hotel.
  - Probar tiempos de respuesta concurrentes desde herramientas de prueba de carga simulando llamadas masivas del motor de reservas.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Habitaciones semilla 101, 103, 105, 205 con sus tarifas predeterminadas.

---

### 2.7. TC-007: Marcar Habitación Reservada (`test-marcar-habitacion-reservada.md`)
- **Verificado contra el código**:
  - Transición de la máquina de estados `Available -> Reserved` gatillada por la ingesta diaria de reservas a las 00:00 o asignación anticipada.
  - Registro de auditoría en `room_state_history` con actor nulo ("Sistema") o recepcionista.
- **Requiere revisión manual**:
  - Validar la actualización visual del badge de estado en el panel de recepción de "Disponible" a "Reservada".
  - Probar la restricción que impide marcar como reservada una habitación que se encuentra en limpieza o mantenimiento.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Habitación 101 en estado Disponible.
  - Evento de reserva con llegada prevista para el día en curso.

---

### 2.8. TC-008: Marcar Habitación como Disponible (`test-marcar-habitacion-como-disponible.md`)
- **Verificado contra el código**:
  - Transición desde `Reserved -> Available` (por cancelación o no-show) o desde `Inactive -> Available` (reactivación administrativa en `ReactivationPage.tsx`).
- **Requiere revisión manual**:
  - Probar en pantalla la reactivación administrativa de una habitación dada de baja en `/administracion/habitaciones/{id}/reactivacion`.
  - Comprobar que una habitación liberada por no-show se refleje inmediatamente en el inventario disponible.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Habitación 104 (Inactiva en la semilla V2).
  - Habitación en estado Reservada cuya reserva haya sido cancelada.

---

### 2.9. TC-009: Marcar Pendiente a Limpieza (`test-marcar-pendiente-a-limpieza.md`)
- **Verificado contra el código**:
  - Transición originada al confirmar check-out (`Occupied -> PendingCleaning`) o al finalizar una reparación correctiva (`DisabledForRepairs -> PendingCleaning`).
  - Creación de registro en la tabla de asignaciones de limpieza (`V3__cleaning_maintenance_schema.sql`).
  - Pantalla React `CleaningPanelPage.tsx` (`/limpieza`) con resumen de pendientes y disponibles.
- **Requiere revisión manual**:
  - Comprobar que la habitación aparezca automáticamente en la lista de tareas del personal de limpieza sin necesidad de recargar la página.
  - Verificar visualmente la prioridad alta asignada a habitaciones con llegada prevista para el mismo día ("Check-out con llegada hoy").
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Habitación ocupada lista para check-out.
  - Cuenta de personal de Limpieza.

---

### 2.10. TC-010: Marcar Habitación en Limpieza (`test-marcar-habitacion-en-limpieza.md`)
- **Verificado contra el código**:
  - Lógica de asignación y bloqueo de concurrencia: al hacer clic en "Iniciar limpieza", se despliega el modal `ConfirmDialog` y la tarea se autoasigna al usuario en sesión impidiendo que otro camarero la tome.
  - Transición a `InCleaning` en `room_state_history` y redirección automática a `/limpieza/tarea` (`ActiveCleaningTaskPage.tsx`).
- **Requiere revisión manual**:
  - Probar concurrencia real abriendo dos navegadores con usuarios de limpieza distintos e intentar iniciar la limpieza de la misma habitación al mismo tiempo.
  - Verificar que el botón "Iniciar limpieza" cambie de estado a "En curso" deshabilitando la acción para otros miembros.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Dos cuentas de usuario del área de Limpieza.
  - Habitación en estado `PendingCleaning`.

---

### 2.11. TC-011: Confirmar Fin de Limpieza (`test-confirmar-fin-de-limpieza.md`)
- **Verificado contra el código**:
  - Servicio de aplicación y lógica de bifurcación de estados:
    - Sin daño ni llegada -> `Available`.
    - Sin daño pero con llegada hoy -> `Reserved`.
    - Con reporte de daño documentado -> `DisabledForRepairs`.
    - Con bloqueo técnico programado para hoy -> `TechnicalBlock`.
  - Componentes React `ActiveCleaningTaskPage.tsx` y `ConfirmCleaningEndDialog.tsx` con retorno de aviso al panel.
- **Requiere revisión manual**:
  - Abrir el modal "Confirmar limpieza" y probar el ingreso opcional de descripción de daño.
  - Verificar que con daño documentado, la habitación pase a "Fuera de servicio" y se alerte con banner verde en el panel de limpieza.
  - Comprobar que al liberar la tarea (`ReleaseCleaningTaskDialog`), la habitación regrese a `PendingCleaning` para otro compañero.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Limpieza.
  - Habitación 101 en estado `InCleaning`.

---

### 2.12. TC-012: Consultar Panel de Limpieza (`test-consultar-panel-limpieza.md`)
- **Verificado contra el código**:
  - Componente React `CleaningPanelPage.tsx` (`/limpieza`) con conteos de pendientes y disponibles, búsqueda por número de habitación y actualización periódica.
  - Redirección automática a `/limpieza/tarea` si el usuario tiene una tarea activa abierta.
- **Requiere revisión manual**:
  - Evaluar la legibilidad de la tabla y la búsqueda rápida por número de habitación.
  - Probar el filtrado y visualización en dispositivos móviles / tablets.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Limpieza.
  - Habitaciones en pisos 1, 2 y 3 en estados Disponibles y Pendientes de limpieza.

---

### 2.13. TC-013: Marcar Inhabilitada por Reparaciones (`test-marcar-habitacion-inhabilitada-por-reparaciones.md`)
- **Verificado contra el código**:
  - Tabla `damage_report` en PostgreSQL y controlador `MaintenanceController`.
  - Componentes React `DamageReportDialog.tsx` y `DamageReportViewDialog.tsx` con contador de caracteres hasta 500.
  - Transición a `DisabledForRepairs` desde `Available` o desde `InCleaning` (fin de limpieza con daño).
- **Requiere revisión manual**:
  - Probar el formulario de reporte de avería desde `/mantenimiento` o `/limpieza`.
  - Verificar que la alerta roja se despliegue en el panel de recepción si la habitación afectada tenía una reserva asignada para hoy.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Mantenimiento o Limpieza.
  - Habitación 101 en estado Disponible.

---

### 2.14. TC-014: Confirmar Reparación Finalizada (`test-confirmar-reparacion-finalizada.md`)
- **Verificado contra el código**:
  - Transición forzosa obligatoria: `DisabledForRepairs` o `TechnicalBlock` SIEMPRE transicionan a `PendingCleaning`.
  - Cierre de tarea con `outcome = Completed` o `Released`.
  - Pantallas React `ActiveRepairTaskPage.tsx`, `ConfirmRepairEndDialog.tsx` y `ReleaseRepairTaskDialog.tsx` conectadas a `POST /api/maintenance/tasks/{taskId}/complete` y `release`.
- **Requiere revisión manual**:
  - Acceder a `/mantenimiento/tarea`, hacer clic en "Confirmar fin" y confirmar en el modal.
  - Verificar que la habitación transicione a "Pendiente de limpieza" y figure de inmediato en la cola de trabajo del personal de limpieza.
  - Probar "Liberar tarea" y verificar el aviso en el panel: "Dejaste la habitación [número] para otro compañero."
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Mantenimiento.
  - Habitación 101 en estado `DisabledForRepairs` o `TechnicalBlock`.

---

### 2.15. TC-015: Consultar Panel de Mantenimiento (`test-consultar-panel-mantenimiento.md`)
- **Verificado contra el código**:
  - Pantalla React `MaintenancePanelPage.tsx` (`/mantenimiento`) y backend `GET /api/maintenance/panel`.
  - Contadores resumen (`MaintenanceSummary.tsx`), búsqueda por número de habitación y grupos ordenados (vencidos primero, luego inhabilitadas y al final demás estados).
  - Acciones contextuales por fila: "Iniciar reparaciones", "Programar mantenimiento", "Reportar daño", "Ver informe".
- **Requiere revisión manual**:
  - Comprobar la visualización del indicador de días transcurridos fuera de servicio y la etiqueta "Vencido" en bloqueos que superaron su fecha estimada de fin.
  - Probar la apertura de los diálogos modales de reporte y programación desde el panel.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Mantenimiento.
  - Habitaciones con daño registrado y con bloqueos técnicos programados.

---

### 2.16. TC-016: Programar Bloqueo Técnico para Habitación (`test-programar-bloqueo-tecnico-para-habitacion.md`)
- **Verificado contra el código**:
  - Diálogo React `ScheduleTechnicalBlockDialog.tsx` conectado a `POST /api/rooms/{roomId}/technical-blocks`.
  - Diálogo de consulta `TechnicalBlockViewDialog.tsx`.
  - Verificación previa de cruces con reservas (Módulo 2) y mantenimientos antes de persistir.
  - Diferenciación entre inicio hoy (aplica de inmediato a `TechnicalBlock`) e inicio futuro (queda en `Scheduled`).
- **Requiere revisión manual**:
  - Probar la validación de fechas en pantalla: fecha de fin anterior a inicio, fechas en el pasado y rangos mayores a 90 días.
  - Verificar el comportamiento al registrar un bloqueo con conflicto de reserva activa: comprobar que el mensaje de error liste el código de reserva en conflicto.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Mantenimiento.
  - Habitación 101 disponible.
  - Habitación 102 con reserva activa en las fechas seleccionadas.

---

### 2.17. TC-017: Consultar Información de Mantenimientos (`test-consultar-informacion-mantenimientos.md`)
- **Verificado contra el código**:
  - Servicio REST de solo lectura (`GET /api/rooms/{roomId}/maintenances`) implementado en `MaintenanceAvailabilityController.java`.
  - Regla de negocio: la respuesta indica disponibilidad de mantenimiento sin revelar descripciones ni justificaciones privadas del hotel.
  - Exclusión de bloqueos vencidos para fechas futuras (la reserva tiene prioridad).
- **Requiere revisión manual**:
  - Probar la respuesta de la API con herramientas de prueba HTTP (Postman/curl) verificando que el tiempo de respuesta sea inferior a 200 ms.
  - Comprobar que una consulta con fecha de fin anterior a inicio retorne error 400 con código `INVALID_DATE_RANGE`.
- **Diferencias entre el spec y el sistema**:
  - Ninguna discrepancia conceptual; contrato alineado con Módulo 2.
- **Datos o cuentas a preparar**:
  - Habitaciones con bloqueos vigentes y sin bloqueos.

---

### 2.18. TC-018: Registrar Habitación (`test-registrar-habitacion.md`)
- **Verificado contra el código**:
  - Formulario React `RegisterRoomPage.tsx` (`/administracion/habitaciones/nueva`) con validaciones Zod y llamadas REST a `POST /api/rooms`.
  - Tarjeta de confirmación `RegisterRoomSuccess.tsx` con botones "Registrar otra" y "Volver al inventario".
  - Apertura del primer periodo en `room_state_history` con estado inicial `Available`.
- **Requiere revisión manual**:
  - Probar en el navegador el ingreso de números de habitación con espacios o minúsculas (ej. " 402a ") y verificar que se normalicen a mayúsculas sin espacios.
  - Probar el intento de registrar un número de habitación que ya existe en estado Inactivo (ej. "104") y verificar el mensaje de rechazo 409.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Administrador.
  - Datos de nueva habitación (ej. 407, Piso 4, Doble, Capacidad 2, Tarifa 250000).

---

### 2.19. TC-019: Editar Habitación (`test-editar-habitacion.md`)
- **Verificado contra el código**:
  - Pantalla React `EditRoomPage.tsx` (`/administracion/habitaciones/:roomId/editar`).
  - Bloqueo optimista mediante el campo `version` detectando `CONCURRENT_UPDATE`.
  - Restricción estricta: solo se permite editar habitaciones en estado "Disponible" (`Available`).
  - Tabla comparativa "Antes / Ahora" en la pantalla de éxito con valores tachados.
- **Requiere revisión manual**:
  - Intentar editar una habitación que esté Ocupada o En Limpieza ingresando directamente la URL y verificar la pantalla de bloqueo con alerta amarilla.
  - Probar guardar sin hacer ningún cambio y verificar el mensaje informativo "No has modificado ningún dato."
  - Simular concurrencia abriendo dos pestañas de edición y guardando en ambas secuencialmente.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Administrador.
  - Habitación 101 (Disponible) y Habitación 103 (Ocupada).

---

### 2.20. TC-020: Dar de Baja Habitación (`test-dar-de-baja-habitacion.md`)
- **Verificado contra el código**:
  - Asistente de 4 pasos implementado en `DecommissionPage.tsx` (`/administracion/habitaciones/:roomId/baja`): Motivo, Confirmación, Verificación con spinner, Resultado.
  - Verificación previa con Módulo 2 de ausencia de reservas vigentes en los próximos 365 días.
  - Transición a estado `Inactive` y apertura de periodo en `room_state_history`.
- **Requiere revisión manual**:
  - Probar la baja de una habitación con reservas vigentes simuladas y verificar la visualización de la lista de reservas en conflicto en el Paso 4.
  - Probar el comportamiento del botón "Reintentar comprobación" cuando Módulo 2 sufre una interrupción temporal de red.
  - Validar que al seleccionar el motivo "Otro", el campo de texto libre sea obligatorio.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Administrador.
  - Habitación 101 (Disponible sin reservas).
  - Habitación con reservas activas futuras.

---

### 2.21. TC-021: Consultar Inventario de Habitaciones (`test-consultar-inventario-habitaciones.md`)
- **Verificado contra el código**:
  - Pantalla React `InventoryPage.tsx` (`/administracion/inventario` y `/gerencia/inventario`) con componentes `RoomInventoryFilters.tsx` y `RoomInventoryTable.tsx`.
  - Paginación dinámica de 10 en 10 con persistencia de filtros en parámetros de URL.
  - Inclusión de habitaciones inactivas por defecto en vistas internas y exclusión en APIs para Módulo 2.
  - Acciones contextuales según rol: Administrador (Editar, Dar de baja, Marcar como disponible) y Gerente (Ver historial).
- **Requiere revisión manual**:
  - Probar la interacción combinada de filtros (número + piso + estado + tipo) y verificar que al cambiar un filtro la paginación vuelva automáticamente a la página 1.
  - Probar el botón "Limpiar filtros" cuando una búsqueda no arroja resultados.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuentas con roles de Administrador y Gerente.
  - Semilla con las 14 habitaciones en sus diversos estados.

---

### 2.22. TC-022: Consultar Historial de Estados (`test-consultar-historial-de-estados.md`)
- **Verificado contra el código**:
  - Pantalla React `RoomStateHistoryPage.tsx` (`/gerencia/historial`) con filtros en `RoomStateHistoryFilters.tsx` y tabla en `RoomStateHistoryTable.tsx`.
  - Columnas de trazabilidad: Hab., Tipo, Estado, Inicio, Fin (o badge "En curso"), Responsable (o "Sistema"), y Flujo de origen.
  - Validación de rango de fechas: 'La fecha "Desde" no puede ser posterior a "Hasta".'
- **Requiere revisión manual**:
  - Navegar desde el inventario haciendo clic en "Ver historial" de una habitación y verificar que la URL y la tabla abran prefiltradas con esa unidad.
  - Probar la paginación de 15 en 15 y el filtrado por rangos de fechas históricos.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Cuenta de Gerente.
  - Habitación con al menos 4 transiciones históricas registradas.

---

### 2.23. TC-023: Gestionar Personal (`test-gestionar-personal.md`)
- **Verificado contra el código**:
  - Entidad `user_account` en PostgreSQL con contraseñas cifradas en BCrypt.
  - Pantallas React completas: `StaffListPage.tsx` (`/administracion/personal`), `RegisterStaffPage.tsx` (`/administracion/personal/nuevo`), `EditStaffPage.tsx` (`/administracion/personal/:username/editar`), y modal `DeactivateStaffDialog.tsx`.
  - Reglas de seguridad: no permitir desactivar la propia cuenta en sesión ni la última cuenta activa del área Administración.
- **Requiere revisión manual**:
  - Probar el flujo completo de registro de una nueva cuenta, cambio de contraseña y prueba de inicio de sesión real en la pantalla de login.
  - Validar que al desactivar una cuenta, esta no pueda volver a autenticarse pero su nombre siga apareciendo en el historial de estados de habitaciones.
  - Verificar las alertas de seguridad al intentar desactivar la propia cuenta o al único administrador del sistema.
- **Diferencias entre el spec y el sistema**:
  - Ninguna.
- **Datos o cuentas a preparar**:
  - Al menos dos cuentas de Administrador activas (`admin` y un administrador secundario).
  - Cuenta de pruebas a crear (ej. `jramirez`, área Limpieza).

---

## 3. Prioridades de Revisión y Ejecución de Pruebas

Para una revisión manual eficiente, se recomienda seguir el siguiente orden de prioridad:

1. **Prioridad Alta (P1 - Flujo Central Operativo y Transiciones Críticas)**:
   - **Check-in y Check-out (TC-003, TC-005)**: Probar el flujo completo desde la llegada del huésped, ocupación de la habitación, consulta de liquidación (TC-004) y liberación a limpieza.
   - **Ciclo de Limpieza y Daños (TC-009, TC-010, TC-011, TC-013, TC-014)**: Verificar que ninguna habitación pase de mantenimiento directamente a disponible sin pasar obligatoriamente por limpieza.
   - **Dar de Baja Habitación (TC-020)**: Probar el asistente de 4 pasos y verificar que se rechace la baja si existen reservas futuras en Módulo 2.

2. **Prioridad Media (P2 - Paneles de Operación y Administración)**:
   - **Panel de Recepción (TC-002)**: Validar las alertas de llegadas sobre habitaciones no disponibles.
   - **Panel de Mantenimiento y Bloqueo Técnico (TC-015, TC-016)**: Validar la tabla de mantenimiento, inicio de tareas de reparación y programación de bloqueos preventivos.
   - **Inventario y Edición de Habitaciones (TC-018, TC-019, TC-021)**: Validar la unicidad de número de habitación, el bloqueo optimista de edición y los filtros.
   - **Gestión de Personal (TC-023)**: Probar las restricciones de desactivación y la trazabilidad de usuarios.

3. **Prioridad Baja (P3 - Consultas Auxiliares y Auditoría)**:
   - **Tarifa Base y Disponibilidad de Mantenimientos (TC-006, TC-017)**: Verificar tiempos de respuesta de los endpoints de integración.
   - **Historial de Estados (TC-022)**: Validar los filtros temporales y la etiqueta "En curso".
