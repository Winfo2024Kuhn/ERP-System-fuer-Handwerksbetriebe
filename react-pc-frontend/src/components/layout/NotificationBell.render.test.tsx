import { act, render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { NotificationBell } from './NotificationBell';
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

/** Antwortet auf GET /summary mit den übergebenen Ständen (der letzte bleibt stehen). */
function stubbeApi(staende: NotificationSummary[]) {
    let abruf = 0;
    mockFetch.mockImplementation((url: string) => {
        if (String(url).startsWith('/api/notifications/summary')) {
            const stand = staende[Math.min(abruf, staende.length - 1)];
            abruf += 1;
            return Promise.resolve(new Response(JSON.stringify(stand), { status: 200 }));
        }
        return Promise.resolve(new Response('[]', { status: 200 }));
    });
}

function summaryAbrufe(): string[] {
    return mockFetch.mock.calls.map(([u]) => String(u)).filter(u => u.startsWith('/api/notifications/summary'));
}

function renderBell(pfad = '/projekte') {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <NotificationBell />
        </MemoryRouter>
    );
}

function glocke() {
    return screen.getByRole('button', { name: /^Benachrichtigungen/ });
}

function stand(totalCount: number): NotificationSummary {
    return {
        totalCount,
        categories: totalCount > 0 ? [{ type: 'TERMINE', label: 'Bevorstehende Termine', count: totalCount, icon: 'CalendarClock', link: '/kalender' }] : [],
        recentItems: [],
    };
}

beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    navigateMock.mockClear();
    mockFetch.mockReset();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('NotificationBell – nur noch Zahl und Weg zur Seite', () => {
    it('räumt beim Start die alten Browser-Merker weg', async () => {
        localStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.eintraege, '["TERMIN::/kalender"]');
        sessionStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.kategorien, '{"TERMINE":1}');
        stubbeApi([stand(0)]);

        renderBell();
        await waitFor(() => expect(mockFetch).toHaveBeenCalled());

        expect(localStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.eintraege)).toBeNull();
        expect(sessionStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.kategorien)).toBeNull();
    });

    it('fragt nur die Kurzfassung ab (ohne alle=true) und zeigt die Zahl im Knopf und im Namen', async () => {
        stubbeApi([stand(4)]);
        renderBell();

        await waitFor(() => expect(glocke()).toHaveTextContent('4'));
        expect(glocke()).toHaveAccessibleName('Benachrichtigungen (4 neu)');
        expect(glocke()).toHaveAttribute('title', 'Benachrichtigungen (4 neu)');
        expect(summaryAbrufe()).toEqual(['/api/notifications/summary']);
    });

    it('schickt den gewählten Mitarbeiter mit', async () => {
        localStorage.setItem('frontendUserSelection', JSON.stringify({ mitarbeiterId: 7 }));
        stubbeApi([stand(0)]);
        renderBell();

        await waitFor(() => expect(summaryAbrufe()).toEqual(['/api/notifications/summary?mitarbeiterId=7']));
    });

    it('zeigt ab 100 Meldungen „99+“', async () => {
        stubbeApi([stand(1234)]);
        renderBell();

        await waitFor(() => expect(glocke()).toHaveTextContent('99+'));
        expect(glocke()).toHaveAccessibleName('Benachrichtigungen (1234 neu)');
    });

    it('ohne Meldungen: kein Zähler, schlichter Name', async () => {
        stubbeApi([stand(0)]);
        renderBell();

        await waitFor(() => expect(mockFetch).toHaveBeenCalled());
        expect(glocke()).toHaveAccessibleName('Benachrichtigungen');
        expect(glocke().textContent).toBe('');
    });

    it('Klick öffnet die Seite „Benachrichtigungen“ statt eines Aufklapp-Fensters', async () => {
        stubbeApi([stand(2)]);
        renderBell();
        await waitFor(() => expect(glocke()).toHaveTextContent('2'));

        fireEvent.click(glocke());

        expect(navigateMock).toHaveBeenCalledWith('/benachrichtigungen');
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(screen.queryByText('Bevorstehende Termine')).toBeNull();
    });

    it('ist auf der Seite „Benachrichtigungen“ als aktiv markiert', async () => {
        stubbeApi([stand(1)]);
        renderBell('/benachrichtigungen');

        await waitFor(() => expect(glocke()).toHaveTextContent('1'));
        expect(glocke()).toHaveAttribute('aria-current', 'page');
        expect(glocke().className).toContain('bg-rose-50');
    });

    it('ist auf anderen Seiten nicht aktiv markiert', async () => {
        stubbeApi([stand(1)]);
        renderBell('/projekte');

        await waitFor(() => expect(glocke()).toHaveTextContent('1'));
        expect(glocke()).not.toHaveAttribute('aria-current');
    });

    it('lädt bei „notifications:refresh“ sofort neu und wackelt, wenn die Zahl steigt', async () => {
        stubbeApi([stand(2), stand(5)]);
        renderBell();
        await waitFor(() => expect(glocke()).toHaveTextContent('2'));
        expect(glocke().className).not.toContain('animate-bell-shake');

        act(() => { window.dispatchEvent(new Event('notifications:refresh')); });

        await waitFor(() => expect(glocke()).toHaveTextContent('5'));
        expect(glocke().className).toContain('animate-bell-shake');
        expect(summaryAbrufe()).toHaveLength(2);
    });

    it('wackelt nicht, wenn die Zahl sinkt', async () => {
        stubbeApi([stand(5), stand(3)]);
        renderBell();
        await waitFor(() => expect(glocke()).toHaveTextContent('5'));

        act(() => { window.dispatchEvent(new Event('notifications:refresh')); });

        await waitFor(() => expect(glocke()).toHaveTextContent('3'));
        expect(glocke().className).not.toContain('animate-bell-shake');
    });

    it('lädt neu, sobald der Browser-Tab wieder sichtbar wird', async () => {
        stubbeApi([stand(1), stand(2)]);
        renderBell();
        await waitFor(() => expect(glocke()).toHaveTextContent('1'));

        act(() => { document.dispatchEvent(new Event('visibilitychange')); });

        await waitFor(() => expect(glocke()).toHaveTextContent('2'));
    });

    it('bleibt bei einem Serverfehler still und zeigt den letzten Stand', async () => {
        let abruf = 0;
        mockFetch.mockImplementation(() => {
            abruf += 1;
            return Promise.resolve(abruf === 1
                ? new Response(JSON.stringify(stand(3)), { status: 200 })
                : new Response('kaputt', { status: 500 }));
        });
        renderBell();
        await waitFor(() => expect(glocke()).toHaveTextContent('3'));

        act(() => { window.dispatchEvent(new Event('notifications:refresh')); });

        await waitFor(() => expect(abruf).toBe(2));
        expect(glocke()).toHaveTextContent('3');
    });
});
