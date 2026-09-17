/**
 * Liest und fragt Berechtigungen fuer Mikrofon, Kamera und Benachrichtigungen ab.
 *
 * Bewusst KEIN Cache und kein eigener Zwischenzustand: jede Abfrage ist frisch. Fuer
 * das Mikrofon gilt (Plan, Entschiedene Punkte Nr. 1) ausdruecklich das Gegenteil von
 * `cameraStreamService`, der einen MediaStream ueber die gesamte Lifetime der
 * Scanner-Page haelt: ein offen gehaltener Mikrofon-Stream zeigt auf iOS dauerhaft
 * einen orangen Punkt in der Statusleiste, auch nachdem das Diktieren laengst
 * abgeschlossen ist. Deshalb fordert jede Aufnahme ihren eigenen Stream frisch an und
 * gibt ihn sofort wieder frei (siehe `frageMikrofonAn`) — der Preis dafuer ist
 * moeglicherweise ein zusaetzlicher Berechtigungsdialog auf iOS, das ist bewusst in
 * Kauf genommen.
 *
 * Ausserdem gilt projektweit (Plan, Entschiedene Punkte Nr. 3): ein Berechtigungsstatus,
 * der sich nicht sicher ermitteln laesst, wird NIE als sicher behauptet. Lieber ehrlich
 * "noch nicht gefragt" melden, siehe Kommentar bei `leseGeraeteStatus` unten.
 */

import { NotificationService } from './NotificationService'
import { acquireCameraStream, releaseCameraStream } from './cameraStreamService'

/** 'nicht-gefragt' ist auch die ehrliche Antwort, wenn der Status NICHT ermittelbar war. */
export type BerechtigungsStatus = 'erlaubt' | 'nicht-gefragt' | 'blockiert' | 'nicht-verfuegbar'

/**
 * Berechtigungsstatus fuer Benachrichtigungen.
 *
 * Anders als bei Mikrofon/Kamera gibt es hier keinen Umweg ueber
 * `navigator.permissions.query` — `Notification.permission` ist der verlaessliche,
 * synchrone Weg dafuer (siehe `NotificationService.getPermissionStatus()`).
 */
export function leseBenachrichtigungsStatus(): BerechtigungsStatus {
    if (!NotificationService.isSupported()) return 'nicht-verfuegbar'
    switch (NotificationService.getPermissionStatus()) {
        case 'granted': return 'erlaubt'
        case 'denied': return 'blockiert'
        case 'default': return 'nicht-gefragt'
        case 'unsupported': return 'nicht-verfuegbar'
    }
}

/**
 * Liest den Berechtigungsstatus fuer Mikrofon oder Kamera per `navigator.permissions.query`.
 *
 * Safari/iOS beantwortet diese Abfrage je nach Version unterschiedlich zuverlaessig —
 * manche Versionen unterstuetzen 'microphone'/'camera' als Permission-Name gar nicht
 * oder werfen dabei eine Ausnahme. Lieber ehrlich "Noch nicht gefragt" anzeigen und den
 * Knopf trotzdem anbieten, als einen Status zu behaupten, den wir nicht ermitteln
 * konnten. Deshalb wird JEDER unbrauchbare Fall — fehlende API, Ausnahme, unbekannter
 * Rueckgabewert — auf 'nicht-gefragt' abgebildet, niemals auf 'erlaubt' oder 'blockiert'.
 */
async function leseGeraeteStatus(name: 'microphone' | 'camera'): Promise<BerechtigungsStatus> {
    if (!navigator.mediaDevices?.getUserMedia) return 'nicht-verfuegbar'
    if (!navigator.permissions?.query) return 'nicht-gefragt'
    try {
        const status = await navigator.permissions.query({ name: name as PermissionName })
        switch (status.state) {
            case 'granted': return 'erlaubt'
            case 'denied': return 'blockiert'
            case 'prompt': return 'nicht-gefragt'
            default: return 'nicht-gefragt'
        }
    } catch {
        return 'nicht-gefragt'
    }
}

/** Berechtigungsstatus fuer das Mikrofon (per `navigator.permissions.query`, defensiv). */
export async function leseMikrofonStatus(): Promise<BerechtigungsStatus> {
    return leseGeraeteStatus('microphone')
}

/** Berechtigungsstatus fuer die Kamera (per `navigator.permissions.query`, defensiv). */
export async function leseKameraStatus(): Promise<BerechtigungsStatus> {
    return leseGeraeteStatus('camera')
}

/**
 * Loest genau einmal den System-Berechtigungsdialog fuer das Mikrofon aus und gibt es
 * sofort wieder frei. Es wird bewusst kein Stream gehalten (siehe Modul-Kommentar oben).
 * Fehler (z.B. Nutzer lehnt ab) werden unveraendert weitergeworfen — die aufrufende
 * Seite zeigt dafuer einen Toast.
 */
export async function frageMikrofonAn(): Promise<void> {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    stream.getTracks().forEach(t => {
        try { t.stop() } catch { /* ignore */ }
    })
}

/**
 * Dito fuer die Kamera — ueber den bestehenden `cameraStreamService`, keine zweite
 * Kamera-Logik. `releaseCameraStream()` laeuft in `finally`, damit auch ein
 * abgelehnter oder fehlgeschlagener Zugriff nichts offen haelt.
 */
export async function frageKameraAn(): Promise<void> {
    try {
        await acquireCameraStream({ video: true })
    } finally {
        releaseCameraStream()
    }
}
