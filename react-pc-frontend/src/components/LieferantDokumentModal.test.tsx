import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ComponentProps } from 'react';
import LieferantDokumentModal from './LieferantDokumentModal';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import type { LieferantDokument } from '../types';

/**
 * Deckt die Umstellung von LieferantDokumentModal auf das neue,
 * verallgemeinerte Sperr-Fundament ab (useDatensatzLock/BearbeitenLeiste/
 * GesperrtHinweis statt useDocumentLock/DocumentLockedModal). Fetch wird wie
 * in useDatensatzLock.test.tsx gefakt, keine echten Netzwerkaufrufe.
 *
 * DSGVO: ausschliesslich Dummy-Namen/-Firmen.
 */

const DOKUMENT_ID = 42;
const LIEFERANT_ID = 7;

const DUMMY_DOKUMENT: LieferantDokument = {
    id: DOKUMENT_ID,
    typ: 'RECHNUNG',
    originalDateiname: 'rechnung-dummy.pdf',
    uploadDatum: '2026-08-01T10:00:00',
    geschaeftsdaten: {
        dokumentNummer: 'RE-2026-100',
        dokumentDatum: '2026-08-01',
        betragNetto: 100,
        betragBrutto: 119,
        mwstSatz: 0.19,
    },
    projektAnteile: [],
    verknuepfteDokumente: [],
};

/** Dummy-Response-Fabrik fuer DatensatzLockDto -- Stil aus useDatensatzLock.test.tsx uebernommen. */
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
        holderDisplayName: 'Thomas Beispiel',
        acquiredAt: '2026-08-01T09:00:00.000Z',
        lastHeartbeatAt: '2026-08-01T09:00:00.000Z',
        ...overrides,
    };
    return new Response(JSON.stringify(body), { status: body.status === 'ACQUIRED' ? 200 : 409 });
}

interface FetchSzenario {
    /** aufrufNummer beginnt bei 1 -- so kann ein Test den 1. (Mount) vom 2. (Retry-Klick) Acquire unterscheiden. */
    acquire?: (aufrufNummer: number) => Response | Promise<Response>;
    put?: () => Response | Promise<Response>;
}

function buildFetchMock(szenario: FetchSzenario = {}) {
    let acquireAufrufe = 0;
    return vi.fn((url: string, options?: RequestInit) => {
        const methode = options?.method ?? 'GET';

        if (typeof url === 'string' && url.includes('/download')) {
            return Promise.resolve(new Response(new Blob(['dummy'], { type: 'application/pdf' })));
        }
        if (typeof url === 'string' && url.endsWith('/acquire')) {
            acquireAufrufe += 1;
            return Promise.resolve(szenario.acquire ? szenario.acquire(acquireAufrufe) : lockResponse());
        }
        if (typeof url === 'string' && url.endsWith('/heartbeat')) {
            return Promise.resolve(lockResponse());
        }
        if (typeof url === 'string' && url.includes('/api/datensatz-locks/') && methode === 'DELETE') {
            return Promise.resolve(new Response(null, { status: 204 }));
        }
        if (typeof url === 'string' && url.startsWith('/api/lieferant-dokumente/') && methode === 'PUT') {
            return Promise.resolve(
                szenario.put ? szenario.put() : new Response(JSON.stringify({ ...DUMMY_DOKUMENT }), { status: 200 })
            );
        }
        return Promise.resolve(new Response(null, { status: 404 }));
    });
}

function renderModal(props: Partial<ComponentProps<typeof LieferantDokumentModal>> = {}) {
    const onClose = vi.fn();
    const onSave = vi.fn();
    render(
        <ToastProvider>
            <ConfirmProvider>
                <LieferantDokumentModal
                    isOpen
                    onClose={onClose}
                    dokument={DUMMY_DOKUMENT}
                    lieferantId={LIEFERANT_ID}
                    onSave={onSave}
                    {...props}
                />
            </ConfirmProvider>
        </ToastProvider>
    );
    return { onClose, onSave };
}

