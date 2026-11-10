import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

/**
 * Campo de contraseña con el botón del ojo para verla mientras se escribe. Empieza oculta y lo que
 * se muestra no sale del campo. Recibe las mismas propiedades que `Input`, salvo `type`.
 */
export function PasswordInput({
  className,
  ...props
}: Omit<React.ComponentProps<'input'>, 'type'>) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input type={visible ? 'text' : 'password'} className={cn('pr-10', className)} {...props} />
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={() => setVisible(!visible)}
        className="absolute top-1/2 right-1 size-8 -translate-y-1/2 text-text-lo hover:text-ink"
        aria-label={visible ? 'Ocultar contraseña' : 'Ver contraseña'}
      >
        {visible ? (
          <EyeOff className="size-4" aria-hidden />
        ) : (
          <Eye className="size-4" aria-hidden />
        )}
      </Button>
    </div>
  );
}
