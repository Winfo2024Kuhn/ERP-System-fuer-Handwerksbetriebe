import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailSettings from './EmailSettings';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import { AuthProvider } from '../auth/AuthContext';

/**
 * Abwesenheitsnotizen pro Postfach: Auswahl der Postfächer, Vorauswahl,
 * Pflicht-Hinweis, gesendeter Body und Fehlermeldungen. Nur Dummy-Daten (DSGVO).
 */

const MAX = { id: 7, emailAdresse: 'max.mustermann@example.com', anzeigename: 'Max Mustermann' };
const INFO = { id: 3, emailAdresse: 'info@example.com', anzeigename: 'Musterbetrieb' };

const WAEHLBAR = [
    { ...MAX, eigenes: true },
    { ...INFO, eigenes: false },
];

const NOTIZ = {
    id: 11,
    title: 'Sommerurlaub',
    startAt: '2099-08-01',
    endAt: '2099-08-14',
    active: true,
    subjectTemplate: 'Automatische Antwort: {{subject}}',
    bodyTemplate: '<p>Bin im Urlaub.</p>',
    signature: null,
    postfaecher: [MAX, INFO],
};

interface StubOptionen {
    notizen?: unknown[];
    waehlbar?: unknown[] | { status: number; body: unknown };
    speichern?: { status: number; body: unknown };
    loeschen?: { status: number; body: unknown };
}

function antwort(body: unknown, status = 200): Response {
    return new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

function stubbe(optionen: StubOptionen = {}) {
    const mock = vi.fn(async (eingabe: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(eingabe), 'http://localhost');
        const methode = init?.method ?? 'GET';
        if (url.pathname === '/api/email/signatures') return antwort([]);
        if (url.pathname === '/api/email/signatures/default') return new Response(null, { status: 204 });
        if (url.pathname === '/api/email/outofoffice/postfaecher') {
            const w = optionen.waehlbar ?? WAEHLBAR;
            return Array.isArray(w) ? antwort(w) : antwort(w.body, w.status);
        }
        if (url.pathname === '/api/email/outofoffice' && methode === 'GET') return antwort(optionen.notizen ?? [NOTIZ]);
        if (url.pathname === '/api/email/outofoffice' && methode === 'POST') {
            const s = optionen.speichern ?? { status: 200, body: NOTIZ };
            return antwort(s.body, s.status);
        }
        if (/^\/api\/email\/outofoffice\/\d+$/.test(url.pathname) && methode === 'DELETE') {
            const l = optionen.loeschen ?? { status: 200, body: undefined };
            return antwort(l.body, l.status);
        }
        return antwort({ message: 'Nicht gefunden' }, 404);
    });
    vi.stubGlobal('fetch', mock);
    return mock;
}

function aufrufe(mock: ReturnType<typeof stubbe>, pfad: string, methode: string) {
    return mock.mock.calls.filter(([eingabe, init]) =>
        new URL(String(eingabe), 'http://localhost').pathname === pfad && (init?.method ?? 'GET') === methode);
}

async function zeigeAbwesenheiten() {
    const user = userEvent.setup();
    render(<ToastProvider><ConfirmProvider><AuthProvider><EmailSettings /></AuthProvider></ConfirmProvider></ToastProvider>);
    await user.click(screen.getByRole('button', { name: /Abwesenheitsnotizen/ }));
    return user;
}

const postfachGruppe = () => screen.getByRole('group', { name: 'Gilt für diese Postfächer' });

