import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
// vitest/config re-exports Vite's defineConfig widened with the `test` block.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // Matches the port the backend allows as a CORS origin and the port the Docker frontend
    // is published on, so the same origin works however the app is started.
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
