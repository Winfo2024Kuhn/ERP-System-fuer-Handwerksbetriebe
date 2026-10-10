import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PostfachSettings } from './PostfachSettings';
import { ToastProvider } from '../../../ui/toast';
import { ConfirmProvider } from '../../../ui/confirm-dialog';
import type { PostfachDto } from '../../../../features/email/postfach';

const info: PostfachDto = {
    id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
    hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
    smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993, abrufAktiv: true,
    letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [],
};
const rechnungen: PostfachDto = {
    ...info, id: 4, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null, hauptpostfach: false,
    fuerGeschaeftsdokumente: true, benutzername: 'buchhaltung@musterbetrieb.example', sortierung: 10,
    letzterAbrufFehler: 'Anmeldung fehlgeschlagen – Passwort prüfen',
    zugewieseneBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
};
const max: PostfachDto = {
    ...info, id: 5, emailAdresse: 'max@musterbetrieb.example', anzeigename: null, hauptpostfach: false, aktiv: false,
    abrufAktiv: false, sortierung: 20,
};

type Antwort = { status?: number; body?: unknown };
type Handler = (url: string, init?: RequestInit) => Antwort | undefined;

function stubFetch(liste: PostfachDto[] | (() => PostfachDto[]), handler: Handler = () => undefined) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const eigene = handler(url, init);
        const antwort: Antwort = eigene
            ?? (url === '/api/postfaecher' && (!init?.method || init.method === 'GET')
                ? { body: typeof liste === 'function' ? liste() : liste }
                : { status: 404, body: { message: 'unbekannt' } });
        const status = antwort.status ?? 200;
        return new Response(status === 204 ? null : JSON.stringify(antwort.body ?? {}), {
            status, headers: { 'Content-Type': 'application/json' },
        });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const bodyVon = (fetchMock: ReturnType<typeof stubFetch>, url: string, method: string) => {
    const call = fetchMock.mock.calls.find(([u, init]) => String(u) === url && init?.method === method);
    return call ? JSON.parse(String(call[1]?.body)) : undefined;
};

function renderSettings(onSaved = vi.fn()) {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <PostfachSettings onSaved={onSaved} />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return onSaved;
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('PostfachSettings – Liste', () => {
    it('zeigt Adresse, Anzeigename, Schilder, Abruf-Status und Benutzer', async () => {
        stubFetch([info, rechnungen, max]);
        renderSettings();

        const liste = await screen.findByRole('list', { name: 'Postfächer' });
        const zeilen = within(liste).getAllByRole('listitem');
        expect(zeilen).toHaveLength(3);
        expect(within(zeilen[0]).getByText('Hauptpostfach')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('Musterbetrieb')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('Noch nicht abgerufen')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('Keinem Benutzer als eigenes Postfach zugeordnet')).toBeInTheDocument();
        expect(within(zeilen[1]).getByText('Rechnungen & Mahnungen')).toBeInTheDocument();
        expect(within(zeilen[1]).getByText('Anmeldung fehlgeschlagen – Passwort prüfen').parentElement).toHaveClass('text-rose-700');
        expect(within(zeilen[1]).getByText('Max Mustermann')).toBeInTheDocument();
        expect(within(zeilen[2]).getByText('Ausgeschaltet')).toBeInTheDocument();
        // Das Hauptpostfach lässt sich nicht löschen.
        expect(within(zeilen[0]).getByRole('button', { name: /löschen/ })).toBeDisabled();
        expect(within(zeilen[1]).getByRole('button', { name: /löschen/ })).toBeEnabled();
    });

    it('zeigt einen erkennbaren Leer-Zustand', async () => {
        stubFetch([]);
        renderSettings();
        expect(await screen.findByText('Noch kein Postfach eingerichtet.')).toBeInTheDocument();
    });

    it('meldet Ladefehler und lädt auf Knopfdruck neu', async () => {
        let fehler = true;
        stubFetch(() => [info], (url) => (url === '/api/postfaecher' && fehler
            ? { status: 403, body: { message: 'Nur für Administratoren.' } } : undefined));
        renderSettings();

        expect(await screen.findByText('Nur für Administratoren.', { selector: 'span' })).toBeInTheDocument();
        fehler = false;
        await userEvent.click(screen.getByRole('button', { name: /Erneut laden/ }));
        expect(await screen.findByText('info@musterbetrieb.example')).toBeInTheDocument();
    });

    it('meldet Netzwerkfehler beim Laden', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('')));
        renderSettings();
        expect(await screen.findByText('Postfächer konnten nicht geladen werden.', { selector: 'span' })).toBeInTheDocument();
    });

    it('nimmt bei unerwarteter Antwort eine leere Liste', async () => {
        stubFetch([], (url) => (url === '/api/postfaecher' ? { body: { kaputt: true } } : undefined));
        renderSettings();
        expect(await screen.findByText('Noch kein Postfach eingerichtet.')).toBeInTheDocument();
    });
});

