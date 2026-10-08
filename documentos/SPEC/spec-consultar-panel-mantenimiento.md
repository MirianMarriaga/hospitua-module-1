# Especificación de Funcionalidad: Panel de Mantenimiento — Vista de Trabajo del Personal de Mantenimiento

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de mantenimiento
**Creado**: 2026-10-07

> **Nota**: Esta especificación describe una vista de consulta y despacho, no un caso de uso transaccional. No registra entidades ni transiciona estados de habitaciones por sí misma, y no tiene casos de uso `<<includes>>` ni `<<extends>>`.
>
> **Reparto de responsabilidades (sin doble definición)**: este documento es el único que define la **presentación** del panel: pestañas, columnas, orden, búsqueda, vista de tarea activa, textos de confirmación y mensajes, y origen de cada dato. Las **reglas de negocio** de cada acción (precondiciones, cambios de estado, registros y contenido de los informes) se definen solo en sus specs funcionales, que remiten a este documento para la vista:
>
> - Iniciar reparaciones, Confirmar fin y Liberar tarea → `spec-confirmar-reparacion-finalizada.md`
> - Programar bloqueo, Cancelar y Ver informe de un bloqueo → `spec-programar-bloqueo-tecnico-para-habitacion.md`
> - Reportar daño y Ver informe de un daño → `spec-marcar-habitacion-inhabilitada-por-reparaciones.md`

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Habitaciones por intervenir (Prioridad: P1)

Como miembro del personal de mantenimiento sin tarea activa, quiero ver las habitaciones inhabilitadas por reparaciones o en bloqueo técnico que esperan un técnico, para elegir qué reparación iniciar.

**Por qué esta prioridad**: Es la entrada al ciclo de mantenimiento: desde aquí se inician las reparaciones.

**Prueba Independiente**: Con habitaciones en `DisabledForRepairs` y `TechnicalBlock`, alguna con una reparación en curso, entrar como Personal de mantenimiento sin tarea activa y verificar que la tabla muestra solo las que no tienen reparación en curso, con sus columnas, orden y acciones.

**Escenarios de Aceptación**:

1. **Escenario**: Carga del panel (Happy Path)
   - **Dado** que el técnico inicia sesión y no tiene una tarea de reparación abierta
   - **Cuando** accede al panel
   - **Entonces** el sistema muestra el encabezado, el saludo, el buscador y dos pestañas, "Por intervenir" (activa por defecto) y "Programados". La tabla de "Por intervenir" tiene las columnas: Hab. · Tipo · Estado · Origen · Acciones

2. **Escenario**: Orden por fecha de origen
   - **Dado** una habitación "102" en `DisabledForRepairs` reportada el 04-10-2026 14:30 y una "210" en `TechnicalBlock` con fecha de inicio 06-10-2026
   - **Cuando** el panel se carga
   - **Entonces** "102" aparece antes que "210": se ordena por la fecha que muestra la columna Origen (fecha del reporte, o fecha de inicio del bloqueo), de la más antigua a la más reciente

3. **Escenario**: Iniciar reparaciones
   - **Dado** una habitación por intervenir
   - **Cuando** el técnico pulsa "Iniciar reparaciones"
   - **Entonces** se ejecuta la acción de *Confirmar reparación finalizada* (FR-002) y el panel muestra la vista de tarea activa (HU-4)

4. **Escenario**: Ver informe
   - **Dado** una habitación por intervenir
   - **Cuando** el técnico pulsa "Ver informe"
   - **Entonces** se abre en una ventana superpuesta el informe que originó la intervención: para `DisabledForRepairs`, el reporte de daño (contenido definido en *Marcar habitación inhabilitada por reparaciones*, FR-005); para `TechnicalBlock`, el bloqueo aplicado (contenido definido en *Programar bloqueo técnico para habitación*, FR-009)

5. **Escenario**: Una reparación liberada vuelve al listado
   - **Dado** que un técnico liberó su tarea en la habitación "102" (*Confirmar reparación finalizada*, FR-003)
   - **Cuando** el panel se actualiza
   - **Entonces** la habitación "102" vuelve a aparecer en "Por intervenir" con "Iniciar reparaciones"

---

### Historia de Usuario 2 - Bloqueos programados (Prioridad: P2)

Como miembro del personal de mantenimiento, quiero ver los mantenimientos preventivos programados que aún no se aplican, para revisarlos y cancelar los que se programaron por error.

**Por qué esta prioridad**: Da visibilidad a los bloqueos futuros y es el punto de entrada de la cancelación.

