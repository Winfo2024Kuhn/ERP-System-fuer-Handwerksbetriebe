/**
 * NotificationService - Manages push notifications for appointment reminders
 * 
 * Uses Service Worker registration.showNotification() for lock screen support.
 * The Service Worker handles the actual notification display, allowing
 * notifications to appear even when the app is in the background or the
 * screen is locked.
 * 
 * Sends notifications:
 * - 24 hours before an appointment
 * - 1 hour before an appointment
 */

// IndexedDB name used by the service worker for tracking sent notifications
const SENT_DB_NAME = 'sw-notifications'
const SENT_STORE_NAME = 'sent'

export const NotificationService = {
    /**
     * Check if notifications are supported in this browser
     */
    isSupported(): boolean {
        return 'Notification' in window && 'serviceWorker' in navigator
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
     * even when the app is closed.
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
     * Stores the token so the Service Worker stays up-to-date and triggers
     * an immediate appointment-notification check.
     * The PC desktop picks up the new Lieferschein via its polling interval
     * (≤60 s) without any extra push needed.
     */
    async onLieferscheinUploaded(token: string): Promise<void> {
        await this.storeTokenForSW(token)
        console.log('[NotificationService] Lieferschein uploaded – PC will pick it up on next poll')
    },

    /**
     * Called after a Reklamation was successfully created from the mobile app.
     * Same pattern as onLieferscheinUploaded – the PC notification bell
     * detects the new open Reklamation on its next poll cycle.
     */
    async onReklamationCreated(token: string): Promise<void> {
        await this.storeTokenForSW(token)
        console.log('[NotificationService] Reklamation created – PC will pick it up on next poll')
    },

    /**
     * Send a message to the Service Worker to check appointments
     * and show notifications via registration.showNotification().
     * This delegates the actual notification display to the SW,
     * which works on the lock screen.
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
