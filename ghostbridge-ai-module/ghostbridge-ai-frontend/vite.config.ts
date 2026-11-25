/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

// Dev-only proxy: the browser talks to its own origin and Vite forwards /api to the GhostBridge backend.
// The target is a non-secret URL. No credentials are configured here.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const target = env.DEV_PROXY_TARGET || 'http://localhost:8080';
  return {
    plugins: [react()],
    server: { proxy: { '/api': { target, changeOrigin: false } } },
    build: { sourcemap: false },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
