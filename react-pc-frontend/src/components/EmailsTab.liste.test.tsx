import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EmailsTab, { type EmailsTabProps } from './EmailsTab';
import { ToastProvider } from './ui/toast';
import {
    istReiterUrl, jsonAntwort, reiterEintrag, reiterSeite,
} from '../features/email/emailsTab/emailsTabTestApi';

/**
 * Mailliste im E-Mail-Reiter: lädt selbst seitenweise vom Server, „Mehr laden“,
 * Suche und Filter fragen den Server und springen auf Seite 1, späte Antworten
 * werden verworfen; Laden, leer und Fehler sehen verschieden aus.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */

vi.mock('./EmailComposeModal', () => ({
    EmailComposeModal: ({ onSuccess }: { onSuccess: () => void }) => <button onClick={onSuccess}>Neue E-Mail gesendet</button>,
}));

interface Offen { url: string; signal?: AbortSignal | null; loesen: (r: Response) => void }
let offen: Offen[];
let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    offen = [];
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
        if (!istReiterUrl(url)) return jsonAntwort(null, 404);
        return new Promise<Response>(loesen => offen.push({ url, signal: init?.signal, loesen }));
    });
    vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const reiterAufrufe = () => fetchMock.mock.calls.map(c => c[0] as string).filter(istReiterUrl);

/** Beantwortet die zuletzt gestellte (bzw. die passende) Listen-Abfrage. */
async function antworte(body: unknown, status = 200, welche: (o: Offen) => boolean = () => true) {
    const ziel = [...offen].reverse().find(welche);
    if (!ziel) throw new Error('Keine offene Abfrage');
    offen = offen.filter(o => o !== ziel);
    await act(async () => { ziel.loesen(await jsonAntwort(body, status)); });
}

function rendere(props: Partial<EmailsTabProps> = {}) {
    return render(
        <MemoryRouter>
            <ToastProvider>
                <EmailsTab bezug="projekt" bezugId={12} showComposeButton={false} showReplyButton={false} {...props} />
            </ToastProvider>
        </MemoryRouter>,
    );
}

const zeilen = () => screen.queryAllByRole('button', { name: /^Anfrage \d+$/ });

