/// <reference lib="webworker" />
import { cleanupOutdatedCaches, precacheAndRoute } from 'workbox-precaching'
import { clientsClaim } from 'workbox-core'
import { registerRoute } from 'workbox-routing'
import { NetworkFirst } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'

declare let self: ServiceWorkerGlobalScope

// ─── Workbox Precaching (injected by VitePWA injectManifest) ───
cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

// Sofortiges Update des Service Workers bei neuer Version
self.skipWaiting()
clientsClaim()

// ─── Runtime Caching for API ───
registerRoute(
  ({ url }) => url.pathname.startsWith('/api'),
  new NetworkFirst({
    cacheName: 'api-cache',
    plugins: [
      new ExpirationPlugin({
        maxEntries: 50,
        maxAgeSeconds: 60 * 60 * 24 // 1 day
      })
    ],
    networkTimeoutSeconds: 3
  })
)

// ─── Navigation Fallback for Offline ───
registerRoute(
  ({ request }) => request.mode === 'navigate',
  async ({ request }) => {
    try {
      return await fetch(request)
    } catch {
      const cache = await caches.open('workbox-precache-v2')
      const cachedResponse = await cache.match('/zeiterfassung/offline.html')
      return cachedResponse || new Response('Offline', { status: 503 })
    }
  }
)

// ─── Appointment Notification Types ───
interface Appointment {
  id: number
  titel: string
  datum: string        // YYYY-MM-DD
  startZeit: string | null
  ganztaegig: boolean
}

interface CheckNotificationsMessage {
  type: 'CHECK_NOTIFICATIONS'
  token: string
}

interface PeriodicSyncEvent extends ExtendableEvent {
  tag: string
}

const HOUR_24_MS = 24 * 60 * 60 * 1000
const HOUR_1_MS = 60 * 60 * 1000
const CHECK_WINDOW_MS = 10 * 60 * 1000 // 10 minute window

// IndexedDB-based tracking for sent notifications (localStorage not available in SW)
const SENT_DB_NAME = 'sw-notifications'
const SENT_STORE_NAME = 'sent'

