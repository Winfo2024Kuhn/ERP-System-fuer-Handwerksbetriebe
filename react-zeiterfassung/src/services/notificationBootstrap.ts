/**
 * notificationBootstrap.ts
 *
 * Loest die Benachrichtigungs-Kette aus einer komponentenlokalen Funktion in
 * App.tsx heraus, damit sie von mehreren Stellen aufgerufen werden kann: dem
 * automatischen Start beim Login/QR-Scan (App.tsx) und dem bewussten
 * "Erlauben"-Knopf auf der Einstellungsseite (EinstellungenPage.tsx). Eine
 * komponentenlokale Funktion in App.tsx ist von dort nicht erreichbar.
 *
 * Trennung in zwei oeffentliche Funktionen (Abweichung B des Plans, siehe
 * docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung.md):
 * - starteBenachrichtigungenFallsErlaubt(): laeuft automatisch, fragt NIE.
 * - frageBenachrichtigungenAn(): fragt bewusst, nur vom Einstellungen-Knopf.
 * Beide fuehren bei Erfolg dieselbe private Kette aus.
 *
 * Modul-globaler Zustand fuer das 5-Minuten-Polling-Intervall, nach dem
 * Muster von cameraStreamService.ts (dort: gecachter MediaStream).
 */
import { NotificationService } from './NotificationService'

const TOKEN_STORAGE_KEY = 'zeiterfassung_token'
const POLL_INTERVALL_MS = 5 * 60 * 1000 // 5 Minuten

let intervallId: number | null = null

/**
 * Fuehrt die eigentliche Benachrichtigungs-Kette aus: Token fuer den Service
 * Worker hinterlegen, Web-Push-Abo versuchen (fuer Sperrbildschirm-
 * Benachrichtigungen auf iOS), periodischen Hintergrund-Sync registrieren,
 * sofort einmal pruefen und danach alle 5 Minuten erneut mit dem dann
 * aktuellen Token. 1:1 uebernommen aus dem bisherigen initializeNotifications
 * in App.tsx (granted-Zweig) — nur der Aufruf-Zeitpunkt hat sich getrennt.
 */
async function starteKette(token: string): Promise<void> {
    // Store token for Service Worker periodic background sync
    await NotificationService.storeTokenForSW(token)

    // Try Web Push subscription (required for iOS lock screen notifications)
    const pushSubscribed = await NotificationService.subscribeToPush(token)
    if (pushSubscribed) {
        console.log('Web Push subscription active - server will send notifications')
    } else {
        console.log('Web Push not available - using fallback polling')
    }

    // Register periodic background sync (Android Chrome/Edge)
    await NotificationService.registerPeriodicSync()
    // Check immediately via Service Worker (fallback)
    NotificationService.loadAndCheck(token)

    // Set up periodic check every 5 minutes as fallback
    // (for browsers that don't support Web Push or periodic background sync)
    if (intervallId !== null) {
        clearInterval(intervallId)
    }
    intervallId = window.setInterval(() => {
        const currentToken = localStorage.getItem(TOKEN_STORAGE_KEY)
        if (currentToken) {
            NotificationService.loadAndCheck(currentToken)
        }
    }, POLL_INTERVALL_MS)
}

/**
 * Startet die komplette Benachrichtigungs-Kette NUR, wenn die Erlaubnis
 * bereits erteilt ist. Fragt NIE von sich aus nach. Liefert true, wenn die
 * Kette gelaufen ist.
 *
 * Kein ungefragter Prompt mehr beim App-Start und beim QR-Login (Issue
 * #163). Erlaubt der Nutzer die Benachrichtigungen spaeter ueber
 * /einstellungen, laeuft ab dem naechsten Start wieder alles automatisch.
 */
export async function starteBenachrichtigungenFallsErlaubt(token: string): Promise<boolean> {
    if (NotificationService.getPermissionStatus() !== 'granted') {
        return false
    }
    await starteKette(token)
    return true
}

/**
 * Loest den System-Dialog aus (nur vom "Erlauben"-Knopf der
 * Einstellungsseite) und startet bei Zustimmung dieselbe Kette. Liefert true
 * bei Zustimmung.
 */
export async function frageBenachrichtigungenAn(token: string): Promise<boolean> {
    const granted = await NotificationService.requestPermission()
    if (!granted) {
        return false
    }
    await starteKette(token)
    return true
}

/** Beendet das 5-Minuten-Polling (beim Abmelden). Mehrfach aufrufbar. */
export function stoppeBenachrichtigungsIntervall(): void {
    if (intervallId !== null) {
        clearInterval(intervallId)
        intervallId = null
    }
}
