import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PostfachSichtbarkeitKarte } from './PostfachSichtbarkeitKarte';
import { ToastProvider } from '../../../ui/toast';
import type { PostfachDto } from '../../../../features/email/postfach';

/**
 * Einstellungen → Berechtigungen: „E-Mail-Postfächer – wer sieht welches Postfach?“
 * Nur Dummy-Daten (DSGVO).
 */

const info: PostfachDto = {
    id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
    hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
    smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993, abrufAktiv: true,
    letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [],
    sichtbarFuerAlle: true, sichtbarFuerAbteilungen: [], sichtbarFuerBenutzer: [], laeuftAus: false,
};
const max: PostfachDto = {
    ...info, id: 5, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann', hauptpostfach: false, sortierung: 10,
    zugewieseneBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
    sichtbarFuerAlle: false,
    sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }],
    sichtbarFuerBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
};
const werkstatt: PostfachDto = {
    ...info, id: 6, emailAdresse: 'werkstatt@musterbetrieb.example', anzeigename: null, hauptpostfach: false, sortierung: 20,
    aktiv: false, abrufAktiv: false,
};

const ABTEILUNGEN = [
    { abteilungId: 3, abteilungName: 'Werkstatt', berechtigungen: [] },
    { abteilungId: 2, abteilungName: 'Büro', berechtigungen: [] },
    { abteilungId: 4, abteilungName: 'Montage', berechtigungen: [] },
];
const BENUTZER = [
    { id: 7, displayName: 'Max Mustermann', active: true },
    { id: 8, displayName: 'Erika Musterfrau', active: true },
    { id: 11, displayName: 'Moritz Muster', active: false },
];

type Antwort = { status?: number; body?: unknown };
type Handler = (url: string, init?: RequestInit) => Antwort | Promise<Antwort> | undefined;

function stubFetch(liste: PostfachDto[], handler: Handler = () => undefined) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const eigene = await handler(url, init);
        const antwort: Antwort = eigene
            ?? (url === '/api/postfaecher' ? { body: liste }
                : url === '/api/abteilungen/berechtigungen' ? { body: ABTEILUNGEN }
                    : url === '/api/frontend-users' ? { body: BENUTZER }
                        : { status: 404, body: { message: 'unbekannt' } });
        return new Response(typeof antwort.body === 'string' ? antwort.body : JSON.stringify(antwort.body ?? {}), {
            status: antwort.status ?? 200, headers: { 'Content-Type': 'application/json' },
        });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const bodyVon = (fetchMock: ReturnType<typeof stubFetch>, url: string) => {
    const call = fetchMock.mock.calls.find(([u, init]) => String(u) === url && init?.method === 'PUT');
    return call ? JSON.parse(String(call[1]?.body)) : undefined;
};
const aufrufe = (fetchMock: ReturnType<typeof stubFetch>, url: string) =>
    fetchMock.mock.calls.filter(([u]) => String(u) === url).length;

/** Der Fehlerkasten der Karte (der Toast hat ebenfalls role="alert"). */
const ladefehlerKasten = async () => (await screen.findByRole('button', { name: /Erneut laden/ })).closest('[role="alert"]');

const renderKarte = () => render(<ToastProvider><PostfachSichtbarkeitKarte /></ToastProvider>);