async function openSentDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(SENT_DB_NAME, 1)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(SENT_STORE_NAME)) {
        db.createObjectStore(SENT_STORE_NAME, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function hasBeenSent(appointmentId: number, type: '24h' | '1h'): Promise<boolean> {
  try {
    const db = await openSentDB()
    return new Promise((resolve) => {
      const tx = db.transaction(SENT_STORE_NAME, 'readonly')
      const store = tx.objectStore(SENT_STORE_NAME)
      const request = store.get(`${appointmentId}_${type}`)
      request.onsuccess = () => resolve(!!request.result)
      request.onerror = () => resolve(false)
    })
  } catch {
    return false
  }
}

async function markAsSent(appointmentId: number, type: '24h' | '1h'): Promise<void> {
  try {
    const db = await openSentDB()
    const tx = db.transaction(SENT_STORE_NAME, 'readwrite')
    const store = tx.objectStore(SENT_STORE_NAME)
    store.put({ key: `${appointmentId}_${type}`, timestamp: Date.now() })
  } catch {
    // Ignore errors
  }
}

async function cleanupOldSentEntries(): Promise<void> {
  try {
    const db = await openSentDB()
    const tx = db.transaction(SENT_STORE_NAME, 'readwrite')
    const store = tx.objectStore(SENT_STORE_NAME)
    const request = store.getAll()
    request.onsuccess = () => {
      const now = Date.now()
      const MAX_AGE = 7 * 24 * 60 * 60 * 1000 // 7 days
      for (const entry of request.result) {
        if (now - entry.timestamp > MAX_AGE) {
          store.delete(entry.key)
        }
      }
    }
  } catch {
    // Ignore errors
  }
}

function parseAppointmentTime(apt: Appointment): number {
  const [year, month, day] = apt.datum.split('-').map(Number)
  if (apt.ganztaegig || !apt.startZeit) {
    return new Date(year, month - 1, day, 8, 0, 0).getTime()
  }
  const timeParts = apt.startZeit.split(':')
  const hours = parseInt(timeParts[0], 10)
  const minutes = parseInt(timeParts[1], 10)
  return new Date(year, month - 1, day, hours, minutes, 0).getTime()
}

function formatDate(dateStr: string): string {
  const [year, month, day] = dateStr.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.toLocaleDateString('de-DE', {
    weekday: 'short',
    day: '2-digit',
    month: 'short'
  })
}

async function checkAndNotify(appointments: Appointment[]): Promise<void> {
  const now = Date.now()

  for (const apt of appointments) {
    const aptTime = parseAppointmentTime(apt)
    if (aptTime < now) continue

    const timeTo24h = aptTime - HOUR_24_MS
    const timeTo1h = aptTime - HOUR_1_MS

    // Check 24h notification (within check window)
    if (now >= timeTo24h && now < timeTo24h + CHECK_WINDOW_MS) {
      if (!(await hasBeenSent(apt.id, '24h'))) {
        const timeStr = apt.ganztaegig || !apt.startZeit
          ? 'Ganztägig'
          : apt.startZeit.substring(0, 5) + ' Uhr'

        await self.registration.showNotification(`📅 Termin morgen: ${apt.titel}`, {
          body: `${formatDate(apt.datum)} um ${timeStr}`,
          icon: '/zeiterfassung/pwa-192x192.png',
          badge: '/zeiterfassung/pwa-192x192.png',
          tag: `apt-24h-${apt.id}`,
          requireInteraction: true,
          data: { url: '/zeiterfassung/kalender' }
        })
        await markAsSent(apt.id, '24h')
        console.log(`[SW] Sent 24h notification for appointment ${apt.id}: ${apt.titel}`)
      }
    }

    // Check 1h notification (within check window)
    if (now >= timeTo1h && now < timeTo1h + CHECK_WINDOW_MS) {
      if (!(await hasBeenSent(apt.id, '1h'))) {
        const timeStr = apt.ganztaegig || !apt.startZeit
          ? 'Ganztägig'
          : apt.startZeit.substring(0, 5) + ' Uhr'

        await self.registration.showNotification(`⏰ Termin in 1 Stunde: ${apt.titel}`, {
          body: timeStr,
          icon: '/zeiterfassung/pwa-192x192.png',
          badge: '/zeiterfassung/pwa-192x192.png',
          tag: `apt-1h-${apt.id}`,
          requireInteraction: true,
          data: { url: '/zeiterfassung/kalender' }
        })
        await markAsSent(apt.id, '1h')
        console.log(`[SW] Sent 1h notification for appointment ${apt.id}: ${apt.titel}`)
      }
    }
  }
}

async function fetchAndCheckAppointments(token: string): Promise<void> {
  try {
    const now = new Date()
    const currentMonth = now.getMonth() + 1
    const currentYear = now.getFullYear()

    let nextMonth = currentMonth + 1
    let nextYear = currentYear
    if (nextMonth > 12) {
      nextMonth = 1
      nextYear++
    }

    const [res1, res2] = await Promise.all([
      fetch(`/api/kalender/mobile?token=${token}&jahr=${currentYear}&monat=${currentMonth}`),
      fetch(`/api/kalender/mobile?token=${token}&jahr=${nextYear}&monat=${nextMonth}`)
    ])

    const appointments1: Appointment[] = res1.ok ? await res1.json() : []
    const appointments2: Appointment[] = res2.ok ? await res2.json() : []

    const allAppointments = [...appointments1, ...appointments2]
    await checkAndNotify(allAppointments)
    await cleanupOldSentEntries()
  } catch (err) {
    console.error('[SW] Error fetching appointments:', err)
  }
}

// ─── Message Handler: triggered by main app ───
self.addEventListener('message', (event) => {
  const data = event.data as CheckNotificationsMessage
  if (data?.type === 'CHECK_NOTIFICATIONS' && data.token) {
    event.waitUntil(fetchAndCheckAppointments(data.token))
  }
});

// ─── Periodic Background Sync (where supported - Chrome/Edge on Android) ───
// periodicSync is not yet in standard TypeScript defs, so we use addEventListener on self cast
const swSelf = self as ServiceWorkerGlobalScope & {
  addEventListener(type: 'periodicsync', listener: (event: PeriodicSyncEvent) => void): void
};
swSelf.addEventListener('periodicsync', (event: PeriodicSyncEvent) => {
  if (event.tag === 'check-appointments') {
    event.waitUntil((async () => {
      // Read token from IndexedDB since localStorage is not available in SW
      try {
        const db = await openSentDB()
        const tx = db.transaction(SENT_STORE_NAME, 'readonly')
        const store = tx.objectStore(SENT_STORE_NAME)
        const tokenEntry = await new Promise<{ key: string; value: string } | undefined>((resolve) => {
          const request = store.get('__auth_token')
          request.onsuccess = () => resolve(request.result)
          request.onerror = () => resolve(undefined)
        })
        if (tokenEntry?.value) {
          await fetchAndCheckAppointments(tokenEntry.value)
        }
      } catch (err) {
        console.error('[SW] Periodic sync error:', err)
      }
    })())
  }
})

// ─── Notification Click: open/focus the app ───
self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const urlToOpen = (event.notification.data?.url as string) || '/zeiterfassung/'

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // Focus existing window if available
      for (const client of clientList) {
        if (client.url.includes('/zeiterfassung') && 'focus' in client) {
          client.navigate(urlToOpen)
          return client.focus()
        }
      }
      // Open new window
      return self.clients.openWindow(urlToOpen)
    })
  )
})
