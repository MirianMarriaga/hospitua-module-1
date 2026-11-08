import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';

import '@/index.css';
import { createQueryClient } from '@/lib/queryClient';
import { AppRoutes } from '@/routes';
import { syncThemeWithDocument } from '@/stores/themeStore';

const queryClient = createQueryClient();
syncThemeWithDocument();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
