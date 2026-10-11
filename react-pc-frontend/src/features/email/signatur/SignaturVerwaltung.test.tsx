import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../../auth/AuthContext';
import EmailSettings from '../../../components/EmailSettings';
import { ConfirmProvider } from '../../../components/ui/confirm-dialog';
import { ToastProvider } from '../../../components/ui/toast';
import { antwort, aufrufe, signatur, stubbeSignaturApi, type StubOptionen } from './signaturTestdaten';

/** Signatur-Liste in den E-Mail-Einstellungen: Badges, Admin vs. Benutzer, Aktionen. Nur Dummy-Daten. */

const EIGENE_STANDARD = signatur({ id: 1, name: 'Standard', defaultSignature: true });
const EIGENE_KURZ = signatur({ id: 2, name: 'Kurz für Antworten' });
const SYSTEM = signatur({ id: 3, name: 'Automatische E-Mails', isSystemDefault: true, besitzerId: null, besitzerName: null, eigene: false });
const KOLLEGE = signatur({ id: 4, name: 'Moritz', besitzerId: 9, besitzerName: 'Moritz Muster', eigene: false });

function NachAnmeldung({ children }: { children: React.ReactNode }) {
    const { loading } = useAuth();
    return loading ? null : <>{children}</>;
}

async function zeige(optionen: StubOptionen = {}) {
    const mock = stubbeSignaturApi({
        ...optionen,
        extra: async (url, methode, init) => {
            const zusatz = await optionen.extra?.(url, methode, init);
            if (zusatz) return zusatz;
            if (url.pathname === '/api/email/outofoffice' || url.pathname === '/api/email/outofoffice/postfaecher') return antwort([]);
            return undefined;
        },
    });
    const user = userEvent.setup();
    render(
        <ToastProvider><ConfirmProvider><AuthProvider><NachAnmeldung><EmailSettings /></NachAnmeldung></AuthProvider></ConfirmProvider></ToastProvider>,
    );
    await screen.findByRole('heading', { name: 'E-Mail-Signaturen' });
    if (optionen.signaturen?.length && !optionen.extra) await screen.findByRole('list', { name: 'Signaturen' });
    return { mock, user };
}

const karte = (name: string) => screen.getByRole('heading', { name, level: 3 }).closest('li') as HTMLElement;