async function zeile(adresse: string) {
    const liste = await screen.findByRole('list', { name: 'Sichtbarkeit der Postfächer' });
    return within(liste).getAllByRole('listitem').find(li => li.textContent?.includes(adresse))!;
}
const gruppe = (el: HTMLElement, name: 'Abteilungen' | 'Benutzer') => within(el).getByRole('group', { name });
const speichernKnopf = (el: HTMLElement, adresse: string) =>
    within(el).getByRole('button', { name: `Sichtbarkeit für ${adresse} speichern` });

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('PostfachSichtbarkeitKarte – Anzeige', () => {
    it('zeigt das Hauptpostfach nur als Text, ausgeschaltete gekennzeichnet und bearbeitbar', async () => {
        stubFetch([info, max, werkstatt]);
        renderKarte();

        expect(screen.getByText('E-Mail-Postfächer – wer sieht welches Postfach?')).toBeInTheDocument();
        const haupt = await zeile('info@musterbetrieb.example');
        expect(within(haupt).getByText('Das Hauptpostfach sieht jeder im Betrieb.')).toBeInTheDocument();
        expect(within(haupt).getByText('Hauptpostfach')).toBeInTheDocument();
        expect(within(haupt).queryByRole('radio')).not.toBeInTheDocument();
        expect(within(haupt).queryByRole('button')).not.toBeInTheDocument();

        const aus = await zeile('werkstatt@musterbetrieb.example');
        expect(within(aus).getByText('ausgeschaltet')).toHaveAttribute('title',
            'Solange es ausgeschaltet ist, sehen nur Admins seine Mails. Die Auswahl gilt wieder, sobald es eingeschaltet ist.');
        expect(screen.getByText(/Mails, die einem Projekt, einer Anfrage oder einem Lieferanten zugeordnet sind, sieht jeder in diesen Reitern\./))
            .toBeInTheDocument();
        expect(within(aus).getByRole('radio', { name: /Alle im Betrieb/ })).toBeChecked();
        expect(within(aus).getByRole('radio', { name: /Alle im Betrieb/ })).toBeEnabled();
    });

    it('zeigt auslaufende Postfächer mit Schild und lässt sie bearbeiten', async () => {
        stubFetch([info, { ...max, laeuftAus: true }]);
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        expect(within(z).getByText('läuft aus')).toHaveAttribute('title', expect.stringMatching(/^Läuft aus: Mails kommen weiter an/));
        expect(within(z).getByRole('radio', { name: /Alle im Betrieb/ })).toBeEnabled();
        expect(within(await zeile('info@musterbetrieb.example')).queryByText('läuft aus')).not.toBeInTheDocument();
    });

    it('belegt die gespeicherten Häkchen vor; Speichern ist ohne Änderung gesperrt und sagt warum', async () => {
        stubFetch([info, max]);
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');

        expect(within(z).getByRole('radio', { name: /Nur bestimmte/ })).toBeChecked();
        expect(within(z).getByText('Der Inhaber des Postfachs und Admins sehen es immer.')).toBeInTheDocument();
        const abteilungen = gruppe(z, 'Abteilungen');
        await within(abteilungen).findByRole('checkbox', { name: 'Werkstatt' });
        expect(within(abteilungen).getAllByRole('checkbox').map(c => c.closest('label')?.textContent))
            .toEqual(['Büro', 'Montage', 'Werkstatt']);
        expect(within(abteilungen).getByRole('checkbox', { name: 'Büro' })).toBeChecked();
        expect(await within(gruppe(z, 'Benutzer')).findByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
        // Ausgeschaltete Benutzer nur, wenn angehakt.
        expect(within(gruppe(z, 'Benutzer')).queryByText('Moritz Muster')).not.toBeInTheDocument();

        const knopf = speichernKnopf(z, 'max@musterbetrieb.example');
        expect(knopf).toBeDisabled();
        expect(knopf).toHaveAttribute('title', 'Noch nichts geändert – erst Auswahl oder Häkchen ändern.');
    });

    it('lädt Abteilungen und Benutzer nur, wenn ein Postfach auf „Nur bestimmte“ steht – und nur einmal', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info, werkstatt, { ...werkstatt, id: 7, emailAdresse: 'lager@musterbetrieb.example', aktiv: true }]);
        renderKarte();
        const lager = await zeile('lager@musterbetrieb.example');
        expect(aufrufe(fetchMock, '/api/abteilungen/berechtigungen')).toBe(0);

        await user.click(within(lager).getByRole('radio', { name: /Nur bestimmte/ }));
        expect(await within(gruppe(lager, 'Abteilungen')).findByRole('checkbox', { name: 'Büro' })).toBeInTheDocument();
        const aus = await zeile('werkstatt@musterbetrieb.example');
        await user.click(within(aus).getByRole('radio', { name: /Nur bestimmte/ }));
        expect(within(gruppe(aus, 'Abteilungen')).getByRole('checkbox', { name: 'Büro' })).toBeInTheDocument();
        await user.click(within(lager).getByRole('radio', { name: /Alle im Betrieb/ }));
        await user.click(within(lager).getByRole('radio', { name: /Nur bestimmte/ }));
        expect(aufrufe(fetchMock, '/api/abteilungen/berechtigungen')).toBe(1);
        expect(aufrufe(fetchMock, '/api/frontend-users')).toBe(1);
        expect(within(gruppe(lager, 'Abteilungen')).getByText('keine gewählt')).toBeInTheDocument();
        expect(within(lager).getByText(/Noch nichts angehakt – dann sehen es nur diese\./)).toBeInTheDocument();
    });
});

