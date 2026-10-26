# Especificación de Funcionalidad: Consultar Tarifa Base

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Módulo 3 — Facturación (sistema externo)
**Creado**: 2026-09-07
**Actualizado**: 2026-10-26

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de tarifa base de una habitación (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero consultar la tarifa base de una habitación específica mediante su identificador (`roomId`), para utilizarla como base en el cálculo de cargos, estancias y facturación del huésped.

**Por qué esta prioridad**: La tarifa base configurada en cada unidad habitacional es el dato comercial fundamental que alimenta la liquidación financiera de cada estancia. Sin acceso confiable a este dato por habitación, el Módulo 3 no puede calcular montos, generar facturas ni procesar pagos. Es una operación de solo lectura que no modifica el estado de la habitación.

**Prueba Independiente**: Puede probarse de forma independiente invocando la consulta de tarifa base enviando el identificador (`roomId`) de una habitación existente y verificando que el sistema retorna el valor numérico exacto almacenado para esa unidad habitacional.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa de tarifa base por habitación
   - **Dado** existe una habitación con un `roomId` dado cuya tarifa base configurada es $150.000
   - **Cuando** el Módulo 3 consulta la tarifa base de ese `roomId` para una fecha
   - **Entonces** el sistema retorna `baseRate = "150000.00"` con `currency = "COP"` y la misma fecha consultada

2. **Escenario**: Consulta de tarifa base actualizada de una habitación
   - **Dado** la habitación con ese `roomId` tenía tarifa base $150.000 y el Administrador la actualizó a $180.000 mediante "Editar habitación"
   - **Cuando** el Módulo 3 consulta la tarifa base de ese `roomId`
   - **Entonces** el sistema retorna el valor actualizado, `baseRate = "180000.00"`

---

### Historia de Usuario 2 - Manejo de consultas inválidas (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero recibir una respuesta clara y controlada cuando consulto la tarifa base de una habitación que no existe o sin una fecha válida, para manejar la excepción correctamente sin generar cálculos inconsistentes.

**Por qué esta prioridad**: Una consulta a una habitación inexistente o sin fecha podría generar cálculos erróneos en la facturación. El sistema debe distinguir la habitación no encontrada (`404 ROOM_NOT_FOUND`, que Módulo 3 no reintenta) de una petición mal formada (`400 VALIDATION_ERROR`) para que Módulo 3 gestione cada caso.

**Prueba Independiente**: Puede probarse consultando la tarifa base de un `roomId` que no existe y, por separado, consultando una habitación existente sin `date` o con una fecha inválida, y verificando que cada caso retorna su error controlado con el formato `ApiError`.

**Escenarios de Aceptación**:

3. **Escenario**: Consulta de tarifa base para habitación inexistente
   - **Dado** no existe ninguna habitación con el `roomId` especificado en el inventario
   - **Cuando** el Módulo 3 consulta la tarifa base de dicha habitación
   - **Entonces** el sistema retorna un error controlado indicando que la habitación no fue encontrada (`404 ROOM_NOT_FOUND`)

4. **Escenario**: Consulta sin fecha o con fecha inválida
   - **Dado** existe una habitación con un `roomId` dado
   - **Cuando** el Módulo 3 consulta su tarifa base sin el parámetro `date`, con un formato distinto de AAAA-MM-DD o con una fecha inexistente (ej. 2026-02-31)
   - **Entonces** el sistema retorna un error controlado de validación (`400 VALIDATION_ERROR`) sin valores parciales

---

### Casos Borde

- **Consulta concurrente de la misma habitación por múltiples instancias de Módulo 3**: el sistema maneja la concurrencia sin bloqueos, retornando siempre el valor actual de la tarifa base de esa habitación.
- **Habitación con tarifa base modificada durante una facturación en curso**: el Módulo 3 captura la tarifa al momento exacto de la consulta, independientemente de modificaciones posteriores en el inventario.
- **Tarifa base con valores decimales**: el sistema retorna la tarifa con exactamente dos decimales, como texto (ej. `"150000.00"`), para precisión en los cálculos financieros.
- **Consulta sobre habitación en cualquier estado operativo**: la tarifa base es un atributo intrínseco de la unidad habitacional física (`Room`), consultable independientemente de si la habitación se encuentra en estado `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir la consulta de tarifa base al actor "Módulo 3" (Facturación) como operación de solo lectura.
- **FR-002**: El sistema DEBE permitir la consulta de la tarifa base por identificador único de habitación (`roomId`), y NO por tipo o categoría de habitación, con el parámetro obligatorio `date` (`YYYY-MM-DD`); si falta o no es válido, retorna un error de validación. La tarifa base no depende de la fecha: cualquier fecha válida devuelve el mismo valor.
- **FR-003**: El sistema DEBE retornar la tarifa base almacenada para la habitación consultada como texto decimal con dos decimales (`baseRate`), junto con `roomId`, la `date` recibida tal cual y `currency` (`"COP"`). No se retorna un objeto completo de Room.
- **FR-004**: El sistema DEBE retornar un error controlado cuando la habitación consultada no exista en el inventario de Módulo 1.
- **FR-005**: El sistema DEBE retornar la tarifa base vigente de la habitación física consultada independientemente de su estado operativo actual.
- **FR-006**: El sistema NO DEBE modificar ningún dato de la habitación ni ejecutar transiciones de estado al atender esta consulta.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Module3 (Facturación)**: Sistema externo que consume la tarifa base por habitación para calcular cargos de estancia y facturación.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta de tarifa base por habitación se responde en menos de 500 ms (timeout de Módulo 3).
- **SC-002**: El 100% de las consultas sobre habitaciones existentes retornan el valor numérico exacto de su tarifa base configurada.
- **SC-003**: El 100% de las consultas sobre habitaciones inexistentes retornan un error controlado sin valores parciales ni inventados.
- **SC-004**: La consulta no altera el estado de la habitación ni interfiere con sus transiciones operativas.
