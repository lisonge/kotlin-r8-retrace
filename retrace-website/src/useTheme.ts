import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'auto';

const STORAGE_KEY = 'r8-retrace-theme';
const THEME_MODES: ThemeMode[] = ['light', 'dark', 'auto'];

const isThemeMode = (value: string | null): value is ThemeMode =>
  value === 'light' || value === 'dark' || value === 'auto';

const getStoredTheme = (): ThemeMode => {
  try {
    const storedTheme = window.localStorage.getItem(STORAGE_KEY);
    return isThemeMode(storedTheme) ? storedTheme : 'auto';
  } catch {
    return 'auto';
  }
};

export const useTheme = () => {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  const themeMode = ref<ThemeMode>(getStoredTheme());
  const systemIsDark = ref(mediaQuery.matches);
  const resolvedTheme = computed<'light' | 'dark'>(() =>
    themeMode.value === 'auto'
      ? systemIsDark.value ? 'dark' : 'light'
      : themeMode.value,
  );

  const themeLabel = computed(() => ({
    light: 'Light',
    dark: 'Dark',
    auto: 'Auto',
  })[themeMode.value]);

  const nextThemeLabel = computed(() => {
    const currentIndex = THEME_MODES.indexOf(themeMode.value);
    const nextMode = THEME_MODES[(currentIndex + 1) % THEME_MODES.length];
    return ({ light: 'Light', dark: 'Dark', auto: 'Auto' })[nextMode];
  });

  const applyTheme = () => {
    const theme = resolvedTheme.value;
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    document.querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', theme === 'dark' ? '#111714' : '#f3f0e8');
  };

  const handleSystemThemeChange = (event: MediaQueryListEvent) => {
    systemIsDark.value = event.matches;
  };

  const cycleTheme = () => {
    const currentIndex = THEME_MODES.indexOf(themeMode.value);
    themeMode.value = THEME_MODES[(currentIndex + 1) % THEME_MODES.length];
  };

  watch(themeMode, (mode) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Theme switching still works when storage is unavailable.
    }
  });

  watch(resolvedTheme, applyTheme, { immediate: true });

  onMounted(() => mediaQuery.addEventListener('change', handleSystemThemeChange));
  onBeforeUnmount(() => mediaQuery.removeEventListener('change', handleSystemThemeChange));

  return {
    cycleTheme,
    nextThemeLabel,
    resolvedTheme,
    themeLabel,
    themeMode,
  };
};
