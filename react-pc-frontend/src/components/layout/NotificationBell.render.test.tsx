import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { NotificationBell } from './NotificationBell';
import { ToastProvider } from '../ui/toast';
import { ALTE_SPEICHER_SCHLUESSEL, type NotificationSummary } from './notification-helpers';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
    return {
        ...actual,
        useNavigate: () => navigateMock,
    };
});

const mockFetch = vi.fn();
global.fetch = mockFetch as unknown as typeof fetch;

/** Antwortet auf GET /summary mit den übergebenen Ständen (der letzte bleibt stehen), auf POSTs mit `postStatus`. */
function stubbeApi(staende: NotificationSummary[], postStatus = 204) {
    let abruf = 0;
    mockFetch.mockImplementation((url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
            const body = postStatus === 200 ? JSON.stringify({ erledigt: 3 }) : null;
            return Promise.resolve(new Response(body, { status: postStatus }));
        }
        if (String(url).startsWith('/api/notifications/summary')) {
            const stand = staende[Math.min(abruf, staende.length - 1)];
            abruf += 1;
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

function renderBell() {
    return render(
        <MemoryRouter>
            <ToastProvider>
                <NotificationBell />
            </ToastProvider>
        </MemoryRouter>
    );
}

async function oeffneGlocke() {
    await waitFor(() => expect(mockFetch).toHaveBeenCalled());
    fireEvent.click(screen.getByTitle('Benachrichtigungen'));
}

const LEER: NotificationSummary = { totalCount: 0, categories: [], recentItems: [] };

const MIT_TERMIN_UND_MAILS: NotificationSummary = {
    totalCount: 4,
    categories: [
        { type: 'TERMINE', label: 'Bevorstehende Termine', count: 1, icon: 'CalendarClock', link: '/kalender' },
        { type: 'EMAILS', label: 'Ungelesene E-Mails', count: 2, icon: 'Mail', link: '/emails/inbox' },
        { type: 'EMAILS_SPAM', label: 'Spam', count: 1, icon: 'ShieldAlert', link: '/emails/spam' },
    ],
    recentItems: [
        { id: 'TERMIN:123', type: 'TERMIN', title: 'Aufmaß Bad', subtitle: 'Heute 10:00', timestamp: '2026-10-11', link: '/kalender?date=2026-10-11' },
        { id: 'EMAIL:456', type: 'EMAIL', title: 'Anfrage Treppe', subtitle: 'Von: Max Mustermann', timestamp: '2026-10-11T09:00:00', link: '/emails/inbox/456' },
    ],
};

beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    navigateMock.mockClear();
    mockFetch.mockReset();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('NotificationBell – Erledigt merkt sich der Server', () => {
    it('räumt beim Start die alten Browser-Merker weg', async () => {
        localStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.eintraege, '["TERMIN::/kalender"]');
        sessionStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.kategorien, '{"TERMINE":1}');
        stubbeApi([LEER]);

        renderBell();
        await waitFor(() => expect(mockFetch).toHaveBeenCalled());

        expect(localStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.eintraege)).toBeNull();
        expect(sessionStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.kategorien)).toBeNull();
    });

    it('Klick auf den Termin meldet seine id als erledigt, blendet ihn aus und lädt danach neu', async () => {
        const nachher: NotificationSummary = {
            ...MIT_TERMIN_UND_MAILS,
            totalCount: 3,
            categories: MIT_TERMIN_UND_MAILS.categories.slice(1),
            recentItems: MIT_TERMIN_UND_MAILS.recentItems.slice(1),
        };
        stubbeApi([MIT_TERMIN_UND_MAILS, nachher]);
        renderBell();
        await oeffneGlocke();

        fireEvent.click(await screen.findByText('Aufmaß Bad'));

        expect(navigateMock).toHaveBeenCalledWith('/kalender?date=2026-10-11');
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt', body: { ids: ['TERMIN:123'] } },
        ]));
        // Nach dem POST holt die Glocke den Stand vom Server.
        await waitFor(() => expect(mockFetch.mock.calls.filter(([u]) => String(u).startsWith('/api/notifications/summary'))).toHaveLength(2));
        // Glocken-Zähler sinkt sofort von 4 auf 3.
        expect(screen.getByTitle('Benachrichtigungen')).toHaveTextContent('3');

        // Auch nach erneutem Öffnen ist der Termin weg.
        fireEvent.click(screen.getByTitle('Benachrichtigungen'));
        expect(await screen.findByText('Anfrage Treppe')).toBeInTheDocument();
        expect(screen.queryByText('Aufmaß Bad')).toBeNull();
        // Kein eigener Browser-Merker mehr.
        expect(localStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.eintraege)).toBeNull();
    });

    it('Klick auf eine E-Mail ruft mark-read nicht mehr selbst auf', async () => {
        stubbeApi([MIT_TERMIN_UND_MAILS]);
        renderBell();
        await oeffneGlocke();

        fireEvent.click(await screen.findByText('Anfrage Treppe'));

        expect(navigateMock).toHaveBeenCalledWith('/emails/inbox/456');
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt', body: { ids: ['EMAIL:456'] } },
        ]));
        expect(mockFetch.mock.calls.some(([u]) => String(u).includes('mark-read'))).toBe(false);
    });

    it('zeigt bei einem Fehler eine Meldung und holt den Stand vom Server zurück', async () => {
        stubbeApi([MIT_TERMIN_UND_MAILS], 500);
        renderBell();
        await oeffneGlocke();

        fireEvent.click(await screen.findByText('Aufmaß Bad'));

        expect(await screen.findByText('Die Meldung konnte nicht als erledigt markiert werden.')).toBeInTheDocument();
        // Server ist die Wahrheit: der Termin ist wieder da, Zähler wieder 4.
        await waitFor(() => expect(screen.getByTitle('Benachrichtigungen')).toHaveTextContent('4'));
    });

    it('„Alle als gelesen" der Posteingänge schickt alle Mail-Kategorien der Spalte und meldet es dem E-Mail-Center', async () => {
        const emailsRefresh = vi.fn();
        window.addEventListener('emails:refresh', emailsRefresh);
        stubbeApi([MIT_TERMIN_UND_MAILS], 200);
        renderBell();
        await oeffneGlocke();

        fireEvent.click(await screen.findByRole('button', { name: 'Posteingänge: alle als gelesen markieren' }));

        // Spalte ist sofort leer, der Termin bleibt.
        expect(screen.queryByText('Anfrage Treppe')).toBeNull();
        expect(screen.getByText('Aufmaß Bad')).toBeInTheDocument();
        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt/kategorien', body: { kategorien: ['EMAILS', 'EMAILS_SPAM'] } },
        ]));
        await waitFor(() => expect(emailsRefresh).toHaveBeenCalledTimes(1));
        window.removeEventListener('emails:refresh', emailsRefresh);
    });

    it('„Alle als gelesen" bei Terminen löst kein Neuladen des E-Mail-Centers aus', async () => {
        const emailsRefresh = vi.fn();
        window.addEventListener('emails:refresh', emailsRefresh);
        stubbeApi([MIT_TERMIN_UND_MAILS], 200);
        renderBell();
        await oeffneGlocke();

        fireEvent.click(await screen.findByRole('button', { name: 'Termine: alle als gelesen markieren' }));

        await waitFor(() => expect(postAufrufe()).toEqual([
            { url: '/api/notifications/erledigt/kategorien', body: { kategorien: ['TERMINE'] } },
        ]));
        await waitFor(() => expect(mockFetch.mock.calls.filter(([u]) => String(u).startsWith('/api/notifications/summary'))).toHaveLength(2));
        expect(emailsRefresh).not.toHaveBeenCalled();
        window.removeEventListener('emails:refresh', emailsRefresh);
    });
});

