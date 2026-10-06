# Especificación de Funcionalidad: Procesar Datos de Huéspedes

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Recepcionista
**Creado**: 2026-09-25

---

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Captura de datos de ocupantes y titular fijo (Prioridad: P1)

Como Recepcionista, quiero capturar durante el Check-In la información de identidad de los ocupantes presentes mediante campos separados de nombre y apellido, manteniendo fijo y precargado al Huésped Titular desde la copia local de reservas, para registrar formalmente a las personas que ocuparán la habitación y asegurar la correspondencia contractual de la estadía.

**Por qué esta prioridad**: Es el caso de uso interno incluido por "Registrar Check-In" durante la admisión de huéspedes. Permite individualizar a las personas físicas que pernoctarán en la unidad habitacional con nombres y apellidos separados y garantiza que el titular responsable de la reserva no sea alterado ni removido durante la recepción.

**Prueba Independiente**: Se puede probar de forma aislada invocando la captura de datos de huéspedes con una reserva activa y una habitación con capacidad asignada (ej. capacidad para 2 personas y `guestCount = 2`). Se verifica que el Huésped Titular figure como precargado desde la copia local, de solo lectura e inamovible (con `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality` e `isReservationGuest = true`), y que sea posible ingresar a un acompañante registrando los 5 datos obligatorios de identidad: `firstName`, `lastName`, `documentType`, `documentNumber` y `nationality` (mediante catálogo de países).

**Escenarios de Aceptación**:

1. **Escenario**: Captura exitosa de Huésped Titular y acompañante coincidiendo con `guestCount` de la habitación (Happy Path)
   - **Dado** una habitación con capacidad para 2 personas y una reserva con `guestCount = 2` para dicha habitación cuyo Huésped Titular fue precargado desde la copia local (`daily_reservation`)
   - **Cuando** el Recepcionista registra para un segundo ocupante los datos: `firstName`, `lastName`, `documentType`, `documentNumber` y `nationality` (Colombia)
   - **Entonces** el sistema valida que todos los campos requeridos estén completos, que el total de ocupantes sea exactamente 2 (coincidiendo con `guestCount` de la habitación), vincula a ambos ocupantes como `RoomGuest` de la estadía (asignando `isReservationGuest = true` al titular y `isReservationGuest = false` al acompañante) y autoriza continuar con el Check-In.

2. **Escenario**: Titular de la reserva fijo, precargado y no modificable
   - **Dado** una reserva activa obtenida en el paso 1 desde la copia local de la lista del día
   - **Cuando** el Recepcionista accede a la pantalla de captura de huéspedes (paso 2)
   - **Entonces** el sistema presenta los datos del titular (`firstName`, `lastName`, Tipo de documento, Número de documento y Nacionalidad) en campos de solo lectura, con el selector de nacionalidad deshabilitado, `fullName` únicamente para visualización y sin ícono de eliminar, permitiendo únicamente gestionar acompañantes adicionales.

3. **Escenario**: Captura para habitación y reserva de ocupación individual (`guestCount = 1`)
   - **Dado** una habitación asignada a una reserva con `guestCount = 1`
   - **Cuando** el Recepcionista procesa los datos de los ocupantes
   - **Entonces** el sistema restringe el registro únicamente al Huésped Titular precargado, deshabilita el botón de agregar acompañantes e informa que se ha completado la cantidad de huéspedes asignada a la habitación, permitiendo continuar tras verificar los datos del titular.

---

### Historia de Usuario 2 - Coincidencia con `guestCount`, control de capacidad física y panel migratorio SIRE (Prioridad: P2)

Como Recepcionista, quiero que el sistema valide que la cantidad de personas registradas coincida exactamente con la cantidad de la habitación (`guestCount`), no exceda la capacidad máxima física de la habitación y abra el panel de datos migratorios para cada ocupante con nacionalidad distinta de Colombia solicitando obligatoriamente fecha de nacimiento, procedencia y destino de catálogo, para evitar sobrecupos físicos y cumplir con la legislación migratoria sin devoluciones.

