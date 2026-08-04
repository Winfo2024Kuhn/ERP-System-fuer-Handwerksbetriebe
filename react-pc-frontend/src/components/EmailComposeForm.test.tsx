import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EmailComposeForm } from './EmailComposeForm';

/**
 * Regressionstests fuer die Rueckfrage "E-Mail-Adresse speichern?".
 *
 * Hintergrund: Beim Antworten stand im Empfaengerfeld `"name" <adresse>`.
 * Verglichen wurde dieser komplette String gegen die gespeicherten (reinen)
 * Projekt-Adressen – der Abgleich schlug immer fehl, die Rueckfrage kam bei
 * jeder Antwort, und beim Speichern landete der komplette String in der DB.
 *
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 42;
const BEKANNTE_ADRESSE = 'max.mustermann@example.com';

/** Sammelt alle POST-Aufrufe, damit der gespeicherte Wert geprueft werden kann. */
let gesendeteRequests: Array<{ url: string; body: unknown }>;

/** Antwort auf /api/email/dokument-absender; pro Test umstellbar. */
let dokumentAbsenderAntwort: { aktiv: boolean; address: string | null } = { aktiv: false, address: null };

function mockFetch() {
    return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        const method = init?.method || 'GET';

        if (method === 'POST' || method === 'PUT') {
            let body: unknown = null;
            if (typeof init?.body === 'string') {
                try { body = JSON.parse(init.body); } catch { body = init.body; }
            }
            gesendeteRequests.push({ url, body });
        }

        if (url.startsWith('/api/projekte/simple')) {
            return jsonResponse([{ id: PROJEKT_ID, bauvorhaben: 'Musterbau', auftragsnummer: 'A-1', kunde: 'Max Mustermann' }]);
        }
        // Der paginierte Endpunkt (page-Param) liefert die Liste im Feld "anfragen"
        if (url.startsWith('/api/anfragen?') || url === '/api/anfragen') {
            return jsonResponse({
                anfragen: [{ id: 7, bauvorhaben: 'Musteranfrage', anfragesnummer: 'AN-1', kundenName: 'Max Mustermann' }],
                gesamt: 1,
                seite: 0,
                seitenGroesse: 50,
            });
        }
        if (url === `/api/projekte/${PROJEKT_ID}`) {
            return jsonResponse({ id: PROJEKT_ID, bauvorhaben: 'Musterbau', kundenEmails: [BEKANNTE_ADRESSE] });
        }
        if (url.startsWith('/api/email/from-addresses')) {
            return jsonResponse(['firma@example.com']);
        }
        if (url.startsWith('/api/email/dokument-absender')) {
            return jsonResponse(dokumentAbsenderAntwort);
        }
        if (url.startsWith('/api/email/signatures/default')) {
            return new Response(null, { status: 204 });
        }
        if (url.startsWith(`/api/projekte/${PROJEKT_ID}/dokumente`)) {
            return jsonResponse([]);
        }
        if (url.startsWith('/api/emails/contacts')) {
            return jsonResponse([]);
        }
        if (url.startsWith('/api/emails/') || url === '/api/emails/send') {
            return jsonResponse({ id: 999 });
        }
        if (url.startsWith('/api/emails/drafts')) {
            return jsonResponse({ id: 1 });
        }
        return jsonResponse({});
    });
}

function jsonResponse(data: unknown) {
    return new Response(JSON.stringify(data), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
    });
}

async function sendeAb() {
    await userEvent.click(screen.getByRole('button', { name: /E-Mail senden/i }));
}

describe('EmailComposeForm – Rueckfrage "E-Mail-Adresse speichern?"', () => {
    beforeEach(() => {
        gesendeteRequests = [];
        vi.stubGlobal('fetch', mockFetch());
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
        dokumentAbsenderAntwort = { aktiv: false, address: null };
    });

    it('fragt nicht nach, wenn die Antwort an eine bereits gespeicherte Adresse geht', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialRecipient={`"${BEKANNTE_ADRESSE}" <${BEKANNTE_ADRESSE}>`}
                initialSubject="AW: Zeichnungsentwurf"
                replyEmailId={7}
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('AW: Zeichnungsentwurf')).toBeInTheDocument());
        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/7/reply')).toBe(true)
        );
        expect(screen.queryByText('E-Mail-Adresse speichern?')).not.toBeInTheDocument();
    });

    it('speichert nur die reine Adresse, wenn der Empfaenger neu ist', async () => {
        const neueAdresse = 'erika.musterfrau@example.com';
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialRecipient={`"Erika Musterfrau" <${neueAdresse}>`}
                initialSubject="AW: Zeichnungsentwurf"
                replyEmailId={7}
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('AW: Zeichnungsentwurf')).toBeInTheDocument());
        await sendeAb();

        expect(await screen.findByText('E-Mail-Adresse speichern?')).toBeInTheDocument();
        // Angezeigt und gespeichert wird die reine Adresse, nicht `"Name" <Adresse>`
        expect(screen.getByText(neueAdresse)).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /Als Projekt-E-Mail speichern/i }));

        await waitFor(() => {
            const gespeichert = gesendeteRequests.find(r => r.url === `/api/projekte/${PROJEKT_ID}/emails`);
            expect(gespeichert?.body).toEqual({ email: neueAdresse });
        });
    });

    it('bietet das Speichern nicht an, wenn mehrere Adressen im Feld stehen', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialRecipient="erika.musterfrau@example.com, john.doe@example.com"
                initialSubject="Sammelmail"
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Sammelmail')).toBeInTheDocument());
        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(true)
        );
        expect(screen.queryByText('E-Mail-Adresse speichern?')).not.toBeInTheDocument();
    });
});