describe('PostfachSettings – Anlegen', () => {
    it('belegt die Server-Einstellungen aus dem Hauptpostfach vor und speichert nur mit Adresse und Passwort', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info], (url, init) => (url === '/api/postfaecher' && init?.method === 'POST'
            ? { status: 201, body: { ...info, id: 9, hauptpostfach: false } } : undefined));
        const onSaved = renderSettings();

        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Neues Postfach')).toBeInTheDocument();
        // Bei einem weiteren Postfach ist der Haken "Hauptpostfach" aus.
        expect(within(dialog).getByRole('checkbox', { name: /^Hauptpostfach/ })).not.toBeChecked();
        expect(within(dialog).getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        expect(within(dialog).getByLabelText('Server für den Versand (SMTP)')).toHaveValue('mail.your-server.de');
        expect(within(dialog).getByLabelText('Server für den Abruf (IMAP)')).toHaveValue('mail.your-server.de');
        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        expect(within(dialog).getByText(/mail\.your-server\.de · Ports 465 \/ 993/)).toBeInTheDocument();

        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'max@musterbetrieb.example');
        await user.type(within(dialog).getByLabelText('Passwort'), 'geheim-123');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(onSaved).toHaveBeenCalled());
        expect(bodyVon(fetchMock, '/api/postfaecher', 'POST')).toEqual({
            emailAdresse: 'max@musterbetrieb.example',
            anzeigename: 'Musterbetrieb',
            aktiv: true,
            sortierung: 10,
            hauptpostfach: false,
            fuerGeschaeftsdokumente: false,
            benutzername: 'max@musterbetrieb.example',
            passwort: 'geheim-123',
            smtpHost: 'mail.your-server.de',
            smtpPort: 465,
            imapHost: 'mail.your-server.de',
            imapPort: 993,
        });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(await screen.findByText('Postfach angelegt.')).toBeInTheDocument();
    });

    it('macht das allererste Postfach automatisch zum Hauptpostfach mit Standard-Ports', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([], (url, init) => (url === '/api/postfaecher' && init?.method === 'POST'
            ? { status: 201, body: info } : undefined));
        renderSettings();

        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByRole('checkbox', { name: /^Hauptpostfach/ })).toBeChecked();
        // Hauptpostfach bleibt immer eingeschaltet.
        expect(within(dialog).getByRole('checkbox', { name: /^Eingeschaltet/ })).toBeDisabled();

        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'info@musterbetrieb.example');
        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        await user.type(within(dialog).getByLabelText('Benutzername'), 'login@musterbetrieb.example');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(bodyVon(fetchMock, '/api/postfaecher', 'POST')).toMatchObject({
            hauptpostfach: true, aktiv: true, benutzername: 'login@musterbetrieb.example', passwort: null,
            smtpHost: null, smtpPort: 465, imapHost: null, imapPort: 993, anzeigename: null,
        }));
    });

    it('prüft Adresse und Ports, bevor etwas gesendet wird', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info]);
        renderSettings();

        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'kein-at');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(within(dialog).getByRole('alert')).toHaveTextContent('gültige E-Mail-Adresse');

        await user.clear(within(dialog).getByLabelText(/E-Mail-Adresse/));
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'max@musterbetrieb.example');
        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        const smtpPort = within(dialog).getAllByLabelText('Port')[0];
        await user.clear(smtpPort);
        await user.type(smtpPort, '70000');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(within(dialog).getByRole('alert')).toHaveTextContent('Port für den Versand');

        await user.clear(smtpPort);
        await user.type(smtpPort, '465');
        const imapPort = within(dialog).getAllByLabelText('Port')[1];
        await user.clear(imapPort);
        await user.type(imapPort, '0');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(within(dialog).getByRole('alert')).toHaveTextContent('Port für den Abruf');

        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
    });

    it('zeigt die 400-Meldung des Servers im Dialog und als Toast', async () => {
        const user = userEvent.setup();
        stubFetch([info], (url, init) => (url === '/api/postfaecher' && init?.method === 'POST'
            ? { status: 400, body: { message: 'Diese Adresse gibt es schon als Postfach.' } } : undefined));
        renderSettings();

        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'info@musterbetrieb.example');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Diese Adresse gibt es schon als Postfach.');
        expect(screen.getAllByText('Diese Adresse gibt es schon als Postfach.').length).toBeGreaterThan(1);
        // Weitertippen nimmt den Hinweis weg.
        await user.type(within(dialog).getByLabelText('Angezeigter Name'), 'x');
        expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
    });

    it('meldet Netzwerkfehler beim Speichern', async () => {
        const user = userEvent.setup();
        stubFetch([info], (url, init) => {
            if (url === '/api/postfaecher' && init?.method === 'POST') throw new Error('offline');
            return undefined;
        });
        renderSettings();
        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'max@musterbetrieb.example');
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Verbindung zum Server fehlgeschlagen');
    });

    it('schließt mit Abbrechen ohne zu speichern', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info]);
        renderSettings();
        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /Abbrechen/ }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(fetchMock.mock.calls.every(([, init]) => !init?.method)).toBe(true);
    });
});

