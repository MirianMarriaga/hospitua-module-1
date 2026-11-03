import { Check } from 'lucide-react';

import { cn } from '@/lib/utils';

/** Pasos de los asistentes de check-in y check-out; `current` empieza en 1. */
export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  return (
    <ol className="mb-6 flex flex-wrap items-center gap-3" aria-label="Pasos">
      {steps.map((label, index) => {
        const number = index + 1;
        const done = number < current;
        const active = number === current;
        return (
          <li
            key={label}
            className="flex items-center gap-2 text-sm"
            aria-current={active ? 'step' : undefined}
          >
            <span
              className={cn(
                'flex size-7 items-center justify-center rounded-full border text-xs font-bold',
                done && 'border-state-green bg-state-green text-white',
                active && 'border-primary bg-primary text-white',
                !done && !active && 'border-line bg-paper text-text-lo',
              )}
            >
              {done ? <Check className="size-3.5" aria-hidden /> : number}
            </span>
            <span className={cn(active ? 'font-semibold text-ink' : 'text-text-lo')}>{label}</span>
            {number < steps.length && <span className="mx-1 h-px w-6 bg-line" aria-hidden />}
          </li>
        );
      })}
    </ol>
  );
}
