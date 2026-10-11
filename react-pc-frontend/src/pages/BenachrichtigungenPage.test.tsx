import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import BenachrichtigungenPage from './BenachrichtigungenPage';
import { ToastProvider } from '../components/ui/toast';
import type { NotificationSummary, RecentItemDto } from '../components/layout/notification-helpers';

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

const SICHERUNG: RecentItemDto = {
    id: 'SICHERUNG:20261011-020017',
    type: 'SICHERUNG',
    title: 'Sicherung nur teilweise',
    subtitle: 'Externe Festplatte nicht gefunden. Bitte anschließen und den Rechner neu starten.\nDatenbank und Cloud-Ordner sind gesichert.',
    timestamp: '2026-10-11T02:00:17',
    link: '',
};

const UPDATE: RecentItemDto = {
    id: 'UPDATE:20261011-030000',
    type: 'UPDATE',
    title: 'Update fehlgeschlagen – alte Version läuft wieder',
    subtitle: 'Version 6f4efa2 -> 48d63f6\nGrund: Neue Version ist nicht sauber hochgefahren\n\nVolles Log auf dem Server: logs/gescheitert-20261011-030000-app.log',
    timestamp: '2026-10-11T03:00:00',
    link: '',
};

const TERMIN: RecentItemDto = {
    id: 'TERMIN:123', type: 'TERMIN', title: 'Aufmaß Bad', subtitle: 'Heute 10:00', timestamp: '2026-10-11', link: '/kalender?date=2026-10-11',
};

const MAIL: RecentItemDto = {
    id: 'EMAIL:456', type: 'EMAIL', title: 'Anfrage Treppe', subtitle: 'Von: Max Mustermann', timestamp: '2026-10-10T14:12:00', link: '/emails/inbox/456',
};

const SPAM: RecentItemDto = {
    id: 'EMAIL:789', type: 'EMAIL', title: 'Gewinnspiel', subtitle: '<script>alert(1)</script>', timestamp: '2026-10-01T08:00:00', link: '/emails/spam/789',
};

const VOLL: NotificationSummary = {
    totalCount: 1240,
    categories: [
        { type: 'TERMINE', label: 'Bevorstehende Termine', count: 1, icon: 'CalendarClock', link: '/kalender' },
        { type: 'EMAILS', label: 'Ungelesene E-Mails', count: 1, icon: 'Mail', link: '/emails/inbox' },
        { type: 'EMAILS_SPAM', label: 'Spam', count: 1234, icon: 'ShieldAlert', link: '/emails/spam' },
        { type: 'SICHERUNG', label: 'Sicherung', count: 1, icon: 'HardDrive', link: '' },
        { type: 'UPDATE', label: 'Update', count: 1, icon: 'RefreshCw', link: '' },
        { type: 'RECHNUNGEN', label: 'Neue Lieferantenrechnungen', count: 2, icon: 'Truck', link: '/offeneposten?tab=eingang' },
    ],
    recentItems: [TERMIN, MAIL, SPAM, SICHERUNG, UPDATE],
};

const LEER: NotificationSummary = { totalCount: 0, categories: [], recentItems: [] };

/** GET /summary liefert die Stände der Reihe nach (der letzte bleibt stehen), POSTs antworten mit `postStatus`. */
function stubbeApi(staende: (NotificationSummary | number)[], postStatus = 204) {
    let abruf = 0;
    mockFetch.mockImplementation((url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
            const body = postStatus === 200 ? JSON.stringify({ erledigt: 2 }) : null;
            return Promise.resolve(new Response(body, { status: postStatus }));
        }
        if (String(url).startsWith('/api/notifications/summary')) {
            const stand = staende[Math.min(abruf, staende.length - 1)];
            abruf += 1;
            if (typeof stand === 'number') return Promise.resolve(new Response('kaputt', { status: stand }));
            return Promise.resolve(new Response(JSON.stringify(stand), { status: 200 }));
        }
        return Promise.resolve(new Response('[]', { status: 200 }));
    });
}