**Prueba Independiente**: Con bloqueos en `Scheduled`, `Applied`, `Expired` y `Cancelled`, abrir la pestaña "Programados" y verificar que solo aparecen los `Scheduled`, ordenados por fecha de inicio y con sus acciones.

**Escenarios de Aceptación**:

1. **Escenario**: Listado de programados
   - **Dado** que existen bloqueos en estado `Scheduled`
   - **Cuando** el técnico abre la pestaña "Programados"
   - **Entonces** la tabla muestra, ordenados por fecha de inicio, las columnas: Hab. · Inicio · Fin estimado · Responsable · Acciones (Ver informe, Cancelar)

2. **Escenario**: Cancelar un bloqueo programado
   - **Dado** un bloqueo `Scheduled`
   - **Cuando** el técnico pulsa "Cancelar" y acepta la confirmación "¿Deseas cancelar el bloqueo técnico de la habitación [número] programado del [inicio] al [fin estimado]? La habitación quedará libre para reservas en ese rango."
   - **Entonces** se ejecuta la cancelación de *Programar bloqueo técnico para habitación* (FR-011) y el bloqueo desaparece del listado

---

### Historia de Usuario 3 - Búsqueda de cualquier habitación (Prioridad: P2)

Como miembro del personal de mantenimiento, quiero buscar cualquier habitación por su número, para reportar un daño o programar un mantenimiento preventivo sobre ella.

**Por qué esta prioridad**: Es el punto de entrada de *Marcar habitación inhabilitada por reparaciones* y de *Programar bloqueo técnico* para el técnico.

**Prueba Independiente**: Buscar habitaciones en cada estado y verificar que el resultado muestra el estado y las acciones correspondientes.

**Escenarios de Aceptación**:

1. **Escenario**: Búsqueda de una habitación disponible
   - **Dado** que la habitación "204" está en `Available`
   - **Cuando** el técnico escribe "204" en el buscador
   - **Entonces** el panel muestra una ficha con número, tipo y estado de la habitación y las acciones "Reportar daño" y "Programar bloqueo"

2. **Escenario**: Búsqueda de una habitación ocupada o en limpieza
   - **Dado** que la habitación "205" está en `Occupied` (o en `Reserved`, `PendingCleaning` o `InCleaning`)
   - **Cuando** el técnico la busca
   - **Entonces** la ficha ofrece solo "Programar bloqueo"

3. **Escenario**: Búsqueda de una habitación en intervención o inactiva
   - **Dado** que la habitación buscada está en `DisabledForRepairs` o `TechnicalBlock` (o en `Inactive`)
   - **Cuando** el técnico la busca
   - **Entonces** la ficha ofrece "Ver informe" y, si no hay una reparación en curso, "Iniciar reparaciones" (ninguna acción si está `Inactive`)

---

### Historia de Usuario 4 - Vista de tarea activa (Prioridad: P1)

Como técnico con una reparación en curso, quiero ver solo la habitación que estoy reparando y las acciones para terminarla o dejarla, para cerrar mi tarea sin distracciones.

**Por qué esta prioridad**: Un técnico no puede tener más de una tarea activa (*Confirmar reparación finalizada*, FR-010); esta vista es la única forma de confirmar el fin de la reparación o liberarla.

**Prueba Independiente**: Con una `ReparationTask` abierta para el técnico, entrar al panel y verificar que solo se muestra su habitación con las acciones "Ver informe", "Confirmar fin" y "Liberar tarea", y que tras confirmar o liberar vuelve al panel general.

**Escenarios de Aceptación**:

1. **Escenario**: Ingreso con tarea activa
   - **Dado** que el técnico tiene una `ReparationTask` abierta para la habitación "101"
   - **Cuando** accede al panel
   - **Entonces** el sistema muestra únicamente la vista de tarea activa con número, tipo y estado de la habitación, la hora de inicio de su tarea y las acciones "Ver informe", "Confirmar fin" y "Liberar tarea"; las pestañas y el buscador no se muestran

2. **Escenario**: Confirmar fin
   - **Dado** la vista de tarea activa
   - **Cuando** el técnico pulsa "Confirmar fin" y acepta "¿Está seguro de que desea finalizar las reparaciones de la habitación [número]? La habitación pasará a la cola de limpieza."
   - **Entonces** se ejecuta *Confirmar reparación finalizada*, el panel vuelve al panel general y muestra "Reparación de la habitación [número] finalizada. Pasó a limpieza."

