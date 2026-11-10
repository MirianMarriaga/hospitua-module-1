import { expect, type Locator, type Page } from '@playwright/test';

import { PASSWORD } from './backend';

/** Una captura por paso, como evidencia (e2e/capturas/, fuera de git). */
export async function capture(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: `e2e/capturas/${name}.png`, fullPage: true });
}

export async function login(page: Page, username: string, fullName: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill(username);
  // exact: el botón del ojo se llama "Ver contraseña" y también coincide con la etiqueta.
  await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByRole('button', { name: `Menú de ${fullName}` })).toBeVisible();
}

/** "Salir" está en el menú del usuario de la barra lateral. */
export async function logout(page: Page, fullName: string): Promise<void> {
  await page.getByRole('button', { name: `Menú de ${fullName}` }).click();
  await page.getByRole('menuitem', { name: /Salir/ }).click();
  await expect(page.getByRole('button', { name: 'Ingresar' })).toBeVisible();
}

/** Elige una opción de un `OptionSelect`: se abre la lista desde su botón y se hace clic en ella. */
export async function chooseOption(
  page: Page,
  trigger: Locator,
  option: string | RegExp,
): Promise<void> {
  await trigger.click();
  await page.getByRole('option', { name: option }).click();
}
