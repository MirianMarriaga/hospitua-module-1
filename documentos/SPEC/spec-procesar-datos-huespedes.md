# Especificación de Funcionalidad: Procesar Datos de Huéspedes

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Captura de datos de ocupantes y titular fijo (Prioridad: P1)

Como Recepcionista, quiero capturar durante el Check-In la información de identidad de los ocupantes presentes manteniendo fijo y precargado al Huésped Titular, para registrar formalmente a las personas que ocuparán la habitación y asegurar la correspondencia contractual de la estadía.

**Por qué esta prioridad**: Es el caso de uso interno incluido por "Registrar Check-In" durante la admisión de huéspedes. Permite individualizar a las personas físicas que pernoctarán en el hotel y garantiza que el titular responsable de la reserva no sea alterado ni removido durante la recepción.

**Prueba Independiente**: Se puede probar de forma aislada invocando la captura de datos de huéspedes con una reserva activa y una habitación con capacidad asignada (ej. capacidad para 2 personas y `guestCount = 2`). Se verifica que el Huésped Titular figure como precargado, de solo lectura e inamovible (con `isReservationGuest = true`), y que sea posible ingresar a un acompañante registrando los 4 datos obligatorios: Nombre completo, Tipo de documento, Número de documento y Nacionalidad (mediante catálogo de países).

**Escenarios de Aceptación**:

1. **Escenario**: Captura exitosa de Huésped Titular y acompañante coincidiendo con `guestCount` (Happy Path)
   - **Dado** una habitación con capacidad para 2 personas y una reserva con `guestCount = 2` cuyo Huésped Titular fue precargado mediante `guestRef`
   - **Cuando** el Recepcionista registra para un segundo ocupante los datos: Nombre completo, Tipo de documento, Número de documento y Nacionalidad
   - **Entonces** el sistema valida que todos los campos requeridos estén completos, que el total de ocupantes sea exactamente 2 (coincidiendo con `guestCount`), vincula a ambos ocupantes como `RoomGuest` de la estadía (asignando `isReservationGuest = true` al titular y `isReservationGuest = false` al acompañante) y autoriza continuar con el Check-In.

2. **Escenario**: Titular de la reserva fijo, precargado y no modificable
   - **Dado** una reserva activa obtenida previamente desde "Consultar reservas"
   - **Cuando** el Recepcionista accede a la pantalla de captura de huéspedes
   - **Entonces** el sistema presenta los datos del titular (Nombre completo, Tipo de documento, Número de documento y Nacionalidad) en campos de solo lectura, con el selector de nacionalidad deshabilitado y sin ícono de eliminar, permitiendo únicamente gestionar acompañantes adicionales.

3. **Escenario**: Captura para habitación y reserva de ocupación individual (`guestCount = 1`)
   - **Dado** una habitación asignada a una reserva con `guestCount = 1`
   - **Cuando** el Recepcionista procesa los datos de los ocupantes
   - **Entonces** el sistema restringe el registro únicamente al Huésped Titular precargado, deshabilita el botón de agregar acompañantes e informa que se ha completado la cantidad de huéspedes contratada en la reserva, permitiendo continuar tras verificar los datos del titular.

---

### Historia de Usuario 2 - Coincidencia con `guestCount`, control de capacidad física y notificación SIRE (Prioridad: P2)

Como Recepcionista, quiero que el sistema valide que la cantidad de personas registradas coincida exactamente con la cantidad de la reserva (`guestCount`), no exceda la capacidad máxima física de la habitación y detecte automáticamente a los ocupantes con nacionalidad distinta de Colombia mostrando la alerta de envío a Módulo 2 (SIRE), para evitar discrepancias contractuales, sobrecupos físicos y cumplir con la legislación migratoria.

**Por qué esta prioridad**: Previene sobreocupación en las unidades habitacionales físicas del hotel, garantiza la correspondencia estricta con la cantidad contratada en la reserva de Módulo 2 y asegura que la presencia de ocupantes extranjeros active de forma transparente el cumplimiento migratorio sin sobrecargar al recepcionista con formularios adicionales.

