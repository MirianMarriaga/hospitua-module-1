import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';

import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { OptionSelect } from '@/components/OptionSelect';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { ROOM_CATEGORIES, ROOM_CATEGORY_LABELS } from '@/lib/labels';
import {
  EMPTY_ROOM,
  registerRoomSchema,
  toRegisterRoomRequest,
  type RegisterRoomValues,
} from '@/pages/administracion/registerRoomSchema';
import { RegisterRoomSuccess } from '@/pages/administracion/register/RegisterRoomSuccess';
import { useRegisterRoom } from '@/pages/administracion/register/useRegisterRoom';

const REGISTER_ERRORS = { VALIDATION_ERROR: 'Revisa los datos de la habitación.' };

export function RegisterRoomPage() {
  const form = useForm<RegisterRoomValues>({
    resolver: zodResolver(registerRoomSchema),
    defaultValues: EMPTY_ROOM,
  });

  const mutation = useRegisterRoom();

  function submit(values: RegisterRoomValues) {
    mutation.mutate(toRegisterRoomRequest(values), {
      onError: (error, request) => {
        if (errorCodeOf(error) === 'ROOM_NUMBER_ALREADY_EXISTS') {
          form.setError('roomNumber', {
            message: `Ya existe una habitación con el número ${request.roomNumber.toUpperCase()}.`,
          });
        }
      },
    });
  }

  if (mutation.isSuccess) {
    return (
      <div>
        <RegisterRoomSuccess
          room={mutation.data}
          onRegisterAnother={() => {
            form.reset(EMPTY_ROOM);
            mutation.reset();
          }}
        />
      </div>
    );
  }

  const showGeneralError =
    mutation.isError && errorCodeOf(mutation.error) !== 'ROOM_NUMBER_ALREADY_EXISTS';

  return (
    <div>
      <PageHeader
        title="Registrar habitación"
        subtitle="La habitación quedará disponible en el inventario apenas se registre."
      />
      <Form {...form}>
        <form noValidate onSubmit={form.handleSubmit(submit)}>
          <Card className="max-w-2xl">
            <CardContent className="grid gap-6 pt-6">
              {showGeneralError && (
                <ErrorAlert message={errorMessage(mutation.error, REGISTER_ERRORS)} />
              )}
              <fieldset className="grid gap-4 sm:grid-cols-2">
                <legend className="mb-3 text-sm font-bold text-ink">Identificación</legend>
                <FormField
                  control={form.control}
                  name="roomNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Número de habitación *</FormLabel>
                      <FormControl>
                        <Input placeholder="Ej. 204" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="floor"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Piso *</FormLabel>
                      <FormControl>
                        <Input inputMode="numeric" placeholder="Ej. 2" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </fieldset>

              <fieldset className="grid gap-4 sm:grid-cols-3">
                <legend className="mb-3 text-sm font-bold text-ink">Categorización y tarifa</legend>
                <FormField
                  control={form.control}
                  name="categoryRoom"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Tipo *</FormLabel>
                      <FormControl>
                        <OptionSelect
                          value={field.value}
                          onValueChange={field.onChange}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          emptyLabel="Selecciona…"
                          options={ROOM_CATEGORIES.map((category) => ({
                            value: category,
                            label: ROOM_CATEGORY_LABELS[category],
                          }))}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="maxCapacity"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Capacidad máxima (personas) *</FormLabel>
                      <FormControl>
                        <Input inputMode="numeric" placeholder="Ej. 2" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="baseRate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Tarifa base por noche (COP) *</FormLabel>
                      <FormControl>
                        <Input inputMode="decimal" placeholder="Ej. 250000" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </fieldset>
              <p className="text-xs text-text-lo">* Campos obligatorios</p>
            </CardContent>
            <CardFooter>
              <Button variant="outline" asChild>
                <Link to="/administracion/inventario">Cancelar</Link>
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Registrando…' : 'Registrar habitación'}
              </Button>
            </CardFooter>
          </Card>
        </form>
      </Form>
    </div>
  );
}
