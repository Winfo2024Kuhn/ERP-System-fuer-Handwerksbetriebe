import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';

const mock = vi.hoisted(() => ({ load: vi.fn(), modal: vi.fn(), toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn(), success: vi.fn() } }));
vi.mock('../features/einkauf/originalBedarfApi', () => ({ ladeBedarfszeilen: mock.load, nutztEchtesBackend: true, speichereWerkstatt: vi.fn(), druckeBedarfsliste: vi.fn(), loescheBedarf: vi.fn(), loeschSperrgrund: () => null }));
vi.mock('../components/ui/toast', () => ({ useToast: () => mock.toast }));
vi.mock('../components/ui/confirm-dialog', () => ({ useConfirm: () => vi.fn() }));
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: { children: ReactNode; actions?: ReactNode }) => <main>{actions}{children}</main> }));
vi.mock('../components/MaterialbestellungModal', () => ({ MaterialbestellungModal: () => null }));
vi.mock('../components/IdsLieferantenAuswahlModal', () => ({ IdsLieferantenAuswahlModal: (props: unknown) => { mock.modal(props); return null; } }));

import ProjektBedarfPage from './ProjektBedarfPage';

const warenkorb = (id: string, number: string, ordered = false) => ({ id, number, ordered, reference: '', updatedAt: '2026-09-24T10:00:00Z', projektId: 7, projektName: 'Musterhaus Max Mustermann',
    items: [{ article: 'DEMO-001', name: 'Dummy Schraube', quantity: 2, unit: 'Stück', netPrice: 1, priceBasis: 1 }, { article: 'DEMO-002', name: 'Dummy Dübel', quantity: 1, unit: 'Stück', netPrice: 1, priceBasis: 1 }] });

function routes(lieferanten: unknown[], warenkoerbe: Response | (() => Response)) {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url === '/api/ids/lieferanten') return new Response(JSON.stringify(lieferanten));
        if (url.startsWith('/api/ids/warenkoerbe')) return typeof warenkoerbe === 'function' ? warenkoerbe() : warenkoerbe.clone();
        return new Response(JSON.stringify({ id: 7, bauvorhaben: 'Musterhaus Max Mustermann' }));
    }));
}
const renderPage = (pfad = '/bestellungen/bedarf/projekt/7') => render(<MemoryRouter initialEntries={[pfad]}><Routes><Route path="/bestellungen/bedarf/projekt/:projektId" element={<ProjektBedarfPage />} /></Routes></MemoryRouter>);

