import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { NotificationBell } from './NotificationBell';

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
    beforeEach(() => {
        sessionStorage.clear();
        navigateMock.mockClear();
        mockFetch.mockReset();
    });

    function renderBell() {
        return render(
            <MemoryRouter>
                <NotificationBell />
            </MemoryRouter>
        );
    }

    function mockSummary(summary: unknown) {
        mockFetch.mockResolvedValue({
            ok: true,
            json: () => Promise.resolve(summary),
        } as unknown as Response);
    }

    it('rendert Kategorie-Button als Fallback, wenn Backend Counter aber keine Items liefert', async () => {
        mockSummary({
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
        });

        renderBell();
        await waitFor(() => expect(mockFetch).toHaveBeenCalled());

        // Glocke oeffnen
        const bell = screen.getByTitle('Benachrichtigungen');
        fireEvent.click(bell);

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
        mockSummary({
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
                    type: 'URLAUBSANTRAG',
                    title: 'URLAUB: Max Mustermann',
                    subtitle: '01.06. – 05.06.',
                    timestamp: '2026-05-07T10:00:00',
                    link: '/urlaubsantraege?antragId=1',
                },
            ],
        });

        renderBell();
        await waitFor(() => expect(mockFetch).toHaveBeenCalled());
        fireEvent.click(screen.getByTitle('Benachrichtigungen'));

        const itemButton = await screen.findByTitle('Direkt zu diesem Eintrag öffnen');
        expect(itemButton).toHaveTextContent('URLAUB: Max Mustermann');

        // Beim Klick auf das Item wird die Detail-URL angefahren, nicht die Listen-URL
        fireEvent.click(itemButton);
        expect(navigateMock).toHaveBeenCalledWith('/urlaubsantraege?antragId=1');
    });
});
