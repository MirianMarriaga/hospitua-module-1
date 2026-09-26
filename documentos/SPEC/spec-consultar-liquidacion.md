# Especificación de Funcionalidad: Consultar Liquidación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía caso de uso "Registrar Check-Out")
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del desglose de liquidación y factura definitiva en Check-Out (Prioridad: P1)

Como Recepcionista, quiero que durante el Check-Out el sistema consulte automáticamente la liquidación y la factura definitiva calculadas por Módulo 3, estructurando la información en un resumen de cobro para el huésped y un resumen operativo de la reserva para recepción, para contar con la información financiera oficial antes de autorizar la salida y liberar la unidad.

**Por qué esta prioridad**: Es la consulta obligatoria de "Registrar Check-Out" que traduce la estancia física en información financiera oficial para el cierre de la estadía. Módulo 1 actúa como consumidor estricto de la liquidación y la factura definitiva asociadas emitidas por Módulo 3, sin calcular tarifas, sin deducir comisiones ni recaudar pagos.

**Prueba Independiente**: Se prueba invocando este caso de uso de forma aislada con un conjunto de datos de estancia válido (referencia de reserva, fechas reservadas, marcas de tiempo reales `checkInTime` y `checkOutTime`, canal y datos de habitación) y verificando que el sistema retorna la estructura completa suministrada por Módulo 3 sin alteraciones, desagregada en el resumen para el huésped y la información operativa de la reserva con la factura definitiva asociada.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de liquidación para reserva de Canal Directo (sin comisión OTA)
   - **Dado** una estancia asociada a una reserva de canal directo (0% comisión OTA)
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema recibe desde Módulo 3 y entrega para la atención:
     1. Para el huésped:
        - Valor del hospedaje (total ya calculado, no desglosado por noche)
        - IVA
        - Total a pagar (suma de hospedaje e IVA)
     2. Para la operación de recepción:
        - Canal de origen (Directo)
        - Comisión OTA en 0% (valor $0)
        - Ingreso neto equivalente al valor de hospedaje
        - Factura definitiva asociada emitida por Módulo 3 con su numeración oficial y desglose facturable (hospedaje, IVA y total)

2. **Escenario**: Consulta de liquidación para reserva originada en OTA (con comisión de intermediario)
   - **Dado** una estancia asociada a una reserva proveniente de una OTA (ej. Booking, Expedia) con porcentaje de comisión pactado
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema recibe desde Módulo 3 y entrega para la atención:
     1. Para el huésped:
        - Valor del hospedaje (total consolidado ya calculado)
        - IVA
        - Total a pagar (sin recargar comisiones intermediarias al huésped)
     2. Para la operación de recepción:
        - Canal de origen (nombre de la OTA)
        - Porcentaje de comisión OTA aplicable
        - Valor monetario de la comisión OTA
        - Ingreso neto (valor de hospedaje menos la comisión OTA)
        - Factura definitiva asociada emitida por Módulo 3, conteniendo su número consecutivo oficial y su desglose correspondiente (hospedaje, comisión como referencia informativa, IVA y total) según FR-010 de generar_factura_final.md

3. **Escenario**: Valor de hospedaje total consolidado sin desglose por noche
   - **Dado** una estancia de múltiples noches o con ajuste por salida anticipada
   - **Cuando** se consulta la liquidación a Módulo 3
   - **Entonces** el sistema recibe el valor del hospedaje como una cifra total consolidada ya calculada por Módulo 3, sin requerir ni procesar un desglose noche a noche en Módulo 1.

4. **Escenario**: Exclusión estricta de consumos locales
   - **Dado** el resultado provisto por Módulo 3
   - **Cuando** se procesa la información de la liquidación
   - **Entonces** el sistema garantiza que no se incluyan ni gestionen conceptos correspondientes a consumos locales (minibar, alimentos, lavandería o daños), circunscribiendo la consulta al hospedaje, comisiones intermediarias, impuestos y la factura definitiva de Módulo 3.

---

### Historia de Usuario 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Prioridad: P2)

Como Recepcionista, quiero que si Módulo 3 no responde o experimenta lentitud al entregar la liquidación y la factura, el sistema informe la contingencia de forma controlada en lugar de emitir errores técnicos, permitiendo gestionar la salida física en recepción.

**Por qué esta prioridad**: Módulo 3 es un sistema externo de pricing y facturación; su indisponibilidad no debe bloquear la recepción del hotel ni generar excepciones técnicas o fallos no controlados, garantizando que la decisión de continuar la salida física (liberar habitación y regularizar saldos posteriormente) quede en manos de "Registrar Check-Out".

**Prueba Independiente**: Se prueba simulando falta de respuesta o mensajes de error de Módulo 3 al consultar la liquidación, verificando que el caso de uso retorne un resultado controlado de estado `UNAVAILABLE` con la causa del fallo al caso de uso llamador "Registrar Check-Out", sin provocar caídas del sistema.

**Escenarios de Aceptación**:

1. **Escenario**: Falta de respuesta por lentitud de red en Módulo 3
   - **Dado** que el servicio de Módulo 3 no emite respuesta dentro del tiempo límite de espera configurado
   - **Cuando** se agota el tiempo de consulta
   - **Entonces** el sistema captura la condición y entrega a "Registrar Check-Out" un resultado controlado de indisponibilidad, impidiendo fallos técnicos o caídas del sistema.