**Prueba Independiente**: Se prueba intentando avanzar con menos o más ocupantes de los definidos en `guestCount`, comprobando el bloqueo en ambos casos; intentando exceder la capacidad máxima (`maxCapacity`) de la habitación; y seleccionando una nacionalidad distinta de Colombia, comprobando que se despliegue de inmediato la alerta de envío a Módulo 2 (SIRE).

**Escenarios de Aceptación**:

1. **Escenario**: Detección de nacionalidad extranjera y despliegue de aviso SIRE
   - **Dado** una reserva donde se registra para cualquiera de los ocupantes una nacionalidad distinta de Colombia
   - **Cuando** el Recepcionista selecciona dicha nacionalidad en la lista desplegable
   - **Entonces** el sistema despliega de inmediato en pantalla la alerta informativa indicando que los datos migratorios se enviarán automáticamente a Módulo 2 para validación migratoria (SIRE), habilita los campos adicionales `originPlace` (procedencia) y `destinationPlace` (destino) requeridos por el SIRE (acuerdo B15) para ese ocupante, y activa la extensión "Enviar datos de huéspedes extranjeros" (`<<extend>>`) para asociar automáticamente el tipo de movimiento (`ENTRY`) y la fecha (`checkInDate`) al confirmar el Check-In.

2. **Escenario**: Bloqueo por discrepancia con la cantidad de huéspedes registrada en la reserva (`guestCount`)
   - **Dado** una reserva con `guestCount = 2`
   - **Cuando** el Recepcionista intenta avanzar registrando únicamente al titular (1 persona) o agregando acompañantes por encima de 2
   - **Entonces** el sistema bloquea el avance e informa que la cantidad de personas que hacen check-in debe coincidir exactamente con la cantidad registrada en la reserva (`guestCount`).

3. **Escenario**: Deshabilitación de botón y mensaje por alcanzar la capacidad máxima
   - **Dado** una habitación con capacidad máxima de 2 personas donde ya figuran registrados el titular y un acompañante
   - **Cuando** el número de ocupantes alcanza la capacidad física de la habitación
   - **Entonces** el sistema deshabilita el botón "Agregar huésped" y muestra el mensaje indicando que se alcanzó la capacidad máxima de la habitación (2 personas), permitiendo únicamente eliminar al acompañante mediante el ícono de papelera.

4. **Escenario**: Bloqueo por campos obligatorios incompletos
   - **Dado** que se añade un acompañante pero se omite diligenciar el Nombre completo, Tipo de documento, Número de documento o Nacionalidad
   - **Cuando** el Recepcionista intenta continuar hacia el resumen de confirmación
   - **Entonces** el sistema resalta los campos faltantes y no permite avanzar hasta completar todos los datos obligatorios.

---

### Casos Borde

- **Documento duplicado entre ocupantes de la misma habitación**: Si se ingresa para un acompañante el mismo tipo y número de documento del Huésped Titular, el sistema alerta sobre duplicidad de identidad en la misma unidad e impide continuar.
- **Incompatibilidad entre expectativa de reserva y aforo físico de la habitación**: Si la reserva externa indicaba una expectativa de personas superior a la capacidad máxima (`maxCapacity`) de la habitación física en Módulo 1, prevalece estrictamente el aforo físico de Módulo 1, impidiendo agregar personas por encima del límite físico.
- **Caracteres con tildes, diéresis y nombres compuestos**: El campo "Nombre completo" admite caracteres alfabéticos internacionales, apóstrofes, espacios y guiones sin alterar el texto.
- **Nacionalidades múltiples o con doble ciudadanía**: Se solicita seleccionar el país correspondiente al documento de identidad presentado físicamente en el mostrador para efectos del reporte de control migratorio.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) por "Registrar Check-In", ejecutándose durante la etapa de captura de ocupantes.
- **FR-002**: El sistema DEBE recibir la reserva validada y la habitación asignada desde "Consultar reservas", precargando los datos del Huésped Titular a partir de `guestRef` y marcándolo internamente con `isReservationGuest = true`.
- **FR-003**: El sistema DEBE capturar exactamente los mismos 4 campos obligatorios tanto para el Huésped Titular como para los acompañantes:
  1. Nombre completo
  2. Tipo de documento
  3. Número de documento
  4. Nacionalidad (seleccionada desde un catálogo general de países)

  Para ocupantes con nacionalidad distinta de Colombia, el formulario DEBE habilitar además los dos campos adicionales exigidos por el SIRE (acuerdo B15):
  5. Procedencia (`originPlace`)
  6. Destino (`destinationPlace`)
