import { useMutation } from '@tanstack/react-query';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation } from 'react-router';
import { z } from 'zod';

import { login } from '@/api/authService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { homeOf } from '@/layouts/areaNavigation';
import { errorMessage } from '@/lib/errorMessages';
import { useSessionStore } from '@/stores/sessionStore';

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Ingresa tu usuario.'),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
});

type LoginValues = z.infer<typeof loginSchema>;

const LOGIN_ERRORS = { INVALID_CREDENTIALS: 'Usuario o contraseña incorrectos.' };

/** No hay vista del login (punto abierto 2 del plan base): sigue el estilo de las demás. */
export function LoginPage() {
  const session = useSessionStore((state) => state.session);
  const expired = useSessionStore((state) => state.expired);
  const saveSession = useSessionStore((state) => state.login);
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });

  const mutation = useMutation({
    mutationFn: login,
    // Al guardar la sesión, el render siguiente redirige a la pantalla pedida o al inicio del área.
    onSuccess: saveSession,
  });

  if (session) {
    return <Navigate to={from ?? homeOf(session.area)} replace />;
  }

  return (
    <div className="flex min-h-screen flex-col bg-paper-dim">
      <header className="flex h-16 items-center bg-navy px-6 text-white">
        <span className="text-base font-bold tracking-tight">Hotel Hospitua</span>
      </header>
      <main className="flex flex-1 items-center justify-center p-6">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle className="text-xl">Iniciar sesión</CardTitle>
            <CardDescription>Ingresa con tu usuario del hotel.</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form
                noValidate
                className="grid gap-4"
                onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
              >
                {expired && !mutation.isError && (
                  <Alert variant="warning">Tu sesión terminó. Inicia sesión de nuevo.</Alert>
                )}
                {mutation.isError && (
                  <ErrorAlert message={errorMessage(mutation.error, LOGIN_ERRORS)} />
                )}
                <FormField
                  control={form.control}
                  name="username"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Usuario</FormLabel>
                      <FormControl>
                        <Input autoComplete="username" autoFocus {...field} />
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
                      <FormLabel>Contraseña</FormLabel>
                      <FormControl>
                        <Input type="password" autoComplete="current-password" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <Button type="submit" disabled={mutation.isPending}>
                  {mutation.isPending ? 'Ingresando…' : 'Ingresar'}
                </Button>
              </form>
            </Form>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