3. **Escenario**: Liberar tarea
   - **Dado** la vista de tarea activa
   - **Cuando** el técnico pulsa "Liberar tarea" y acepta "¿Deseas liberar la reparación de la habitación [número]? Quedará disponible para que otro técnico la inicie."
   - **Entonces** se ejecuta la liberación de *Confirmar reparación finalizada* (FR-003) y el panel vuelve al panel general, donde la habitación aparece de nuevo en "Por intervenir"

4. **Escenario**: Tarea liberada por el Administrador
   - **Dado** que el Administrador liberó la tarea del técnico mientras tenía la vista abierta
   - **Cuando** intenta confirmar el fin o liberarla
   - **Entonces** el panel muestra "Tu tarea en la habitación [número] fue liberada por la administración." y vuelve al panel general (*Confirmar reparación finalizada*, FR-011)

5. **Escenario**: Consultar el informe durante la reparación
   - **Dado** la vista de tarea activa de la habitación "101"
   - **Cuando** el técnico pulsa "Ver informe"
   - **Entonces** se abre, en solo lectura, el reporte de origen de su tarea (`SourceReportId`); al cerrarlo sigue en la vista de tarea activa

---

### Historia de Usuario 5 - Mensajes de resultado y de error (Prioridad: P2)

Como técnico, quiero saber siempre si mi acción se completó o por qué no, para no repetirla ni dejar una habitación o un bloqueo en un estado que no esperaba.

**Por qué esta prioridad**: Los specs funcionales piden retroalimentación y rechazos que informen el estado actual; esta historia define dónde y con qué texto se muestran.

**Prueba Independiente**: Ejecutar cada acción con éxito, con un rechazo y con un fallo técnico simulado, y verificar el mensaje de FR-010 en la zona de mensajes; enviar el formulario de programación con cada regla incumplida y verificar el error dentro del diálogo.

**Escenarios de Aceptación**:

1. **Escenario**: Programar un bloqueo con éxito
   - **Dado** el formulario de "Programar bloqueo" de la habitación "204" con datos válidos y sin cruces
   - **Cuando** el técnico pulsa "Programar"
   - **Entonces** el panel abre la pestaña "Programados", donde aparece el bloqueo, y muestra "Bloqueo programado para la habitación 204 del [inicio] al [fin estimado]."; si la fecha de inicio es hoy y el bloqueo se aplicó al registrarse, abre "Por intervenir" y muestra "Bloqueo aplicado en la habitación 204."

2. **Escenario**: Error de validación o cruce al programar
   - **Dado** el formulario de "Programar bloqueo" abierto
   - **Cuando** el técnico lo envía con una regla incumplida, con un cruce de reservas o mantenimientos, o sin poder verificar
   - **Entonces** el diálogo sigue abierto, conserva lo escrito y muestra el mensaje de FR-011 correspondiente

3. **Escenario**: Cancelar un bloqueo que ya no está programado
   - **Dado** que el técnico confirma "Cancelar bloqueo" de la habitación "305", pero el bloqueo se aplicó un instante antes
   - **Cuando** el sistema rechaza la cancelación
   - **Entonces** la zona de mensajes muestra "El bloqueo de la habitación 305 ya no está programado (ahora: Aplicado)." y el listado "Programados" se actualiza

4. **Escenario**: Fallo técnico
   - **Dado** que ocurre un fallo al guardar una acción
   - **Cuando** la operación falla y el sistema no aplica nada
   - **Entonces** la zona de mensajes muestra "No se pudo completar la operación. Inténtalo de nuevo." y la pantalla queda como estaba

---

### Casos Borde

- **Sin habitaciones por intervenir o sin programados**: La pestaña correspondiente muestra "No hay habitaciones que mostrar.".
- **Búsqueda de un número inexistente**: El panel muestra "No hay habitaciones que mostrar." sin errores técnicos.
- **Tolerancia de formato en la búsqueda**: El sistema elimina espacios al inicio y al final antes de comparar el número de habitación.
- **Dos técnicos pulsan la misma acción a la vez**: Solo el primero procede; el segundo ve el estado actual y la tabla se actualiza (FR-010).
- **Bloqueo que se aplica mientras el panel está abierto**: Desaparece de "Programados" y aparece en "Por intervenir" sin recargar.
- **Bloqueos `Applied`, `Expired` o `Cancelled`**: Nunca aparecen en "Programados".
- **Habitaciones con una reparación en curso**: No aparecen en "Por intervenir"; pertenecen a su titular hasta que la confirme o la libere (*Confirmar reparación finalizada*, FR-003 y FR-011).

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE mostrar al Personal de mantenimiento autenticado y sin tarea activa una pantalla compuesta por: encabezado con el nombre del sistema ("Hotel Hospitua · Mantenimiento") y chip de sesión (nombre y rol), saludo "Hola, [nombre]", subtítulo "Habitaciones por intervenir y mantenimientos programados", buscador de habitaciones y dos pestañas: "Por intervenir" (activa por defecto) y "Programados".

