import { forwardRef, useEffect, useImperativeHandle } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, useSearchParams } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import DocumentEditorPage from './DocumentEditorPage';

/**
 * Deckt die Umstellung von DocumentEditorPage auf das neue,
 * verallgemeinerte Sperr-Fundament ab (useDatensatzLock/BearbeitenLeiste/
 * GesperrtHinweis statt useDocumentLock/DocumentLockedModal) -- Abschnitt 7a,
 * Issue #82. `DocumentEditor` (riesige Komponente mit Tiptap/dnd-kit) wird
 * hier bewusst gemockt (siehe ArtikelDetail.test.tsx fuer denselben Ansatz
 * mit TiptapEditor) -- diese Seite testet die Zustands-Verdrahtung
 * (Lock-Zustand -> readOnly/Banner/Leiste, Untaetigkeits-Reihenfolge), nicht
 * den Editor selbst (der ist in document-editor/index.test.tsx abgedeckt).
 * `useDatensatzLock` bleibt dagegen ECHT (nur `fetch` gefakt, Stil aus
 * useDatensatzLock.test.tsx/LieferantDokumentModal.test.tsx) -- die
 * Kern-Eigenschaft dieser Seite ist gerade das Zusammenspiel mit dem echten
 * Zustandsautomaten (kein Flackern beim Uebergang "neu -> gerade angelegt").
 *
 * DSGVO: ausschliesslich Dummy-Namen.
 */

const mockState = vi.hoisted(() => ({
    speichernSpy: vi.fn(async () => {}),
    mountCount: 0,
}));

vi.mock('../components/DocumentEditor', () => ({
    default: forwardRef(function MockDocumentEditor(props: Record<string, unknown>, ref: unknown) {
        useImperativeHandle(ref as never, () => ({
            speichernFuerFreigabe: mockState.speichernSpy,
        }));
        useEffect(() => {
            mockState.mountCount += 1;
        }, []);
        const [, setSearchParams] = useSearchParams();
        const onLockFreigeben = props.onLockFreigeben as (() => Promise<void>) | undefined;
        const onClose = props.onClose as () => void;
        return (
            <div
                data-testid="mock-editor"
                data-readonly={String(!!props.readOnly)}
                data-dokument-id={String(props.dokumentId ?? '')}
            >
                {/* Simuliert genau das, was document-editor/index.tsx nach dem
                    Anlegen eines neuen Dokuments tut: die neu vergebene Id per
                    Router in die URL schreiben (syncDocumentIdInUrl). */}
                <button
                    onClick={() => {
                        setSearchParams(
                            prev => {
                                const naechste = new URLSearchParams(prev);
                                naechste.set('dokumentId', '1');
                                return naechste;
                            },
                            { replace: true }
                        );
                    }}
                >
                    Simuliere Anlegen
                </button>
                {onLockFreigeben && (
                    <button onClick={() => void onLockFreigeben()}>LockFreigebenAufrufen</button>
                )}
                <button onClick={onClose}>SchliessenFallback</button>
            </div>
        );
    }),
}));

const DOKUMENT_ID = 1;
const HALTER_NAME = 'Anna Beispiel';

function lockResponse(
    overrides: Partial<{
        status: 'ACQUIRED' | 'LOCKED_BY_OTHER';
        holderDisplayName: string;
        acquiredAt: string;
    }> = {}
) {
    const body = {
        status: 'ACQUIRED' as const,
        holderUserId: 9,
        holderDisplayName: HALTER_NAME,
        acquiredAt: new Date().toISOString(),
        lastHeartbeatAt: new Date().toISOString(),
        ...overrides,
    };
    return new Response(JSON.stringify(body), { status: body.status === 'ACQUIRED' ? 200 : 409 });
}

interface FetchSzenario {
    /** aufrufNummer beginnt bei 1 -- unterscheidet Mount-Acquire von einem spaeteren Retry-Klick. */
    acquire?: (aufrufNummer: number) => Response | Promise<Response>;
    onDelete?: () => void;
}

function buildFetchMock(szenario: FetchSzenario = {}) {
    let acquireAufrufe = 0;
    return vi.fn((url: string, options?: RequestInit) => {
        const methode = options?.method ?? 'GET';
        if (typeof url === 'string' && url.includes('/api/datensatz-locks/') && url.endsWith('/acquire')) {
            acquireAufrufe += 1;
            return Promise.resolve(szenario.acquire ? szenario.acquire(acquireAufrufe) : lockResponse());
        }
        if (typeof url === 'string' && url.includes('/api/datensatz-locks/') && url.endsWith('/heartbeat')) {
            return Promise.resolve(lockResponse());
        }
        if (typeof url === 'string' && url.includes('/api/datensatz-locks/') && methode === 'DELETE') {
            szenario.onDelete?.();
            return Promise.resolve(new Response(null, { status: 204 }));
        }
        return Promise.resolve(new Response(null, { status: 404 }));
    });
}

