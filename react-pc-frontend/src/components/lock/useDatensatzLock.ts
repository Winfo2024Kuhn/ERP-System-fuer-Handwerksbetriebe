import { useEffect, useRef, useState } from 'react';

/**
 * Verwaltet das Soft-Lock fuer einen einzelnen sperrbaren Datensatz
 * (Ausgangs-/Eingangsbeleg) -- der fuenfte der fuenf wiederverwendbaren
 * Frontend-Bausteine aus der Spec (Issue #82). Verallgemeinerte Nachfolge-
 * Fassung von useDocumentLock (siehe src/components/useDocumentLock.ts, der
 * alte Hook wird spaeter als Ganzes geloescht) -- gleiche Netzwerk-Mechanik
 * (acquire beim Mount, Heartbeat-Intervall, Freigabe bei "pagehide" und beim
 * Unmount mit keepalive), aber auf die neue, verallgemeinerte Route
 * /api/datensatz-locks/{typ}/{id}/... und den Typ 'AUSGANG' | 'EINGANG'
 * umgestellt (siehe DatensatzLockController).
 *
 * Modus/kannBearbeiten/verbindungWeg gehen direkt als Props an
 * BearbeitenLeiste, halterName/seit direkt an GesperrtHinweis -- siehe
 * die Kommentare dort. "lesen"/"bearbeiten" ist ein reiner UI-Umschalter:
 * das Lock wird bereits beim Mount erworben (bzw. als belegt erkannt) und
 * fuer die gesamte Seiten-Lebensdauer gehalten; onBearbeiten/onFertig loesen
 * dafuer KEINEN zusaetzlichen Netzwerk-Request aus. Aktives Freigeben (fuer
 * den spaeteren X-Button-Ablauf: speichern, Sperre freigeben, Tab
 * schliessen) steht ueber freigeben() zur Verfuegung.
 *
 * Verbleibende Sekunden bis zur automatischen Freigabe kommen bewusst NICHT
 * von hier, sondern aus useIdleTimer -- die Seite verdrahtet beides. Ein
 * zweiter, eigener Timer hier wuerde genau den Fehler wiederholen, an dem
 * der heutige Dokument-Editor krankt (zwei Timer, die auseinanderlaufen).
 */

export type DatensatzLockTyp = 'AUSGANG' | 'EINGANG';

type DatensatzLockStatus = 'idle' | 'loading' | 'acquired' | 'locked-by-other' | 'error';

interface DatensatzLockDto {
    status: 'ACQUIRED' | 'LOCKED_BY_OTHER';
    holderUserId: number;
    holderDisplayName: string;
    acquiredAt: string;
    lastHeartbeatAt: string;
}

interface Halter {
    displayName: string;
    acquiredAt: string;
}

export interface UseDatensatzLockResult {
    /** Aktueller Anzeige-Modus -- direkt als Prop an BearbeitenLeiste. */
    modus: 'lesen' | 'bearbeiten';
    /** false, wenn ein anderer Nutzer den Datensatz gerade haelt -- direkt an BearbeitenLeiste. */
    kannBearbeiten: boolean;
    /** true nach mehreren fehlgeschlagenen Heartbeats in Folge -- direkt an BearbeitenLeiste. */
    verbindungWeg: boolean;
    /** Anzeigename des Halters, nur gesetzt waehrend kannBearbeiten=false -- direkt an GesperrtHinweis. */
    halterName?: string;
    /** Minuten seit Sperrbeginn, als fertig formatierter String -- direkt an GesperrtHinweis. */
    seit?: string;
    /** Klick auf "Bearbeiten": schaltet in den Bearbeiten-Modus (kein Netzwerk-Request, das Lock ist bereits gehalten). */
    onBearbeiten: () => void;
    /** Klick auf "Fertig": schaltet zurueck in den Lesen-Modus (das Lock bleibt gehalten). */
    onFertig: () => void;
    /** Aktives Freigeben des Locks (z.B. fuer den X-Button-Ablauf). */
    freigeben: () => Promise<void>;
}

const HEARTBEAT_INTERVAL_MS = 30_000;
/** Erst ab dieser Anzahl aufeinanderfolgender Fehlschlaege gilt die Verbindung als weg -- ein einzelner Netz-Hiccup reicht nicht. */
const VERBINDUNG_WEG_SCHWELLE = 2;