**Por qué esta prioridad**: Previene sobreocupación en las unidades habitacionales físicas del hotel, garantiza la correspondencia estricta con la cantidad contratada para la habitación y asegura que la presencia de ocupantes extranjeros active el panel migratorio exigiendo los datos completos antes de avanzar, eliminando devoluciones externas.

**Prueba Independiente**: Se prueba intentando avanzar con menos o más ocupantes de los definidos en `guestCount` de la habitación, comprobando el bloqueo en ambos casos; intentando exceder la capacidad máxima (`maxCapacity`) de la habitación; seleccionando una nacionalidad distinta de Colombia y verificando la apertura del panel migratorio con los tres campos faltantes requeridos; y comprobando el bloqueo si se intenta dejar campos vacíos, ingresar una fecha de nacimiento no válida o un destino no perteneciente al catálogo.

**Escenarios de Aceptación**:

1. **Escenario**: Detección de nacionalidad extranjera y apertura del panel migratorio
   - **Dado** un formulario de ocupantes donde se registra o selecciona para cualquiera de los huéspedes una nacionalidad distinta de Colombia (`nationality !== 'Colombia'`)
   - **Cuando** el Recepcionista establece dicha nacionalidad en el selector
   - **Entonces** el sistema abre de inmediato el panel de datos migratorios para ese ocupante, despliega la alerta informativa de envío a Módulo 2 (SIRE), y solicita obligatoriamente: fecha de nacimiento (`birthDate`: fecha válida pasada sin hora en formato `AAAA-MM-DD`), procedencia (`originPlace`) y destino (`destinationPlace`) seleccionados desde el catálogo general de países mediante combobox con búsqueda, reutilizando los datos de identidad ya capturados (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`). Si la nacionalidad se revierte a Colombia, el panel migratorio se cierra de inmediato.

2. **Escenario**: Bloqueo por discrepancia con la cantidad de huéspedes registrada en la habitación (`guestCount`)
   - **Dado** una habitación asignada con `guestCount = 2`
   - **Cuando** el Recepcionista intenta avanzar registrando únicamente al titular (1 persona) o agregando acompañantes por encima de 2
   - **Entonces** el sistema bloquea el avance e informa que la cantidad de personas que hacen check-in debe coincidir exactamente con la cantidad asignada a la habitación (`guestCount = 2`).

3. **Escenario**: Deshabilitación de botón y mensaje por alcanzar la capacidad máxima física
   - **Dado** una habitación con capacidad máxima de 2 personas donde ya figuran registrados el titular y un acompañante
   - **Cuando** el número de ocupantes alcanza la capacidad física de la habitación
   - **Entonces** el sistema deshabilita el botón "Agregar huésped" y muestra el mensaje indicando que se alcanzó la capacidad máxima física de la habitación (`maxCapacity`: 2 personas), permitiendo únicamente eliminar al acompañante mediante el ícono de papelera.

4. **Escenario**: Bloqueo por campos obligatorios incompletos en identidad o panel migratorio
   - **Dado** que se añade un acompañante pero se omite diligenciar alguno de los campos de identidad obligatorios (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`), o en un ocupante extranjero se deja vacío alguno de los campos del panel migratorio (`birthDate`, `originPlace`, `destinationPlace`)
   - **Cuando** el Recepcionista intenta continuar hacia el resumen de confirmación
   - **Entonces** el sistema resalta los campos faltantes y no permite avanzar al paso 3 hasta completar todos los datos obligatorios.

5. **Escenario**: Rechazo ante valor de procedencia o destino no perteneciente al catálogo
   - **Dado** un ocupante extranjero en cuyo panel migratorio no se selecciona un valor existente del catálogo de países en los campos `originPlace` o `destinationPlace`
   - **Cuando** el Recepcionista intenta avanzar hacia la confirmación
   - **Entonces** el sistema no permite el avance, manteniendo el campo vacío y bloqueando el paso 3 hasta que se elija una opción válida del catálogo.

---

### Casos Borde

