# Especificación de Funcionalidad: Consultar Liquidación

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista (indirecto, vía `<<includes>>` desde "Registrar Check-Out")
**Creado**: 2026-09-25
**Actualizado**: 2026-10-26

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del desglose de liquidación en Check-Out (Prioridad: P1)

Como Recepcionista, quiero que durante el Check-Out el sistema consulte de forma reactiva mediante REST GET a Módulo 3 la liquidación de la estancia enviando `reservationRef` y `roomId`, para estructurar la información en los pasos de Liquidación (tipo de liquidación, fuente local desde `Stay.source`, comisión e ingreso neto) y Pago (hospedaje y datos de estadía), sin que Módulo 3 devuelva el campo `source`.

**Por qué esta prioridad**: Es la consulta obligatoria de "Registrar Check-Out" que traduce la estancia en información financiera oficial. Módulo 1 actúa como consumidor estricto: no calcula tarifas, no deduce comisiones, no recauda pagos. Todos los montos provienen exclusivamente de M3 (nunca se calculan localmente en M1).

**Prueba Independiente**: Invocar con datos válidos de estancia (`reservationRef` de `Stay` y `roomId`) y verificar que el sistema envía solo esos dos query params sobre la URL `GET /api/settlements` (sin `/v1/`) y retorna la estructura de M3 desagregada para los pasos de Liquidación y Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta de liquidación para reserva DIRECTA (sin comisión OTA)
   - **Dado** una estancia con `source = DIRECTA` en `Stay.source`
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `GET /api/settlements` con `reservationRef` y `roomId` y recibe de M3 una liquidación con `channel = DIRECT`:
     - Para el paso Liquidación: `settlementType` ("Liquidación informativa"), fuente desplegada desde `Stay.source` local, comisión "No aplica" (`otaCommissionPercentage = null`, `otaCommissionAmount = "0.00"`), ingreso neto = hospedaje
     - Para el paso Pago: `lodgingAmount` de M3, noches, `checkInDate`, `checkOutDate`

2. **Escenario**: Consulta de liquidación para reserva OTA (con comisión)
   - **Dado** una estancia con `source = BOOKING` o `EXPEDIA` en `Stay.source`
   - **Cuando** "Registrar Check-Out" invoca "Consultar liquidación"
   - **Entonces** el sistema envía `reservationRef` y `roomId` al endpoint de M3 y recibe la liquidación con `channel = OTA` (sin `source` en la respuesta). Muestra en el paso Liquidación: fuente desde `Stay.source`, `otaCommissionPercentage`, `otaCommissionAmount`, `netIncome`

3. **Escenario**: Valor de hospedaje consolidado (sin desglose por noche)
   - **Dado** una estancia de múltiples noches o con salida anticipada
   - **Cuando** se consulta la liquidación
   - **Entonces** el sistema recibe `lodgingAmount` como cifra total ya calculada por M3 con lo cotizado (una salida anticipada se cobra por lo cotizado, sin reducción); Módulo 1 no desglosa por noche

4. **Escenario**: Liquidación informativa mostrada como valor exacto
   - **Dado** que durante el check-out M3 devuelve siempre `settlementType = INFORMATIVE` con `invoice = null` (genera la FINAL y la factura al procesar el evento de check-out, que se publica después)
   - **Cuando** se presenta la liquidación
   - **Entonces** el sistema muestra el valor de hospedaje (`lodgingAmount`), la comisión OTA y el ingreso neto (`netIncome`) como valores exactos, porque M3 calcula siempre con lo cotizado y la informativa coincide con la final. No se muestra IVA, total ni número de factura; se indica "La factura se emite al confirmar la salida"

---

### Historia de Usuario 2 - Manejo controlado de la indisponibilidad de Módulo 3 (Prioridad: P2)

Como Recepcionista, quiero que si Módulo 3 no responde o devuelve error, el sistema informe la contingencia de forma controlada sin emitir errores técnicos.

**Por qué esta prioridad**: La liquidación depende de un módulo externo; si su falla llegara como error técnico, el Recepcionista no sabría cómo continuar y el huésped quedaría retenido. Traducir toda falla a un resultado controlado deja la decisión a *Registrar Check-Out*, que permite liberar la habitación igualmente.

