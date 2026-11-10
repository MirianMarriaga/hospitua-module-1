import type { MaintenanceRoom } from '@/api/maintenanceService';
import { StatusBadge } from '@/components/StatusBadge';
import { Badge } from '@/components/ui/badge';

/** Estado de una habitación del panel de mantenimiento: "Bloqueo técnico (vencido)" en rojo (FR-003). */
export function MaintenanceStateBadge({
  room,
}: {
  room: Pick<MaintenanceRoom, 'status' | 'overdue'>;
}) {
  return room.overdue ? (
    <Badge tone="red">Bloqueo técnico (vencido)</Badge>
  ) : (
    <StatusBadge status={room.status} />
  );
}
