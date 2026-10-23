import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmProvider } from '../../../ui/confirm-dialog';
import { AbteilungenVerwaltenKarte } from './AbteilungenVerwaltenKarte';

/**
 * Einstellungen → Abteilungen & Rechte: Karte „Abteilungen“ (anlegen, umbenennen, löschen).
 * Nur Dummy-Daten (DSGVO).
 */

const { toast } = vi.hoisted(() => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() } }));
vi.mock('../../../ui/toast', () => ({ useToast: () => toast }));

type Abteilung = { id: number; name: string };

function json(body: unknown, status = 200) {
    return new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

/** Kleiner Server im Speicher: GET liefert immer den aktuellen Stand. */
function stubbeServer(start: Abteilung[], antworten: Partial<Record<'POST' | 'PUT' | 'DELETE', () => Response>> = {}) {
    let liste = [...start];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const methode = init?.method ?? 'GET';
        if (methode === 'GET' && url === '/api/abteilungen') return json(liste);
        const eigene = antworten[methode as 'POST' | 'PUT' | 'DELETE'];
        if (eigene) return eigene();
        if (methode === 'POST' && url === '/api/abteilungen') {
            const neu = { id: 99, name: JSON.parse(String(init?.body)).name };
            liste = [...liste, neu];
            return json(neu, 201);
        }
        const id = Number(url.split('/').pop());
        if (methode === 'PUT') {
            const name = JSON.parse(String(init?.body)).name;
            liste = liste.map(a => (a.id === id ? { ...a, name } : a));
            return json({ id, name });
        }
        if (methode === 'DELETE') {
            liste = liste.filter(a => a.id !== id);
            return new Response(null, { status: 204 });
        }
        return json({ message: 'unbekannt' }, 404);
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function zeige(onGeaendert = vi.fn()) {
    render(<ConfirmProvider><AbteilungenVerwaltenKarte onGeaendert={onGeaendert} /></ConfirmProvider>);
    return onGeaendert;
}

const aufrufe = (fetchMock: ReturnType<typeof stubbeServer>, methode: string) =>
    fetchMock.mock.calls.filter(([, init]) => (init?.method ?? 'GET') === methode);

beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
});