describe('EmailSettings – Abwesenheitsnotiz pro Postfach', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('neue Notiz: zeigt die wählbaren Postfächer und hakt das eigene vor', async () => {
        const mock = stubbe();
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: /Neue Abwesenheit/ }));

        const gruppe = postfachGruppe();
        const eigenes = await within(gruppe).findByRole('checkbox', { name: /max\.mustermann@example\.com.*Ihr Postfach/ });
        expect(eigenes).toBeChecked();
        expect(within(gruppe).getByRole('checkbox', { name: /info@example\.com/ })).not.toBeChecked();
        expect(within(gruppe).getAllByRole('checkbox')).toHaveLength(2);
        expect(screen.getByText('Die automatische Antwort geht nur auf E-Mails an die gewählten Postfächer – und kommt auch von dort.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Für welche Postfächer?' })).toBeInTheDocument();

        // Benutzer kommt allein aus der Anmeldung – kein frontendUserId-Parameter
        const geladen = aufrufe(mock, '/api/email/outofoffice/postfaecher', 'GET');
        expect(geladen).toHaveLength(1);
        expect(String(geladen[0][0])).not.toContain('frontendUserId');
    });

    it('neue Notiz ohne eigenes Postfach: nichts vorausgewählt', async () => {
        stubbe({ waehlbar: [{ ...INFO, eigenes: false }] });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: /Neue Abwesenheit/ }));
        expect(await within(postfachGruppe()).findByRole('checkbox', { name: /info@example\.com/ })).not.toBeChecked();
        expect(screen.getByText('Bitte mindestens ein Postfach auswählen.')).toBeInTheDocument();
    });

    it('Liste: jede Karte zeigt die Postfächer als Chips', async () => {
        stubbe();
        await zeigeAbwesenheiten();
        const chips = await screen.findByRole('list', { name: 'Gilt für diese Postfächer' });
        expect(within(chips).getByText('max.mustermann@example.com')).toBeInTheDocument();
        expect(within(chips).getByText('info@example.com')).toBeInTheDocument();
        expect(within(chips).getByText('info@example.com').closest('li')).toHaveAttribute('title', 'Musterbetrieb <info@example.com>');
    });

    it('Notiz ohne Postfach: deutlicher Hinweis statt Chips', async () => {
        stubbe({ notizen: [{ ...NOTIZ, postfaecher: [] }] });
        await zeigeAbwesenheiten();
        expect(await screen.findByText('Gilt für kein Postfach – antwortet nicht. Bitte bearbeiten und Postfach wählen.')).toBeInTheDocument();
        expect(screen.queryByRole('list', { name: 'Gilt für diese Postfächer' })).toBeNull();
    });

    it('Vorschau zeigt gespeichertes HTML nur als Text und führt nichts aus', async () => {
        const fenster = window as unknown as { __xss?: number };
        delete fenster.__xss;
        // jsdom lädt keine Bilder, onerror feuert dort nie – darum zusätzlich: kein innerHTML mit dem Roh-HTML.
        const innerHtmlSetter = vi.spyOn(Element.prototype, 'innerHTML', 'set');
        stubbe({ notizen: [{ ...NOTIZ, bodyTemplate: '<p>Bin weg &amp; zurück</p><img src=x onerror="window.__xss=1">' }] });
        await zeigeAbwesenheiten();
        expect(await screen.findByText('Bin weg & zurück')).toBeInTheDocument();
        await new Promise((r) => setTimeout(r, 50));
        expect(fenster.__xss).toBeUndefined();
        expect(document.querySelector('img')).toBeNull();
        expect(innerHtmlSetter.mock.calls.some(([wert]) => String(wert).includes('onerror'))).toBe(false);
        innerHtmlSetter.mockRestore();
    });

    it('Bearbeiten: gespeicherte Postfächer sind angehakt, gesendet werden die gewählten postfachIds', async () => {
        const mock = stubbe();
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));

        const gruppe = postfachGruppe();
        const info = await within(gruppe).findByRole('checkbox', { name: /info@example\.com/ });
        expect(info).toBeChecked();
        expect(within(gruppe).getByRole('checkbox', { name: /max\.mustermann/ })).toBeChecked();
        await user.click(info);
        await user.click(screen.getByRole('button', { name: /Änderungen speichern/ }));

        await waitFor(() => expect(aufrufe(mock, '/api/email/outofoffice', 'POST')).toHaveLength(1));
        const [eingabe, init] = aufrufe(mock, '/api/email/outofoffice', 'POST')[0];
        expect(String(eingabe)).toBe('/api/email/outofoffice');
        const body = JSON.parse(String(init?.body));
        expect(body).toMatchObject({ id: 11, title: 'Sommerurlaub', startDate: '2099-08-01', endDate: '2099-08-14', active: true, postfachIds: [7] });
        expect(body).not.toHaveProperty('frontendUserId');
        // Formular zu, Liste neu geladen
        await waitFor(() => expect(screen.queryByRole('group', { name: 'Gilt für diese Postfächer' })).toBeNull());
        expect(aufrufe(mock, '/api/email/outofoffice', 'GET').length).toBeGreaterThanOrEqual(2);
    });

    it('Bearbeiten: ein nicht mehr wählbares Postfach wird nicht angehakt und als entfernt angezeigt', async () => {
        stubbe({ waehlbar: [{ ...MAX, eigenes: true }] });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));
        expect(within(postfachGruppe()).getAllByRole('checkbox')).toHaveLength(1);
        expect(screen.getByText(/Nicht mehr wählbar und wird beim Speichern entfernt: info@example\.com/)).toBeInTheDocument();
    });

    it('ohne Häkchen: Hinweis, kein Speichern', async () => {
        const mock = stubbe();
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));
        const gruppe = postfachGruppe();
        for (const box of within(gruppe).getAllByRole('checkbox')) await user.click(box);
        expect(within(gruppe).getByText('Bitte mindestens ein Postfach auswählen.')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Änderungen speichern/ }));
        // Hinweis im Formular + Meldung
        await waitFor(() => expect(screen.getAllByText('Bitte mindestens ein Postfach auswählen.')).toHaveLength(2));
        expect(aufrufe(mock, '/api/email/outofoffice', 'POST')).toHaveLength(0);
    });

    it('403 beim Speichern: zeigt die Meldung des Servers, Formular bleibt offen', async () => {
        stubbe({ speichern: { status: 403, body: { message: 'Für dieses Postfach dürfen Sie keine Abwesenheitsnotiz anlegen.' } } });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));
        await user.click(screen.getByRole('button', { name: /Änderungen speichern/ }));

        expect(await screen.findByText('Für dieses Postfach dürfen Sie keine Abwesenheitsnotiz anlegen.')).toBeInTheDocument();
        expect(postfachGruppe()).toBeInTheDocument();
    });

    it('400 ohne Meldung beim Speichern: Standardtext', async () => {
        stubbe({ speichern: { status: 400, body: {} } });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));
        await user.click(screen.getByRole('button', { name: /Änderungen speichern/ }));
        expect(await screen.findByText('Abwesenheitsnotiz konnte nicht gespeichert werden.')).toBeInTheDocument();
    });

    it('404 beim Speichern: Meldung, Formular zu, Liste neu geladen', async () => {
        const mock = stubbe({ speichern: { status: 404, body: { message: 'Diese Abwesenheitsnotiz gibt es nicht mehr.' } } });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub bearbeiten' }));
        await user.click(screen.getByRole('button', { name: /Änderungen speichern/ }));
        expect(await screen.findByText('Diese Abwesenheitsnotiz gibt es nicht mehr.')).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByRole('group', { name: 'Gilt für diese Postfächer' })).toBeNull());
        expect(aufrufe(mock, '/api/email/outofoffice', 'GET').length).toBeGreaterThanOrEqual(2);
    });

    it('404 beim Löschen (Notiz eines Kollegen oder schon weg): Meldung des Servers, Liste neu geladen', async () => {
        const mock = stubbe({ loeschen: { status: 404, body: { message: 'Diese Abwesenheitsnotiz gibt es nicht mehr.' } } });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        expect(await screen.findByText('Diese Abwesenheitsnotiz gibt es nicht mehr.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/email/outofoffice/11', 'DELETE')).toHaveLength(1);
        await waitFor(() => expect(aufrufe(mock, '/api/email/outofoffice', 'GET').length).toBeGreaterThanOrEqual(2));
    });

    it('Fehler beim Löschen ohne Meldung: Standardtext', async () => {
        stubbe({ loeschen: { status: 500, body: {} } });
        const user = await zeigeAbwesenheiten();
        await user.click(await screen.findByRole('button', { name: 'Sommerurlaub löschen' }));
        await user.click(await screen.findByRole('button', { name: 'Löschen' }));
        expect(await screen.findByText('Abwesenheitsnotiz konnte nicht gelöscht werden.')).toBeInTheDocument();
    });

    it('keine wählbaren Postfächer: Hinweis und „Neue Abwesenheit“ gesperrt', async () => {
        stubbe({ waehlbar: [] });
        await zeigeAbwesenheiten();
        expect(await screen.findByText('Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.')).toBeInTheDocument();
        const knopf = screen.getByRole('button', { name: /Neue Abwesenheit/ });
        expect(knopf).toBeDisabled();
        expect(knopf.parentElement).toHaveAttribute('title', 'Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.');
    });

    it('keine Notizen und keine wählbaren Postfächer: auch „Erste Abwesenheit planen“ gesperrt', async () => {
        stubbe({ waehlbar: [], notizen: [] });
        await zeigeAbwesenheiten();
        await screen.findByText('Für Sie ist kein Postfach eingerichtet. Bitte den Admin fragen.');
        expect(screen.getByRole('button', { name: /Erste Abwesenheit planen/ })).toBeDisabled();
    });

    it('Postfächer laden fehlgeschlagen: Meldung und „Neue Abwesenheit“ gesperrt', async () => {
        stubbe({ waehlbar: { status: 500, body: {} } });
        await zeigeAbwesenheiten();
        expect(await screen.findByText('Postfächer für die Abwesenheitsnotiz konnten nicht geladen werden.')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole('button', { name: /Neue Abwesenheit/ })).toBeDisabled());
    });
});
