import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import ProjektEditor from './ProjektEditor';

// Der PDF-Viewer rendert über pdf.js auf ein Canvas – in jsdom nicht verfügbar
// und für diese Tests auch nicht relevant. Wir prüfen nur, dass die Vorschau
// mit der richtigen URL geöffnet wird.
vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf-viewer" data-url={url} />,
}));

const mockProjekte = [
    {
        id: 1,
        bauvorhaben: 'Dachsanierung Musterweg',
        kunde: 'Max Mustermann',
        auftragsnummer: '2026/07/00001',
        bruttoPreis: 7930.04,
        bezahlt: false,
        abgeschlossen: false,
    },
];

function mockFetch() {
    return vi.fn((url: string) => {
        if (url.startsWith('/api/projekte?')) {
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ projekte: mockProjekte, gesamt: mockProjekte.length }),
            });
        }
        if (url.startsWith('/api/last-accessed/')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }
        if (url.startsWith('/api/projekte/freigabe-status')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }
        if (url.startsWith('/api/projekte/jahre')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve([2026, 2025]) });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
}

function renderProjektEditor() {
    return render(
        <MemoryRouter initialEntries={['/projekte']}>
            <ConfirmProvider>
                <ToastProvider>
                    <ProjektEditor />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/** Alle URLs, mit denen die Projektliste geladen wurde. */
function projektlistenAufrufe(fetchMock: ReturnType<typeof mockFetch>): string[] {
    return fetchMock.mock.calls
        .map(call => call[0] as string)
        .filter(url => typeof url === 'string' && url.startsWith('/api/projekte?'));
}

/**
 * Öffnet das Status-Dropdown und wählt einen Eintrag. Die Auswahl allein löst
 * den Ladevorgang aus – einen Filtern-Button gibt es bewusst nicht mehr.
 * Die Auswahl wird im Dropdown gesucht, weil Begriffe wie "Beendet" auch auf
 * den Projektkarten stehen.
 */
async function waehleStatus(user: ReturnType<typeof userEvent.setup>, label: string) {
    await user.click(screen.getByText('Alle'));
    const dropdown = await screen.findByRole('listbox');
    await user.click(within(dropdown).getByRole('option', { name: label }));
}

describe('ProjektEditor – Projektliste', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('zeigt die PDF-Liste der Projekte in Arbeit in der Vorschau statt sie herunterzuladen', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg')).toBeInTheDocument());

        await user.click(screen.getByRole('button', { name: /Liste: Projekte in Arbeit/i }));

        const viewer = await screen.findByTestId('pdf-viewer');
        expect(viewer).toHaveAttribute('data-url', '/api/projekte/export-pdf');
    });

    it('filtert über den Status auf beendete Projekte', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg')).toBeInTheDocument());

        await waehleStatus(user, 'Beendet');

        await waitFor(() => {
            expect(projektlistenAufrufe(fetchMock).some(url => url.includes('abgeschlossen=true'))).toBe(true);
        });
    });

    it('filtert über den Status auf Projekte in Arbeit', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg')).toBeInTheDocument());

        await waehleStatus(user, 'In Arbeit');

        await waitFor(() => {
            expect(projektlistenAufrufe(fetchMock).some(url => url.includes('abgeschlossen=false'))).toBe(true);
        });
    });

    it('filtert über das Jahres-Dropdown auf das gewählte Anlegejahr', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg')).toBeInTheDocument());

        // Das Jahres-Dropdown zeigt als Platzhalter "Alle Jahre" und wird aus
        // /api/projekte/jahre befüllt (im Mock: 2026 und 2025).
        await user.click(screen.getByText('Alle Jahre'));
        const dropdown = await screen.findByRole('listbox');
        await user.click(within(dropdown).getByRole('option', { name: '2025' }));

        await waitFor(() => {
            expect(projektlistenAufrufe(fetchMock).some(url => url.includes('jahr=2025'))).toBe(true);
        });
    });

    it('setzt den Jahresfilter beim Zurücksetzen wieder auf alle Jahre', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg')).toBeInTheDocument());

        await user.click(screen.getByText('Alle Jahre'));
        const dropdown = await screen.findByRole('listbox');
        await user.click(within(dropdown).getByRole('option', { name: '2025' }));
        await waitFor(() => {
            expect(projektlistenAufrufe(fetchMock).some(url => url.includes('jahr=2025'))).toBe(true);
        });

        await user.click(screen.getByRole('button', { name: /Filter zurücksetzen/i }));

        await waitFor(() => {
            const alle = projektlistenAufrufe(fetchMock);
            expect(alle[alle.length - 1]).not.toContain('jahr=');
        });
    });
});

