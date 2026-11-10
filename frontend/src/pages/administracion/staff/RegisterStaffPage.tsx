import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';

import { ErrorAlert } from '@/components/ErrorAlert';
import { OptionSelect } from '@/components/OptionSelect';
import { PageHeader } from '@/components/PageHeader';
import { PasswordInput } from '@/components/PasswordInput';
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
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { STAFF_AREAS, STAFF_AREA_LABELS } from '@/lib/labels';
import { StaffConfirmation } from '@/pages/administracion/staff/StaffConfirmation';
import { STAFF_LIST_PATH } from '@/pages/administracion/staff/staffRoutes';
import {
  EMPTY_STAFF,
  fieldErrorText,
  registerStaffSchema,
  rejectedFields,
  toRegisterStaffRequest,
  type RegisterStaffValues,
} from '@/pages/administracion/staff/staffSchema';
import { useRegisterStaff } from '@/pages/administracion/staff/useStaff';

const FIELDS = ['username', 'password', 'fullName', 'area'] as const;

const REGISTER_ERRORS = { VALIDATION_ERROR: 'Revisa los datos de la cuenta.' };

/**
 * Registrar personal de `administrador/13-personal-registrar.html` (`plan-gestionar-personal.md`
 * T017): usuario, contraseña, nombre y área; al terminar muestra la confirmación de la vista 14.
 * La contraseña solo vive en el formulario y en el cuerpo de la petición (FR-001 a FR-006).
 */
export function RegisterStaffPage() {
  const form = useForm<RegisterStaffValues>({
    resolver: zodResolver(registerStaffSchema),
    defaultValues: EMPTY_STAFF,
  });

  const mutation = useRegisterStaff();

  function submit(values: RegisterStaffValues) {
    mutation.mutate(toRegisterStaffRequest(values), {
      onError: (error, request) => {
        if (errorCodeOf(error) === 'USERNAME_ALREADY_EXISTS') {
          form.setError('username', {
            message: `Ya existe una cuenta con el usuario ${request.username}.`,
          });
          return;
        }
        for (const field of rejectedFields(error, FIELDS)) {
          form.setError(field, {
            message: fieldErrorText(registerStaffSchema, form.getValues(), field),
          });
        }
      },
    });
  }

  if (mutation.isSuccess) {
    return (
      <StaffConfirmation
        account={mutation.data}
        onRegisterAnother={() => {
          form.reset(EMPTY_STAFF);
          mutation.reset();
        }}
      />
    );
  }

  const showGeneralError =
    mutation.isError &&
    errorCodeOf(mutation.error) !== 'USERNAME_ALREADY_EXISTS' &&
    rejectedFields(mutation.error, FIELDS).length === 0;

  return (
    <div>
      <PageHeader
        title="Registrar personal"
        subtitle="La persona podrá iniciar sesión con este usuario y contraseña."
      />
      <Form {...form}>
        <form noValidate onSubmit={form.handleSubmit(submit)}>
          <Card className="max-w-2xl">
            <CardContent className="grid gap-6 pt-6">
              {showGeneralError && (
                <ErrorAlert message={errorMessage(mutation.error, REGISTER_ERRORS)} />
              )}
              <fieldset className="grid gap-4 sm:grid-cols-2">
                <legend className="mb-3 text-sm font-bold text-ink">Cuenta</legend>
                <FormField
                  control={form.control}
                  name="username"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Usuario *</FormLabel>
                      <FormControl>
                        <Input autoComplete="off" placeholder="Ej. jramirez" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Contraseña *</FormLabel>
                      <FormControl>
                        <PasswordInput
                          autoComplete="new-password"
                          placeholder="Mínimo 8 caracteres"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </fieldset>

              <fieldset className="grid gap-4 sm:grid-cols-2">
                <legend className="mb-3 text-sm font-bold text-ink">Datos de la persona</legend>
                <FormField
                  control={form.control}
                  name="fullName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Nombre completo *</FormLabel>
                      <FormControl>
                        <Input autoComplete="off" placeholder="Ej. Jorge Ramírez" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="area"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Área *</FormLabel>
                      <FormControl>
                        <OptionSelect
                          value={field.value}
                          onValueChange={field.onChange}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          emptyLabel="Seleccionar"
                          options={STAFF_AREAS.map((area) => ({
                            value: area,
                            label: STAFF_AREA_LABELS[area],
                          }))}
                        />
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
                <Link to={STAFF_LIST_PATH}>Cancelar</Link>
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Registrando…' : 'Registrar personal'}
              </Button>
            </CardFooter>
          </Card>
        </form>
      </Form>
    </div>
  );
}
