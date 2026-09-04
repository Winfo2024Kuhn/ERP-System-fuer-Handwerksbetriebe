import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Reine Untaetigkeits-Erkennung fuer den Dokument-Editor.
 *
 * Der Hook kennt weder ein Lock noch Netzwerkaufrufe noch UI - er zaehlt
 * ausschliesslich Aktivitaet im Tab (Maus, Tastatur, Scroll, Tab-Wechsel)
 * und meldet zwei Ereignisse per Callback:
 *   - onWarn: `warnMs` vor Ablauf, genau einmal je Untaetigkeitsphase.
 *   - onIdle: nach `timeoutMs` ohne Aktivitaet, genau einmal je Untaetigkeitsphase.
 * Jede Aktivitaet setzt den Countdown zurueck und startet eine neue Phase.
 * Was bei onWarn/onIdle tatsaechlich passiert (Banner zeigen, Lock
 * freigeben, speichern, ...), entscheidet ausschliesslich der Aufrufer
 * (z.B. useDatensatzLock) - dieser Baustein bleibt dafuer isoliert testbar.
 */

export interface UseIdleTimerOptions {
    /** Zeit bis zur Untaetigkeit in Millisekunden. Standard: 5 Minuten. */
    timeoutMs?: number;
    /** Vorwarnzeit vor Ablauf in Millisekunden. Standard: 60 Sekunden. */
    warnMs?: number;
    /** Feuert genau einmal je Untaetigkeitsphase, `warnMs` vor Ablauf. */
    onWarn?: () => void;
    /** Feuert genau einmal je Untaetigkeitsphase, nach `timeoutMs`. */
    onIdle?: () => void;
    /** false meldet alle Listener ab und raeumt alle Timer weg. Standard: true. */
    enabled?: boolean;
}

export interface UseIdleTimerResult {
    /** Sekunden bis zur Untaetigkeit - nur waehrend der Vorwarnung gesetzt, sonst null. */
    verbleibendeSekunden: number | null;
    /** Setzt den Countdown manuell zurueck, so als waere gerade Aktivitaet passiert. */
    zuruecksetzen: () => void;
}

/** Standard-Timeout: 5 Minuten Untaetigkeit bis zur automatischen Freigabe. */
export const IDLE_TIMER_TIMEOUT_MS_STANDARD = 300_000;
/** Standard-Vorwarnzeit: 60 Sekunden vor Ablauf. */
export const IDLE_TIMER_WARN_MS_STANDARD = 60_000;

// Aktionen im Tab, die als "Anwesenheit" zaehlen - nicht nur echte
// Dokumentaenderungen. Reihenfolge wie in der Spec.
const FENSTER_EREIGNISSE = ['pointermove', 'keydown', 'scroll', 'click', 'wheel', 'touchstart'] as const;
const DOKUMENT_EREIGNISSE = ['visibilitychange'] as const;

// Der Reset wird auf maximal 1x/Sekunde gedrosselt - sonst loest jede
// Mausbewegung einen eigenen Re-Render aus.
const RESET_DROSSEL_MS = 1_000;