- **Documento duplicado entre ocupantes de la misma habitación**: Si se ingresa para un acompañante el mismo tipo y número de documento del Huésped Titular, el sistema alerta sobre duplicidad de identidad en la misma unidad e impide continuar.
- **Incompatibilidad entre expectativa de reserva y aforo físico de la habitación**: Prevalece estrictamente el aforo físico (`maxCapacity`) de Módulo 1, impidiendo registrar personas por encima del límite físico de la habitación asignada.
- **Caracteres con tildes, diéresis y nombres compuestos**: Los campos `firstName` y `lastName` admiten caracteres alfabéticos internacionales, apóstrofes, espacios y guiones sin alterar el texto.
- **Formato del valor entregado por el catálogo de países**: Como asunción de Módulo 1, los selectores de `originPlace` y `destinationPlace` envían el valor oficial entregado por el catálogo general de países; queda pendiente confirmación de Módulo 2 sobre si prefiere nombre del país o código ISO.

---

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE operar como un caso de uso interno incluido obligatoriamente (`<<includes>>`) por "Registrar Check-In", ejecutándose durante la etapa de captura de ocupantes (paso 2).
- **FR-002**: El sistema DEBE recibir los datos de la habitación y reserva desde la copia local de la lista del día (`daily_reservation` y `daily_reservation_room`), precargando los datos del Huésped Titular (`firstName`, `lastName`, `documentType`, `documentNumber` y `nationality`) y marcándolo internamente con `isReservationGuest = true`.
- **FR-003**: El sistema DEBE capturar obligatoriamente los siguientes campos de identidad con nombre y apellido separados tanto para el Huésped Titular como para los acompañantes:
  1. Primer nombre / nombres (`firstName`)
  2. Apellidos (`lastName`)
  3. Tipo de documento (`documentType`)
  4. Número de documento (`documentNumber`)
  5. Nacionalidad (`nationality`, seleccionada desde el catálogo general de países)

  Para ocupantes con nacionalidad distinta de Colombia (`nationality !== 'Colombia'`), el panel de datos migratorios DEBE exigir obligatoriamente:
  6. Fecha de nacimiento (`birthDate`: fecha válida pasada, solo fecha sin hora en formato `AAAA-MM-DD` hora Colombia)
  7. Procedencia (`originPlace`: seleccionada mediante combobox con búsqueda desde el catálogo general de países)
  8. Destino (`destinationPlace`: seleccionada mediante combobox con búsqueda desde el catálogo general de países)

  No se permite texto libre en procedencia ni destino; solo valores existentes en el catálogo.
- **FR-004**: Los datos del Huésped Titular DEBEN permanecer fijos, en modo de solo lectura (incluyendo la lista desplegable de nacionalidad deshabilitada) y sin ícono de eliminar, permitiendo al Recepcionista únicamente registrar, modificar o remover a los acompañantes.
- **FR-005**: El sistema DEBE validar de forma obligatoria que la cantidad total de personas registradas (titular más acompañantes) coincida exactamente con la cantidad de huéspedes asignada a la habitación en la reserva (`guestCount` de la habitación; pendiente de confirmación por Módulo 2: en caso de no recibirse por habitación, se valida contra la capacidad máxima `maxCapacity`), y no exceda la capacidad máxima (`maxCapacity`) de la habitación física asignada. Al alcanzar la capacidad máxima de la habitación, el sistema DEBE deshabilitar el botón "+ Agregar huésped" y presentar la nota informativa "Se alcanzó la capacidad máxima de la habitación (N personas)". Si la cantidad de personas registradas difiere de `guestCount`, el sistema DEBE bloquear el avance al paso 3.
- **FR-006**: Panel de datos migratorios para extranjeros: Si la nacionalidad de cualquiera de los ocupantes es distinta de Colombia (`nationality !== 'Colombia'`), el sistema DEBE:
  1. Abrir de inmediato el panel de datos migratorios para ese ocupante. Si la nacionalidad es Colombia, el panel no se abre (o se cierra automáticamente si se rectifica la nacionalidad a Colombia).
  2. Solicitar en el panel exclusivamente los tres campos faltantes: `birthDate`, `originPlace` y `destinationPlace`. Los datos de identidad (`firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`) se toman directamente del formulario del ocupante sin volver a solicitarlos.
  3. Exigir que los tres campos estén diligenciados y validados (`birthDate` fecha pasada válida `AAAA-MM-DD`; `originPlace` y `destinationPlace` seleccionados de catálogo) como condición obligatoria antes de autorizar el avance al paso 3 de Confirmación, resaltando los faltantes o inválidos.
  4. Activar la extensión al caso de uso "Enviar datos de huéspedes extranjeros" (`<<extend>>`).
