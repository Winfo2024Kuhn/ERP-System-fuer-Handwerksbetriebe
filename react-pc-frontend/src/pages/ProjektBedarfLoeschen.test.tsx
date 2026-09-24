import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import type { BedarfResponse } from '../features/einkauf/types';

const mock = vi.hoisted(() => ({
    load: vi.fn(), loesche: vi.fn(), confirm: vi.fn(),
    toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() },
}));
vi.mock('../features/einkauf/originalBedarfApi', async (original) => ({
    ...(await original<typeof import('../features/einkauf/originalBedarfApi')>()),
    ladeBedarfszeilen: mock.load, nutztEchtesBackend: true, loescheBedarf: mock.loesche,
    speichereWerkstatt: vi.fn(), druckeBedarfsliste: vi.fn(),
}));
vi.mock('../components/ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => mock.confirm }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <main>{actions}{children}</main> }));
vi.mock('../components/MaterialbestellungModal', () => ({ MaterialbestellungModal: () => null }));
vi.mock('../components/IdsLieferantenAuswahlModal', () => ({ IdsLieferantenAuswahlModal: () => null }));

import ProjektBedarfPage from './ProjektBedarfPage';

const bedarf = (mengen: Partial<BedarfResponse['mengen']> = {}): BedarfResponse => ({
    id: 5, version: 3,
    position: { art: 'FREITEXT', artikelId: null, interneReferenz: null, zeichnungsnummer: null, zeichnungsrevision: null,
        bezeichnung: 'Flachstahl 40x5', werkstoff: 'S235JR', abmessung: null,
        basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null },
        schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] },
    liefergruppe: { projektId: 7, lagerzweck: null, lieferadresse: null, bedarfstermin: null },
    mengen: { bedarf: 4, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 4, disponierbar: 4, ...mengen },
    nachpflegeErforderlich: false, historischerHinweis: null,
});
const zeile = (b: BedarfResponse) => ({
    id: b.id, version: b.version, bedarf: b, projektId: 7, produktname: 'Flachstahl 40x5', menge: 4, einheit: 'Stück',
    stueckzahl: 4, vorhanden: b.mengen.lagergedeckt ?? 0, bestellen: 4, werkstattMaximum: 4,
});

const renderPage = () => render(<MemoryRouter initialEntries={['/bestellungen/bedarf/projekt/7']}><Routes>
    <Route path="/bestellungen/bedarf/projekt/:projektId" element={<ProjektBedarfPage />} />
</Routes></MemoryRouter>);

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(url === '/api/ids/lieferanten' || url.startsWith('/api/ids/warenkoerbe')
        ? '[]' : JSON.stringify({ id: 7, bauvorhaben: 'Musterhaus Max Mustermann' }))));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('Projektbedarf löschen', () => {
    it('löscht einen freien Bedarf nach Bestätigung mit Version und lädt die Liste neu', async () => {
        mock.load.mockResolvedValueOnce([zeile(bedarf())]).mockResolvedValueOnce([]);
        mock.confirm.mockResolvedValue(true);
        mock.loesche.mockResolvedValue(undefined);
        renderPage();

        const button = await screen.findByRole('button', { name: 'Flachstahl 40x5 löschen' });
        expect(button).toBeEnabled();
        fireEvent.click(button);

        await waitFor(() => expect(mock.loesche).toHaveBeenCalledWith(expect.objectContaining({ id: 5, version: 3 })));
        expect(mock.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'Bedarf wirklich löschen?', variant: 'danger' }));
        await waitFor(() => expect(mock.toast.success).toHaveBeenCalledWith('Bedarf gelöscht.'));
        await waitFor(() => expect(mock.load).toHaveBeenCalledTimes(2));
    });

    it('löscht nichts, wenn die Bestätigung abgebrochen wird', async () => {
        mock.load.mockResolvedValue([zeile(bedarf())]);
        mock.confirm.mockResolvedValue(false);
        renderPage();
        fireEvent.click(await screen.findByRole('button', { name: 'Flachstahl 40x5 löschen' }));
        await waitFor(() => expect(mock.confirm).toHaveBeenCalled());
        expect(mock.loesche).not.toHaveBeenCalled();
    });

    it('zeigt die Servermeldung bei einem Konflikt im Toast', async () => {
        mock.load.mockResolvedValue([zeile(bedarf())]);
        mock.confirm.mockResolvedValue(true);
        mock.loesche.mockRejectedValue(new Error('Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden.'));
        renderPage();
        fireEvent.click(await screen.findByRole('button', { name: 'Flachstahl 40x5 löschen' }));
        await waitFor(() => expect(mock.toast.error).toHaveBeenCalledWith(
            'Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden.'));
        expect(mock.toast.success).not.toHaveBeenCalled();
    });

    it('sperrt bereits angefragte Bedarfe und erklärt den Grund', async () => {
        mock.load.mockResolvedValue([zeile(bedarf({ angefragt: 4 }))]);
        renderPage();
        const button = await screen.findByRole('button', { name: /Löschen nicht möglich: Steht in einer Preisanfrage/ });
        expect(button).toBeDisabled();
        expect(button.parentElement).toHaveAttribute('title', 'Steht in einer Preisanfrage – nicht mehr löschbar');
        fireEvent.click(button);
        expect(mock.confirm).not.toHaveBeenCalled();
    });
});
