# Arquitectura Hexagonal (Puertos y Adaptadores)

La arquitectura hexagonal funciona estructurando el software en capas concéntricas para aislar por completo la lógica de negocio de cualquier tecnología externa, base de datos o framework.

Su principio fundamental es la **Regla de Dependencia**: todas las dependencias del código apuntan siempre hacia el centro (el dominio); el núcleo jamás depende de lo que hay afuera.

El funcionamiento se divide en tres componentes principales que interactúan entre sí:

## 1. El Centro del Hexágono (Dominio y Aplicación)
Es el corazón del sistema y no depende de la tecnología externa.
*   **Dominio:** Contiene las entidades, objetos de valor y reglas de negocio puras (ej. la máquina de estados de las habitaciones). Es código puro, sin anotaciones de frameworks ni librerías externas.
*   **Casos de Uso (Capa de Aplicación):** Son las clases que orquestan el flujo de trabajo. Toman decisiones basadas en las reglas del dominio, pero no interactúan directamente con bases de datos ni con la web: lo hacen a través de los puertos de salida.

> **Decisión de Módulo 1:** la capa de aplicación admite únicamente `@Service`, `@Transactional` y los eventos de aplicación de Spring, porque la atomicidad de cada caso de uso (por ejemplo, el Check-In con su notificación en el Outbox) pertenece al caso de uso. No usa ninguna otra anotación de Spring, JPA, Jackson, web ni Bean Validation (plan base, regla 1).

## 2. Los Puertos (Los Contratos)
Son los límites o fronteras del hexágono. Funcionan como interfaces (contratos abstractos) que definen cómo se entra o se sale del núcleo, asegurando que el centro no conozca los detalles de implementación.
*   **Puertos de Entrada (Inbound / Driving Ports):** Interfaces que exponen lo que el sistema puede hacer. Hay un puerto por caso de uso (ej. `ReportDamageUseCase`, `StartRepairUseCase`), no uno por área.
*   **Puertos de Salida (Outbound / Driven Ports):** Interfaces que definen lo que el sistema necesita del exterior para funcionar, como guardar datos o enviar notificaciones (ej. `RoomRepositoryPort`, `OutboxEventPublisherPort`).

Los puertos viven en la capa de aplicación (`application/ports/in/` y `application/ports/out/`), junto a los comandos y DTOs que usan sus métodos. Así ninguna clase del dominio depende de la capa de aplicación.

## 3. Los Adaptadores (La Infraestructura)
Están fuera del hexágono. Son las implementaciones tecnológicas concretas que "se enchufan" a los puertos.
*   **Adaptadores de Entrada (Inbound / Driving Adapters):** Son los mecanismos que inician la interacción con el sistema. Por ejemplo, un Controlador REST que recibe un JSON HTTP, lo valida, lo transforma en un comando y llama a un Puerto de Entrada. También lo son los consumidores de colas RabbitMQ y los trabajos programados.
*   **Adaptadores de Salida (Outbound / Driven Adapters):** Son las clases que implementan los Puertos de Salida. Por ejemplo, un adaptador de persistencia que convierte la entidad del dominio en una entidad JPA (`Room` → `RoomJpaEntity`) y la guarda en PostgreSQL con un repositorio de Spring Data; o un cliente REST hacia Módulo 2 o Módulo 3.

---

## Flujo de Ejecución (Cómo viaja la información)

Para entenderlo en acción, este es el recorrido de una petición típica (ej. reportar un daño en una habitación):

1.  **Entrada (Adaptador de Entrada):** El cliente web hace una petición HTTP `POST /api/rooms/{roomId}/damage-reports`. El **Controlador REST** (Adaptador de Entrada) recibe el JSON en un DTO de request y valida su formato con Bean Validation (`@Valid`).
2.  **Llamada al Contrato (Puerto de Entrada):** El Controlador convierte el DTO en un comando de aplicación y invoca el método definido en la interfaz del **Puerto de Entrada** (`ReportDamageUseCase`).
3.  **Procesamiento (Caso de Uso y Dominio):** La clase del **Caso de Uso** (`ReportDamageService`, dentro del hexágono) toma el control. Llama al dominio para validar la regla de negocio: `Room.transitionTo(DisabledForRepairs)` verifica que la transición sea válida en la máquina de estados.
4.  **Petición de Persistencia (Puerto de Salida):** Una vez que el dominio acepta el cambio, el Caso de Uso llama a la interfaz del **Puerto de Salida** (`RoomRepositoryPort`, `DamageReportRepositoryPort`) para pedir que se guarde el nuevo estado, sin saber cómo se hará.
5.  **Salida (Adaptador de Salida):** El adaptador de persistencia de PostgreSQL (`RoomRepositoryAdapter`), que implementa ese puerto, convierte la entidad del dominio en su entidad JPA y la guarda en la base de datos real.

---

## Correspondencia con los paquetes de Módulo 1

| Componente | Paquete (`com.hospitua.habitaciones`) | Puede depender de |
| --- | --- | --- |
| Dominio | `domain/model/`, `domain/exception/` | Nada externo |
| Puertos de entrada y de salida | `application/ports/in/`, `application/ports/out/` | `domain/` |
| Casos de uso, comandos y DTOs, eventos internos | `application/service/`, `application/dto/`, `application/event/` | `domain/`, `application/ports/` |
| Adaptadores de entrada | `infrastructure/adapters/in/` (`web/`, `amqp/`, `scheduling/`, `startup/`) | Todas las capas |
| Adaptadores de salida | `infrastructure/adapters/out/` (`persistence/`, `rest/`, `messaging/`, `security/`) | Todas las capas |
| Configuración Spring | `infrastructure/config/` | Todas las capas |

Nombres: los puertos de entrada terminan en `UseCase`; los puertos de salida de persistencia en `RepositoryPort`, implementados por el `RepositoryAdapter` del mismo nombre. El detalle completo está en el [plan base](../PLAN/base/plan.md) (regla 1 y árbol del código fuente).
