import { ensureStaffAccount, USERS } from './backend';
import { startModule2Stub } from './module2Stub';

/**
 * Antes de los E2E: las cuentas de prueba, creadas por la API y no con SQL, y Módulo 2 simulado.
 * Devuelve la función que apaga el simulador al terminar.
 */
export default async function globalSetup(): Promise<() => Promise<void>> {
  for (const user of [USERS.manager, USERS.reception, USERS.cleaning, USERS.maintenance]) {
    await ensureStaffAccount(user);
  }
  const module2 = await startModule2Stub();
  return async () => {
    if (module2) {
      await new Promise((resolve) => module2.close(resolve));
    }
  };
}
