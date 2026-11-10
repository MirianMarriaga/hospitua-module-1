import { Link } from 'react-router';

import type { StaffAccount } from '@/api/staffService';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { STAFF_AREA_LABELS } from '@/lib/labels';
import { deactivateStaffPath, editStaffPath } from '@/pages/administracion/staff/staffRoutes';
import { StaffStateBadge } from '@/pages/administracion/staff/StaffStateBadge';

interface StaffTableProps {
  accounts: StaffAccount[];
  emptyMessage: React.ReactNode;
}

/** Tabla de `12-personal.html`: "Editar" y "Desactivar" solo en las cuentas activas (FR-007). */
export function StaffTable({ accounts, emptyMessage }: StaffTableProps) {
  return (
    <Table className="min-w-[760px]">
      <TableHeader>
        <TableRow>
          <TableHead>Usuario</TableHead>
          <TableHead>Nombre</TableHead>
          <TableHead>Área</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>
            <span className="sr-only">Acciones</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {accounts.length === 0 ? (
          <TableRow>
            <TableCell colSpan={5} className="py-10 text-center text-text-lo">
              {emptyMessage}
            </TableCell>
          </TableRow>
        ) : (
          accounts.map((account) => (
            <TableRow key={account.username}>
              <TableCell className="font-mono text-ink">{account.username}</TableCell>
              <TableCell>{account.fullName}</TableCell>
              <TableCell>{STAFF_AREA_LABELS[account.area]}</TableCell>
              <TableCell>
                <StaffStateBadge active={account.active} />
              </TableCell>
              <TableCell>
                <div className="flex justify-end gap-2">
                  {account.active ? (
                    <>
                      <Button asChild size="sm">
                        <Link to={editStaffPath(account.username)}>Editar</Link>
                      </Button>
                      <Button asChild size="sm" variant="ink">
                        <Link to={deactivateStaffPath(account.username)}>Desactivar</Link>
                      </Button>
                    </>
                  ) : (
                    <span className="text-text-lo">—</span>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