export function useDatensatzLock(
    typ: DatensatzLockTyp,
    id: number | null | undefined
): UseDatensatzLockResult {
    const [status, setStatus] = useState<DatensatzLockStatus>('idle');
    const [halter, setHalter] = useState<Halter | null>(null);
    const [modus, setModus] = useState<'lesen' | 'bearbeiten'>('lesen');
    const [verbindungWeg, setVerbindungWeg] = useState(false);

    const heldRef = useRef(false);
    const freigebenRef = useRef<() => Promise<void>>(async () => {});

    useEffect(() => {
        if (id == null) {
            heldRef.current = false;
            return;
        }

        const lockUrl = `/api/datensatz-locks/${typ}/${id}`;
        const controller = new AbortController();
        let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
        let cancelled = false;
        let fehlschlaegeInFolge = 0;

        const stopHeartbeat = () => {
            if (heartbeatTimer != null) {
                clearInterval(heartbeatTimer);
                heartbeatTimer = null;
            }
        };

        const acquire = async () => {
            setStatus('loading');
            try {
                const res = await fetch(`${lockUrl}/acquire`, {
                    method: 'POST',
                    credentials: 'same-origin',
                    signal: controller.signal,
                });
                if (cancelled) return;

                if (res.status === 409) {
                    const data = (await res.json().catch(() => null)) as DatensatzLockDto | null;
                    setHalter(toHalter(data));
                    setStatus('locked-by-other');
                    heldRef.current = false;
                    return;
                }

                if (!res.ok) {
                    setStatus('error');
                    heldRef.current = false;
                    return;
                }

                setHalter(null);
                setStatus('acquired');
                heldRef.current = true;
                heartbeatTimer = setInterval(() => void heartbeat(), HEARTBEAT_INTERVAL_MS);
            } catch (err) {
                if (cancelled || (err instanceof DOMException && err.name === 'AbortError')) {
                    return;
                }
                setStatus('error');
                heldRef.current = false;
            }
        };

        const heartbeat = async () => {
            try {
                const res = await fetch(`${lockUrl}/heartbeat`, {
                    method: 'POST',
                    credentials: 'same-origin',
                });
                if (cancelled) return;

                if (res.status === 409) {
                    const data = (await res.json().catch(() => null)) as DatensatzLockDto | null;
                    fehlschlaegeInFolge = 0;
                    setVerbindungWeg(false);
                    setHalter(toHalter(data));
                    setStatus('locked-by-other');
                    heldRef.current = false;
                    stopHeartbeat();
                    return;
                }

                if (!res.ok) {
                    fehlschlaegeInFolge += 1;
                    if (fehlschlaegeInFolge >= VERBINDUNG_WEG_SCHWELLE) {
                        setVerbindungWeg(true);
                    }
                    return;
                }

                fehlschlaegeInFolge = 0;
                setVerbindungWeg(false);
            } catch {
                if (cancelled) return;
                fehlschlaegeInFolge += 1;
                if (fehlschlaegeInFolge >= VERBINDUNG_WEG_SCHWELLE) {
                    setVerbindungWeg(true);
                }
            }
        };

        const releaseKeepalive = () => {
            if (!heldRef.current) return;
            heldRef.current = false;
            try {
                void fetch(lockUrl, {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    keepalive: true,
                });
            } catch {
                // best effort
            }
        };

        const aktivFreigeben = async () => {
            stopHeartbeat();
            if (!heldRef.current) {
                setModus('lesen');
                return;
            }
            heldRef.current = false;
            try {
                await fetch(lockUrl, {
                    method: 'DELETE',
                    credentials: 'same-origin',
                });
            } catch {
                // best effort -- Server raeumt verwaiste Locks nach 90s selbst auf
            }
            if (cancelled) return;
            setStatus('idle');
            setHalter(null);
            setModus('lesen');
        };
        freigebenRef.current = aktivFreigeben;

        const handlePageHide = () => releaseKeepalive();
        window.addEventListener('pagehide', handlePageHide);

        void acquire();

        return () => {
            cancelled = true;
            controller.abort();
            stopHeartbeat();
            window.removeEventListener('pagehide', handlePageHide);
            releaseKeepalive();
            setStatus('idle');
            setHalter(null);
            setModus('lesen');
            setVerbindungWeg(false);
        };
    }, [typ, id]);

    const kannBearbeiten = status !== 'locked-by-other';

    return {
        modus,
        kannBearbeiten,
        verbindungWeg,
        halterName: status === 'locked-by-other' ? halter?.displayName : undefined,
        seit: status === 'locked-by-other' ? formatiereSeitMinuten(halter) : undefined,
        onBearbeiten: () => {
            if (!kannBearbeiten) return;
            setModus('bearbeiten');
        },
        onFertig: () => setModus('lesen'),
        freigeben: () => freigebenRef.current(),
    };
}

function toHalter(data: DatensatzLockDto | null): Halter | null {
    if (!data) return null;
    return {
        displayName: data.holderDisplayName,
        acquiredAt: data.acquiredAt,
    };
}

function formatiereSeitMinuten(halter: Halter | null): string | undefined {
    if (!halter) return undefined;
    const minuten = Math.floor((Date.now() - new Date(halter.acquiredAt).getTime()) / 60_000);
    return String(Math.max(0, minuten));
}
