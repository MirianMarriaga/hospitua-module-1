
### 1. Proyecto: HOSPITUA

# HOSPITUA: Plataforma de Gestión Hotelera (SaaS)

Este documento define la arquitectura técnica para un sistema de gestión hotelera por suscripción, integrando el control de inventario físico, la admisión y salida de huéspedes, el cumplimiento legal migratorio y la liquidación financiera con intermediarios.

---

### 🏨 Módulo 1: Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out
Este módulo digitaliza la infraestructura física del hotel, controla la disponibilidad en tiempo real y gestiona directamente la admisión (Check-In) y salida (Check-Out) física de los huéspedes, funcionando como la base de datos central para evitar reservas duplicadas y gestionar el mantenimiento.

#### 1.1. Atributos de la Habitación (Entidad)
Cada unidad habitacional debe estar indexada con los siguientes metadatos:
*   **Identificación:** ID Único (UUID), Número de habitación, Piso.
*   **Categorización:** Tipo (Sencilla, Doble, Suite, Boutique), Capacidad máxima de personas.
*   **Tarifa base:** Tarifa base.

#### 1.2. Ciclo de Vida y Estados de la Habitación
El sistema gestiona la transición de estados para garantizar la integridad operativa. El
nombre canónico de cada estado (usado en SPECs y código) va en inglés; el nombre en
español entre paréntesis es de referencia para los diagramas del equipo:
1.  **Available** (Disponible): Lista para check-in o para pasar a mantenimiento.
2.  **Reserved** (Reservada): Existe una reserva asociada en Módulo 2, aún no ocupada físicamente. La marca Módulo 2 mediante evento (no polling); el Check-In puede finalizar tanto desde `Available` como desde `Reserved`.
3.  **Occupied** (Ocupada): Huésped en sitio (Check-in realizado).
4.  **PendingCleaning** (Pendiente de limpieza): Liberada tras el Check-out o tras una intervención de mantenimiento finalizada, en espera de ser tomada por el Personal de limpieza.
5.  **InCleaning** (En Limpieza): El Personal de limpieza está realizando el aseo.
6.  **DisabledForRepairs** (Inhabilitada por reparaciones): Fuera de servicio por daños físicos.
7.  **TechnicalBlock** (Bloqueo Técnico): Reservada para mantenimiento preventivo.
8.  **Inactive** (Inactiva): Dada de baja del inventario operativo por el Administrador.

> Ver documentos/SPEC/referencias/maquina-estados-habitacion.md para la tabla completa de transiciones entre estados.

#### 1.3. Check-In y Check-Out
El Recepcionista opera ambos eventos directamente en Módulo 1:
*   **Check-In:** Transiciona la habitación de `Available` o `Reserved` a `Occupied`, de forma interna y síncrona. No dispara ninguna llamada de liquidación. Notifica de forma asíncrona a Módulo 2 para actualizar la reserva a `CHECKED_IN`.
*   **Check-Out:** Transiciona la habitación de `Occupied` a `PendingCleaning`, de forma interna y síncrona. Envía los datos de la estancia (fechas reservadas y reales, categoría de habitación) para obtener la liquidación final, y notifica de forma asíncrona a Módulo 2 para actualizar la reserva a `CHECKED_OUT`.
*   Ningún fallo de integración con Módulo 2 o Módulo 3 bloquea el cierre físico de ninguno de los dos eventos: se marca `PENDING` localmente y se reintenta en segundo plano.

---

### 📋 Módulo 2: Operación de Reservas y Cumplimiento Legal
Este módulo gestiona el ciclo de vida de la reserva y el cumplimiento migratorio, integrando con los canales externos de venta.

#### 2.1. Gestión de Origen de Reserva (Lógica de Canal)
El sistema identifica de dónde proviene la reserva para calcular costos operativos:
*   **Canal Directo:** Recepción, teléfono o portal propio (0% comisión).
*   **OTAs (Booking, Airbnb, Expedia):** Requiere registro de código de confirmación externo y porcentaje de comisión pactado.

#### 2.2. Validación Migratoria (Módulo SIRE)
Para cumplir con la legislación, el sistema valida datos obligatorios capturados durante el Check-In:
*   **Origen del dato:** Módulo 1 captura la información durante el Check-In y se la envía a Módulo 2; no es una validación independiente ni redundante.
*   **Campos Requeridos:** Documento de identidad, nacionalidad, tipo de visa y fechas de estancia.
*   **Generación de Archivo:** Exportación automática de un archivo plano **(.TXT)** con la estructura exigida por las autoridades de migración.

---

### 💰 Módulo 3: Facturación, Consumos y Liquidación
Este módulo traduce la estancia en datos financieros, consolidando el hospedaje y deduciendo las comisiones de terceros.

#### 3.1. Reglas de Negocio para el Cobro
*   **Tarifas Dinámicas:** Aplicación automática de precios según el calendario de "Temporada Alta".
*   **Salida anticipada:** Cuando el Check-Out ocurre antes del `endDate` originalmente reservado, se cobran únicamente las noches efectivamente hospedadas, más una penalización asociada. El cálculo exacto de esa penalización es responsabilidad interna de Módulo 3.
*   **Liquidación única al Check-Out:** La liquidación se genera una sola vez, disparada exclusivamente por el evento de Check-Out de Módulo 1. No hay etapa previa ni estados intermedios; el IVA aplicado es el vigente en ese momento.

#### 3.2. Matriz de Liquidación Final (Ingreso Neto)
| Concepto de Cobro | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Hospedaje Base** | Tarifa (según temporada) x Noches efectivamente hospedadas | Ingreso principal. Si hay salida anticipada, se calcula sobre noches reales, no las reservadas. |
| **Comisión OTA** | - (Valor Hospedaje * % Comisión) | Se descuenta si proviene de un intermediario. |
| **Penalización por salida anticipada** | Según política interna de Módulo 3 | Aplica solo cuando el Check-Out ocurre antes del `endDate` reservado. |
| **Impuestos (IVA)** | % Según legislación local | Aplicado al total de servicios prestados. |
```

***
