# Especificación de Funcionalidad: Consultar Tarifa Base

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Módulo 3 — Facturación (sistema externo)
**Creado**: 2026-09-07
**Actualizado**: 2026-10-09

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de tarifa base de una habitación (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero consultar la tarifa base de una habitación específica mediante su identificador o número de habitación, para utilizarla como base en el cálculo de cargos, estancias y facturación del huésped.

**Por qué esta prioridad**: La tarifa base configurada en cada unidad habitacional es el dato comercial fundamental que alimenta la liquidación financiera de cada estancia. Sin acceso confiable a este dato por habitación, el Módulo 3 no puede calcular montos, generar facturas ni procesar pagos. Es una operación de solo lectura que no modifica el estado de la habitación.

**Prueba Independiente**: Puede probarse de forma independiente invocando la consulta de tarifa base enviando el identificador o número de una habitación existente y verificando que el sistema retorna el valor numérico exacto almacenado para esa unidad habitacional.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa de tarifa base por habitación
   - **Dado** existe una habitación con número "101" cuya tarifa base configurada es $150.000
   - **Cuando** el Módulo 3 consulta la tarifa base de la habitación "101"
   - **Entonces** el sistema retorna el valor numérico $150.000 correspondiente a la tarifa base de esa habitación

2. **Escenario**: Consulta de tarifa base actualizada de una habitación
   - **Dado** la habitación "101" tenía tarifa base $150.000 y el Administrador la actualizó a $180.000 mediante "Editar habitación"
   - **Cuando** el Módulo 3 consulta la tarifa base de la habitación "101"
   - **Entonces** el sistema retorna el valor actualizado de $180.000

3. **Escenario**: Consulta de tarifa base para habitación inexistente
   - **Dado** no existe ninguna habitación con número "999" o identificador especificado en el inventario
   - **Cuando** el Módulo 3 consulta la tarifa base de dicha habitación
   - **Entonces** el sistema retorna un error controlado indicando que la habitación no fue encontrada

---

### Historia de Usuario 2 - Manejo de consulta sobre habitación no encontrada (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero recibir una respuesta clara y controlada cuando intento consultar la tarifa base de una habitación que no existe, para manejar la excepción correctamente sin generar cálculos inconsistentes.

**Por qué esta prioridad**: Una consulta a una habitación inexistente podría generar cálculos erróneos en la facturación. El sistema debe informar claramente que la habitación no fue encontrada para que el Módulo 3 pueda gestionar la excepción.

**Prueba Independiente**: Puede probarse intentando consultar la tarifa base de un número o identificador de habitación que no existe en el sistema y verificando que se retorna un error claro y controlado.

**Escenarios de Aceptación**:

1. **Escenario**: Intento de consultar tarifa de habitación inexistente
   - **Dado** no existe ninguna habitación con número "999" en el sistema
   - **Cuando** el Módulo 3 consulta la tarifa base de la habitación "999"
   - **Entonces** el sistema retorna un error controlado indicando que la habitación no fue encontrada

---

### Casos Borde

- **Consulta concurrente de la misma habitación por múltiples instancias de Módulo 3**: el sistema maneja la concurrencia sin bloqueos, retornando siempre el valor actual de la tarifa base de esa habitación.
- **Habitación con tarifa base modificada durante una facturación en curso**: el Módulo 3 captura la tarifa al momento exacto de la consulta, independientemente de modificaciones posteriores en el inventario.
- **Tarifa base con valores decimales**: el sistema soporta y retorna valores con hasta dos decimales para precisión en los cálculos financieros.
- **Consulta sobre habitación en cualquier estado operativo**: la tarifa base es un atributo intrínseco de la unidad habitacional física (`Room`), consultable independientemente de si la habitación se encuentra en estado `Available`, `Reserved`, `Occupied`, `PendingCleaning`, `InCleaning`, `DisabledForRepairs`, `TechnicalBlock` o `Inactive`.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir la consulta de tarifa base exclusivamente al actor "Módulo 3" (Facturación) como operación de solo lectura.
- **FR-002**: El sistema DEBE permitir la consulta de la tarifa base por identificador único de habitación (`roomId`) o número de habitación (`numberRoom`), y NO por tipo o categoría de habitación.
- **FR-003**: El sistema DEBE retornar el valor numérico de la tarifa base almacenada para la habitación física consultada, incluyendo decimales si aplica. [NEEDS_CONFIRMATION_MODULO_3: Confirmar si Módulo 3 espera únicamente el valor numérico decimal o una estructura JSON con identificador y tarifa base]
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

- **SC-001**: La consulta de tarifa base por habitación se responde en menos de 1 segundo.
- **SC-002**: El 100% de las consultas sobre habitaciones existentes retornan el valor numérico exacto de su tarifa base configurada.
- **SC-003**: El 100% de las consultas sobre habitaciones inexistentes retornan un error controlado sin valores parciales ni inventados.
- **SC-004**: La consulta no altera el estado de la habitación ni interfiere con sus transiciones operativas.