**Prueba Independiente**: Simular contra Módulo 3 un timeout, una respuesta con cuerpo `ApiError`, una respuesta sin cuerpo y un error de conexión, y verificar que el caso de uso devuelve `UNAVAILABLE` con la causa correspondiente, sin lanzar excepciones al llamador ni modificar `Stay` ni `Room`.

**Escenarios de Aceptación**:

5. **Escenario**: Timeout (sin respuesta en 3 s)
   - **Dado** que M3 no responde dentro del tiempo límite
   - **Entonces** el caso de uso entrega a "Registrar Check-Out" un resultado `UNAVAILABLE` con causa `TIMEOUT`

6. **Escenario**: Error con cuerpo `ApiError` devuelto por M3 (por ejemplo 404 `RESERVATION_NOT_FOUND` o `QUOTE_NOT_FOUND`)
   - **Dado** que M3 responde con un HTTP no 2xx (400, 404, 409 o 422) y cuerpo `ApiError` con `errorCode`
   - **Entonces** el sistema mapea a `UNAVAILABLE` con la causa del `errorCode` específico de M3; si la respuesta no trae cuerpo `ApiError`, la causa es `HTTP_ERROR`

7. **Escenario**: Error `MODULE2_UNAVAILABLE` (HTTP 424) devuelto por M3
   - **Dado** que M3 responde con HTTP 424 y `errorCode = MODULE2_UNAVAILABLE`
   - **Entonces** el sistema mapea a `UNAVAILABLE` con esa causa; no se trata como 404 ni como 503 (M3 no emite 503)

8. **Escenario**: Error de conexión con Módulo 3
   - **Dado** que no se puede establecer la conexión con M3 (servicio caído o red interrumpida)
   - **Entonces** el caso de uso entrega a "Registrar Check-Out" un resultado `UNAVAILABLE` con causa `CONNECTION_ERROR`

9. **Escenario**: Delegación de contingencia en "Registrar Check-Out"
   - **Dado** un resultado `UNAVAILABLE`
   - **Entonces** "Consultar liquidación" no libera la habitación ni persiste saldos; la decisión es de "Registrar Check-Out"

---

### Casos Borde

- **`source` DIRECTA**: M3 responde `channel = DIRECT`; comisión "No aplica" (`otaCommissionPercentage = null`), valor $0 (`otaCommissionAmount = "0.00"`), ingreso neto = hospedaje.
- **`source` OTA**: Se muestra desde `Stay.source` (M3 no lo devuelve en la respuesta).
- **Factura durante el check-out**: M3 aún no la ha emitido (`invoice = null`); la emite al procesar el evento de check-out.
- **Montos como texto decimal**: M3 devuelve los montos como texto decimal; Módulo 1 los convierte a `BigDecimal` sin redondear ni recalcular.
- **Consultas reiteradas**: Seguras sin duplicar registros ni persistir saldos antes de la confirmación final.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como caso de uso interno incluido (`<<includes>>`) por "Registrar Check-Out" durante la fase de liquidación.
- **FR-002**: El sistema DEBE consultar mediante REST GET la URL `GET /api/settlements` (sin prefijo `/v1/`) con los siguientes query params:
  - `reservationRef` (string, formato `RES-000123`)
  - `roomId` (UUID)

  Módulo 3 ignora cualquier otro parámetro, por lo que no se envían otros.

- **FR-003**: El sistema DEBE recibir desde M3 la liquidación y convertirla a la estructura `SettlementSummary` con:
  1. `settlementType` (string: `"INFORMATIVE"` | `"FINAL"`; durante el check-out siempre `INFORMATIVE`)
  2. `lodgingAmount` (desde `breakdown.lodgingAmount`)
  3. `otaCommissionPercentage` (desde `breakdown.otaCommissionPercentage`; null en `DIRECT`)
  4. `otaCommissionAmount` (desde `breakdown.otaCommissionAmount`; `"0.00"` en `DIRECT`)
  5. `netIncome` (desde `breakdown.netIncome` — fórmula confirmada: hospedaje − comisión OTA)

  M3 devuelve los montos como texto decimal; Módulo 1 los convierte a `BigDecimal` sin recalcularlos. Los demás campos de M3 (`reservationRef`, `roomId`, `channel`, `otaId`, `categoryRoom`, `currency`, `baseLodgingAmount`, `dynamicAdjustmentAmount`, `invoice`, `generatedAt`) no se usan.

  *Nota*: `source` NO regresa en la respuesta de M3; Módulo 1 lo recupera de `Stay.source`.