/**
 * Regression: Im Screenshot vom 2026-05-07 zeigte das Notification-Center vier
 * Spalten mit Counts (Geschäft 2, Finanzen 5, Personal 2, Lieferanten 1), aber
 * keine einzelnen Eintraege – nur die tote Textzeile "X Eintraege – in der
 * Uebersicht oeffnen.". Ursache: Backend liefert eine Kategorie ohne
 * passende recentItems (z.B. RECHNUNGEN), und das Frontend rendert in dem
 * Fall einen unklickbaren Platzhalter. Fix: Wenn keine Items da sind, werden
 * stattdessen die Kategorien als klickbare Buttons gerendert.
 */
describe('NotificationBell – Spalten ohne recentItems bleiben klickbar', () => {
    it('rendert Kategorie-Button als Fallback, wenn Backend Counter aber keine Items liefert', async () => {
        stubbeApi([{
            totalCount: 4,
            categories: [
                {
                    type: 'RECHNUNGEN',
                    label: 'Neue Lieferantenrechnungen',
                    count: 4,
                    icon: 'Truck',
                    link: '/offeneposten?tab=eingang',
                },
            ],
            recentItems: [],
        }]);

        renderBell();
        await oeffneGlocke();

        // Statt der toten Textzeile darf ein klickbarer Button mit dem
        // Kategorie-Label sichtbar sein, der per Klick navigiert.
        const fallbackButton = await screen.findByTitle('Übersicht öffnen');
        expect(fallbackButton).toHaveTextContent('Neue Lieferantenrechnungen');
        expect(fallbackButton).toHaveTextContent('4');

        fireEvent.click(fallbackButton);
        expect(navigateMock).toHaveBeenCalledWith('/offeneposten?tab=eingang');

        // Der alte Platzhaltertext darf nirgends mehr auftauchen.
        expect(screen.queryByText(/in der Übersicht öffnen\./)).toBeNull();
    });

    it('rendert weiterhin echte Item-Buttons, wenn Backend recentItems liefert', async () => {
        stubbeApi([{
            totalCount: 1,
            categories: [
                {
                    type: 'URLAUBSANTRAEGE',
                    label: 'Offene Anträge',
                    count: 1,
                    icon: 'Plane',
                    link: '/urlaubsantraege',
                },
            ],
            recentItems: [
                {
                    id: 'URLAUBSANTRAG:1',
                    type: 'URLAUBSANTRAG',
                    title: 'URLAUB: Max Mustermann',
                    subtitle: '01.06. – 05.06.',
                    timestamp: '2026-05-07T10:00:00',
                    link: '/urlaubsantraege?antragId=1',
                },
            ],
        }]);

        renderBell();
        await oeffneGlocke();

        const itemButton = await screen.findByTitle('Direkt zu diesem Eintrag öffnen');
        expect(itemButton).toHaveTextContent('URLAUB: Max Mustermann');

        // Beim Klick auf das Item wird die Detail-URL angefahren, nicht die Listen-URL
        fireEvent.click(itemButton);
        expect(navigateMock).toHaveBeenCalledWith('/urlaubsantraege?antragId=1');
    });
});

