# Especificación de Funcionalidad: Consultar Liquidación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía `<<includes>>` desde "Registrar Check-Out")
**Creado**: 2026-09-25
**Actualizado**: 2026-10-09

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del desglose de liquidación en Check-Out (Prioridad: P1)

Como Recepcionista, quiero que durante el Check-Out el sistema consulte de forma reactiva mediante REST GET a Módulo 3 la liquidación de la estancia enviando `source` desde `Stay.source`, para estructurar la información en los pasos de Liquidación (tipo de liquidación, factura, fuente local, comisión e ingreso neto) y Pago (hospedaje, IVA, total a pagar de M3, datos de estadía), sin que Módulo 3 devuelva el campo `source`.

**Por qué esta prioridad**: Es la consulta obligatoria de "Registrar Check-Out" que traduce la estancia en información financiera oficial. Módulo 1 actúa como consumidor estricto: no calcula tarifas, no deduce comisiones, no recauda pagos. El `totalAmount` proviene exclusivamente de M3 (nunca se suma localmente en M1).

**Prueba Independiente**: Invocar con datos válidos de estancia (`reservationRef`, `checkInDate`, `checkOutDate`, `source` de `Stay`, `roomId`) y verificar que el sistema retorna la estructura de M3 desagregada para los pasos de Liquidación y Pago, sin parámetros `eventType`, `startDate` ni `endDate`, sin header `Authorization`, y sobre la URL `GET /api/settlements` (sin `/v1/`).

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de liquidación para reserva DIRECTA (sin comisión OTA)
   - **Dado** una estancia con `source = DIRECTA` en `Stay.source`
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `GET /api/settlements` con `reservationRef`, `checkInDate`, `checkOutDate`, `source = DIRECTA`, `roomId`, `categoryRoom` y recibe de M3:
     - Para el paso Liquidación: `settlementType` ("Liquidación informativa" o "Liquidación final"), `invoiceNumber` (entero consecutivo o no mostrado si informativa), fuente desplegada desde `Stay.source` local, comisión 0%, ingreso neto = hospedaje
     - Para el paso Pago: `accommodationTotalAmount`, `taxAmount`, `totalAmount` de M3 (no mostrados si informativa), noches, `checkInDate`, `checkOutDate`

2. **Escenario**: Consulta de liquidación para reserva OTA (con comisión)
   - **Dado** una estancia con `source = BOOKING` o `EXPEDIA` en `Stay.source`
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `source` y `categoryRoom` al endpoint de M3 y recibe la liquidación (sin `source` en la respuesta). Muestra en el paso Liquidación: fuente desde `Stay.source`, `otaCommissionPercentage`, `otaCommissionAmount`, `netIncomeAmount`

3. **Escenario**: Valor de hospedaje consolidado (sin desglose por noche)
   - **Dado** una estancia de múltiples noches o con salida anticipada
   - **Cuando** se consulta la liquidación
   - **Entonces** el sistema recibe `accommodationTotalAmount` como cifra total ya calculada por M3; Módulo 1 no desglosa por noche

4. **Escenario**: Liquidación informativa — sin IVA, sin total, sin factura
   - **Dado** que M3 devuelve `settlementType = INFORMATIVA` con `invoiceNumber = null`, `taxAmount = null`, `totalAmount = null`
   - **Cuando** se presenta la liquidación
   - **Entonces** el sistema muestra exclusivamente el valor de hospedaje (`accommodationTotalAmount`), comisión OTA e ingreso neto (`netIncomeAmount`). No se calcula ni se muestra IVA, no se muestra total ni número de factura (se indican como no aplicables o pendientes de facturación)

---

### Historia de Usuario 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Prioridad: P2)

Como Recepcionista, quiero que si Módulo 3 no responde o devuelve error, el sistema informe la contingencia de forma controlada sin emitir errores técnicos.

**Escenarios de Aceptación**:

1. **Escenario**: Timeout (sin respuesta en 3 s)
   - **Dado** que M3 no responde dentro del tiempo límite
   - **Entonces** el caso de uso entrega a "Registrar Check-Out" un resultado `UNAVAILABLE` con causa `TIMEOUT`

2. **Escenario**: Error 404 devuelto por M3 (`RESERVATION_NOT_FOUND` o `QUOTE_NOT_FOUND`)
   - **Dado** que M3 responde con HTTP 404 y `errorCode` conocido
   - **Entonces** el sistema mapea a `UNAVAILABLE` con la causa del `errorCode` específico de M3

3. **Escenario**: Error `MODULE2_UNAVAILABLE` devuelto por M3
   - **Dado** que M3 responde con `errorCode = MODULE2_UNAVAILABLE`
   - **Entonces** el sistema mapea a `UNAVAILABLE` con esa causa; no se trata como error 503 (M3 no emite 503)

4. **Escenario**: Delegación de contingencia en "Registrar Check-Out"
   - **Dado** un resultado `UNAVAILABLE`
   - **Entonces** "Consultar liquidación" no libera la habitación ni persiste saldos; la decisión es de "Registrar Check-Out"

---

### Casos Borde

