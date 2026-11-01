import * as React from 'react';

import { cn } from '@/lib/utils';

function NativeSelect({ className, ...props }: React.ComponentProps<'select'>) {
  return (
    <select
      data-slot="native-select"
      className={cn(
        'flex h-10 w-full rounded-lg border border-input bg-paper px-3 text-sm text-text-hi focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/20 disabled:cursor-not-allowed disabled:bg-paper-dim aria-invalid:border-due',
        className,
      )}
      {...props}
    />
  );
}

export { NativeSelect };
