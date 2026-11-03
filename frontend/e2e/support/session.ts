import { expect, type Page } from '@playwright/test';

import { PASSWORD } from './backend';

/** Una captura por paso, como evidencia (e2e/capturas/, fuera de git). */
export async function capture(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: `e2e/capturas/${name}.png`, fullPage: true });
}

export async function login(page: Page, username: string, fullName: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill(username);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page.getByLabel('Usuario actual')).toContainText(fullName);
}