describe('PostfachSettings – Bearbeiten', () => {
    it('lässt ein leeres Passwort unverändert und schickt PUT', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info, rechnungen], (url, init) => (url === '/api/postfaecher/4' && init?.method === 'PUT'
            ? { body: rechnungen } : undefined));
        const onSaved = renderSettings();

        await user.click(await screen.findByRole('button', { name: 'Postfach rechnungen@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Postfach bearbeiten')).toBeInTheDocument();
        expect(within(dialog).getByLabelText(/Passwort/, { selector: 'input' })).toHaveAttribute('placeholder', '✓ gesetzt – leer lassen = unverändert');
        expect(within(dialog).getByRole('checkbox', { name: /Für Rechnungen & Mahnungen/ })).toBeChecked();
        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        // Abweichender Benutzername bleibt sichtbar.
        expect(within(dialog).getByLabelText('Benutzername')).toHaveValue('buchhaltung@musterbetrieb.example');

        await user.click(within(dialog).getByRole('checkbox', { name: /Für Rechnungen & Mahnungen/ }));
        await user.click(within(dialog).getByRole('checkbox', { name: /^Hauptpostfach/ }));
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(onSaved).toHaveBeenCalled());
        expect(bodyVon(fetchMock, '/api/postfaecher/4', 'PUT')).toMatchObject({
            passwort: null, fuerGeschaeftsdokumente: false, hauptpostfach: true, aktiv: true, sortierung: 10,
            benutzername: 'buchhaltung@musterbetrieb.example',
        });
        expect(await screen.findByText('Postfach gespeichert.')).toBeInTheDocument();
    });

    it('sperrt beim Hauptpostfach das Abwählen und Ausschalten', async () => {
        const user = userEvent.setup();
        stubFetch([info]);
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach info@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByRole('checkbox', { name: /^Hauptpostfach/ })).toBeDisabled();
        expect(within(dialog).getByRole('checkbox', { name: /^Eingeschaltet/ })).toBeDisabled();
        expect(within(dialog).getByText(/ein anderes Postfach als Hauptpostfach markieren/)).toBeInTheDocument();
        // Benutzername gleich Adresse bleibt leer (Standard).
        await user.click(within(dialog).getByRole('button', { name: /Server-Einstellungen/ }));
        expect(within(dialog).getByLabelText('Benutzername')).toHaveValue('');
    });

    it('schaltet ein Nebenpostfach aus', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info, { ...max, aktiv: true, smtpHost: null, smtpPort: null, imapHost: null, imapPort: null, benutzername: null, anzeigename: 'Max' }],
            (url, init) => (url === '/api/postfaecher/5' && init?.method === 'PUT' ? { body: max } : undefined));
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach max@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('checkbox', { name: /^Eingeschaltet/ }));
        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(bodyVon(fetchMock, '/api/postfaecher/5', 'PUT')).toMatchObject({
            aktiv: false, smtpPort: null, imapPort: null, anzeigename: 'Max',
        }));
    });
});

