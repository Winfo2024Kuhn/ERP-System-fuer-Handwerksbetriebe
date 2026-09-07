import { useEffect } from 'react';
import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useDatensatzLock, type DatensatzLockTyp } from './useDatensatzLock';
import { BearbeitenLeiste } from './BearbeitenLeiste';

// Dummy-Response-Fabrik fuer die DatensatzLockDto-Antworten des Backends
// (siehe DatensatzLockController: status/holderUserId/holderDisplayName/
// acquiredAt/lastHeartbeatAt).
function lockResponse(
    overrides: Partial<{
        status: 'ACQUIRED' | 'LOCKED_BY_OTHER';
        holderUserId: number;
        holderDisplayName: string;
        acquiredAt: string;
        lastHeartbeatAt: string;
    }> = {}
) {
    const body = {
        status: 'ACQUIRED' as const,
        holderUserId: 7,
        holderDisplayName: 'Erika Musterfrau',
        acquiredAt: '2026-09-04T10:00:00.000Z',
        lastHeartbeatAt: '2026-09-04T10:00:00.000Z',
        ...overrides,
    };
    return new Response(JSON.stringify(body), {
        status: body.status === 'ACQUIRED' ? 200 : 409,
    });
}

/**
 * Task 8a (Kontext-Log-Vorgabe): die Invariante "modus='bearbeiten' steht nie
 * bei status!=='acquired'" wurde bisher nur manuell per pruefeInvariante() an
 * einzelnen Stellen im Kettentest weiter unten geprueft. Dieser Wrapper
 * zeichnet den Zustand nach JEDEM Commit ALLER Hook-Tests auf (nicht nur der
 * Kette), damit afterEach() die Invariante global durchsetzt -- auch an
 * Stellen, an denen bisher niemand explizit danach gefragt hat. useEffect
 * (statt direkt beim Render) trifft nur tatsaechlich COMMITTETE Zustaende,
 * genau das, was ein Nutzer je zu sehen bekaeme.
 */
const beobachteteZustaende: Array<{ modus: string; status: string }> = [];

function useUeberwachterDatensatzLock(typ: DatensatzLockTyp, id: number | null) {
    const ergebnis = useDatensatzLock(typ, id);
    useEffect(() => {
        beobachteteZustaende.push({ modus: ergebnis.modus, status: ergebnis.status });
    });
    return ergebnis;
}

// Testkomponente fuer die "Zusammenspiel"-Tests weiter unten: verdrahtet den
// Hook 1:1 mit der ECHTEN BearbeitenLeiste, so wie es eine Seite spaeter tun
// wird -- nur so laesst sich pruefen, ob kannBearbeiten am Knopf ankommt.
function LeisteMitHook({ typ, id }: { typ: DatensatzLockTyp; id: number | null }) {
    const lock = useUeberwachterDatensatzLock(typ, id);
    return (
        <BearbeitenLeiste
            modus={lock.modus}
            kannBearbeiten={lock.kannBearbeiten}
            verbleibendeSekunden={null}
            verbindungWeg={lock.verbindungWeg}
            onBearbeiten={lock.onBearbeiten}
            onFertig={lock.onFertig}
        />
    );
}