function postAufrufe() {
    return mockFetch.mock.calls
        .filter(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
        .map(([url, init]) => ({ url: String(url), body: JSON.parse(String((init as RequestInit).body)) }));
}

function summaryAbrufe(): string[] {
    return mockFetch.mock.calls.map(([u]) => String(u)).filter(u => u.startsWith('/api/notifications/summary'));
}

function Ort() {
    const { pathname, search } = useLocation();
    return <output data-testid="ort">{pathname + search}</output>;
}

function renderSeite(pfad = '/benachrichtigungen') {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ToastProvider>
                <Routes>
                    <Route path="/benachrichtigungen" element={<BenachrichtigungenPage />} />
                    <Route path="*" element={<p>Andere Seite</p>} />
                </Routes>
                <Ort />
            </ToastProvider>
        </MemoryRouter>,
    );
}

function ueberschriften() {
    return screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent);
}

function themenleiste() {
    return within(screen.getByRole('group', { name: 'Nach Thema filtern' }));
}

beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    mockFetch.mockReset();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 11, 9, 30, 0));
});

afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
});

describe('Seite „Benachrichtigungen“ – Aufbau', () => {
    it('fragt alle Einträge ab und zeigt die Themen, „Sicherung & Updates“ ganz oben', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        expect(await screen.findByRole('heading', { name: 'Sicherung & Updates' })).toBeInTheDocument();
        expect(ueberschriften()).toEqual(['Sicherung & Updates', 'Posteingänge', 'Finanzen', 'Termine']);
        expect(summaryAbrufe()[0]).toBe('/api/notifications/summary?alle=true');
        expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Benachrichtigungen');
        expect(screen.getByText('1.240 neue Meldungen – nach Themen sortiert.')).toBeInTheDocument();
    });

    it('zeigt beim Laden ein Gerüst statt einer leeren Fläche', async () => {
        let antworte: (r: Response) => void = () => {};
        mockFetch.mockImplementation(() => new Promise<Response>(resolve => { antworte = resolve; }));
        renderSeite();

        expect(screen.getByRole('status', { name: 'Benachrichtigungen werden geladen' })).toBeInTheDocument();
        await act(async () => { antworte(new Response(JSON.stringify(LEER), { status: 200 })); });
        expect(await screen.findByText('Keine neuen Benachrichtigungen – alles erledigt.')).toBeInTheDocument();
    });

    it('zeigt den leeren Zustand, wenn nichts offen ist', async () => {
        stubbeApi([LEER]);
        renderSeite();

        expect(await screen.findByText('Keine neuen Benachrichtigungen – alles erledigt.')).toBeInTheDocument();
        expect(screen.queryByRole('group', { name: 'Nach Thema filtern' })).toBeNull();
    });

    it('zeigt einen Fehler mit „Neu laden“ und lädt auf Klick erneut', async () => {
        stubbeApi([500, VOLL]);
        renderSeite();

        const fehler = await screen.findByRole('alert');
        expect(fehler).toHaveTextContent('Benachrichtigungen konnten nicht geladen werden.');
        fireEvent.click(within(fehler).getByRole('button', { name: 'Neu laden' }));

        expect(await screen.findByRole('heading', { name: 'Sicherung & Updates' })).toBeInTheDocument();
        expect(summaryAbrufe()).toHaveLength(2);
    });

    it('zeigt den Zeitpunkt in deutscher Schreibweise', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        const sicherung = await screen.findByText('Sicherung nur teilweise');
        const zeile = sicherung.closest('li') as HTMLElement;
        expect(within(zeile).getByText('heute, 02:00')).toHaveAttribute('dateTime', '2026-10-11T02:00:17');
        expect(screen.getByText('gestern, 14:12')).toBeInTheDocument();
        expect(screen.getByText('01.10.2026, 08:00')).toBeInTheDocument();
        // Termin mit reinem Datum: ohne Uhrzeit.
        expect(within(screen.getByText('Aufmaß Bad').closest('li') as HTMLElement).getByText('heute')).toBeInTheDocument();
    });
});

