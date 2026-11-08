import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap',
  {
    variants: {
      tone: {
        green: 'bg-state-green-bg text-state-green',
        blue: 'bg-state-blue-bg text-state-blue',
        amber: 'bg-state-amber-bg text-state-amber',
        red: 'bg-state-red-bg text-state-red',
        purple: 'bg-state-purple-bg text-state-purple',
        orange: 'bg-state-orange-bg text-state-orange',
        crimson: 'bg-state-crimson-bg text-state-crimson',
        gray: 'bg-state-gray-bg text-state-gray',
        due: 'bg-due text-due-foreground',
      },
    },
    defaultVariants: { tone: 'gray' },
  },
);

function Badge({
  className,
  tone,
  ...props
}: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ tone }), className)} {...props} />;
}

export { Badge, badgeVariants };
