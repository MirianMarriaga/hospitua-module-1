# E2E y evidencias

Flujos felices contra el backend real en perfil `dev`. No corren en el CI.

1. Infraestructura y backend, desde la raíz del repositorio:

   ```bash
   docker compose up -d
   (cd backend && ./mvnw spring-boot:run)
   ```

2. Cuentas de Gerencia y Recepción, con la contraseña del admin (`admin123`), una sola vez:

   ```bash
   docker compose exec -T postgres psql -U hospitua -d hospitua_m1 < frontend/e2e/seed/demo-users.sql
   ```

3. Desde `frontend/`, la primera vez `npx playwright install chromium`, y después:

   ```bash
   npm run e2e
   ```

   Playwright levanta `npm run dev` si no está corriendo. `recepcion.spec.ts` crea una habitación y
   publica en RabbitMQ la lista del día con una reserva que llega hoy, así que no hace falta Módulo 2.
   Sin Módulo 3, la liquidación del check-out llega `UNAVAILABLE` y se ve el banner de contingencia.

Las capturas de cada paso quedan en `e2e/capturas/` (fuera de git).

## Demostración en el navegador

Con el backend y la base levantados (pasos 1 y 2), desde `frontend/`:

```bash
npm run dev            # http://localhost:5173
npm run demo:llegada   # crea una llegada de hoy para el panel de recepción
```
