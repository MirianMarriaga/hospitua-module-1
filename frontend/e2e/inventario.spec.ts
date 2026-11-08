import { expect, test } from '@playwright/test';

import { uniqueRoomNumber, USERS } from './support/backend';
import { capture, login } from './support/session';

test('Administración registra una habitación y Gerencia ve su historial', async ({ page }) => {
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
  await expect(row.getByRole('button', { name: 'Editar' })).toBeDisabled();
  await capture(page, '04-admin-inventario-filtrado');

  // "Salir" está en el menú del usuario de la barra lateral (F01e).
  await page.getByRole('button', { name: `Menú de ${USERS.admin.fullName}` }).click();
  await page.getByRole('menuitem', { name: /Salir/ }).click();
  await login(page, USERS.manager.username, USERS.manager.fullName);
  await page.getByLabel('Número').fill(roomNumber);
  await page
    .getByRole('row', { name: new RegExp(roomNumber) })
    .getByRole('link', { name: 'Ver historial' })
    .click();

  await expect(page.getByRole('heading', { name: 'Historial de estados' })).toBeVisible();
  const period = page.getByRole('row', { name: new RegExp(roomNumber) });
  await expect(period.getByText('En curso')).toBeVisible();
  await expect(period.getByText('Registrar habitación')).toBeVisible();
  await expect(period.getByText(USERS.admin.fullName)).toBeVisible();
  await capture(page, '05-gerente-historial');
});
