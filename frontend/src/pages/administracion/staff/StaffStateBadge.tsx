import { Badge } from '@/components/ui/badge';

/** Estado de la cuenta, con los colores de las vistas: "Activa" o "Desactivada". */
export function StaffStateBadge({ active }: { active: boolean }) {
  return <Badge tone={active ? 'green' : 'gray'}>{active ? 'Activa' : 'Desactivada'}</Badge>;
}
