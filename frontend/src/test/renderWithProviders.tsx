import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, type InitialEntry } from 'react-router';

interface Options {
  route?: InitialEntry;
  path?: string;
  /** Rutas extra para comprobar a dónde navega la pantalla. */
  extraRoutes?: { path: string; element: ReactElement }[];
}

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  });
}

export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { route = '/', path = '/', extraRoutes = [] } = options;
  const queryClient = createTestQueryClient();
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={path} element={ui} />
          {extraRoutes.map((extra) => (
            <Route key={extra.path} path={extra.path} element={extra.element} />
          ))}
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

/** Muestra la ruta actual para comprobar navegaciones. */
export function LocationProbe({ label }: { label: string }) {
  return <div data-testid="location">{label}</div>;
}
