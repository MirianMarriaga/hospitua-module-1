import { useEffect, useMemo } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useFieldArray, useForm } from 'react-hook-form';

import type { CheckInValidation } from '@/api/checkInService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Form } from '@/components/ui/form';
import { formatPeople, todayInBogota } from '@/lib/format';
import type { GuestFieldError } from '@/pages/checkin/checkInReducer';
import { GuestBlock } from '@/pages/checkin/GuestBlock';
import {
  createGuestsSchema,
  GUEST_FIELD_TEXTS,
  GUEST_FIELDS,
  initialGuests,
  type GuestsValues,
  type GuestValues,
} from '@/pages/checkin/guestSchema';

interface GuestsStepProps {
  validation: CheckInValidation;
  guests?: GuestValues[];
  fieldError?: GuestFieldError;
  onBack: () => void;
  onContinue: (guests: GuestValues[]) => void;
}

/** Paso 2: un bloque por ocupante (`guestCount` de la reserva). */
export function GuestsStep({
  validation,
  guests,
  fieldError,
  onBack,
  onContinue,
}: GuestsStepProps) {
  const schema = useMemo(() => createGuestsSchema(todayInBogota()), []);
  const form = useForm<GuestsValues>({
    resolver: zodResolver(schema),
    defaultValues: { guests: guests ?? initialGuests(validation.holder, validation.guestCount) },
  });
  const { fields } = useFieldArray({ control: form.control, name: 'guests' });

  useEffect(() => {
    if (!fieldError) {
      return;
    }
    const field = GUEST_FIELDS.find((candidate) => candidate === fieldError.field);
    if (field && fieldError.guestIndex >= 0 && fieldError.guestIndex < validation.guestCount) {
      form.setError(`guests.${fieldError.guestIndex}.${field}`, {
        message: GUEST_FIELD_TEXTS[field],
      });
    }
  }, [fieldError, form, validation.guestCount]);

  const arrayError =
    form.formState.errors.guests?.root?.message ?? form.formState.errors.guests?.message;

  return (
    <Form {...form}>
      <form noValidate onSubmit={form.handleSubmit((values) => onContinue(values.guests))}>
        <Card className="max-w-4xl">
          <CardHeader>
            <CardTitle>2. Datos de huéspedes — Habitación {validation.roomNumber}</CardTitle>
            <CardDescription>
              Captura las identificaciones de los ocupantes de esta habitación. Aforo asignado:{' '}
              {formatPeople(validation.guestCount)}.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            {fieldError && (
              <ErrorAlert message={`Revisa los datos del huésped ${fieldError.guestIndex + 1}.`} />
            )}
            {arrayError && <ErrorAlert message={arrayError} />}
            {fields.map((field, index) => (
              <GuestBlock key={field.id} index={index} holder={validation.holder} />
            ))}
          </CardContent>
          <CardFooter>
            <Button type="button" variant="outline" onClick={onBack}>
              Atrás
            </Button>
            <Button type="submit">Continuar</Button>
          </CardFooter>
        </Card>
      </form>
    </Form>
  );
}
