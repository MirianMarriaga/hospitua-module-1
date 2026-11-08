import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmDialog } from '@/components/ConfirmDialog';

function renderDialog(props: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const onConfirm = vi.fn();
  const onOpenChange = vi.fn();
  render(
    <ConfirmDialog
      open
      onOpenChange={onOpenChange}
      title="Iniciar limpieza"
      description="La habitación 305 quedará a tu cargo."
      confirmLabel="Iniciar limpieza"
      onConfirm={onConfirm}
      {...props}
    />,
  );
  return { onConfirm, onOpenChange };
}

describe('ConfirmDialog', () => {
  it('muestra título, texto, aviso y las dos acciones', () => {
    renderDialog({ warning: 'La habitación tiene un mantenimiento programado.' });
    const dialog = screen.getByRole('alertdialog', { name: 'Iniciar limpieza' });
    expect(dialog).toHaveAccessibleDescription('La habitación 305 quedará a tu cargo.');
    expect(screen.getByRole('alert')).toHaveTextContent(
      'La habitación tiene un mantenimiento programado.',
    );
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });

  it('confirmar llama a la acción sin cerrar el diálogo', async () => {
    const { onConfirm, onOpenChange } = renderDialog();
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar limpieza' }));
    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it('cancelar y Esc cierran el diálogo sin confirmar', async () => {
    const { onConfirm, onOpenChange } = renderDialog();
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onOpenChange).toHaveBeenLastCalledWith(false);
    await userEvent.keyboard('{Escape}');
    expect(onOpenChange).toHaveBeenCalledTimes(2);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('mientras la acción está en curso no se puede confirmar ni cerrar', async () => {
    const { onOpenChange } = renderDialog({ pending: true });
    expect(screen.getByRole('button', { name: 'Iniciar limpieza' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    await userEvent.keyboard('{Escape}');
    expect(onOpenChange).not.toHaveBeenCalled();
  });
});