/**
 * Zuordnung zu Projekt/Anfrage beim freien Schreiben (E-Mail-Center).
 * Alle Daten sind Dummy-Daten (DSGVO).
 */
describe('EmailComposeForm – Projekt/Anfrage verknüpfen', () => {
    beforeEach(() => {
        gesendeteRequests = [];
        vi.stubGlobal('fetch', mockFetch());
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('warnt vor dem Senden, wenn weder Projekt noch Anfrage verknüpft ist', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialRecipient="lieferant@example.com"
                initialSubject="Materialbestellung"
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Materialbestellung')).toBeInTheDocument());
        await sendeAb();

        expect(await screen.findByText('Kein Projekt und keine Anfrage verknüpft')).toBeInTheDocument();
        expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(false);
    });

    it('sendet nach "Trotzdem senden" ohne Verknüpfung', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialRecipient="lieferant@example.com"
                initialSubject="Materialbestellung"
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Materialbestellung')).toBeInTheDocument());
        await sendeAb();
        await screen.findByText('Kein Projekt und keine Anfrage verknüpft');
        await userEvent.click(screen.getByRole('button', { name: /Trotzdem senden/i }));

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(true)
        );
    });

    it('sendet ohne Rueckfrage, sobald ein Projekt ausgewaehlt wurde', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialRecipient="lieferant@example.com"
                initialSubject="Materialbestellung"
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Materialbestellung')).toBeInTheDocument());
        await userEvent.click(screen.getByRole('button', { name: /Projekt oder Anfrage suchen/i }));
        await userEvent.click(await screen.findByText('Musterbau'));

        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(true)
        );
        expect(screen.queryByText('Kein Projekt und keine Anfrage verknüpft')).not.toBeInTheDocument();
    });

    it('laesst die Zuordnung eines wieder geoeffneten Entwurfs weiter aendern', async () => {
        // E-Mail-Center reicht die im Entwurf gespeicherte projektId durch –
        // das Feld muss trotzdem sichtbar und aenderbar bleiben.
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialSubject="Entwurf"
                draftId={1}
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Entwurf')).toBeInTheDocument());
        expect(await screen.findByText('Musterbau')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Ändern/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Verknüpfung entfernen/i })).toBeInTheDocument();
    });

    it('uebernimmt den Kunden-Empfaenger des neu gewaehlten Projekts', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialSubject="Anfrage Material"
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Anfrage Material')).toBeInTheDocument());
        await userEvent.click(screen.getByRole('button', { name: /Projekt oder Anfrage suchen/i }));
        await userEvent.click(await screen.findByText('Musterbau'));

        await waitFor(() => expect(screen.getByDisplayValue(BEKANNTE_ADRESSE)).toBeInTheDocument());
    });

    it('laesst einen selbst getippten Empfaenger beim Wechsel der Zuordnung stehen', async () => {
        // Sonst ginge die Mail still an den Kunden des neu gewaehlten Projekts.
        const eigeneAdresse = 'erika.musterfrau@example.com';
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialSubject="Anfrage Material"
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Anfrage Material')).toBeInTheDocument());
        await userEvent.type(
            screen.getByPlaceholderText('Name, Firma oder E-Mail eingeben'),
            eigeneAdresse
        );

        await userEvent.click(screen.getByRole('button', { name: /Projekt oder Anfrage suchen/i }));
        await userEvent.click(await screen.findByText('Musterbau'));

        // Zuordnung ist da, der getippte Empfaenger bleibt aber unveraendert
        expect(await screen.findByText('Musterbau')).toBeInTheDocument();
        expect(screen.getByDisplayValue(eigeneAdresse)).toBeInTheDocument();
        expect(screen.queryByDisplayValue(BEKANNTE_ADRESSE)).not.toBeInTheDocument();
    });

    it('zeigt beim Antworten kein Entfernen der Verknuepfung an', async () => {
        // Der Reply-Endpunkt erbt den Vorgang der Ursprungsmail – ein Entfernen
        // im Formular haette keine Wirkung.
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialRecipient={BEKANNTE_ADRESSE}
                initialSubject="AW: Zwischenstand"
                replyEmailId={7}
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('AW: Zwischenstand')).toBeInTheDocument());
        expect(await screen.findByText('Musterbau')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Verknüpfung entfernen/i })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Ändern/i })).toBeInTheDocument();
    });

    it('fragt beim Antworten nicht nach, auch ohne eigene Verknuepfung', async () => {
        // Eine Antwort erbt die Zuordnung der Ursprungsmail – die Rueckfrage
        // waere dort fachlich falsch.
        render(
            <EmailComposeForm
                onClose={() => {}}
                initialRecipient="lieferant@example.com"
                initialSubject="AW: Materialbestellung"
                replyEmailId={7}
                zuordnungWaehlbar
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('AW: Materialbestellung')).toBeInTheDocument());
        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/7/reply')).toBe(true)
        );
        expect(screen.queryByText('Kein Projekt und keine Anfrage verknüpft')).not.toBeInTheDocument();
    });

    it('blendet die Suche aus, wenn direkt aus einem Projekt geschrieben wird', async () => {
        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                initialRecipient={BEKANNTE_ADRESSE}
                initialSubject="Zwischenstand"
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Zwischenstand')).toBeInTheDocument());
        expect(screen.queryByRole('button', { name: /Projekt oder Anfrage suchen/i })).not.toBeInTheDocument();

        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(true)
        );
        expect(screen.queryByText('Kein Projekt und keine Anfrage verknüpft')).not.toBeInTheDocument();
    });
});

