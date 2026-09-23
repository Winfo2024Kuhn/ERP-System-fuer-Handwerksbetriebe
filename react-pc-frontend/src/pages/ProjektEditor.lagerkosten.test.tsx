import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import ProjektEditor from './ProjektEditor';

vi.mock('../components/ui/PdfCanvasViewer', () => ({ PdfCanvasViewer: () => null }));

describe('ProjektEditor – Lagerentnahmekosten', () => {
    beforeEach(() => {
        global.fetch = vi.fn((input: RequestInfo | URL) => {
            const url = String(input);
            const body = url === '/api/projekte/9'
                ? {
                    id: 9, bauvorhaben: 'Werkstattdach Muster', bruttoPreis: 1190, bezahlt: false,
                    lagerentnahmenKosten: 6, lagerentnahmenBewertungOffen: true,
                    artikel: [{ id: 4, artikelId: 3, produktname: 'Altes Lagerprofil', ausLager: true, gesamtpreis: 40 }],
                    materialkosten: [], zeiten: [],
                }
                : url.startsWith('/api/projekte/9/eingangsrechnungen') || url === '/api/ausgangs-dokumente/projekt/9' ? [] : {};
            return Promise.resolve({ ok: true, json: () => Promise.resolve(body) } as Response);
        }) as typeof fetch;
    });

    it('addiert bestätigte Teilentnahmen zusätzlich zu alten Lagerpositionen und zeigt offene Bewertung', async () => {
        render(
            <MemoryRouter initialEntries={['/projekte?projektId=9&tab=materialkosten']}>
                <ConfirmProvider><ToastProvider><ProjektEditor /></ToastProvider></ConfirmProvider>
            </MemoryRouter>,
        );

        expect(await screen.findByText('Teilentnahmen aus Lager')).toBeInTheDocument();
        expect(screen.getByText('Teilentnahmen aus Lager').parentElement?.parentElement).toHaveTextContent('6,00');
        expect(screen.getAllByText(/6,00\s*€/u).length).toBeGreaterThanOrEqual(1);
        expect(screen.getByText('Bewertung offen')).toBeInTheDocument();
        expect(screen.getByText(/46,00\s*€/u)).toBeInTheDocument();
    });
});
