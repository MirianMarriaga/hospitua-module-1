import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation } from 'react-router';
import { z } from 'zod';

import hospituaLogo from '@/assets/icono-hospitua-grande.png';
import loginPhoto from '@/assets/login-example-photo.jpg';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/PasswordInput';
import { homeOf } from '@/layouts/areaNavigation';
import { errorMessage } from '@/lib/errorMessages';
import { useLogin } from '@/pages/login/useLogin';
import { useSessionStore } from '@/stores/sessionStore';

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Ingresa tu usuario.'),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
});

type LoginValues = z.infer<typeof loginSchema>;

const LOGIN_ERRORS = { INVALID_CREDENTIALS: 'Usuario o contraseña incorrectos.' };

/** Pantalla de inicio de sesión dividida en dos paneles: imagen de hotel + formulario de acceso. */
export function LoginPage() {
  const session = useSessionStore((state) => state.session);
  const expired = useSessionStore((state) => state.expired);
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });

  const mutation = useLogin();

  if (session) {
    return <Navigate to={from ?? homeOf(session.area)} replace />;
  }

  return (
    <div className="flex min-h-screen">
      {/* Panel izquierdo: imagen con degradado y bienvenida (oculto en pantallas < 900px) */}
      <div className="relative hidden min-h-screen w-1/2 overflow-hidden bg-[linear-gradient(180deg,var(--sidebar-from),var(--sidebar-to))] min-[900px]:flex min-[900px]:flex-col min-[900px]:justify-between p-12 text-sidebar-foreground">
        <img
          src={loginPhoto}
          alt="Fachada de Hotel Hospitua"
          className="absolute inset-0 size-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-photo-overlay/90 via-photo-overlay/60 to-photo-overlay/30" />
        <div className="relative z-10 flex items-center gap-3">
          <img
            src={hospituaLogo}
            alt="Hotel Hospitua"
            className="size-10 rounded-lg object-contain shadow"
          />
          <span className="text-xl font-bold tracking-tight">Hotel Hospitua</span>
        </div>
        <div className="relative z-10 max-w-md">
          <h2 className="text-3xl font-extrabold leading-tight">
            Bienvenido al sistema de gestión hotelera
          </h2>
          <p className="mt-3 text-base text-sidebar-text">
            Control integral de estancias, reservas y administración de habitaciones.
          </p>
        </div>
        <div className="relative z-10 text-xs text-sidebar-muted">
          © {new Date().getFullYear()} Hotel Hospitua. Todos los derechos reservados.
        </div>
      </div>

      {/* Panel derecho: formulario centrado */}
      <div className="flex flex-1 items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-sm space-y-6">
          <div className="flex flex-col items-center text-center">
            <img
              src={hospituaLogo}
              alt="Hotel Hospitua"
              className="mb-4 size-16 rounded-xl object-contain shadow-sm"
            />
            <h1 className="text-2xl font-bold tracking-tight text-ink">Iniciar sesión</h1>
            <p className="mt-1 text-sm text-text-lo">Ingresa con tu usuario del hotel</p>
          </div>

          <Card>
            <CardContent className="pt-6">
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
                          <Input
                            autoComplete="username"
                            autoFocus
                            placeholder="Tu usuario"
                            {...field}
                          />
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
                          <PasswordInput
                            autoComplete="current-password"
                            placeholder="Tu contraseña"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <Button type="submit" className="mt-2 w-full" disabled={mutation.isPending}>
                    {mutation.isPending ? 'Ingresando…' : 'Ingresar'}
                  </Button>
                </form>
              </Form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
