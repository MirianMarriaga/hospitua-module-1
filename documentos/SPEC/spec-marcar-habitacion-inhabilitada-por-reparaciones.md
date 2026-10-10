# Especificación del Caso de Uso: Marcar Habitación Inhabilitada por Reparaciones

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reporte de daño en habitación (Prioridad: P1)

Como miembro del personal de limpieza o mantenimiento quiero poder reportar daños físicos o estructurales en una habitación, para así poder formalizar la labor de reporte de anomalías sobre unidades habitacionales, agilizando la intervención del personal calificado en la resolución de los conflictos descritos en el informe generado por el reporte.

**Por qué esta prioridad**: Es una funcionalidad crítica para mantener los estándares de calidad del hotel. Las habitaciones con daños deben ser reportadas y retiradas temporalmente de la operación (no pueden asignarse para el día actual ni recibir huéspedes) para evitar asignaciones, mitigar malas experiencias con los huéspedes y prevenir el deterioro prolongado de la infraestructura.

**Prueba independiente**: Puede ser probada autenticándose en el sistema como miembro del personal de limpieza o mantenimiento, seleccionando una habitación con estado `Available`, diligenciando y enviando el formulario de reporte con una descripción textual válida del daño y verificando que el sistema transiciona la habitación a `DisabledForRepairs`, persiste un nuevo registro transaccional en la entidad de reportes vinculado al usuario en sesión, la habitación junto con la fecha y hora del reporte, la retira del panel de limpieza y verificando su aparición en el panel de mantenimiento (definido en el caso de uso *Confirmar Fin de Reparación de Habitación*) con el estado `DisabledForRepairs`.

**Escenarios de aceptación**:

1. **Escenario**: Reporte exitoso de daño estructural o físico
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado y existe una habitación "101" cuyo estado actual es `Available`
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) diligencia y envía el reporte adjuntando la descripción textual detallada del daño
   - **Entonces** El sistema transiciona el estado de la habitación a `DisabledForRepairs`, genera un registro histórico con la justificación ingresada, vinculándolo con el autor del reporte junto con la fecha y hora proporcionadas por el servidor.

2. **Escenario**: Intento de reporte fallido por omisión de justificación (Fallo de validación)
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado e intenta reportar un daño en la habitación "101"
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) intenta enviar la confirmación del reporte dejando nulo o vacío el campo correspondiente a la descripción del daño
   - **Entonces** El sistema evita la confirmación de realización del reporte, mantiene el estado `Available` de la habitación y expone un error de validación indicando que la descripción del daño es un campo estrictamente obligatorio.

3. **Escenario**: Intento de reporte sobre unidad habitacional con estado incompatible
   - **Dado que** El miembro del personal (de limpieza o mantenimiento) está autenticado y existe una habitación "105" en estado `Occupied` (o cualquier estado distinto a `Available`)
   - **Cuando** El miembro del personal (de limpieza o mantenimiento) intenta reportar un daño en dicha habitación
   - **Entonces** El sistema evita la realización del reporte, bloquea la alteración del estado y retorna retroalimentación visual indicando el estado de la habitación y que la acción no está permitida sobre dicha habitación, y actualiza la vista para dicho usuario.

---

### Casos Límite

1. **¿Qué ocurre si dos actores intentan inhabilitar la misma habitación concurrentemente?**
   El sistema aplicará un mecanismo de control de concurrencia (ej. bloqueo optimista). La primera transacción en llegar realizará el cambio a `DisabledForRepairs`. La segunda fallará al validar el estado previo, retornando una alerta al usuario indicando un mensaje de error y el estado actual de la habitación.

2. **¿Qué ocurre si se excede la longitud del campo de justificación?**
   El sistema debe ejecutar una validación de longitud máxima de 500 caracteres, bloqueando la opción de confirmación del reporte y entregando retroalimentación visual de la causa del bloqueo.

3. **¿Qué ocurre si se detecta un daño durante una limpieza activa (`InCleaning`)?**
   El sistema no permitirá la inhabilitación directa desde `InCleaning`. El miembro del personal de limpieza vinculado a la tarea reporta el daño diligenciando la descripción opcional al confirmar el fin de limpieza (caso de uso *Confirmar Fin de Limpieza de Habitación*, FR-012): ese flujo transiciona la habitación a `Available` e invoca este caso de uso en la misma transacción. Fuera de ese flujo, el reporte de daños solo se puede realizar sobre habitaciones en estado `Available`.

4. **¿Qué ocurre si el cliente envía una fecha y hora de reporte propia o manipulada?**
   El sistema ignora cualquier fecha enviada por el cliente y utiliza exclusivamente el timestamp del servidor, evitando reportes con fechas sin sentido.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE proveer al personal autenticado (actores: limpieza, mantenimiento) la interfaz de captura de datos para el reporte *(despliegue de un formulario modal superpuesto que exija un input de texto plano para la descripción del daño)*.
