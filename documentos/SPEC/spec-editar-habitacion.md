# Especificación de Funcionalidad: Editar Habitación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Edición exitosa de una habitación disponible (Prioridad: P1)

Como Administrador, quiero poder actualizar los atributos de una habitación existente para mantener la información del inventario alineada con la realidad operativa del hotel y corregir errores que puedan afectar la experiencia de trabajadores y huéspedes.

**Por qué esta prioridad**: La edición de atributos base impacta directamente la lógica comercial (tarifas) y operativa (capacidad) del hotel. Debe ejecutarse solo cuando la habitación no tenga ocupación activa para evitar inconsistencias en reservas en curso.

**Prueba Independiente**: Puede probarse tomando una habitación en estado "Available", editando uno o más atributos, y verificando que los cambios se persisten correctamente en el inventario.

**Escenarios de Aceptación**:

1. **Escenario**: Edición de tarifa base en habitación disponible
   - **Dado** existe una habitación con estado "Available" 
   - **Cuando** el Administrador modifica su tarifa base y confirma los cambios
   - **Entonces** el sistema persiste la nueva tarifa base y confirma la actualización exitosa

2. **Escenario**: Edición de tipo de habitación en habitación disponible
   - **Dado** existe una habitación con estado "Available" y tipo "Sencilla"
   - **Cuando** el Administrador cambia el tipo a "Doble" y confirma
   - **Entonces** el sistema actualiza el tipo y mantiene todos los demás atributos sin cambios

3. **Escenario**: Edición de capacidad máxima en habitación disponible
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador actualiza la capacidad máxima a un valor válido y confirma
   - **Entonces** el sistema persiste la nueva capacidad y la habitación permanece en estado "Available"

4. **Escenario**: Edición del número de habitación
   - **Dado** existe una habitación "101" en estado "Available" y no existe ninguna otra habitación con el número "110"
   - **Cuando** el Administrador cambia el número de la habitación a "110" y confirma
   - **Entonces** el sistema actualiza el número a "110", conserva el mismo ID único (UUID) y los demás atributos, y la habitación aparece en el inventario con su nuevo número

5. **Escenario**: Confirmación con los cambios realizados
   - **Dado** el Administrador cambió la tarifa base de la habitación "101" de $180.000 a $195.000
   - **Cuando** guarda los cambios
   - **Entonces** el sistema muestra una confirmación con cada dato modificado, su valor anterior y su valor nuevo, junto con los datos actualizados de la habitación

6. **Escenario**: Guardar sin haber modificado ningún dato
   - **Dado** el Administrador abrió la edición de una habitación "Available"
   - **Cuando** intenta guardar sin haber cambiado ningún valor
   - **Entonces** el sistema informa que no se modificó ningún dato y no registra ninguna edición

---

### Historia de Usuario 2 - Intento de edición sobre habitación no disponible (Prioridad: P1)

Como Administrador, quiero ser informado cuando una habitación no puede editarse por estar comprometida, para evitar modificaciones que generen inconsistencias en la operación del hotel.

**Por qué esta prioridad**: Editar una habitación en cualquier estado que no sea "Available" podría generar inconsistencias críticas en reservas activas, en procesos de limpieza o en intervenciones de mantenimiento. Esta restricción es tan prioritaria como la edición misma.

**Prueba Independiente**: Puede probarse intentando editar habitaciones en cada estado distinto de "Available" y verificando que el sistema bloquea la operación en todos los casos.

**Escenarios de Aceptación**:

7. **Escenario**: Intento de edición sobre habitación ocupada o reservada
   - **Dado** existe una habitación con estado "Occupied" o "Reserved"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición, indicando su estado actual

8. **Escenario**: Intento de edición sobre habitación en limpieza
   - **Dado** existe una habitación con estado "PendingCleaning" o "InCleaning"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición

9. **Escenario**: Intento de edición sobre habitación en mantenimiento o inhabilitada
   - **Dado** existe una habitación con estado "DisabledForRepairs", "TechnicalBlock" o "Inactive"
   - **Cuando** el Administrador intenta editar sus atributos
   - **Entonces** el sistema rechaza la operación e informa que la habitación no está disponible para edición

---

### Historia de Usuario 3 - Edición con datos inválidos (Prioridad: P2)

Como Administrador, quiero recibir retroalimentación clara cuando ingreso valores inválidos durante la edición para corregirlos sin alterar accidentalmente los datos originales de la habitación.

**Por qué esta prioridad**: La validación de datos protege la integridad del inventario. Es secundaria respecto a la edición exitosa pero necesaria antes de la entrega.

**Prueba Independiente**: Puede probarse enviando actualizaciones con valores fuera de rango y verificando que los datos originales de la habitación permanecen sin cambios.

**Escenarios de Aceptación**:

10. **Escenario**: Intento de cambio de tipo a valor no permitido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador intenta asignar un tipo de habitación que no existe en el catálogo
   - **Entonces** el sistema rechaza el cambio e indica los tipos válidos permitidos

