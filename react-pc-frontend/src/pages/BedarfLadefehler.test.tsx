import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';

const mock = vi.hoisted(() => ({ load: vi.fn(), toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() } }));
vi.mock('../features/einkauf/originalBedarfApi', () => ({ ladeBedarfszeilen: mock.load, nutztEchtesBackend: true, speichereWerkstatt: vi.fn(), druckeBedarfsliste: vi.fn(), loescheBedarf: vi.fn(), loeschSperrgrund: () => null }));
vi.mock('../components/ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => vi.fn() }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children }: { children: ReactNode }) => <main>{children}</main> }));
vi.mock('../components/MaterialbestellungModal', () => ({ MaterialbestellungModal: () => null }));
vi.mock('../components/IdsLieferantenAuswahlModal', () => ({ IdsLieferantenAuswahlModal: () => null }));
vi.mock('../components/AngeboteEinholenModal', () => ({ AngeboteEinholenModal: () => null }));

import BedarfUebersichtPage from './BedarfUebersichtPage';
import ProjektBedarfPage from './ProjektBedarfPage';
import BestellungEditor from './BestellungEditor';

beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 7, bauvorhaben: 'Testprojekt' }))));
    mock.load.mockRejectedValueOnce(new Error('Einkauf konnte nicht geladen werden (HTTP 503).')).mockResolvedValue([]);
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('Dauerhafter Ladefehler statt leerem Bedarf', () => {
    it.each([
        ['Übersicht', BedarfUebersichtPage, /Noch kein Material|Kein offener Bedarf/],
        ['Projekt', ProjektBedarfPage, /Noch kein Material angelegt/],
        ['Vorrat', BestellungEditor, /Kein offener Bedarf vorhanden/],
    ] as const)('%s: zeigt 503 und lädt nach Retry erneut', async (_name, Component, emptyMessage) => {
        render(<MemoryRouter initialEntries={['/projekt/7']}><Routes><Route path="/projekt/:projektId" element={<Component />} /></Routes></MemoryRouter>);
        expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 503');
        expect(screen.queryByText(emptyMessage)).not.toBeInTheDocument();
        expect(mock.toast.error).toHaveBeenCalledWith(expect.stringContaining('503'));
        await userEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
        if (_name !== 'Übersicht') expect(screen.getByText(emptyMessage)).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(mock.load).toHaveBeenCalledTimes(2);
    });
});


it('zählt vollständig gedeckte Bedarfe als gespeicherte Positionen, nicht als offene Zeilen', async () => {
    mock.load.mockReset().mockResolvedValue([{ id: 701, projektId: 7, projektName: 'Testprojekt erledigt',
        menge: 5, stueckzahl: 5, kilogramm: 10, bestellt: true, vorhanden: 0, bestellen: 0,
        bedarf: { mengen: { bedarf: 5, bestellt: 5, disponierbar: 0, ungedeckt: 0 } } }]);
    render(<MemoryRouter><BedarfUebersichtPage /></MemoryRouter>);
    expect(await screen.findByText('Testprojekt erledigt')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Bedarfspositionen' })).toBeInTheDocument();
    expect(screen.queryByText('Offene Zeilen')).not.toBeInTheDocument();
});