- **FR-004**: El sistema DEBE proveer la información para su presentación en dos etapas:
  - *Paso 2 (Liquidación)*:
    - `settlementType` mostrado como "Liquidación informativa", con valores exactos (coincide con la final)
    - Fuente (desde `Stay.source` local)
    - `otaCommissionPercentage` ("No aplica" en `DIRECT`), `otaCommissionAmount`, `netIncome`
  - *Paso 3 (Pago)*:
    - `lodgingAmount`, noches, `checkInDate`, `checkOutDate`.
    - No incluye IVA, total a pagar ni número de factura; se indica "La factura se emite al confirmar la salida".

- **FR-005**: El sistema DEBE mantener los valores como datos de consulta sin procesar pagos ni modificar las cifras de M3.
- **FR-006**: Este caso de uso NO DEBE liberar físicamente la habitación ni alterar su estado ante fallas de M3; delega la gestión de contingencia en "Registrar Check-Out".

### Manejo de Errores de M3

- M3 usa su propio `ApiError` con campo `errorCode`.
- Códigos conocidos (con su HTTP): 400 `INVALID_QUERY_PARAMS`, 404 `RESERVATION_NOT_FOUND`, 404 `QUOTE_NOT_FOUND`, 409 `MISSING_COMMISSION` / `INVALID_COMMISSION`, 422 `UNEXPECTED_ERROR`, 424 `MODULE2_UNAVAILABLE`.
- M3 **no** devuelve HTTP 503.
- Mapeo (regla general):
  - Cualquier respuesta no 2xx con cuerpo `ApiError` → `UNAVAILABLE` con causa = `errorCode`.
  - Respuesta no 2xx sin cuerpo `ApiError` → `UNAVAILABLE` con causa = `HTTP_ERROR`.
  - Timeout (sin respuesta en 3 s) → `UNAVAILABLE` con causa = `TIMEOUT`.
  - Error de red / conexión → `UNAVAILABLE` con causa = `CONNECTION_ERROR`.

---

### Entidades Clave

- **SettlementRequest**: Parámetros enviados a M3: `reservationRef` (de `Stay`) y `roomId`.
- **SettlementSummary**: Estructura conceptual informativa que Módulo 1 obtiene de la respuesta de Módulo 3 conteniendo: tipo de liquidación (`settlementType`: INFORMATIVE o FINAL), valor de hospedaje consolidado (`lodgingAmount`), porcentaje de comisión OTA (`otaCommissionPercentage`, null en directa), valor de comisión OTA (`otaCommissionAmount`) e ingreso neto (`netIncome`: hospedaje − comisión OTA). No incluye `source` ni datos de factura.
- **Stay**: Fuente de `source`, `checkInDate`, `checkOutDate`, `reservationRef`, `roomId`.
- **Module3**: Sistema externo responsable del cálculo, comisiones, IVA y generación de factura oficial. Consume notificaciones de Check-Out de forma asíncrona. No recibe ni espera datos de huéspedes en peticiones directas.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El 100% de las consultas exitosas suministran los datos estructurados para los pasos de Liquidación y Pago sin alterar ni recalcular los montos de M3.
- **SC-002**: La consulta responde en menos de 3 s. Pasado ese tiempo, el caso de uso devuelve `UNAVAILABLE` con causa `TIMEOUT`.
- **SC-003**: Cero excepciones técnicas propagadas al Recepcionista; el 100% de las fallas se traducen en resultado controlado `UNAVAILABLE`.
- **SC-004**: Cero cálculos locales de montos (siempre de M3) y cero recaudos ejecutados en Módulo 1.