11. **Escenario**: Intento de actualizar tarifa base a valor inválido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador ingresa una tarifa base igual a cero o negativa
   - **Entonces** el sistema rechaza la actualización y la tarifa base original no se modifica

12. **Escenario**: Intento de actualizar capacidad máxima a valor inválido
   - **Dado** existe una habitación con estado "Available"
   - **Cuando** el Administrador ingresa una capacidad máxima menor o igual a cero
   - **Entonces** el sistema rechaza la actualización y la capacidad original no se modifica

13. **Escenario**: Intento de cambiar el número por uno ya existente
   - **Dado** existe una habitación "101" en estado "Available" y otra habitación "204" en estado "Inactive"
   - **Cuando** el Administrador intenta cambiar el número de la habitación "101" a "204"
   - **Entonces** el sistema rechaza el cambio indicando que el número 204 ya existe en el inventario (aunque esa habitación esté inactiva) y el número original no se modifica

---

### Casos Borde

- **Número de habitación sin cambios**: si el Administrador guarda la edición conservando el mismo número, el sistema no lo considera duplicado con la propia habitación.
- **Número de habitación vacío**: el número es obligatorio; si se deja vacío, el sistema rechaza la edición y conserva el número original.
- **Habitación inexistente**: cuando se busca una habitación que no está registrada, el sistema informa que no se encontró ninguna habitación con ese identificador.
- **Sin cambios**: si el Administrador guarda sin modificar ningún valor, el sistema no persiste nada ni registra auditoría, e informa que no hubo cambios (FR-010).
- **Ediciones simultáneas**: si dos usuarios intentan editar la misma habitación al mismo tiempo, la primera edición se guarda y la segunda recibe un aviso de que la información ya fue actualizada por otro usuario.
- **Edición de piso**: el piso puede modificarse siempre que el nuevo valor sea válido. La unicidad aplica solo al número de habitación, por lo que cambiar únicamente el piso nunca genera un conflicto de duplicidad.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al Administrador editar los atributos de una habitación existente: número de habitación, piso, tipo, capacidad máxima y tarifa base. El ID único (UUID) no es editable.
- **FR-002**: El sistema DEBE verificar que la habitación se encuentre en estado "Available"  antes de permitir cualquier edición.
- **FR-003**: El sistema DEBE rechazar la edición si la habitación se encuentra en cualquier estado distinto de "Available" ("Reserved", "Occupied", "PendingCleaning", "InCleaning", "DisabledForRepairs", "TechnicalBlock" o "Inactive"), informando el estado actual.
- **FR-004**: El sistema DEBE informar al Administrador el estado actual de la habitación cuando la edición sea rechazada por disponibilidad.
- **FR-005**: El sistema DEBE validar que los nuevos valores cumplan las mismas reglas de negocio que el registro inicial (piso entero > 0, tipo válido, capacidad > 0, tarifa > 0).
- **FR-006**: El sistema DEBE persistir únicamente los campos que el Administrador modificó, sin alterar los demás atributos.
- **FR-007**: El sistema DEBE confirmar al Administrador la actualización exitosa mostrando cada dato modificado con su valor anterior y su valor nuevo, junto con los datos actualizados de la habitación. El usuario y la fecha de la edición se guardan (FR-008), pero no se muestran en la confirmación.
- **FR-008**: El sistema DEBE registrar en la bitácora de auditoría cada edición exitosa, incluyendo el ID de la habitación, cada campo modificado con su valor anterior y su valor nuevo, el Administrador responsable y la fecha/hora de la modificación.
- **FR-009**: El sistema DEBE validar que el nuevo número de habitación sea único en todo el sistema, incluidas las habitaciones activas e inactivas, excluyendo a la propia habitación que se edita, y rechazar el cambio si ya existe otra habitación con ese número.
- **FR-010**: Si el Administrador intenta guardar sin haber modificado ningún dato, el sistema DEBE informarlo y NO DEBE persistir cambios ni registrar auditoría.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional existente en el inventario. Los atributos editables son: número de habitación (único en todo el inventario), piso, tipo (Sencilla | Doble | Suite | Boutique), capacidad máxima y tarifa base. El ID único (UUID) no cambia. Solo puede editarse cuando se encuentra en estado "Available" .
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su edición.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede completar la edición de una habitación disponible en menos de 2 minutos.
- **SC-002**: El 100% de los intentos de edición sobre habitaciones en estado distinto de "Available" son rechazados antes de persistir.
- **SC-003**: El 100% de los intentos de edición con datos inválidos son rechazados sin modificar los datos originales de la habitación.
- **SC-004**: Los cambios exitosos se reflejan en el inventario de forma inmediata tras la confirmación.
- **SC-005**: El Administrador recibe siempre retroalimentación clara (éxito o motivo de rechazo) tras cada intento de edición.
- **SC-006**: El 100% de los intentos de cambiar el número de habitación por uno ya existente (en habitaciones activas o inactivas) son rechazados sin modificar el número original.
