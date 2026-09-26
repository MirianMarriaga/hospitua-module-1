# Especificación de Funcionalidad: Procesar Datos de Huéspedes

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Captura de datos de ocupantes y titular fijo (Prioridad: P1)

Como Recepcionista, quiero capturar durante el Check-In la información de identidad de los ocupantes presentes manteniendo fijo al Huésped Titular, para registrar formalmente a las personas que ocuparán la habitación y asegurar la correspondencia contractual de la estadía.

**Por qué esta prioridad**: Es el caso de uso interno incluido por "Registrar Check-In" durante la admisión de huéspedes. Permite individualizar a las personas físicas que pernoctarán en el hotel y garantiza que el titular responsable de la reserva no sea alterado ni removido durante la recepción.

**Prueba Independiente**: Se puede probar de forma aislada invocando la captura de datos de huéspedes con una reserva activa y una habitación con capacidad asignada (ej. capacidad para 2 personas). Se verifica que el Huésped Titular figure como no modificable ni removible una vez inferido mediante `guestRef`, y que sea posible ingresar a un acompañante registrando los datos obligatorios: Nombre completo, Tipo de documento, Número de documento y Nacionalidad.

**Escenarios de Aceptación**:

1. **Escenario**: Captura exitosa de Huésped Titular y acompañante dentro de la capacidad (Happy Path)
   - **Dado** una habitación con capacidad para 2 personas y una reserva cuyo Huésped Titular ya fue identificado mediante `guestRef`
   - **Cuando** el Recepcionista registra para un segundo ocupante los datos: Nombre completo, Tipo de documento, Número de documento y Nacionalidad
   - **Entonces** el sistema valida que todos los campos requeridos estén completos, vincula conceptualmente a ambos ocupantes como `RoomGuest` de la estadía y autoriza continuar con el Check-In.

2. **Escenario**: Titular de la reserva fijo y no modificable
   - **Dado** una reserva activa obtenida previamente
   - **Cuando** el Recepcionista gestiona los datos de los ocupantes
   - **Entonces** el sistema mantiene los datos del titular como no modificables ni removibles, informando que el titular contractual de la reserva es fijo y solo se permite gestionar acompañantes adicionales.

3. **Escenario**: Captura para habitación de ocupación individual (titular único)
   - **Dado** una habitación con capacidad máxima configurada de 1 persona
   - **Cuando** el Recepcionista procesa los datos de los ocupantes
   - **Entonces** el sistema restringe el registro únicamente al Huésped Titular e impide añadir acompañantes adicionales, permitiendo continuar tras verificar los datos del titular.

---

### Historia de Usuario 2 - Control de capacidad máxima, detección de extranjeros y notificación SIRE (Prioridad: P2)

Como Recepcionista, quiero que el sistema valide que la cantidad de personas no exceda la capacidad máxima de la habitación y detecte automáticamente a los ocupantes extranjeros notificando el envío a Módulo 2 (SIRE), para evitar sobrecupos físicos y activar la notificación migratoria a Módulo 2 (SIRE).

**Por qué esta prioridad**: Previene sobreocupación en las unidades habitacionales físicas del hotel y asegura que la presencia de ocupantes extranjeros active de forma transparente el cumplimiento migratorio exigido por ley sin sobrecargar al recepcionista.

**Prueba Independiente**: Se prueba intentando registrar más ocupantes de los permitidos por la capacidad máxima (`maxCapacity`) de la habitación, comprobando el bloqueo inmediato; y registrando una nacionalidad extranjera, comprobando que el sistema informe oportunamente el envío a Módulo 2 (SIRE).

**Escenarios de Aceptación**:

1. **Escenario**: Detección de nacionalidad extranjera e información sobre envío SIRE
   - **Dado** una reserva donde se registra para cualquiera de los ocupantes una nacionalidad extranjera
   - **Cuando** el Recepcionista ingresa dicha nacionalidad
   - **Entonces** el sistema informa al Recepcionista que los datos migratorios se enviarán automáticamente a Módulo 2 (SIRE), y activa la extensión al caso de uso "Enviar datos de huéspedes extranjeros" (`<<extend>>`) para asignar automáticamente tipo de movimiento y fecha al confirmar el Check-In.

2. **Escenario**: Rechazo inmediato por superar la capacidad máxima de la habitación
   - **Dado** una habitación con capacidad máxima de 2 personas
   - **Cuando** el Recepcionista intenta registrar un tercer ocupante
   - **Entonces** el sistema bloquea la adición y notifica que se ha alcanzado la capacidad máxima física permitida para la habitación asignada.

