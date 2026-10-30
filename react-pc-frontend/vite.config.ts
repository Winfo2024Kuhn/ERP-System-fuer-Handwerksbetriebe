import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { readdirSync, readFileSync } from 'fs';
import { resolve, sep } from 'path';
import { PDFJS_HILFSORDNER, istPdfjsHilfsdatei, pdfjsHilfsdateiAusUrl, pdfjsHilfsordner } from './src/lib/pdfjsPfad';

const PDFJS_PAKET = resolve(import.meta.dirname, 'node_modules/pdfjs-dist');

/**
 * Liefert die Hilfsdateien von PDF.js mit aus. PDF.js lädt sie über feste Dateinamen nach,
 * deshalb ohne Hash, aber mit der Version im Ordner. Den Pfad legt `src/lib/pdfjsPfad.ts` fest.
 */
function pdfjsHilfsdateien(): Plugin {
  const version: string = JSON.parse(readFileSync(resolve(PDFJS_PAKET, 'package.json'), 'utf8')).version;
  return {
    name: 'pdfjs-hilfsdateien',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const treffer = pdfjsHilfsdateiAusUrl(req.url ?? '', version);
        if (!treffer) return next();
        const datei = resolve(PDFJS_PAKET, treffer.ordner, treffer.datei);
        // Zweite Sicherung: nie etwas außerhalb des Hilfsordners lesen.
        if (!datei.startsWith(resolve(PDFJS_PAKET, treffer.ordner) + sep)) return next();
        try {
          const inhalt = readFileSync(datei);
          if (datei.endsWith('.wasm')) res.setHeader('Content-Type', 'application/wasm');
          res.end(inhalt);
        } catch {
          next();
        }
      });
    },
    generateBundle() {
      const ziel = pdfjsHilfsordner(version).slice(1);
      for (const ordner of PDFJS_HILFSORDNER) {
        for (const eintrag of readdirSync(resolve(PDFJS_PAKET, ordner), { withFileTypes: true })) {
          if (!eintrag.isFile() || !istPdfjsHilfsdatei(eintrag.name)) continue;
          this.emitFile({
            type: 'asset',
            fileName: `${ziel}${ordner}/${eintrag.name}`,
            source: readFileSync(resolve(PDFJS_PAKET, ordner, eintrag.name)),
          });
        }
      }
    },
  };
}

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
  plugins: [react(), pdfjsHilfsdateien()],
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
