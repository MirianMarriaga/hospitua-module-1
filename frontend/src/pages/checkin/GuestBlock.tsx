import { useFormContext, useWatch } from 'react-hook-form';

import type { CheckInHolder } from '@/api/checkInService';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { DOCUMENT_TYPE_LABELS, DOCUMENT_TYPES } from '@/lib/labels';
import {
  emptyGuest,
  holderGuest,
  isForeigner,
  type GuestsValues,
} from '@/pages/checkin/guestSchema';

interface GuestBlockProps {
  index: number;
  holder: CheckInHolder;
}

/** Un ocupante. El primero es el titular: con la casilla marcada sus datos de la reserva son de solo lectura. */
export function GuestBlock({ index, holder }: GuestBlockProps) {
  const form = useFormContext<GuestsValues>();
  const isHolderBlock = index === 0;
  const isReservationGuest = useWatch({
    control: form.control,
    name: `guests.${index}.isReservationGuest`,
  });
  const nationality = useWatch({ control: form.control, name: `guests.${index}.nationality` });
  const locked = isHolderBlock && isReservationGuest;
  const foreigner = isForeigner(nationality ?? '');

  function toggleHolder(checked: boolean) {
    const current = form.getValues(`guests.${index}`);
    const base = checked ? holderGuest(holder) : emptyGuest();
    form.setValue(
      `guests.${index}`,
      {
        ...base,
        birthDate: current.birthDate,
        originPlace: current.originPlace,
        destinationPlace: current.destinationPlace,
      },
      { shouldValidate: form.formState.isSubmitted },
    );
  }

  return (
    <fieldset
      className="grid gap-4 rounded-lg border border-line p-4"
      aria-label={`Huésped ${index + 1}`}
    >
      <legend className="flex items-center gap-2 px-1 text-sm font-bold text-ink">
        Huésped {index + 1}
        {locked && <Badge tone="blue">Titular de la reserva</Badge>}
      </legend>
      {isHolderBlock && (
        <div className="flex items-center gap-2">
          <Checkbox
            id={`guest-${index}-holder`}
            checked={isReservationGuest}
            onCheckedChange={(checked) => toggleHolder(checked === true)}
          />
          <Label htmlFor={`guest-${index}-holder`} className="text-sm font-normal text-text-hi">
            El titular de la reserva se aloja en esta habitación
          </Label>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          control={form.control}
          name={`guests.${index}.firstName`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Nombres</FormLabel>
              <FormControl>
                <Input readOnly={locked} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name={`guests.${index}.lastName`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Apellidos</FormLabel>
              <FormControl>
                <Input readOnly={locked} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name={`guests.${index}.documentType`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Tipo de documento</FormLabel>
              <FormControl>
                <NativeSelect {...field} disabled={locked}>
                  {DOCUMENT_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {DOCUMENT_TYPE_LABELS[type]}
                    </option>
                  ))}
                </NativeSelect>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name={`guests.${index}.documentNumber`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Número de documento</FormLabel>
              <FormControl>
                <Input readOnly={locked} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name={`guests.${index}.nationality`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Nacionalidad</FormLabel>
              <FormControl>
                <Input readOnly={locked} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name={`guests.${index}.birthDate`}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Fecha de nacimiento</FormLabel>
              <FormControl>
                <Input type="date" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>
      {foreigner && (
        <div className="grid gap-4 rounded-lg bg-state-amber-bg/60 p-4 sm:grid-cols-2">
          <p className="text-xs font-semibold text-state-amber sm:col-span-2">
            Registro migratorio: huésped extranjero
          </p>
          <FormField
            control={form.control}
            name={`guests.${index}.originPlace`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Lugar de procedencia</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name={`guests.${index}.destinationPlace`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Lugar de destino</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      )}
    </fieldset>
  );
}
