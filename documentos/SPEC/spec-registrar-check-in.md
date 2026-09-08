# Especificación de Funcionalidad: Registrar Check-in

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registro de check-in y actualización a ocupada (Prioridad: P1)

Como Módulo 2, quiero notificar el check-in de una estancia asignando una habitación disponible, para que el sistema actualice el estado de la habitación a "Occupied" y quede excluida del inventario vendible actual.

**Por qué esta prioridad**: El check-in formaliza la ocupación de una unidad física en el inventario del Módulo 1. Sin esta operación, no existe constancia de habitaciones ocupadas en sitio, lo que generaría riesgo de sobreventa y bloquearía los procesos de consumos y facturación del hotel.

**Prueba Independiente**: Puede probarse de forma independiente enviando una petición de check-in desde el Módulo 2 con el identificador de una habitación en estado `Available`, verificando que el sistema transicione su estado a `Occupied` y actualice el inventario en tiempo real.

**Escenarios de Aceptación**:

1. **Escenario**: Check-in exitoso con habitación disponible
* **Dado** existe una habitación registrada en estado operativo `Available`
* **Cuando** el Módulo 2 envía la solicitud de check-in con el identificador de la habitación y los datos de la reserva confirmada
* **Entonces** el sistema actualiza de forma atómica el estado de la habitación a `Occupied`, confirma la operación al Módulo 2 y excluye la unidad de futuras asignaciones


2. **Escenario**: Reflejo inmediato de habitación ocupada en el inventario
* **Dado** se ha completado exitosamente un check-in solicitado por el Módulo 2
* **Cuando** se consulta el inventario general de habitaciones
* **Entonces** la habitación refleja el estado `Occupied` y ya no figura como disponible para nuevas reservas



---

### Historia de Usuario 2 - Rechazo de check-in sobre habitación no disponible (Prioridad: P1)

Como Módulo 2, quiero recibir una respuesta de rechazo explícita cuando se intente registrar un check-in sobre una habitación que no esté disponible, para evitar asignaciones inconsistentes y proteger la integridad del inventario físico.

**Por qué esta prioridad**: Asignar una habitación que no esté en estado `Available` provocaría conflictos operativos críticos (doble ocupación, ingreso a habitaciones sucias o asignación de unidades con fallas técnicas). Esta validación asegura la consistencia de la máquina de estados.

**Prueba Independiente**: Puede probarse enviando peticiones de check-in desde el Módulo 2 hacia habitaciones en cada uno de los estados distintos de `Available`, comprobando que el sistema rechace la solicitud en todos los casos sin alterar los datos.

**Escenarios de Aceptación**:

3. **Escenario**: Intento de check-in en habitación ocupada
* **Dado** una habitación que se encuentra en estado `Occupied`
* **Cuando** el Módulo 2 intenta registrar un check-in para dicha habitación
* **Entonces** el sistema rechaza la operación e informa que la habitación ya se encuentra ocupada


4. **Escenario**: Intento de check-in en habitación en proceso de limpieza
* **Dado** una habitación que se encuentra en estado `PendingCleaning` o `InCleaning`
* **Cuando** el Módulo 2 intenta registrar un check-in para dicha habitación
* **Entonces** el sistema rechaza la operación e informa que la unidad se encuentra en ciclo de limpieza y no está habilitada


5. **Escenario**: Intento de check-in en habitación inhabilitada o con bloqueo técnico
* **Dado** una habitación que se encuentra en estado `TechnicalBlock` o `DisabledForRepairs`
* **Cuando** el Módulo 2 solicita el registro de check-in
* **Entonces** el sistema rechaza la operación e informa que la habitación se encuentra bajo intervención técnica


6. **Escenario**: Intento de check-in en habitación inactiva
* **Dado** una habitación que se encuentra en estado `Inactive`
* **Cuando** el Módulo 2 envía una solicitud de check-in
* **Entonces** el sistema rechaza la operación e informa que la habitación fue dada de baja del inventario operativo



---

### Casos Borde

* Solicitud de check-in sin identificador de habitación: el sistema rechaza la petición exigiendo el UUID de la habitación para proceder.
* Peticiones simultáneas de check-in desde el Módulo 2 para la misma habitación: el primer intento procesado exitosamente cambia el estado a `Occupied` y el segundo es rechazado de inmediato por colisión de concurrencia.
* Fallo de comunicación o caída del sistema durante la confirmación: la transacción se cancela atómicamente y la habitación permanece en estado `Available`.
* Solicitud de check-in con identificador de habitación inexistente: el sistema rechaza la petición informando que el recurso no existe en el catálogo.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

* **FR-001**: Precondición: la habitación debe estar en el estado `Available`.
* **FR-002**: Transición de estado: El sistema DEBE actualizar de forma atómica el estado de la entidad `Room` de `Available` a `Occupied` tras procesar la confirmación de check-in.
* **FR-003**: El sistema DEBE permitir la invocación de esta operación únicamente al actor "Módulo 2".
* **FR-004**: El sistema DEBE validar que la habitación se encuentre estrictamente en estado `Available` antes de autorizar el cambio de estado.
* **FR-005**: El sistema DEBE rechazar la solicitud de check-in si la habitación se encuentra en cualquiera de los otros estados operativos (`Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock`, `Inactive`).
* **FR-006**: El sistema DEBE excluir la habitación de los resultados de disponibilidad comercial de forma inmediata al pasar a `Occupied`.
* **FR-007**: El sistema DEBE registrar en la bitácora de auditoría el ID de la habitación, el identificador de la reserva/estancia provisto por el Módulo 2 y la marca de tiempo (timestamp) de la transacción.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

* **Room**: Unidad habitacional física en el Módulo 1. Su atributo `status` transiciona de `Available` a `Occupied`. Atributos involucrados: ID único (UUID), número de habitación, tarifa base y estado operativo actual.
* **Module2**: Módulo externo del sistema responsable de gestionar las reservas y la asignación de huéspedes, el cual actúa como disparador de la operación de check-in.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

* **SC-001**: El 100% de las solicitudes válidas de check-in enviadas por el Módulo 2 transicionan la habitación a `Occupied` en menos de 1 segundo.
* **SC-002**: El sistema rechaza el 100% de los intentos de check-in sobre habitaciones que no se encuentren en estado `Available`.
* **SC-003**: Cero sobreventas o asignaciones dobles permitidas sobre la misma habitación ante peticiones concurrentes.
* **SC-004**: El 100% de las transiciones a `Occupied` quedan persistidas con su marca de tiempo y referencia de reserva en la bitácora de auditoría.
