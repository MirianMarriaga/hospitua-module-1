/** Rutas de la sección "Personal" del Administrador. */
export const STAFF_LIST_PATH = '/administracion/personal';
export const REGISTER_STAFF_PATH = '/administracion/personal/nuevo';

export function editStaffPath(username: string): string {
  return `${STAFF_LIST_PATH}/${encodeURIComponent(username)}/editar`;
}

export function deactivateStaffPath(username: string): string {
  return `${STAFF_LIST_PATH}/${encodeURIComponent(username)}/desactivar`;
}