- **FR-002**: El sistema DEBE validar de manera estricta que la ejecución de la inhabilitación proceda única y exclusivamente cuando la habitación objetivo se encuentre en estado `Available`; en caso contrario, arrojará un error *(ofreciendo retroalimentación visual a través de un modal superpuesto indicando el estado de la habitación y la causa del error, en este caso, una operación no válida)*.
- **FR-003**: El sistema DEBE exigir la presencia de la descripción del daño, evaluando que la cadena de texto no sea nula, vacía, ni exceda el límite de caracteres preestablecido *(validación ejecutada previamente al envío del reporte)*.
- **FR-004**: El sistema DEBE transicionar el estado de la entidad habitación a `DisabledForRepairs` al procesar el reporte *(confirmación visual de éxito de la acción realizada)*. Desde ese momento la habitación deja de listarse en el panel de limpieza, figura como inhabilitada en el panel de mantenimiento y no puede asignarse para el día actual (*Consultar Información de Mantenimientos* FR-014).
- **FR-005**: El sistema DEBE generar el registro de auditoría y trazabilidad extrayendo los metadatos de forma autónoma *(información de la sesión, habitación, fecha y hora serán proporcionadas por la sesión)* sin depender de inserciones manuales del cliente para ser asociadas al reporte de manera automática.
- **FR-006**: El sistema DEBE garantizar la integridad referencial y de estado frente a peticiones simultáneas, rechazando solicitudes sobre habitaciones que hayan abandonado el estado `Available` milisegundos antes de procesar la petición actual.
- **FR-007**: El sistema DEBE ofrecer, mientras la habitación esté en `DisabledForRepairs`, la acción **Ver informe**, que muestra el `DamageReport` que originó ese estado.
  - **FR-007.1**: El recurso **Ver informe** DEBE mostrar la descripción del daño, el autor del reporte y la fecha y hora del reporte en formato DD-MM-YYYY HH:MM.
  - **FR-007.2**: El sistema DEBE calcular y autorizar las acciones operativas disponibles sobre las habitaciones inhabilitadas (`DisabledForRepairs`) aplicando las siguientes políticas de control de acceso según la sesión activa:
    - **Para el rol `Personal de mantenimiento`**: el sistema expondrá en el panel de mantenimiento la acción de consulta (**Ver informe**) y la acción **Iniciar reparaciones**, ambas definidas en el caso de uso *Confirmar Fin de Reparación de Habitación*.
    - **Para el rol `Personal de limpieza`**: el sistema no expondrá acciones sobre habitaciones en `DisabledForRepairs`, ya que estas no se listan en el panel de limpieza.
- **FR-008**: El sistema DEBE generar `ReportDateTime` exclusivamente en el servidor, sin aceptar fechas u horas enviadas por el cliente.
- **FR-009**: El sistema DEBE asignar `ReportDateTime` en el mismo momento en que se procesa la transición a `DisabledForRepairs`, de modo que coincida con el inicio del periodo registrado en `RoomStateHistory`.
- **FR-010**: El sistema DEBE impedir la modificación o eliminación de un `DamageReport` una vez creado.
- **FR-011**: El sistema DEBE registrar la transición en `RoomStateHistory` dentro de la misma transacción: cerrar el periodo abierto de la habitación y abrir uno nuevo con `Status` = `DisabledForRepairs`, `PreviousStatus` = `Available`, `StartDateTime` = `ReportDateTime`, `ActorId` = autor del reporte y `SourceFlow` = *Marcar Habitación Inhabilitada por Reparaciones* (o *Confirmar Fin de Limpieza de Habitación* cuando se invoca desde ese flujo). El `DamageReport` complementa ese registro y no lo reemplaza.
- **FR-012**: El sistema DEBE ejecutar la inhabilitación (transición, `DamageReport` y registro en `RoomStateHistory`) dentro de una única transacción; si falla la persistencia o la conexión durante el procesamiento, el sistema DEBE ejecutar rollback completo, mantener la habitación en su estado previo (`Available`), conservar la descripción diligenciada en el formulario, no crear ni modificar registros y mostrar retroalimentación visual del error con la sugerencia de reintentar la operación. Cuando se invoca desde *Confirmar Fin de Limpieza de Habitación*, el rollback DEBE abarcar también la transacción de ese flujo.

### Entidades Clave

- **Room**: Entidad principal que representa la unidad habitacional del hotel. Su máquina de estados para este caso de uso transita estrictamente de `Available` a `DisabledForRepairs`.
- **DamageReport**: Entidad diseñada para el registro histórico (log) de anomalías, esta almacena:
  - **DamageDescription**: Cadena de texto descriptiva del daño reportado
  - **UserId**: Identificador del usuario (de limpieza o mantenimiento) vinculado al reporte
  - **RoomId**: Identificador de la habitación vinculada al reporte
  - **ReportDateTime**: TimeStamp de realización del reporte - Formato DD-MM-YYYY HH:MM
- **RoomStateHistory**: Historial común de estados de la habitación. En este caso de uso se abre el periodo `DisabledForRepairs`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El miembro del personal puede ejecutar el flujo de inhabilitación, incluyendo la redacción de la justificación, en un tiempo máximo de 45 segundos.
- **SC-002**: El 100% de las inhabilitaciones transicionan el estado de la habitación y simultáneamente generan el registro íntegro `DamageReport` dentro de la misma transacción de base de datos.
- **SC-003**: La habitación afectada deja de listarse en el panel de limpieza y aparece como inhabilitada en el panel de mantenimiento en un máximo de 3 segundos.
- **SC-004**: El 100% de las inhabilitaciones quedan registradas en `RoomStateHistory` con el mismo timestamp de servidor que `ReportDateTime`.
