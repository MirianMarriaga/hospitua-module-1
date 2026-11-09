import { expect, test } from '@playwright/test';

import { publishTodayArrival, USERS, type Arrival } from './support/backend';
import { capture, login } from './support/session';

let arrival: Arrival;

test.beforeAll(async () => {
  arrival = await publishTodayArrival();
});

test('Recepción hace el check-in y el check-out de una llegada de hoy', async ({ page }) => {
  await login(page, USERS.reception.username, USERS.reception.fullName);
  await page.getByRole('searchbox', { name: 'Buscar reservas' }).fill(arrival.reservationRef);
  const arrivalRow = page.getByRole('row', { name: new RegExp(arrival.roomNumber) });
  await expect(arrivalRow).toBeVisible();
  await capture(page, '10-recepcion-panel-llegadas');

  // Check-In en 4 pasos, con un huésped extranjero.
  await arrivalRow.getByRole('link', { name: 'Check-in' }).click();
  await expect(page.getByText(arrival.holderName)).toBeVisible();
  await capture(page, '11-checkin-1-reserva');
  await page.getByRole('button', { name: 'Continuar' }).click();

  const holder = page.getByRole('group', { name: 'Huésped 1' });
  await holder.getByLabel('Fecha de nacimiento').fill('1985-03-20');
  const second = page.getByRole('group', { name: 'Huésped 2' });
  await second.getByLabel('Nombres').fill('John');
  await second.getByLabel('Apellidos').fill('Smith');
  // Tipo de documento es un OptionSelect (F02b): se abre la lista y se elige la opción.
  await second.getByLabel('Tipo de documento').click();
  await page.getByRole('option', { name: /^PAS/ }).click();
  await second.getByLabel('Número de documento').fill(`X${Date.now().toString().slice(-7)}`);
  await second.getByLabel('Nacionalidad').fill('Estados Unidos');
  await second.getByLabel('Fecha de nacimiento').fill('1987-07-04');
  await second.getByLabel('Lugar de procedencia').fill('Miami');
  await second.getByLabel('Lugar de destino').fill('Cartagena');
  await capture(page, '12-checkin-2-huespedes');
  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByText('3. Resumen y confirmación')).toBeVisible();
  await capture(page, '13-checkin-3-confirmacion');
  await page.getByRole('button', { name: 'Confirmar Check-In' }).click();
  await expect(page.getByRole('heading', { name: 'Check-in completado' })).toBeVisible();
  await capture(page, '14-checkin-4-exito');

  // Check-Out en 5 pasos; sin Módulo 3 la liquidación llega UNAVAILABLE. La pestaña Salidas solo
  // lista las estadías que salen hoy o están vencidas, y esta sale en dos días: se entra con el mismo
  // enlace que arma el botón "Check-out" del panel.
  await page.getByRole('link', { name: 'Volver al inicio' }).click();
  await page.getByRole('tab', { name: 'Salidas' }).click();
  await capture(page, '15-recepcion-panel-salidas');
  const params = new URLSearchParams({
    reservationRef: arrival.reservationRef,
    roomId: arrival.roomId,
  });
  await page.goto(`/check-out?${params.toString()}`);

  await expect(page.getByText(arrival.reservationRef)).toBeVisible();
  await capture(page, '16-checkout-1-reserva');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByText('2. Liquidación de la estadía')).toBeVisible();
  await expect(
    page
      .getByText('La liquidación no está disponible en este momento.')
      .or(page.getByText('Información de la liquidación')),
  ).toBeVisible({ timeout: 10_000 });
  await capture(page, '17-checkout-2-liquidacion');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByText('3. Pago')).toBeVisible();
  await capture(page, '18-checkout-3-pago');
  await page.getByRole('button', { name: 'Continuar' }).click();

  const confirm = page.getByRole('button', { name: 'Confirmar Check-Out' });
  await expect(confirm).toBeDisabled();
  await page.getByRole('checkbox').click();
  await capture(page, '19-checkout-4-confirmacion');
  await confirm.click();

  await expect(page.getByRole('heading', { name: 'Check-out completado' })).toBeVisible();
  await expect(page.getByText('Pendiente de limpieza').first()).toBeVisible();
  await capture(page, '20-checkout-5-exito');
});