describe('Sicherung & Updates', () => {
    it('zeigt den mehrzeiligen Text vollständig und ohne „Öffnen“', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        const bereich = await screen.findByRole('region', { name: 'Sicherung & Updates' });
        const text = within(bereich).getByText(/Externe Festplatte nicht gefunden/);
        expect(text.textContent).toBe(SICHERUNG.subtitle);
        expect(text.className).toContain('whitespace-pre-line');
        expect(text.className).not.toContain('line-clamp');
        expect(within(bereich).getByText(/Volles Log auf dem Server/).textContent).toBe(UPDATE.subtitle);

        expect(within(bereich).queryByRole('button', { name: /^Öffnen/ })).toBeNull();
        expect(within(bereich).getByRole('button', { name: 'Erledigt: Sicherung nur teilweise' })).toBeInTheDocument();
        // Hervorgehoben: roter Rand links.
        expect((text.closest('li') as HTMLElement).className).toContain('border-l-red-500');
    });

    it('erklärt per Info-Knopf neben der Überschrift, was nachts passiert', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        const bereich = await screen.findByRole('region', { name: 'Sicherung & Updates' });
        fireEvent.click(within(bereich).getByRole('button', { name: 'Mehr zu: Sicherung & Updates' }));

        const info = screen.getByRole('dialog', { name: 'Sicherung & Updates' });
        expect(info).toHaveTextContent('Was passiert im Hintergrund?');
        expect(info).toHaveTextContent(/Jede Nacht \(standardmäßig um 2 Uhr\) sichert das ERP die komplette Datenbank/);
        expect(info).toHaveTextContent(/Danach \(standardmäßig um 3 Uhr\) schaut das ERP nach einer neuen Version\./);
        expect(info).toHaveTextContent('Was bewirkt das?');
        expect(info).toHaveTextContent(/„Erledigt“ blendet nur die Meldung aus, das Problem bleibt/);
        expect(info).toHaveTextContent('Diese Meldungen sehen nur Admins.');
    });

    it('„Alle erledigt“ meldet beide Kategorien und sagt der Glocke Bescheid', async () => {
        const glocke = vi.fn();
        window.addEventListener('notifications:refresh', glocke);
        const emails = vi.fn();
        window.addEventListener('emails:refresh', emails);
        const nachher: NotificationSummary = {
            ...VOLL,
            totalCount: 1238,
            categories: VOLL.categories.filter(c => !['SICHERUNG', 'UPDATE'].includes(c.type)),
            recentItems: [TERMIN, MAIL, SPAM],
        };
        stubbeApi([VOLL, nachher], 200);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Sicherung & Updates: alle erledigt' }));

        // Sofort weg, ohne auf den Server zu warten.
        expect(screen.queryByRole('heading', { name: 'Sicherung & Updates' })).toBeNull();
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt/kategorien', body: { kategorien: ['SICHERUNG', 'UPDATE'] } },
        ]));
        await waitFor(() => expect(glocke).toHaveBeenCalledTimes(1));
        expect(summaryAbrufe()).toHaveLength(2);
        expect(emails).not.toHaveBeenCalled();
        window.removeEventListener('notifications:refresh', glocke);
        window.removeEventListener('emails:refresh', emails);
    });
});

