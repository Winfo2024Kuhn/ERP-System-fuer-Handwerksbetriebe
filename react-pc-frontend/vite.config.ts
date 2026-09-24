import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: false, // Do not delete existing legacy files yet
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]'
      }
    }
  },
  server: {
    proxy: {
      '/api': {
        // Port 8080 ist lokal oft durch andere Dienste belegt – dann per ERP_BACKEND_URL umlenken.
        target: process.env.ERP_BACKEND_URL || 'https://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});