3. **Escenario**: Bloqueo por campos obligatorios incompletos
   - **Dado** que se añade un ocupante adicional pero se omite diligenciar el Nombre completo, Tipo de documento, Número de documento o Nacionalidad
   - **Cuando** el Recepcionista intenta confirmar la lista de ocupantes
   - **Entonces** el sistema resalta los campos faltantes y no permite avanzar hasta completar todos los datos obligatorios.

---

### Casos Borde

- **Documento duplicado entre ocupantes de la misma habitación**: Si se ingresa para el Huésped 2 el mismo tipo y número de documento del Huésped Titular, el sistema alerta sobre duplicidad de identidad en la misma unidad e impide continuar.
- **Incompatibilidad entre expectativa de reserva y aforo físico de la habitación**: Si la reserva externa indicaba una expectativa de personas superior al atributo de capacidad máxima (`maxCapacity`) de la entidad Room en Módulo 1, prevalece estrictamente la capacidad física de Módulo 1, impidiendo el sobrecupo.
- **Caracteres con tildes, diéresis y nombres compuestos**: El campo "Nombre completo" admite caracteres alfabéticos internacionales, apóstrofes, espacios y guiones sin alterar el texto.
- **Nacionalidades múltiples o con doble ciudadanía**: Se solicita registrar el país correspondiente al documento de viaje o pasaporte presentado físicamente en el mostrador para efectos de control migratorio.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) por "Registrar Check-In", ejecutándose durante la etapa de admisión de ocupantes.
- **FR-002**: El sistema DEBE recibir la reserva validada y la habitación asignada desde "Consultar reservas", e inferir la identidad del titular comparando el documento contra el `guestRef` provisto por Módulo 2.
- **FR-003**: El sistema DEBE capturar los siguientes datos obligatorios para el Huésped Titular y los ocupantes adicionales:
  - Nombre completo
  - Tipo de documento
  - Número de documento
  - Nacionalidad
- **FR-004**: Los datos del Huésped Titular DEBEN permanecer fijos y no removibles, permitiendo al Recepcionista únicamente registrar, modificar o remover a los ocupantes adicionales (acompañantes).
- **FR-005**: El sistema DEBE validar de forma estricta que la cantidad total de personas registradas (titular más ocupantes adicionales) no exceda en ningún momento la capacidad máxima (`maxCapacity`) de la habitación asignada.
- **FR-006**: Si se registra una nacionalidad extranjera para cualquiera de los ocupantes, el sistema DEBE:
  1. Informar al Recepcionista que los datos migratorios se enviarán automáticamente a Módulo 2 (SIRE).
  2. Activar la extensión al caso de uso "Enviar datos de huéspedes extranjeros" (`<<extend>>`).
- **FR-007**: El sistema NO DEBE solicitar al Recepcionista parámetros de control migratorio como tipo de movimiento ni fecha de movimiento, delegando su asignación automática al caso de uso extendido.
- **FR-008**: Al completar la validación satisfactoria de los datos ingresados, el sistema DEBE registrar conceptualmente a los ocupantes como inmutables `RoomGuest` vinculados a la `Estancia`, sin persistir flags de titularidad ni datos migratorios en el esquema de datos de Módulo 1.
- **FR-009**: Este caso de uso NO DEBE validar fechas de estadía, vigencia contractual de la reserva ni liquidaciones financieras, responsabilidades delegadas en otros pasos del flujo de Check-In.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **RoomGuest**: Entidad conceptual de Módulo 1 que representa a cada persona físicamente alojada en la unidad durante la estancia. Registro inmutable vinculado a la Estancia. Atributos: Nombre completo, tipo de documento de identidad, número de documento y nacionalidad.
- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), fecha/hora de llegada real (`checkInTime`), fecha/hora de salida (`checkOutTime`), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Receptionist**: Actor que captura y verifica los datos de identidad presentados físicamente en el mostrador.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede capturar y validar los datos de los ocupantes en menos de 1 minuto para un grupo de hasta 2 personas.
- **SC-002**: El 100% de los intentos de ingresar ocupantes por encima de la capacidad máxima de la unidad son bloqueados de forma inmediata.
- **SC-003**: El 100% de los registros con nacionalidad extranjera informan oportunamente el envío automático a Módulo 2 (SIRE).
- **SC-004**: El 100% de los intentos de modificar o eliminar la identidad del Huésped Titular son prevenidos por el sistema.