describe('PostfachSettings – Verbindung testen', () => {
    it('prüft mit gespeichertem Passwort und zeigt Versand- und Abruf-Ergebnis', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info], (url) => (url === '/api/postfaecher/test'
            ? { body: { versandOk: true, abrufOk: true, message: 'Test-Mail verschickt.' } } : undefined));
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach info@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        await user.type(within(dialog).getByLabelText(/Test-Mail an/), 'max.mustermann@example.org');
        await user.click(within(dialog).getByRole('button', { name: /Verbindung testen/ }));

        expect(await within(dialog).findByRole('status')).toHaveTextContent('Versand: ok · Abruf: ok – Test-Mail verschickt.');
        expect(bodyVon(fetchMock, '/api/postfaecher/test', 'POST')).toEqual({
            id: 3, benutzername: 'info@musterbetrieb.example', passwort: null, smtpHost: 'mail.your-server.de',
            smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993, testEmpfaenger: 'max.mustermann@example.org',
        });
    });

    it('zeigt einen Teilfehler und braucht bei neuen Postfächern erst ein Passwort', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info], (url) => (url === '/api/postfaecher/test'
            ? { body: { versandOk: true, abrufOk: false, message: '' } } : undefined));
        renderSettings();
        await user.click(await screen.findByRole('button', { name: /Neues Postfach/ }));
        const dialog = await screen.findByRole('dialog');
        const testKnopf = within(dialog).getByRole('button', { name: /Verbindung testen/ });
        expect(testKnopf).toBeDisabled();
        expect(testKnopf).toHaveAttribute('title', 'Bitte zuerst die E-Mail-Adresse eintragen.');
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'max@musterbetrieb.example');
        expect(testKnopf).toHaveAttribute('title', 'Bitte zuerst das Passwort eintragen.');
        await user.type(within(dialog).getByLabelText('Passwort'), 'geheim');
        await user.click(testKnopf);

        expect(await within(dialog).findByRole('status')).toHaveTextContent('Versand: ok · Abruf: fehlgeschlagen');
        expect(bodyVon(fetchMock, '/api/postfaecher/test', 'POST')).toMatchObject({ id: null, passwort: 'geheim', testEmpfaenger: null });
    });

    it('zeigt Fehlerantworten und Netzwerkfehler des Tests', async () => {
        const user = userEvent.setup();
        let modus: 'fehler' | 'netz' = 'fehler';
        stubFetch([info], (url) => {
            if (url !== '/api/postfaecher/test') return undefined;
            if (modus === 'netz') throw new Error('offline');
            return { status: 400, body: { message: 'Server nicht erreichbar.' } };
        });
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach info@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('button', { name: /Verbindung testen/ }));
        expect(await within(dialog).findByRole('status')).toHaveTextContent('Versand: fehlgeschlagen · Abruf: fehlgeschlagen – Server nicht erreichbar.');

        modus = 'netz';
        await user.click(within(dialog).getByRole('button', { name: /Verbindung testen/ }));
        await waitFor(() => expect(within(dialog).getByRole('status')).toHaveTextContent('Verbindung zum Server fehlgeschlagen.'));
    });

    it('prüft vor dem Test die Eingaben', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info]);
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach info@musterbetrieb.example bearbeiten' }));
        const dialog = await screen.findByRole('dialog');
        await user.clear(within(dialog).getByLabelText(/E-Mail-Adresse/));
        await user.type(within(dialog).getByLabelText(/E-Mail-Adresse/), 'falsch@');
        await user.click(within(dialog).getByRole('button', { name: /Verbindung testen/ }));
        expect(within(dialog).getByRole('alert')).toHaveTextContent('gültige E-Mail-Adresse');
        expect(fetchMock.mock.calls.some(([url]) => String(url) === '/api/postfaecher/test')).toBe(false);
    });
});