describe('PostfachSichtbarkeitKarte – Postfach für Rechnungen & Mahnungen', () => {
    const rechnungen: PostfachDto = {
        ...info, id: 4, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null, hauptpostfach: false,
        fuerGeschaeftsdokumente: true, sortierung: 5,
        // Selbst wenn das Backend (fälschlich) „Nur bestimmte“ liefert: keine Auswahl, keine Listen.
        sichtbarFuerAlle: false, sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }],
    };

    it('zeigt wie beim Hauptpostfach nur den Text – ohne Auswahl, ohne Speichern, ohne Listen', async () => {
        const fetchMock = stubFetch([info, rechnungen]);
        renderKarte();
        const z = await zeile('rechnungen@musterbetrieb.example');
        expect(within(z).getByText('Rechnungen & Mahnungen')).toBeInTheDocument();
        expect(within(z).getByText('Das Postfach für Rechnungen & Mahnungen sieht jeder im Betrieb.')).toBeInTheDocument();
        expect(within(z).queryByRole('radio')).not.toBeInTheDocument();
        expect(within(z).queryByRole('button')).not.toBeInTheDocument();
        expect(aufrufe(fetchMock, '/api/abteilungen/berechtigungen')).toBe(0);
        expect(aufrufe(fetchMock, '/api/frontend-users')).toBe(0);
    });

    it('nimmt den Hauptpostfach-Text, wenn ein Postfach beides ist', async () => {
        stubFetch([{ ...info, fuerGeschaeftsdokumente: true }]);
        renderKarte();
        const z = await zeile('info@musterbetrieb.example');
        expect(within(z).getByText('Das Hauptpostfach sieht jeder im Betrieb.')).toBeInTheDocument();
        expect(within(z).queryByText(/Postfach für Rechnungen & Mahnungen sieht jeder/)).not.toBeInTheDocument();
        expect(within(z).getByText('Hauptpostfach')).toBeInTheDocument();
        expect(within(z).getByText('Rechnungen & Mahnungen')).toBeInTheDocument();
    });

    it('erklärt beim ausgeschalteten Rechnungs-Postfach nur, dass dann nur Admins es sehen', async () => {
        stubFetch([info, { ...rechnungen, aktiv: false }]);
        renderKarte();
        const z = await zeile('rechnungen@musterbetrieb.example');
        expect(within(z).getByText('ausgeschaltet')).toHaveAttribute('title', 'Solange es ausgeschaltet ist, sehen nur Admins seine Mails.');
    });
});

