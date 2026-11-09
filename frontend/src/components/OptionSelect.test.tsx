import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { OptionSelect } from '@/components/OptionSelect';
import { chooseOption } from '@/test/chooseOption';

const OPTIONS = [
  { value: 'DOBLE', label: 'Doble' },
  { value: 'SUITE', label: 'Suite' },
];

describe('OptionSelect', () => {
  it('muestra la opción vacía y devuelve "" al elegirla', async () => {
    const onValueChange = vi.fn();
    render(
      <>
        <label htmlFor="tipo">Tipo</label>
        <OptionSelect
          id="tipo"
          value="DOBLE"
          onValueChange={onValueChange}
          emptyLabel="Todos"
          options={OPTIONS}
        />
      </>,
    );
    const trigger = screen.getByLabelText('Tipo');
    expect(trigger).toHaveTextContent('Doble');

    await chooseOption(trigger, 'Todos');
    expect(onValueChange).toHaveBeenLastCalledWith('');
  });

  it('devuelve el valor de la opción elegida', async () => {
    const onValueChange = vi.fn();
    render(
      <OptionSelect aria-label="Tipo" value="" onValueChange={onValueChange} options={OPTIONS} />,
    );
    await chooseOption(screen.getByRole('combobox', { name: 'Tipo' }), 'Suite');
    expect(onValueChange).toHaveBeenLastCalledWith('SUITE');
  });
});
