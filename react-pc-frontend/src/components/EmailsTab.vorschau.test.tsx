import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab from './EmailsTab';
import { ToastProvider } from './ui/toast';
import { mitReiter, reiterEintrag, type ReiterEintragRoh } from '../features/email/emailsTab/emailsTabTestApi';

const previewCalls = vi.hoisted(() => ({ count: 0 }));
vi.mock('../features/email/threadQuotes', async importOriginal => {
    const actual = await importOriginal<typeof import('../features/email/threadQuotes')>();
    return {
        ...actual,
        getThreadPreview: (...args: Parameters<typeof actual.getThreadPreview>) => {
            previewCalls.count++;
            return actual.getThreadPreview(...args);
        },
    };
});

afterEach(() => vi.unstubAllGlobals());

/**
 * Die Vorschauzeile im E-Mail-Reiter (Kunde, Projekt, Anfrage, Lieferant) zeigt nur die
 * neue Antwort, wie die Vorschau im E-Mail-Center. Dummy-Daten (DSGVO).
 */
const tab = () => (
    <MemoryRouter>
        <ToastProvider>
            <EmailsTab bezug="projekt" bezugId={1} showComposeButton={false} showReplyButton={false} />
        </ToastProvider>
    </MemoryRouter>
);

function stubbe(eintraege: ReiterEintragRoh[]) {
    vi.stubGlobal('fetch', vi.fn(mitReiter(eintraege)));
}

async function vorschauVon(bodyHtml: string): Promise<string> {
    stubbe([reiterEintrag(1, { subject: 'Anfrage Geländer', bodyHtml })]);
    render(tab());
    return (await screen.findByText(/Neue Antwort/)).textContent || '';
}

describe('EmailsTab – Vorschau ohne zitierten Verlauf', () => {
    it('Gmail mehrstufig: nur die neue Antwort', async () => {
        const html = '<div>Neue Antwort</div><div class="gmail_quote gmail_quote_container"><div class="gmail_attr">Max Mustermann &lt;max@example.com&gt; schrieb am Do., 9. Okt. 2026 um 10:00:</div><blockquote class="gmail_quote"><div>ZWEITE Mail</div><div class="gmail_quote"><div class="gmail_attr">Erika Musterfrau &lt;erika@example.com&gt; schrieb am Mi., 8. Okt. 2026 um 09:00:</div><blockquote class="gmail_quote">ERSTE Mail</blockquote></div></blockquote></div>';
        expect(await vorschauVon(html)).toBe('Neue Antwort');
    });

    it('Yahoo mit ydp-Präfix: nur die neue Antwort', async () => {
        const html = '<div>Neue Antwort</div><div id="ydp1yahoo_quoted_1" class="ydp8a8a0641yahoo_quoted"><div>Am Dienstag, 7. Oktober 2026 um 10:00:00 MESZ hat Max Mustermann &lt;max@example.com&gt; Folgendes geschrieben:</div><div>ALTE Mail</div></div>';
        expect(await vorschauVon(html)).toBe('Neue Antwort');
    });

    it('WEB.DE/GMX-App: Einleitung aus mehreren Teilen verschwindet mit dem Zitat', async () => {
        const html = '<div>Neue Antwort</div><div class="mail_android_quote">Am 09.10.26, 10:00 schrieb Max Mustermann &lt;<a href="mailto:max@example.com">max@example.com</a>&gt;:<blockquote class="gmail_quote">ALTE Mail</blockquote></div>';
        expect(await vorschauVon(html)).toBe('Neue Antwort');
    });

    it('Klartext mit abschließenden „>“-Zeilen: nur die neue Antwort', async () => {
        const html = '<pre>Neue Antwort\n\n&gt; Gesendet: Donnerstag, 09.10.2026 um 10:00 Uhr\n&gt; Von: "Max Mustermann" &lt;max@example.com&gt;\n&gt;\n&gt; ALTE Mail</pre>';
        expect(await vorschauVon(html)).toBe('Neue Antwort');
    });
});

describe('EmailsTab – Vorschau wird nicht bei jedem Rendern neu berechnet', () => {
    it('rechnet die Vorschau einmal je geladener Seite, nicht bei jedem Rendern', async () => {
        stubbe([1, 2, 3].map(id => reiterEintrag(id)));
        const vorher = previewCalls.count;
        const { rerender } = render(tab());
        await screen.findByText('Text 3');
        const nachDemLaden = previewCalls.count;
        expect(nachDemLaden - vorher).toBe(3);
        rerender(tab());
        rerender(tab());
        expect(previewCalls.count).toBe(nachDemLaden);
    });
});
