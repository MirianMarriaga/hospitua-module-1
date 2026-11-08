import { afterEach, describe, expect, it } from 'vitest';

import { syncThemeWithDocument, useThemeStore } from '@/stores/themeStore';

describe('themeStore', () => {
  afterEach(() => {
    useThemeStore.setState({ theme: 'light' });
    localStorage.clear();
  });

  it('empieza en el tema claro', () => {
    expect(useThemeStore.getState().theme).toBe('light');
  });

  it('pone y quita la clase dark de <html> al cambiar el tema', () => {
    const root = document.createElement('html');
    const stop = syncThemeWithDocument(root);
    expect(root.classList.contains('dark')).toBe(false);

    useThemeStore.getState().setTheme('dark');
    expect(root.classList.contains('dark')).toBe(true);

    useThemeStore.getState().setTheme('light');
    expect(root.classList.contains('dark')).toBe(false);
    stop();
  });

  it('recuerda el tema elegido en el navegador', () => {
    useThemeStore.getState().setTheme('dark');
    expect(JSON.parse(localStorage.getItem('hospitua-tema') ?? '{}')).toMatchObject({
      state: { theme: 'dark' },
    });
  });
});