beforeEach(() => { vi.clearAllMocks(); mock.load.mockResolvedValue([]); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('Geparkte Shop-Warenkörbe am Projekt', () => {
    it('lädt die Warenkörbe des Projekts, hebt den zurückgegebenen hervor und bestätigt per Toast', async () => {
        routes([{ id: 203, name: 'Würth' }], new Response(JSON.stringify([warenkorb('a1', 'B-2026-000001'), warenkorb('b2', 'B-2026-000002', true)])));
        renderPage('/bestellungen/bedarf/projekt/7?warenkorb=a1');
        const abschnitt = await screen.findByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
        const offen = await within(abschnitt).findByRole('link', { name: /B-2026-000001/ });
        expect(offen).toHaveAttribute('href', '/bestellungen/ids/a1');
        expect(offen).toHaveAttribute('aria-current', 'true');
        expect(offen).toHaveTextContent('2 Positionen');
        expect(offen).toHaveTextContent('Noch nicht bestellt');
        const bestellt = within(abschnitt).getByRole('link', { name: /B-2026-000002/ });
        expect(bestellt).toHaveTextContent('Bestellt');
        expect(bestellt).not.toHaveAttribute('aria-current');
        await waitFor(() => expect(mock.toast.success).toHaveBeenCalledWith('Warenkorb am Projekt geparkt'));
        expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/ids/warenkoerbe?projektId=7');
        await waitFor(() => expect(mock.modal).toHaveBeenLastCalledWith(expect.objectContaining({ projektId: 7, projektName: 'Musterhaus Max Mustermann' })));
    });

    it('zeigt einen Leer-Hinweis, wenn der Shop eingerichtet, aber noch nichts geparkt ist', async () => {
        routes([{ id: 203, name: 'Würth' }], new Response('[]'));
        renderPage();
        const abschnitt = await screen.findByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
        expect(await within(abschnitt).findByText(/Noch kein Warenkorb geparkt/)).toBeInTheDocument();
        expect(mock.toast.success).not.toHaveBeenCalled();
    });

    it('bleibt ohne Shop-Anbindung und ohne Warenkörbe verborgen', async () => {
        routes([], new Response('[]'));
        renderPage();
        await screen.findByText(/Noch kein Material angelegt/);
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/ids/warenkoerbe?projektId=7'));
        expect(screen.queryByRole('region', { name: 'Geparkte Shop-Warenkörbe' })).not.toBeInTheDocument();
    });

    it('zeigt einen Ladefehler mit Toast und lädt nach „Erneut laden“ neu', async () => {
        let aufrufe = 0;
        routes([{ id: 203, name: 'Würth' }], () => ++aufrufe === 1 ? new Response('{}', { status: 500 }) : new Response(JSON.stringify([warenkorb('a1', 'B-2026-000001')])));
        renderPage();
        const abschnitt = await screen.findByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
        expect(await within(abschnitt).findByRole('alert')).toHaveTextContent('Geparkte Shop-Warenkörbe konnten nicht geladen werden.');
        expect(mock.toast.error).toHaveBeenCalledWith('Geparkte Shop-Warenkörbe konnten nicht geladen werden.');
        await userEvent.click(within(abschnitt).getByRole('button', { name: 'Erneut laden' }));
        expect(await within(abschnitt).findByRole('link', { name: /B-2026-000001/ })).toBeInTheDocument();
    });

    it('aktualisiert beim Fokus still: kein Toast und der bisherige Inhalt bleibt bei Fehlern stehen', async () => {
        let aufrufe = 0;
        routes([{ id: 203, name: 'Würth' }], () => ++aufrufe === 1 ? new Response(JSON.stringify([warenkorb('a1', 'B-2026-000001')])) : new Response('<html>Fehler</html>', { status: 500 }));
        renderPage();
        const abschnitt = await screen.findByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
        expect(await within(abschnitt).findByRole('link', { name: /B-2026-000001/ })).toBeInTheDocument();
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        await waitFor(() => expect(aufrufe).toBe(2));
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        await waitFor(() => expect(aufrufe).toBe(3));
        expect(within(abschnitt).getByRole('link', { name: /B-2026-000001/ })).toBeInTheDocument();
        expect(within(abschnitt).queryByRole('alert')).not.toBeInTheDocument();
        expect(within(abschnitt).queryByRole('status')).not.toBeInTheDocument();
        expect(mock.toast.error).not.toHaveBeenCalled();
    });

    it('zeigt beim Fokus neu geparkte Warenkörbe ohne Ladeanzeige an', async () => {
        let aufrufe = 0;
        routes([{ id: 203, name: 'Würth' }], () => new Response(JSON.stringify(++aufrufe === 1 ? [] : [warenkorb('a1', 'B-2026-000001')])));
        renderPage();
        const abschnitt = await screen.findByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
        await within(abschnitt).findByText(/Noch kein Warenkorb geparkt/);
        await act(async () => { window.dispatchEvent(new Event('focus')); });
        expect(await within(abschnitt).findByRole('link', { name: /B-2026-000001/ })).toBeInTheDocument();
        expect(mock.toast.error).not.toHaveBeenCalled();
    });

    it('blendet den Abschnitt ohne Fehler aus, wenn die Warenkorb-Schnittstelle nicht verfügbar ist (404)', async () => {
        routes([{ id: 203, name: 'Würth' }], new Response(JSON.stringify({ message: 'Nicht gefunden' }), { status: 404 }));
        renderPage();
        await screen.findByText(/Noch kein Material angelegt/);
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/ids/warenkoerbe?projektId=7'));
        await waitFor(() => expect(screen.queryByRole('region', { name: 'Geparkte Shop-Warenkörbe' })).not.toBeInTheDocument());
        expect(mock.toast.error).not.toHaveBeenCalled();
    });
});
