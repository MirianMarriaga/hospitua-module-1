import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const alertVariants = cva(
  'block rounded-lg border px-4 py-3 text-sm has-[>svg]:flex has-[>svg]:gap-3 [&_svg]:mt-0.5 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        info: 'border-state-blue/20 bg-state-blue-bg text-state-blue',
        warning: 'border-state-amber/20 bg-state-amber-bg text-state-amber',
        destructive: 'border-state-red/20 bg-state-red-bg text-state-red',
        success: 'border-state-green/20 bg-state-green-bg text-state-green',
      },
    },
    defaultVariants: { variant: 'info' },
  },
);

function Alert({
  className,
  variant,
  ...props
}: React.ComponentProps<'div'> & VariantProps<typeof alertVariants>) {
  return (
    <div
      data-slot="alert"
      role="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  );
}

export { Alert };