describe('LieferantDokumentModal', () => {
    beforeEach(() => {
        // jsdom kennt keine Blob-URLs -- fuer die PDF-Vorschau gefakt.
        global.URL.createObjectURL = vi.fn(() => `blob:vorschau-${Math.random()}`);
        global.URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        // Sicherheitsnetz: ein abgebrochener Fake-Timer-Test darf nachfolgende
        // Tests nicht unbemerkt unter Fake-Timern haengen lassen.
        vi.useRealTimers();
    });

    describe('Sperre beim Oeffnen', () => {
        it('ruft beim Oeffnen die Acquire-Route mit EINGANG und der Dokument-ID auf', async () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/acquire`,
                    expect.objectContaining({ method: 'POST' })
                )
            );
        });

        it('ruft keine Sperr-Route auf, wenn das Modal geschlossen ist', () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal({ isOpen: false });

            expect(fetchMock.mock.calls.some(call => String(call[0]).includes('/datensatz-locks/'))).toBe(false);
        });
    });

    describe('Freies Lock (Zustand "acquired")', () => {
        it('Formular ist nach dem Oeffnen sofort frei, Leiste zeigt "Fertig" -- kein Extra-Klick auf Bearbeiten noetig', async () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            expect(await screen.findByRole('button', { name: 'Fertig' })).toBeEnabled();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeEnabled();
            expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
            expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
        });

        it('ruft die Acquire-Route nur einmal auf -- der fruehere Modal-Effekt (6b-Behelf) entfaellt seit Task 7b, der Hook selbst schaltet nach Erfolg auf "bearbeiten" um', async () => {
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            await screen.findByRole('button', { name: 'Fertig' });
            // Ein paar Mikrotask-Ticks "nachschwingen" lassen, damit ein
            // etwaiger uebrig gebliebener Effekt Zeit haette, noch zuzuschlagen.
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(
                fetchMock.mock.calls.filter(call => String(call[0]).endsWith('/acquire'))
            ).toHaveLength(1);
        });

        it('Klick auf "Fertig" gibt die Sperre per DELETE frei; Formular wieder gesperrt, "Bearbeiten" aktiv', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();
            await user.click(await screen.findByRole('button', { name: 'Fertig' }));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`,
                    expect.objectContaining({ method: 'DELETE' })
                )
            );
            expect(await screen.findByRole('button', { name: 'Bearbeiten' })).toBeEnabled();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
        });
    });

    describe('Fremdes Lock (Zustand "locked-by-other")', () => {
        it('zeigt GesperrtHinweis, sperrt das Formular, und "Bearbeiten" versucht die Uebernahme', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock({
                acquire: aufrufNummer =>
                    aufrufNummer === 1
                        ? lockResponse({ status: 'LOCKED_BY_OTHER', holderDisplayName: 'Thomas Beispiel' })
                        : lockResponse(),
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            expect(await screen.findByText(/Thomas Beispiel/)).toBeInTheDocument();
            expect(screen.getByText(/bearbeitet das gerade/)).toBeInTheDocument();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

            const bearbeiten = screen.getByRole('button', { name: 'Bearbeiten' });
            expect(bearbeiten).toBeEnabled();

            await user.click(bearbeiten);

            expect(await screen.findByRole('button', { name: 'Fertig' })).toBeInTheDocument();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeEnabled();
            expect(
                fetchMock.mock.calls.filter(call => call[0] === `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/acquire`)
            ).toHaveLength(2);
        });
    });

    describe('Waehrend des Sperren-Abrufs (Zustand "loading")', () => {
        it('zeigt einen Ladehinweis statt eines leeren Kastens; "Bearbeiten" ist deaktiviert', async () => {
            let acquireAufloesen: (r: Response) => void = () => {};
            const fetchMock = buildFetchMock({
                acquire: () => new Promise<Response>(resolve => { acquireAufloesen = resolve; }),
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            expect(await screen.findByText('Sperre wird geprüft…')).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeDisabled();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeDisabled();

            // Aufraeumen, damit die haengende Promise den Test nicht ueberlebt.
            acquireAufloesen(lockResponse());
            await screen.findByRole('button', { name: 'Fertig' });
        });
    });

    describe('Fehler beim Sperren (Zustand "error")', () => {
        it('zeigt Hinweis im Modal UND Toast; "Bearbeiten" und "Speichern" bleiben deaktiviert', async () => {
            const fetchMock = buildFetchMock({ acquire: () => new Response(null, { status: 500 }) });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            expect(await screen.findByRole('alert')).toHaveTextContent(
                'Sperre konnte nicht geholt werden — bitte neu laden.'
            );
            // Derselbe Wortlaut kommt zusaetzlich als Toast -- also zweimal im Dokument.
            await waitFor(() =>
                expect(screen.getAllByText('Sperre konnte nicht geholt werden — bitte neu laden.')).toHaveLength(2)
            );
            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
        });
    });

    describe('Schliessen', () => {
        it('X-Button gibt die Sperre aktiv per DELETE frei (nicht erst beim Unmount) und ruft onClose', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            const { onClose } = renderModal();
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Schließen' }));

            expect(onClose).toHaveBeenCalledTimes(1);
            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`,
                    expect.objectContaining({ method: 'DELETE' })
                )
            );
        });

        it('"Abbrechen" gibt die Sperre ebenfalls aktiv frei', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            const { onClose } = renderModal();
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Abbrechen' }));

            expect(onClose).toHaveBeenCalledTimes(1);
            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`,
                    expect.objectContaining({ method: 'DELETE' })
                )
            );
        });
    });

    describe('Speichern', () => {
        it('speichert erfolgreich, ruft onSave/onClose und gibt danach die Sperre frei', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            const { onSave, onClose } = renderModal();
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Speichern' }));

            await waitFor(() =>
                expect(fetchMock).toHaveBeenCalledWith(
                    `/api/lieferant-dokumente/${DOKUMENT_ID}`,
                    expect.objectContaining({ method: 'PUT' })
                )
            );
            await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
            expect(onClose).toHaveBeenCalledTimes(1);
            expect(fetchMock).toHaveBeenCalledWith(
                `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`,
                expect.objectContaining({ method: 'DELETE' })
            );
        });

        it('zeigt bei einem Versionskonflikt (409) die Neu-laden-Meldung und speichert nicht lokal weiter', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock({
                put: () =>
                    new Response(
                        JSON.stringify({
                            message:
                                'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.',
                        }),
                        { status: 409 }
                    ),
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            const { onSave } = renderModal();
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Speichern' }));

            expect(await screen.findByText('Nicht gespeichert')).toBeInTheDocument();
            expect(onSave).not.toHaveBeenCalled();
        });

        it('zeigt bei einem gewoehnlichen Speicherfehler (500) einen Toast und laesst das Modal offen', async () => {
            const user = userEvent.setup();
            const fetchMock = buildFetchMock({ put: () => new Response(null, { status: 500 }) });
            global.fetch = fetchMock as unknown as typeof fetch;

            const { onSave, onClose } = renderModal();
            await screen.findByRole('button', { name: 'Fertig' });

            await user.click(screen.getByRole('button', { name: 'Speichern' }));

            const treffer = await screen.findAllByText('Speichern fehlgeschlagen');
            expect(treffer.length).toBeGreaterThanOrEqual(1);
            expect(onSave).not.toHaveBeenCalled();
            expect(onClose).not.toHaveBeenCalled();
        });
    });

    describe('Untaetigkeit', () => {
        it('gibt die Sperre nach 5 Minuten Untaetigkeit automatisch frei, faellt zurueck in "lesen", und zeigt vorher den Countdown', async () => {
            vi.useFakeTimers();
            const fetchMock = buildFetchMock();
            global.fetch = fetchMock as unknown as typeof fetch;

            renderModal();

            // Mount-Acquire ist reine Mikrotask-Kette, keine Timer beteiligt.
            await act(async () => {
                await Promise.resolve();
                await Promise.resolve();
                await Promise.resolve();
            });
            expect(screen.getByRole('button', { name: 'Fertig' })).toBeInTheDocument();

            // Vorwarnung 60s vor Ablauf (Standardwerte aus useIdleTimer).
            await act(async () => {
                await vi.advanceTimersByTimeAsync(300_000 - 60_000);
            });
            expect(
                screen.getByText('Wird in 60 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.')
            ).toBeInTheDocument();

            await act(async () => {
                await vi.advanceTimersByTimeAsync(60_000);
            });

            expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeEnabled();
            expect(screen.getByPlaceholderText('RE-2024-001')).toBeDisabled();
            expect(fetchMock).toHaveBeenCalledWith(
                `/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`,
                expect.objectContaining({ method: 'DELETE' })
            );
        });
    });
});