describe('EmailsTab – Liste vom Server', () => {
    it('zeigt beim Laden Platzhalter, dann die erste Seite mit Zähler, „Mehr laden“ hängt an', async () => {
        const { container } = rendere();
        expect(reiterAufrufe()).toEqual(['/api/emails/reiter/projekt/12?page=0&size=25']);
        expect(container.querySelectorAll('.motion-safe\\:animate-pulse').length).toBeGreaterThan(0);
        expect(screen.queryByText('Keine E-Mails vorhanden.')).not.toBeInTheDocument();

        await antworte(reiterSeite([reiterEintrag(1), reiterEintrag(2)], 3));
        expect(zeilen()).toHaveLength(2);
        expect(container.querySelector('.motion-safe\\:animate-pulse')).toBeNull();
        expect(screen.getByText('2 von 3 angezeigt')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        expect(reiterAufrufe()[1]).toBe('/api/emails/reiter/projekt/12?page=1&size=25');
        // Beim Nachladen bleibt die Liste stehen, nur der Knopf ist gesperrt.
        expect(zeilen()).toHaveLength(2);
        expect(screen.getByRole('button', { name: 'Mehr laden' })).toBeDisabled();

        await antworte(reiterSeite([reiterEintrag(3)], 3, 1));
        expect(zeilen().map(z => z.textContent)).toEqual([
            expect.stringContaining('Anfrage 1'), expect.stringContaining('Anfrage 2'), expect.stringContaining('Anfrage 3'),
        ]);
        expect(screen.getByText('3 von 3 angezeigt')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Mehr laden' })).not.toBeInTheDocument();
    });

    it('zeigt den Verlauf-Chip ab 2 Nachrichten und die Vorschau ohne Zitat', async () => {
        rendere();
        await antworte(reiterSeite([reiterEintrag(1, {
            nachrichtenAnzahl: 3, bodyHtml: '<p>Passt so</p><blockquote class="gmail_quote">ALT</blockquote>',
        })]));
        expect(screen.getByText('3 Nachrichten')).toBeInTheDocument();
        expect(screen.getByText('Passt so')).toBeInTheDocument();
        expect(screen.queryByText(/ALT/)).not.toBeInTheDocument();
    });

    it('sucht entprellt beim Server und springt auf Seite 1; späte Antworten zählen nicht', async () => {
        rendere();
        await antworte(reiterSeite([reiterEintrag(1), reiterEintrag(2)], 30));
        fireEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        const nachladen = offen[0];

        const suche = screen.getByRole('textbox', { name: 'E-Mails durchsuchen' });
        fireEvent.change(suche, { target: { value: 'a' } });
        fireEvent.change(suche, { target: { value: 'Angebot' } });
        await waitFor(() => expect(reiterAufrufe()).toContain('/api/emails/reiter/projekt/12?page=0&size=25&q=Angebot'));
        // Ein Zeichen allein fragt den Server nicht; die alte Nachlade-Abfrage ist abgebrochen.
        expect(reiterAufrufe().some(u => u.includes('q=a&') || u.endsWith('q=a'))).toBe(false);
        expect(nachladen.signal?.aborted).toBe(true);

        // Späte Antwort der alten Abfrage wird verworfen
        await act(async () => { nachladen.loesen(await jsonAntwort(reiterSeite([reiterEintrag(9)], 30, 1))); });
        await antworte(reiterSeite([reiterEintrag(5)], 1), 200, o => o.url.includes('q=Angebot'));
        expect(zeilen().map(z => within(z).getByText(/^Anfrage/).textContent)).toEqual(['Anfrage 5']);
        expect(screen.getByText('1 von 1 angezeigt')).toBeInTheDocument();
    });

    it('filtert beim Server nach Richtung, Seite 1, Suche bleibt', async () => {
        rendere();
        await antworte(reiterSeite([reiterEintrag(1)]));
        fireEvent.change(screen.getByRole('textbox', { name: 'E-Mails durchsuchen' }), { target: { value: 'Geländer' } });
        await waitFor(() => expect(reiterAufrufe()).toHaveLength(2));
        await antworte(reiterSeite([reiterEintrag(1)]));

        fireEvent.click(screen.getByRole('radio', { name: 'Eingang' }));
        expect(reiterAufrufe().at(-1)).toBe('/api/emails/reiter/projekt/12?page=0&size=25&q=Gel%C3%A4nder&richtung=IN');
        await antworte(reiterSeite([]));
        expect(screen.getByText('Keine E-Mails gefunden.')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('radio', { name: 'Ausgang' }));
        expect(reiterAufrufe().at(-1)).toContain('richtung=OUT');
    });

    it('meldet einen leeren Reiter als „Keine E-Mails vorhanden.“ ohne Zähler und ohne Zurücksetzen', async () => {
        rendere();
        await antworte(reiterSeite([]));
        expect(screen.getByText('Keine E-Mails vorhanden.')).toBeInTheDocument();
        expect(screen.queryByText(/angezeigt/)).not.toBeInTheDocument();
        expect(screen.queryByText(/sobald sie einem Projekt/)).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Filter zurücksetzen' })).not.toBeInTheDocument();
    });

    it('sagt im leeren Kunden-Reiter, wann hier E-Mails erscheinen', async () => {
        rendere({ bezug: 'kunde', bezugId: 3 });
        await antworte(reiterSeite([]));
        expect(screen.getByText('Keine E-Mails vorhanden.')).toBeInTheDocument();
        expect(screen.getByText('E-Mails erscheinen hier, sobald sie einem Projekt oder einer Anfrage dieses Kunden zugeordnet sind.'))
            .toBeInTheDocument();
    });

    it('bietet ohne Treffer „Filter zurücksetzen“ an: Suche leer, „Alle“, sofort ab Seite 1', async () => {
        rendere();
        await antworte(reiterSeite([reiterEintrag(1)]));
        const suche = screen.getByRole('textbox', { name: 'E-Mails durchsuchen' });
        fireEvent.change(suche, { target: { value: 'Treppe' } });
        await waitFor(() => expect(reiterAufrufe()).toHaveLength(2));
        await antworte(reiterSeite([]));
        fireEvent.click(screen.getByRole('radio', { name: 'Eingang' }));
        await antworte(reiterSeite([]));
        expect(screen.getByText('Keine E-Mails gefunden.')).toBeInTheDocument();
        expect(screen.getByText('Suche oder Filter ändern.')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }));
        expect(suche).toHaveValue('');
        expect(screen.getByRole('radio', { name: 'Alle' })).toHaveAttribute('aria-checked', 'true');
        // Sofort eine Abfrage ohne Suche und Richtung, nicht erst nach der Suchpause
        expect(reiterAufrufe().at(-1)).toBe('/api/emails/reiter/projekt/12?page=0&size=25');
        const anzahl = reiterAufrufe().length;
        await antworte(reiterSeite([reiterEintrag(1)]));
        expect(zeilen()).toHaveLength(1);
        await act(async () => { await new Promise(r => setTimeout(r, 450)); });
        expect(reiterAufrufe()).toHaveLength(anzahl);
    });

    it('zeigt beim ersten Laden einen Fehler mit „Erneut versuchen“ und Meldung', async () => {
        rendere();
        await antworte({ status: 404, message: 'Projekt nicht gefunden.' }, 404);
        expect(screen.getAllByText('Projekt nicht gefunden.').length).toBeGreaterThanOrEqual(2); // Fehlerfläche + Toast
        expect(screen.queryByText('Keine E-Mails vorhanden.')).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
        await antworte(reiterSeite([reiterEintrag(1)]));
        expect(zeilen()).toHaveLength(1);
        expect(screen.queryByRole('button', { name: 'Erneut versuchen' })).not.toBeInTheDocument();
    });

    it('meldet einen Fehler beim Nachladen per Meldung, die Liste bleibt', async () => {
        rendere();
        await antworte(reiterSeite([reiterEintrag(1)], 2));
        fireEvent.click(screen.getByRole('button', { name: 'Mehr laden' }));
        await antworte({ status: 500, message: 'Die E-Mails konnten nicht geladen werden.' }, 500);
        expect(screen.getByText('Die E-Mails konnten nicht geladen werden.')).toBeInTheDocument();
        expect(zeilen()).toHaveLength(1);
        expect(screen.getByRole('button', { name: 'Mehr laden' })).toBeEnabled();
    });

    it('lädt nach einer neuen E-Mail ab Seite 1 neu, Filter bleibt, und meldet es dem Editor', async () => {
        const onEmailSent = vi.fn();
        rendere({ showComposeButton: true, projektId: 12, onEmailSent });
        await antworte(reiterSeite([reiterEintrag(1)]));
        fireEvent.click(screen.getByRole('radio', { name: 'Ausgang' }));
        await antworte(reiterSeite([reiterEintrag(1, { direction: 'OUT' })]));

        fireEvent.click(screen.getByRole('button', { name: /Neue E-Mail/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Neue E-Mail gesendet' }));
        expect(onEmailSent).toHaveBeenCalledTimes(1);
        expect(reiterAufrufe().at(-1)).toBe('/api/emails/reiter/projekt/12?page=0&size=25&richtung=OUT');
        expect(reiterAufrufe()).toHaveLength(3);
        // Die bisherige Liste bleibt stehen, bis die neue Antwort da ist.
        expect(zeilen()).toHaveLength(1);
        await antworte(reiterSeite([reiterEintrag(2, { direction: 'OUT' }), reiterEintrag(1, { direction: 'OUT' })]));
        expect(zeilen()).toHaveLength(2);
    });

    it('lädt ohne gültige Kennung nichts', () => {
        rendere({ bezugId: 0 });
        expect(reiterAufrufe()).toHaveLength(0);
    });
});

describe('EmailsTab – Erklärung im Kunden-Reiter', () => {
    it('erklärt im Kunden-Reiter, woher die E-Mails kommen', async () => {
        rendere({ bezug: 'kunde', bezugId: 3 });
        expect(reiterAufrufe()).toEqual(['/api/emails/reiter/kunde/3?page=0&size=25']);
        fireEvent.click(screen.getByRole('button', { name: /E-Mails des Kunden/ }));
        expect(screen.getByText(/Hier stehen die E-Mails aus allen Projekten und Anfragen dieses Kunden\./)).toBeInTheDocument();
        expect(screen.getByText(/Die finden Sie im E-Mail-Center\./)).toBeInTheDocument();
    });

    it('zeigt in Projekt, Anfrage und Lieferant keine Erklärung', () => {
        rendere({ bezug: 'lieferant', bezugId: 5 });
        expect(screen.queryByRole('button', { name: /Mehr zu/ })).not.toBeInTheDocument();
    });
});
