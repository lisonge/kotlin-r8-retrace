import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import unocss from 'unocss/vite';

export default defineConfig({
  plugins: [unocss(), vue()],
  build: {
    rolldownOptions: {
      // kotlin wasm nodejs
      external: ['node:module'],
    },
  },
  server: {
    host: '127.0.0.1',
    port: 8420,
  },
});
