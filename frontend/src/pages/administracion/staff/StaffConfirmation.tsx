import { CircleCheck } from 'lucide-react';
import { Link } from 'react-router';

import type { StaffAccount } from '@/api/staffService';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { STAFF_LIST_PATH } from '@/pages/administracion/staff/staffRoutes';
import { StaffSummary } from '@/pages/administracion/staff/StaffSummary';

interface StaffConfirmationProps {
  account: StaffAccount;
  /** Solo tras registrar: vuelve al formulario vacío. */
  onRegisterAnother?: () => void;
}

/**
 * Confirmación de `administrador/14-personal-exito.html` (`plan-gestionar-personal.md` T017): la
 * del registro ofrece "Registrar otra persona"; la de la edición no. Muestra usuario, nombre, área
 * y estado, sin la contraseña (FR-006).
 */
export function StaffConfirmation({ account, onRegisterAnother }: StaffConfirmationProps) {
  const registered = onRegisterAnother !== undefined;
  return (
    <Card className="max-w-2xl">
      <CardContent className="pt-6">
        <div className="mb-6 flex items-start gap-4">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-state-green-bg text-state-green">
            <CircleCheck className="size-7" aria-hidden />
          </span>
          <div>
            <h1 className="text-xl font-bold text-ink">
              Cuenta de {account.fullName} {registered ? 'registrada' : 'actualizada'}
            </h1>
            <p className="mt-1 text-sm text-text-lo">
              {registered
                ? 'Ya puede iniciar sesión con su usuario y contraseña.'
                : 'Los cambios ya están guardados.'}
            </p>
          </div>
        </div>
        <StaffSummary account={account} />
      </CardContent>
      <CardFooter>
        {registered && (
          <Button variant="outline" onClick={onRegisterAnother}>
            Registrar otra persona
          </Button>
        )}
        <Button asChild>
          <Link to={STAFF_LIST_PATH}>Volver al personal</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