- **FR-007**: El sistema NO DEBE solicitar al Recepcionista parámetros de control migratorio como tipo de movimiento ni fecha de movimiento, asignando automáticamente `movementType = 'ENTRY'` y `movementDate = checkInDate` a través del caso de uso extendido.
- **FR-008**: Al completar la validación de los datos ingresados, el sistema DEBE persistir a los ocupantes como registros inmutables `RoomGuest` vinculados a la `Estancia`, almacenando obligatoriamente `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, los campos migratorios si aplica (`birthDate`, `originPlace`, `destinationPlace`) y el atributo `isReservationGuest` (`true` para el titular y `false` para los acompañantes).
- **FR-009**: Este caso de uso NO DEBE validar fechas de estadía, vigencia contractual de la reserva ni liquidaciones financieras, responsabilidades delegadas en otros pasos del flujo de Check-In.

---

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **RoomGuest**: Entidad conceptual que representa a cada individuo físicamente alojado. Registro inmutable vinculado a la Estancia. Atributos clave: `id`, `stayId`, `firstName`, `lastName`, `documentType`, `documentNumber`, `nationality`, `birthDate` (solo extranjeros), `originPlace` (solo extranjeros), `destinationPlace` (solo extranjeros) e `isReservationGuest` (flag booleano que identifica al titular de la reserva).
- **ForeignGuestData**: Estructura de control migratorio con los diez campos exigidos por el SIRE que se envía por la cola independiente `m2.huespedes.extranjeros.queue`. Los campos `birthDate`, `originPlace` y `destinationPlace` se capturan en el panel migratorio de este caso de uso; `movementType` (`ENTRY`) y `movementDate` (`checkInDate`) los asigna automáticamente el caso de uso "Enviar datos de huéspedes extranjeros".
- **Room**: Unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo, capacidad máxima de personas (`maxCapacity`), tarifa base y estado actual (uno de los 8 estados del ciclo de vida).
- **Stay**: Entidad conceptual de estancia que representa la ocupación física real. Atributos clave: ID único, referencia de reserva (`reservationRef`), identificador de habitación (`roomId`), canal de origen (`source`: `DIRECTA` o nombre de la OTA), fecha de llegada real (`checkInDate`), fecha de salida real (`checkOutDate`), fechas esperadas de reserva, recepcionista de check-in (`receptionistIdCheckIn`) y recepcionista de check-out (`receptionistIdCheckOut`).
- **Receptionist**: Actor de recepcionista que opera el flujo de recepción y captura de huéspedes.

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Recepcionista puede capturar y validar los datos de los ocupantes en menos de 1 minuto para un grupo de hasta 2 personas nacionales y en menos de 2 minutos para ocupantes extranjeros.
- **SC-002**: El 100% de los intentos de Check-In donde la cantidad de ocupantes difiera de `guestCount` de la habitación son bloqueados por el sistema informando la discrepancia.
- **SC-003**: El 100% de los ocupantes con nacionalidad distinta de Colombia abren el panel migratorio exigiendo `birthDate`, `originPlace` y `destinationPlace` de catálogo antes de permitir el paso al resumen de confirmación.
- **SC-004**: El 100% de los intentos de modificar o eliminar la identidad del Huésped Titular son prevenidos manteniendo sus campos como de solo lectura.

