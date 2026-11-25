/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

// Dev only: the browser talks to its own origin and Vite forwards /api to GhostBridge App (one backend, one origin).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const target = env.DEV_PROXY_TARGET || 'http://localhost:8080';
  return {
    plugins: [react()],
    // One copy of React and the router, shared with the hosted AI console (npm workspace hoisting plus this safeguard).
    resolve: { dedupe: ['react', 'react-dom', 'react-router-dom'] },
    server: { port: 5173, proxy: { '/api': { target, changeOrigin: false } } },
    build: { sourcemap: false },
    test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'], css: false },
  };
});
