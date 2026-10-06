# Especificación de Funcionalidad: Consultar Liquidación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía caso de uso "Registrar Check-Out")
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del desglose de liquidación y factura definitiva en Check-Out (Prioridad: P1)

Como Recepcionista, quiero que durante el Check-Out el sistema consulte de forma reactiva mediante REST GET a Módulo 3 la liquidación y la factura definitiva asociada para estructurar la información en los pasos de Liquidación (factura asociada e información de la reserva: canal, comisión e ingreso neto) y Pago (factura asociada, resumen para el huésped con hospedaje, IVA y total a pagar; junto a los datos reales de estadía: noches, fecha de entrada y fecha de salida), para contar con la información financiera oficial antes de autorizar la salida y liberar la unidad.

**Por qué esta prioridad**: Es la consulta obligatoria de "Registrar Check-Out" que traduce la estancia física en información financiera oficial para el cierre de la estadía. Módulo 1 actúa como consumidor estricto de la liquidación y de la factura definitiva emitidas por Módulo 3, sin calcular tarifas, sin deducir comisiones ni recaudar pagos en recepción.

**Prueba Independiente**: Se prueba invocando este caso de uso de forma aislada con un conjunto de datos de estancia válido (`reservationRef`, fechas reservadas `startDate` y `endDate`, fechas reales `checkInDate` y `checkOutDate`, canal "Directo" u "OTA" obtenido de `Stay`, y `roomId`) y verificando que el sistema retorna la estructura completa suministrada por Módulo 3 sin alteraciones, desagregada para su presentación en el paso Liquidación (Información de la reserva) y en el paso Pago (Resumen para el huésped y Datos de la estadía), con la factura definitiva asociada visible en la parte superior de ambos pasos y sin procesar transacciones monetarias en Módulo 1.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de liquidación para reserva de Canal Directo (sin comisión OTA)
   - **Dado** una estancia asociada a una reserva con canal de origen "Directo" registrado en `Stay.source` (0% comisión OTA)
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `SettlementRequest` con `source` a Módulo 3 mediante REST GET, recibe la liquidación financiera y entrega para la atención:
     1. Para el paso Liquidación:
        - Factura definitiva asociada (arriba, con su número oficial, ej. FAC-40001)
        - Información de la reserva: Canal de origen (desplegado desde `Stay.source` local: "Directo"), Comisión OTA en 0% (valor $0) e Ingreso neto equivalente al valor del hospedaje
     2. Para el paso Pago:
        - Factura definitiva asociada (arriba)
        - Resumen para el huésped: Valor del hospedaje (total ya calculado), IVA y Total a pagar
        - Datos de la estadía: Noches de hospedaje, Fecha de entrada real (`checkInDate`) y Fecha de salida real (`checkOutDate`), sin registrar ni recibir pagos en recepción

2. **Escenario**: Consulta de liquidación para reserva originada en OTA (con comisión de intermediario)
   - **Dado** una estancia asociada a una reserva proveniente de intermediario con canal registrado en `Stay.source` ("OTA" o identificador de OTA) y comisión pactada
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `SettlementRequest` con `source` a Módulo 3 mediante REST GET, recibe la liquidación financiera y entrega para la atención:
     1. Para el paso Liquidación:
        - Factura definitiva asociada (arriba)
        - Información de la reserva: Canal de origen (desplegado desde `Stay.source` local), Porcentaje de comisión OTA, Valor monetario de comisión OTA e Ingreso neto (hospedaje menos comisión)
     2. Para el paso Pago:
        - Factura definitiva asociada (arriba)
        - Resumen para el huésped: Valor del hospedaje, IVA y Total a pagar (sin recargar comisiones al huésped)
        - Datos de la estadía: Noches, Fecha de entrada real (`checkInDate`) y Fecha de salida real (`checkOutDate`)

3. **Escenario**: Valor de hospedaje total consolidado sin desglose por noche
   - **Dado** una estancia de múltiples noches o con ajuste por salida anticipada
   - **Cuando** se consulta la liquidación a Módulo 3
   - **Entonces** el sistema recibe el valor del hospedaje como una cifra total consolidada ya calculada por Módulo 3, sin requerir ni procesar un desglose noche a noche en Módulo 1.

4. **Escenario**: Exclusión estricta de consumos locales y recaudos
   - **Dado** el resultado provisto por Módulo 3
   - **Cuando** se procesa la información de la liquidación en los pasos de Liquidación y Pago
   - **Entonces** el sistema garantiza que no se incluyan conceptos de consumos locales (minibar, restaurante, lavandería o daños) ni se procesen recaudos monetarios o pasarelas en Módulo 1, circunscribiendo la consulta al hospedaje, comisiones intermediarias, impuestos y la factura definitiva de Módulo 3.

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

