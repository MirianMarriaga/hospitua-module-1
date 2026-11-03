import { Check } from 'lucide-react';

import { cn } from '@/lib/utils';

interface StepProgressProps {
  steps: string[];
  currentStep: number;
}

/** Componente de pasos numerados con indicador de avance (stepper) del prototipo. */
export function StepProgress({ steps, currentStep }: StepProgressProps) {
  return (
    <div className="flex flex-wrap items-center gap-y-2">
      {steps.map((label, index) => {
        const stepNum = index + 1;
        const isDone = stepNum < currentStep;
        const isActive = stepNum === currentStep;

        return (
          <div key={label} className="flex items-center">
            {index > 0 && (
              <div
                className={cn(
                  'mx-2.5 h-0.5 min-w-4 sm:min-w-8',
                  isDone || isActive ? 'bg-state-green' : 'bg-slate-200',
                )}
              />
            )}
            <div
              className={cn(
                'flex items-center gap-2 text-xs font-bold sm:text-sm',
                isDone && 'text-state-green',
                isActive && 'text-brand',
                !isDone && !isActive && 'text-text-lo',
              )}
            >
              <div
                className={cn(
                  'flex size-6 items-center justify-center rounded-full text-xs font-bold border-2',
                  isDone && 'border-state-green bg-state-green text-white',
                  isActive && 'border-brand bg-brand text-white',
                  !isDone && !isActive && 'border-slate-300 bg-white text-text-lo',
                )}
              >
                {isDone ? <Check className="size-3.5 stroke-[3]" /> : stepNum}
              </div>
              <span>{label}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
