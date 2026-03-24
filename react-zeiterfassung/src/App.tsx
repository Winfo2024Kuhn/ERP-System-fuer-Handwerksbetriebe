import { Routes, Route, Navigate, useSearchParams } from 'react-router-dom'
import { useState, useEffect, useRef } from 'react'
import SetupPage from './pages/SetupPage'

// ─── Cookie helpers (iOS PWA: localStorage gets cleared, cookies persist) ───
const COOKIE_NAME = 'ze_token'
const COOKIE_MITARBEITER = 'ze_mitarbeiter'
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365 // 1 year

function setCookie(name: string, value: string) {
  const secure = location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${name}=${encodeURIComponent(value)}; Max-Age=${COOKIE_MAX_AGE}; Path=/; SameSite=Strict${secure}`
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

function deleteCookie(name: string) {
  document.cookie = `${name}=; Max-Age=0; Path=/; SameSite=Strict`
}

function saveAuth(token: string, mitarbeiterData: object) {
  localStorage.setItem('zeiterfassung_token', token)
  localStorage.setItem('zeiterfassung_mitarbeiter', JSON.stringify(mitarbeiterData))
  setCookie(COOKIE_NAME, token)
  setCookie(COOKIE_MITARBEITER, JSON.stringify(mitarbeiterData))
}

function clearAuth() {
  localStorage.removeItem('zeiterfassung_token')
  localStorage.removeItem('zeiterfassung_mitarbeiter')
  deleteCookie(COOKIE_NAME)
  deleteCookie(COOKIE_MITARBEITER)
}

function loadAuth(): { token: string; mitarbeiter: object } | null {
  // Try localStorage first, fall back to cookie (iOS PWA storage loss)
  let token = localStorage.getItem('zeiterfassung_token')
  let mitarbeiterStr = localStorage.getItem('zeiterfassung_mitarbeiter')

  if (!token || !mitarbeiterStr) {
    token = getCookie(COOKIE_NAME)
    mitarbeiterStr = getCookie(COOKIE_MITARBEITER)
    if (token && mitarbeiterStr) {
      // Restore localStorage from cookie
      localStorage.setItem('zeiterfassung_token', token)
      localStorage.setItem('zeiterfassung_mitarbeiter', mitarbeiterStr)
    }
  }

  if (!token || !mitarbeiterStr) return null
  try {
    return { token, mitarbeiter: JSON.parse(mitarbeiterStr) }
  } catch {
    return null
  }
}
import DashboardPage from './pages/DashboardPage'
import ZeiterfassungPage from './pages/ZeiterfassungPage'
import ProjektePage from './pages/ProjektePage'
import ProjektNotizenPage from './pages/ProjektNotizenPage'

import AnfragenPage from './pages/AnfragenPage'
import AnfrageNotizenPage from './pages/AnfrageNotizenPage'
import KundenPage from './pages/KundenPage'
import LieferantenPage from './pages/LieferantenPage'
import UrlaubsantragPage from './pages/UrlaubsantragPage'
import AbwesenheitenPage from './pages/AbwesenheitenPage'
import SaldenPage from './pages/SaldenPage'
import TagesbuchungenPage from './pages/TagesbuchungenPage'
import LieferantReklamationCreatePage from './pages/LieferantReklamationCreatePage'
import { LieferantReklamationenPage } from './pages/LieferantReklamationenPage'
import LieferantLieferscheinePage from './pages/LieferantLieferscheinePage'
import { LieferantReklamationDetailPage } from './pages/LieferantReklamationDetailPage'
import KalenderPage from './pages/KalenderPage'
import { OfflineService } from './services/OfflineService'
import { NotificationService } from './services/NotificationService'

function App() {
  const [searchParams] = useSearchParams()
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [mitarbeiter, setMitarbeiter] = useState<{ id: number; name: string; vorname?: string; nachname?: string } | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncStatus, setSyncStatus] = useState<'syncing' | 'done' | 'error'>('syncing')
  const [error, setError] = useState<string | null>(null)
  const notificationIntervalRef = useRef<number | null>(null)

  useEffect(() => {
    initializeApp()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // === GLOBAL SYNC HANDLERS ===
  // Diese Handler sind auf App-Ebene, damit Sync auf ALLEN Seiten funktioniert
  useEffect(() => {
    const handleOnline = async () => {
      console.log('🌐 App ist online -> Sync pending entries');
      setSyncStatus('syncing');
      const result = await OfflineService.syncAll();
      // Only clear offline minutes when ALL pending entries are gone.
      // If any failed (5xx), they'll retry later and clearing now would lose hours.
      if (result.allPendingCleared && result.pendingSynced > 0) {
        await OfflineService.clearOfflineHeuteMinuten();
      }
      // If a 'start' entry was just synced, set a cooldown so loadActiveSession
      // won't kill the local session before the server confirms it via GET
      if (result.startSynced) {
        localStorage.setItem('zeiterfassung_start_synced_at', Date.now().toString());
      }
      setSyncStatus(result.success ? 'done' : 'error');
    };

    const handleVisibilityChange = async () => {
      // PWA: Wenn App aus dem Hintergrund kommt -> Sync
      if (document.visibilityState === 'visible') {
        console.log('👁️ App wurde wieder sichtbar -> Sync check');
        const pendingCount = await OfflineService.getPendingCount();
        if (pendingCount > 0) {
          setSyncStatus('syncing');
          const result = await OfflineService.syncAll();
          if (result.allPendingCleared && result.pendingSynced > 0) {
            await OfflineService.clearOfflineHeuteMinuten();
          }
          if (result.startSynced) {
            localStorage.setItem('zeiterfassung_start_synced_at', Date.now().toString());
          }
          setSyncStatus(result.success ? 'done' : 'error');
        }
      }
    };

    window.addEventListener('online', handleOnline);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Heartbeat alle 30 Sekunden für "stille" Reconnects
    const heartbeat = setInterval(async () => {
      const pendingCount = await OfflineService.getPendingCount();
      if (pendingCount > 0) {
        console.log(`❤️ Heartbeat: ${pendingCount} pending entries -> Sync`);
        const result = await OfflineService.syncAll();
        if (result.allPendingCleared && result.pendingSynced > 0) {
          await OfflineService.clearOfflineHeuteMinuten();
        }
        if (result.startSynced) {
          localStorage.setItem('zeiterfassung_start_synced_at', Date.now().toString());
        }
        setSyncStatus(result.success ? 'done' : 'error');
      }
    }, 30000); // 30 Sekunden

    return () => {
      window.removeEventListener('online', handleOnline);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      clearInterval(heartbeat);
    };
  }, [])


  const initializeApp = async () => {
    // Check URL for token (from QR code scan)
    const urlToken = searchParams.get('token')

    if (urlToken) {
      // Auto-login from QR code
      await validateAndStoreToken(urlToken)
    } else {
      // Check if already logged in (localStorage or cookie fallback for iOS PWA)
      const auth = loadAuth()

      if (auth) {
        try {
          setMitarbeiter(auth.mitarbeiter as { id: number; name: string; vorname?: string; nachname?: string })
          setIsAuthenticated(true)
          // Sync all data on app start
          syncData()
          // Initialize notifications
          initializeNotifications(auth.token)
        } catch {
          clearAuth()
        }
      }
    }
    setLoading(false)
  }

  // Initialize push notifications for appointment reminders
  // Uses Service Worker registration.showNotification() for lock screen support
  const initializeNotifications = async (token: string) => {
    // Request permission
    const granted = await NotificationService.requestPermission()
    if (granted) {
      console.log('Notification permission granted')
      // Store token for Service Worker periodic background sync
      await NotificationService.storeTokenForSW(token)
      // Register periodic background sync (where supported)
      await NotificationService.registerPeriodicSync()
      // Check immediately via Service Worker
      NotificationService.loadAndCheck(token)
      // Set up periodic check every 5 minutes as fallback
      // (for browsers that don't support periodic background sync)
      if (notificationIntervalRef.current) {
        clearInterval(notificationIntervalRef.current)
      }
      notificationIntervalRef.current = window.setInterval(() => {
        const currentToken = localStorage.getItem('zeiterfassung_token')
        if (currentToken) {
          NotificationService.loadAndCheck(currentToken)
        }
      }, 5 * 60 * 1000) // 5 minutes
    } else {
      console.log('Notification permission not granted')
    }
  }

  const syncData = async () => {
    setSyncStatus('syncing')

    // Prüfe ob Mitarbeiter noch aktiv ist
    const token = localStorage.getItem('zeiterfassung_token')
    if (token) {
      try {
        const res = await fetch(`/api/mitarbeiter/by-token/${token}`)
        if (res.status === 404 || res.status === 401 || res.status === 403) {
          // Mitarbeiter nicht gefunden oder Token ungültig -> abmelden
          console.log('Token ungültig (' + res.status + ') - automatische Abmeldung')
          handleLogout()
          return
        }

        if (!res.ok) {
          // Server Fehler (500 etc) -> Als Offline behandeln
          throw new Error('Server Fehler: ' + res.status)
        }

        // Prüfe ob Mitarbeiter aktiv ist
        const mitarbeiterData = await res.json()
        if (mitarbeiterData.aktiv === false) {
          console.log('Mitarbeiter ist deaktiviert - automatische Abmeldung')
          handleLogout()
          return
        }
      } catch {
        // Offline - keine Prüfung möglich, weitermachen
        console.log('Offline - Aktivitätsprüfung übersprungen')
      }
    }

    const result = await OfflineService.syncAll()
    setSyncStatus(result.success ? 'done' : 'error')

    // Check for appointment notifications after sync
    if (result.success && token) {
      NotificationService.loadAndCheck(token)
    }
  }

  const validateAndStoreToken = async (token: string) => {
    setError(null)
    try {
      console.log('Validiere Token:', token.substring(0, 8) + '...')
      const res = await fetch(`/api/mitarbeiter/by-token/${token}`)

      if (res.ok) {
        const data = await res.json()
        console.log('Token gültig, Mitarbeiter:', data.vorname, data.nachname)

        const mitarbeiterData = { id: data.id, name: `${data.vorname} ${data.nachname}`, vorname: data.vorname, nachname: data.nachname }

        // Speichere Token und Mitarbeiter (localStorage + Cookie für iOS PWA)
        saveAuth(token, mitarbeiterData)

        setMitarbeiter(mitarbeiterData)
        setIsAuthenticated(true)

        // Sync all data after login
        await OfflineService.syncAll()
        setSyncStatus('done')

        // Initialize notifications after login
        initializeNotifications(token)

        // Remove token from URL (clean up) - wichtig für Homescreen!
        window.history.replaceState({}, '', window.location.pathname)
      } else {
        console.error('Token ungültig, Status:', res.status)
        setError(`Token ungültig (${res.status}). Bitte neuen QR-Code anfordern.`)
      }
    } catch (err) {
      console.error('Token validation failed:', err)
      // Offline-Modus: Speichere Token trotzdem und versuche später
      localStorage.setItem('zeiterfassung_token', token)
      setCookie(COOKIE_NAME, token)
      setError('Server nicht erreichbar. Bitte später erneut versuchen.')
    }
  }

  const handleLogout = () => {
    // Clear notification interval
    if (notificationIntervalRef.current) {
      clearInterval(notificationIntervalRef.current)
      notificationIntervalRef.current = null
    }
    clearAuth()
    setIsAuthenticated(false)
    setMitarbeiter(null)
  }

  // Show loading while checking token
  if (loading) {
    return (
      <div className="min-h-screen bg-white flex flex-col items-center justify-center gap-4">
        <div className="w-12 h-12 border-4 border-rose-600 border-t-transparent rounded-full animate-spin"></div>
        <p className="text-slate-500 text-sm">Wird geladen...</p>
      </div>
    )
  }

  // Show setup page if not authenticated (need to scan QR code)
  if (!isAuthenticated) {
    return <SetupPage error={error} onTokenScanned={validateAndStoreToken} />
  }

  return (
    <div className="h-full w-full bg-slate-50 flex flex-col">
      <Routes>
        <Route path="/" element={<DashboardPage mitarbeiter={mitarbeiter} onLogout={handleLogout} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/zeiterfassung" element={<ZeiterfassungPage mitarbeiter={mitarbeiter} />} />
        <Route path="/projekte" element={<ProjektePage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/projekte/:projektId/notizen" element={<ProjektNotizenPage />} />

        <Route path="/anfragen" element={<AnfragenPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/anfragen/:anfrageId/notizen" element={<AnfrageNotizenPage />} />
        <Route path="/kunden" element={<KundenPage syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/lieferanten" element={<LieferantenPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/lieferanten/:lieferantId/lieferscheine" element={<LieferantLieferscheinePage />} />
        <Route path="/lieferanten/:lieferantId/reklamation/neu" element={<LieferantReklamationCreatePage />} />
        <Route path="/lieferanten/:lieferantId/reklamationen" element={<LieferantReklamationenPage />} />
        <Route path="/reklamationen/:id" element={<LieferantReklamationDetailPage />} />
        <Route path="/mitarbeiter" element={<Navigate to="/" replace />} /> {/* Legacy redirect */}
        <Route path="/urlaub" element={<UrlaubsantragPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/abwesenheit" element={<UrlaubsantragPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/abwesenheiten" element={<AbwesenheitenPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/salden" element={<SaldenPage mitarbeiter={mitarbeiter} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/tagesbuchungen" element={<TagesbuchungenPage syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="/kalender" element={<KalenderPage mitarbeiter={mitarbeiter} token={localStorage.getItem('zeiterfassung_token')} syncStatus={syncStatus} onSync={syncData} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}

export default App
