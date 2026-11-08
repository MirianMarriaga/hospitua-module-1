/**
 * Búsqueda por número exacto de los paneles de limpieza y mantenimiento (FR-007 y FR-008 de
 * `plan-consultar-panel-limpieza.md` y `plan-consultar-panel-mantenimiento.md`): se hace en el
 * frontend sobre la lista ya cargada y, con la búsqueda vacía, devuelve la lista completa.
 */
export function filterByRoomNumber<T extends { roomNumber: string }>(
  rooms: readonly T[],
  query: string,
): readonly T[] {
  const number = query.trim();
  return number === '' ? rooms : rooms.filter((room) => room.roomNumber === number);
}

/** Mensaje de búsqueda sin coincidencias (FR-008). */
export function roomNotFoundMessage(query: string): string {
  return `No se encontró la habitación ${query.trim()}`;
}
