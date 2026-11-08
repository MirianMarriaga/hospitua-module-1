import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { RoomNumberSearch } from '@/components/RoomNumberSearch';
import { filterByRoomNumber } from '@/lib/roomNumberSearch';

const ROOMS = [{ roomNumber: '101' }, { roomNumber: '204' }];

function Harness() {
  const [query, setQuery] = useState('');
  const rooms = filterByRoomNumber(ROOMS, query);
  return (
    <>
      <RoomNumberSearch value={query} onChange={setQuery} />
      <ul>
        {rooms.map((room) => (
          <li key={room.roomNumber}>{room.roomNumber}</li>
        ))}
      </ul>
    </>
  );
}

describe('RoomNumberSearch', () => {
  it('filtra por número exacto y al vaciarse vuelve la lista completa', async () => {
    render(<Harness />);
    const input = screen.getByRole('searchbox', { name: 'Buscar habitación por número' });
    expect(input).toHaveAttribute('placeholder', 'Buscar habitación por número…');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);

    await userEvent.type(input, '204');
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['204']);

    await userEvent.clear(input);
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });
});