- **Canal Directo sin comisión OTA**: El sistema reporta el canal como "Directo", el porcentaje de comisión OTA en 0%, el valor de comisión en $0 y el ingreso neto exactamente igual al valor del hospedaje.
- **Canal OTA sin marcas comerciales específicas**: El canal de intermediario se expone únicamente con la etiqueta general "OTA" (sin nombres de plataformas o agencias de viajes externas).
- **Inmutabilidad de la factura definitiva asociada**: La factura definitiva es emitida exclusivamente por Módulo 3 al liquidar; Módulo 1 la consume y referencia como un registro inmutable con su numeración oficial asignada (ej. FAC-40001), mostrándola en la cabecera de los pasos de Liquidación y Pago sin alterarla ni recalcularla.
- **Consultas reiteradas para la misma estancia**: Si se solicita la liquidación más de una vez antes de formalizar la salida, la consulta se reejecuta de manera segura sin duplicar registros ni persistir saldos hasta la confirmación final.
- **Salida anticipada (Early Check-Out)**: El valor de hospedaje consolidado recibido de Módulo 3 ya incorpora cualquier penalización o ajuste definido internamente por las políticas de Módulo 3, entregándose como un único valor total ya procesado sobre las noches reales.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) por el caso de uso "Registrar Check-Out", durante la fase de liquidación de la estadía.
- **FR-002**: Conforme al contrato de interfaces con Módulo 3: "Consultar liquidación: REST · GET · Reactivo", durante el Check-Out el sistema DEBE consultar de forma reactiva mediante una petición sincrónica REST GET a Módulo 3 la liquidación de la estadía (`SettlementRequest`), esperando respuesta inmediata con los siguientes parámetros:
  - Referencia de reserva (`reservationRef`)
  - Tipo de evento (`CHECK_OUT`)
  - Fechas contratadas de estadía (`startDate`, `endDate`) obtenidas de `Stay`
  - Fechas reales de la estancia (`checkInDate` real registrado en `Stay` y `checkOutDate` correspondiente a la fecha de salida, sin horas)
  - Canal de procedencia (`source`: "Directo" u "OTA", obtenido localmente de `Stay`)
  - Identificador de la habitación física (`roomId`)
- **FR-003**: El sistema DEBE recibir desde Módulo 3 la siguiente estructura oficial de liquidación y facturación (`SettlementSummary`):
  1. Factura definitiva asociada emitida por Módulo 3 (número consecutivo oficial `invoiceNumber`, ej. FAC-40001).
  2. Valor de hospedaje consolidado (`accommodationTotalAmount`, total ya calculado, no desglosado por noche).
  3. Porcentaje de la comisión OTA aplicable (`otaCommissionPercentage`, si corresponde).
  4. Valor monetario de la comisión OTA (`otaCommissionAmount`, si corresponde).
  5. Impuesto al Valor Agregado (`taxAmount`, IVA).
  6. Ingreso neto (`netIncomeAmount`: valor de hospedaje menos comisión OTA).
  *(Nota: El canal de origen de la reserva `source` viaja de Módulo 1 a Módulo 3 en la solicitud, pero no regresa en la respuesta de Módulo 3; Módulo 1 lo recupera de la entidad local `Stay.source`).*
- **FR-004**: El sistema DEBE proveer la información estructurada para su presentación separada en dos etapas de recepción:
  - *En el paso 2 (Liquidación)*:
    - Factura definitiva asociada (arriba).
    - **Información de la reserva**:
      - Canal de origen (desplegado directamente desde `Stay.source` local: ej. "Directo" o nombre del canal/OTA)
      - Porcentaje de comisión OTA (XX %)
      - Valor monetario de comisión OTA ($XXX)
      - Ingreso neto ($XXX)
  - *En el paso 3 (Pago)*:
    - Factura definitiva asociada (arriba).
    - **Resumen para el huésped**:
      - Valor del hospedaje ($XXX)
      - IVA ($XXX)
      - Total a pagar ($XXX)
    - **Datos de la estadía**:
      - Noches (X)
      - Fecha de entrada (`checkInDate`)
      - Fecha de salida (`checkOutDate`)
- **FR-005**: El sistema DEBE mantener los valores como datos de consulta e informativos, sin procesar pagos, cobros por pasarela ni modificar las cifras provistas por Módulo 3. En el paso 3 ("Pago"), no se registra ni recibe dinero; su propósito es la exhibición y revisión del resumen con el huésped.
- **FR-006**: Este caso de uso NO DEBE decidir de forma autónoma la liberación física de la habitación ni alterar el estado físico de la misma ante fallas de Módulo 3, delegando la gestión de contingencia en "Registrar Check-Out".

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **SettlementRequest**: Objeto conceptual de solicitud remitido a Módulo 3 con los parámetros de la estancia: `reservationRef`, `eventType` (`CHECK_OUT`), `startDate`, `endDate`, `checkInDate`, `checkOutDate` (fechas sin hora), `source` (obtenido localmente de `Stay.source`) y `roomId`.
- **SettlementSummary**: Estructura conceptual informativa devuelta por Módulo 3 y consumida vía "Consultar liquidación" conteniendo: factura definitiva asociada (`invoiceNumber`), valor de hospedaje consolidado (`accommodationTotalAmount`), porcentaje de comisión OTA (`otaCommissionPercentage`), valor de comisión OTA (`otaCommissionAmount`), IVA (`taxAmount`) e ingreso neto (`netIncomeAmount`). No incluye `source`.
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real de la habitación. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: "DIRECTA" o identificador de OTA como `BOOKING`, `EXPEDIA`...), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Module3 (Facturación y Liquidación)**: Sistema externo responsable exclusivo de calcular la liquidación, aplicar comisiones e IVA, y generar la factura definitiva oficial.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las consultas de liquidación completadas exitosamente suministran con exactitud los datos estructurados para los pasos de Liquidación y Pago con su factura definitiva asociada en la cabecera, sin alterar ni recalcular los montos provistos por Módulo 3.
- **SC-002**: La consulta de liquidación responde en menos de 2 segundos en condiciones normales de conectividad con Módulo 3.
- **SC-003**: Cero excepciones técnicas no controladas propagadas al Recepcionista ante demoras o errores devueltos por Módulo 3; el 100% de las fallas se traducen en un resultado controlado.
- **SC-004**: Cero cálculos locales de tarifas, comisiones o impuestos y cero recaudos monetarios ejecutados dentro de Módulo 1.
