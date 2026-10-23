# Especificación de Funcionalidad: Gestionar Personal

**Módulo**: Módulo 1 — Gestión de Habitaciones e Inventario
**Actor principal**: Administrador
**Creado**: 2026-10-12

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar una cuenta del personal (Prioridad: P1)

Como Administrador, quiero registrar en el sistema a cada persona del personal del hotel (Recepción, Administración, Gerencia, Limpieza y Mantenimiento) con su usuario, contraseña, nombre completo y área, para que pueda iniciar sesión y el sistema sepa quién realiza cada acción.

**Por qué esta prioridad**: Sin cuentas nadie puede iniciar sesión, y los casos de uso que registran un responsable (historial de estados, tareas de limpieza y reparación, estancias, bajas) no tendrían a quién atribuir cada acción.

**Prueba Independiente**: Como Administrador, registrar a una persona del área de Limpieza y verificar que aparece en la lista del personal como activa y que puede iniciar sesión con su usuario y contraseña.

**Escenarios de Aceptación**:

1. **Escenario**: Registro exitoso de una cuenta
   - **Dado** el Administrador se encuentra en la sección de Personal
   - **Cuando** registra el usuario "jramirez", la contraseña, el nombre "Jorge Ramírez" y el área "Limpieza", y confirma
   - **Entonces** el sistema crea la cuenta activa, guarda la contraseña cifrada y muestra una confirmación con el usuario, el nombre y el área

2. **Escenario**: Usuario ya existente
   - **Dado** ya existe una cuenta con el usuario "jramirez", activa o desactivada
   - **Cuando** el Administrador intenta registrar otra cuenta con el usuario "jramirez"
   - **Entonces** el sistema rechaza el registro e indica que el usuario ya existe

3. **Escenario**: Datos incompletos o inválidos
   - **Dado** el Administrador está completando el registro
   - **Cuando** deja vacío un campo obligatorio, escribe un usuario con caracteres no permitidos o una contraseña de menos de 8 caracteres
   - **Entonces** el sistema impide el registro y señala cada campo con su error

---

### Historia de Usuario 2 - Consultar el personal registrado (Prioridad: P1)

Como Administrador, quiero ver la lista del personal registrado con su área y si su cuenta está activa, para saber quién tiene acceso al sistema.

**Por qué esta prioridad**: Es el punto de entrada de la sección de Personal y desde ella se llega a editar y desactivar.

**Prueba Independiente**: Como Administrador, abrir la sección de Personal y verificar que se listan todas las cuentas, activas y desactivadas, y que los filtros por área y estado funcionan.

**Escenarios de Aceptación**:

4. **Escenario**: Lista del personal
   - **Dado** existen cuentas registradas en distintas áreas, activas y desactivadas
   - **Cuando** el Administrador abre la sección de Personal
   - **Entonces** el sistema muestra cada cuenta con su usuario, nombre, área y estado ("Activa" o "Desactivada"), las acciones "Editar" y "Desactivar" en las cuentas activas y el botón "Registrar personal"

5. **Escenario**: Filtros y búsqueda
   - **Dado** el Administrador está en la lista del personal
   - **Cuando** filtra por un área o un estado, o busca por usuario o nombre
   - **Entonces** el sistema muestra solo las cuentas que cumplen los criterios

---

### Historia de Usuario 3 - Editar una cuenta del personal (Prioridad: P2)

Como Administrador, quiero cambiar el nombre, el área o la contraseña de una persona, para mantener sus datos al día o restablecer su acceso si olvida la contraseña.

**Por qué esta prioridad**: Corrige errores de registro y resuelve olvidos de contraseña sin crear una cuenta nueva.

**Prueba Independiente**: Como Administrador, cambiar el área de una cuenta y su contraseña, y verificar que la persona inicia sesión con la contraseña nueva y llega a la pantalla de su nueva área.

**Escenarios de Aceptación**:

