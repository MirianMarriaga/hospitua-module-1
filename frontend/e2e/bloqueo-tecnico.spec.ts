import { expect, test } from '@playwright/test';

import {
  addDays,
  createRoom,
  releaseOpenTask,
  todayInBogota,
  uniqueRoomNumber,
  USERS,
  type CreatedRoom,
} from './support/backend';
import { capture, login } from './support/session';

let room: CreatedRoom;

test.beforeAll(async () => {
  await releaseOpenTask('maintenance');
  room = await createRoom(uniqueRoomNumber('B'));
});

/** Fecha AAAA-MM-DD como la muestra la interfaz: DD-MM-AAAA. */
function shown(isoDate: string): string {
  const [year, month, day] = isoDate.split('-');
  return `${day}-${month}-${year}`;
}

test('Mantenimiento programa un bloqueo técnico y consulta su informe', async ({ page }) => {
  const { roomNumber } = room;
  const start = addDays(todayInBogota(), 5);
  const end = addDays(todayInBogota(), 6);

  await login(page, USERS.maintenance.username, USERS.maintenance.fullName);
  await page.getByRole('searchbox', { name: 'Buscar habitación por número' }).fill(roomNumber);
  const row = page.getByRole('row', { name: new RegExp(roomNumber) });
  await expect(row.getByText('Sin programar')).toBeVisible();

  await row
    .getByRole('button', { name: `Programar mantenimiento de la habitación ${roomNumber}` })
    .click();
  const dialog = page.getByRole('alertdialog');
  await dialog
    .getByLabel('Motivo del mantenimiento *')
    .fill('Revisión anual del aire acondicionado.');
  await dialog.getByLabel('Fecha de inicio *').fill(start);
  await dialog.getByLabel('Fecha estimada de fin *').fill(end);
  await capture(page, '34-mantenimiento-programar');
  // Módulo 2 simulado responde que no hay reservas en esas fechas.
  await dialog.getByRole('button', { name: 'Programar' }).click();

  await expect(
    page.getByText(
      `Mantenimiento programado del ${shown(start)} al ${shown(end)} para la habitación ${roomNumber}.`,
    ),
  ).toBeVisible();
  await expect(row.getByText(`${shown(start)} a ${shown(end)}`)).toBeVisible();
  await capture(page, '35-mantenimiento-programado');

  await row
    .getByRole('button', { name: `Ver informe del mantenimiento de la habitación ${roomNumber}` })
    .click();
  await expect(page.getByText('Revisión anual del aire acondicionado.')).toBeVisible();
  await expect(page.getByText(USERS.maintenance.fullName).last()).toBeVisible();
  await capture(page, '36-mantenimiento-informe');
});
