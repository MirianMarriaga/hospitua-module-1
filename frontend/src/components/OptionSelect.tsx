import type { ComponentProps } from 'react';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

export interface SelectOption {
  value: string;
  label: string;
}

/** Radix no admite `""` en un `SelectItem`; la opción vacía ("Todos", "Selecciona…") usa este valor. */
const EMPTY_VALUE = '__vacio__';

interface OptionSelectProps extends Omit<
  ComponentProps<typeof SelectTrigger>,
  'value' | 'defaultValue' | 'onChange'
> {
  value: string;
  onValueChange: (value: string) => void;
  options: readonly SelectOption[];
  /** Primera opción con valor vacío, por ejemplo "Todos" o "Selecciona…". */
  emptyLabel?: string;
}

/**
 * Lista desplegable de una opción con el `Select` de shadcn, cuya lista es de vidrio (`glass-pop`)
 * en lugar de la del navegador. Las propiedades que sobran (id, aria-*, ref de React Hook Form)
 * van al botón que la abre, así la etiqueta del campo y el foco del error siguen funcionando.
 */
export function OptionSelect({
  value,
  onValueChange,
  options,
  emptyLabel,
  disabled,
  ...triggerProps
}: OptionSelectProps) {
  const hasEmpty = emptyLabel !== undefined;
  return (
    <Select
      value={value === '' && hasEmpty ? EMPTY_VALUE : value}
      onValueChange={(next) => onValueChange(next === EMPTY_VALUE ? '' : next)}
      disabled={disabled}
    >
      <SelectTrigger {...triggerProps}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {hasEmpty && <SelectItem value={EMPTY_VALUE}>{emptyLabel}</SelectItem>}
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
