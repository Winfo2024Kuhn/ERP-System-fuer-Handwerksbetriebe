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

// Testkomponente fuer die "Zusammenspiel"-Tests weiter unten: verdrahtet den
// Hook 1:1 mit der ECHTEN BearbeitenLeiste, so wie es eine Seite spaeter tun
// wird -- nur so laesst sich pruefen, ob kannBearbeiten am Knopf ankommt.
function LeisteMitHook({ typ, id }: { typ: DatensatzLockTyp; id: number | null }) {
    const lock = useDatensatzLock(typ, id);
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
        vi.restoreAllMocks();
        // Sicherheitsnetz: wenn ein Test mit vi.useFakeTimers() vorzeitig
        // (z.B. durch eine fehlschlagende Assertion) abbricht, bevor er
        // vi.useRealTimers() erreicht, wuerden sonst ALLE nachfolgenden
        // Tests unbemerkt unter Fake-Timern laufen und mit einem
        // undurchsichtigen "Test timed out" statt einem echten Assertion-
        // Fehler scheitern.
        vi.useRealTimers();
    });

    describe('Mount / Acquire', () => {
        it('ruft beim Mount die acquire-Route mit Typ und ID auf', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    '/api/datensatz-locks/AUSGANG/42/acquire',
                    expect.objectContaining({ method: 'POST' })
                )
            );
        });

        it('nutzt den Typ EINGANG korrekt in der URL', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useDatensatzLock('EINGANG', 5));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    '/api/datensatz-locks/EINGANG/5/acquire',
                    expect.anything()
                )
            );
        });

        it('startet im Modus "lesen", auch nachdem das Lock erfolgreich erworben wurde', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            expect(result.current.modus).toBe('lesen');
        });

        it('ruft ohne ID (idle) keinen acquire-Request auf und erlaubt sofort Bearbeiten', () => {
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', null));

            expect(fetchMock).not.toHaveBeenCalled();
            expect(result.current.status).toBe('idle');
            expect(result.current.kannBearbeiten).toBe(true);
        });
    });

    describe('Modus-Umschalter', () => {
        it('onBearbeiten schaltet nach erfolgreichem Erwerb in den Modus "bearbeiten"', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            act(() => result.current.onBearbeiten());

            expect(result.current.modus).toBe('bearbeiten');
        });

        it('onFertig schaltet aus dem Modus "bearbeiten" zurueck auf "lesen" (und gibt dabei das Lock frei)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // DELETE durch onFertig
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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

        it('halterName/seit sind undefined, solange kein anderer den Datensatz haelt', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            expect(result.current.halterName).toBeUndefined();
            expect(result.current.seit).toBeUndefined();
        });
    });

    describe('Heartbeat', () => {
        it('sendet nach 30 Sekunden einen Heartbeat-Request', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(lockResponse());
            renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(result.current.verbindungWeg).toBe(false);

            vi.useRealTimers();
        });

        it('zwei aufeinanderfolgende fehlgeschlagene Heartbeats setzen verbindungWeg auf true', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });

            expect(result.current.verbindungWeg).toBe(true);

            vi.useRealTimers();
        });

        it('ein erfolgreicher Heartbeat setzt den Fehlschlag-Zaehler zurueck', async () => {
            vi.useFakeTimers();
            fetchMock.mockResolvedValueOnce(lockResponse()); // acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // heartbeat 1: fail
            fetchMock.mockResolvedValueOnce(lockResponse()); // heartbeat 2: success
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 })); // heartbeat 3: fail
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await act(async () => {
                await vi.advanceTimersByTimeAsync(30_000);
            });

            expect(result.current.status).toBe('locked-by-other');
            expect(result.current.kannBearbeiten).toBe(true);
            expect(result.current.halterName).toBe('Anna Beispiel');

            const anzahlNachErstemAusfall = fetchMock.mock.calls.length;

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });

            expect(fetchMock.mock.calls.length).toBe(anzahlNachErstemAusfall);

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

            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result, unmount } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));

            act(() => {
                window.dispatchEvent(new Event('pagehide'));
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE', keepalive: true })
            );
        });

        it('sendet beim Unmount KEIN DELETE, wenn das Lock nie gehalten wurde (durch anderen gesperrt)', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse({ status: 'LOCKED_BY_OTHER' }));
            const { result, unmount } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('locked-by-other'));

            fetchMock.mockClear();
            unmount();

            expect(fetchMock).not.toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE' })
            );
        });

        it('freigeben() sendet aktiv ein DELETE und schaltet den Modus zurueck auf "lesen"', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('acquired'));
            act(() => result.current.onBearbeiten());
            expect(result.current.modus).toBe('bearbeiten');

            await act(async () => {
                await result.current.freigeben();
            });

            expect(fetchMock).toHaveBeenCalledWith(
                '/api/datensatz-locks/AUSGANG/42',
                expect.objectContaining({ method: 'DELETE' })
            );
            expect(result.current.modus).toBe('lesen');
        });
    });

    // status exportieren: die Seite braucht den rohen Zustand z.B. fuer eine
    // eigene Fehleranzeige bei 'error' (siehe Kontext-Log, Nachbesserung 2).
    describe('status', () => {
        it('wird nach einem Acquire-Fehler (500) zu "error"', async () => {
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

            await waitFor(() => expect(result.current.status).toBe('error'));
        });

        it('wird nach freigeben() zu "idle"', async () => {
            fetchMock.mockResolvedValueOnce(lockResponse());
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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
            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));

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

            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));
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

            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));
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

            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));
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

            const { result } = renderHook(() => useDatensatzLock('AUSGANG', 42));
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

    // Nachbesserung 2: kannBearbeiten war so definiert, dass es genau in den
    // Zustaenden false wurde, in denen der Retry aus Nachbesserung 1 gebraucht
    // wird (nach "Fertig" und bei Fremdsperre) -- die ECHTE BearbeitenLeiste
    // deaktiviert ihren Knopf ueber genau diese Prop, war also nie klickbar.
    // Diese Tests rendern Hook und echte BearbeitenLeiste zusammen, um genau
    // das abzudecken (ein reiner Hook-Test haette den Fehler nicht gefunden).
    describe('Zusammenspiel mit der echten BearbeitenLeiste', () => {
        it('nach onFertig() ist der Bearbeiten-Knopf wieder aktiv; ein Klick acquiriert erneut, erst bei 200 erscheint "Fertig"', async () => {
            const user = userEvent.setup();
            fetchMock.mockResolvedValueOnce(lockResponse()); // Mount-Acquire
            fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // DELETE durch Fertig-Klick
            fetchMock.mockResolvedValueOnce(lockResponse()); // erneutes Acquire durch Bearbeiten-Klick

            render(<LeisteMitHook typ="AUSGANG" id={42} />);

            const bearbeiten1 = await screen.findByRole('button', { name: 'Bearbeiten' });
            await waitFor(() => expect(bearbeiten1).toBeEnabled());
            await user.click(bearbeiten1);

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
