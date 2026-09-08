# Especificación de Funcionalidad: Consultar Tarifa Base

**Módulo**: Módulo 3 — Facturación
**Actor principal**: Módulo 3 (sistema externo)
**Creado**: 2026-09-07

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de tarifa base de una habitación (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero consultar la tarifa base según el tipo de habitación para utilizarla como base en el cálculo de cargos, estancias y facturación del huésped.

**Por qué esta prioridad**: La tarifa base es el dato comercial fundamental que alimenta la liquidación financiera de cada estancia. Sin acceso confiable a este dato, el Módulo 3 no puede calcular montos, generar facturas ni procesar pagos. Es una operación de solo lectura que no modifica el estado de la habitación.

**Prueba Independiente**: Puede probarse de forma independiente invocando la consulta de tarifa base por tipo de habitación y verificando que el sistema retorna el valor numérico correcto almacenado para ese tipo.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa de tarifa base por tipo de habitación
   - **Dado** existe un tipo de habitación "Suite" con tarifa base $200.000
   - **Cuando** el Módulo 3 consulta la tarifa base para el tipo "Suite"
   - **Entonces** el sistema retorna el valor $200.000 como tarifa base para ese tipo

2. **Escenario**: Consulta de tarifa base actualizada para tipo de habitación
   - **Dado** el tipo de habitación "Sencilla" tenía tarifa base $100.000 y el Administrador la actualizó a $120.000 mediante el caso de uso "Editar tipo de habitación"
   - **Cuando** el Módulo 3 consulta la tarifa base para el tipo "Sencilla"
   - **Entonces** el sistema retorna el valor actualizado de $120.000

3. **Escenario**: Consulta de tarifa base para tipo de habitación cuando no existe
   - **Dado** no existe ningún tipo de habitación llamado "Penthouse"
   - **Cuando** el Módulo 3 consulta la tarifa base para "Penthouse"
   - **Entonces** el sistema retorna un error indicando que el tipo de habitación no fue encontrado

---

### Historia de Usuario 2 - Rechazo de consulta sobre habitación inexistente (Prioridad: P1)

Como sistema del Módulo 3 (Facturación), quiero recibir una respuesta clara cuando intento consultar la tarifa base de una habitación que no existe, para manejar el error correctamente sin generar facturación inconsistente.

**Por qué esta prioridad**: Una consulta a una habitación inexistente podría generar cálculos erróneos en la facturación. El sistema debe informar claramente que la habitación no fue encontrada para que el Módulo 3 pueda gestionar la excepción.

**Prueba Independiente**: Puede probarse intentando consultar la tarifa base de un número de habitación que no existe en el sistema y verificando que se retorna un error claro.

**Escenarios de Aceptación**:

1. **Escenario**: Intento de consultar tarifa de habitación inexistente
   - **Dado** no existe ninguna habitación con número "999" en el sistema
   - **Cuando** el Módulo 3 consulta la tarifa base de la habitación "999"
   - **Entonces** el sistema retorna un error indicando que la habitación no fue encontrada

---

### Casos Borde

- Consulta concurrente de la misma **tipo de habitación** por múltiples instancias del Módulo 3: el sistema debe manejar la concurrencia sin bloqueos, retornando siempre el valor actual de la tarifa base del tipo.
- Tipo de habitación cuya tarifa base fue modificada durante una facturación en curso: el Módulo 3 debe capturar la tarifa al momento de la consulta, independientemente de modificaciones posteriores.
- Tarifa base con valores decimales: el sistema debe soportar y retornar valores con hasta dos decimales para precisión en los cargos.
- Tipo de habitación con tarifa base en cero o negativa: aunque el registro inicial valida que la tarifa sea mayor a cero, si por algún error de datos existe una tarifa inválida, el sistema debe retornar el valor almacenado y permitir que el Módulo 3 gestione la inconsistencia.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir la consulta de tarifa base exclusivamente al actor "Módulo 3" (Facturación) como operación de solo lectura.
- **FR-002**: El sistema DEBE retornar el valor numérico de la tarifa base almacenada para el tipo de habitación consultado, incluyendo decimales si aplica.
- **FR-003**: El sistema DEBE permitir la consulta por nombre o código de tipo de habitación como identificador de búsqueda, restringiendo a los tipos definidos: Sencilla, Doble, Suite, Boutique.
- **FR-004**: El sistema DEBE retornar un error claro cuando el tipo de habitación consultado no existe en el sistema.
- **FR-005**: El sistema DEBE retornar la tarifa base actual del tipo de habitación, independiente del estado de habitaciones individuales.
- **FR-006**: El sistema NO DEBE modificar ningún dato ni ejecutar transiciones de estado al realizar esta consulta.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Unidad habitacional del hotel. Para este caso de uso, se consulta el atributo "tarifa base" asociado al **tipo de habitación**, no a una habitación individual.
- **RoomType**: Categoría de habitación (ej. "Sencilla", "Doble", "Suite", "Boutique") que posee una tarifa base asociada.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: La consulta de tarifa base se responde en menos de 1 segundo.
- **SC-002**: El 100% de las consultas sobre tipos de habitación existentes retornan el valor correcto de la tarifa base.
- **SC-003**: El 100% de las consultas sobre tipos de habitación inexistentes retornan un error claro sin resultados parciales.
- **SC-004**: La consulta no bloquea ni interfiere con operaciones de transición de estado en curso sobre la misma habitación.