- **FR-004**: Los datos del Huésped Titular DEBEN permanecer fijos, en modo de solo lectura (incluyendo la lista desplegable de nacionalidad deshabilitada) y sin ícono de eliminar, permitiendo al Recepcionista únicamente registrar, modificar o remover a los acompañantes.
- **FR-005**: El sistema DEBE validar de forma obligatoria que la cantidad total de personas registradas (titular más acompañantes) coincida exactamente con la cantidad de huéspedes registrada en la reserva (`guestCount`), y no exceda la capacidad máxima (`maxCapacity`) de la habitación asignada. Al alcanzar la capacidad máxima de la habitación, el sistema DEBE deshabilitar el botón "+ Agregar huésped" y presentar la nota informativa "Se alcanzó la capacidad máxima de la habitación (N personas)". Si la cantidad de personas registradas difiere de `guestCount`, el sistema DEBE bloquear la confirmación e informar la discrepancia al Recepcionista.
- **FR-006**: Si la nacionalidad de cualquiera de los ocupantes es distinta de Colombia (`nationality !== 'Colombia'`), el sistema DEBE:
  1. Mostrar de inmediato la alerta visual informativa indicando que los datos migratorios se enviarán automáticamente a Módulo 2 (SIRE). Si la nacionalidad es Colombia, dicha alerta NO debe mostrarse.
  2. Habilitar en el formulario los campos `originPlace` (procedencia) y `destinationPlace` (destino) para ese ocupante, requeridos por el SIRE (acuerdo B15). Estos dos campos son obligatorios para extranjeros; el sistema DEBE validar que no estén vacíos antes de permitir avanzar.
  3. Activar la extensión al caso de uso "Enviar datos de huéspedes extranjeros" (`<<extend>>`).
- **FR-007**: El sistema NO DEBE solicitar al Recepcionista parámetros de control migratorio como tipo de movimiento ni fecha de movimiento, delegando su asignación automática al caso de uso extendido.
- **FR-008**: Al completar la validación de los datos ingresados, el sistema DEBE persistir a los ocupantes como registros inmutables `RoomGuest` vinculados a la `Estancia`, almacenando obligatoriamente el atributo `isReservationGuest` (`true` para el titular y `false` para los acompañantes).
- **FR-009**: Este caso de uso NO DEBE validar fechas de estadía, vigencia contractual de la reserva ni liquidaciones financieras, responsabilidades delegadas en otros pasos del flujo de Check-In.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `fullName`, `documentType`, `documentNumber`, `nationality` e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura efímera de control migratorio con los diez campos exigidos por el SIRE (acuerdo B15). Los campos `originPlace` y `destinationPlace` se capturan en este caso de uso; `movementType` (`ENTRY`) y `movementDate` (`checkInDate`) los asigna automáticamente el caso de uso "Enviar datos de huéspedes extranjeros".
- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas, tarifa base y estado actual (uno de los 8 estados del ciclo de vida: Available, Reserved, Occupied, PendingCleaning, InCleaning, DisabledForRepairs, TechnicalBlock, Inactive).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: "Directo" u "OTA"), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva (`expectedCheckinTime`, `expectedCheckoutTime` — fechas sin hora), recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción, consultas, registro de check-in y registro de check-out en el hotel.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede capturar y validar los datos de los ocupantes en menos de 1 minuto para un grupo de hasta 2 personas.
- **SC-002**: El 100% de los intentos de Check-In donde la cantidad de ocupantes difiera de `guestCount` son bloqueados por el sistema informando la discrepancia.
- **SC-003**: El 100% de los registros con nacionalidad distinta de Colombia muestran oportunamente la alerta de envío automático a Módulo 2 (SIRE).
- **SC-004**: El 100% de los intentos de modificar o eliminar la identidad del Huésped Titular son prevenidos manteniendo sus campos como de solo lectura.