6. **Escenario**: Edición exitosa
   - **Dado** existe la cuenta activa "jramirez" del área "Limpieza"
   - **Cuando** el Administrador cambia su área a "Mantenimiento", escribe una contraseña nueva y confirma
   - **Entonces** el sistema guarda los cambios, cifra la contraseña nueva y muestra la confirmación con los datos actualizados

7. **Escenario**: Edición sin cambiar la contraseña
   - **Dado** el Administrador edita el nombre de una cuenta
   - **Cuando** deja vacío el campo de contraseña y confirma
   - **Entonces** el sistema guarda el nombre y conserva la contraseña actual

---

### Historia de Usuario 4 - Desactivar una cuenta del personal (Prioridad: P2)

Como Administrador, quiero desactivar la cuenta de una persona que ya no trabaja en el hotel, para que no pueda iniciar sesión sin perder el registro de lo que hizo.

**Por qué esta prioridad**: Retira el acceso de quien deja el hotel. La cuenta no se borra, para que el historial de estados siga mostrando su nombre.

**Prueba Independiente**: Como Administrador, desactivar una cuenta y verificar que ya no puede iniciar sesión, que aparece como "Desactivada" en la lista y que el historial de estados sigue mostrando su nombre.

**Escenarios de Aceptación**:

8. **Escenario**: Desactivación exitosa
   - **Dado** existe la cuenta activa "jramirez"
   - **Cuando** el Administrador elige "Desactivar" y confirma
   - **Entonces** el sistema marca la cuenta como desactivada, la persona ya no puede iniciar sesión y la cuenta sigue en la lista como "Desactivada"

9. **Escenario**: Confirmación antes de desactivar
   - **Dado** el Administrador eligió "Desactivar" en una cuenta
   - **Cuando** el sistema le pide confirmar
   - **Entonces** muestra el usuario, el nombre y el área de la cuenta, y advierte que la persona no podrá iniciar sesión

10. **Escenario**: No se puede desactivar la propia cuenta ni al último Administrador
    - **Dado** el Administrador intenta desactivar su propia cuenta, o la única cuenta activa del área "Administración"
    - **Cuando** confirma la desactivación
    - **Entonces** el sistema la rechaza e indica el motivo, para que el sistema no quede sin nadie que pueda gestionar al personal

11. **Escenario**: Desactivar una cuenta con una tarea abierta
    - **Dado** la persona tiene abierta una tarea de limpieza o de reparación
    - **Cuando** el Administrador confirma la desactivación
    - **Entonces** el sistema libera la tarea, y la habitación queda disponible para que otra persona la atienda (una de limpieza vuelve a `PendingCleaning`; una de reparación sigue en su estado, sin tarea abierta)

---

### Casos Borde

