import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';
import ProjektEditor from './ProjektEditor';

vi.mock('../components/ui/PdfCanvasViewer', () => ({ PdfCanvasViewer: () => null }));

describe('ProjektEditor – einfache Materialkosten', () => {
    beforeEach(() => {
        global.fetch = vi.fn((input: RequestInfo | URL) => {
            const url = String(input);
            const body = url === '/api/projekte/9'
                ? {
                    id: 9, bauvorhaben: 'Werkstattdach Muster', bruttoPreis: 1190, bezahlt: false,
                    artikel: [{ id: 4, artikelId: 3, produktname: 'Altes Lagerprofil', ausLager: true, gesamtpreis: 40 }],
                    materialkosten: [{
                        id: 8, beschreibung: 'Dummy Bohrer', betrag: 16, artikelIdSnapshot: 31,
                        mengeSnapshot: 2, einheitSnapshot: 'STUECK', preisJeEinheitSnapshot: 8,
                        lieferantennameSnapshot: 'Dummy Lieferant',
                    }], zeiten: [],
                }
                : url.startsWith('/api/projekte/9/eingangsrechnungen') || url === '/api/ausgangs-dokumente/projekt/9' ? [] : {};
            return Promise.resolve({ ok: true, json: () => Promise.resolve(body) } as Response);
        }) as typeof fetch;
    });

    it('zeigt Artikelmenge und gespeicherten Einzelpreis, ohne Lagerbedienung anzubieten', async () => {
        render(
            <MemoryRouter initialEntries={['/projekte?projektId=9&tab=materialkosten']}>
                <ConfirmProvider><ToastProvider><ProjektEditor /></ToastProvider></ConfirmProvider>
            </MemoryRouter>,
        );

        expect(await screen.findByText('Dummy Bohrer')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Material (2)' })).toBeInTheDocument();
        expect(screen.getByText(/2\s*Stück/u)).toBeInTheDocument();
        expect(screen.getByText(/8,00\s*€/u)).toBeInTheDocument();
        expect(screen.getByText(/56,00\s*€/u)).toBeInTheDocument();
        expect(screen.queryByText(/Teilentnahme aus Lager|Teilentnahmen aus Lager/)).not.toBeInTheDocument();
    });
});
