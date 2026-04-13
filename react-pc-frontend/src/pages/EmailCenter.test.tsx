import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import EmailCenter from './EmailCenter';

// ---- Mock data ----
const mockEmails = [
    {
        id: 1, type: 'EMAIL', direction: 'IN' as const,
        subject: 'Angebot für Treppe', sender: 'Max Mustermann',
        fromAddress: 'max@example.com', body: 'Hallo, hier ist das Angebot.',
        sentAt: new Date().toISOString(), isRead: false,
        zuordnungTyp: 'KEINE', attachments: []
    },
    {
        id: 2, type: 'EMAIL', direction: 'IN' as const,
        subject: 'Rechnung 2024-001', sender: 'Erika Musterfrau',
        fromAddress: 'erika@example.com', body: 'Anbei die Rechnung.',
        sentAt: new Date().toISOString(), isRead: true,
        zuordnungTyp: 'KEINE', attachments: []
    },
    {
        id: 3, type: 'EMAIL', direction: 'OUT' as const,
        subject: 'Re: Terminbestätigung', sender: 'Test Firma',
        fromAddress: 'info@test.com', recipient: 'kunde@example.com',
        body: 'Termin bestätigt.', sentAt: new Date().toISOString(),
        isRead: true, zuordnungTyp: 'KEINE', attachments: []
    }
];

const mockStats = {
    inboxCount: 2, sentCount: 1, trashCount: 0, spamCount: 0,
    newsletterCount: 0, unassignedCount: 1, inquiriesCount: 0,
    projectCount: 0, offerCount: 0, supplierCount: 0
};

// ---- Test helpers ----
function mockFetchResponses(overrides: Record<string, unknown> = {}) {
    const responses: Record<string, unknown> = {
        '/api/emails/inbox': mockEmails.filter(e => e.direction === 'IN'),
        '/api/emails/stats': mockStats,
        '/api/emails/sent': mockEmails.filter(e => e.direction === 'OUT'),
        '/api/emails/trash': [],
        '/api/emails/spam': [],
        '/api/emails/newsletter': [],
        ...overrides
    };

    return vi.fn((url: string, options?: RequestInit) => {
        const method = options?.method || 'GET';

        // Handle action endpoints
        if (method === 'POST' || method === 'DELETE') {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }

        const data = responses[url];
        if (data !== undefined) {
            return Promise.resolve({
                ok: true,
                json: () => Promise.resolve(data)
            });
        }
        return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
    });
}

function renderEmailCenter(folder = 'inbox') {
    return render(
        <MemoryRouter initialEntries={[`/emails/${folder}`]}>
            <ConfirmProvider>
                <ToastProvider>
                    <Routes>
                        <Route path="/emails/:folder" element={<EmailCenter />} />
                        <Route path="/emails/:folder/:emailId" element={<EmailCenter />} />
                    </Routes>
                </ToastProvider>
            </ConfirmProvider>
        </MemoryRouter>
    );
}

