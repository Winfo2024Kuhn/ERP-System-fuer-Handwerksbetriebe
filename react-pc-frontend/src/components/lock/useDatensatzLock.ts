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
 * WICHTIG (Kontext-Log, Task 7b): jedes ERFOLGREICHE Acquire -- ob beim
 * stillen Mount-Erwerb oder als Retry aus onBearbeiten() -- schaltet den
 * Modus selbst auf "bearbeiten" um (siehe acquire()). Vorher blieb der Hook
 * nach dem Mount-Erwerb im Modus "lesen" haengen: wer die Seite oeffnete,
 * hielt das Lock zwar (blockierte also Kollegen), durfte selbst aber nichts
 * bearbeiten, ohne vorher noch einmal auf "Bearbeiten" zu klicken -- das
 * entspricht nicht dem tatsaechlichen Editor-Verhalten und war reine
 * Blockade ohne Nutzen fuer den Oeffnenden selbst. Nur eine Fremdsperre
 * (409, beim Mount ODER beim Heartbeat) und ein Fehler lassen den Modus
 * bewusst auf "lesen" -- die Spec ersetzt damit nur die harte Blockierung
 * bei Fremdsperre durch Nur-Lesen, nicht mehr.
 *
 * Ebenfalls aus dieser Nachbesserung: in den beiden 409-Zweigen (acquire()
 * und heartbeat()) steht die Generationspruefung jetzt ZWEIMAL -- einmal
 * direkt nach dem fetch()-await wie bisher, und ein zweites Mal NACH
 * res.json(). json() ist selbst ein zweiter await, und in dessen Fenster
 * kann ein zwischenzeitliches freigeben() (oder ein neuer Versuch) genau
 * diesen Aufruf ueberholen -- ohne die zweite Pruefung wuerde ein laengst
 * ungueltiger 409-Befund trotzdem noch halterName/status auf den (falschen)
 * Stand schreiben.
 *
 * WICHTIG (Kontext-Log, Task 7b Nachbesserung 1): der Absatz oben behauptete,
 * eine Fremdsperre "beim Mount ODER beim Heartbeat" liesse den Modus auf
 * "lesen" -- fuer den Heartbeat stimmte das bis zu dieser Nachbesserung
 * NICHT: der 409-Zweig in heartbeat() setzte status/heldRef zwar korrekt
 * zurueck, liess modus aber unangetastet. Weil acquire() seit Task 7b nach
 * einem erfolgreichen Erwerb modus auf "bearbeiten" setzt, war damit
 * status='locked-by-other' bei GLEICHZEITIG modus='bearbeiten' erreichbar --
 * genau die seit Review 5 verbotene Kombination "Bearbeiten-Modus ohne
 * gehaltenes Lock" (siehe kannBearbeiten-Definition unten). Fix: setModus
 * ('lesen') ergaenzt, direkt neben heldRef.current=false. Die Invariante
 * gilt jetzt an JEDER Stelle im Hook, die heldRef auf false setzt, fuer sich
 * selbst -- auch dort, wo modus nach heutigem Aufrufer-Gefuege ohnehin schon
 * "lesen" waere (siehe die "defensiv"-Kommentare in acquire()), statt sich
 * auf die Reihenfolge der Aufrufer zu verlassen.
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
                    // Zweite Generationspruefung NACH res.json() (Task 7b): das
                    // await davor kann durch ein zwischenzeitliches freigeben()
                    // oder einen neuen Versuch ueberholt werden -- genau in
                    // diesem Fenster wartet json() noch auf die Antwort. Ohne
                    // diese zweite Pruefung wuerde ein laengst ungueltiger
                    // 409-Befund trotzdem noch halterName/status auf den
                    // (falschen) Stand schreiben.
                    if (!mountedRef.current || gen !== generationRef.current) return;
                    fehlschlaegeRef.current = 0;
                    setVerbindungWeg(false);
                    setHalter(toHalter(data));
                    setStatus('locked-by-other');
                    heldRef.current = false;
                    // Nachbesserung 1 (Task 7b): OHNE dieses setModus blieb der
                    // Modus "bearbeiten" stehen, obwohl das Lock hier gerade an
                    // einen anderen verloren geht -- genau die seit Review 5
                    // verbotene Kombination "Bearbeiten-Modus ohne gehaltenes
                    // Lock". Erreichbar z.B. durch einen gedrosselten
                    // Hintergrund-Tab: das 90-Sekunden-Fenster reisst, ein
                    // Kollege uebernimmt, der naechste Heartbeat bekommt 409.
                    setModus('lesen');
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
                    // Zweite Generationspruefung NACH res.json() (Task 7b): siehe
                    // ausfuehrliche Begruendung im gleichen Zweig in heartbeat()
                    // oben -- json() ist selbst ein zweiter await, in dessen
                    // Fenster ein freigeben() oder ein neuer Versuch diesen
                    // Acquire ueberholen kann.
                    if (!mountedRef.current || gen !== generationRef.current) return;
                    setHalter(toHalter(data));
                    setStatus('locked-by-other');
                    heldRef.current = false;
                    // Defensiv (Nachbesserung 1, Task 7b): siehe die
                    // ausfuehrliche Begruendung im 409-Zweig von heartbeat()
                    // oben. An dieser Stelle ist modus nach heutigem Stand
                    // bereits "lesen" (acquire() wird nur aufgerufen, wenn
                    // heldRef vorher schon false war), aber die Invariante
                    // "heldRef=false => modus='lesen'" soll an JEDER Stelle,
                    // die heldRef auf false setzt, selbst gelten -- nicht nur
                    // aus der Reihenfolge der Aufrufer folgen.
                    setModus('lesen');
                    return;
                }

                if (!res.ok) {
                    setStatus('error');
                    heldRef.current = false;
                    setModus('lesen'); // defensiv, siehe Kommentar im 409-Zweig oben
                    return;
                }

                setHalter(null);
                setStatus('acquired');
                heldRef.current = true;
                // Modus direkt auf "bearbeiten" umschalten (Task 7b): sowohl beim
                // stillen Mount-Erwerb als auch bei einem Retry aus onBearbeiten()
                // -- vorher blieb der Hook nach dem Mount im Modus "lesen" haengen,
                // wer oeffnete hielt das Lock (blockierte also Kollegen), durfte
                // selbst aber nichts bearbeiten. Nur eine Fremdsperre oder ein
                // Fehler (siehe die beiden Zweige oben) sollen "lesen" ergeben.
                setModus('bearbeiten');
                startHeartbeat(url, gen);
            } catch (err) {
                if (!mountedRef.current || gen !== generationRef.current) return;
                if (err instanceof DOMException && err.name === 'AbortError') return;
                setStatus('error');
                heldRef.current = false;
                setModus('lesen'); // defensiv, siehe Kommentar im 409-Zweig oben
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
        // ein frischer Versuch sinnvoll, siehe Klassenkommentar. acquire()
        // selbst schaltet bei Erfolg bereits auf "bearbeiten" um (Task 7b) --
        // bei einem erneuten Fehlschlag (409/500) bleibt es bei "lesen", auch
        // dafuer sorgt acquire() bereits selbst.
        void acquire(lockUrl);
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