describe('ProjektEditor – Pagination', () => {
    // PAGE_SIZE im ProjektEditor ist 12 – bei 40 Treffern also 4 Seiten.
    const PAGE_SIZE = 12;
    const GESAMT = 40;

    let fetchMock: ReturnType<typeof vi.fn>;
    // Über diese Variable lässt sich im Test simulieren, dass die Trefferzahl schrumpft.
    let gesamtTreffer = GESAMT;

    // DSGVO: ausschließlich Dummy-Daten in Tests.
    function dummyProjekte(anzahl: number) {
        return Array.from({ length: anzahl }, (_, i) => ({
            id: i + 1,
            bauvorhaben: `Musterbauvorhaben ${i + 1}`,
            kunde: 'Max Mustermann',
            auftragsnummer: `2026/07/${(i + 1).toString().padStart(5, '0')}`,
            bruttoPreis: 1000,
            bezahlt: false,
            abgeschlossen: false,
        }));
    }

    /** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
    function letzterListenRequest(): URLSearchParams {
        const alle = projektlistenAufrufe(fetchMock as ReturnType<typeof mockFetch>);
        const letzte = alle[alle.length - 1];
        return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
    }

    beforeEach(() => {
        gesamtTreffer = GESAMT;
        fetchMock = vi.fn((url: string) => {
            const antwort = (daten: unknown) => Promise.resolve({ ok: true, json: () => Promise.resolve(daten) });
            if (typeof url !== 'string') return antwort({});
            if (url.startsWith('/api/last-accessed/')) return antwort({});
            if (url.startsWith('/api/projekte/freigabe-status')) return antwort({});
            if (url.startsWith('/api/projekte/jahre')) return antwort([2026]);
            if (url.startsWith('/api/projekte?')) {
                const params = new URLSearchParams(url.substring(url.indexOf('?') + 1));
                const seite = Number(params.get('page') ?? 0);
                const verbleibend = Math.max(0, gesamtTreffer - seite * PAGE_SIZE);
                return antwort({
                    projekte: dummyProjekte(Math.min(PAGE_SIZE, verbleibend)),
                    gesamt: gesamtTreffer,
                });
            }
            return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
        });
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('springt auf die letzte gültige Seite, wenn die Trefferzahl schrumpft', async () => {
        const user = userEvent.setup();
        renderProjektEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        const weiter = () => screen.getByRole('button', { name: /weiter/i });
        await user.click(weiter());
        await user.click(weiter());
        await user.click(weiter());
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('3'));

        // Ab jetzt liefert das Backend nur noch 20 Treffer – also zwei Seiten (0 und 1).
        gesamtTreffer = 20;
        await user.click(screen.getByRole('button', { name: /Aktualisieren/i }));

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));
        expect(screen.getByText('Musterbauvorhaben 1')).toBeInTheDocument();

        // Der Guard darf sich nicht selbst nachtriggern. Erwartet sind 6 Requests
        // (Start, 3x Weiter, Aktualisieren, Korrektur-Sprung); eine Endlosschleife
        // würde hier sofort auffallen.
        await act(async () => {
            await Promise.resolve();
        });
        expect(projektlistenAufrufe(fetchMock as ReturnType<typeof mockFetch>).length).toBeLessThanOrEqual(8);
    });
});