describe('Erledigt und Öffnen', () => {
    it('„Erledigt“ schickt die id, blendet den Eintrag aus und lädt die Glocke neu', async () => {
        const glocke = vi.fn();
        window.addEventListener('notifications:refresh', glocke);
        const nachher: NotificationSummary = {
            ...VOLL,
            totalCount: 1239,
            categories: VOLL.categories.filter(c => c.type !== 'SICHERUNG'),
            recentItems: VOLL.recentItems.filter(i => i.id !== SICHERUNG.id),
        };
        stubbeApi([VOLL, nachher]);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Erledigt: Sicherung nur teilweise' }));

        expect(screen.queryByText('Sicherung nur teilweise')).toBeNull();
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt', body: { ids: ['SICHERUNG:20261011-020017'] } },
        ]));
        await waitFor(() => expect(glocke).toHaveBeenCalledTimes(1));
        // Nach dem Melden genau ein Neuladen – das eigene Ereignis löst kein zweites aus.
        expect(summaryAbrufe()).toHaveLength(2);
        expect(screen.getByText('Update fehlgeschlagen – alte Version läuft wieder')).toBeInTheDocument();
        expect(screen.getByTestId('ort')).toHaveTextContent('/benachrichtigungen');
        window.removeEventListener('notifications:refresh', glocke);
    });

    it('zeigt bei einem Fehler eine Meldung und holt den Stand vom Server zurück', async () => {
        stubbeApi([VOLL], 500);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Erledigt: Aufmaß Bad' }));

        expect(await screen.findByText('Die Meldung konnte nicht als erledigt markiert werden.')).toBeInTheDocument();
        expect(await screen.findByText('Aufmaß Bad')).toBeInTheDocument();
    });

    it('„Öffnen“ meldet erledigt und springt zum Eintrag', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Öffnen: Aufmaß Bad' }));

        expect(screen.getByTestId('ort')).toHaveTextContent('/kalender?date=2026-10-11');
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt', body: { ids: ['TERMIN:123'] } },
        ]));
    });

    it('„Öffnen“ einer E-Mail lässt danach das E-Mail-Center neu laden', async () => {
        const emails = vi.fn();
        window.addEventListener('emails:refresh', emails);
        stubbeApi([VOLL]);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Öffnen: Anfrage Treppe' }));

        expect(screen.getByTestId('ort')).toHaveTextContent('/emails/inbox/456');
        await waitFor(() => expect(emails).toHaveBeenCalledTimes(1));
        expect(mockFetch.mock.calls.some(([u]) => String(u).includes('mark-read'))).toBe(false);
        window.removeEventListener('emails:refresh', emails);
    });

    it('„Alle erledigt“ der Posteingänge schickt die Mail-Kategorien und lädt das E-Mail-Center neu', async () => {
        const emails = vi.fn();
        window.addEventListener('emails:refresh', emails);
        stubbeApi([VOLL], 200);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Posteingänge: alle erledigt' }));

        expect(screen.queryByText('Anfrage Treppe')).toBeNull();
        expect(screen.getByText('Aufmaß Bad')).toBeInTheDocument();
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt/kategorien', body: { kategorien: ['EMAILS', 'EMAILS_SPAM'] } },
        ]));
        await waitFor(() => expect(emails).toHaveBeenCalledTimes(1));
        window.removeEventListener('emails:refresh', emails);
    });

    it('„Alle erledigt“ ohne Kategorien schickt die ids der Einträge', async () => {
        stubbeApi([{ totalCount: 0, categories: [], recentItems: [TERMIN] }]);
        renderSeite();

        fireEvent.click(await screen.findByRole('button', { name: 'Termine: alle erledigt' }));

        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt', body: { ids: ['TERMIN:123'] } },
        ]));
    });
});

describe('Themenleiste', () => {
    it('filtert per Klick und schreibt das Thema in die Adresse', async () => {
        stubbeApi([VOLL]);
        renderSeite();
        await screen.findByRole('heading', { name: 'Sicherung & Updates' });

        const alle = themenleiste().getByRole('button', { name: /^Alle/ });
        expect(alle).toHaveAttribute('aria-pressed', 'true');
        expect(alle).toHaveTextContent('1.240');
        const betrieb = themenleiste().getByRole('button', { name: /^Sicherung & Updates/ });
        expect(betrieb).toHaveTextContent('2');

        fireEvent.click(betrieb);

        expect(ueberschriften()).toEqual(['Sicherung & Updates']);
        expect(betrieb).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByTestId('ort')).toHaveTextContent('/benachrichtigungen?thema=betrieb');

        fireEvent.click(themenleiste().getByRole('button', { name: /^Alle/ }));
        expect(ueberschriften()).toEqual(['Sicherung & Updates', 'Posteingänge', 'Finanzen', 'Termine']);
        expect(screen.getByTestId('ort').textContent).toBe('/benachrichtigungen');
    });

    it('übernimmt das Thema aus der Adresse', async () => {
        stubbeApi([VOLL]);
        renderSeite('/benachrichtigungen?thema=termine');

        expect(await screen.findByRole('heading', { name: 'Termine' })).toBeInTheDocument();
        expect(ueberschriften()).toEqual(['Termine']);
        expect(themenleiste().getByRole('button', { name: /^Termine/ })).toHaveAttribute('aria-pressed', 'true');
    });

    it('ein unbekanntes Thema in der Adresse zeigt einfach alles', async () => {
        stubbeApi([VOLL]);
        renderSeite('/benachrichtigungen?thema=%3Cscript%3E');

        await screen.findByRole('heading', { name: 'Termine' });
        expect(ueberschriften()).toHaveLength(4);
        expect(themenleiste().getByRole('button', { name: /^Alle/ })).toHaveAttribute('aria-pressed', 'true');
    });

    it('ein gewähltes, inzwischen leeres Thema sagt das und führt zurück zu allen', async () => {
        stubbeApi([{ ...VOLL, categories: VOLL.categories.filter(c => c.type === 'TERMINE'), recentItems: [TERMIN], totalCount: 1 }]);
        renderSeite('/benachrichtigungen?thema=betrieb');

        expect(await screen.findByText('In diesem Thema ist alles erledigt.')).toBeInTheDocument();
        expect(themenleiste().getByRole('button', { name: /^Sicherung & Updates/ })).toHaveTextContent('0');

        fireEvent.click(screen.getByRole('button', { name: 'Alle Themen zeigen' }));
        expect(ueberschriften()).toEqual(['Termine']);
    });
});

