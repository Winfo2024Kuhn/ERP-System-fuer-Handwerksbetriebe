import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'

// ─── PWA Service Worker Auto-Update ───
// Registriert den SW und lädt die Seite automatisch neu wenn ein Update verfügbar ist
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/zeiterfassung/sw.js', { scope: '/zeiterfassung/' })
      console.log('✅ Service Worker registriert')

      // Prüfe regelmäßig auf Updates (alle 60 Sekunden)
      setInterval(() => {
        registration.update().catch(() => { /* offline – ignorieren */ })
      }, 60 * 1000)

      // Wenn ein neuer SW installiert wird → Seite sofort neu laden
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing
        if (!newWorker) return

        newWorker.addEventListener('statechange', () => {
          // Neuer SW ist aktiv und es gab bereits einen alten → Reload
          if (newWorker.state === 'activated' && navigator.serviceWorker.controller) {
            console.log('🔄 Neues Update verfügbar – Seite wird neu geladen...')
            window.location.reload()
          }
        })
      })

      // Fallback: Wenn der neue SW über skipWaiting() den alten Controller ersetzt
      let refreshing = false
      navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (!refreshing) {
          refreshing = true
          console.log('🔄 Controller gewechselt – Seite wird neu geladen...')
          window.location.reload()
        }
      })
    } catch (err) {
      console.error('SW Registrierung fehlgeschlagen:', err)
    }
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename="/zeiterfassung">
      <App />
    </BrowserRouter>
  </StrictMode>,
)
