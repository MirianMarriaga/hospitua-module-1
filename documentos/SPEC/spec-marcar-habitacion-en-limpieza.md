# Especificación del Caso de Uso: Marcar Habitación en Limpieza

**Fecha de creación**: 20/09/2026

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Marcar habitación en limpieza (Prioridad: P1)

Como Personal de limpieza quiero poder indicar que voy a limpiar una habitación, ya sea porque acaba de ser liberada por un huésped o porque lleva tiempo sin uso, para que el sistema registre que voy a realizar labores en ella y pueda registrarse formalmente en el sistema .

**Por qué esta prioridad**: Es la acción principal del personal de limpieza. Sin ella, las habitaciones no avanzan en su ciclo de vida y quedan estancadas, reduciendo la disponibilidad del hotel.

**Prueba independiente**: Puede ser probada indicando que se va a limpiar una habitación y verificando que el sistema la marca como en proceso de limpieza, asigna la tarea al miembro que lo solicitó previamente y la excluye de la lista de habitaciones disponibles al público.

**Escenarios de aceptación**:

1. **Escenario**: Inicio de limpieza post-check-out
   - **Dado que** el Personal de limpieza está autenticado y existe una habitación "101" que acaba de ser liberada pero aparece como pendiente de limpieza
   - **Cuando** el Personal de limpieza indica que va a limpiar la habitación
   - **Entonces** el sistema registra que la habitación está en proceso de limpieza, por quién y desde cuándo

2. **Escenario**: Limpieza preventiva
   - **Dado que** el Personal de limpieza está autenticado y existe una habitación "101" que está disponible pero aparece como pendiente de limpieza
   - **Cuando** el Personal de limpieza indica que va a iniciar labores en la habitación
   - **Entonces** el sistema registra que la habitación está en proceso de limpieza, por quién y desde cuándo

3. **Escenario**: Habitación ocupada por un huésped
   - **Dado que** el Personal de limpieza está autenticado y existe una habitación "101" que está ocupada
   - **Cuando** el Personal de limpieza intenta indicar que va a limpiar la habitación
   - **Entonces** el sistema rechaza la operación y muestra un error con el estado actual de la habitación

---

### Casos Límite

1. **¿Qué ocurre si dos miembros del personal intentan limpiar la misma habitación?**
   El sistema debe asignar la habitación al primero que la marque. El segundo recibe un error indicando que la habitación ya está en proceso de limpieza.

2. **¿Qué ocurre si se marca una habitación en limpieza y el personal no se autentica?**
   El sistema debe rechazar la operación y solicitar autenticación.

3. **¿Qué ocurre si la habitación fue liberada hace mucho tiempo y nadie la ha limpiado?**
   El sistema debe permitir la limpieza sin ninguna restricción de tiempo. La verificación de tiempos no es responsabilidad de este caso de uso.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al Personal de limpieza autenticado marcar habitaciones en limpieza y recibir verificación visual de la acción que está realizando.
- **FR-002**: El sistema DEBE aceptar la marcación únicamente desde habitaciones que se encuentren en los estados {`InCleaning`,`Available}`.
- **FR-003**: El sistema DEBE cambiar el estado de la habitación a `InCleaning` al procesar la marcación.
- **FR-004**: El sistema DEBE registrar los datos del personal que solicita la tarea y la fecha/hora de inicio.
- **FR-005**: El sistema DEBE rechazar la marcación si la habitación se encuentra en cualquier estado distinto a `Available`.
- **FR-006**: El sistema DEBE evitar que dos usuarios marquen el inicio de labores en la misma habitación de forma simultánea.
- **FR-007**: El sistema DEBE permitir al personal de limpieza filtrar las habitaciones por `InCleaning` para agilizar la labor de búsqueda sobre habitaciones que requieran atención.
- **FR-008**: El sistema DEBE limitar las acciones de los miembros del personal de limpieza con tareas activas a la marcación de finalización de labores en su unidad habitacional asignada.

### Entidades Clave

- **Room**: Entidad que representa una habitación del hotel. En este caso de uso transita de `PendingCleaning` o `Available` a `InCleaning`.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Personal de limpieza puede marcar una habitación en limpieza en menos de 15 segundos.
- **SC-002**: El 100% de las marcaciones registran correctamente el personal y la fecha/hora.
- **SC-003**: El cambio de estado se refleja en el inventario en menos de 2 segundos.
