import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { Slot } from 'radix-ui';

import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow-primary hover:bg-primary-dark',
        ink: 'bg-ink text-ink-foreground hover:bg-ink-hover',
        outline: 'border border-line bg-field text-text-hi backdrop-blur-sm hover:bg-accent',
        ghost: 'text-ink hover:bg-accent',
        link: 'text-primary underline-offset-4 hover:underline',
        destructive: 'bg-due text-due-foreground hover:bg-due/90',
        // btn-danger-outline de las vistas: "Reportar daño" en los paneles de limpieza y mantenimiento.
        'destructive-outline':
          'border border-state-red/40 bg-field text-state-red backdrop-blur-sm hover:bg-state-red-bg',
      },
      size: {
        default: 'h-10 px-4',
        sm: 'h-8 px-3 text-xs',
        lg: 'h-11 px-6',
        icon: 'size-9',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<'button'> & VariantProps<typeof buttonVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : 'button';
  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };
