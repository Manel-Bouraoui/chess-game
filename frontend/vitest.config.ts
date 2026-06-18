import path from 'path';
import { defineConfig } from 'vitest/config';

/**
 * Standalone Vitest config (no Tailwind/Vite UI plugins) — avoids plugin crashes with Vitest 4.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: './src/test/setup.ts',
  },
});
