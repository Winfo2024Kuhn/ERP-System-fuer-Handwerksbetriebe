import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../../auth/AuthContext';
import { ConfirmProvider } from '../../../components/ui/confirm-dialog';
import { ToastProvider } from '../../../components/ui/toast';
import { SignaturStudio } from './SignaturStudio';
import { aufrufe, gesendeterBody, signatur, stubbeSignaturApi, type StubOptionen } from './signaturTestdaten';
import { leereSignaturDaten, type SignaturDto } from './signaturTypen';

/** Signatur-Studio: Live-Vorschau, Speichern nach API-Vertrag, Admin-Besitzer, HTML-Modus. Nur Dummy-Daten. */

/** Wie RequireAuth in der App: Inhalt erst nach geprüfter Anmeldung. */
function NachAnmeldung({ children }: { children: React.ReactNode }) {
    const { loading } = useAuth();
    return loading ? null : <>{children}</>;
}

function zeige(optionen: StubOptionen & { sig?: SignaturDto | null } = {}) {
    const mock = stubbeSignaturApi(optionen);
    const onGespeichert = vi.fn();
    const onAbbrechen = vi.fn();
    const user = userEvent.setup();
    render(
        <ToastProvider><ConfirmProvider><AuthProvider>
            <NachAnmeldung>
                <SignaturStudio signatur={optionen.sig ?? null} onGespeichert={onGespeichert} onAbbrechen={onAbbrechen} />
            </NachAnmeldung>
        </AuthProvider></ConfirmProvider></ToastProvider>,
    );
    return { mock, user, onGespeichert, onAbbrechen };
}

const vorschau = () => screen.getByTitle('Vorschau der Signatur').getAttribute('srcdoc') ?? '';
const feld = (name: string) => screen.getByLabelText(name) as HTMLInputElement;
const studioDa = () => screen.findByRole('heading', { name: /Signatur gestalten|Signatur bearbeiten/ });

