import { expect, test } from '@playwright/test';

import { uniqueRoomNumber, USERS } from './support/backend';
import { capture, chooseOption, login, logout } from './support/session';

test('Administración registra, edita, da de baja y reactiva una habitación, y Gerencia ve su historial', async ({
  page,
}) => {
  const roomNumber = uniqueRoomNumber('A');

  await login(page, USERS.admin.username, USERS.admin.fullName);
  await expect(page.getByRole('heading', { name: 'Inventario de habitaciones' })).toBeVisible();
  await capture(page, '01-admin-inventario');

  await page.getByRole('link', { name: 'Registrar habitación' }).first().click();
  await page.getByLabel(/Número de habitación/).fill(roomNumber);
  await page.getByLabel(/Piso/).fill('7');
  // Tipo es un OptionSelect (F02b): se abre la lista y se elige la opción.
  await page.getByLabel(/Tipo/).click();
  await page.getByRole('option', { name: 'Suite' }).click();
  await page.getByLabel(/Capacidad máxima/).fill('4');
  await page.getByLabel(/Tarifa base/).fill('520000');
  await capture(page, '02-admin-registrar');
  await page.getByRole('button', { name: 'Registrar habitación' }).click();

  await expect(
    page.getByRole('heading', { name: `Habitación ${roomNumber} registrada` }),
  ).toBeVisible();
  await capture(page, '03-admin-registrar-exito');

  await page.getByRole('link', { name: 'Volver al inventario' }).click();
  await page.getByLabel('Número').fill(roomNumber);
  const row = page.getByRole('row', { name: new RegExp(roomNumber) });
  await expect(row.getByText('Disponible')).toBeVisible();
  await expect(row.getByRole('link', { name: 'Editar' })).toBeVisible();
  await capture(page, '04-admin-inventario-filtrado');

  // Editar: cambia la capacidad y muestra la confirmación "Antes / Ahora".
  await row.getByRole('link', { name: 'Editar' }).click();
  await expect(
    page.getByRole('heading', { name: `Editar habitación ${roomNumber}` }),
  ).toBeVisible();
  await page.getByLabel(/Capacidad máxima/).fill('5');
  await capture(page, '05-admin-editar');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(page.getByText(`Habitación ${roomNumber} actualizada`)).toBeVisible();
  await capture(page, '06-admin-editar-exito');
  await page.getByRole('link', { name: 'Volver al inventario' }).click();

  // Dar de baja: motivo, confirmación y resultado. Módulo 2 simulado no reporta reservas.
  await page.getByLabel('Número').fill(roomNumber);
  await row.getByRole('link', { name: 'Dar de baja' }).click();
  await chooseOption(page, page.getByLabel('Motivo de la baja *'), 'Decisión administrativa');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByText(`¿Confirmas la baja de la habitación ${roomNumber}?`)).toBeVisible();
  await capture(page, '07-admin-baja-confirmar');
  await page.getByRole('button', { name: 'Confirmar baja' }).click();
  await expect(
    page.getByRole('heading', { name: `Habitación ${roomNumber} dada de baja` }),
  ).toBeVisible();
  await capture(page, '08-admin-baja-exito');
  await page.getByRole('link', { name: 'Volver al inventario' }).click();

  // Reactivar: la habitación inactiva vuelve a estar disponible.
  await page.getByLabel('Número').fill(roomNumber);
  await expect(row.getByText('Inactiva')).toBeVisible();
  await row.getByRole('link', { name: 'Marcar como disponible' }).click();
  await page.getByRole('button', { name: 'Confirmar reactivación' }).click();
  await expect(page.getByText(`Habitación ${roomNumber} disponible`)).toBeVisible();
  await capture(page, '09-admin-reactivar-exito');

  await logout(page, USERS.admin.fullName);
  await login(page, USERS.manager.username, USERS.manager.fullName);
  await page.getByLabel('Número').fill(roomNumber);
  await page
    .getByRole('row', { name: new RegExp(roomNumber) })
    .getByRole('link', { name: 'Ver historial' })
    .click();

  await expect(page.getByRole('heading', { name: 'Historial de estados' })).toBeVisible();
  // Tres periodos: disponible al registrarla, inactiva tras la baja y disponible otra vez.
  const periods = page.getByRole('row', { name: new RegExp(roomNumber) });
  await expect(periods).toHaveCount(3);
  await expect(periods.filter({ hasText: 'En curso' })).toHaveCount(1);
  await expect(periods.filter({ hasText: 'Inactiva' })).toHaveCount(1);
  await expect(periods.filter({ hasText: USERS.admin.fullName })).toHaveCount(3);
  await capture(page, '10-gerente-historial');
});
