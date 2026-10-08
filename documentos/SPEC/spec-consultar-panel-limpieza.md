# Especificación de Funcionalidad: Panel de Limpieza — Vista de Trabajo del Personal de Limpieza

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Personal de limpieza
**Creado**: 2026-10-07

> **Nota**: Esta especificación describe una vista de consulta y despacho, no un caso de uso transaccional. No registra entidades ni transiciona estados de habitaciones por sí misma, y no tiene casos de uso `<<includes>>` ni `<<extends>>`.
>
> **Reparto de responsabilidades (sin doble definición)**: este documento es el único que define la **presentación** del panel: qué habitaciones se listan, columnas, orden, búsqueda, vista de tarea activa, textos de confirmación y mensajes, y origen de cada dato. Las **reglas de negocio** de cada acción (precondiciones, cambios de estado, registros) se definen solo en sus specs funcionales, que remiten a este documento para la vista:
>
> - Iniciar limpieza y Liberar tarea → `spec-marcar-habitacion-en-limpieza.md`
> - Confirmar fin → `spec-confirmar-fin-de-limpieza.md`
> - Reportar daño → `spec-marcar-habitacion-inhabilitada-por-reparaciones.md`

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Listado de habitaciones por atender (Prioridad: P1)

Como miembro del personal de limpieza sin tarea activa, quiero ver al entrar las habitaciones que puedo limpiar, en un orden útil, para elegir cuál iniciar y empezar la limpieza desde el mismo listado.

**Por qué esta prioridad**: Es el punto de entrada del personal de limpieza. Sin el listado no hay forma de iniciar limpiezas ni de reportar daños en habitaciones disponibles.

**Prueba Independiente**: Con habitaciones en `PendingCleaning`, `InCleaning` y `Available`, entrar como Personal de limpieza sin tarea activa y verificar que la tabla muestra solo las `PendingCleaning` y `Available`, con sus columnas y acciones y en el orden definido.

**Escenarios de Aceptación**:

1. **Escenario**: Carga del panel (Happy Path)
   - **Dado** que el miembro del personal de limpieza inicia sesión y no tiene una tarea de limpieza abierta
   - **Cuando** accede al panel
   - **Entonces** el sistema muestra el encabezado, el saludo, el buscador y la tabla de habitaciones en `PendingCleaning` y `Available` con las columnas: Hab. · Tipo · Estado · Última limpieza · Acciones

2. **Escenario**: Orden del listado
   - **Dado** dos habitaciones en `PendingCleaning` ("102" y "101") y dos en `Available` ("104", limpiada hace 5 días, y "105", limpiada hace 1 día)
   - **Cuando** el panel se carga
   - **Entonces** las filas aparecen en el orden 101 · 102 · 104 · 105: primero las pendientes de limpieza por número de habitación, luego las disponibles empezando por la de última limpieza más antigua

3. **Escenario**: Acciones por estado
   - **Dado** el listado cargado
   - **Cuando** el miembro del personal revisa las filas
   - **Entonces** las habitaciones `PendingCleaning` ofrecen "Iniciar limpieza" y las `Available` ofrecen "Iniciar limpieza" y "Reportar daño"

4. **Escenario**: Iniciar limpieza desde el listado
   - **Dado** una habitación en `PendingCleaning` o `Available`
   - **Cuando** el miembro del personal pulsa "Iniciar limpieza"
   - **Entonces** se ejecuta *Marcar habitación en limpieza* sin pedir más datos y, al terminar, el panel muestra la vista de tarea activa (HU-3)

5. **Escenario**: Reportar daño en una habitación disponible
   - **Dado** una habitación en `Available`
   - **Cuando** el miembro del personal pulsa "Reportar daño"
   - **Entonces** se abre el formulario de reporte de *Marcar habitación inhabilitada por reparaciones*; al enviarse, la habitación sale del listado