- **FR-002**: La tabla **Por intervenir** DEBE listar las habitaciones en `DisabledForRepairs` o `TechnicalBlock` **sin** `ReparationTask` abierta, con una fila por habitación y las columnas:
  - **Hab.** y **Tipo** (`Room`).
  - **Estado**: "Inhabilitada por reparaciones" o "Bloqueo técnico" (`Room.status`).
  - **Origen**: para `DisabledForRepairs`, la fecha y hora del `DamageReport` más reciente (DD-MM-YYYY HH:MM); para `TechnicalBlock`, la fecha de inicio del `TechnicalBlockReport` en estado `Applied` (DD-MM-YYYY) y, debajo, "Fin estimado: DD-MM-YYYY".
  - **Acciones**: Ver informe e Iniciar reparaciones.

- **FR-003**: La tabla **Por intervenir** DEBE ordenarse por la fecha que muestra la columna Origen (`ReportDateTime` del `DamageReport`, o `TechnicalBlockStartDate` del bloqueo), de la más antigua a la más reciente y, en caso de empate, por número de habitación.

- **FR-004**: La tabla **Programados** DEBE listar los `TechnicalBlockReport` en estado `Scheduled`, ordenados por fecha de inicio, con las columnas Hab., Inicio (`TechnicalBlockStartDate`), Fin estimado (`EstimatedTechnicalBlockEndDate`), Responsable (`MaintenanceStaffMemberId`) y las acciones Ver informe y Cancelar.

- **FR-005**: El buscador DEBE localizar **cualquier habitación** por número (coincidencia exacta) y mostrar una ficha con número, tipo y estado, y las acciones según el estado:
  - `Available`: Reportar daño y Programar bloqueo.
  - `Reserved`, `Occupied`, `PendingCleaning` o `InCleaning`: Programar bloqueo.
  - `DisabledForRepairs` o `TechnicalBlock`: Ver informe; además, Iniciar reparaciones si no hay una `ReparationTask` abierta.
  - `Inactive`: ninguna acción.

- **FR-006**: Cada acción DEBE llevar al flujo que la define, sin pedir de nuevo la habitación:
  - **Iniciar reparaciones** → *Confirmar reparación finalizada* (FR-002).
  - **Ver informe** → para `DisabledForRepairs`, el `DamageReport` (*Marcar habitación inhabilitada por reparaciones*, FR-005); para `TechnicalBlock` o un bloqueo programado, el `TechnicalBlockReport` (*Programar bloqueo técnico para habitación*, FR-009).
  - **Reportar daño** → formulario de *Marcar habitación inhabilitada por reparaciones* (FR-001).
  - **Programar bloqueo** → formulario de *Programar bloqueo técnico para habitación* (FR-001, FR-002).
  - **Cancelar** → confirmación con habitación, fecha de inicio y fecha estimada de fin y, al aceptar, *Programar bloqueo técnico para habitación* (FR-011).

- **FR-007**: Si el técnico autenticado tiene una `ReparationTask` abierta, el sistema DEBE mostrar únicamente la **vista de tarea activa**, con:
  - número, tipo y estado de la habitación y la hora de inicio de su tarea (`ReparationTask.StartDateTime`, HH:MM);
  - el título "Tarea activa";
  - tres acciones:
    - **Ver informe**, solo lectura, con el reporte de origen de la tarea (`ReparationTask.SourceReportId`); al cerrarlo se sigue en la vista de tarea activa;
    - **Confirmar fin**, con la confirmación "¿Está seguro de que desea finalizar las reparaciones de la habitación [número]? La habitación pasará a la cola de limpieza." (*Confirmar reparación finalizada*, FR-004 a FR-008);
    - **Liberar tarea**, con la confirmación "¿Deseas liberar la reparación de la habitación [número]? Quedará disponible para que otro técnico la inicie." (*Confirmar reparación finalizada*, FR-003).

  Tras confirmar el fin o liberar la tarea, el sistema vuelve al panel general. Si el Administrador liberó la tarea, muestra "Tu tarea en la habitación [número] fue liberada por la administración." y vuelve al panel general.

