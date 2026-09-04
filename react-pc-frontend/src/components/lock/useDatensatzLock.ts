import { useCallback, useEffect, useRef, useState } from 'react';

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
 * die Kommentare dort. status wird zusaetzlich roh exportiert, damit die
 * aufrufende Seite z.B. bei 'error' einen eigenen Hinweis zeigen kann.
 *
 * Anders als im ersten Entwurf ist "Bearbeiten" HIER kein reiner
 * UI-Umschalter mehr: das Mount-Acquire haelt das Lock zwar zunaechst fuer
 * die gesamte Seiten-Lebensdauer, aber sobald es verloren geht (freigeben(),
 * ein Acquire-Fehler oder eine 409-Fremdsperre), MUSS ein Klick auf
 * "Bearbeiten" ein frisches Acquire ausloesen -- alles andere waere ein
 * Editor, der ohne gehaltenes Lock und ohne Heartbeat editierbar erscheint
 * (siehe Kontext-Log, Nachbesserung 1). onBearbeiten() haengt darum NICHT
 * von kannBearbeiten ab, sondern direkt vom internen "wird der Datensatz
 * gerade tatsaechlich gehalten"-Zustand (heldRef):
 *   - Ohne ID (kein Lock-Konzept) oder bereits gehalten: nur die Anzeige
 *     umschalten, kein Request.
 *   - Waehrend ein Acquire noch laeuft oder nach einem Server-/Netzfehler:
 *     wirkungslos -- ein Doppel-Request waere sinnlos, ein automatischer
 *     Fehler-Retry ist nicht Teil dieser Spec.
 *   - Sonst (frisch freigegeben ODER durch einen anderen gesperrt): ein
 *     neues Acquire versuchen. Bei 200 wechselt der Modus, bei 409 bleibt er
 *     auf "lesen" und halterName/seit zeigen den (neuen) Halter.
 *
 * onFertig() gibt das Lock jetzt ebenfalls aktiv frei (nicht nur die
 * Anzeige) -- wer "Fertig" klickt, blockiert Kollegen danach nicht mehr.
 * Fuer den kuenftigen X-Button-Ablauf (speichern, freigeben, Tab schliessen)
 * steht dieselbe Freigabe-Logik zusaetzlich als freigeben() zur Verfuegung.
 *
 * Verbleibende Sekunden bis zur automatischen Freigabe kommen bewusst NICHT
 * von hier, sondern aus useIdleTimer -- die Seite verdrahtet beides. Ein
 * zweiter, eigener Timer hier wuerde genau den Fehler wiederholen, an dem
 * der heutige Dokument-Editor krankt (zwei Timer, die auseinanderlaufen).
 *
 * WICHTIG (Kontext-Log, Nachbesserung 2): kannBearbeiten bedeutet "ein Klick
 * auf Bearbeiten ist gerade sinnvoll", NICHT "wir halten das Lock". Eine
 * Fremdsperre (locked-by-other) und der frisch freigegebene Zustand (idle)
 * liefern darum beide kannBearbeiten=true -- BearbeitenLeiste rendert den
 * Knopf disabled=!kannBearbeiten, und genau in diesen beiden Zustaenden
 * braucht der Knopf einen Klick, um den Retry aus onBearbeiten() ueberhaupt
 * ausloesen zu koennen. Nur waehrend ein Acquire laeuft (loading) oder nach
 * einem Fehler (error) ist kannBearbeiten=false, weil ein Klick dort
 * wirkungslos waere (siehe onBearbeiten unten).
 */

export type DatensatzLockTyp = 'AUSGANG' | 'EINGANG';

