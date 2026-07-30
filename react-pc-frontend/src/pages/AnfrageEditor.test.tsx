import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import AnfrageEditor from './AnfrageEditor';

// 13 Anfragen: genug für zwei Seiten, damit der Seitenwechsel testbar ist.
const mockAnfragen = Array.from({ length: 13 }, (_, i) => ({
    id: i + 1,
    bauvorhaben: `Dachsanierung Musterweg ${i + 1}`,
    kundenName: 'Max Mustermann',
    anfragesnummer: `AG-2026/07/${String(i + 1).padStart(5, '0')}`,
    betrag: 1585.08,
    anlegedatum: '2026-07-28',
    abgeschlossen: false,
}));

function mockFetch() {
    return vi.fn((url: string) => {
        if (url.startsWith('/api/anfragen/jahre')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve([2026]) });
        }
        if (url.startsWith('/api/anfragen/funnel-ids')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
        }
        if (url.startsWith('/api/anfragen/freigabe-status')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }
        if (url.startsWith('/api/anfragen?')) {
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve({ anfragen: mockAnfragen.slice(0, 12), gesamt: mockAnfragen.length }),
            });
        }
        if (url.startsWith('/api/last-accessed/')) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
}

function renderAnfrageEditor() {
    return render(
        <MemoryRouter initialEntries={['/anfragen']}>
            <ConfirmProvider>
                <ToastProvider>
                    <AnfrageEditor />
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

/** Alle URLs, mit denen die Anfragenliste geladen wurde. */
function anfragenlistenAufrufe(fetchMock: ReturnType<typeof mockFetch>): string[] {
    return fetchMock.mock.calls
        .map(call => call[0] as string)
        .filter(url => typeof url === 'string' && url.startsWith('/api/anfragen?'));
}

/**
 * Öffnet das Angebots-Status-Dropdown und wählt einen Eintrag. Der Trigger trägt
 * den Text "Alle" – "Alle Jahre" daneben ist ein eigener Textknoten und kollidiert
 * bei exakter Suche nicht.
 */
async function waehleAngebotsStatus(user: ReturnType<typeof userEvent.setup>, label: string) {
    await user.click(screen.getByText('Alle'));
    const dropdown = await screen.findByRole('listbox');
    await user.click(within(dropdown).getByRole('option', { name: label }));
}

describe('AnfrageEditor – Anfragenliste', () => {
    let fetchMock: ReturnType<typeof mockFetch>;

    beforeEach(() => {
        fetchMock = mockFetch();
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('schickt den Angebots-Status als Filter ans Backend, statt nur die geladene Seite auszudünnen', async () => {
        const user = userEvent.setup();
        renderAnfrageEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg 1')).toBeInTheDocument());

        await waehleAngebotsStatus(user, 'Wartet auf Kunden');

        await waitFor(() => {
            expect(anfragenlistenAufrufe(fetchMock).some(url => url.includes('freigabe=pending'))).toBe(true);
        });
    });

    it('springt beim Filtern zurück auf die erste Seite', async () => {
        const user = userEvent.setup();
        renderAnfrageEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg 1')).toBeInTheDocument());

        await user.click(screen.getByRole('button', { name: /Weiter/i }));
        await waitFor(() => {
            expect(anfragenlistenAufrufe(fetchMock).some(url => url.includes('page=1'))).toBe(true);
        });

        await waehleAngebotsStatus(user, 'Angebot angenommen');

        await waitFor(() => {
            const aufrufe = anfragenlistenAufrufe(fetchMock);
            const letzter = aufrufe[aufrufe.length - 1];
            expect(letzter).toContain('page=0');
            expect(letzter).toContain('freigabe=accepted');
        });
    });

    it('lädt ohne gesetzten Status-Filter keinen freigabe-Parameter', async () => {
        renderAnfrageEditor();

        await waitFor(() => expect(screen.getByText('Dachsanierung Musterweg 1')).toBeInTheDocument());

        expect(anfragenlistenAufrufe(fetchMock).every(url => !url.includes('freigabe='))).toBe(true);
    });
});