describe('PostfachSichtbarkeitKarte – Speichern', () => {
    it('speichert die geänderten Häkchen über den eigenen Endpoint und übernimmt die Antwort', async () => {
        const user = userEvent.setup();
        const gespeichert: PostfachDto = {
            ...max, sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }, { id: 3, name: 'Werkstatt' }],
            sichtbarFuerBenutzer: [{ id: 8, displayName: 'Erika Musterfrau' }],
        };
        const fetchMock = stubFetch([info, max], (url, init) => (url === '/api/postfaecher/5/sichtbarkeit' && init?.method === 'PUT'
            ? { body: gespeichert } : undefined));
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        const abteilungen = gruppe(z, 'Abteilungen');
        const benutzer = gruppe(z, 'Benutzer');
        await user.click(await within(abteilungen).findByRole('checkbox', { name: 'Werkstatt' }));
        await user.click(await within(benutzer).findByRole('checkbox', { name: 'Max Mustermann' }));
        await user.click(within(benutzer).getByRole('checkbox', { name: 'Erika Musterfrau' }));
        expect(within(abteilungen).getByText('2 gewählt')).toBeInTheDocument();

        const knopf = speichernKnopf(z, 'max@musterbetrieb.example');
        expect(knopf).toBeEnabled();
        expect(knopf).not.toHaveAttribute('title');
        await user.click(knopf);

        await waitFor(() => expect(bodyVon(fetchMock, '/api/postfaecher/5/sichtbarkeit')).toEqual({
            sichtbarFuerAlle: false, abteilungIds: [2, 3], benutzerIds: [8],
        }));
        expect(await screen.findByText('Sichtbarkeit für max@musterbetrieb.example gespeichert.')).toBeInTheDocument();
        // Gespeicherter Stand = Antwort → nichts mehr zu speichern.
        await waitFor(() => expect(speichernKnopf(z, 'max@musterbetrieb.example')).toBeDisabled());
        expect(within(abteilungen).getByRole('checkbox', { name: 'Werkstatt' })).toBeChecked();
    });

    it('lädt neu, wenn die Antwort nach dem Speichern kein Postfach ist', async () => {
        const user = userEvent.setup();
        let gespeichert = false;
        const fetchMock = stubFetch([info, max], (url, init) => {
            if (url === '/api/postfaecher/5/sichtbarkeit' && init?.method === 'PUT') {
                gespeichert = true;
                return { body: { kaputt: true } };
            }
            if (url === '/api/postfaecher' && gespeichert) return { body: [info, { ...max, sichtbarFuerAlle: true }] };
            return undefined;
        });
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        await user.click(within(z).getByRole('radio', { name: /Alle im Betrieb/ }));
        await user.click(speichernKnopf(z, 'max@musterbetrieb.example'));

        expect(await screen.findByText('Sichtbarkeit für max@musterbetrieb.example gespeichert.')).toBeInTheDocument();
        await waitFor(() => expect(aufrufe(fetchMock, '/api/postfaecher')).toBe(2));
        const neu = await zeile('max@musterbetrieb.example');
        await waitFor(() => expect(within(neu).getByRole('radio', { name: /Alle im Betrieb/ })).toBeChecked());
        expect(speichernKnopf(neu, 'max@musterbetrieb.example')).toBeDisabled();
    });

    it('zurückgenommene Änderung sperrt den Knopf wieder (Reihenfolge egal)', async () => {
        const user = userEvent.setup();
        stubFetch([info, max]);
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        const abteilungen = gruppe(z, 'Abteilungen');
        await user.click(await within(abteilungen).findByRole('checkbox', { name: 'Büro' }));
        expect(speichernKnopf(z, 'max@musterbetrieb.example')).toBeEnabled();
        await user.click(within(abteilungen).getByRole('checkbox', { name: 'Büro' }));
        expect(speichernKnopf(z, 'max@musterbetrieb.example')).toBeDisabled();
        await user.click(within(z).getByRole('radio', { name: /Alle im Betrieb/ }));
        expect(speichernKnopf(z, 'max@musterbetrieb.example')).toBeEnabled();
    });

    it('zeigt 400-Meldungen als Toast und an der Zeile; neue Änderung blendet den Hinweis aus', async () => {
        const user = userEvent.setup();
        stubFetch([info, max], (url) => (url === '/api/postfaecher/5/sichtbarkeit'
            ? { status: 400, body: { message: 'Diese Abteilung gibt es nicht (mehr).' } } : undefined));
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        await user.click(await within(gruppe(z, 'Abteilungen')).findByRole('checkbox', { name: 'Montage' }));
        await user.click(speichernKnopf(z, 'max@musterbetrieb.example'));

        expect(await within(z).findByRole('alert')).toHaveTextContent('Diese Abteilung gibt es nicht (mehr).');
        expect(screen.getAllByText('Diese Abteilung gibt es nicht (mehr).')).toHaveLength(2);
        // Die Änderung bleibt stehen – man kann es erneut versuchen.
        expect(speichernKnopf(z, 'max@musterbetrieb.example')).toBeEnabled();
        await user.click(within(gruppe(z, 'Abteilungen')).getByRole('checkbox', { name: 'Werkstatt' }));
        expect(within(z).queryByRole('alert')).not.toBeInTheDocument();
    });

    it('meldet 404 ohne Text und Netzwerkfehler verständlich', async () => {
        const user = userEvent.setup();
        let netzwerk = false;
        stubFetch([info, { ...max, sichtbarFuerAlle: true }], (url) => {
            if (url !== '/api/postfaecher/5/sichtbarkeit') return undefined;
            if (netzwerk) throw new TypeError('Failed to fetch');
            return { status: 404, body: '' };
        });
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        await user.click(within(z).getByRole('radio', { name: /Nur bestimmte/ }));
        await user.click(speichernKnopf(z, 'max@musterbetrieb.example'));
        expect(await within(z).findByRole('alert')).toHaveTextContent('Sichtbarkeit konnte nicht gespeichert werden.');

        netzwerk = true;
        await user.click(speichernKnopf(z, 'max@musterbetrieb.example'));
        expect(await within(z).findByText('Verbindung zum Server fehlgeschlagen. Sichtbarkeit wurde nicht gespeichert.')).toBeInTheDocument();
    });

    it('sperrt während des Speicherns alle Speichern-Knöpfe und die Häkchen der Zeile', async () => {
        const user = userEvent.setup();
        let freigeben: () => void = () => {};
        const zweites = { ...max, id: 9, emailAdresse: 'lager@musterbetrieb.example' };
        stubFetch([info, max, zweites], (url) => (url === '/api/postfaecher/5/sichtbarkeit'
            ? new Promise<Antwort>(resolve => { freigeben = () => resolve({ body: { ...max, sichtbarFuerAlle: true } }); })
            : undefined));
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        const lager = await zeile('lager@musterbetrieb.example');
        await user.click(within(z).getByRole('radio', { name: /Alle im Betrieb/ }));
        await user.click(within(lager).getByRole('radio', { name: /Alle im Betrieb/ }));
        await user.click(speichernKnopf(z, 'max@musterbetrieb.example'));

        expect(within(z).getByRole('button', { name: /speichern/ })).toHaveTextContent('Wird gespeichert …');
        expect(within(z).getByRole('radio', { name: /Nur bestimmte/ })).toBeDisabled();
        expect(speichernKnopf(lager, 'lager@musterbetrieb.example')).toBeDisabled();
        freigeben();
        await waitFor(() => expect(speichernKnopf(lager, 'lager@musterbetrieb.example')).toBeEnabled());
    });
});