describe('SignaturStudio – neue Signatur', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('füllt Name und Firmendaten vor und zeigt sie in der Live-Vorschau', async () => {
        zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await waitFor(() => expect(feld('Firma').value).toBe('Mustermann Metallbau GmbH'));
        expect(feld('Telefon').value).toBe('0931 123456');
        expect(feld('Adresse').value).toBe('Musterstraße 1, 12345 Musterstadt');
        await waitFor(() => expect(vorschau()).toContain('Mustermann Metallbau GmbH'));
        // Firmenfarbe als Akzent übernommen und als Vorschlag angeboten
        expect(vorschau()).toContain('#7c2d12');
        expect(screen.getByRole('button', { name: 'Firmenfarbe (#7c2d12)' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('Live-Vorschau aktualisiert sich beim Tippen und beim Vorlagen-Wechsel', async () => {
        const { user } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.type(feld('Position'), 'Metallbaumeister');
        await waitFor(() => expect(vorschau()).toContain('Metallbaumeister'));

        expect(vorschau()).not.toContain('Tel.:');
        await user.click(screen.getByRole('radio', { name: /Schlicht/ }));
        expect(screen.getByRole('radio', { name: /Schlicht/ })).toHaveAttribute('aria-checked', 'true');
        await waitFor(() => expect(vorschau()).toContain('Tel.:'));
    });

    it('Vorschau ist abgeschottet: sandbox ohne Skripte und CSP', async () => {
        const { user } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.clear(feld('Ihr Name'));
        await user.type(feld('Ihr Name'), '<img src=x onerror=alert(1)>');
        const iframe = screen.getByTitle('Vorschau der Signatur');
        expect(iframe.getAttribute('sandbox')).toBe('allow-same-origin');
        await waitFor(() => expect(vorschau()).toContain('&lt;img src=x onerror=alert(1)&gt;'));
        expect(vorschau()).toContain("default-src 'none'");
    });

    it('Speichern schickt den Vertrag-Body mit vorlageDaten, ohne besitzerId (kein Admin)', async () => {
        const { mock, user, onGespeichert } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Firma').value).toBe('Mustermann Metallbau GmbH'));
        await user.type(feld('Position'), 'Metallbaumeister');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => expect(onGespeichert).toHaveBeenCalledTimes(1));
        const body = gesendeterBody(mock);
        expect(body).toMatchObject({ id: null, name: 'Meine Signatur', alsStandard: false });
        expect(body).not.toHaveProperty('besitzerId');
        expect(body).not.toHaveProperty('besitzerFestlegen');
        expect(body.html).toContain('Metallbaumeister');
        const daten = JSON.parse(String(body.vorlageDaten));
        expect(daten).toMatchObject({ vorlage: 'klassisch-foto', name: 'Max Mustermann', position: 'Metallbaumeister', version: 1 });
        expect(await screen.findByText('Signatur gespeichert.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/frontend-users')).toHaveLength(0);
    });

    it('„Speichern & als meine Standard-Signatur“ schickt alsStandard: true', async () => {
        const { mock, user } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.click(screen.getByRole('button', { name: /Speichern & als meine Standard-Signatur/ }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        expect(gesendeterBody(mock).alsStandard).toBe(true);
        expect(await screen.findByText('Signatur gespeichert und als Ihre Standard-Signatur gesetzt.')).toBeInTheDocument();
    });

    it('prüft vorher: ohne Namen der Signatur kein Speichern', async () => {
        const { mock, user } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.clear(feld('Name der Signatur'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Bitte der Signatur einen Namen geben, z. B. „Standard“.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(0);
    });

    it('prüft vorher: leere Signatur wird nicht gespeichert', async () => {
        const { mock, user } = zeige({
            extra: (url) => (url.pathname === '/api/firma' ? new Response(null, { status: 204 }) : undefined),
        });
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.clear(feld('Ihr Name'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Die Signatur ist noch leer. Bitte mindestens Ihren Namen eintragen.')).toBeInTheDocument();
        expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(0);
    });

    it('Fehler vom Server: deutsche Meldung als Toast, Studio bleibt offen', async () => {
        const { user, onGespeichert } = zeige({ speichern: { status: 400, body: { message: 'Ein eingebettetes Bild ist größer als 1 MB.' } } });
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Ein eingebettetes Bild ist größer als 1 MB.')).toBeInTheDocument();
        expect(onGespeichert).not.toHaveBeenCalled();
    });

    it('Abbrechen ohne Änderungen schließt sofort, mit Änderungen erst nach Rückfrage', async () => {
        const { user, onAbbrechen } = zeige();
        await studioDa();
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalledTimes(1);

        await user.type(feld('Position'), 'Meister');
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText(/noch nicht gespeichert/)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', { name: 'Verwerfen' }));
        await waitFor(() => expect(onAbbrechen).toHaveBeenCalledTimes(2));
    });

    it('In Zwischenablage kopieren: formatiert mit HTML und Text', async () => {
        const write = vi.fn(async () => undefined);
        const { user } = zeige();
        await studioDa();
        Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { write, writeText: vi.fn() } });
        vi.stubGlobal('ClipboardItem', class { constructor(public daten: Record<string, Blob>) {} });
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await user.click(screen.getByRole('button', { name: 'In Zwischenablage kopieren' }));
        expect(await screen.findByText('Signatur kopiert. Sie können sie jetzt z. B. in Outlook einfügen.')).toBeInTheDocument();
        expect(write).toHaveBeenCalledTimes(1);
    });

    it('Handy-Vorschau umschalten', async () => {
        const { user } = zeige();
        await studioDa();
        const handy = screen.getByRole('button', { name: 'Handy' });
        expect(handy).toHaveAttribute('aria-pressed', 'false');
        await user.click(handy);
        expect(handy).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('button', { name: 'Computer' })).toHaveAttribute('aria-pressed', 'false');
    });

    it('Bilder-Felder passen zur Vorlage; ungültiger Link wird angemahnt, Dropbox-Link aufbereitet', async () => {
        const { user } = zeige();
        await studioDa();
        expect(screen.getByRole('group', { name: 'Foto von Ihnen' })).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Banner' })).toBeNull();

        const logo = screen.getByLabelText('Firmenlogo: Link zum Bild');
        await user.type(logo, 'javascript:alert(1)');
        expect(screen.getByText('Nur Links, die mit https:// oder http:// beginnen, sind möglich.')).toBeInTheDocument();
        await user.clear(logo);
        await user.type(logo, 'https://www.dropbox.com/s/abc/logo.png?dl=0');
        await user.tab();
        expect((logo as HTMLInputElement).value).toBe('https://www.dropbox.com/s/abc/logo.png?raw=1');
        await waitFor(() => expect(vorschau()).toContain('https://www.dropbox.com/s/abc/logo.png?raw=1'));

        await user.click(screen.getByRole('radio', { name: /Mit Banner/ }));
        expect(screen.getByRole('group', { name: 'Banner' })).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Foto von Ihnen' })).toBeNull();
    });

    it('Bild hochladen: unerlaubter Dateityp gibt eine Meldung', async () => {
        zeige();
        await studioDa();
        const eingabe = screen.getByTestId('bild-datei-logo');
        fireEvent.change(eingabe, { target: { files: [new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' })] } });
        expect(await screen.findByText('Bitte ein Bild im Format PNG, JPG, GIF oder WebP wählen.')).toBeInTheDocument();
    });
});

describe('SignaturStudio – Admin', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('neue Signatur: „Gehört zu“ mit eigenem Benutzer vorausgewählt, besitzerId wird mitgeschickt', async () => {
        const { mock, user } = zeige({ admin: true });
        await studioDa();
        const auswahl = await screen.findByRole('combobox', { name: /Gehört zu/ });
        await waitFor(() => expect(auswahl).toHaveTextContent('Erika Admin (Sie)'));
        await waitFor(() => expect(aufrufe(mock, '/api/frontend-users')).toHaveLength(1));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        expect(gesendeterBody(mock).besitzerId).toBe(1);
        expect(gesendeterBody(mock).besitzerFestlegen).toBe(true);
    });

    it('„Niemand – nur Admins“ schickt besitzerId: null; inaktive Benutzer werden nicht angeboten', async () => {
        const { mock, user } = zeige({ admin: true });
        await studioDa();
        const auswahl = await screen.findByRole('combobox', { name: /Gehört zu/ });
        await waitFor(() => expect(aufrufe(mock, '/api/frontend-users')).toHaveLength(1));
        await user.click(auswahl);
        const liste = await screen.findByRole('listbox');
        expect(within(liste).queryByRole('option', { name: 'Alt Ausgeschieden' })).toBeNull();
        expect(within(liste).getByRole('option', { name: 'Moritz Muster' })).toBeInTheDocument();
        await user.click(within(liste).getByRole('option', { name: 'Niemand – nur Admins sehen sie' }));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        expect(gesendeterBody(mock).besitzerId).toBeNull();
        expect(gesendeterBody(mock).besitzerFestlegen).toBe(true);
    });

    it('bestehende Signatur eines Kollegen: Besitzer bleibt, id wird mitgeschickt', async () => {
        const daten = { ...leereSignaturDaten(), name: 'Moritz Muster', vorlage: 'kompakt' as const };
        const sig = signatur({ id: 42, name: 'Moritz', besitzerId: 9, besitzerName: 'Moritz Muster', eigene: false, vorlageDaten: JSON.stringify(daten) });
        const { mock, user } = zeige({ admin: true, sig });
        await studioDa();
        const auswahl = await screen.findByRole('combobox', { name: /Gehört zu/ });
        await waitFor(() => expect(auswahl).toHaveTextContent('Moritz Muster'));
        expect(screen.getByRole('radio', { name: /Kompakt/ })).toHaveAttribute('aria-checked', 'true');
        // Bestehende Signatur wird nicht mit Firmendaten überschrieben
        expect(feld('Firma').value).toBe('');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        expect(gesendeterBody(mock)).toMatchObject({ id: 42, name: 'Moritz', besitzerId: 9 });
    });
});

describe('SignaturStudio – HTML-Modus', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('alte Signatur ohne vorlageDaten öffnet im HTML-Modus und speichert vorlageDaten: null', async () => {
        const sig = signatur({ html: '<p>Alte Signatur</p>' });
        const { mock, user } = zeige({ sig });
        await studioDa();
        const code = screen.getByLabelText('HTML der Signatur') as HTMLTextAreaElement;
        expect(code.value).toBe('<p>Alte Signatur</p>');
        expect(screen.getByText(/ohne Vorlage angelegt/)).toBeInTheDocument();
        expect(screen.queryByRole('switch')).toBeNull();
        await user.type(code, '<p>Neu</p>');
        await waitFor(() => expect(vorschau()).toContain('<p>Neu</p>'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        expect(gesendeterBody(mock)).toMatchObject({ id: 5, html: '<p>Alte Signatur</p><p>Neu</p>', vorlageDaten: null });
    });

    it('„Mit Vorlage neu gestalten“ wechselt nach Rückfrage ins Formular', async () => {
        const { mock, user } = zeige({ sig: signatur({ html: '<p>Alte Signatur</p>' }) });
        await studioDa();
        await user.click(screen.getByRole('button', { name: /Mit Vorlage neu gestalten/ }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Neu gestalten' }));
        await waitFor(() => expect(feld('Ihr Name').value).toBe('Max Mustermann'));
        await waitFor(() => expect(feld('Firma').value).toBe('Mustermann Metallbau GmbH'));
        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        const body = gesendeterBody(mock);
        expect(body.vorlageDaten).not.toBeNull();
        expect(body.html).toContain('Mustermann Metallbau GmbH');
        expect(body.html).not.toContain('Alte Signatur');
    });

    it('Umschalter „HTML direkt bearbeiten“: Code bearbeiten, zurück nur nach Rückfrage', async () => {
        const daten = { ...leereSignaturDaten(), name: 'Max Mustermann' };
        const { mock, user } = zeige({ sig: signatur({ vorlageDaten: JSON.stringify(daten) }) });
        await studioDa();
        const schalter = screen.getByRole('switch', { name: 'HTML direkt bearbeiten' });
        await user.click(schalter);
        expect(schalter).toHaveAttribute('aria-checked', 'true');
        const code = screen.getByLabelText('HTML der Signatur') as HTMLTextAreaElement;
        expect(code.value).toContain('Max Mustermann');
        await user.type(code, '<p>Extra</p>');

        await user.click(schalter);
        const dialog = await screen.findByRole('dialog');
        await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        expect(schalter).toHaveAttribute('aria-checked', 'true');

        await user.click(screen.getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(aufrufe(mock, '/api/email/signatures', 'POST')).toHaveLength(1));
        const body = gesendeterBody(mock);
        expect(body.vorlageDaten).toBeNull();
        expect(body.html).toContain('<p>Extra</p>');
    });
});