describe('useDatensatzLock', () => {
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        // Task 8a: die Invariante "modus='bearbeiten' nie bei
        // status!=='acquired'" jetzt global ueber JEDEN aufgezeichneten
        // Commit ALLER Hook-Tests durchsetzen (siehe useUeberwachterDatensatzLock
        // oben), nicht nur an den vom Kettentest manuell geprueften Stellen.
        //
        // Abschnitt 8-2: Pruefung und Aufraeumen sind getrennt. Schlaegt die
        // Invariante an, MUSS das Aufraeumen trotzdem laufen -- sonst laufen
        // alle folgenden Tests unter geleakten Fake-Timern und Mocks, und aus
        // einem echten Fehler werden zweiundzwanzig (so beim Code-Review 7-2/8-1
        // gemessen: 1 kaputt, 21 Folgeschaeden als "Test timed out").
        try {
            for (const zustand of beobachteteZustaende) {
                if (zustand.status !== 'acquired') {
                    expect(zustand.modus).not.toBe('bearbeiten');
                }
            }
        } finally {
            beobachteteZustaende.length = 0;
            vi.restoreAllMocks();
            // Sicherheitsnetz: wenn ein Test mit vi.useFakeTimers() vorzeitig
            // (z.B. durch eine fehlschlagende Assertion) abbricht, bevor er
            // vi.useRealTimers() erreicht, wuerden sonst ALLE nachfolgenden
            // Tests unbemerkt unter Fake-Timern laufen und mit einem
            // undurchsichtigen "Test timed out" statt einem echten Assertion-
            // Fehler scheitern.
            vi.useRealTimers();
        }
    });

    describe('Mount / Acquire', () => {
        it('ruft beim Mount die acquire-Route mit Typ und ID auf', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    '/api/datensatz-locks/AUSGANG/42/acquire',
                    expect.objectContaining({ method: 'POST' })
                )
            );
        });

        it('nutzt den Typ EINGANG korrekt in der URL', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useUeberwachterDatensatzLock('EINGANG', 5));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    '/api/datensatz-locks/EINGANG/5/acquire',
                    expect.anything()
                )
            );
        });

        it('wechselt nach einem erfolgreichen Mount-Acquire automatisch in den Modus "bearbeiten" (Task 7b)', async () => {
            // Vorher blieb der Hook hier im Modus "lesen" haengen: wer die Seite
            // oeffnete, hielt das Lock (blockierte also Kollegen), durfte selbst
            // aber nichts bearbeiten, ohne vorher noch einmal auf "Bearbeiten" zu
            // klicken -- das entspricht nicht dem tatsaechlichen Editor-Verhalten
            // und war reine Blockade ohne Nutzen. Nur eine Fremdsperre (409) soll
            // weiterhin Nur-Lesen ergeben, siehe Test direkt unten.
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            expect(result.current.modus).toBe('bearbeiten');
        });

        it('ruft ohne ID (idle) keinen acquire-Request auf und erlaubt sofort Bearbeiten', () => {
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', null));

            expect(fetchMock).not.toHaveBeenCalled();
            expect(result.current.status).toBe('idle');
            expect(result.current.kannBearbeiten).toBe(true);
        });
    });

    describe('Modus-Umschalter', () => {
        it('onBearbeiten schaltet nach erfolgreichem Erwerb in den Modus "bearbeiten"', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            act(() => result.current.onBearbeiten());

            expect(result.current.modus).toBe('bearbeiten');
        });

        it('onFertig schaltet aus dem Modus "bearbeiten" zurueck auf "lesen" (und gibt dabei das Lock frei)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // DELETE durch onFertig
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));
            act(() => result.current.onBearbeiten());
            expect(result.current.modus).toBe('bearbeiten');

            act(() => result.current.onFertig());

            // onFertig() gibt das Lock aktiv per DELETE frei (siehe eigener
            // Test dazu weiter unten) -- der Modus-Wechsel passiert darum
            // asynchron, erst nachdem dieser Request durchgelaufen ist.
            await waitFor(() => expect(result.current.modus).toBe('lesen'));
        });
    });

    describe('Gesperrt durch anderen (409)', () => {
        it('setzt status auf "locked-by-other" und liefert halterName/seit -- kannBearbeiten bleibt true, damit die Uebernahme moeglich ist', async () => {
            const jetzt = new Date('2026-09-04T10:12:00.000Z');
            vi.useFakeTimers();
            vi.setSystemTime(jetzt);

            fetchMock.mockResolvedValueOnce(
                lockResponse({
                    status: 'LOCKED_BY_OTHER',
                    holderDisplayName: 'Thomas Beispiel',
                    acquiredAt: '2026-09-04T10:07:00.000Z',
                })
            );
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.runOnlyPendingTimersAsync();
            });

            expect(result.current.status).toBe('locked-by-other');
            expect(result.current.kannBearbeiten).toBe(true);
            expect(result.current.halterName).toBe('Thomas Beispiel');
            expect(result.current.seit).toBe('5');

            vi.useRealTimers();
        });

        it('onBearbeiten() versucht bei einer Fremdsperre ein neues Acquire, bleibt aber im Modus "lesen", solange weiter gesperrt ist', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER' })); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER' })); // erneuter Versuch durch onBearbeiten: weiterhin gesperrt
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('locked-by-other'));
            expect(result.current.kannBearbeiten).toBe(true);

            act(() => result.current.onBearbeiten());

            await waitFor(() =>
                expect(
                    fetchMock.mock.calls.filter(
                        call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
                    )
                ).toHaveLength(2)
            );
            expect(result.current.modus).toBe('lesen');
        });

        it('bleibt bei einer Fremdsperre beim Mount im Modus "lesen" (Task 7b: nur der Erfolgsfall wechselt automatisch)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER' }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('locked-by-other'));

            expect(result.current.modus).toBe('lesen');
        });

        it('halterName/seit sind undefined, solange kein anderer den Datensatz haelt', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            expect(result.current.halterName).toBeUndefined();
            expect(result.current.seit).toBeUndefined();
        });

        it('ein durch freigeben() ueberholtes 409-Ergebnis beim Mount-Acquire schreibt nach dem verzoegerten json() keinen Zustand mehr (Task 7b)', async () => {
            // Befund aus der Review: die Generationspruefung nach dem ERSTEN await
            // (fetch) reicht nicht -- res.json() ist selbst ein zweiter await, und
            // GENAU in diesem Fenster kann freigeben() den Versuch ueberholen. Ohne
            // eine zweite Pruefung NACH json() wuerde der laengst ungueltige
            // 409-Befund trotzdem noch halterName/status auf einen falschen Halter
            // schreiben.
            let jsonAufloesen: (value: unknown) => void = () => {};
            const langsame409Antwort = {
                status: 409,
                json: () => new Promise(resolve => { jsonAufloesen = resolve; }),
            } as unknown as Response;
            fetchMock.mockResolvedValueOnce(langsame409Antwort);

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

            // json() haengt noch -- jetzt ueberholt freigeben() den Versuch.
            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.status).toBe('idle');

            // Erst jetzt loest die (laengst ueberholte) 409-Antwort auf.
            act(() => {
                jsonAufloesen({
                    status: 'LOCKED_BY_OTHER',
                    holderDisplayName: 'Ueberholt Beispiel',
                    acquiredAt: '2026-09-04T10:00:00.000Z',
                });
            });
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(result.current.status).toBe('idle');
            expect(result.current.halterName).toBeUndefined();
        });
    });

    describe('Heartbeat', () => {
        it('sendet nach 30 Sekunden einen Heartbeat-Request', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42/heartbeat',
                expect.objectContaining({ method: 'POST' })
            );

            vi.useRealTimers();
        });

        it('ein einzelner fehlgeschlagener Heartbeat setzt verbindungWeg NICHT (nur ein Netz-Hiccup)', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(result.current.verbindungWeg).toBe(false);

            vi.useRealTimers();
        });

        it('zwei aufeinanderfolgende fehlgeschlagene Heartbeats setzen verbindungWeg auf true -- das Lock selbst (heldRef/status/modus) bleibt unangetastet', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });

            expect(result.current.verbindungWeg).toBe(true);
            // Nachbesserung 1 (Task 7b), systematische Pruefung: ein einfacher
            // Netzfehler (ohne 409) setzt heldRef NICHT auf false -- das Lock
            // gilt weiter als gehalten, bis entweder ein 409 kommt oder aktiv
            // freigegeben wird. modus/status duerfen sich hier also NICHT
            // aendern, auch nach mehreren Fehlschlägen in Folge.
            expect(result.current.status).toBe('acquired');
            expect(result.current.modus).toBe('bearbeiten');

            vi.useRealTimers();
        });

        it('ein erfolgreicher Heartbeat setzt den Fehlschlag-Zaehler zurueck', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse()); // acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // heartbeat 1: fail
            fetchMock.mockResolvedValueOnce(lockResponse()); // heartbeat 2: success
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // heartbeat 3: fail
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(90_000);
            });

            expect(result.current.verbindungWeg).toBe(false);

            vi.useRealTimers();
        });

        it('ein Heartbeat mit 409 wechselt in "locked-by-other" (kannBearbeiten bleibt true) und stoppt weitere Heartbeats', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER', holderDisplayName: 'Anna Beispiel' }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(result.current.status).toBe('locked-by-other');
            expect(result.current.kannBearbeiten).toBe(true);
            expect(result.current.halterName).toBe('Anna Beispiel');
            // Nachbesserung 1 (Task 7b): vor diesem Test wurde hier NUR status/
            // kannBearbeiten/halterName geprueft, nicht modus -- durch genau
            // diese Luecke rutschte der Befund "Bearbeiten-Modus ohne
            // gehaltenes Lock" durch (modus blieb faelschlich "bearbeiten",
            // obwohl der Heartbeat das Lock gerade an einen anderen verloren
            // hat). Das Lieferant-Modal haette bei dieser Kombination alle
            // Felder aktiv gelassen, waehrend der GesperrtHinweis einen
            // anderen Halter meldet.
            expect(result.current.modus).toBe('lesen');

            const anzahlNachErstemAusfall = fetchMock.mock.calls.length;

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });

            expect(fetchMock.mock.calls.length).toBe(anzahlNachErstemAusfall);

            vi.useRealTimers();
        });

        it('ein durch freigeben() ueberholtes 409-Ergebnis beim Heartbeat schreibt nach dem verzoegerten json() keinen Zustand mehr (Task 7b)', async () => {
            vi.useFakeTimers();
            let jsonAufloesen: (value: unknown) => void = () => {};
            const langsame409Antwort = {
                status: 409,
                json: () => new Promise(resolve => { jsonAufloesen = resolve; }),
            } as unknown as Response;
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire: Erfolg
            fetchMock.mockResolvedValueOnce(langsame409Antwort); // Heartbeat: 409, json() haengt
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // freigeben(): DELETE

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('acquired');

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });
            // Heartbeat-Request ist raus (409), aber json() haengt noch --
            // jetzt ueberholt freigeben() den Versuch.
            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.status).toBe('idle');

            // Erst jetzt loest die (laengst ueberholte) 409-Antwort auf.
            act(() => {
                jsonAufloesen({
                    status: 'LOCKED_BY_OTHER',
                    holderDisplayName: 'Ueberholt Beispiel',
                    acquiredAt: '2026-09-04T10:00:00.000Z',
                });
            });
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(result.current.status).toBe('idle');
            expect(result.current.halterName).toBeUndefined();

            vi.useRealTimers();
        });

        it('verbindungWeg wird beim naechsten erfolgreichen Acquire wieder auf false gesetzt, auch wenn es vom vorherigen Zyklus noch true war', async () => {
            // Regressionstest fuer den Befund aus Nachbesserung 2: startHeartbeat
            // setzte bisher nur den Fehlschlag-Zaehler zurueck, nicht das
            // sichtbare verbindungWeg-Flag -- eine "Verbindung weg"-Warnung aus
            // einem VORHERIGEN Zyklus (vor freigeben()) blieb dadurch nach einem
            // frischen, erfolgreichen Acquire faelschlich stehen.
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // Heartbeat 1: fail
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // Heartbeat 2: fail -> verbindungWeg true
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // freigeben(): DELETE
            fetchMock.mockResolvedValueOnce(lockResponse()); // erneutes Acquire durch onBearbeiten

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('acquired');

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });
            expect(result.current.verbindungWeg).toBe(true);

            await act(async () => {
                await result.current.freigeben();
            });

            await act(async () => {
                result.current.onBearbeiten();
                await Promise.resolve();
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(result.current.status).toBe('acquired');
            expect(result.current.verbindungWeg).toBe(false);

            vi.useRealTimers();
        });
    });

    describe('Freigabe', () => {
        it('gibt das Lock beim Unmount per DELETE mit keepalive frei, wenn es gehalten wurde', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result, unmount } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            unmount();

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE', keepalive: true })
            );
        });

        it('gibt das Lock beim "pagehide"-Event per DELETE mit keepalive frei', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            act(() => {
                window.dispatchEvent(new Event('pagehide'));
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE', keepalive: true })
            );
        });

        it('zieht bei "pagehide" auch modus/status nach, nicht nur heldRef -- sonst bearbeitbar ohne Sperre nach einer bfcache-Rueckkehr (Task 8a)', async () => {
            // Befund aus dem Review: releaseKeepalive() setzte bisher NUR
            // heldRef=false. Auf dem Unmount-Cleanup-Pfad ist das folgenlos,
            // weil der Aufrufer (der Cleanup des lockUrl-Effekts) direkt
            // danach ohnehin modus/status selbst zuruecksetzt -- der
            // Komponentenbaum verschwindet ja gleich ganz. Auf dem
            // "pagehide"-Pfad ist releaseKeepalive() aber die EINZIGE Stelle:
            // die Komponente bleibt (der Tab schliesst nicht, geht nur in den
            // bfcache) im DOM, mit modus/status unveraendert auf 'bearbeiten'/
            // 'acquired' stehen -- obwohl das Lock gerade per DELETE
            // freigegeben wurde. Kommt der Tab per bfcache zurueck (kein
            // Reload, kein Remount), zeigt das Formular sich weiterhin
            // bearbeitbar; ein Speichern liefe ins Leere, weil das Backend
            // den PUT ohne gueltige Sperre ablehnt.
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));
            expect(result.current.modus).toBe('bearbeiten');

            act(() => {
                window.dispatchEvent(new Event('pagehide'));
            });

            expect(result.current.modus).toBe('lesen');
            expect(result.current.status).toBe('idle');
            expect(result.current.kannBearbeiten).toBe(true);
        });

        it('nach "pagehide" UND bfcache-Rueckkehr ("pageshow") acquiriert ein Klick auf "Bearbeiten" das Lock frisch (Task 8a)', async () => {
            // Ergaenzt den Test oben um den tatsaechlichen Wiedereinstieg: der
            // Hook selbst hoert nicht auf "pageshow" (bfcache liefert exakt
            // denselben React-Baum zurueck, kein Remount noetig) -- sobald
            // "pagehide" modus/status korrekt auf 'lesen'/'idle' zuruecksetzt
            // (siehe Test oben), genuegt der ganz normale onBearbeiten()-Pfad
            // (derselbe wie nach "Fertig"), um ein frisches Acquire
            // auszuloesen. "pageshow" selbst loest dabei bewusst NICHTS aus --
            // automatisches Nachladen ohne Nutzerklick ist nicht Teil dieser
            // Spec (siehe Klassenkommentar zu onBearbeiten).
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // "pagehide": DELETE
            fetchMock.mockResolvedValueOnce(lockResponse()); // Retry-Acquire nach der Rueckkehr

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await waitFor(() => expect(result.current.status).toBe('acquired'));

            act(() => {
                window.dispatchEvent(new Event('pagehide'));
            });
            expect(result.current.modus).toBe('lesen');

            // bfcache-Rueckkehr: derselbe Tab, derselbe React-Baum, kein
            // Reload -- der Browser feuert "pageshow" mit persisted=true.
            act(() => {
                window.dispatchEvent(new Event('pageshow'));
            });

            act(() => result.current.onBearbeiten());

            await waitFor(() =>
                expect(
                    fetchMock.mock.calls.filter(
                        call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
                    )
                ).toHaveLength(2)
            );
            await waitFor(() => expect(result.current.modus).toBe('bearbeiten'));
            expect(result.current.status).toBe('acquired');
        });

        it('sendet beim Unmount KEIN DELETE, wenn das Lock nie gehalten wurde (durch anderen gesperrt)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER' }));
            const { result, unmount } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('locked-by-other'));

            fetchMock.mockClear();
            unmount();

            expect(fetchMock).not.toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE' })
            );
        });

        it('freigeben() sendet aktiv ein DELETE und schaltet den Modus zurueck auf "lesen" -- und bleibt dort ohne weiteren Klick', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));
            expect(result.current.modus).toBe('bearbeiten'); // seit Task 7b automatisch nach Erfolg

            await act(async () => {
                await result.current.freigeben();
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE' })
            );
            expect(result.current.modus).toBe('lesen');

            // Kein automatischer Retry -- ohne einen erneuten Klick auf
            // "Bearbeiten" bleibt es bei "lesen".
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.modus).toBe('lesen');
        });
    });

    // status exportieren: die Seite braucht den rohen Zustand z.B. fuer eine
    // eigene Fehleranzeige bei 'error' (siehe Kontext-Log, Nachbesserung 2).
    describe('status', () => {
        it('wird nach einem Acquire-Fehler (500) zu "error"', async () => {
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('error'));
        });

        it('wird nach freigeben() zu "idle"', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            await act(async () => {
                await result.current.freigeben();
            });

            expect(result.current.status).toBe('idle');
        });
    });

    // Nachbesserung: der Hook konnte ein Lock nach dem Mount nie wieder
    // holen (weder nach freigeben(), noch nach einem Acquire-Fehler, noch
    // nach einer 409-Fremdsperre). Die drei Faelle teilen dieselbe Ursache
    // (onBearbeiten schaltete nur die Anzeige um, ohne je erneut zu
    // acquire'n) und werden hier einzeln nachgestellt.
    describe('Erneutes Acquire nach Freigabe/Fehler/Fremdsperre', () => {
        it('waehrend ein Acquire noch laeuft, ist kannBearbeiten false und onBearbeiten() loest keinen zweiten Request aus', async () => {
            let acquireAufloesen: (value: Response) => void = () => {};
            fetchMock.mockReturnValueOnce(
                new Promise<Response>(resolve => {
                    acquireAufloesen = resolve;
                })
            );
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
            expect(result.current.status).toBe('loading');
            expect(result.current.kannBearbeiten).toBe(false);

            act(() => result.current.onBearbeiten());

            expect(fetchMock).toHaveBeenCalledTimes(1);
            expect(result.current.modus).toBe('lesen');

            // Aufraeumen, damit die haengende Promise den Test nicht ueberlebt.
            acquireAufloesen(lockResponse());
            await waitFor(() => expect(result.current.status).toBe('acquired'));
        });

        it('nach einem Acquire-Fehler (500) ist kannBearbeiten false und onBearbeiten() loest keinen zweiten Request aus', async () => {
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
            await waitFor(() => expect(result.current.status).toBe('error'));
            expect(result.current.kannBearbeiten).toBe(false);

            act(() => result.current.onBearbeiten());

            expect(fetchMock).toHaveBeenCalledTimes(1);
            expect(result.current.modus).toBe('lesen');
        });

        it('onBearbeiten() nach freigeben() sendet ein neues Acquire und wechselt erst bei Erfolg in den Bearbeiten-Modus', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // freigeben(): DELETE
            fetchMock.mockResolvedValueOnce(lockResponse()); // erneutes Acquire durch onBearbeiten: Erfolg

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await waitFor(() => expect(result.current.status).toBe('acquired'));

            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.modus).toBe('lesen');
            expect(result.current.status).toBe('idle');
            expect(result.current.kannBearbeiten).toBe(true);

            act(() => result.current.onBearbeiten());

            await waitFor(() => expect(result.current.modus).toBe('bearbeiten'));
            expect(
                fetchMock.mock.calls.filter(
                    call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
                )
            ).toHaveLength(2);
        });

        it('onBearbeiten() nach freigeben() bleibt im Modus "lesen", wenn der erneute Versuch mit 409 scheitert (neuer Halter)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // freigeben(): DELETE
            fetchMock.mockResolvedValueOnce(
                lockResponse({ status: 'LOCKED_BY_OTHER', holderDisplayName: 'Petra Beispiel' })
            ); // erneutes Acquire durch onBearbeiten: inzwischen haelt jemand anderes

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await waitFor(() => expect(result.current.status).toBe('acquired'));

            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.modus).toBe('lesen');

            act(() => result.current.onBearbeiten());

            await waitFor(() => expect(result.current.halterName).toBe('Petra Beispiel'));
            expect(result.current.modus).toBe('lesen');
        });

        it('nach erfolgreichem erneuten Acquire laeuft der Heartbeat wieder', async () => {
            // Fake-Timer laufen von Anfang an mit -- der Heartbeat-Interval,
            // den der erneute Acquire gleich (wieder) registriert, muss spaeter
            // mit vi.advanceTimersByTimeAsync() steuerbar sein. Zum Durchflushen
            // der reinen Promise-Ketten (Acquire/Freigeben/erneutes Acquire)
            // wird bewusst NICHT vi.runOnlyPendingTimersAsync() verwendet: das
            // faehrt bei einem erfolgreichen Acquire ungewollt auch gleich den
            // frisch registrierten Heartbeat-Interval mit hoch und verbraucht
            // dabei eine der fuer spaeter vorgesehenen Mock-Antworten. Reine
            // Mikrotask-Ticks (await Promise.resolve()) ruehren die Fake-Uhr
            // nicht an und laufen darum niemals einen Timer mit.
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // freigeben(): DELETE
            fetchMock.mockResolvedValueOnce(lockResponse()); // erneutes Acquire durch onBearbeiten

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('acquired');

            await act(async () => {
                await result.current.freigeben();
            });

            await act(async () => {
                result.current.onBearbeiten();
                await Promise.resolve();
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.modus).toBe('bearbeiten');

            const anzahlVorHeartbeat = fetchMock.mock.calls.length;

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42/heartbeat',
                expect.objectContaining({ method: 'POST' })
            );
            expect(fetchMock.mock.calls.length).toBeGreaterThan(anzahlVorHeartbeat);

            vi.useRealTimers();
        });

        it('onFertig() gibt das Lock aktiv frei (DELETE), nicht nur die Anzeige', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // DELETE durch onFertig

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await waitFor(() => expect(result.current.status).toBe('acquired'));
            act(() => result.current.onBearbeiten());
            expect(result.current.modus).toBe('bearbeiten');

            act(() => result.current.onFertig());

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    '/api/datensatz-locks/AUSGANG/42',
                    expect.objectContaining({ method: 'DELETE' })
                )
            );
            await waitFor(() => expect(result.current.modus).toBe('lesen'));
            expect(result.current.status).toBe('idle');
            expect(result.current.kannBearbeiten).toBe(true);
        });
    });

    // Nachbesserung 1 (Task 7b): der Code-Review fand die seit Review 5
    // verbotene Kombination "modus='bearbeiten' ohne gehaltenes Lock" ueber
    // den 409-Zweig von heartbeat() (siehe der korrigierte Test oben in
    // "Heartbeat"). Die Tests hier gehen die vom Reviewer genannten weiteren
    // Uebergaenge systematisch durch: einen zusammenhaengenden Ablauf ueber
    // mehrere Zustandswechsel hinweg, und den Sonderfall "freigeben() waehrend
    // ein Acquire noch laeuft".
    describe('Invariante: modus "bearbeiten" nur bei tatsaechlich gehaltenem Lock', () => {
        /**
         * Die vom Reviewer vorgeschlagene Invariante, hier als Helfer: sobald
         * status NICHT (mehr) 'acquired' ist, darf modus NICHT 'bearbeiten'
         * sein -- unabhaengig davon, WELCHER Uebergang gerade dazu gefuehrt
         * hat. Gilt bewusst nur fuer den Fall mit ID (lockUrl != null); ohne
         * ID gibt es kein Lock-Konzept, und modus darf dort frei umschalten
         * (siehe Klassenkommentar, "Ohne ID... nur die Anzeige umschalten").
         */
        function pruefeInvariante(ergebnis: { modus: string; status: string }) {
            if (ergebnis.status !== 'acquired') {
                expect(ergebnis.modus).not.toBe('bearbeiten');
            }
        }

        it('gilt ueber eine ganze Kette von Uebergaengen hinweg: Mount-Erfolg -> Heartbeat-409 -> Retry-Erfolg -> Fertig -> erneuter Acquire-Fehler', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse()); // 1: Mount-Acquire Erfolg
            fetchMock.mockResolvedValueOnce(
                lockResponse({ status: 'LOCKED_BY_OTHER', holderDisplayName: 'Petra Beispiel' })
            ); // 2: Heartbeat -> 409 (der eigentliche Befund)
            fetchMock.mockResolvedValueOnce(lockResponse()); // 3: Retry-Acquire durch onBearbeiten: Erfolg
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // 4: onFertig -> DELETE
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // 5: erneuter Retry-Acquire: Fehler

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('acquired');
            pruefeInvariante(result.current);

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });
            expect(result.current.status).toBe('locked-by-other');
            // Genau diese Zeile waere ohne den Fix in diesem Nachbesserungsauftrag
            // fehlgeschlagen: modus stand hier faelschlich noch auf "bearbeiten".
            pruefeInvariante(result.current);

            act(() => result.current.onBearbeiten());
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('acquired');
            pruefeInvariante(result.current);

            act(() => result.current.onFertig());
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('idle');
            pruefeInvariante(result.current);

            act(() => result.current.onBearbeiten());
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(result.current.status).toBe('error');
            pruefeInvariante(result.current);

            vi.useRealTimers();
        });

        it('freigeben() waehrend ein Retry-Acquire noch laeuft: ein spaeter eintreffender Erfolg (200) darf modus nicht mehr auf "bearbeiten" setzen', async () => {
            // Dritter vom Reviewer genannter Uebergang. Abgesichert durch die
            // bestehende Generationspruefung direkt nach dem fetch()-await in
            // acquire() (nicht neu in dieser Nachbesserung) -- hier erstmals
            // mit modus/status statt nur mit dem Request-Zaehler geprueft.
            let retryAufloesen: (value: Response) => void = () => {};
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire: Erfolg
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // 1. freigeben(): DELETE
            fetchMock.mockReturnValueOnce(
                new Promise<Response>(resolve => { retryAufloesen = resolve; })
            ); // Retry-Acquire durch onBearbeiten: haengt zunaechst

            const { result } = renderHook(() => useUeberwachterDatensatzLock('AUSGANG', 42));
            await waitFor(() => expect(result.current.status).toBe('acquired'));
            expect(result.current.modus).toBe('bearbeiten');

            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.modus).toBe('lesen');

            act(() => result.current.onBearbeiten());
            await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));

            // WAEHREND der Retry-Acquire noch haengt, wird erneut freigegeben
            // (z.B. Idle-Timeout oder ein zweiter Klick anderswo). heldRef ist
            // hier bereits false -- kein weiteres DELETE noetig.
            await act(async () => {
                await result.current.freigeben();
            });
            expect(result.current.modus).toBe('lesen');
            expect(result.current.status).toBe('idle');

            // Der laengst ueberholte Retry-Acquire trifft jetzt (verspaetet)
            // mit Erfolg (200) ein.
            act(() => {
                retryAufloesen(lockResponse());
            });
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(result.current.modus).toBe('lesen');
            expect(result.current.status).toBe('idle');
        });
    });

    // Nachbesserung 2: kannBearbeiten war so definiert, dass es genau in den
    // Zustaenden false wurde, in denen der Retry aus Nachbesserung 1 gebraucht
    // wird (nach "Fertig" und bei Fremdsperre) -- die ECHTE BearbeitenLeiste
    // deaktiviert ihren Knopf ueber genau diese Prop, war also nie klickbar.
    // Diese Tests rendern Hook und echte BearbeitenLeiste zusammen, um genau
    // das abzudecken (ein reiner Hook-Test haette den Fehler nicht gefunden).
    describe('Zusammenspiel mit der echten BearbeitenLeiste', () => {
        it('nach Mount steht direkt "Fertig"; Klick darauf gibt frei, macht "Bearbeiten" aktiv, und ein erneuter Klick acquiriert erst bei 200 wieder "Fertig"', async () => {
            // Seit Task 7b zeigt ein erfolgreiches Mount-Acquire direkt "Fertig"
            // -- kein Erst-Klick auf "Bearbeiten" mehr noetig (siehe
            // useDatensatzLock.ts). Vorher musste dieser Test noch selbst einmal
            // auf "Bearbeiten" klicken, bevor "Fertig" ueberhaupt erschien.
            const user = userEvent.setup();
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // DELETE durch Fertig-Klick
            fetchMock.mockResolvedValueOnce(lockResponse()); // erneutes Acquire durch Bearbeiten-Klick

            render(<LeisteMitHook typ="AUSGANG" id={42} />);

            const fertig = await screen.findByRole('button', { name: 'Fertig' });
            await user.click(fertig);

            const bearbeiten2 = await screen.findByRole('button', { name: 'Bearbeiten' });
            await waitFor(() => expect(bearbeiten2).toBeEnabled());

            await user.click(bearbeiten2);

            await waitFor(() =>
                expect(
                    fetchMock.mock.calls.filter(
                        call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
                    )
                ).toHaveLength(2)
            );
            expect(await screen.findByRole('button', { name: 'Fertig' })).toBeInTheDocument();
        });

        it('bei Fremdsperre ist der Bearbeiten-Knopf aktiv; ein Klick uebernimmt einen zwischenzeitlich freigewordenen Datensatz', async () => {
            // Echte Timer + waitFor statt Fake-Timer: userEvent.click() haengt
            // sich unter vi.useFakeTimers() zuverlaessig auf (auch mit
            // delay: null), vermutlich weil die jsdom/Pointer-Events-Kette
            // intern auf requestAnimationFrame wartet, das vi.useFakeTimers()
            // standardmaessig mitfaked. Dass ein erfolgreicher erneuter
            // Acquire den Heartbeat wieder in Gang setzt, ist bereits durch
            // "nach erfolgreichem erneuten Acquire laeuft der Heartbeat
            // wieder" und den verbindungWeg-Regressionstest oben abgedeckt --
            // beide durchlaufen exakt denselben acquire()/startHeartbeat()-
            // Code, nur ueber onBearbeiten() statt per Klick ausgeloest.
            const user = userEvent.setup();
            fetchMock.mockResolvedValueOnce(
                lockResponse({ status: 'LOCKED_BY_OTHER', holderDisplayName: 'Klaus Beispiel' })
            ); // Mount: fremd gesperrt
            fetchMock.mockResolvedValueOnce(lockResponse()); // Klick auf "Bearbeiten": Kollege hat inzwischen freigegeben

            render(<LeisteMitHook typ="AUSGANG" id={42} />);

            const bearbeitenKnopf = await screen.findByRole('button', { name: 'Bearbeiten' });
            await waitFor(() => expect(bearbeitenKnopf).toBeEnabled());

            await user.click(bearbeitenKnopf);

            expect(await screen.findByRole('button', { name: 'Fertig' })).toBeInTheDocument();
        });

        it('nach einem Acquire-Fehler (500) ist der Bearbeiten-Knopf deaktiviert; ein Klick loest keinen zweiten Request aus', async () => {
            const user = userEvent.setup();
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));

            render(<LeisteMitHook typ="AUSGANG" id={42} />);

            const bearbeitenKnopf = await screen.findByRole('button', { name: 'Bearbeiten' });
            await waitFor(() => expect(bearbeitenKnopf).toBeDisabled());

            await user.click(bearbeitenKnopf);

            expect(
                fetchMock.mock.calls.filter(
                    call => call[0] === '/api/datensatz-locks/AUSGANG/42/acquire'
                )
            ).toHaveLength(1);
        });
    });
});