6. **Escenario**: Una habitación liberada vuelve al listado
   - **Dado** que un compañero liberó su tarea en la habitación "105" (*Marcar habitación en limpieza*, FR-005)
   - **Cuando** el panel se actualiza
   - **Entonces** la habitación "105" aparece como "Pendiente de limpieza" con "Iniciar limpieza"

7. **Escenario**: Actualización sin recargar
   - **Dado** que el panel está abierto
   - **Cuando** otro actor cambia el estado de una habitación (por ejemplo, un check-out deja una habitación en `PendingCleaning` o un compañero inicia una limpieza)
   - **Entonces** la tabla refleja el cambio sin que el usuario recargue la página

---

### Historia de Usuario 2 - Búsqueda por número de habitación (Prioridad: P2)

Como miembro del personal de limpieza, quiero buscar una habitación por su número para atender rápidamente una solicitud puntual.

**Por qué esta prioridad**: Agiliza las solicitudes específicas (por ejemplo, "limpiar la 204") sin recorrer el listado.

**Prueba Independiente**: Buscar el número de una habitación del listado y verificar que la tabla muestra solo esa fila con sus acciones; buscar el número de una habitación en otro estado y verificar el mensaje de vacío.

**Escenarios de Aceptación**:

1. **Escenario**: Búsqueda con coincidencia
   - **Dado** que la habitación "204" está en `PendingCleaning`
   - **Cuando** el miembro del personal escribe "204" en el buscador
   - **Entonces** la tabla muestra únicamente la fila de la habitación "204" con el mismo formato y acciones del listado

2. **Escenario**: Búsqueda de una habitación fuera del panel
   - **Dado** que la habitación "301" está en `Occupied` (o en `InCleaning` a cargo de otro miembro)
   - **Cuando** el miembro del personal busca "301"
   - **Entonces** la tabla muestra "No hay habitaciones que mostrar.", porque la búsqueda solo opera sobre las habitaciones del panel

---

### Historia de Usuario 3 - Vista de tarea activa (Prioridad: P1)

Como miembro del personal de limpieza con una tarea abierta, quiero ver solo la habitación que estoy limpiando y las acciones para cerrarla o dejarla, para no confundirme con otras habitaciones mientras trabajo.

**Por qué esta prioridad**: Un miembro del personal no puede tener más de una tarea activa (*Marcar habitación en limpieza*, FR-008); esta vista es la única forma de cerrar o liberar la tarea.

**Prueba Independiente**: Con una `CleaningTask` abierta para el usuario, entrar al panel y verificar que solo se muestra su habitación con las acciones "Confirmar fin", "Reportar daño" y "Liberar tarea", y que tras cualquiera de ellas vuelve al listado general.

**Escenarios de Aceptación**:

1. **Escenario**: Ingreso con tarea activa
   - **Dado** que el miembro del personal tiene una `CleaningTask` abierta para la habitación "101"
   - **Cuando** accede al panel
   - **Entonces** el sistema muestra únicamente la vista de tarea activa con la habitación "101" (mismo formato del listado más la hora de inicio de su tarea) y las acciones "Confirmar fin", "Reportar daño" y "Liberar tarea"; el listado general y el buscador no se muestran

2. **Escenario**: Confirmar fin
   - **Dado** la vista de tarea activa
   - **Cuando** el miembro del personal pulsa "Confirmar fin" y acepta la confirmación "¿Confirmas que terminaste la limpieza de la habitación [número]?"
   - **Entonces** se ejecuta *Confirmar fin de limpieza de habitación*, el panel vuelve al listado general y muestra "Limpieza de la habitación [número] finalizada."; si elige "Volver", la tarea sigue abierta sin cambios

3. **Escenario**: Reportar daño durante la limpieza
   - **Dado** la vista de tarea activa
   - **Cuando** el miembro del personal pulsa "Reportar daño" y envía el formulario
   - **Entonces** se ejecuta *Marcar habitación inhabilitada por reparaciones* desde `InCleaning` y el panel vuelve al listado general; si cancela el formulario, permanece en la vista de tarea activa

