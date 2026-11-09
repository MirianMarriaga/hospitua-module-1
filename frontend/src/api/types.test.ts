import type { components } from '@/api/schema';
import type { ApiError, PagedResult } from '@/api/types';

type Schemas = components['schemas'];

// Las formas escritas a mano en `types.ts` deben seguir siendo las del contrato: si el backend
// cambia un campo y se regenera `schema.d.ts`, este archivo deja de compilar en `typecheck`.
type SameKeys<A, B> = [keyof A] extends [keyof B]
  ? [keyof B] extends [keyof A]
    ? true
    : false
  : false;

describe('tipos del contrato', () => {
  it('PagedResult tiene los mismos campos que las páginas del contrato', () => {
    const rooms: SameKeys<PagedResult<unknown>, Schemas['PagedResultDtoRoomDto']> = true;
    const history: SameKeys<
      PagedResult<unknown>,
      Schemas['PagedResultDtoRoomStateHistoryRowDto']
    > = true;
    const staff: SameKeys<PagedResult<unknown>, Schemas['PagedResultDtoStaffAccountDto']> = true;
    expect([rooms, history, staff]).toEqual([true, true, true]);
  });

  it('ApiError tiene los mismos campos que el ApiError del contrato', () => {
    const error: SameKeys<ApiError, Schemas['ApiError']> = true;
    expect(error).toBe(true);
  });
});