- **FR-008**: Las tablas, la ficha de búsqueda y la vista de tarea activa DEBEN reflejar los cambios de estado de habitaciones, tareas y bloqueos en menos de 2 segundos, sin recargar la página. Esto incluye los cambios de otros actores: reportes de daño de limpieza, aplicación automática de bloqueos, liberaciones de tareas y reparaciones iniciadas por otros técnicos.

- **FR-009**: Cuando una tabla o la búsqueda no tienen resultados, el sistema DEBE mostrar "No hay habitaciones que mostrar." sin errores técnicos.

- **FR-010**: El panel DEBE tener una **zona de mensajes** bajo el saludo (en la vista de tarea activa, bajo su título) que muestra el resultado de la última acción hasta la siguiente acción del usuario:

  | Situación | Mensaje | Tipo |
  | --- | --- | --- |
  | Iniciar reparaciones | Sin mensaje: el cambio a la vista de tarea activa es la confirmación. | — |
  | Reparación finalizada | "Reparación de la habitación [número] finalizada. Pasó a limpieza." | Éxito |
  | Tarea liberada | "Liberaste la reparación de la habitación [número]." | Éxito |
  | Daño reportado (se abre "Por intervenir") | "Daño reportado en la habitación [número]." | Éxito |
  | Bloqueo programado (se abre "Programados") | "Bloqueo programado para la habitación [número] del [inicio] al [fin estimado]." | Éxito |
  | Bloqueo aplicado al registrarse porque empieza hoy (se abre "Por intervenir") | "Bloqueo aplicado en la habitación [número]." | Éxito |
  | Bloqueo cancelado | "Bloqueo de la habitación [número] cancelado." | Éxito |
  | El bloqueo ya no está programado al cancelarlo | "El bloqueo de la habitación [número] ya no está programado (ahora: [Aplicado / Caducado / Cancelado])." El listado se actualiza. | Error |
  | Tarea liberada por la administración | "Tu tarea en la habitación [número] fue liberada por la administración." | Aviso |
  | Rechazo por estado, titularidad o concurrencia | "No se pudo completar: la habitación [número] ahora está en [estado]." El listado se actualiza. | Error |
  | Fallo técnico (la operación no se aplicó) | "No se pudo completar la operación. Inténtalo de nuevo." Nada cambia. | Error |
  | Sesión expirada o rol no autorizado | No se ejecuta la acción; se pide iniciar sesión o se informa "No tienes permiso para esta acción." | Error |

- **FR-011**: Los diálogos DEBEN seguir estas reglas:
  - Toda confirmación tiene dos botones: **"Volver"** (cierra sin cambios) y uno con el nombre de la acción ("Confirmar fin", "Liberar tarea", "Cancelar bloqueo").
  - **Ver informe**: título "Reporte de daño · Habitación [número]" o "Bloqueo técnico · Habitación [número]" y botón "Cerrar".
  - **Reportar daño**: título "Reportar daño · Habitación [número]", campo "Descripción del daño" (ayuda "Máximo 500 caracteres.") y botones "Volver" y "Enviar reporte".
  - **Programar bloqueo**: título "Programar bloqueo técnico · Habitación [número]", campos "Justificación técnica" (ayuda "Máximo 500 caracteres."), "Fecha de inicio" y "Fecha estimada de fin" (ayuda "DD-MM-YYYY") y botones "Volver" y "Programar".
  - Los errores se muestran **dentro del diálogo**, sin perder lo escrito:

    | Regla incumplida | Mensaje |
    | --- | --- |
    | Descripción del daño vacía o solo con espacios | "Describe el daño para poder enviar el reporte." |
    | Justificación técnica vacía o solo con espacios | "Escribe la justificación técnica." |
    | Texto de más de 500 caracteres | "El texto no puede superar los 500 caracteres." |
    | Fecha vacía, con otro formato o inexistente | "Usa una fecha válida en formato DD-MM-YYYY." |
    | Fecha de inicio anterior a hoy | "La fecha de inicio no puede ser anterior a hoy." |
    | Fecha estimada de fin anterior a la de inicio | "La fecha estimada de fin no puede ser anterior a la fecha de inicio." |
    | Más de 90 días de duración | "El bloqueo no puede durar más de 90 días." |
    | Inicio a más de 365 días | "La fecha de inicio no puede estar a más de 365 días de hoy." |
    | Cruce con una reserva | "Hay una reserva en esas fechas. Elige otras fechas o coordina con Reservas." |
    | Cruce con otro mantenimiento | "Ya hay un mantenimiento programado del [inicio] al [fin estimado]." |
    | No se pudo verificar (Módulo 2 sin respuesta o fallo interno) | "No fue posible verificar reservas y mantenimientos. Inténtalo de nuevo." |

