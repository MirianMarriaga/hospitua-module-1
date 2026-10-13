# HOSPITUA — Módulo 1: Gestión de Habitaciones e Inventario de Aforo, Check-In y Check-Out

HOSPITUA es una plataforma de gestión hotelera (SaaS) dividida en tres módulos. Este repositorio
contiene el **Módulo 1**, la fuente de verdad del inventario de habitaciones, de su ciclo de vida
(8 estados) y de las ocupaciones físicas (Check-In y Check-Out). Se integra con el Módulo 2
(Reservas y cumplimiento migratorio) por RabbitMQ y REST, y con el Módulo 3 (Facturación y
liquidación) por REST.

El contexto funcional completo está en [`documentos/contexto/hospitua.md`](documentos/contexto/hospitua.md).

## Estructura

```text
.
├── backend/            # Spring Boot 4 (Java 21), arquitectura hexagonal
├── documentos/         # Documentación del desarrollo guiado por specs
│   ├── contexto/       # Visión del sistema, arquitectura hexagonal y diccionario
│   ├── SPEC/           # Un spec por caso de uso y la máquina de estados
│   ├── PLAN/           # Plan base (plataforma común) y un plan por caso de uso
│   ├── diagramas/      # Casos de uso, entidad-relación, estados, integración M1-M2-M3
│   ├── vistas/         # Prototipos HTML por área (recepción, limpieza, mantenimiento, ...)
│   └── plantillas/     # Plantillas de spec y plan
└── docker-compose.yml  # PostgreSQL 16 y RabbitMQ 3.13 para desarrollo local
```

Antes de implementar una feature, lee el [plan base](documentos/PLAN/base/plan.md): define el
stack, los contratos REST y de colas, el formato de errores y las reglas de arquitectura comunes.

## Requisitos

- Java 21
- Docker (para `docker-compose.yml` y para las pruebas con Testcontainers)

## Ejecutar en local

```bash
docker compose up -d          # PostgreSQL en :5432, RabbitMQ en :5672 (gestión en http://localhost:15672)
cd backend
./mvnw spring-boot:run        # perfil dev por defecto, en http://localhost:8080
```

Sin `docker compose`, también se puede arrancar con contenedores efímeros de Testcontainers:

```bash
cd backend
./mvnw spring-boot:test-run
```

### Perfiles

| Perfil | Uso | Configuración |
| --- | --- | --- |
| `dev` (por defecto) | Desarrollo local | Valores por defecto que coinciden con `docker-compose.yml` |
| `test` | Pruebas automatizadas | PostgreSQL y RabbitMQ de Testcontainers |
| `prod` | Despliegue | Todo por variables de entorno: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `MODULE2_BASE_URL`, `MODULE3_BASE_URL` |

## Pruebas y formato

```bash
cd backend
./mvnw verify                 # compila, ejecuta las pruebas y verifica el formato (Spotless)
./mvnw spotless:apply         # aplica el formato de código (palantir-java-format)
```

## Flujo de trabajo

Git + GitHub con Gitflow: las ramas `feature/*` se integran por pull request.
