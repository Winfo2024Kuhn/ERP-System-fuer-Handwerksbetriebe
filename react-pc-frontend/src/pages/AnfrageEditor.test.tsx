import { act, render, screen, waitFor, within } from '@testing-library/react';
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

describe('AnfrageEditor – Pagination', () => {
    // PAGE_SIZE im AnfrageEditor ist 12 – bei 40 Treffern also 4 Seiten.
    const PAGE_SIZE = 12;
    const GESAMT = 40;

    let fetchMock: ReturnType<typeof vi.fn>;
    // Über diese Variable lässt sich im Test simulieren, dass die Trefferzahl schrumpft.
    let gesamtTreffer = GESAMT;

    /** Query-Parameter des zuletzt abgesetzten Listen-Requests. */
    function letzterListenRequest(): URLSearchParams {
        const alle = anfragenlistenAufrufe(fetchMock as ReturnType<typeof mockFetch>);
        const letzte = alle[alle.length - 1];
        return new URLSearchParams(letzte.substring(letzte.indexOf('?') + 1));
    }

    beforeEach(() => {
        gesamtTreffer = GESAMT;
        fetchMock = vi.fn((url: string) => {
            const antwort = (daten: unknown) => Promise.resolve({ ok: true, json: () => Promise.resolve(daten) });
            if (typeof url !== 'string') return antwort({});
            if (url.startsWith('/api/anfragen/jahre')) return antwort([2026]);
            if (url.startsWith('/api/anfragen/funnel-ids')) return antwort([]);
            if (url.startsWith('/api/anfragen/freigabe-status')) return antwort({});
            if (url.startsWith('/api/last-accessed/')) return antwort({});
            if (url.startsWith('/api/anfragen?')) {
                const params = new URLSearchParams(url.substring(url.indexOf('?') + 1));
                const seite = Number(params.get('page') ?? 0);
                const verbleibend = Math.max(0, gesamtTreffer - seite * PAGE_SIZE);
                return antwort({
                    anfragen: mockAnfragen.slice(0, Math.min(PAGE_SIZE, verbleibend)),
                    gesamt: gesamtTreffer,
                });
            }
            return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
        });
        global.fetch = fetchMock as unknown as typeof fetch;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('springt auf die letzte gültige Seite, wenn die Trefferzahl schrumpft', async () => {
        const user = userEvent.setup();
        renderAnfrageEditor();

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('0'));

        const weiter = () => screen.getByRole('button', { name: /Weiter/i });
        await user.click(weiter());
        await user.click(weiter());
        await user.click(weiter());
        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('3'));

        // Ab jetzt liefert das Backend nur noch 20 Treffer – also zwei Seiten (0 und 1).
        gesamtTreffer = 20;
        await user.click(screen.getByRole('button', { name: /Aktualisieren/i }));

        await waitFor(() => expect(letzterListenRequest().get('page')).toBe('1'));
        expect(screen.getByText('Dachsanierung Musterweg 1')).toBeInTheDocument();

        // Der Guard darf sich nicht selbst nachtriggern. Erwartet sind 6 Requests
        // (Start, 3x Weiter, Aktualisieren, Korrektur-Sprung); eine Endlosschleife
        // würde hier sofort auffallen.
        await act(async () => {
            await Promise.resolve();
        });
        expect(anfragenlistenAufrufe(fetchMock as ReturnType<typeof mockFetch>).length).toBeLessThanOrEqual(8);
    });
});