describe('PostfachSettings – Löschen', () => {
    it('löscht nach Bestätigung und lädt neu', async () => {
        const user = userEvent.setup();
        let liste = [info, max];
        const fetchMock = stubFetch(() => liste, (url, init) => {
            if (url === '/api/postfaecher/5' && init?.method === 'DELETE') { liste = [info]; return { status: 204 }; }
            return undefined;
        });
        const onSaved = renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach max@musterbetrieb.example löschen' }));
        const bestaetigung = await screen.findByRole('alertdialog').catch(() => screen.findByRole('dialog'));
        expect(bestaetigung).toHaveTextContent('max@musterbetrieb.example wirklich löschen');
        await user.click(within(bestaetigung).getByRole('button', { name: 'Löschen' }));

        await waitFor(() => expect(screen.queryByText('max@musterbetrieb.example')).not.toBeInTheDocument());
        expect(onSaved).toHaveBeenCalled();
        expect(fetchMock.mock.calls.some(([url, init]) => String(url) === '/api/postfaecher/5' && init?.method === 'DELETE')).toBe(true);
        expect(await screen.findByText('Postfach gelöscht.')).toBeInTheDocument();
    });

    it('löscht nicht ohne Bestätigung', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch([info, max]);
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach max@musterbetrieb.example löschen' }));
        const bestaetigung = await screen.findByRole('alertdialog').catch(() => screen.findByRole('dialog'));
        await user.click(within(bestaetigung).getByRole('button', { name: 'Abbrechen' }));
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    });

    it('zeigt die 400-Meldung, wenn das Postfach schon Mails hat, und Netzwerkfehler', async () => {
        const user = userEvent.setup();
        let netz = false;
        stubFetch([info, max], (url, init) => {
            if (url !== '/api/postfaecher/5' || init?.method !== 'DELETE') return undefined;
            if (netz) throw new Error('offline');
            return { status: 400, body: { message: 'Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten.' } };
        });
        renderSettings();
        await user.click(await screen.findByRole('button', { name: 'Postfach max@musterbetrieb.example löschen' }));
        let bestaetigung = await screen.findByRole('alertdialog').catch(() => screen.findByRole('dialog'));
        await user.click(within(bestaetigung).getByRole('button', { name: 'Löschen' }));
        expect(await screen.findByText('Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten.')).toBeInTheDocument();

        netz = true;
        await user.click(screen.getByRole('button', { name: 'Postfach max@musterbetrieb.example löschen' }));
        bestaetigung = await screen.findByRole('alertdialog').catch(() => screen.findByRole('dialog'));
        await user.click(within(bestaetigung).getByRole('button', { name: 'Löschen' }));
        expect(await screen.findByText(/Postfach wurde nicht gelöscht/)).toBeInTheDocument();
    });
});
