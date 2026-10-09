import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ZeugnisPositionen } from './ZeugnisPositionen';
import { ToastProvider } from './ui/toast';

// DSGVO: nur Fantasie-Werte (Musterstahl GmbH, Dummy-Chargen)

function position(id: number, felder: Record<string, unknown>) {
    return {
        id, positionNr: id, positionsArt: 'WARE', externeArtikelnummer: null, bezeichnung: null,
        menge: null, mengeneinheit: null, einzelpreis: null, preiseinheit: null, gesamtpreisNetto: null,
        projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null,
        werkstoff: null, charge: null, abmessung: null,
        ...felder,
    };
}

function antwort(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function rendere(dokumentId = 77) {
    return render(
        <ToastProvider>
            <ZeugnisPositionen dokumentId={dokumentId} />
        </ToastProvider>,
    );
}

describe('ZeugnisPositionen', () => {
    afterEach(() => vi.restoreAllMocks());

    it('zeigt Erzeugnis, Werkstoff, Charge, Abmessung und Menge mit Einheit', async () => {
        const fetchMock = vi.fn(() => Promise.resolve(antwort({
            geschaeftsdokumentId: 77,
            positionen: [
                position(1, { bezeichnung: 'Flachstahl 50x5', werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5', menge: 12.5, mengeneinheit: 'm' }),
                position(2, { bezeichnung: 'Rundrohr', menge: 3 }),
                position(3, { bezeichnung: null }),
            ],
        })));
        globalThis.fetch = fetchMock as unknown as typeof fetch;

        rendere();

        expect(screen.getByRole('status', { name: 'Positionen werden geladen' })).toBeInTheDocument();
        expect(await screen.findByText('Flachstahl 50x5')).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith('/api/bestellungen-uebersicht/positionen/77', expect.objectContaining({ signal: expect.any(AbortSignal) }));
        for (const kopf of ['Erzeugnis', 'Werkstoff', 'Charge', 'Abmessung', 'Menge']) {
            expect(screen.getByRole('columnheader', { name: kopf })).toBeInTheDocument();
        }
        expect(screen.getByText('S235JR')).toBeInTheDocument();
        expect(screen.getByText('123456')).toBeInTheDocument();
        expect(screen.getByText('50 x 5')).toBeInTheDocument();
        expect(screen.getByText('12,5 m')).toBeInTheDocument();
        // Menge ohne Einheit, fehlende Werte als Strich
        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getAllByRole('row')).toHaveLength(4);
        expect(screen.getAllByText('–').length).toBeGreaterThanOrEqual(8);
    });

    it('zeigt einen Hinweis, wenn noch keine Positionen da sind (404)', async () => {
        globalThis.fetch = vi.fn(() => Promise.resolve(new Response(null, { status: 404 }))) as unknown as typeof fetch;
        rendere();
        expect(await screen.findByText('Noch keine Positionen ausgelesen.')).toBeInTheDocument();
    });

    it('behandelt eine Antwort ohne Positionsliste wie keine Positionen', async () => {
        globalThis.fetch = vi.fn(() => Promise.resolve(antwort({ geschaeftsdokumentId: 77 }))) as unknown as typeof fetch;
        rendere();
        expect(await screen.findByText('Noch keine Positionen ausgelesen.')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler sichtbar und als Toast', async () => {
        globalThis.fetch = vi.fn(() => Promise.resolve(new Response(null, { status: 500 }))) as unknown as typeof fetch;
        rendere();
        await waitFor(() => expect(screen.getAllByText('Positionen des Werkstoffzeugnisses konnten nicht geladen werden.')).toHaveLength(2));
    });

    it('meldet einen Netzwerkfehler mit verständlichem Text', async () => {
        globalThis.fetch = vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))) as unknown as typeof fetch;
        rendere();
        await waitFor(() => expect(screen.getAllByText('Positionen des Werkstoffzeugnisses konnten nicht geladen werden.')).toHaveLength(2));
    });

    it('bricht die Anfrage beim Schließen ab und meldet dann nichts', async () => {
        let signal: AbortSignal | undefined;
        globalThis.fetch = vi.fn((_url: string, init?: RequestInit) => {
            signal = init?.signal ?? undefined;
            return new Promise<Response>((_resolve, reject) => {
                signal?.addEventListener('abort', () => reject(new DOMException('abgebrochen', 'AbortError')));
            });
        }) as unknown as typeof fetch;
        const { unmount } = rendere();
        await waitFor(() => expect(signal).toBeDefined());
        unmount();
        expect(signal?.aborted).toBe(true);
    });
});
