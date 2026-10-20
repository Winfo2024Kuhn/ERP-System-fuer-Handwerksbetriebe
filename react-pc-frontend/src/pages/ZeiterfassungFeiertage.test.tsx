import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ZeiterfassungFeiertage, { type FeiertagDto } from './ZeiterfassungFeiertage';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { ToastProvider } from '../components/ui/toast';

const auth = vi.hoisted(() => ({ isAdmin: true }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }));

const jahr = new Date().getFullYear();
const monat = String(new Date().getMonth() + 1).padStart(2, '0');
const neujahr: FeiertagDto = { id: 1, datum: `${jahr}-01-01`, bezeichnung: 'Neujahr', bundesland: 'BY', halbTag: false, aktiv: true, eigener: false };
const himmelfahrt: FeiertagDto = { id: 2, datum: `${jahr}-08-15`, bezeichnung: 'Mariä Himmelfahrt', bundesland: 'BY', halbTag: false, aktiv: false, eigener: false };
const heiligabend: FeiertagDto = { id: 3, datum: `${jahr}-12-24`, bezeichnung: 'Heiligabend', bundesland: 'BY', halbTag: true, aktiv: true, eigener: true };

const json = (body: unknown, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => body }) as Response;

function renderSeite() {
    return render(<MemoryRouter><ToastProvider><ConfirmProvider><ZeiterfassungFeiertage /></ConfirmProvider></ToastProvider></MemoryRouter>);
}