/**
 * Beim Versand aus dem Dokument-Editor darf der Absender nicht frei waehlbar
 * sein, sobald ein eigenes Postfach fuer Geschaeftsdokumente laeuft: Eine
 * Adresse aus der allgemeinen Liste passt dann nicht zum versendenden Postfach
 * und die Mail scheitert beim Empfaenger an SPF/DKIM.
 */
describe('EmailComposeForm – Absender bei Geschaeftsdokumenten', () => {
    beforeEach(() => {
        gesendeteRequests = [];
        vi.stubGlobal('fetch', mockFetch());
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
        dokumentAbsenderAntwort = { aktiv: false, address: null };
    });

    it('zeigt den festen Absender an und laesst ihn nicht aendern', async () => {
        dokumentAbsenderAntwort = { aktiv: true, address: 'rechnungen@musterfirma-beispiel.de' };

        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                geschaeftsdokument
                initialSubject="Rechnung RE-2026/07/0001"
            />
        );

        const vonFeld = await screen.findByDisplayValue('rechnungen@musterfirma-beispiel.de');
        expect(vonFeld).toHaveAttribute('readonly');
        expect(screen.getByText(/lässt sich hier deshalb nicht ändern/i)).toBeInTheDocument();
    });

    it('laesst die freie Auswahl, solange kein eigenes Postfach eingerichtet ist', async () => {
        dokumentAbsenderAntwort = { aktiv: false, address: null };

        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                geschaeftsdokument
                initialSubject="Rechnung RE-2026/07/0001"
            />
        );

        await waitFor(() => expect(screen.getByDisplayValue('Rechnung RE-2026/07/0001')).toBeInTheDocument());
        expect(screen.queryByDisplayValue('rechnungen@musterfirma-beispiel.de')).not.toBeInTheDocument();
        expect(screen.queryByText(/lässt sich hier deshalb nicht ändern/i)).not.toBeInTheDocument();
    });

    it('meldet dem Backend, dass es sich um ein Geschaeftsdokument handelt', async () => {
        dokumentAbsenderAntwort = { aktiv: true, address: 'rechnungen@musterfirma-beispiel.de' };

        render(
            <EmailComposeForm
                onClose={() => {}}
                projektId={PROJEKT_ID}
                geschaeftsdokument
                initialRecipient={BEKANNTE_ADRESSE}
                initialSubject="Rechnung RE-2026/07/0001"
            />
        );

        await screen.findByDisplayValue('rechnungen@musterfirma-beispiel.de');
        await sendeAb();

        await waitFor(() =>
            expect(gesendeteRequests.some(r => r.url === '/api/emails/send')).toBe(true)
        );
    });
});
