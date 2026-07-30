import { render, screen, waitFor, within } from '@testing-library/react';
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
 * Öffnet das Status-Dropdown und wählt einen Eintrag.
 * Die Auswahl wird bewusst im Dropdown gesucht – Begriffe wie "Beendet" stehen
 * auch auf den Projektkarten.
 */
async function waehleStatus(user: ReturnType<typeof userEvent.setup>, label: string) {
    await user.click(screen.getByText('Alle'));
    const dropdown = await screen.findByRole('listbox');
    await user.click(within(dropdown).getByRole('option', { name: label }));
    await user.click(screen.getByRole('button', { name: 'Filtern' }));
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
});