export function useIdleTimer({
    timeoutMs = IDLE_TIMER_TIMEOUT_MS_STANDARD,
    warnMs = IDLE_TIMER_WARN_MS_STANDARD,
    onWarn,
    onIdle,
    enabled = true,
}: UseIdleTimerOptions): UseIdleTimerResult {
    // Interner Rohwert. Waehrend `enabled === false` wird er bewusst NICHT per
    // setState auf null gezwungen (das waere ein synchroner setState-Aufruf
    // direkt im Effekt-Koerper, react-hooks/set-state-in-effect) - stattdessen
    // maskiert der Rueckgabewert unten den Zustand fuer diesen Fall weg.
    const [countdownState, setCountdownState] = useState<number | null>(null);

    // Callbacks in Refs spiegeln: eine neue Funktionsreferenz vom Aufrufer bei
    // jedem Render soll Listener/Timer nicht neu aufbauen. Das Schreiben passiert
    // bewusst in einem Effekt OHNE Dep-Array (laeuft nach jedem Commit) statt
    // direkt im Funktionskoerper - ein Ref waehrend des Renders zu beschreiben
    // ist unzulaessig (react-hooks/refs), auch wenn es hier faktisch sicher waere.
    const onWarnRef = useRef(onWarn);
    const onIdleRef = useRef(onIdle);
    useEffect(() => {
        onWarnRef.current = onWarn;
        onIdleRef.current = onIdle;
    });

    const warnTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const idleTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const countdownIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const letzterResetRef = useRef(0);

    const alleTimerRaeumen = useCallback(() => {
        if (warnTimeoutRef.current != null) {
            clearTimeout(warnTimeoutRef.current);
            warnTimeoutRef.current = null;
        }
        if (idleTimeoutRef.current != null) {
            clearTimeout(idleTimeoutRef.current);
            idleTimeoutRef.current = null;
        }
        if (countdownIntervalRef.current != null) {
            clearInterval(countdownIntervalRef.current);
            countdownIntervalRef.current = null;
        }
    }, []);

    // Plant Warn- und Idle-Timeout neu, OHNE selbst synchron State zu setzen.
    // Das Zuruecksetzen von countdownState auf null passiert ausschliesslich in
    // jetztZuruecksetzen() - so bleibt der einzige synchron-im-Effekt-erreichbare
    // Aufruf beim Mount ein reiner Timer-Aufbau ohne setState
    // (react-hooks/set-state-in-effect). Die setState-Aufrufe in den
    // Timeout-/Interval-Callbacks unten sind unkritisch: die feuern asynchron,
    // nicht synchron waehrend der Effekt-Ausfuehrung.
    const timerPlanen = useCallback(() => {
        alleTimerRaeumen();

        const warnNachMs = Math.max(timeoutMs - warnMs, 0);
        const vorwarnDauerSekunden = Math.max(Math.round((timeoutMs - warnNachMs) / 1000), 0);

        warnTimeoutRef.current = setTimeout(() => {
            warnTimeoutRef.current = null;
            setCountdownState(vorwarnDauerSekunden);
            countdownIntervalRef.current = setInterval(() => {
                setCountdownState(vorher => (vorher != null && vorher > 0 ? vorher - 1 : 0));
            }, 1_000);
            onWarnRef.current?.();
        }, warnNachMs);

        idleTimeoutRef.current = setTimeout(() => {
            idleTimeoutRef.current = null;
            if (countdownIntervalRef.current != null) {
                clearInterval(countdownIntervalRef.current);
                countdownIntervalRef.current = null;
            }
            setCountdownState(null);
            onIdleRef.current?.();
        }, timeoutMs);
    }, [timeoutMs, warnMs, alleTimerRaeumen]);

    // Aktivitaet (oder ein manueller Aufruf): plant die Timer neu UND setzt den
    // Countdown zurueck. Wird bewusst nur aus dem (asynchron feuernden)
    // Aktivitaets-Listener und als oeffentliche zuruecksetzen()-Funktion
    // aufgerufen - NICHT direkt aus dem Effekt-Koerper beim Mount, sonst waere
    // der setState-Aufruf hier synchron im Effekt erreichbar.
    const jetztZuruecksetzen = useCallback(() => {
        letzterResetRef.current = Date.now();
        timerPlanen();
        setCountdownState(null);
    }, [timerPlanen]);

    useEffect(() => {
        if (!enabled) {
            // Kein setState hier - der Rueckgabewert unten maskiert
            // countdownState bereits auf null, solange enabled false ist.
            alleTimerRaeumen();
            return;
        }

        // Direkter Aufbau ohne jetztZuruecksetzen() - siehe Kommentar dort.
        letzterResetRef.current = Date.now();
        timerPlanen();

        const throttledReset = () => {
            const jetzt = Date.now();
            if (jetzt - letzterResetRef.current < RESET_DROSSEL_MS) return;
            jetztZuruecksetzen();
        };

        FENSTER_EREIGNISSE.forEach(name => {
            window.addEventListener(name, throttledReset, { passive: true });
        });
        DOKUMENT_EREIGNISSE.forEach(name => {
            document.addEventListener(name, throttledReset, { passive: true });
        });

        return () => {
            FENSTER_EREIGNISSE.forEach(name => {
                window.removeEventListener(name, throttledReset);
            });
            DOKUMENT_EREIGNISSE.forEach(name => {
                document.removeEventListener(name, throttledReset);
            });
            alleTimerRaeumen();
        };
    }, [enabled, timerPlanen, jetztZuruecksetzen, alleTimerRaeumen]);

    return {
        verbleibendeSekunden: enabled ? countdownState : null,
        zuruecksetzen: jetztZuruecksetzen,
    };
}