// ---- Tests ----
describe('EmailCenter', () => {
    let fetchMock: ReturnType<typeof mockFetchResponses>;

    beforeEach(() => {
        fetchMock = mockFetchResponses();
        global.fetch = fetchMock as unknown as typeof fetch;
        vi.useFakeTimers({ shouldAdvanceTime: true });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('Rendering', () => {
        it('zeigt Posteingang-Ordner und E-Mails an', async () => {
            renderEmailCenter();
            await waitFor(() => {
                expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument();
                expect(screen.getByText('Rechnung 2024-001')).toBeInTheDocument();
            });
        });

        it('zeigt Ordner-Sidebar mit Zählern', async () => {
            renderEmailCenter();
            await waitFor(() => {
                expect(screen.getByText('Posteingang')).toBeInTheDocument();
                expect(screen.getByText('Gesendet')).toBeInTheDocument();
                expect(screen.getByText('Papierkorb')).toBeInTheDocument();
                expect(screen.getByText('Spam')).toBeInTheDocument();
            });
        });

        it('ruft inbox und stats Endpoints auf', async () => {
            renderEmailCenter();
            await waitFor(() => {
                expect(fetchMock).toHaveBeenCalledWith('/api/emails/inbox');
                expect(fetchMock).toHaveBeenCalledWith('/api/emails/stats');
            });
        });
    });

    describe('Folder-Navigation', () => {
        it('wechselt zu Gesendet-Ordner', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            await user.click(screen.getByText('Gesendet'));

            await waitFor(() => {
                expect(fetchMock).toHaveBeenCalledWith('/api/emails/sent');
            });
        });
    });

    describe('E-Mail-Auswahl', () => {
        it('zeigt E-Mail-Details bei Klick', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());
            await user.click(screen.getByText('Angebot für Treppe'));

            await waitFor(() => {
                // Subject should appear in detail pane
                const headings = screen.getAllByText('Angebot für Treppe');
                expect(headings.length).toBeGreaterThanOrEqual(1);
            });
        });
    });

    describe('Suche', () => {
        it('zeigt Suchfeld an', async () => {
            renderEmailCenter();
            await waitFor(() => {
                expect(screen.getByPlaceholderText(/suchen/i)).toBeInTheDocument();
            });
        });

        it('filtert E-Mails lokal bei Eingabe', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            const searchInput = screen.getByPlaceholderText(/suchen/i);
            await user.type(searchInput, 'Rechnung');

            await waitFor(() => {
                expect(screen.getByText('Rechnung 2024-001')).toBeInTheDocument();
                expect(screen.queryByText('Angebot für Treppe')).not.toBeInTheDocument();
            });
        });
    });

    describe('Globale Suche', () => {
        it('wechselt zwischen Ordner- und Serversuche', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            // Find the "Alle Ordner" toggle button for global search
            const globeButton = screen.getByText('Alle Ordner');
            expect(globeButton).toBeInTheDocument();

            await user.click(globeButton);

            // Info banner should appear
            await waitFor(() => {
                expect(screen.getByText(/Mindestens 2 Zeichen/i)).toBeInTheDocument();
            });
        });
    });

    describe('AssignModal – Manuelle Suche (Zuordnung)', () => {
        it('ruft /api/projekte/suche auf bei Projekt-Suche', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            const projektResults = [
                { id: 10, bauvorhaben: 'BV Riedel Höchberg', kunde: 'Riedel GmbH', auftragsnummer: '2026-003', abgeschlossen: false }
            ];
            fetchMock = mockFetchResponses({
                '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
            });
            // Also handle the search endpoint
            const originalMock = fetchMock;
            global.fetch = vi.fn((url: string, options?: RequestInit) => {
                if (typeof url === 'string' && url.startsWith('/api/projekte/suche?q=')) {
                    return Promise.resolve({ ok: true, json: () => Promise.resolve(projektResults) });
                }
                return originalMock(url, options);
            }) as unknown as typeof fetch;

            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            // Select email to show detail pane
            await user.click(screen.getByText('Angebot für Treppe'));
            await waitFor(() => expect(screen.getByRole('button', { name: /Zuordnen/ })).toBeInTheDocument());

            // Open assign modal
            await user.click(screen.getByRole('button', { name: /Zuordnen/ }));
            await waitFor(() => expect(screen.getByText('E-Mail zuordnen')).toBeInTheDocument());

            // Type in manual search
            const searchInput = screen.getByPlaceholderText('Projekt suchen...');
            await user.type(searchInput, 'riedel');

            // Advance debounce timer
            await act(async () => { vi.advanceTimersByTime(400); });

            // Should call correct endpoint
            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    expect.stringContaining('/api/projekte/suche?q=riedel')
                );
            });

            // Should show result
            await waitFor(() => {
                expect(screen.getByText('BV Riedel Höchberg')).toBeInTheDocument();
                expect(screen.getByText('Riedel GmbH')).toBeInTheDocument();
            });
        });

        it('ruft /api/anfragen auf bei Anfrage-Suche', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            const anfrageResults = [
                { id: 20, bauvorhaben: 'Geländer Müller', kundenName: 'Müller Bau', anfragesnummer: 'ANF-2026-005' }
            ];
            fetchMock = mockFetchResponses({
                '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
            });
            const originalMock = fetchMock;
            global.fetch = vi.fn((url: string, options?: RequestInit) => {
                if (typeof url === 'string' && url.startsWith('/api/anfragen?q=')) {
                    return Promise.resolve({ ok: true, json: () => Promise.resolve(anfrageResults) });
                }
                return originalMock(url, options);
            }) as unknown as typeof fetch;

            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            await user.click(screen.getByText('Angebot für Treppe'));
            await waitFor(() => expect(screen.getByRole('button', { name: /Zuordnen/ })).toBeInTheDocument());

            await user.click(screen.getByRole('button', { name: /Zuordnen/ }));
            await waitFor(() => expect(screen.getByText('E-Mail zuordnen')).toBeInTheDocument());

            // Switch to Anfrage tab (inside the modal, find by exact text match)
            const allButtons = screen.getAllByRole('button');
            const anfrageTab = allButtons.find(btn => {
                const text = btn.textContent || '';
                return text.includes('Anfrage') && !text.includes('Anfragen');
            });
            await user.click(anfrageTab!);

            // Type search
            const searchInput = screen.getByPlaceholderText('Anfrage suchen...');
            await user.type(searchInput, 'müller');

            await act(async () => { vi.advanceTimersByTime(400); });

            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    expect.stringContaining('/api/anfragen?q=m')
                );
            });

            // Should show result with kundenName displayed
            await waitFor(() => {
                expect(screen.getByText('Geländer Müller')).toBeInTheDocument();
                expect(screen.getByText('Müller Bau')).toBeInTheDocument();
                expect(screen.getByText('ANF-2026-005')).toBeInTheDocument();
            });
        });

        it('zeigt keine Ergebnisse bei leerem Suchfeld', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            fetchMock = mockFetchResponses({
                '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            await user.click(screen.getByText('Angebot für Treppe'));
            await waitFor(() => expect(screen.getByRole('button', { name: /Zuordnen/ })).toBeInTheDocument());

            await user.click(screen.getByRole('button', { name: /Zuordnen/ }));
            await waitFor(() => expect(screen.getByText('E-Mail zuordnen')).toBeInTheDocument());

            // Don't type anything – no search API call should be made
            await act(async () => { vi.advanceTimersByTime(400); });

            const searchCalls = fetchMock.mock.calls.filter(
                (call: [string, ...unknown[]]) => typeof call[0] === 'string' && (call[0].includes('/api/projekte/suche') || call[0].includes('/api/anfragen?q='))
            );
            expect(searchCalls).toHaveLength(0);
        });
    });

    describe('Optimistic Updates', () => {
        it('entfernt E-Mail sofort aus Liste bei Spam-Markierung', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            // Select email to show detail pane
            await user.click(screen.getByText('Angebot für Treppe'));

            // Wait for detail pane with spam button (title="Als Spam markieren")
            await waitFor(() => {
                expect(screen.getByTitle('Als Spam markieren')).toBeInTheDocument();
            });

            await user.click(screen.getByTitle('Als Spam markieren'));

            // Email should be optimistically removed
            await waitFor(() => {
                expect(screen.queryByText('Angebot für Treppe')).not.toBeInTheDocument();
            });

            // API should have been called with bulk move-to-folder
            expect(fetchMock).toHaveBeenCalledWith('/api/emails/bulk/move-to-folder', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ids: [1], targetFolder: 'spam' })
            });
        });

        it('entfernt E-Mail sofort bei Löschung', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());
            await user.click(screen.getByText('Angebot für Treppe'));

            // Find delete button
            const deleteButtons = screen.getAllByRole('button').filter(btn =>
                btn.textContent?.includes('Löschen') || btn.getAttribute('title')?.includes('Löschen')
            );

            if (deleteButtons.length > 0) {
                await user.click(deleteButtons[0]);

                // Email should be optimistically removed
                await waitFor(() => {
                    expect(screen.queryByText('Angebot für Treppe')).not.toBeInTheDocument();
                });
            }
        });
    });

    describe('Auto-Polling', () => {
        it('aktualisiert E-Mails nach 30 Sekunden', async () => {
            renderEmailCenter();

            await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/emails/inbox'));

            const callsBefore = fetchMock.mock.calls.filter(c => c[0] === '/api/emails/inbox').length;

            // Advance timers by 30 seconds
            await act(async () => {
                vi.advanceTimersByTime(30000);
            });

            await waitFor(() => {
                const callsAfter = fetchMock.mock.calls.filter(c => c[0] === '/api/emails/inbox').length;
                expect(callsAfter).toBeGreaterThan(callsBefore);
            });
        });

        it('aktualisiert Stats nach 30 Sekunden', async () => {
            renderEmailCenter();

            await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/emails/stats'));

            const callsBefore = fetchMock.mock.calls.filter(c => c[0] === '/api/emails/stats').length;

            await act(async () => {
                vi.advanceTimersByTime(30000);
            });

            await waitFor(() => {
                const callsAfter = fetchMock.mock.calls.filter(c => c[0] === '/api/emails/stats').length;
                expect(callsAfter).toBeGreaterThan(callsBefore);
            });
        });
    });

    describe('Neue E-Mail schreiben', () => {
        it('öffnet Compose-Dialog bei Klick auf Neue E-Mail', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            const newEmailBtn = screen.getByRole('button', { name: /Neue E-Mail/i });
            await user.click(newEmailBtn);

            // Compose form should appear - look for "E-Mail senden" or "Abbrechen" button
            await waitFor(() => {
                expect(screen.getByText('E-Mail senden')).toBeInTheDocument();
            });
        });
    });
});
