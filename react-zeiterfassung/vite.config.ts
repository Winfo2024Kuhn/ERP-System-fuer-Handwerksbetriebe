import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'app-icon.png'],
      manifest: {
        name: 'Zeiterfassung App',
        short_name: 'Zeiterfassung',
        description: 'Mobile Zeiterfassung für Mitarbeiter',
        theme_color: '#dc2626', // red-600 = Logo-/App-Icon-Farbe, nicht die UI-Primärfarbe
        background_color: '#ffffff',
        display: 'standalone',
        orientation: 'portrait',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      injectManifest: {
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024, // größte Datei: Erkennungs-Modell ~1,9 MB
        // mjs/wasm/ort = Ecken-Erkennung des Scanners (scanic-ml: Modell ~1,9 MB +
        // Laufzeit ~1,5 MB). Bewusst im Precache: Die Erkennung soll auch bei
        // schlechtem Netz auf der Baustelle sofort und offline funktionieren.
        globPatterns: ['**/*.{js,mjs,css,html,ico,png,svg,wasm,ort}']
      }
    }),
    basicSsl()
  ],
  base: '/zeiterfassung/',
  build: {
    outDir: '../src/main/resources/static/zeiterfassung',
    emptyOutDir: true,
  },
  server: {
    host: true, // Allow access from network (mobile)
    proxy: {
      // Backend (./mvnw spring-boot:run) spricht HTTP auf server.port=8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
