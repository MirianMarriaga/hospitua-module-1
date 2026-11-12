# E2E y evidencias

Flujos felices de todas las áreas contra el backend real en perfil `dev`. No corren en el CI.

1. Infraestructura y backend, desde la raíz del repositorio:

   ```bash
   docker compose up -d
   (cd backend && ./mvnw spring-boot:run)
   ```

2. Desde `frontend/`, la primera vez `npx playwright install chromium`, y después:

   ```bash
   npm run e2e
   ```

   Playwright levanta `npm run dev` si no está corriendo.

No hace falta preparar nada más: `e2e/support/globalSetup.ts` crea las cuentas de prueba por
`POST /api/staff` (Gerencia, Recepción, Limpieza y Mantenimiento, con la contraseña del admin) y
levanta un Módulo 2 simulado en el puerto 8082, que responde sin reservas en conflicto. Si ese puerto
ya está ocupado (otro simulador o el Módulo 2 real), se usa el que esté.

| Archivo                    | Flujo                                                                                              |
| -------------------------- | -------------------------------------------------------------------------------------------------- |
| `inventario.spec.ts`       | Administración registra, edita, da de baja y reactiva una habitación; Gerencia ve su historial     |
| `ciclo-habitacion.spec.ts` | Recepción hace el check-in y el check-out; Limpieza limpia y reporta un daño; Mantenimiento repara |
| `bloqueo-tecnico.spec.ts`  | Mantenimiento programa un bloqueo técnico y consulta su informe                                    |
| `personal.spec.ts`         | Administración registra una cuenta, la persona inicia sesión, se desactiva y su ingreso se rechaza |

Para tener en cuenta:

- `ciclo-habitacion.spec.ts` crea una habitación y publica en RabbitMQ la lista del día con una
  reserva que llega hoy, así que no hace falta el Módulo 2 real. **La lista reemplaza la copia local
  de reservas del día.**
- Sin Módulo 3, la liquidación del check-out llega `UNAVAILABLE` y se ve el banner de contingencia.
  Además, el evento de facturación (`facturacion.checkout`) no tiene cola que lo reciba
  (`modulo3.checkout` la declara Módulo 3): queda en `outbox_notification` reintentándose y termina
  en `FAILED`. Es lo esperado sin Módulo 3; no afecta a los E2E.
- Cada corrida deja datos en la base local: habitaciones nuevas, una estadía y una cuenta
  desactivada (`e2e…`), porque las cuentas no se borran.

Las capturas de cada paso quedan en `e2e/capturas/` (fuera de git).

## Demostración en el navegador

Con el backend y la base levantados (paso 1), desde `frontend/`:

```bash
npm run dev            # http://localhost:5173
npm run demo:llegada   # crea una llegada de hoy para el panel de recepción
```
