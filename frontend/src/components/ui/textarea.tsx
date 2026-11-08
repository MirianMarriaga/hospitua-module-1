import * as React from 'react';

import { cn } from '@/lib/utils';

function Textarea({ className, ...props }: React.ComponentProps<'textarea'>) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        'flex min-h-20 w-full rounded-lg border border-input bg-field px-3 py-2 text-sm text-text-hi placeholder:text-text-lo focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/20 read-only:bg-paper-dim read-only:text-text-lo disabled:cursor-not-allowed disabled:bg-paper-dim aria-invalid:border-due aria-invalid:ring-due/20',
        className,
      )}
      {...props}
    />
  );
}

export { Textarea };
