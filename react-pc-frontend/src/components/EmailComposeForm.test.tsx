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

        if (url === `/api/projekte/${PROJEKT_ID}`) {
            return jsonResponse({ id: PROJEKT_ID, bauvorhaben: 'Musterbau', kundenEmails: [BEKANNTE_ADRESSE] });
        }
        if (url.startsWith('/api/email/from-addresses')) {
            return jsonResponse(['firma@example.com']);
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
