/**
 * Modul-globaler MediaStream-Cache fuer die Beleg-Kamera.
 *
 * Hintergrund: Auf iOS PWA (Home-Bildschirm-Modus) wird die Kamera-Permission
 * NICHT pro Origin gespeichert — jeder neue `getUserMedia`-Aufruf kann erneut
 * einen Permission-Prompt ausloesen, selbst wenn der User vor wenigen Sekunden
 * zugestimmt hat. `react-webcam` ruft bei jedem Mount neu getUserMedia auf, was
 * im Beleg-Scanner-Workflow zu einem Prompt pro Modal-Oeffnung gefuehrt hat.
 *
 * Diese Service-Schicht haelt den MediaStream ueber die gesamte Lifetime der
 * Scanner-Page lebendig: nur EIN getUserMedia-Aufruf pro Page-Besuch, alle
 * Modal-Oeffnungen wiederverwenden denselben Stream. Erst beim Verlassen der
 * Scanner-Page (`releaseCameraStream`) werden die Tracks gestoppt.
 * Ausnahme Lieferscheine: Dort stoppt ein fertiger Scan die Kamera sofort, weil
 * der weiterlaufende 4K-Stream neben der Pruefen-Ansicht das iPhone in den
 * Speichermangel trieb (iOS hat die App dann neu geladen).
 */

let cachedStream: MediaStream | null = null
let pendingAcquire: Promise<MediaStream> | null = null

function streamIsLive(stream: MediaStream | null): stream is MediaStream {
    if (!stream) return false
    return stream.getVideoTracks().some(t => t.readyState === 'live')
}

export async function acquireCameraStream(
    constraints: MediaStreamConstraints,
): Promise<MediaStream> {
    if (streamIsLive(cachedStream)) {
        return cachedStream
    }
    if (cachedStream && !streamIsLive(cachedStream)) {
        cachedStream = null
    }
    if (pendingAcquire) {
        return pendingAcquire
    }
    pendingAcquire = navigator.mediaDevices
        .getUserMedia(constraints)
        .then(stream => {
            cachedStream = stream
            return stream
        })
        .finally(() => {
            pendingAcquire = null
        })
    return pendingAcquire
}

export function getActiveCameraStream(): MediaStream | null {
    return streamIsLive(cachedStream) ? cachedStream : null
}

export function releaseCameraStream(): void {
    if (cachedStream) {
        cachedStream.getTracks().forEach(t => {
            try { t.stop() } catch { /* ignore */ }
        })
        cachedStream = null
    }
    pendingAcquire = null
}
