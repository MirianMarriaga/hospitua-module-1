import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Pagination } from '@/components/Pagination';

describe('Pagination', () => {
  it('muestra el rango y cambia de página', async () => {
    const onPageChange = vi.fn();
    render(
      <Pagination
        page={2}
        size={10}
        totalElements={24}
        totalPages={3}
        onPageChange={onPageChange}
      />,
    );

    expect(screen.getByText('Mostrando 11–20 de 24')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Página siguiente' }));
    await userEvent.click(screen.getByRole('button', { name: 'Página anterior' }));

    expect(onPageChange.mock.calls).toEqual([[3], [1]]);
  });

  it('deshabilita los extremos y ajusta la última página', () => {
    render(
      <Pagination page={3} size={10} totalElements={24} totalPages={3} onPageChange={vi.fn()} />,
    );

    expect(screen.getByText('Mostrando 21–24 de 24')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Página siguiente' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Página anterior' })).toBeEnabled();
  });

  it('no se muestra sin resultados', () => {
    const { container } = render(
      <Pagination page={1} size={10} totalElements={0} totalPages={0} onPageChange={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