4. **Escenario**: Liberar tarea
   - **Dado** la vista de tarea activa de la habitación "101"
   - **Cuando** el miembro del personal pulsa "Liberar tarea" y acepta la confirmación "¿Deseas liberar la limpieza de la habitación [número]? Volverá a Pendiente de limpieza para que otro miembro pueda iniciarla."
   - **Entonces** se ejecuta la liberación de *Marcar habitación en limpieza* (FR-005) y el panel vuelve al listado general, donde la habitación aparece como "Pendiente de limpieza"

5. **Escenario**: Tarea liberada por el Administrador
   - **Dado** que el Administrador liberó la tarea del usuario mientras tenía la vista abierta
   - **Cuando** intenta confirmar el fin, reportar un daño o liberarla
   - **Entonces** el panel muestra "Tu tarea en la habitación [número] fue liberada por la administración." y vuelve al listado general (*Marcar habitación en limpieza*, FR-009)

---

### Historia de Usuario 4 - Mensajes de resultado y de error (Prioridad: P2)

Como miembro del personal de limpieza, quiero saber siempre si mi acción se completó o por qué no, para no repetirla ni dejar una habitación en un estado que no esperaba.

**Por qué esta prioridad**: Los specs funcionales piden retroalimentación visual y rechazos que informen el estado actual de la habitación; esta historia define dónde y con qué texto se muestran.

**Prueba Independiente**: Ejecutar cada acción con éxito, con la habitación en un estado distinto al esperado y con un fallo técnico simulado, y verificar el mensaje de FR-009 en la zona de mensajes; enviar el formulario de reporte vacío y verificar el error dentro del diálogo.

**Escenarios de Aceptación**:

1. **Escenario**: Mensaje de éxito
   - **Dado** que el miembro del personal confirma el fin, libera su tarea o reporta un daño
   - **Cuando** la operación termina
   - **Entonces** el panel vuelve al listado general y muestra en la zona de mensajes el texto de éxito de esa acción (FR-009)

2. **Escenario**: Rechazo porque la habitación cambió de estado
   - **Dado** que el miembro del personal pulsa "Iniciar limpieza" en la habitación "102", pero un compañero la inició un instante antes
   - **Cuando** el sistema rechaza la acción porque la habitación cambió de estado
   - **Entonces** la zona de mensajes muestra "No se pudo completar: la habitación 102 ahora está en En limpieza." y el listado se actualiza

3. **Escenario**: Fallo técnico
   - **Dado** que ocurre un fallo al guardar una acción
   - **Cuando** la operación falla y el sistema no aplica nada
   - **Entonces** la zona de mensajes muestra "No se pudo completar la operación. Inténtalo de nuevo." y la pantalla queda como estaba

4. **Escenario**: Error en el formulario de reporte
   - **Dado** el formulario de "Reportar daño" abierto
   - **Cuando** el miembro del personal lo envía con la descripción vacía o solo con espacios
   - **Entonces** el diálogo sigue abierto y muestra "Describe el daño para poder enviar el reporte." junto al campo

---

### Casos Borde

