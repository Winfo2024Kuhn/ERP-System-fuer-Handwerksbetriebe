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
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'masked-icon.svg'],
      manifest: {
        name: 'Zeiterfassung App',
        short_name: 'Zeiterfassung',
        description: 'Mobile Zeiterfassung für Mitarbeiter',
        theme_color: '#dc2626', // rose-600
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
        maximumFileSizeToCacheInBytes: 20 * 1024 * 1024, // 20MB limit to ensure build passes
        globPatterns: ['**/*.{js,css,html,ico,png,svg}']
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
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        secure: false, // Allow proxying to HTTP backend
      },
    },
  },
})