- **FR-012**: El buscador DEBE mostrar la ayuda "Buscar habitación por número". La ficha de búsqueda sustituye a la tabla de la pestaña y ninguna pestaña queda marcada; al pulsar una pestaña o vaciar el buscador vuelve el listado. Una habitación sin acciones (`Inactive`) muestra "Sin acciones disponibles".

- **FR-013**: El chip de sesión DEBE ofrecer la acción "Cerrar sesión", que delega en la autenticación común de HOSPITUA: el inicio y el cierre de sesión, y el tiempo tras el cual expira una sesión, quedan fuera del alcance de Módulo 1. Los listados "Por intervenir" y "Programados" se muestran completos, sin paginación; para localizar una habitación se usa el buscador.
- **FR-014**: El panel es el único canal de avisos del área: el sistema NO DEBE enviar notificaciones push, correos ni alertas fuera de él.

---

### Origen de la información

| Elemento | Fuente | Definido en |
| --- | --- | --- |
| Nombre y rol del chip de sesión y del saludo; "Cerrar sesión" | Usuario autenticado (sesión) | Autenticación común de HOSPITUA (FR-013) |
| Hab., Tipo | `Room` | *Registrar habitación* |
| Estado | `Room.status` | Referencia de la máquina de estados |
| Origen (`DisabledForRepairs`) | `DamageReport` más reciente de la habitación (`ReportDateTime`) | *Marcar habitación inhabilitada por reparaciones* |
| Origen (`TechnicalBlock`), Inicio y Fin estimado (Programados) | `TechnicalBlockReport` (`Applied` / `Scheduled`) | *Programar bloqueo técnico para habitación* |
| Responsable (Programados) | `TechnicalBlockReport.MaintenanceStaffMemberId` | *Programar bloqueo técnico para habitación* |
| Habitación con reparación en curso (se excluye de "Por intervenir") | `ReparationTask` abierta | *Confirmar reparación finalizada* |
| Hora de inicio (vista de tarea activa) | `ReparationTask.StartDateTime` | *Confirmar reparación finalizada* |
| Ver informe (vista de tarea activa) | Reporte indicado en `ReparationTask.SourceReportId` | *Confirmar reparación finalizada* |
| Aviso de tarea liberada | `ReparationTask.Outcome = Released` y `ReleasedBy` | *Confirmar reparación finalizada* |
| Contenido de Ver informe | `DamageReport` o `TechnicalBlockReport` | Specs indicados en FR-006 |

El panel no consulta a Módulo 2 ni a Módulo 3: toda la información es local de Módulo 1. La consulta de reservas a Módulo 2 solo ocurre dentro del flujo *Programar bloqueo técnico* al confirmar una programación.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Atributos usados: número, tipo y estado.
- **DamageReport**: Definido en *Marcar habitación inhabilitada por reparaciones*. Atributos usados: descripción, autor, `ReportDateTime`.
- **TechnicalBlockReport**: Definido en *Programar bloqueo técnico para habitación*. Atributos usados: justificación, responsable, `TechnicalBlockStartDate`, `EstimatedTechnicalBlockEndDate`, `Status`, `ReportDateTime`.
- **ReparationTask**: Definida en *Confirmar reparación finalizada*. Atributos usados: `RoomId`, `MaintenanceStaffMemberId`, `SourceReportId`, `StartDateTime`, `Outcome` y `ReleasedBy`.
- **MaintenanceStaff**: Actor Personal de mantenimiento que usa el panel.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El panel se carga con la pestaña "Por intervenir" en menos de 2 segundos en condiciones normales.
- **SC-002**: El 100% de las habitaciones en `DisabledForRepairs` o `TechnicalBlock` sin reparación en curso aparecen en "Por intervenir", y el 100% de los bloqueos `Scheduled` en "Programados".
- **SC-003**: El 100% de las búsquedas por número muestran la habitación con las acciones que corresponden a su estado.
- **SC-004**: El 100% de las acciones llevan a su flujo con la habitación ya seleccionada, sin pedir datos adicionales.
- **SC-005**: Un técnico con tarea activa ve únicamente la vista de tarea activa en el 100% de los accesos.