- **Sin habitaciones por atender**: La tabla muestra "No hay habitaciones que mostrar.".
- **Búsqueda sin coincidencias**: La tabla muestra "No hay habitaciones que mostrar." sin errores técnicos.
- **Tolerancia de formato en la búsqueda**: El sistema elimina espacios al inicio y al final antes de comparar el número de habitación.
- **Habitación sin limpiezas completadas**: La columna "Última limpieza" muestra "Sin registro".
- **Dos miembros pulsan la misma acción a la vez**: Solo el primero procede; el segundo ve el estado actual y la tabla se actualiza (FR-009).
- **Habitaciones fuera del panel**: Las habitaciones en `InCleaning` (a cargo de su titular), `Reserved`, `Occupied`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive` nunca aparecen en el listado.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE mostrar al Personal de limpieza autenticado y sin tarea activa una pantalla compuesta por: encabezado con el nombre del sistema ("Hotel Hospitua · Limpieza") y chip de sesión (nombre y rol), saludo "Hola, [nombre]", subtítulo "Habitaciones por atender", buscador por número de habitación y tabla de habitaciones.

- **FR-002**: La tabla DEBE listar las habitaciones en `PendingCleaning` y `Available`, con una fila por habitación y las columnas:
  - **Hab.**: número de habitación (`Room.roomNumber`).
  - **Tipo**: Sencilla, Doble, Suite o Boutique (`Room.type`).
  - **Estado**: "Pendiente de limpieza" o "Disponible" (`Room.status`).
  - **Última limpieza**: `EndDateTime` de la última `CleaningTask` de la habitación con `Outcome = Completed`, en formato DD-MM-YYYY HH:MM; "Sin registro" si no existe.
  - **Acciones**: `PendingCleaning` → Iniciar limpieza; `Available` → Iniciar limpieza, Reportar daño.

- **FR-003**: La tabla DEBE ordenarse así: primero las habitaciones en `PendingCleaning`, por número de habitación; después las `Available`, empezando por la de última limpieza más antigua ("Sin registro" primero) y, en caso de empate, por número de habitación.

- **FR-004**: El buscador DEBE filtrar la tabla por número de habitación con coincidencia exacta, operando solo sobre las habitaciones del panel y mostrando el resultado con el mismo formato y acciones.

- **FR-005**: Cada acción DEBE llevar al flujo que la define, sin pedir de nuevo la habitación:
  - **Iniciar limpieza** → *Marcar habitación en limpieza* (FR-001 a FR-003).
  - **Reportar daño** → formulario de *Marcar habitación inhabilitada por reparaciones* (FR-001).

- **FR-006**: Si el usuario autenticado tiene una `CleaningTask` abierta, el sistema DEBE mostrar únicamente la **vista de tarea activa**, con:
  - la habitación de su tarea en el mismo formato de la tabla, más la hora de inicio de su tarea (`CleaningTask.StartDateTime`, HH:MM);
  - el título "Tarea activa";
  - tres acciones: **Confirmar fin** (*Confirmar fin de limpieza de habitación*, FR-001 a FR-006), con la confirmación "¿Confirmas que terminaste la limpieza de la habitación [número]?"; **Reportar daño** (*Marcar habitación inhabilitada por reparaciones*, FR-001 a FR-004) y **Liberar tarea** (*Marcar habitación en limpieza*, FR-005), esta última con la confirmación "¿Deseas liberar la limpieza de la habitación [número]? Volverá a Pendiente de limpieza para que otro miembro pueda iniciarla."

  Tras completar cualquiera de las tres acciones, el sistema vuelve al listado general. Si el Administrador liberó la tarea, muestra "Tu tarea en la habitación [número] fue liberada por la administración." y vuelve al listado general.

- **FR-007**: La tabla y la vista de tarea activa DEBEN reflejar los cambios de estado de las habitaciones y de las tareas en menos de 2 segundos, sin recargar la página. Esto incluye los cambios hechos por otros actores: check-out, confirmación de reparaciones, compañeros de limpieza y liberaciones del Administrador.

- **FR-008**: Cuando la tabla no tiene filas que mostrar (por defecto o por la búsqueda), el sistema DEBE mostrar "No hay habitaciones que mostrar." sin errores técnicos.

- **FR-009**: El panel DEBE tener una **zona de mensajes** bajo el saludo (en la vista de tarea activa, bajo su título) que muestra el resultado de la última acción hasta la siguiente acción del usuario:

  | Situación | Mensaje | Tipo |
  | --- | --- | --- |
  | Iniciar limpieza | Sin mensaje: el cambio a la vista de tarea activa es la confirmación. | — |
  | Fin de limpieza confirmado | "Limpieza de la habitación [número] finalizada." | Éxito |
  | Tarea liberada | "Liberaste la limpieza de la habitación [número]." | Éxito |
  | Daño reportado | "Daño reportado en la habitación [número]." | Éxito |
  | Tarea liberada por la administración | "Tu tarea en la habitación [número] fue liberada por la administración." | Aviso |
  | Rechazo por estado, titularidad o concurrencia | "No se pudo completar: la habitación [número] ahora está en [estado]." El listado se actualiza. | Error |
  | Fallo técnico (la operación no se aplicó) | "No se pudo completar la operación. Inténtalo de nuevo." Nada cambia. | Error |
  | Sesión expirada o rol no autorizado | No se ejecuta la acción; se pide iniciar sesión o se informa "No tienes permiso para esta acción." | Error |

- **FR-010**: Los diálogos DEBEN seguir estas reglas:
  - Toda confirmación tiene dos botones: **"Volver"** (cierra sin cambios) y uno con el nombre de la acción ("Confirmar fin", "Liberar tarea").
  - El formulario de reporte tiene el título "Reportar daño · Habitación [número]", el campo "Descripción del daño" con la ayuda "Máximo 500 caracteres." y los botones "Volver" y "Enviar reporte".
  - Los errores de validación se muestran **dentro del diálogo**, junto al campo, sin perder lo escrito: descripción vacía o solo con espacios → "Describe el daño para poder enviar el reporte."; más de 500 caracteres → "El texto no puede superar los 500 caracteres." (mismos textos que el formulario de reporte del panel de mantenimiento).

- **FR-011**: El buscador DEBE mostrar la ayuda "Buscar por número de habitación"; al vaciarlo, vuelve el listado completo.

- **FR-012**: El chip de sesión DEBE ofrecer la acción "Cerrar sesión", que delega en la autenticación común de HOSPITUA: el inicio y el cierre de sesión, y el tiempo tras el cual expira una sesión, quedan fuera del alcance de Módulo 1. El listado se muestra completo, sin paginación; para localizar una habitación se usa el buscador.
- **FR-013**: El panel es el único canal de avisos del área: el sistema NO DEBE enviar notificaciones push, correos ni alertas fuera de él.

---

### Origen de la información

| Elemento | Fuente | Definido en |
| --- | --- | --- |
| Nombre y rol del chip de sesión y del saludo; "Cerrar sesión" | Usuario autenticado (sesión) | Autenticación común de HOSPITUA (FR-012) |
| Hab., Tipo | `Room` | *Registrar habitación* |
| Estado | `Room.status` | Referencia de la máquina de estados |
| Última limpieza | `CleaningTask` (última con `Outcome = Completed`) | *Marcar habitación en limpieza* |
| Hora de inicio (vista de tarea activa) | `CleaningTask.StartDateTime` | *Marcar habitación en limpieza* |
| Aviso de tarea liberada | `CleaningTask.Outcome = Released` y `ReleasedBy` | *Marcar habitación en limpieza* |

El panel no consulta a Módulo 2 ni a Módulo 3: toda la información es local de Módulo 1.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional. Atributos usados: número, tipo y estado.
- **CleaningTask**: Tarea de limpieza definida en *Marcar habitación en limpieza*. Atributos usados: `RoomId`, `CleaningStaffMemberId`, `StartDateTime`, `EndDateTime`, `Outcome` y `ReleasedBy`.
- **CleaningStaff**: Actor Personal de limpieza que usa el panel.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El panel se carga con la tabla en menos de 2 segundos en condiciones normales.
- **SC-002**: El 100% de las habitaciones en `PendingCleaning` y `Available` aparecen en el panel, y ninguna en otro estado.
- **SC-003**: El orden de la tabla respeta FR-003 en el 100% de las cargas.
- **SC-004**: El 100% de las acciones llevan a su flujo con la habitación ya seleccionada, sin pedir datos adicionales.
- **SC-005**: Un usuario con tarea activa ve únicamente la vista de tarea activa en el 100% de los accesos.