- **`source` DIRECTA**: Comisión 0%, valor $0, ingreso neto = hospedaje.
- **`source` OTA**: Se muestra desde `Stay.source` (M3 no lo devuelve en la respuesta).
- **`invoiceNumber` como entero**: Número consecutivo entero (sin prefijos como "F-2026-" ni "FAC-").
- **`totalAmount` de M3**: Módulo 1 **nunca** suma `accommodationTotalAmount + taxAmount` localmente; usa el `totalAmount` de M3 tal cual.
- **`categoryRoom` como parámetro**: Confirmado — Módulo 3 acepta `categoryRoom` (resuelto y cerrado; no se usa `roomType`).
- **Consultas reiteradas**: Seguras sin duplicar registros ni persistir saldos antes de la confirmación final.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como caso de uso interno incluido (`<<includes>>`) por "Registrar Check-Out" durante la fase de liquidación.
- **FR-002**: El sistema DEBE consultar mediante REST GET la URL `GET /api/settlements` (sin prefijo `/v1/`, sin header `Authorization`) con los siguientes query params:
  - `reservationRef` (string, formato `RES-000123`)
  - `checkInDate` (YYYY-MM-DD)
  - `checkOutDate` (YYYY-MM-DD)
  - `source` (`DIRECTA` o nombre de la OTA, obtenido de `Stay.source`)
  - `roomId` (UUID)
  - `categoryRoom` (string, nombre confirmado y aceptado por M3)
  
  Parámetros que **NO** se envían: `eventType`, `startDate`, `endDate`.
  
- **FR-003**: El sistema DEBE recibir desde M3 la estructura `SettlementSummary` con:
  1. `invoiceNumber` (entero consecutivo, null si liquidación informativa)
  2. `accommodationTotalAmount` (decimal)
  3. `otaCommissionPercentage` (decimal)
  4. `otaCommissionAmount` (decimal)
  5. `taxAmount` (decimal, null si informativa)
  6. `netIncomeAmount` (decimal — fórmula confirmada: hospedaje − comisión OTA)
  7. `totalAmount` (decimal, null si informativa — lo calcula M3; Módulo 1 **no** lo recalcula)
  8. `settlementType` (string: `"INFORMATIVA"` | `"FINAL"`, nombre y valores confirmados por Módulo 3)
  
  *Nota*: `source` NO regresa en la respuesta de M3; Módulo 1 lo recupera de `Stay.source`.

- **FR-004**: El sistema DEBE proveer la información para su presentación en dos etapas:
  - *Paso 2 (Liquidación)*:
    - `settlementType` mostrado como "Liquidación informativa" o "Liquidación final"
    - `invoiceNumber` (entero consecutivo; si es informativa y null, no se muestra número o se indica como "Pendiente de facturación")
    - Fuente (desde `Stay.source` local)
    - `otaCommissionPercentage`, `otaCommissionAmount`, `netIncomeAmount`
  - *Paso 3 (Pago)*:
    - En liquidación FINAL: `invoiceNumber`, `accommodationTotalAmount`, `taxAmount`, `totalAmount` de M3 (Módulo 1 **no lo suma localmente**), noches, `checkInDate`, `checkOutDate`.
    - En liquidación INFORMATIVA: Muestra únicamente el subtotal de hospedaje (`accommodationTotalAmount`), comisión OTA e ingreso neto (`netIncomeAmount`). No incluye IVA, total a pagar ni número de factura (se indican como no aplicables / pendientes de facturación).

- **FR-005**: El sistema DEBE mantener los valores como datos de consulta sin procesar pagos ni modificar las cifras de M3.
- **FR-006**: Este caso de uso NO DEBE liberar físicamente la habitación ni alterar su estado ante fallas de M3; delega la gestión de contingencia en "Registrar Check-Out".

### Manejo de Errores de M3

- M3 usa su propio `ApiError` con campo `errorCode`.
- Códigos conocidos: `RESERVATION_NOT_FOUND`, `QUOTE_NOT_FOUND`, `MODULE2_UNAVAILABLE`.
- M3 **no** devuelve HTTP 503.
- Mapeo:
  - HTTP 404 con `errorCode` conocido → `UNAVAILABLE` con causa = `errorCode`.
  - Timeout (sin respuesta en 3 s) → `UNAVAILABLE` con causa = `TIMEOUT`.
  - Error de red / conexión → `UNAVAILABLE` con causa = `CONNECTION_ERROR`.

---

### Entidades Clave

- **SettlementRequest**: Parámetros enviados a M3: `reservationRef`, `checkInDate`, `checkOutDate`, `source` (de `Stay.source`), `roomId`, `categoryRoom`.
- **SettlementSummary**: Estructura conceptual informativa devuelta por Módulo 3 conteniendo: valor de hospedaje consolidado (`accommodationTotalAmount`), porcentaje de comisión OTA (`otaCommissionPercentage`), valor de comisión OTA (`otaCommissionAmount`), IVA (`taxAmount`, null si informativa), ingreso neto (`netIncomeAmount`: hospedaje − comisión OTA), total a pagar (`totalAmount`, null si informativa, calculado por M3), número de factura oficial (`invoiceNumber`: entero consecutivo, null si informativa) y tipo de liquidación (`settlementType`: INFORMATIVA o FINAL). No incluye `source`.
- **Stay**: Fuente de `source`, `checkInDate`, `checkOutDate`, `reservationRef`, `roomId`.
- **Module3**: Sistema externo responsable del cálculo, comisiones, IVA y generación de factura oficial. Consume notificaciones de Check-Out de forma asíncrona. No recibe ni espera datos de huéspedes en peticiones directas.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las consultas exitosas suministran los datos estructurados para los pasos de Liquidación y Pago sin alterar ni recalcular los montos de M3.
- **SC-002**: La consulta responde en menos de 3 s. Pasado ese tiempo, el caso de uso devuelve `UNAVAILABLE` con causa `TIMEOUT`.
- **SC-003**: Cero excepciones técnicas propagadas al Recepcionista; el 100% de las fallas se traducen en resultado controlado `UNAVAILABLE`.
- **SC-004**: Cero cálculos locales de `totalAmount` (siempre de M3) y cero recaudos ejecutados en Módulo 1.
- **SC-005**: El `invoiceNumber` siempre se trata como entero (sin prefijos de formato); si es null, se muestra "Pendiente de facturación".
