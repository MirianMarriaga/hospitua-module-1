import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type Theme = 'light' | 'dark';

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

/** Tema Claro u Oscuro elegido en el menú del usuario; se recuerda en este navegador. */
export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: 'light',
      setTheme: (theme) => set({ theme }),
    }),
    { name: 'hospitua-tema', storage: createJSONStorage(() => localStorage) },
  ),
);

/** Pone o quita la clase `dark` de <html> según el tema guardado y la mantiene al día. */
export function syncThemeWithDocument(root: HTMLElement = document.documentElement) {
  const apply = (theme: Theme) => root.classList.toggle('dark', theme === 'dark');
  apply(useThemeStore.getState().theme);
  return useThemeStore.subscribe((state) => apply(state.theme));
}
