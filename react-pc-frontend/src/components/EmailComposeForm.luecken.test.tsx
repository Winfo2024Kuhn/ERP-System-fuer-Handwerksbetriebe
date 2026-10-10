import { act, fireEvent, render as rtlRender, screen, waitFor, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { EmailComposeForm, anredeEnumToText, formatFileSize, MAX_ATTACHMENT_BYTES } from './EmailComposeForm';

/**
 * Lückentests für das Schreiben-Fenster: Anrede, Vorgangs-Daten, KI-Formulierung,
 * Anhänge, Projekt-Dateien und die Rückfrage „Adresse speichern?“.
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

vi.mock('../lib/bildKomprimierung', () => ({
    komprimiereBildFuerEmail: async (datei: File) => datei,
    komprimiereBilderFuerEmail: async (dateien: File[]) => dateien,
}));
vi.mock('./ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf-viewer">{url}</div>,
}));

const render = (ui: ReactElement) => rtlRender(<ToastProvider>{ui}</ToastProvider>);

type Antwort = { status?: number; body?: unknown; roh?: Response };
let routen: Record<string, Antwort | (() => Antwort)>;
let aufrufe: { url: string; init?: RequestInit }[];

function antwort(a: Antwort) {
    if (a.roh) return a.roh;
    const status = a.status ?? 200;
    return new Response(status === 204 ? null : JSON.stringify(a.body ?? {}), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
    aufrufe = [];
    routen = {
        '/api/emails/absender-postfaecher': { body: [{ id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: true, hauptpostfach: true }] },
        '/api/email/signatures/default': { body: { html: '<p>Viele Grüße<br>Max Mustermann</p>' } },
        '/api/emails/send': { body: { id: 900 } },
    };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        aufrufe.push({ url, init });
        const pfad = url.split('?')[0];
        const treffer = routen[url] ?? routen[pfad];
        if (treffer) return antwort(typeof treffer === 'function' ? treffer() : treffer);
        if (pfad.startsWith('/api/emails/drafts')) return antwort({ body: { id: 55 } });
        return antwort({ body: [] });
    }));
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:vorschau'), revokeObjectURL: vi.fn() }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('anredeEnumToText und formatFileSize', () => {
    it('übersetzt jede Anrede', () => {
        expect(anredeEnumToText(undefined)).toBe('Sehr geehrte Damen und Herren');
        expect(anredeEnumToText('herr')).toBe('Sehr geehrter Herr');
        expect(anredeEnumToText('FRAU')).toBe('Sehr geehrte Frau');
        expect(anredeEnumToText('FAMILIE')).toBe('Sehr geehrte Familie');
        expect(anredeEnumToText('FIRMA')).toBe('Sehr geehrte Damen und Herren');
        expect(anredeEnumToText('DAMEN_HERREN')).toBe('Sehr geehrte Damen und Herren');
        expect(anredeEnumToText('UNBEKANNT')).toBe('Sehr geehrte Damen und Herren');
    });

    it('zeigt Größen in KB und MB', () => {
        expect(formatFileSize(10)).toBe('1 KB');
        expect(formatFileSize(5 * 1024 * 1024)).toBe('5.0 MB');
    });
});

describe('EmailComposeForm – Vorgang und Titel', () => {
    it('zeigt den Titel einer Bestellanfrage', async () => {
        render(<EmailComposeForm onClose={() => {}} initialSubject="Bestellanfrage: Musterhandel GmbH" initialRecipient="verkauf@example.org" />);
        expect(await screen.findByText('Bestellung an Musterhandel GmbH senden')).toBeInTheDocument();
        expect(screen.getByText('Bestellanfrage mit E-Mail-Versand vorbereiten')).toBeInTheDocument();
    });

    it('nimmt bei einer Anfrage die Adressen aus Prop und Backend', async () => {
        routen['/api/anfragen/12'] = { body: { bauvorhaben: 'Treppe Mustermann', kundenEmails: ['erika@example.org', 'max@example.org'] } };
        routen['/api/anfragen/12/dokumente'] = { body: [] };
        routen['/api/anfragen/12/notizen'] = { status: 500, body: {} };
        render(<EmailComposeForm onClose={() => {}} anfrageId={12}
            anfrage={{ bauvorhaben: 'Treppe Mustermann', kundenEmails: ['max@example.org'] }} />);
        expect(await screen.findByText('Anfrage: Treppe Mustermann')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben')).toHaveValue('max@example.org'));
        expect(screen.getByRole('button', { name: /Dateien aus Anfrage hinzufügen/ })).toBeInTheDocument();
    });

    it('nimmt beim Projekt die Adressen aus Projekt und Kunde', async () => {
        routen['/api/projekte/41'] = { body: { id: 41, bauvorhaben: 'Garagentor', kundenEmails: ['a@example.org'], kundeDto: { kundenEmails: ['b@example.org'] } } };
        render(<EmailComposeForm onClose={() => {}} projektId={41}
            projekt={{ id: 41, bauvorhaben: 'Garagentor', kundenEmails: ['a@example.org'], kundeDto: { kundenEmails: ['b@example.org', 'a@example.org'] } } as never} />);
        expect(await screen.findByText('Projekt: Garagentor')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben')).toHaveValue('a@example.org'));
    });

    it('übersteht einen Netzwerkfehler beim Laden des Vorgangs', async () => {
        routen['/api/projekte/41'] = () => { throw new Error('offline'); };
        render(<EmailComposeForm onClose={() => {}} projektId={41} />);
        expect(await screen.findByText('Projekt: Projekt', { exact: false }).catch(() => screen.findByText('Neue E-Mail'))).toBeInTheDocument();
    });

    it('meldet, wenn die Projekt-Dateien nicht geladen werden können', async () => {
        routen['/api/projekte/41/dokumente'] = { status: 500, body: {} };
        render(<EmailComposeForm onClose={() => {}} projektId={41} />);
        expect(await screen.findByText('Projekt-/Anfrage-Dateien konnten nicht geladen werden.')).toBeInTheDocument();
    });

    it('fragt bei offener Rückfrage mit Escape nicht weiter', async () => {
        render(<EmailComposeForm onClose={() => {}} zuordnungWaehlbar initialRecipient="kunde@example.org" initialSubject="Termin" />);
        await screen.findByRole('combobox', { name: 'Senden von' }).catch(() => undefined);
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        expect(await screen.findByText('Kein Projekt und keine Anfrage verknüpft')).toBeInTheDocument();
        fireEvent.keyDown(document, { key: 'Escape' });
        await waitFor(() => expect(screen.queryByText('Kein Projekt und keine Anfrage verknüpft')).not.toBeInTheDocument());
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Abbrechen/ }));
        expect(screen.queryByText('Kein Projekt und keine Anfrage verknüpft')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Projekt oder Anfrage auswählen/ }));
    });
});

describe('EmailComposeForm – Text, Signatur und KI', () => {
    it('hängt die Signatur an einen mitgegebenen Text an', async () => {
        render(<EmailComposeForm onClose={() => {}} initialBody="<p>Anbei die Rechnung.</p>" initialRecipient="kunde@example.org" initialSubject="Rechnung" />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        await waitFor(() => expect(editor.innerHTML).toContain('Viele Grüße'));
        expect(editor.innerHTML).toContain('Anbei die Rechnung.');
    });

    it('lässt einen Text mit Signatur unverändert', async () => {
        render(<EmailComposeForm onClose={() => {}} initialBody='<p>Hallo</p><div class="email-signature">Gruß</div>' />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        await waitFor(() => expect(editor.innerHTML).toContain('Gruß'));
        expect(aufrufe.some(a => a.url.startsWith('/api/email/signatures/default'))).toBe(false);
    });

    it('lädt die Signatur für den gemerkten Benutzer und übersteht Fehler', async () => {
        localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 7, displayName: 'Max Mustermann' }));
        routen['/api/email/signatures/default'] = { body: { html: '<div class="email-signature">Max</div>' } };
        render(<EmailComposeForm onClose={() => {}} />);
        await waitFor(() => expect(aufrufe.some(a => a.url === '/api/email/signatures/default?frontendUserId=7')).toBe(true));
        localStorage.setItem('frontendUserSelection', '{kaputt');
        routen['/api/email/signatures/default'] = () => { throw new Error('offline'); };
        render(<EmailComposeForm onClose={() => {}} />);
        await waitFor(() => expect(aufrufe.filter(a => a.url.startsWith('/api/email/signatures/default')).length).toBe(2));
        localStorage.removeItem('frontendUserSelection');
    });

    it('formuliert mit KI um und behält Signatur und Zitat', async () => {
        routen['/api/email/beautify'] = { body: { body: 'Erster Absatz.\n\nZweiter Absatz.' } };
        render(<EmailComposeForm onClose={() => {}} replyEmailId={5} antwortPostfach={null}
            replyQuote='<div class="email-quote">Alte Nachricht</div>' initialRecipient="kunde@example.org" initialSubject="Re: x" />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        await waitFor(() => expect(editor.innerHTML).toContain('Alte Nachricht'));
        editor.innerHTML = '<p>bitte schöner</p>' + editor.innerHTML;
        await userEvent.click(screen.getByRole('button', { name: /KI-Optimierung/ }));
        await waitFor(() => expect(editor.innerHTML).toContain('<p>Erster Absatz.</p><p>Zweiter Absatz.</p>'));
        expect(editor.innerHTML).toContain('Alte Nachricht');
        const anfrage = aufrufe.find(a => a.url === '/api/email/beautify')!;
        expect(JSON.parse(String(anfrage.init?.body))).toMatchObject({ body: 'bitte schöner', context: 'Alte Nachricht' });
    });

    it('übernimmt HTML-Vorschläge und erkennt Zitate am Stil', async () => {
        routen['/api/email/beautify'] = { body: { body: '<p>Fertig.</p>' } };
        render(<EmailComposeForm onClose={() => {}} />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        await waitFor(() => expect(editor.innerHTML).toContain('Viele Grüße'));
        editor.innerHTML = '<p>Text</p><div style="border-left: 3px solid #ccc">Zitat</div>';
        await userEvent.click(screen.getByRole('button', { name: /KI-Optimierung/ }));
        await waitFor(() => expect(editor.innerHTML).toContain('<p>Fertig.</p>'));
        expect(editor.innerHTML).toContain('Zitat');
    });

    it('meldet leere Texte, leere Vorschläge und Fehler der KI', async () => {
        render(<EmailComposeForm onClose={() => {}} projektId={41} />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        await waitFor(() => expect(editor.innerHTML).toContain('Viele Grüße'));
        editor.innerHTML = '<div class="email-signature">Gruß</div>';
        await userEvent.click(screen.getByRole('button', { name: /KI-Optimierung/ }));
        expect(await screen.findByText('Kein Text zum Umformulieren gefunden.')).toBeInTheDocument();

        editor.innerHTML = '<p>Hallo</p>';
        routen['/api/email/beautify'] = { body: { body: '  ' } };
        await userEvent.click(screen.getByRole('button', { name: /KI-Optimierung/ }));
        expect(await screen.findByText('Keine alternative Formulierung erhalten.')).toBeInTheDocument();

        routen['/api/email/beautify'] = { status: 500, body: {} };
        await userEvent.click(screen.getByRole('button', { name: /KI-Optimierung/ }));
        expect(await screen.findByText('Formulierung fehlgeschlagen. Bitte erneut versuchen.')).toBeInTheDocument();
    });

    it('merkt Eingaben im Text als Änderung', async () => {
        render(<EmailComposeForm onClose={() => {}} />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        editor.innerHTML = '<p>Neu</p>';
        fireEvent.input(editor);
        expect(await screen.findByText('Änderungen noch nicht gespeichert')).toBeInTheDocument();
    });
});

describe('EmailComposeForm – Anhänge', () => {
    const dateiInput = () => document.querySelector('input[type="file"]') as HTMLInputElement;

    it('nimmt Dateien per Auswahl und Ablegen an, zeigt Vorschauen und entfernt sie', async () => {
        render(<EmailComposeForm onClose={() => {}} />);
        const pdf = new File(['%PDF'], 'angebot.pdf', { type: 'application/pdf' });
        const bild = new File(['x'], 'foto.png', { type: 'image/png' });
        await act(async () => { fireEvent.change(dateiInput(), { target: { files: [pdf] } }); });
        expect(await screen.findByText('angebot.pdf')).toBeInTheDocument();

        await act(async () => {
            fireEvent.dragOver(screen.getByText('Dateien hier ablegen oder klicken'));
            fireEvent.drop(screen.getByText('Dateien hier ablegen oder klicken'), { dataTransfer: { files: [bild] } });
        });
        expect(await screen.findByText('foto.png')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau für angebot.pdf' }));
        expect(screen.getByTestId('pdf-viewer')).toHaveTextContent('blob:vorschau');
        await userEvent.click(screen.getByRole('button', { name: 'Vorschau schließen' }));

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau für foto.png' }));
        expect(screen.getByRole('img', { name: 'foto.png' })).toHaveAttribute('src', 'blob:vorschau');
        await userEvent.click(screen.getByRole('button', { name: 'Bildvorschau schließen' }));

        await userEvent.click(screen.getByRole('button', { name: 'angebot.pdf entfernen' }));
        expect(screen.queryByText('angebot.pdf')).not.toBeInTheDocument();
        // Leere Auswahl ändert nichts.
        await act(async () => { fireEvent.change(dateiInput(), { target: { files: [] } }); });
        expect(screen.getByText('foto.png')).toBeInTheDocument();
    });

    it('lehnt Dateien über dem Limit ab', async () => {
        render(<EmailComposeForm onClose={() => {}} initialRecipient="kunde@example.org" initialSubject="Pläne" />);
        const gross = new File(['x'], 'plan.pdf', { type: 'application/pdf' });
        Object.defineProperty(gross, 'size', { value: MAX_ATTACHMENT_BYTES + 1 });
        await act(async () => { fireEvent.change(dateiInput(), { target: { files: [gross] } }); });
        expect(await screen.findByText(/Anhänge dürfen zusammen höchstens/)).toBeInTheDocument();
        expect(screen.queryByText('plan.pdf')).not.toBeInTheDocument();
    });

    it('sperrt das Senden, wenn mitgegebene Anhänge zu groß sind, und zeigt die PDF-Vorschau', async () => {
        const gross = new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
        Object.defineProperty(gross, 'size', { value: MAX_ATTACHMENT_BYTES + 1 });
        const zweite = new File(['x'], 'anlage.txt', { type: 'text/plain' });
        render(<EmailComposeForm onClose={() => {}} initialAttachments={[gross, zweite]} initialRecipient="kunde@example.org" initialSubject="Rechnung" />);
        expect(await screen.findByText('2 Dateien automatisch angehängt')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /E-Mail senden/ })).toBeDisabled();
        await userEvent.click(screen.getByRole('button', { name: /PDF-Vorschau öffnen/ }));
        expect(screen.getByTestId('pdf-viewer')).toBeInTheDocument();
    });

    it('hängt Projekt-Dateien und Bautagebuch-Bilder an und wieder ab', async () => {
        routen['/api/projekte/41/dokumente'] = { body: [
            { id: 1, originalDateiname: 'aufmass.pdf', url: '/dateien/1', dateityp: 'application/pdf', dokumentGruppe: 'PLANUNGSDOKUMENTE' },
            { id: 2, originalDateiname: 'kaputt.pdf', url: '/dateien/2?v=1', dokumentGruppe: 'PLANUNGSDOKUMENTE' },
        ] };
        routen['/api/projekte/41/notizen'] = { body: [{ bilder: [{ id: 9, url: '/bilder/9', originalDateiname: 'baustelle.jpg' }] }, {}] };
        routen['/dateien/1'] = { roh: { ok: true, status: 200, blob: async () => new Blob(['%PDF'], { type: 'application/pdf' }) } as unknown as Response };
        routen['/dateien/2'] = { status: 404, body: {} };
        render(<EmailComposeForm onClose={() => {}} projektId={41} />);

        await userEvent.click(await screen.findByRole('button', { name: /Dateien aus Projekt hinzufügen/ }));
        const auswahl = screen.getByText('Projekt-Dateien').closest('div.flex.h-\\[82vh\\]') as HTMLElement;
        await userEvent.click(within(auswahl).getByText('Planungsdokumente'));
        await userEvent.click(within(auswahl).getByText('Bautagebuch'));
        const checkboxAufmass = within(auswahl).getByText('aufmass.pdf').closest('label')!.querySelector('input')!;
        await userEvent.click(checkboxAufmass);
        await waitFor(() => expect(within(auswahl).getByText('1 Datei(en) ausgewählt')).toBeInTheDocument());
        await userEvent.click(within(auswahl).getByText('kaputt.pdf').closest('label')!.querySelector('input')!);
        expect(await screen.findByText('"kaputt.pdf" konnte nicht angehängt werden.')).toBeInTheDocument();
        await userEvent.click(checkboxAufmass);
        await waitFor(() => expect(within(auswahl).getByText('0 Datei(en) ausgewählt')).toBeInTheDocument());
        await userEvent.click(within(auswahl).getByRole('button', { name: 'Auswahl übernehmen' }));
        expect(screen.queryByText('Projekt-Dateien')).not.toBeInTheDocument();
        expect(aufrufe.some(a => a.url === '/dateien/2?v=1&download=true')).toBe(true);
    });
});

describe('EmailComposeForm – Adresse speichern', () => {
    it('speichert eine neue Adresse beim Kunden', async () => {
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} kundeId={5} initialRecipient="neu@example.org" initialSubject="Hallo" />);
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Beim Kunden speichern/ }));
        await waitFor(() => expect(onClose).toHaveBeenCalled());
        const call = aufrufe.find(a => a.url === '/api/kunden/5/emails')!;
        expect(JSON.parse(String(call.init?.body))).toEqual({ email: 'neu@example.org' });
    });

    it('speichert eine neue Adresse bei der Anfrage', async () => {
        routen['/api/anfragen/12'] = { body: { bauvorhaben: 'Treppe', kundenEmails: [] } };
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} anfrageId={12} anfrage={{ bauvorhaben: 'Treppe' }} initialRecipient="neu@example.org" initialSubject="Hallo" />);
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Als Anfrage-E-Mail speichern/ }));
        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(aufrufe.some(a => a.url === '/api/anfragen/12/emails')).toBe(true);
    });

    it('lässt die Adresse auf Wunsch weg und übersteht Speicherfehler', async () => {
        const onClose = vi.fn();
        const { unmount } = render(<EmailComposeForm onClose={onClose} kundeId={5} initialRecipient="neu@example.org" initialSubject="Hallo" />);
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Nicht speichern/ }));
        expect(onClose).toHaveBeenCalledTimes(1);
        unmount();

        routen['/api/kunden/5/emails'] = () => { throw new Error('offline'); };
        render(<EmailComposeForm onClose={onClose} kundeId={5} initialRecipient="neu@example.org" initialSubject="Hallo" />);
        await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/ }));
        await userEvent.click(await screen.findByRole('button', { name: /Beim Kunden speichern/ }));
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(2));
    });
});

describe('EmailComposeForm – Entwurf laden', () => {
    it('bietet bei Ladefehlern „Erneut laden“ und „Schließen“', async () => {
        let versuche = 0;
        routen['/api/emails/drafts/55'] = () => (++versuche === 1
            ? { status: 500, body: {} }
            : { body: { id: 55, subject: 'Wiedergefunden', recipient: 'kunde@example.org', projektId: null, attachments: [] } });
        const onClose = vi.fn();
        render(<EmailComposeForm onClose={onClose} draftId={55} />);
        expect((await screen.findAllByText('Entwurf konnte nicht geladen werden. Bitte erneut versuchen.')).length).toBeGreaterThan(0);
        await userEvent.click(screen.getByRole('button', { name: 'Erneut laden' }));
        expect(await screen.findByDisplayValue('Wiedergefunden')).toBeInTheDocument();
    });

    it('stellt Anfrage-Zuordnung und CC eines Entwurfs wieder her', async () => {
        routen['/api/emails/drafts/55'] = { body: { id: 55, subject: 'Entwurf', recipient: 'kunde@example.org', cc: 'kopie@example.org', anfrageId: 12, attachments: [] } };
        render(<EmailComposeForm onClose={() => {}} draftId={55} zuordnungWaehlbar />);
        expect(await screen.findByDisplayValue('kopie@example.org')).toBeInTheDocument();
        expect(screen.getAllByText('Anfrage').length).toBeGreaterThan(0);
    });
});
