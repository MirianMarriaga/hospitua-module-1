# Especificación de Funcionalidad: Registrar Habitación

**Creado**: 2026-08-29

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Creación de una nueva habitación en el inventario (Prioridad: P1)

Como Administrador, quiero registrar una nueva habitación en la plataforma indicando sus datos de identificación, categorización y comerciales, para que quede disponible en el inventario del hotel y pueda empezar a ser reservada por el personal de recepción o a través de los canales de venta configurados.

**Por qué esta prioridad**: Es la operación fundacional del Módulo 1 (Gestión de Habitaciones e Inventario). Sin la capacidad de crear habitaciones, ningún otro caso de uso del módulo (editar, dar de baja, bloquear, marcar estados, generar reportes) tiene datos sobre los cuales operar. Es la base de datos central del sistema.

**Prueba Independiente**: Puede probarse de forma independiente iniciando sesión como Administrador del sistema, completando el formulario de registro con todos los datos (identificación, categorización y comerciales) y verificando que la habitación aparezca en el inventario con estado "Disponible", con un identificador único asignado y con su tarifa base.

**Escenarios de Aceptación**:

1. **Escenario**: Registro exitoso con datos completos y válidos
   - **Dado** el Administrador del sistema ha iniciado sesión y se encuentra en la sección de Inventario de Habitaciones
   - **Cuando** completa el formulario de registro con número de habitación, piso/ala, tipo, capacidad máxima, tarifa base, y confirma la creación
   - **Entonces** el sistema crea la habitación con un ID único (UUID), la asigna al estado "Available", almacena la tarifa base y la muestra en el listado de inventario

2. **Escenario**: Intento de registro con número de habitación duplicado
   - **Dado** ya existe una habitación registrada con el número "204"
   - **Cuando** el Administrador intenta registrar una nueva habitación usando el mismo número "204"
   - **Entonces** el sistema rechaza la operación y muestra un mensaje indicando que el número de habitación ya existe

3. **Escenario**: Intento de registro con campos obligatorios incompletos
   - **Dado** el Administrador está completando el formulario de registro
   - **Cuando** intenta confirmar la creación sin diligenciar uno o más campos obligatorios (por ejemplo, tipo de habitación o capacidad máxima)
   - **Entonces** el sistema impide el guardado y señala los campos pendientes por completar

4. **Escenario**: Intento de registro con tarifa base negativa o igual a cero
   - **Dado** el Administrador está completando el formulario de registro
   - **Cuando** ingresa una tarifa base menor o igual a cero
   - **Entonces** el sistema rechaza el valor y solicita ingresar una tarifa base válida (mayor a cero)

---

### Casos Borde

- Capacidad máxima de personas igual a cero o negativa: el sistema rechaza el valor y solicita un número entero positivo (FR-006).
- Número de habitación duplicado en pisos/alas diferentes: el sistema rechaza el registro, ya que el número de habitación debe ser único en todo el inventario (FR-004).
- Tipo de habitación no predefinido: el sistema restringe la selección a las categorías predefinidas, impidiendo el registro de otros tipos (FR-005).
- Pérdida de conexión o fallo de guardado a mitad del registro: la transacción se cancela, evitando la creación de una habitación en estado inconsistente o parcialmente creada.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir únicamente al actor "Administrador" registrar nuevas habitaciones en el inventario.
- **FR-002**: El sistema DEBE generar automáticamente un identificador único (UUID) para cada habitación registrada, sin intervención manual del usuario.
- **FR-003**: El sistema DEBE requerir y validar como obligatorios los siguientes campos al registrar una habitación: número de habitación, piso/ala, tipo de habitación y capacidad máxima de personas.
- **FR-004**: El sistema DEBE validar que el número de habitación sea único dentro del inventario, rechazando el registro si ya existe una habitación activa con el mismo número.
- **FR-005**: El sistema DEBE restringir el campo "Tipo" a las categorías predefinidas: Sencilla, Doble, Suite, Boutique.
- **FR-006**: El sistema DEBE validar que la capacidad máxima de personas sea un número entero positivo (mayor a cero).
- **FR-007**: El sistema DEBE validar que la tarifa base ingresada sea un valor numérico positivo (mayor a cero).
- **FR-008**: El sistema DEBE asignar automáticamente el estado "Available" a toda habitación recién registrada.
- **FR-009**: El sistema DEBE persistir la habitación registrada de forma que quede visible de inmediato en el listado de inventario de habitaciones.
- **FR-010**: El sistema DEBE registrar la fecha y el usuario responsable de la creación de cada habitación, para efectos de trazabilidad.


### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Room**: Representa una unidad habitacional del hotel. Atributos clave: ID único (UUID), número de habitación, piso/ala, tipo (Sencilla, Doble, Suite, Boutique), capacidad máxima de personas, tarifa base, y estado actual (por defecto "Available" al ser creada). Se relaciona con el Módulo 2 a través del caso de uso Consultar inventario de habitaciones, y con el Módulo 3 (Facturación) a través de la consulta de su tarifa base.
- **Administrator**: Actor responsable de gestionar el inventario de habitaciones, incluyendo su registro, edición y baja.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador del sistema puede completar el registro de una nueva habitación en menos de 5 minutos.
- **SC-002**: El 100% de las habitaciones registradas exitosamente quedan en estado "Available" y visibles en el inventario de forma inmediata (sin recarga manual).
- **SC-003**: El sistema rechaza el 100% de los intentos de registro con número de habitación duplicado o con campos obligatorios faltantes, mostrando un mensaje de error claro.
- **SC-004**: Cero habitaciones quedan registradas sin ID único o sin estado inicial asignado tras el proceso de alta.