function renderSeite(pfad: string) {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ToastProvider>
                <DocumentEditorPage />
            </ToastProvider>
        </MemoryRouter>
    );
}

describe('DocumentEditorPage', () => {
    beforeEach(() => {
        mockState.speichernSpy = vi.fn(async () => {});
        mockState.mountCount = 0;
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    describe('Neues, noch ungespeichertes Dokument (keine Id)', () => {
        it('zeigt den Editor sofort editierbar, ohne Lock-Anfrage und ohne Bearbeiten-Leiste', async () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite('/dokument-editor?dokumentTyp=ANGEBOT');

            const editor = await screen.findByTestId('mock-editor');
            expect(editor).toHaveAttribute('data-readonly', 'false');
            expect(editor).toHaveAttribute('data-dokument-id', '');
            expect(screen.queryByRole('button', { name: 'Fertig' })).not.toBeInTheDocument();
            expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
            expect(screen.queryByText('Dokument wird geöffnet ...')).not.toBeInTheDocument();

            // Kein Lock-Konzept ohne Id -- der Hook darf keinen Request schicken.
            const lockAufrufe = fetchMock.mock.calls.filter(call =>
                typeof call[0] === 'string' && call[0].includes('/api/datensatz-locks/')
            );
            expect(lockAufrufe).toHaveLength(0);
        });

        it(
            'flackert beim Anlegen (Id kommt ueber die URL) nicht auf readOnly und mountet den Editor nicht neu',
            async () => {
                // Verzoegertes Acquire: haelt die Seite absichtlich laenger im
                // Uebergangs-Fenster fest, in dem der Server noch nicht
                // geantwortet hat -- genau das Fenster, in dem ein an
                // lock.modus geklammertes readOnly frueher geflackert haette.
                let aufloesen!: (r: Response) => void;
                const wartendeAntwort = new Promise<Response>(resolve => {
                    aufloesen = resolve;
                });
                const fetchMock = buildFetchMock({ acquire: () => wartendeAntwort });
                global.fetch = fetchMock as unknown as typeof fetch;

                renderSeite('/dokument-editor?dokumentTyp=ANGEBOT');
                await screen.findByTestId('mock-editor');
                expect(mockState.mountCount).toBe(1);

                await act(async () => {
                    (await screen.findByRole('button', { name: 'Simuliere Anlegen' })).click();
                });

                // Mitten im Acquire-Roundtrip (Server hat noch nicht geantwortet):
                // weiterhin editierbar, weiterhin derselbe Editor, keine Vollbild-Ladeanzeige.
                expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'false');
                expect(screen.queryByText('Dokument wird geöffnet ...')).not.toBeInTheDocument();
                expect(mockState.mountCount).toBe(1);

                await act(async () => {
                    aufloesen(lockResponse());
                    await Promise.resolve();
                    await Promise.resolve();
                });

                // Danach haelt die Seite das Lock wirklich (kein Unterschied fuer den Nutzer sichtbar).
                await screen.findByRole('button', { name: 'Fertig' });
                expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'false');
                expect(mockState.mountCount).toBe(1);
            }
        );
    });

    describe('Bestehendes Dokument (mit Id)', () => {
        it('zeigt eine Vollbild-Ladeanzeige, solange das Lock noch nicht aufgeloest ist -- kein Editor im DOM', async () => {
            const wartendeAntwort = new Promise<Response>(() => {}); // niemals aufloesen
            const fetchMock = buildFetchMock({ acquire: () => wartendeAntwort });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);

            expect(await screen.findByText('Dokument wird geöffnet ...')).toBeInTheDocument();
            expect(screen.queryByTestId('mock-editor')).not.toBeInTheDocument();
        });

        it('oeffnet mit freiem Lock editierbar und zeigt die Bearbeiten-Leiste im Modus "Fertig"', async () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);

            await screen.findByRole('button', { name: 'Fertig' });
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'false');
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-dokument-id', String(DOKUMENT_ID));
            expect(screen.queryByText('Sie lesen nur mit.')).not.toBeInTheDocument();
        });

        it('"Fertig" gibt frei (readOnly, "Sie lesen nur mit."), "Bearbeiten" erwirbt danach neu', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Fertig' }));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/datensatz-locks/AUSGANG/${DOKUMENT_ID}`,
                    expect.objectContaining({ method: 'DELETE' })
                )
            );
            expect(await screen.findByText('Sie lesen nur mit.')).toBeInTheDocument();
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'true');
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeEnabled();

            await user.click(screen.getByRole('button', { name: 'Bearbeiten' }));

            await screen.findByRole('button', { name: 'Fertig' });
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'false');
        });

        it('zeigt bei fremder Sperre den Gesperrt-Hinweis mit Dummy-Namen, Editor readOnly', async () => {
            const fetchMock = buildFetchMock({ acquire: () => lockResponse({ status: 'LOCKED_BY_OTHER' }) });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);

            expect(await screen.findByText(new RegExp(HALTER_NAME))).toBeInTheDocument();
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'true');
            // Uebernahmeversuch bleibt moeglich (kannBearbeiten=true bei locked-by-other).
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeEnabled();
        });

        it('zeigt bei einem Acquire-Fehler Hinweis + Toast und einen deaktivierten Knopf mit Tooltip', async () => {
            const fetchMock = buildFetchMock({
                acquire: () => new Response(null, { status: 500 }),
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);

            const treffer = await screen.findAllByText('Sperre konnte nicht geholt werden — bitte neu laden.');
            // Einmal als Inline-Hinweis auf der Seite, einmal als Toast.
            expect(treffer.length).toBeGreaterThanOrEqual(2);
            expect(screen.getByTestId('mock-editor')).toHaveAttribute('data-readonly', 'true');
            const bearbeitenKnopf = screen.getByRole('button', { name: 'Bearbeiten' });
            expect(bearbeitenKnopf).toBeDisabled();
            expect(bearbeitenKnopf).toHaveAttribute(
                'title',
                'Sperre konnte nicht geholt werden — bitte neu laden.'
            );
        });
    });

    describe('X-Button-Schliessen (onLockFreigeben)', () => {
        it(
            'blendet die eigene Leiste aus, sobald der Editor darueber die Sperre freigibt -- ' +
                'sonst wuerde sie den TabSchliessenHinweis des Editors (ganze Flaeche, keine Aktion) ueberlagern',
            async () => {
                const user = userEvent.setup();
                const fetchMock = buildFetchMock();
                global.fetch = fetchMock as unknown as typeof fetch;

                renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);
                await screen.findByRole('button', { name: 'Fertig' });

                await user.click(screen.getByRole('button', { name: 'LockFreigebenAufrufen' }));

                await waitFor(() => {
                    expect(screen.queryByRole('button', { name: 'Fertig' })).not.toBeInTheDocument();
                    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
                    expect(screen.queryByText('Sie lesen nur mit.')).not.toBeInTheDocument();
                });
                // Der Editor-Bereich selbst bleibt (der echte Editor entscheidet
                // intern, ob er TabSchliessenHinweis zeigt) -- nur die Leiste der
                // Seite verschwindet.
                expect(screen.getByTestId('mock-editor')).toBeInTheDocument();
            }
        );

        it('normales "Fertig" (nicht ueber X-Button) blendet die Leiste NICHT aus -- "Sie lesen nur mit." bleibt sichtbar', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Fertig' }));

            expect(await screen.findByText('Sie lesen nur mit.')).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeInTheDocument();
        });
    });

    describe('Untaetigkeits-Timer', () => {
        it('speichert VOR dem Freigeben (Reihenfolge per Spy) und faellt danach in den Lesen-Modus zurueck', async () => {
            vi.useFakeTimers();
            const reihenfolge: string[] = [];
            mockState.speichernSpy = vi.fn(async () => {
                reihenfolge.push('speichern');
            });
            const fetchMock = buildFetchMock({ onDelete: () => reihenfolge.push('freigeben') });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);
            await act(async () => {
                await vi.advanceTimersByTimeAsync(0);
            });
            expect(screen.getByRole('button', { name: 'Fertig' })).toBeInTheDocument();

            // Standard-Timeout aus useIdleTimer: 5 Minuten Untaetigkeit.
            await act(async () => {
                await vi.advanceTimersByTimeAsync(300_000);
            });

            expect(reihenfolge).toEqual(['speichern', 'freigeben']);
            expect(mockState.speichernSpy).toHaveBeenCalledTimes(1);
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeEnabled();
        });

        it('ist im Lesen-Modus (Fremdsperre) deaktiviert -- kein automatisches Freigeben ohne eigenes Lock', async () => {
            vi.useFakeTimers();
            const fetchMock = buildFetchMock({ acquire: () => lockResponse({ status: 'LOCKED_BY_OTHER' }) });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderSeite(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=RECHNUNG`);
            await act(async () => {
                await vi.advanceTimersByTimeAsync(0);
            });

            await act(async () => {
                await vi.advanceTimersByTimeAsync(300_000);
            });

            // Kein Countdown-Band -- der Timer war nie aktiv (modus war nie 'bearbeiten').
            expect(screen.queryByText(/Wird in \d+ Sekunden freigegeben/)).not.toBeInTheDocument();
            expect(mockState.speichernSpy).not.toHaveBeenCalled();
        });
    });
});
