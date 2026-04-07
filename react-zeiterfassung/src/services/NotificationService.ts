/**
 * NotificationService - Manages push notifications for appointment reminders
 * 
 * Uses Web Push API (VAPID) for true server-sent push notifications.
 * This enables lock screen notifications on iOS (16.4+) and Android,
 * even when the app is closed or the screen is locked.
 * 
 * The server sends push messages:
 * - 24 hours before an appointment
 * - 1 hour before an appointment
 * 
 * Fallback: If Web Push is not available, uses SW message-based
 * polling (works when app is in foreground).
 */

// IndexedDB name used by the service worker for tracking sent notifications
const SENT_DB_NAME = 'sw-notifications'
const SENT_STORE_NAME = 'sent'

/**
 * Convert a URL-safe base64 VAPID key to a Uint8Array for PushManager.subscribe()
 */
function urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
    const rawData = window.atob(base64)
    const outputArray = new Uint8Array(rawData.length)
    for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i)
    }
    return outputArray
}

export const NotificationService = {
    /**
     * Check if notifications are supported in this browser
     */
    isSupported(): boolean {
        return 'Notification' in window && 'serviceWorker' in navigator
    },

    /**
     * Check if Web Push (PushManager) is supported
     */
    isPushSupported(): boolean {
        return 'PushManager' in window && 'serviceWorker' in navigator
    },

    /**
     * Get current notification permission status
     */
    getPermissionStatus(): NotificationPermission | 'unsupported' {
        if (!('Notification' in window)) return 'unsupported'
        return Notification.permission
    },

    /**
     * Request notification permission from user
     * Returns true if permission granted
     */
    async requestPermission(): Promise<boolean> {
        if (!('Notification' in window)) {
            console.log('Notifications not supported in this browser')
            return false
        }

        if (Notification.permission === 'granted') {
            return true
        }

        if (Notification.permission === 'denied') {
            console.log('Notification permission was previously denied')
            return false
        }

        try {
            const result = await Notification.requestPermission()
            return result === 'granted'
        } catch (err) {
            console.error('Error requesting notification permission:', err)
            return false
        }
    },

    /**
     * Subscribe to Web Push notifications via the Push API.
     * This is required for iOS lock screen notifications.
     * The subscription is sent to the server which will use it
     * to send push messages for calendar reminders.
     */
    async subscribeToPush(token: string): Promise<boolean> {
        if (!this.isPushSupported()) {
            console.log('[Push] PushManager not supported')
            return false
        }

        try {
            // 1. Get VAPID public key from server
            const vapidRes = await fetch(`/api/push/vapid-key?token=${encodeURIComponent(token)}`)
            if (!vapidRes.ok) {
                console.log('[Push] Failed to get VAPID key:', vapidRes.status)
                return false
            }
            const { publicKey, enabled } = await vapidRes.json()
            if (enabled === 'false' || !publicKey) {
                console.log('[Push] Web Push not enabled on server')
                return false
            }

            // 2. Subscribe via PushManager
            const registration = await navigator.serviceWorker.ready
            let subscription = await registration.pushManager.getSubscription()

            if (!subscription) {
                const applicationServerKey = urlBase64ToUint8Array(publicKey)
                subscription = await registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: applicationServerKey.buffer as ArrayBuffer
                })
                console.log('[Push] New push subscription created')
            } else {
                console.log('[Push] Existing push subscription found')
            }

            // 3. Send subscription to server
            const subJson = subscription.toJSON()
            const res = await fetch(`/api/push/subscribe?token=${encodeURIComponent(token)}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    endpoint: subJson.endpoint,
                    p256dh: subJson.keys?.p256dh || '',
                    auth: subJson.keys?.auth || ''
                })
            })

            if (res.ok) {
                console.log('[Push] Subscription sent to server successfully')
                return true
            } else {
                console.warn('[Push] Server rejected subscription:', res.status)
                return false
            }
        } catch (err) {
            console.error('[Push] Error subscribing to push:', err)
            return false
        }
    },

    /**
     * Store token in IndexedDB so the Service Worker can access it
     * for periodic background sync (localStorage is not available in SW).
     */
    async storeTokenForSW(token: string): Promise<void> {
        try {
            const db = await this._openSentDB()
            const tx = db.transaction(SENT_STORE_NAME, 'readwrite')
            const store = tx.objectStore(SENT_STORE_NAME)
            store.put({ key: '__auth_token', value: token, timestamp: Date.now() })
        } catch (err) {
            console.error('Error storing token for SW:', err)
        }
    },

    /**
     * Open the shared IndexedDB for notification tracking
     */
    _openSentDB(): Promise<IDBDatabase> {
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
    },

    /**
     * Register for Periodic Background Sync if available.
     * This allows the Service Worker to check for appointments
     * even when the app is closed (Android Chrome/Edge only).
     */
    async registerPeriodicSync(): Promise<void> {
        try {
            const registration = await navigator.serviceWorker.ready
            // Check if periodicSync is available (Chrome/Edge on Android)
            if ('periodicSync' in registration) {
                const status = await navigator.permissions.query({
                    name: 'periodic-background-sync' as PermissionName
                })
                if (status.state === 'granted') {
                    await (registration as ServiceWorkerRegistration & { periodicSync: { register: (tag: string, options: { minInterval: number }) => Promise<void> } }).periodicSync.register('check-appointments', {
                        minInterval: 5 * 60 * 1000 // minimum 5 minutes
                    })
                    console.log('Periodic background sync registered')
                } else {
                    console.log('Periodic background sync permission not granted')
                }
            } else {
                console.log('Periodic background sync not supported')
            }
        } catch (err) {
            console.error('Error registering periodic sync:', err)
        }
    },

    /**
     * Called after a Lieferschein was successfully uploaded from the mobile app.
     */
    async onLieferscheinUploaded(token: string): Promise<void> {
        await this.storeTokenForSW(token)
        console.log('[NotificationService] Lieferschein uploaded – PC will pick it up on next poll')
    },

    /**
     * Called after a Reklamation was successfully created from the mobile app.
     */
    async onReklamationCreated(token: string): Promise<void> {
        await this.storeTokenForSW(token)
        console.log('[NotificationService] Reklamation created – PC will pick it up on next poll')
    },

    /**
     * Send a message to the Service Worker to check appointments
     * and show notifications. This is a fallback for browsers
     * that don't support the Push API (works when app is open).
     */
    async loadAndCheck(token: string): Promise<void> {
        if (!token || !this.isSupported() || Notification.permission !== 'granted') {
            return
        }

        try {
            // Store token in IndexedDB for periodic background sync
            await this.storeTokenForSW(token)

            // Send message to Service Worker to check and notify
            const registration = await navigator.serviceWorker.ready
            if (registration.active) {
                registration.active.postMessage({
                    type: 'CHECK_NOTIFICATIONS',
                    token: token
                })
                console.log('Sent CHECK_NOTIFICATIONS message to Service Worker')
            }
        } catch (err) {
            console.error('Error sending notification check to SW:', err)
        }
    }
}
