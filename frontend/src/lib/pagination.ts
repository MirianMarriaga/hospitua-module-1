/** Página de la URL (`?page=`), a partir de 1; lo que no sea un entero válido es la página 1. */
export function readPage(params: URLSearchParams): number {
  const page = Number(params.get('page'));
  return Number.isInteger(page) && page >= 1 ? page : 1;
}
