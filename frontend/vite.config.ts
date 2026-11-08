import path from 'node:path';
import babel from '@rolldown/plugin-babel';
import tailwindcss from '@tailwindcss/vite';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// El backend no tiene CORS: en desarrollo Vite reenvía /api al backend en perfil dev.
// El compilador de React (con Babel, como indica @vitejs/plugin-react 6) memoiza los componentes:
// no se escriben useMemo, useCallback ni React.memo a mano.
export default defineConfig({
  plugins: [
    react(),
    babel({ include: /[\\/]src[\\/].*\.[jt]sx?$/, presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // Solo reporte, sin umbral que haga fallar (D9 del plan del frontend).
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/api/schema.d.ts', 'src/main.tsx'],
      reporter: ['text-summary', 'html'],
    },
  },
});