describe('Signatur-Liste – Benutzer', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt eigene Signaturen mit Vorschau und Standard-Badge, ohne Admin-Teile', async () => {
        await zeige({ signaturen: [EIGENE_KURZ, EIGENE_STANDARD] });
        const liste = await screen.findByRole('list', { name: 'Signaturen' });
        const namen = within(liste).getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
        expect(namen).toEqual(['Standard', 'Kurz für Antworten']); // Standard zuerst
        expect(within(karte('Standard')).getByText('Meine Standard-Signatur')).toBeInTheDocument();
        expect(within(karte('Kurz für Antworten')).queryByText('Meine Standard-Signatur')).toBeNull();
        expect(within(karte('Standard')).getByTitle('Vorschau der Signatur Standard').getAttribute('sandbox')).toBe('allow-same-origin');

        expect(screen.queryByText('System-Signatur für automatische E-Mails')).toBeNull();
        expect(screen.queryByRole('button', { name: /Als System-Signatur/ })).toBeNull();
        expect(screen.queryByRole('group', { name: 'Signaturen filtern' })).toBeNull();
        expect(screen.queryByText('Ihre Signatur')).toBeNull();
    });

    it('leerer Zustand: großer Knopf öffnet das Studio, nach dem Speichern wieder die Liste', async () => {
        const { mock, user } = await zeige({ signaturen: [] });
        expect(await screen.findByText('Noch keine Signatur')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Signatur erstellen' }));
        expect(await screen.findByRole('heading', { name: 'Neue Signatur gestalten' })).toBeInTheDocument();
        await waitFor(() => expect((screen.getByLabelText('Firma') as HTMLInputElement).value).toBe('Mustermann Metallbau GmbH'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByRole('heading', { name: 'E-Mail-Signaturen' })).toBeInTheDocument();
        expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1);
        expect(aufrufe(mock, '/api/email/signatures', 'GET').length).toBeGreaterThanOrEqual(2);
    });

    it('Reiterwechsel zu den Abwesenheitsnotizen verliert das offene Studio nicht', async () => {
        const { user } = await zeige({ signaturen: [] });
        await user.click(await screen.findByRole('button', { name: 'Signatur erstellen' }));
        await user.type(await screen.findByLabelText('Position'), 'Metallbaumeister');
        await user.click(screen.getByRole('button', { name: /Abwesenheitsnotizen/ }));
        expect(screen.getByLabelText('Position')).not.toBeVisible();
        await user.click(screen.getByRole('button', { name: /^Signaturen$/ }));
        expect((screen.getByLabelText('Position') as HTMLInputElement).value).toBe('Metallbaumeister');
    });

    it('Bearbeiten öffnet das Studio mit der Signatur', async () => {
        const { user } = await zeige({ signaturen: [EIGENE_STANDARD] });
        await user.click(within(karte('Standard')).getByRole('button', { name: 'Bearbeiten' }));
        expect(await screen.findByRole('heading', { name: 'Signatur bearbeiten' })).toBeInTheDocument();
        expect((screen.getByLabelText('Name der Signatur') as HTMLInputElement).value).toBe('Standard');
        await user.click(screen.getByRole('button', { name: /Zurück zur Übersicht/ }));
        expect(await screen.findByRole('heading', { name: 'E-Mail-Signaturen' })).toBeInTheDocument();
    });

    it('Löschen nur nach Bestätigung', async () => {
        const { mock, user } = await zeige({ signaturen: [EIGENE_STANDARD, EIGENE_KURZ] });
        await user.click(screen.getByRole('button', { name: 'Signatur „Kurz für Antworten“ löschen' }));
        let dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Signatur „Kurz für Antworten“ wirklich löschen?')).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(aufrufe(mock, /^\/api\/email\/signatures\/\d+$/, 'DELETE')).toHaveLength(0);

        await user.click(screen.getByRole('button', { name: 'Signatur „Standard“ löschen' }));
        dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText(/Sie ist Ihre Standard-Signatur/)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures/1', 'DELETE')).toHaveLength(1));
        expect(await screen.findByText('Signatur gelöscht.')).toBeInTheDocument();
    });

    it('Als Standard setzen und Standard wieder entfernen', async () => {
        const { mock, user } = await zeige({ signaturen: [EIGENE_STANDARD, EIGENE_KURZ] });
        await user.click(within(karte('Kurz für Antworten')).getByRole('button', { name: 'Als Standard' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures/2/standard', 'PUT')).toHaveLength(1));
        expect(await screen.findByText('„Kurz für Antworten“ ist jetzt Ihre Standard-Signatur.')).toBeInTheDocument();

        await user.click(within(karte('Standard')).getByRole('button', { name: 'Nicht mehr Standard' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures/standard', 'DELETE')).toHaveLength(1));
    });

    it('Fehler bei einer Aktion: Meldung des Servers', async () => {
        const { user } = await zeige({
            signaturen: [EIGENE_KURZ],
            extra: (url, methode) => (url.pathname === '/api/email/signatures/2/standard' && methode === 'PUT'
                ? antwort({ message: 'Diese Signatur gibt es nicht mehr.' }, 404) : undefined),
        });
        await screen.findByRole('list', { name: 'Signaturen' });
        await user.click(within(karte('Kurz für Antworten')).getByRole('button', { name: 'Als Standard' }));
        expect(await screen.findByText('Diese Signatur gibt es nicht mehr.')).toBeInTheDocument();
    });

    it('Laden fehlgeschlagen: Meldung und „Erneut laden“', async () => {
        const { mock, user } = await zeige({
            extra: (url, methode) => (url.pathname === '/api/email/signatures' && methode === 'GET' ? antwort({}, 500) : undefined),
        });
        expect(await screen.findByText('Signaturen konnten nicht geladen werden.', { selector: 'span' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Erneut laden' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'GET')).toHaveLength(2));
    });

    it('alte Signatur mit hochgeladenen Bildern: Vorschau mit aufgelösten Bildern', async () => {
        const alt = signatur({ id: 8, name: 'Alt', images: [{ id: 1, cid: 'logo', originalFilename: 'logo.png', contentType: 'image/png', sizeBytes: 100 }] });
        const { mock } = await zeige({ signaturen: [alt] });
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures/8/preview')).toHaveLength(1));
        await waitFor(() => expect(within(karte('Alt')).getByTitle('Vorschau der Signatur Alt').getAttribute('srcdoc')).toContain('Vorschau mit Bild'));
    });
});

describe('Signatur-Liste – Admin', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt alle Signaturen mit Besitzer, System-Banner und Filter', async () => {
        const { user } = await zeige({ admin: true, signaturen: [SYSTEM, KOLLEGE, { ...EIGENE_KURZ, besitzerId: 1, besitzerName: 'Erika Admin' }] });
        expect(await screen.findByText('System-Signatur für automatische E-Mails')).toBeInTheDocument();
        expect(screen.getByText('Automatische E-Mails', { selector: 'span' })).toBeInTheDocument();
        expect(within(karte('Automatische E-Mails')).getByText('System-Signatur')).toBeInTheDocument();
        expect(within(karte('Automatische E-Mails')).getByText('ohne Besitzer')).toBeInTheDocument();
        expect(within(karte('Moritz')).getByText('gehört: Moritz Muster')).toBeInTheDocument();
        expect(within(karte('Kurz für Antworten')).getByText('Ihre Signatur')).toBeInTheDocument();

        // System-Signatur kann nicht gelöscht werden
        expect(screen.getByRole('button', { name: 'Signatur „Automatische E-Mails“ löschen' })).toBeDisabled();
        expect(within(karte('Automatische E-Mails')).queryByRole('button', { name: /Als System-Signatur/ })).toBeNull();

        const filter = screen.getByRole('group', { name: 'Signaturen filtern' });
        await user.click(within(filter).getByRole('button', { name: /Meine/ }));
        expect(screen.queryByRole('heading', { name: 'Moritz', level: 3 })).toBeNull();
        expect(screen.getByRole('heading', { name: 'Kurz für Antworten', level: 3 })).toBeInTheDocument();
        await user.click(within(filter).getByRole('button', { name: /Alle/ }));
        expect(screen.getByRole('heading', { name: 'Moritz', level: 3 })).toBeInTheDocument();
    });

    it('Als System-Signatur setzen', async () => {
        const { mock, user } = await zeige({ admin: true, signaturen: [SYSTEM, KOLLEGE] });
        await user.click(within(karte('Moritz')).getByRole('button', { name: 'Als System-Signatur' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures/4/system-default', 'PUT')).toHaveLength(1));
        expect(await screen.findByText('System-Signatur aktualisiert.')).toBeInTheDocument();
    });

    it('Platzhalter-System-Signatur: Hinweis zum Einrichten; ohne System-Signatur: Warnung', async () => {
        await zeige({ admin: true, signaturen: [{ ...SYSTEM, html: '<p data-system-placeholder="1">Bitte anpassen</p>' }] });
        expect(await screen.findByText(/enthält noch den Platzhalter-Text/)).toBeInTheDocument();
    });

    it('ohne System-Signatur: Warnung', async () => {
        await zeige({ admin: true, signaturen: [KOLLEGE] });
        expect(await screen.findByText('Keine System-Signatur gesetzt.')).toBeInTheDocument();
    });

    it('Filter „Meine“ ohne eigene Signatur: Hinweis mit Knopf', async () => {
        const { user } = await zeige({ admin: true, signaturen: [KOLLEGE] });
        await user.click(within(screen.getByRole('group', { name: 'Signaturen filtern' })).getByRole('button', { name: /Meine/ }));
        expect(screen.getByText(/Sie haben noch keine eigene Signatur/)).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Jetzt eine erstellen' }));
        expect(await screen.findByRole('heading', { name: 'Neue Signatur gestalten' })).toBeInTheDocument();
    });
});
