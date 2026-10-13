/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    testTimeout: 20000,
    // Aufrufverlauf aller Mocks vor jedem Test leeren (Implementierungen bleiben). Sonst finden
    // calls.find()/toHaveBeenCalledTimes bei gemischter Reihenfolge Aufrufe früherer Tests.
    clearMocks: true,
  },
});
