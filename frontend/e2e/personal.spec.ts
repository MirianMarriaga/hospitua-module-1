import { expect, test } from '@playwright/test';

import { PASSWORD, USERS } from './support/backend';
import { capture, chooseOption, login, logout } from './support/session';

test('Administración registra una cuenta, la persona entra y luego se desactiva', async ({
  page,
}) => {
  // Usuario nuevo en cada corrida: las cuentas no se borran, solo se desactivan.
  const username = `e2e${Date.now().toString(36)}`;
  const fullName = 'Paula Prueba';

  await login(page, USERS.admin.username, USERS.admin.fullName);
  await page.getByRole('link', { name: 'Personal' }).click();
  await expect(page.getByRole('heading', { name: 'Personal' })).toBeVisible();
  await capture(page, '40-personal-lista');

  await page.getByRole('link', { name: 'Registrar personal' }).click();
  await page.getByLabel(/Usuario/).fill(username);
  await page.getByLabel(/^Contraseña/).fill(PASSWORD);
  await page.getByLabel(/Nombre completo/).fill(fullName);
  await chooseOption(page, page.getByLabel(/Área/), 'Recepción');
  await capture(page, '41-personal-registrar');
  await page.getByRole('button', { name: 'Registrar personal' }).click();

  await expect(
    page.getByRole('heading', { name: `Cuenta de ${fullName} registrada` }),
  ).toBeVisible();
  await capture(page, '42-personal-registrada');

  // La cuenta nueva inicia sesión y llega a la pantalla de su área.
  await logout(page, USERS.admin.fullName);
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByRole('button', { name: `Menú de ${fullName}` })).toBeVisible();
  await expect(page).toHaveURL(/\/recepcion$/);
  await capture(page, '43-personal-cuenta-nueva-entra');
  await logout(page, fullName);

  // El Administrador la desactiva desde la lista.
  await login(page, USERS.admin.username, USERS.admin.fullName);
  await page.getByRole('link', { name: 'Personal' }).click();
  await page.getByLabel('Buscar').fill(username);
  const row = page.getByRole('row', { name: new RegExp(username) });
  await expect(row.getByText('Activa', { exact: true })).toBeVisible();
  await row.getByRole('button', { name: 'Desactivar' }).click();
  const dialog = page.getByRole('alertdialog');
  await expect(dialog.getByText(`¿Desactivar la cuenta de ${fullName}?`)).toBeVisible();
  await capture(page, '44-personal-desactivar');
  await dialog.getByRole('button', { name: 'Desactivar cuenta' }).click();

  await expect(row.getByText('Desactivada')).toBeVisible();
  await expect(row.getByRole('button', { name: 'Desactivar' })).toHaveCount(0);
  await capture(page, '45-personal-desactivada');

  // Con la cuenta desactivada, el inicio de sesión se rechaza.
  await logout(page, USERS.admin.fullName);
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByText('Usuario o contraseña incorrectos.')).toBeVisible();
  await capture(page, '46-personal-login-rechazado');
});
