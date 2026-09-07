
### 1. Proyecto: HOSPITUA

```markdown
# HOSPITUA: Plataforma de Gestión Hotelera (SaaS)

Este documento define la arquitectura técnica para un sistema de gestión hotelera por suscripción, integrando el control de inventario físico, el cumplimiento legal migratorio y la liquidación financiera con intermediarios.

---

### 🏨 Módulo 1: Gestión de Habitaciones e Inventario de Aforo
Este módulo digitaliza la infraestructura física del hotel y controla la disponibilidad en tiempo real, funcionando como la base de datos central para evitar reservas duplicadas y gestionar el mantenimiento.

#### 1.1. Atributos de la Habitación (Entidad)
Cada unidad habitacional debe estar indexada con los siguientes metadatos:
*   **Identificación:** ID Único (UUID), Número de habitación, Piso/Ala.
*   **Categorización:** Tipo (Sencilla, Doble, Suite, Boutique), Capacidad máxima de personas.
*   **Tarifa base:** Tarifa base, Estado de minibar (stock actual), Servicios incluidos.

#### 1.2. Ciclo de Vida y Estados de la Habitación
El sistema gestiona la transición de estados para garantizar la integridad operativa. El
nombre canónico de cada estado (usado en SPECs y código) va en inglés; el nombre en
español entre paréntesis es de referencia para los diagramas del equipo:
1.  **Available** (Disponible): Lista para check-in o para pasar a mantenimiento.
2.  **Occupied** (Ocupada): Huésped en sitio (Check-in realizado).
3.  **PendingCleaning** (Pendiente de limpieza): Liberada tras el Check-out o tras una intervención de mantenimiento finalizada, en espera de ser tomada por el Personal de limpieza.
4.  **InCleaning** (En Limpieza): El Personal de limpieza está realizando el aseo.
5.  **DisabledForRepairs** (Inhabilitada por reparaciones): Fuera de servicio por daños físicos.
6.  **TechnicalBlock** (Bloqueo Técnico): Reservada para mantenimiento preventivo.
7.  **Inactive** (Inactiva): Dada de baja del inventario operativo por el Administrador.

> Ver documentos/SPEC/referencias/maquina-estados-habitacion.md para la tabla completa de transiciones entre estados.

---

### 📋 Módulo 2: Operación de Reservas y Cumplimiento Legal
Este módulo es el núcleo operativo que procesa la entrada y salida de personas, gestionando la lógica de negocio para el registro y la integración con canales externos.

#### 2.1. Gestión de Origen de Reserva (Lógica de Canal)
El sistema identifica de dónde proviene la reserva para calcular costos operativos:
*   **Canal Directo:** Recepción, teléfono o portal propio (0% comisión).
*   **OTAs (Booking, Airbnb, Expedia):** Requiere registro de código de confirmación externo y porcentaje de comisión pactado.

#### 2.2. Validación Migratoria (Módulo SIRE)
Para cumplir con la legislación, el sistema debe validar datos obligatorios antes del Check-in:
*   **Campos Requeridos:** Documento de identidad, nacionalidad, tipo de visa y fechas de estancia.
*   **Generación de Archivo:** Exportación automática de un archivo plano **(.TXT)** con la estructura exigida por las autoridades de migración.

---

### 💰 Módulo 3: Facturación, Consumos y Liquidación
Este módulo traduce la estancia en datos financieros, consolidando el hospedaje con servicios adicionales y deduciendo las comisiones de terceros.

#### 3.1. Reglas de Negocio para el Cobro
*   **Tarifas Dinámicas:** Aplicación automática de precios según el calendario de "Temporada Alta".
*   **Control de Minibar:** Los consumos registrados se cargan automáticamente a la cuenta de la habitación y se descuentan del inventario.

#### 3.2. Matriz de Liquidación Final (Ingreso Neto)
| Concepto de Cobro | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Hospedaje Base** | Tarifa (según temporada) x Noches | Ingreso principal. |
| **Comisión OTA** | - (Valor Hospedaje * % Comisión) | Se descuenta si proviene de un intermediario. |
| **Consumos Extra** | Sumatoria de productos de minibar | Basado en el inventario descontado. |
| **Impuestos (IVA)** | % Según legislación local | Aplicado al total de servicios prestados. |
```

***