describe('PostfachSichtbarkeitKarte – Lade-, Leer- und Fehlerzustände', () => {
    it('zeigt beim Laden Platzhalter statt eines leeren Kastens', () => {
        vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
        renderKarte();
        expect(screen.getByLabelText('Postfächer werden geladen')).toHaveAttribute('aria-busy', 'true');
    });

    it('verweist ohne Postfächer auf Einstellungen → E-Mail', async () => {
        stubFetch([], (url) => (url === '/api/postfaecher' ? { body: { kaputt: true } } : undefined));
        renderKarte();
        expect(await screen.findByText('Noch kein Postfach eingerichtet.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Einstellungen → E-Mail' })).toHaveAttribute('href', '#email');
    });

    it('meldet Ladefehler mit Text des Servers und lädt auf Knopfdruck neu', async () => {
        const user = userEvent.setup();
        let kaputt = true;
        stubFetch([info], (url) => (url === '/api/postfaecher' && kaputt
            ? { status: 403, body: { message: 'Nur für Administratoren.' } } : undefined));
        renderKarte();
        expect(await ladefehlerKasten()).toHaveTextContent('Nur für Administratoren.');
        expect(screen.getAllByText('Nur für Administratoren.')).toHaveLength(2);
        kaputt = false;
        await user.click(screen.getByRole('button', { name: /Erneut laden/ }));
        expect(await screen.findByText('Das Hauptpostfach sieht jeder im Betrieb.')).toBeInTheDocument();
    });

    it('meldet Netzwerkfehler beim Laden mit eigenem Text', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('')));
        renderKarte();
        expect(await ladefehlerKasten()).toHaveTextContent('Postfächer konnten nicht geladen werden.');
    });

    it('meldet Fehler der Häkchen-Listen, behält die Auswahl und lädt neu', async () => {
        const user = userEvent.setup();
        let kaputt = true;
        const fetchMock = stubFetch([info, max], (url) => (url === '/api/abteilungen/berechtigungen' && kaputt
            ? { status: 500, body: {} } : undefined));
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        const abteilungen = gruppe(z, 'Abteilungen');
        expect(await within(abteilungen).findByRole('alert'))
            .toHaveTextContent('Abteilungen konnten nicht geladen werden. Die bisherige Auswahl bleibt beim Speichern erhalten.');
        expect(await within(gruppe(z, 'Benutzer')).findByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();

        kaputt = false;
        await user.click(within(abteilungen).getByRole('button', { name: /Erneut laden/ }));
        expect(await within(abteilungen).findByRole('checkbox', { name: 'Büro' })).toBeChecked();
        expect(aufrufe(fetchMock, '/api/abteilungen/berechtigungen')).toBe(2);
    });

    it('zeigt den Fehler ohne Zusatz, wenn noch nichts gewählt war', async () => {
        const user = userEvent.setup();
        stubFetch([info, { ...max, sichtbarFuerAlle: true, sichtbarFuerAbteilungen: [], sichtbarFuerBenutzer: [] }],
            (url) => (url === '/api/frontend-users' ? { status: 500, body: {} } : undefined));
        renderKarte();
        const z = await zeile('max@musterbetrieb.example');
        await user.click(within(z).getByRole('radio', { name: /Nur bestimmte/ }));
        expect(await within(gruppe(z, 'Benutzer')).findByRole('alert')).toHaveTextContent(/^Benutzer konnten nicht geladen werden\.Erneut laden$/);
    });
});
