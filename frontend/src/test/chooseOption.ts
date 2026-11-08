import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/** Elige una opción de un `OptionSelect`: abre la lista desde su botón y hace clic en la opción. */
export async function chooseOption(trigger: HTMLElement, option: string | RegExp): Promise<void> {
  await userEvent.click(trigger);
  await userEvent.click(await screen.findByRole('option', { name: option }));
}
