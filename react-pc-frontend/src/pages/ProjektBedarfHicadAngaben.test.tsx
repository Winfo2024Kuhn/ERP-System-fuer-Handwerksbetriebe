import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';

const mock = vi.hoisted(() => ({ load: vi.fn(), toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() } }));
vi.mock('../features/einkauf/originalBedarfApi', () => ({ ladeBedarfszeilen: mock.load, nutztEchtesBackend: true, speichereWerkstatt: vi.fn(), druckeBedarfsliste: vi.fn(), loescheBedarf: vi.fn(), loeschSperrgrund: () => null }));
vi.mock('../components/ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => vi.fn() }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <main>{actions}{children}</main> }));
vi.mock('../components/MaterialbestellungModal', () => ({ MaterialbestellungModal: () => null }));
vi.mock('../components/IdsLieferantenAuswahlModal', () => ({ IdsLieferantenAuswahlModal: () => null }));

import ProjektBedarfPage from './ProjektBedarfPage';

const zeile = (werte: Record<string, unknown>) => ({
    id: 1, version: 0, projektId: 7, produktname: 'HEB 220', werkstoffName: 'S235JR', menge: 347.381, einheit: 'kg', stueckzahl: 1,
    fixmassMm: 5076.5, kilogramm: 347.381, vorhanden: 0, bestellen: 347.381, werkstattMaximum: 347.381, ...werte,
});

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(url === '/api/ids/lieferanten' || url.startsWith('/api/ids/warenkoerbe')
        ? '[]' : JSON.stringify({ id: 7, bauvorhaben: 'Musterhaus Max Mustermann' }))));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('Projektbedarf mit HiCAD-Angaben', () => {
    it('zeigt die HiCAD-Positionsnummer und die Mantelfläche je Bedarfsposition', async () => {
        mock.load.mockResolvedValue([zeile({ positionsnummer: '1200', mantelflaecheM2: 6.3483 }),
            zeile({ id: 2, produktname: 'Rohr 76.1x4', positionsnummer: '1100, 1102, 1104', mantelflaecheM2: null })]);
        render(<MemoryRouter initialEntries={['/bestellungen/bedarf/projekt/7']}><Routes>
            <Route path="/bestellungen/bedarf/projekt/:projektId" element={<ProjektBedarfPage />} />
        </Routes></MemoryRouter>);

        expect(await screen.findByText('Pos 1200')).toBeInTheDocument();
        expect(screen.getByText('Mantelfläche 6,35 m²')).toBeInTheDocument();
        expect(screen.getByText('Pos 1100, 1102, 1104')).toBeInTheDocument();
        expect(screen.getAllByText(/Mantelfläche/)).toHaveLength(1);
    });
});