- Primera puesta en marcha: si no existe ninguna cuenta activa del área "Administración", el sistema crea al arrancar una cuenta inicial de Administrador con los datos de su configuración, por el mismo proceso de registro, para que alguien pueda iniciar sesión por primera vez (FR-012).
- Usuario con mayúsculas o espacios: el usuario se guarda sin espacios al inicio ni al final y en minúsculas; "JRamirez" y "jramirez" son el mismo usuario (FR-003).
- Cuenta ya desactivada: la acción "Desactivar" no se muestra, y si la petición llega igual, el sistema la rechaza indicando que la cuenta ya está desactivada.
- Edición de una cuenta desactivada: no se ofrece; una cuenta desactivada no se edita.
- Ediciones simultáneas: si dos Administradores editan la misma cuenta al mismo tiempo, se guarda la primera y la segunda recibe un aviso de que la información ya fue actualizada.
- Pérdida de conexión o fallo al guardar: la operación se cancela completa y la cuenta queda como estaba.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE permitir al Administrador registrar, consultar, editar y desactivar las cuentas del personal desde la sección de Personal. Todas estas operaciones DEBEN pasar por el backend; ninguna cuenta se crea ni se modifica escribiendo directamente en la base de datos.
- **FR-002**: Al registrar una cuenta, el sistema DEBE exigir usuario, contraseña, nombre completo y área. El área DEBE ser una de: Recepción, Administración, Gerencia, Limpieza o Mantenimiento.
- **FR-003**: El sistema DEBE guardar el usuario sin espacios al inicio ni al final y en minúsculas, aceptar solo letras sin tilde, números, punto y guion bajo, con entre 3 y 30 caracteres, y validar que sea único entre todas las cuentas, activas y desactivadas.
- **FR-004**: El sistema DEBE exigir una contraseña de al menos 8 caracteres y guardarla cifrada; la contraseña nunca se guarda ni se muestra en texto plano.
- **FR-005**: Toda cuenta nueva DEBE quedar activa.
- **FR-006**: Tras un registro o una edición exitosa, el sistema DEBE mostrar una confirmación con el usuario, el nombre, el área y el estado, sin mostrar la contraseña.
- **FR-007**: El sistema DEBE mostrar la lista del personal con usuario, nombre, área y estado ("Activa" o "Desactivada"), incluir por defecto las cuentas desactivadas, permitir filtrar por área y por estado y buscar por usuario o nombre, y paginar de 10 en 10, mostrando el rango y el total ("Mostrando X–Y de Z").
- **FR-008**: El sistema DEBE permitir editar el nombre, el área y la contraseña de una cuenta activa. El usuario no se puede cambiar, porque identifica a la persona en el historial. Si el campo de contraseña se deja vacío, la contraseña actual se conserva.
- **FR-009**: El sistema DEBE pedir confirmación antes de desactivar una cuenta, mostrando el usuario, el nombre y el área y advirtiendo que la persona no podrá iniciar sesión. Al confirmar, la cuenta queda desactivada: no se borra y no puede iniciar sesión.
- **FR-010**: El sistema NO DEBE permitir que el Administrador desactive su propia cuenta ni la última cuenta activa del área Administración.
- **FR-011**: Al desactivar una cuenta, el sistema DEBE liberar las tareas abiertas de la cuenta (limpieza y reparación). La liberación de limpieza registra al Administrador como actor en el historial de estados; la de reparación no registra actor.
- **FR-012**: Si al arrancar no existe ninguna cuenta activa del área Administración, el sistema DEBE crear una cuenta inicial de Administrador con el usuario, la contraseña y el nombre de su configuración, aplicando las mismas reglas de FR-002 a FR-005.
- **FR-013**: El sistema DEBE registrar la fecha y hora y el Administrador responsable del registro, de la última edición y de la desactivación de cada cuenta; la cuenta inicial de FR-012 queda registrada por el sistema.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **Cuenta del personal (`user_account`)**: Persona que puede iniciar sesión en Módulo 1. Atributos clave: usuario (único, no editable), contraseña cifrada, nombre completo, área (Recepción, Administración, Gerencia, Limpieza o Mantenimiento), estado (activa o desactivada) y la fecha y el responsable de su registro, su última edición y su desactivación.
- **Administrator**: Actor responsable de gestionar las cuentas del personal.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El Administrador puede registrar una cuenta del personal en menos de 2 minutos.
- **SC-002**: El 100% de las contraseñas quedan guardadas cifradas; ninguna aparece en texto plano en la base de datos, en las respuestas ni en los registros del sistema.
- **SC-003**: El 100% de las cuentas desactivadas no pueden iniciar sesión, y el historial de estados sigue mostrando su nombre, y el 100% de las tareas abiertas de una cuenta desactivada quedan liberadas (una habitación en limpieza vuelve a `PendingCleaning`; una en reparación queda sin tarea abierta).
- **SC-004**: El sistema rechaza el 100% de los intentos de registrar un usuario repetido o de desactivar la propia cuenta o al último Administrador.