2. **Escenario**: Error devuelto por Módulo 3
   - **Dado** que Módulo 3 responde con un mensaje de error controlado o falla técnica del servicio
   - **Cuando** "Consultar liquidación" procesa la respuesta
   - **Entonces** el sistema traduce el código en un mensaje informativo sobre indisponibilidad del servicio de facturación, sin intentar calcular ni generar montos inventados o parciales.

3. **Escenario**: Delegación de la contingencia en "Registrar Check-Out"
   - **Dado** un resultado de indisponibilidad de liquidación devuelto por este caso de uso
   - **Cuando** el flujo de Check-Out recibe la notificación de indisponibilidad
   - **Entonces** "Consultar liquidación" no ejecuta ninguna acción autónoma de liberación física ni de persistencia; la decisión de proceder con la salida física y registrar la transacción pendiente de regularización es asumida íntegramente por "Registrar Check-Out".

---

### Casos Borde

- **Canal Directo sin comisión OTA**: El sistema reporta el porcentaje de comisión OTA en 0%, el valor de comisión en $0 y el ingreso neto exactamente igual al valor del hospedaje.
- **Inmutabilidad de la factura definitiva asociada**: La factura definitiva es emitida exclusivamente por Módulo 3 al liquidar; Módulo 1 la consume y referencia como un registro inmutable con su numeración oficial asignada, sin alterarla ni recalcularla.
- **Consultas reiteradas para la misma estancia**: Si se solicita la liquidación más de una vez antes de formalizar la salida, la consulta se reejecuta de manera segura sin duplicar registros ni persistir saldos hasta la confirmación final.
- **Salida anticipada (Early Check-Out)**: El valor de hospedaje consolidado recibido de Módulo 3 ya incorpora cualquier penalización o ajuste definido internamente por las políticas de Módulo 3, entregándose como un único valor total ya procesado.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) por el caso de uso "Registrar Check-Out", durante la fase de liquidación de la estadía.
- **FR-002**: El sistema DEBE estructurar la solicitud hacia Módulo 3 (`SettlementRequest`) con los siguientes datos:
  - Referencia de reserva (`reservationRef`)
  - Tipo de evento (`CHECK_OUT`)
  - Fechas contratadas de estadía (`startDate`, `endDate`) obtenidas de Módulo 2
  - Marcas de tiempo reales de la estancia (`checkInTime` real registrado en la Estancia y `checkOutTime` correspondiente al momento de la salida)
  - Canal de procedencia (`source`)
  - Identificador de la habitación física (`roomId`)
- **FR-003**: El sistema DEBE recibir desde Módulo 3 la siguiente estructura oficial de liquidación y facturación:
  1. Valor de hospedaje (total, ya calculado y consolidado — no desglosado por noche).
  2. Canal de origen de la reserva (Booking / Web / Directo / etc.).
  3. Porcentaje de la comisión OTA aplicable (si corresponde).
  4. Valor monetario de la comisión OTA (si corresponde).
  5. Impuesto al Valor Agregado (IVA).
  6. Ingreso neto (valor de hospedaje menos comisión OTA).
  7. Factura definitiva asociada emitida por "Generar factura final" de Módulo 3

- **FR-004**: El sistema DEBE proveer la información estructurada en dos grupos diferenciados para la atención en recepción:
  - **Resumen para el huésped**:
    - Valor del hospedaje ($XXX)
    - IVA ($XXX)
    - Total a pagar ($XXX)
  - **Información de la reserva / operación (para uso de la recepcionista)**:
    - Canal de origen (Booking / Web / Directo)
    - Porcentaje de comisión OTA (XX %)
    - Valor monetario de comisión OTA ($XXX)
    - Ingreso neto ($XXX)
    - Factura definitiva asociada (número consecutivo oficial y desglose)
- **FR-005**: El sistema DEBE mantener los valores como datos de consulta e informativos, sin procesar pagos, cobros por pasarela ni modificar las cifras provistas por Módulo 3.
- **FR-006**: Este caso de uso NO DEBE decidir de forma autónoma la liberación física de la habitación ni alterar el estado físico de la misma ante fallas de Módulo 3, delegando la gestión de contingencia en "Registrar Check-Out".

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **SettlementRequest**: Objeto conceptual de solicitud remitido a Módulo 3 con los parámetros de la estancia: `reservationRef`, `eventType`, `startDate`, `endDate`, `checkInTime`, `checkOutTime`, `source` y `roomId`.
- **SettlementSummary**: Estructura conceptual informativa devuelta por Módulo 3 conteniendo: valor de hospedaje consolidado (`accommodationTotalAmount`), canal (`source`), porcentaje de comisión OTA (`otaCommissionPercentage`), valor de comisión OTA (`otaCommissionAmount`), IVA (`taxAmount`), ingreso neto (`netIncomeAmount`) y la factura definitiva asociada.
- **Module3 (Facturación y Liquidación)**: Sistema externo responsable exclusivo de calcular la liquidación, aplicar comisiones e IVA, y generar la factura definitiva oficial.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las consultas de liquidación completadas exitosamente suministran con exactitud los dos bloques de información (resumen para el huésped e información operativa de la reserva) sin alterar ni recalcular los montos provistos por Módulo 3.
- **SC-002**: La consulta de liquidación responde en menos de 2 segundos en condiciones normales de conectividad con Módulo 3.
- **SC-003**: Cero excepciones técnicas no controladas propagadas al Recepcionista ante demoras o errores devueltos por Módulo 3; el 100% de las fallas se traducen en un resultado controlado.
- **SC-004**: Cero cálculos locales de tarifas, comisiones o impuestos ejecutados dentro de Módulo 1.
