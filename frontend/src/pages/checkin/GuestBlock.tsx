import { useFormContext, useWatch } from 'react-hook-form';

import type { CheckInHolder } from '@/api/checkInService';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { OptionSelect } from '@/components/OptionSelect';
import { formatDate, todayInBogota } from '@/lib/format';
import { DOCUMENT_TYPE_LABELS, DOCUMENT_TYPES } from '@/lib/labels';
import {
  COUNTRIES,
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
/** Países de la lista; si el huésped trae uno que no está (de la reserva), se agrega al principio. */
function countryOptions(current: string) {
  const known = (COUNTRIES as readonly string[]).includes(current);
  const countries = current && !known ? [current, ...COUNTRIES] : COUNTRIES;
  return countries.map((country) => ({ value: country, label: country }));
}

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
                <OptionSelect
                  value={field.value}
                  onValueChange={field.onChange}
                  onBlur={field.onBlur}
                  ref={field.ref}
                  disabled={locked}
                  options={DOCUMENT_TYPES.map((type) => ({
                    value: type,
                    label: `${type} — ${DOCUMENT_TYPE_LABELS[type]}`,
                  }))}
                />
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
                <OptionSelect
                  value={field.value}
                  onBlur={field.onBlur}
                  ref={field.ref}
                  disabled={locked}
                  onValueChange={(newCountry) => {
                    field.onChange(newCountry);
                    if (newCountry === 'Colombia') {
                      form.setValue(`guests.${index}.originPlace`, '');
                      form.setValue(`guests.${index}.destinationPlace`, '');
                    }
                  }}
                  options={countryOptions(field.value)}
                />
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
            Datos de control migratorio
          </p>
          <FormField
            control={form.control}
            name={`guests.${index}.originPlace`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Lugar de procedencia</FormLabel>
                <FormControl>
                  <Input placeholder="Ej. Miami, EE. UU." {...field} />
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
                  <Input placeholder="Ej. Cartagena, Colombia" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid gap-1.5">
            <Label className="text-xs font-medium text-text-lo">Movimiento</Label>
            <Input value="Entrada" readOnly className="bg-paper text-text-lo cursor-not-allowed" />
          </div>
          <div className="grid gap-1.5">
            <Label className="text-xs font-medium text-text-lo">Fecha de registro</Label>
            <Input
              value={formatDate(todayInBogota())}
              readOnly
              className="bg-paper text-text-lo cursor-not-allowed"
            />
          </div>
        </div>
      )}
    </fieldset>
  );
}