it('öffnet MONATSABSCHLUSS als Kalender-Deep-Link auch ohne recentItems', async () => {
    stubbeApi([{ totalCount: 3,
        categories: [{ type: 'MONATSABSCHLUSS', label: 'Monate abschließen', count: 3, icon: 'CalendarCheck', link: '/zeitbuchungen?jahr=2025&monat=8' }], recentItems: [] }]);
    renderBell();
    await oeffneGlocke();
    fireEvent.click(await screen.findByText('Monate abschließen'));
    expect(navigateMock).toHaveBeenCalledWith('/zeitbuchungen?jahr=2025&monat=8');
});

it('zeigt eine Spalte "Telefon" direkt nach der Webseite mit Nachrichten und verpassten Anrufen', async () => {
    stubbeApi([{ totalCount: 4,
        categories: [
            { type: 'ANFRAGEN_WEBSEITE', label: 'Neue Anfragen', count: 1, icon: 'Globe', link: '/anfragen' },
            { type: 'SPRACHNACHRICHTEN', label: 'Neue Nachrichten', count: 1, icon: 'Voicemail', link: '/telefon/anrufbeantworter' },
            { type: 'VERPASSTE_ANRUFE', label: 'Verpasste Anrufe', count: 1, icon: 'PhoneMissed', link: '/telefon/anrufe?offen=1' },
            { type: 'URLAUBSANTRAEGE', label: 'Offene Anträge', count: 1, icon: 'Plane', link: '/urlaubsantraege' },
        ],
        recentItems: [
            { id: 'ANFRAGE_WEBSEITE:1', type: 'ANFRAGE_WEBSEITE', title: 'Anfrage: Max Mustermann', subtitle: 'Balkon', timestamp: '2026-09-29T10:00:00', link: '/anfragen?anfrageId=1' },
            { id: 'SPRACHNACHRICHT:11', type: 'SPRACHNACHRICHT', title: 'Nachricht von Max Mustermann', subtitle: 'AB Tag · 0:42', timestamp: '2026-09-29T11:57:00', link: '/telefon/anrufbeantworter?nachricht=11' },
            { id: 'VERPASSTER_ANRUF:4', type: 'VERPASSTER_ANRUF', title: 'Verpasst: 0931 1234567', subtitle: 'Heute 11:55', timestamp: '2026-09-29T11:55:00', link: '/telefon/anrufe?anruf=4' },
            { id: 'URLAUBSANTRAG:2', type: 'URLAUBSANTRAG', title: 'URLAUB: Erika Mustermann', subtitle: '01.10.', timestamp: '2026-09-29T09:00:00', link: '/urlaubsantraege?antragId=2' },
        ] }]);
    renderBell();
    await oeffneGlocke();

    const ueberschriften = (await screen.findAllByText(/^(Neu von der Webseite|Telefon|Personal)$/)).map(e => e.textContent);
    expect(ueberschriften).toEqual(['Neu von der Webseite', 'Telefon', 'Personal']);

    fireEvent.click(screen.getByText('Nachricht von Max Mustermann'));
    expect(navigateMock).toHaveBeenCalledWith('/telefon/anrufbeantworter?nachricht=11');
});
