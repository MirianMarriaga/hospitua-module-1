import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router';

import type { StaffAccountDetail } from '@/api/staffService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { OptionSelect } from '@/components/OptionSelect';
import { PageHeader } from '@/components/PageHeader';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { STAFF_AREAS, STAFF_AREA_LABELS } from '@/lib/labels';
import { StaffConfirmation } from '@/pages/administracion/staff/StaffConfirmation';
import { STAFF_LIST_PATH } from '@/pages/administracion/staff/staffRoutes';
import {
  editStaffSchema,
  fieldErrorText,
  rejectedFields,
  toEditStaffRequest,
  toEditStaffValues,
  type EditStaffValues,
} from '@/pages/administracion/staff/staffSchema';
import { useEditStaff, useStaffAccount } from '@/pages/administracion/staff/useStaff';

const FIELDS = ['password', 'fullName', 'area'] as const;

const LAST_ADMINISTRATOR_TEXT =
  'No se puede cambiar el área: es la única cuenta activa de Administración.';

// Textos de la tabla de errores de `PATCH /api/staff/{username}` (plan-gestionar-personal.md).
const EDIT_ERRORS = {
  VALIDATION_ERROR: 'Revisa los datos de la cuenta.',
  STAFF_NOT_FOUND: 'No se encontró la cuenta.',
  ACCOUNT_INACTIVE: 'La cuenta está desactivada; no se puede editar.',
};

/** Tarjeta de las vistas cuando la cuenta no se puede editar: título, aviso y vuelta a la lista. */
function BlockedCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card className="max-w-2xl">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
      <CardFooter>
        <Button asChild>
          <Link to={STAFF_LIST_PATH}>Volver al personal</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}

/**
 * Editar personal de `administrador/15-personal-editar.html` (`plan-gestionar-personal.md` T028):
 * carga la cuenta con su `version`; el usuario es de solo lectura y la contraseña, opcional
 * (FR-008).
 */
export function EditStaffPage() {
  const { username } = useParams();
  const accountQuery = useStaffAccount(username);
  // Cambia al recargar tras una edición simultánea, para que el formulario tome los datos nuevos.
  const [formKey, setFormKey] = useState(0);

  if (accountQuery.isPending) {
    return <p className="py-6 text-sm text-text-lo">Cargando la cuenta…</p>;
  }
  if (accountQuery.isError) {
    if (errorCodeOf(accountQuery.error) === 'STAFF_NOT_FOUND') {
      return (
        <BlockedCard title="Cuenta no encontrada">
          <p className="text-sm text-text-lo">No existe ninguna cuenta con ese usuario.</p>
        </BlockedCard>
      );
    }
    return <ErrorAlert message={errorMessage(accountQuery.error)} />;
  }

  return (
    <EditStaffForm
      key={formKey}
      account={accountQuery.data}
      onReload={() => {
        void accountQuery.refetch().then(() => setFormKey((key) => key + 1));
      }}
    />
  );
}

function EditStaffForm({
  account,
  onReload,
}: {
  account: StaffAccountDetail;
  onReload: () => void;
}) {
  const form = useForm<EditStaffValues>({
    resolver: zodResolver(editStaffSchema),
    defaultValues: toEditStaffValues(account),
  });
  const mutation = useEditStaff(account.username);

  function submit(values: EditStaffValues) {
    mutation.mutate(toEditStaffRequest(values, account.version), {
      onError: (error) => {
        if (errorCodeOf(error) === 'LAST_ADMINISTRATOR') {
          form.setError('area', { message: LAST_ADMINISTRATOR_TEXT });
          return;
        }
        for (const field of rejectedFields(error, FIELDS)) {
          form.setError(field, {
            message: fieldErrorText(editStaffSchema, form.getValues(), field),
          });
        }
      },
    });
  }

  // La confirmación sale de la respuesta: no desaparece cuando la cuenta se vuelve a consultar.
  if (mutation.isSuccess) {
    return <StaffConfirmation account={mutation.data} />;
  }

  if (!account.active) {
    return (
      <BlockedCard title={`No se puede editar la cuenta ${account.username}`}>
        <Alert variant="warning">La cuenta está desactivada; no se puede editar.</Alert>
      </BlockedCard>
    );
  }

  const code = errorCodeOf(mutation.error);
  const concurrentError = code === 'CONCURRENT_UPDATE';
  const showGeneralError =
    mutation.isError &&
    !concurrentError &&
    code !== 'LAST_ADMINISTRATOR' &&
    rejectedFields(mutation.error, FIELDS).length === 0;

  return (
    <div>
      <PageHeader
        title="Editar personal"
        subtitle="El usuario no se puede cambiar, porque identifica a la persona en el historial."
      />
      <Form {...form}>
        <form noValidate onSubmit={form.handleSubmit(submit)}>
          <Card className="max-w-2xl">
            <CardContent className="grid gap-6 pt-6">
              {concurrentError && (
                <Alert variant="warning">
                  La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver
                  los datos actuales.{' '}
                  <Button
                    type="button"
                    variant="link"
                    onClick={onReload}
                    className="h-auto p-0 font-semibold text-inherit underline hover:no-underline"
                  >
                    Cargar los datos actuales
                  </Button>
                </Alert>
              )}
              {showGeneralError && (
                <ErrorAlert message={errorMessage(mutation.error, EDIT_ERRORS)} />
              )}
              <fieldset className="grid gap-4 sm:grid-cols-2">
                <legend className="mb-3 text-sm font-bold text-ink">Cuenta</legend>
                <div className="grid gap-2">
                  <Label htmlFor="usuario">Usuario</Label>
                  <Input id="usuario" className="font-mono" value={account.username} readOnly />
                </div>
                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Contraseña nueva</FormLabel>
                      <FormControl>
                        <Input
                          type="password"
                          autoComplete="new-password"
                          placeholder="Déjala vacía para no cambiarla"
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
                        <Input autoComplete="off" {...field} />
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
                {mutation.isPending ? 'Guardando…' : 'Guardar cambios'}
              </Button>
            </CardFooter>
          </Card>
        </form>
      </Form>
    </div>
  );
}