describe('ZeiterfassungFeiertage', () => {
    const fetchMock = vi.fn();
    let liste: FeiertagDto[];
    let firma: unknown;

    beforeEach(() => {
        liste = [neujahr, himmelfahrt, heiligabend];
        firma = { id: 1, firmenname: 'Testbetrieb', bundesland: 'BY' };
        fetchMock.mockReset();
        fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = String(input);
            const method = init?.method ?? 'GET';
            if (url === '/api/firma') return json(firma);
            if (url.startsWith('/api/zeitverwaltung/feiertage?jahr=')) return json(liste);
            const id = Number(url.match(/feiertage\/(\d+)$/)?.[1]);
            if (method === 'PUT' && id) {
                const body = JSON.parse(String(init?.body));
                const alt = liste.find(f => f.id === id)!;
                return json({ ...alt, ...body });
            }
            if (method === 'DELETE' && id) return json(null, 204);
            if (method === 'POST' && url === '/api/zeitverwaltung/feiertage') {
                const body = JSON.parse(String(init?.body));
                if (body.datum === `${jahr}-${monat}-01`) return json({ status: 400, message: `Am 01.${monat}.${jahr} ist schon ein Feiertag eingetragen.` }, 400);
                return json({ id: 9, bundesland: 'BY', aktiv: true, eigener: true, ...body });
            }
            return json({ message: 'Unbekannt' }, 404);
        });
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => { vi.unstubAllGlobals(); auth.isAdmin = true; });

    it('zeigt das eingestellte Bundesland, Etiketten und die Info-Knöpfe', async () => {
        const user = userEvent.setup();
        renderSeite();
        expect(await screen.findByRole('heading', { name: `Feiertage in Bayern ${jahr}` })).toBeVisible();
        expect(screen.queryByText(/Greußenheim/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Bayerische Feiertage/)).not.toBeInTheDocument();
        expect(await screen.findByText('Neujahr')).toBeVisible();
        expect(screen.getByText('Abgeschaltet')).toBeVisible();
        expect(screen.getByText('Eigener Feiertag')).toBeVisible();
        expect(screen.getByRole('switch', { name: 'Gilt bei uns: Mariä Himmelfahrt' })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('switch', { name: 'Halber Tag: Heiligabend' })).toHaveAttribute('aria-checked', 'true');
        // Gesetzliche Feiertage lassen sich nicht löschen, nur abschalten.
        expect(screen.queryByRole('button', { name: 'Neujahr löschen' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Heiligabend löschen' })).toBeVisible();

        await user.click(screen.getByRole('button', { name: 'Mehr zu: Halber Tag' }));
        expect(await screen.findByText(/gutgeschrieben wird aber die Hälfte/)).toBeVisible();
        await user.keyboard('{Escape}');
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Gilt bei uns' }));
        expect(await screen.findByText(/Mariä Himmelfahrt oder Fronleichnam/)).toBeVisible();
        await user.keyboard('{Escape}');
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Feiertage neu generieren' }));
        expect(await screen.findByText(/bleiben erhalten/)).toBeVisible();
    });

    it('fällt ohne Bundesland auf eine neutrale Überschrift zurück', async () => {
        firma = { id: 1, firmenname: 'Testbetrieb' };
        renderSeite();
        expect(await screen.findByRole('heading', { name: `Feiertage ${jahr}` })).toBeVisible();
    });

    it('schaltet halben Tag und „Gilt bei uns“ über PUT um', async () => {
        const user = userEvent.setup();
        renderSeite();
        const halb = await screen.findByRole('switch', { name: 'Halber Tag: Neujahr' });
        await user.click(halb);
        await waitFor(() => expect(halb).toHaveAttribute('aria-checked', 'true'));
        const put = fetchMock.mock.calls.find(([url, init]) => String(url) === '/api/zeitverwaltung/feiertage/1' && init?.method === 'PUT');
        expect(JSON.parse(String(put?.[1]?.body))).toEqual({ halbTag: true, aktiv: true });

        await user.click(screen.getByRole('switch', { name: 'Gilt bei uns: Mariä Himmelfahrt' }));
        await waitFor(() => expect(screen.queryByText('Abgeschaltet')).not.toBeInTheDocument());
    });

    it('zeigt Fehler beim Umschalten als Toast mit der Meldung des Servers', async () => {
        const user = userEvent.setup();
        renderSeite();
        const schalter = await screen.findByRole('switch', { name: 'Halber Tag: Neujahr' });
        fetchMock.mockImplementationOnce(async () => json({ status: 403, message: 'Dafür fehlt Ihnen die Berechtigung.' }, 403));
        await user.click(schalter);
        expect(await screen.findByText('Dafür fehlt Ihnen die Berechtigung.')).toBeVisible();
        expect(schalter).toHaveAttribute('aria-checked', 'false');
    });

    it('legt einen eigenen Feiertag an und meldet Doppel-Einträge', async () => {
        const user = userEvent.setup();
        renderSeite();
        await screen.findByText('Neujahr');
        await user.click(screen.getByRole('button', { name: 'Eigenen Feiertag hinzufügen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Eigenen Feiertag hinzufügen' });
        await user.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));
        expect(await screen.findByText('Bitte ein Datum wählen.')).toBeVisible();
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);

        const datum = within(dialog).getByRole('button', { name: 'Datum' });
        await user.click(datum);
        await user.click(await screen.findByRole('button', { name: `01.${monat}.${jahr}` }));
        await user.type(within(dialog).getByRole('textbox', { name: 'Bezeichnung *' }), 'Betriebsfeier');
        await user.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));
        expect(await screen.findByText(`Am 01.${monat}.${jahr} ist schon ein Feiertag eingetragen.`)).toBeVisible();
        expect(screen.getByRole('dialog', { name: 'Eigenen Feiertag hinzufügen' })).toBeVisible();

        await user.click(within(dialog).getByRole('button', { name: /^Datum|01\./ }));
        await user.click(await screen.findByRole('button', { name: `02.${monat}.${jahr}` }));
        await user.click(within(dialog).getByRole('switch', { name: 'Halber Tag' }));
        await user.click(within(dialog).getByRole('button', { name: 'Hinzufügen' }));
        expect(await screen.findByText('Betriebsfeier')).toBeVisible();
        const post = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').at(-1);
        expect(JSON.parse(String(post?.[1]?.body))).toEqual({ datum: `${jahr}-${monat}-02`, bezeichnung: 'Betriebsfeier', halbTag: true });
        expect(screen.queryByRole('dialog', { name: 'Eigenen Feiertag hinzufügen' })).not.toBeInTheDocument();
    });

    it('löscht eigene Feiertage erst nach Rückfrage', async () => {
        const user = userEvent.setup();
        renderSeite();
        await user.click(await screen.findByRole('button', { name: 'Heiligabend löschen' }));
        const rueckfrage = await screen.findByRole('dialog', { name: 'Eigenen Feiertag löschen' });
        await user.click(within(rueckfrage).getByRole('button', { name: 'Abbrechen' }));
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

        await user.click(screen.getByRole('button', { name: 'Heiligabend löschen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Eigenen Feiertag löschen' })).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(screen.queryByText('Heiligabend')).not.toBeInTheDocument());
        expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/zeitverwaltung/feiertage/3' && init?.method === 'DELETE')).toBe(true);
    });

    it('zeigt Nicht-Admins die Liste nur lesend', async () => {
        auth.isAdmin = false;
        renderSeite();
        expect(await screen.findByText('Heiligabend')).toBeVisible();
        expect(screen.queryByRole('switch')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Eigenen Feiertag hinzufügen' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Feiertage neu generieren' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Mehr zu: Feiertage neu generieren' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /löschen/ })).not.toBeInTheDocument();
        expect(screen.getByText('Halber Tag')).toBeVisible();
        expect(screen.getByText(/Ändern können das nur Administratoren/)).toBeVisible();
    });
});
