import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

/**
 * Große Bibliotheken als eigene Dateien: Sie ändern sich selten, bleiben also über Updates
 * hinweg im Browser-Cache, und sie landen nur dort, wo eine Seite sie wirklich braucht.
 */
const BIBLIOTHEKS_PAKETE: Record<string, RegExp> = {
  react: /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/,
  editor: /[\\/]node_modules[\\/](@tiptap|prosemirror-[^\\/]+|linkifyjs|orderedmap|rope-sequence|w3c-keyname)[\\/]/,
  diagramme: /[\\/]node_modules[\\/](chart\.js|react-chartjs-2|@kurkle)[\\/]/,
  dnd: /[\\/]node_modules[\\/]@dnd-kit[\\/]/,
  // Sonst wird jedes Symbol eine eigene Mini-Datei – von unterwegs kostet jede Anfrage Zeit.
  icons: /[\\/]node_modules[\\/]lucide-react[\\/]/,
};

function bibliotheksPaket(id: string): string | undefined {
  return Object.keys(BIBLIOTHEKS_PAKETE).find(name => BIBLIOTHEKS_PAKETE[name].test(id));
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: resolve(import.meta.dirname, '../src/main/resources/static'),
    emptyOutDir: false, // Do not delete existing legacy files yet
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        // Der PDF.js-Worker kommt als .mjs; als .js liefert jeder Server ihn sicher mit
        // JavaScript-Typ aus (Module-Worker verweigern sonst den Start).
        assetFileNames: (asset) => (asset.names[0]?.endsWith('.mjs')
          ? 'assets/[name]-[hash].js'
          : 'assets/[name]-[hash][extname]'),
        manualChunks: bibliotheksPaket,
      }
    }
  },
  server: {
    proxy: {
      // Backend (./mvnw spring-boot:run) spricht HTTP auf server.port=8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});