describe('Inhalte vom Server', () => {
    it('zeigt HTML im Text nur als Text an', async () => {
        stubbeApi([VOLL]);
        const { container } = renderSeite();

        expect(await screen.findByText('<script>alert(1)</script>')).toBeInTheDocument();
        expect(container.querySelector('script')).toBeNull();
        expect(document.querySelector('main script, section script')).toBeNull();
    });

    it('„und X weitere“ unter der Liste führt zur passenden Übersicht', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        const post = await screen.findByRole('region', { name: 'Posteingänge' });
        expect(within(post).getByText((_, el) => el?.tagName === 'P' && el.textContent === 'und 1.233 weitere · Spam')).toBeInTheDocument();
        expect(within(post).getByText('1.235 Meldungen')).toBeInTheDocument();

        fireEvent.click(within(post).getByRole('button', { name: 'Spam: zur Übersicht' }));
        expect(screen.getByTestId('ort')).toHaveTextContent('/emails/spam');
        expect(postAufrufe()).toEqual([]);
    });

    it('Kategorie ohne mitgeschickte Einträge bleibt erreichbar', async () => {
        stubbeApi([VOLL]);
        renderSeite();

        const finanzen = await screen.findByRole('region', { name: 'Finanzen' });
        expect(within(finanzen).getByText((_, el) => el?.tagName === 'P' && el.textContent === '2 Neue Lieferantenrechnungen')).toBeInTheDocument();
        fireEvent.click(within(finanzen).getByRole('button', { name: 'Neue Lieferantenrechnungen: zur Übersicht' }));
        expect(screen.getByTestId('ort')).toHaveTextContent('/offeneposten?tab=eingang');
    });

    it('ohne Link kein Weg zur Übersicht', async () => {
        stubbeApi([{
            totalCount: 5,
            categories: [{ type: 'SICHERUNG', label: 'Sicherung', count: 5, icon: 'HardDrive', link: '' }],
            recentItems: [SICHERUNG],
        }]);
        renderSeite();

        const bereich = await screen.findByRole('region', { name: 'Sicherung & Updates' });
        expect(within(bereich).getByText((_, el) => el?.tagName === 'P' && el.textContent === 'und 4 weitere · Sicherung')).toBeInTheDocument();
        expect(within(bereich).queryByRole('button', { name: /zur Übersicht/ })).toBeNull();
    });

    it('lädt neu, wenn eine andere Stelle „notifications:refresh“ meldet', async () => {
        stubbeApi([VOLL, LEER]);
        renderSeite();
        await screen.findByRole('heading', { name: 'Termine' });

        act(() => { window.dispatchEvent(new Event('notifications:refresh')); });

        expect(await screen.findByText('Keine neuen Benachrichtigungen – alles erledigt.')).toBeInTheDocument();
    });

    it('„Neu laden“ oben holt den Stand vom Server und meldet Fehler', async () => {
        stubbeApi([VOLL, 503]);
        renderSeite();
        await screen.findByRole('heading', { name: 'Termine' });

        fireEvent.click(screen.getByRole('button', { name: 'Neu laden' }));

        expect(await screen.findByText('Benachrichtigungen konnten nicht geladen werden.')).toBeInTheDocument();
        // Die bisherige Liste bleibt stehen.
        expect(screen.getByRole('heading', { name: 'Termine' })).toBeInTheDocument();
    });
});