describe('AbteilungenVerwaltenKarte', () => {
    it('lädt die Abteilungen und zeigt sie alphabetisch', async () => {
        stubbeServer([{ id: 2, name: 'Werkstatt' }, { id: 1, name: 'Büro' }]);
        zeige();
        const liste = await screen.findByRole('list', { name: 'Abteilungen' });
        expect(within(liste).getAllByRole('listitem').map(li => li.textContent)).toEqual(['Büro', 'Werkstatt']);
        expect(screen.getByRole('button', { name: '„Büro“ umbenennen' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: '„Büro“ löschen' })).toBeInTheDocument();
    });

    it('zeigt einen leeren Zustand und einen Ladefehler unterscheidbar', async () => {
        stubbeServer([]);
        zeige();
        expect(await screen.findByText('Noch keine Abteilung angelegt.')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('meldet einen Ladefehler mit „Erneut laden“', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({}, 500)));
        zeige();
        expect(await screen.findByRole('alert')).toHaveTextContent('Abteilungen konnten nicht geladen werden.');
        expect(screen.getByRole('button', { name: /Erneut laden/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Abteilung anlegen/ })).not.toBeInTheDocument();
    });

    it('sperrt „Abteilung anlegen“, solange kein Name eingetragen ist', async () => {
        const fetchMock = stubbeServer([]);
        zeige();
        const knopf = await screen.findByRole('button', { name: /Abteilung anlegen/ });
        expect(knopf).toBeDisabled();
        const user = userEvent.setup();
        await user.type(screen.getByRole('textbox', { name: 'Name der neuen Abteilung' }), '   ');
        expect(knopf).toBeDisabled();
        await user.keyboard('{Enter}');
        expect(toast.warning).toHaveBeenCalledWith('Bitte einen Namen für die Abteilung eingeben.');
        expect(aufrufe(fetchMock, 'POST')).toHaveLength(0);
    });

    it('legt eine Abteilung per Knopf an, zeigt sie und meldet die Änderung', async () => {
        const fetchMock = stubbeServer([{ id: 1, name: 'Büro' }]);
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.type(await screen.findByRole('textbox', { name: 'Name der neuen Abteilung' }), '  Montage ');
        await user.click(screen.getByRole('button', { name: /Abteilung anlegen/ }));

        expect(await screen.findByText('Montage')).toBeInTheDocument();
        const [url, init] = aufrufe(fetchMock, 'POST')[0];
        expect(url).toBe('/api/abteilungen');
        expect(JSON.parse(String(init?.body))).toEqual({ name: 'Montage' });
        expect(toast.success).toHaveBeenCalledWith('Abteilung „Montage“ angelegt.');
        expect(onGeaendert).toHaveBeenCalledTimes(1);
        expect(screen.getByRole('textbox', { name: 'Name der neuen Abteilung' })).toHaveValue('');
    });

    it('legt eine Abteilung mit Enter an', async () => {
        const fetchMock = stubbeServer([]);
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.type(await screen.findByRole('textbox', { name: 'Name der neuen Abteilung' }), 'Montage{Enter}');
        await waitFor(() => expect(onGeaendert).toHaveBeenCalledTimes(1));
        expect(aufrufe(fetchMock, 'POST')).toHaveLength(1);
    });

    it('zeigt die 409-Meldung des Servers wörtlich und meldet keine Änderung', async () => {
        stubbeServer([{ id: 4, name: 'Montage' }], {
            POST: () => json({ message: 'Eine Abteilung „Montage“ gibt es schon.' }, 409),
        });
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.type(await screen.findByRole('textbox', { name: 'Name der neuen Abteilung' }), 'Montage');
        await user.click(screen.getByRole('button', { name: /Abteilung anlegen/ }));
        await waitFor(() => expect(toast.warning).toHaveBeenCalledWith('Eine Abteilung „Montage“ gibt es schon.'));
        expect(toast.success).not.toHaveBeenCalled();
        expect(onGeaendert).not.toHaveBeenCalled();
        // Eingabe bleibt stehen, damit man sie korrigieren kann.
        expect(screen.getByRole('textbox', { name: 'Name der neuen Abteilung' })).toHaveValue('Montage');
    });

    it('nimmt bei Fehlern ohne JSON einen eigenen Text', async () => {
        stubbeServer([], { POST: () => new Response('', { status: 500 }) });
        zeige();
        const user = userEvent.setup();
        await user.type(await screen.findByRole('textbox', { name: 'Name der neuen Abteilung' }), 'Montage{Enter}');
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Abteilung konnte nicht angelegt werden.'));
    });

    it('meldet eine fehlende Verbindung beim Anlegen', async () => {
        stubbeServer([], { POST: () => { throw new TypeError('Failed to fetch'); } });
        zeige();
        const user = userEvent.setup();
        await user.type(await screen.findByRole('textbox', { name: 'Name der neuen Abteilung' }), 'Montage{Enter}');
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Verbindung zum Server fehlgeschlagen. Abteilung wurde nicht angelegt.'));
    });

    it('benennt per Enter um und meldet die Änderung', async () => {
        const fetchMock = stubbeServer([{ id: 4, name: 'Montage' }]);
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ umbenennen' }));
        const feld = screen.getByRole('textbox', { name: 'Neuer Name für „Montage“' });
        expect(feld).toHaveFocus();
        await user.clear(feld);
        await user.type(feld, 'Außenmontage{Enter}');

        expect(await screen.findByText('Außenmontage')).toBeInTheDocument();
        const [url, init] = aufrufe(fetchMock, 'PUT')[0];
        expect(url).toBe('/api/abteilungen/4');
        expect(JSON.parse(String(init?.body))).toEqual({ name: 'Außenmontage' });
        expect(toast.success).toHaveBeenCalledWith('„Montage“ heißt jetzt „Außenmontage“.');
        expect(onGeaendert).toHaveBeenCalledTimes(1);
        await waitFor(() => expect(screen.getByRole('button', { name: '„Außenmontage“ umbenennen' })).toHaveFocus());
    });

    it('bricht das Umbenennen mit Escape ab, ohne zu speichern', async () => {
        const fetchMock = stubbeServer([{ id: 4, name: 'Montage' }]);
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ umbenennen' }));
        await user.type(screen.getByRole('textbox', { name: 'Neuer Name für „Montage“' }), ' Süd{Escape}');

        expect(screen.queryByRole('textbox', { name: 'Neuer Name für „Montage“' })).not.toBeInTheDocument();
        expect(screen.getByText('Montage')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: '„Montage“ umbenennen' })).toHaveFocus();
        expect(aufrufe(fetchMock, 'PUT')).toHaveLength(0);
        expect(onGeaendert).not.toHaveBeenCalled();
    });

    it('speichert einen unveränderten Namen nicht und sperrt einen leeren', async () => {
        const fetchMock = stubbeServer([{ id: 4, name: 'Montage' }]);
        zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ umbenennen' }));
        const feld = screen.getByRole('textbox', { name: 'Neuer Name für „Montage“' });
        await user.clear(feld);
        expect(screen.getByRole('button', { name: /Speichern/ })).toBeDisabled();
        await user.type(feld, 'Montage');
        await user.click(screen.getByRole('button', { name: /Speichern/ }));
        expect(screen.queryByRole('textbox', { name: 'Neuer Name für „Montage“' })).not.toBeInTheDocument();
        expect(aufrufe(fetchMock, 'PUT')).toHaveLength(0);
    });

    it('zeigt die 409-Meldung beim Umbenennen und lässt das Feld offen', async () => {
        stubbeServer([{ id: 4, name: 'Montage' }, { id: 5, name: 'Werkstatt' }], {
            PUT: () => json({ message: 'Eine Abteilung „Werkstatt“ gibt es schon.' }, 409),
        });
        zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ umbenennen' }));
        const feld = screen.getByRole('textbox', { name: 'Neuer Name für „Montage“' });
        await user.clear(feld);
        await user.type(feld, 'Werkstatt{Enter}');
        await waitFor(() => expect(toast.warning).toHaveBeenCalledWith('Eine Abteilung „Werkstatt“ gibt es schon.'));
        expect(screen.getByRole('textbox', { name: 'Neuer Name für „Montage“' })).toHaveValue('Werkstatt');
    });

    it('löscht erst nach Bestätigung und meldet die Änderung', async () => {
        const fetchMock = stubbeServer([{ id: 4, name: 'Montage' }]);
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ löschen' }));

        const dialog = await screen.findByRole('dialog', { name: 'Abteilung löschen?' });
        expect(dialog).toHaveTextContent('„Montage“ wird gelöscht');
        expect(aufrufe(fetchMock, 'DELETE')).toHaveLength(0);
        await user.click(within(dialog).getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Abteilung „Montage“ gelöscht.'));
        expect(aufrufe(fetchMock, 'DELETE')[0][0]).toBe('/api/abteilungen/4');
        expect(onGeaendert).toHaveBeenCalledTimes(1);
        expect(await screen.findByText('Noch keine Abteilung angelegt.')).toBeInTheDocument();
    });

    it('löscht nicht, wenn die Bestätigung abgebrochen wird', async () => {
        const fetchMock = stubbeServer([{ id: 4, name: 'Montage' }]);
        zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ löschen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Abteilung löschen?' })).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Abteilung löschen?' })).not.toBeInTheDocument());
        expect(aufrufe(fetchMock, 'DELETE')).toHaveLength(0);
        expect(screen.getByText('Montage')).toBeInTheDocument();
    });

    it('zeigt die 409-Meldung, wenn noch Mitarbeiter oder Arbeitsgänge zugeordnet sind', async () => {
        const meldung = '„Montage“ kann nicht gelöscht werden: 3 Mitarbeiter und 5 Arbeitsgänge sind noch zugeordnet. Bitte zuerst beim Mitarbeiter bzw. unter Arbeitsgänge umstellen.';
        stubbeServer([{ id: 4, name: 'Montage' }], { DELETE: () => json({ message: meldung }, 409) });
        const onGeaendert = zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ löschen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Abteilung löschen?' })).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(toast.warning).toHaveBeenCalledWith(meldung));
        expect(onGeaendert).not.toHaveBeenCalled();
        expect(screen.getByText('Montage')).toBeInTheDocument();
    });

    it('zeigt 404 beim Löschen als Fehler', async () => {
        stubbeServer([{ id: 4, name: 'Montage' }], { DELETE: () => json({ message: 'Diese Abteilung gibt es nicht mehr.' }, 404) });
        zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: '„Montage“ löschen' }));
        await user.click(within(await screen.findByRole('dialog', { name: 'Abteilung löschen?' })).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Diese Abteilung gibt es nicht mehr.'));
    });

    it('erklärt Abteilungen im Info-Knopf neben der Überschrift', async () => {
        stubbeServer([]);
        zeige();
        const user = userEvent.setup();
        await user.click(await screen.findByRole('button', { name: 'Mehr zu: Abteilungen' }));
        const info = await screen.findByRole('dialog', { name: 'Abteilungen' });
        expect(within(info).getByText('Was passiert im Hintergrund?')).toBeInTheDocument();
        expect(within(info).getByText(/jeder Arbeitsgang gehört ebenfalls zu einer Abteilung/)).toBeInTheDocument();
        expect(within(info).getByText(/Bereits gebuchte Zeiten bleiben\s+unverändert/)).toBeInTheDocument();
    });
});