export type DatensatzLockStatus = 'idle' | 'loading' | 'acquired' | 'locked-by-other' | 'error';

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
    /**
     * "Ein Klick auf Bearbeiten ist gerade sinnvoll" -- direkt als Prop an
     * BearbeitenLeiste (dort disabled=!kannBearbeiten). True bei 'idle'
     * (frisch freigegeben oder noch nie geholt), 'acquired' und AUCH bei
     * 'locked-by-other' (ein Klick versucht dann die Uebernahme, siehe
     * onBearbeiten). Nur bei 'loading' oder 'error' false, weil ein Klick
     * dort wirkungslos waere.
     */
    kannBearbeiten: boolean;
    /** Roher interner Zustand, z.B. damit die Seite bei 'error' einen Hinweis zeigen kann. */
    status: DatensatzLockStatus;
    /** true nach mehreren fehlgeschlagenen Heartbeats in Folge -- direkt an BearbeitenLeiste. */
    verbindungWeg: boolean;
    /** Anzeigename des Halters, nur gesetzt waehrend jemand anderes haelt -- direkt an GesperrtHinweis. */
    halterName?: string;
    /** Minuten seit Sperrbeginn, als fertig formatierter String -- direkt an GesperrtHinweis. */
    seit?: string;
    /** Klick auf "Bearbeiten": versucht bei Bedarf ein frisches Acquire, siehe Klassenkommentar. */
    onBearbeiten: () => void;
    /** Klick auf "Fertig": gibt das Lock aktiv frei und schaltet zurueck auf "lesen". */
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

    // heldRef ist die eine "Quelle der Wahrheit" darueber, ob WIR das Lock
    // gerade tatsaechlich halten -- synchron gepflegt, damit onBearbeiten/
    // die Freigabe-Pfade nicht auf den (asynchronen) React-State warten
    // muessen. status==='acquired' gilt genau dann, wenn heldRef.current
    // true ist; beides wird an denselben Stellen gemeinsam gesetzt.
    const heldRef = useRef(false);
    const mountedRef = useRef(true);
    // Jeder neue Acquire-/Freigabe-Versuch erhoeht die Generation. Ergebnisse
    // eines UEBERHOLTEN Versuchs (z.B. ein Acquire, das durch ein
    // zwischenzeitliches freigeben() oder einen zweiten Klick ueberholt
    // wurde) werden anhand der Generation verworfen, statt auf einen
    // veralteten Zustand zu schreiben.
    const generationRef = useRef(0);
    const heartbeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const fehlschlaegeRef = useRef(0);
    const controllerRef = useRef<AbortController | null>(null);

    const lockUrl = id != null ? `/api/datensatz-locks/${typ}/${id}` : null;

    const stopHeartbeat = useCallback(() => {
        if (heartbeatTimerRef.current != null) {
            clearInterval(heartbeatTimerRef.current);
            heartbeatTimerRef.current = null;
        }
    }, []);

    const heartbeat = useCallback(
        async (url: string, gen: number) => {
            try {
                const res = await fetch(`${url}/heartbeat`, {
                    method: 'POST',
                    credentials: 'same-origin',
                });
                if (!mountedRef.current || gen !== generationRef.current) return;

                if (res.status === 409) {
                    const data = (await res.json().catch(() => null)) as DatensatzLockDto | null;
                    fehlschlaegeRef.current = 0;
                    setVerbindungWeg(false);
                    setHalter(toHalter(data));
                    setStatus('locked-by-other');
                    heldRef.current = false;
                    stopHeartbeat();
                    return;
                }

                if (!res.ok) {
                    fehlschlaegeRef.current += 1;
                    if (fehlschlaegeRef.current >= VERBINDUNG_WEG_SCHWELLE) {
                        setVerbindungWeg(true);
                    }
                    return;
                }

                fehlschlaegeRef.current = 0;
                setVerbindungWeg(false);
            } catch {
                if (!mountedRef.current || gen !== generationRef.current) return;
                fehlschlaegeRef.current += 1;
                if (fehlschlaegeRef.current >= VERBINDUNG_WEG_SCHWELLE) {
                    setVerbindungWeg(true);
                }
            }
        },
        [stopHeartbeat]
    );

    const startHeartbeat = useCallback(
        (url: string, gen: number) => {
            stopHeartbeat();
            fehlschlaegeRef.current = 0;
            // verbindungWeg explizit zuruecksetzen, nicht nur den Zaehler:
            // ein VORHERIGER Zyklus (vor einem freigeben()/Lock-Verlust)
            // kann das Flag auf true stehen gelassen haben (Fehlschlaege
            // wurden nie durch einen weiteren erfolgreichen Heartbeat
            // "geheilt", weil der Zyklus vorher schon endete). Ohne diesen
            // Reset wuerde die "Verbindung weg"-Warnung nach einem
            // erfolgreichen erneuten Acquire faelschlich weiter angezeigt,
            // obwohl der frische Heartbeat noch gar keine Chance hatte,
            // etwas zu melden (siehe Kontext-Log, Nachbesserung 2).
            setVerbindungWeg(false);
            heartbeatTimerRef.current = setInterval(() => void heartbeat(url, gen), HEARTBEAT_INTERVAL_MS);
        },
        [heartbeat, stopHeartbeat]
    );

    /** Ein (erneuter) Versuch, das Lock fuer `url` zu erwerben. Wird sowohl vom Mount-Effekt als auch von onBearbeiten aufgerufen. */
    const acquire = useCallback(
        async (url: string) => {
            controllerRef.current?.abort();
            const controller = new AbortController();
            controllerRef.current = controller;
            const gen = ++generationRef.current;

            stopHeartbeat();
            setStatus('loading');
            try {
                const res = await fetch(`${url}/acquire`, {
                    method: 'POST',
                    credentials: 'same-origin',
                    signal: controller.signal,
                });
                if (!mountedRef.current || gen !== generationRef.current) return;

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
                startHeartbeat(url, gen);
            } catch (err) {
                if (!mountedRef.current || gen !== generationRef.current) return;
                if (err instanceof DOMException && err.name === 'AbortError') return;
                setStatus('error');
                heldRef.current = false;
            }
        },
        [startHeartbeat, stopHeartbeat]
    );

    /** Freigabe im Hintergrund (Unmount/"pagehide"): "fire and forget" mit keepalive, nur wenn das Lock gerade gehalten wird. */
    const releaseKeepalive = useCallback(
        (url: string) => {
            controllerRef.current?.abort();
            if (!heldRef.current) return;
            heldRef.current = false;
            stopHeartbeat();
            try {
                void fetch(url, {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    keepalive: true,
                });
            } catch {
                // best effort
            }
        },
        [stopHeartbeat]
    );

    /** Aktives, awaitbares Freigeben -- von onFertig() und vom exponierten freigeben() genutzt. */
    const aktivFreigeben = useCallback(
        async (url: string | null) => {
            controllerRef.current?.abort();
            stopHeartbeat();
            generationRef.current += 1;
            const warGehalten = heldRef.current;
            heldRef.current = false;
            if (url != null && warGehalten) {
                try {
                    await fetch(url, {
                        method: 'DELETE',
                        credentials: 'same-origin',
                    });
                } catch {
                    // best effort -- Server raeumt verwaiste Locks nach 90s selbst auf
                }
            }
            if (!mountedRef.current) return;
            setStatus('idle');
            setHalter(null);
            setModus('lesen');
        },
        [stopHeartbeat]
    );

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        if (lockUrl == null) {
            // Kein setState hier: entweder ist dies der allererste Render
            // (Status/Halter/Modus stehen bereits auf ihren Default-Werten)
            // oder lockUrl wechselte von einem Wert zu null -- dann hat der
            // Cleanup des VORHERIGEN Effekt-Durchlaufs (siehe unten) bereits
            // auf die Default-Werte zurueckgesetzt, bevor dieser Zweig hier
            // ueberhaupt laeuft.
            heldRef.current = false;
            return;
        }

        // acquire() setzt als allerersten (synchronen) Schritt setStatus('loading')
        // -- das meldet der Linter hier als "setState direkt im Effekt", weil er
        // die Spur bis in die aufgerufene Funktion zurueckverfolgt. Das ist hier
        // aber gewollt und kein abgeleiteter Render-Wert: der Uebergang von
        // 'idle' zu 'loading' passiert exakt dann, wenn der Lock-Erwerb fuer
        // diese typ/id-Kombination beginnt (Mount ODER ein manueller Retry via
        // onBearbeiten, das denselben acquire() direkt aufruft) -- das laesst
        // sich nicht "aus Props/State ableiten" und gehoert daher in den Effekt-
        // Aufbau selbst, nicht in eine Reaktion darauf (vgl. useIdleTimer.ts,
        // das denselben Fall aus demselben Grund unterdrueckt).
        // eslint-disable-next-line react-hooks/set-state-in-effect
        void acquire(lockUrl);

        const handlePageHide = () => releaseKeepalive(lockUrl);
        window.addEventListener('pagehide', handlePageHide);

        return () => {
            window.removeEventListener('pagehide', handlePageHide);
            releaseKeepalive(lockUrl);
            generationRef.current += 1;
            setStatus('idle');
            setHalter(null);
            setModus('lesen');
            setVerbindungWeg(false);
        };
        // acquire/releaseKeepalive haengen selbst nur an stopHeartbeat (leeres
        // Deps-Array) und sind damit ueber Re-Renders hinweg referenzstabil --
        // der Effekt laeuft also tatsaechlich nur bei einem Wechsel von
        // lockUrl neu, nicht bei jedem Render.
    }, [lockUrl, acquire, releaseKeepalive]);

    const onBearbeiten = useCallback(() => {
        if (lockUrl == null || heldRef.current) {
            setModus('bearbeiten');
            return;
        }
        // Waehrend ein Acquire noch laeuft oder nach einem Fehler bleibt der
        // Klick wirkungslos -- kein Doppel-Request, kein impliziter Retry.
        if (status === 'loading' || status === 'error') return;

        // status ist hier 'idle' (frisch freigegeben) oder 'locked-by-other'
        // (ein anderer hielt den Datensatz zuletzt) -- in beiden Faellen ist
        // ein frischer Versuch sinnvoll, siehe Klassenkommentar.
        void (async () => {
            await acquire(lockUrl);
            if (heldRef.current) {
                setModus('bearbeiten');
            }
        })();
    }, [lockUrl, status, acquire]);

    const freigeben = useCallback(() => aktivFreigeben(lockUrl), [lockUrl, aktivFreigeben]);

    const onFertig = useCallback(() => {
        void freigeben();
    }, [freigeben]);

    // Bewusst NICHT auf status === 'acquired' verengt: 'idle' (frisch
    // freigegeben) und 'locked-by-other' (Fremdsperre) sind die beiden
    // Faelle, in denen der Retry aus onBearbeiten() ueberhaupt erst noetig
    // wird -- der Knopf, der ihn ausloest, darf dafuer nicht deaktiviert
    // sein. Siehe Klassenkommentar und Kontext-Log, Nachbesserung 2.
    const kannBearbeiten = lockUrl == null || (status !== 'loading' && status !== 'error');

    return {
        modus,
        kannBearbeiten,
        status,
        verbindungWeg,
        halterName: status === 'locked-by-other' ? halter?.displayName : undefined,
        seit: status === 'locked-by-other' ? formatiereSeitMinuten(halter) : undefined,
        onBearbeiten,
        onFertig,
        freigeben,
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
