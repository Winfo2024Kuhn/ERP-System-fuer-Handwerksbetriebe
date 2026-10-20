import { render, screen, waitFor, act, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import EmailCenter from './EmailCenter';

// Eigener Benutzer für die Kennzeichnung fremder Entwürfe; Standard: keine Anmeldung.
const anmeldung = vi.hoisted(() => ({ wert: null as null | { user: { displayName: string }; isAdmin: boolean } }));
vi.mock('../auth/AuthContext', async (original) => ({
    ...(await original<typeof import('../auth/AuthContext')>()),
    useOptionalAuth: () => anmeldung.wert,
}));

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
    projectCount: 0, offerCount: 0, supplierCount: 0, taxAdvisorCount: 0
};

// ---- Test helpers ----
// Folder-Endpoints werden mit Pagination-Query (`?offset=0&limit=50`) aufgerufen.
// Der Mock matcht daher gegen den Pfad ohne Query-String.
function stripQuery(url: string): string {
    const idx = url.indexOf('?');
    return idx === -1 ? url : url.substring(0, idx);
}

function mockFetchResponses(overrides: Record<string, unknown> = {}) {
    const responses: Record<string, unknown> = {
        '/api/emails/inbox': mockEmails.filter(e => e.direction === 'IN'),
        '/api/emails/stats': mockStats,
        '/api/emails/sent': mockEmails.filter(e => e.direction === 'OUT'),
        '/api/emails/trash': [],
        '/api/emails/spam': [],
        '/api/emails/newsletter': [],
        '/api/emails/tax-advisors': [],
        '/api/emails/absender-postfaecher': [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }],
        ...overrides
    };

    return vi.fn((url: string, options?: RequestInit) => {
        const method = options?.method || 'GET';

        // Handle action endpoints
        if (method === 'POST' || method === 'DELETE') {
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        }

        // Erst mit voller URL probieren (fuer Endpoints mit Pflicht-Query wie /search?q=),
        // dann Fallback auf den Pfad ohne Query (Pagination-Endpoints).
        const data = responses[url] ?? responses[stripQuery(url)];
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
                        <Route path="/anfragen" element={<p>Anfragen-Seite</p>} />
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

    describe('Postfächer', () => {
        it('zeigt an jeder Mail ein Schild mit dem Postfach', async () => {
            global.fetch = mockFetchResponses({
                '/api/emails/inbox': [{
                    ...mockEmails[0],
                    postfaecher: [
                        { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' },
                        { id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: null },
                    ],
                }],
            }) as unknown as typeof fetch;
            renderEmailCenter();
            const schild = await screen.findByTestId('postfach-schild');
            expect(within(schild).getByText('info@')).toBeInTheDocument();
            expect(within(schild).getByText('max@').parentElement).toHaveAttribute('title', 'max@musterbetrieb.example');
        });

        it('leitet mit festem Absender und weitergeleitetVonEmailId weiter', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const original = {
                ...mockEmails[0], htmlBody: '<p>Hallo, hier ist das Angebot.</p>',
                antwortPostfach: { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' },
            };
            global.fetch = mockFetchResponses({
                '/api/emails/inbox': [original],
                '/api/emails/1': original,
                '/api/emails/1/thread': { rootEmailId: 1, focusedEmailId: 1, emails: [original] },
            }) as unknown as typeof fetch;
            renderEmailCenter();
            await user.click(await screen.findByText('Angebot für Treppe'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByRole('menuitem', { name: /Weiterleiten/ }));
            expect(await screen.findByTestId('absender-fest')).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>');
            expect(screen.getByText(/Weiterleitungen gehen über das Postfach raus/)).toBeInTheDocument();
        });

        it('bietet beim Weiterleiten die Anhänge der Original-Mail an, ohne eingebettete Bilder', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const original = {
                ...mockEmails[0], htmlBody: '<p>Hallo, hier ist das Angebot.</p>',
                antwortPostfach: { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' },
                attachments: [
                    { id: 21, originalFilename: 'Angebot-Treppe.pdf', fileSize: 120 * 1024, inline: false },
                    { id: 22, originalFilename: 'signatur-logo.png', fileSize: 4 * 1024, inline: true },
                ],
            };
            global.fetch = mockFetchResponses({
                '/api/emails/inbox': [{ ...mockEmails[0], attachments: [] }],
                '/api/emails/1': original,
                '/api/emails/1/thread': { rootEmailId: 1, focusedEmailId: 1, emails: [original] },
            }) as unknown as typeof fetch;
            renderEmailCenter();
            await user.click(await screen.findByText('Angebot für Treppe'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByRole('menuitem', { name: /Weiterleiten/ }));

            const liste = await screen.findByTestId('weiterleitung-anhaenge');
            expect(within(liste).getByText('Angebot-Treppe.pdf')).toBeInTheDocument();
            expect(within(liste).queryByText('signatur-logo.png')).not.toBeInTheDocument();
        });

        it('öffnet einen Weiterleitungs-Entwurf mit festem Absender statt „Senden von“', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const entwurf = {
                id: 55, recipient: 'kollege@example.org', subject: 'WG: Angebot für Treppe', body: '<p>Bitte ansehen.</p>',
                replyEmailId: null, weitergeleitetVonEmailId: 1, attachments: [], updatedAt: '2026-10-10T09:00:00',
            };
            global.fetch = mockFetchResponses({
                '/api/emails/drafts': [entwurf],
                '/api/emails/drafts/55': entwurf,
                '/api/emails/1': { ...mockEmails[0], antwortPostfach: { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' } },
            }) as unknown as typeof fetch;
            renderEmailCenter('drafts');
            await user.click(await screen.findByText('kollege@example.org'));
            expect(await screen.findByTestId('absender-fest')).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>');
            expect(screen.getByText(/Weiterleitungen gehen über das Postfach raus/)).toBeInTheDocument();
            expect(screen.queryByRole('combobox', { name: 'Senden von' })).not.toBeInTheDocument();
        });
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

        it('zeigt im Ordner-Kopf neben „Alle gelesen“ ein (i), das „gelesen pro Benutzer“ erklärt', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            renderEmailCenter();
            const kopf = await screen.findByTestId('email-ungelesen-kopfzeile');
            expect(within(kopf).getByText('1 ungelesen')).toBeInTheDocument();
            const alleGelesen = within(kopf).getByRole('button', { name: 'Alle gelesen' });
            const info = within(kopf).getByRole('button', { name: 'Mehr zu: Gelesen gilt nur für dich' });
            expect(alleGelesen.nextElementSibling).toBe(info);
            await user.click(info);
            expect(await screen.findByRole('dialog', { name: 'Gelesen gilt nur für dich' })).toBeVisible();
            expect(screen.getByText(/Für deine Kollegen bleibt sie fett/)).toBeVisible();
        });

        it('ruft inbox und stats Endpoints auf', async () => {
            renderEmailCenter();
            await waitFor(() => {
                expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/emails\/inbox(\?|$)/));
                expect(fetchMock).toHaveBeenCalledWith('/api/emails/stats');
            });
        });
    });

    describe('Zuordnung in der Liste', () => {
        it('zeigt statt des Enum-Texts einen Link zur Anfrage', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            fetchMock = mockFetchResponses({
                '/api/emails/inbox': [{
                    ...mockEmails[0], zuordnungTyp: 'ANFRAGE', anfrageId: 12, anfrageName: 'Garagentor Mustermann',
                }],
            });
            global.fetch = fetchMock as unknown as typeof fetch;
            renderEmailCenter();

            const link = await screen.findByRole('link', { name: 'Anfrage · Garagentor Mustermann öffnen' });
            expect(link).toHaveAttribute('href', '/anfragen?anfrageId=12');
            expect(screen.queryByText('ANFRAGE')).not.toBeInTheDocument();

            await user.click(link);
            expect(await screen.findByText('Anfragen-Seite')).toBeInTheDocument();
        });
    });

    describe('Folder-Navigation', () => {
        it('wechselt zu Gesendet-Ordner', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            await user.click(screen.getByText('Gesendet'));

            await waitFor(() => {
                expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/emails\/sent(\?|$)/));
            });
        });

        it('zeigt Steuerberater unter Lieferanten und lädt den neuen Ordner', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Lieferanten')).toBeInTheDocument());

            const lieferanten = screen.getByText('Lieferanten');
            const steuerberater = screen.getByText('Steuerberater');
            expect(lieferanten.compareDocumentPosition(steuerberater) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

            await user.click(steuerberater);
            await waitFor(() => {
                expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/emails\/tax-advisors(\?|$)/));
            });
        });
    });

    describe('E-Mail-Auswahl', () => {
        it('zeigt E-Mail-Details bei Klick', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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

    describe('Suche in Entwürfen', () => {
        it('findet Entwürfe über den Text, auch über Absätze hinweg, und blendet die übrigen aus', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            global.fetch = mockFetchResponses({
                '/api/emails/drafts': [
                    {
                        id: 71, recipient: 'max.mustermann@example.com', subject: 'Angebot Treppe',
                        body: '<p>Maße für die</p><p><strong>Treppe</strong> &amp; Geländer</p>', updatedAt: '2026-10-10T09:00:00',
                    },
                    {
                        id: 72, recipient: 'erika.musterfrau@example.com', subject: 'Rechnung 2026-014',
                        body: '<p>Anbei die Rechnung.</p>', updatedAt: '2026-10-11T09:00:00',
                    },
                ],
            }) as unknown as typeof fetch;
            renderEmailCenter('drafts');
            await screen.findByText('Angebot Treppe');
            expect(screen.getByText('Rechnung 2026-014')).toBeInTheDocument();

            await user.type(screen.getByPlaceholderText(/suchen/i), 'die Treppe & Geländer');

            await waitFor(() => expect(screen.queryByText('Rechnung 2026-014')).not.toBeInTheDocument());
            expect(screen.getByText('Angebot Treppe')).toBeInTheDocument();
            expect(screen.getByText('Maße für die Treppe & Geländer')).toBeInTheDocument();
        });

        it('trifft keine HTML-Tags im Entwurfstext', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            global.fetch = mockFetchResponses({
                '/api/emails/drafts': [{
                    id: 73, recipient: 'max.mustermann@example.com', subject: 'Aufmaß Balkon',
                    body: '<p><strong>Bitte</strong> prüfen.</p>', updatedAt: '2026-10-10T09:00:00',
                }],
            }) as unknown as typeof fetch;
            renderEmailCenter('drafts');
            await screen.findByText('Aufmaß Balkon');

            await user.type(screen.getByPlaceholderText(/suchen/i), 'strong');

            await waitFor(() => expect(screen.queryByText('Aufmaß Balkon')).not.toBeInTheDocument());
        });
    });

    describe('Globale Suche', () => {
        it('wechselt zwischen Ordner- und Serversuche', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const projektResults = [
                { id: 10, bauvorhaben: 'BV Muster Musterstadt', kunde: 'Muster GmbH', auftragsnummer: '2026-003', abgeschlossen: false }
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
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await waitFor(() => expect(screen.getByRole('menuitem', { name: 'Zuordnen' })).toBeInTheDocument());

            // Open assign modal
            await user.click(screen.getByRole('menuitem', { name: 'Zuordnen' }));
            await waitFor(() => expect(screen.getByText('E-Mail zuordnen')).toBeInTheDocument());

            // Type in manual search
            const searchInput = screen.getByPlaceholderText('Projekt suchen...');
            await user.type(searchInput, 'muster');

            // Advance debounce timer
            await act(async () => { vi.advanceTimersByTime(400); });

            // Should call correct endpoint
            await waitFor(() => {
                expect(global.fetch).toHaveBeenCalledWith(
                    expect.stringContaining('/api/projekte/suche?q=muster')
                );
            });

            // Should show result
            await waitFor(() => {
                expect(screen.getByText('BV Muster Musterstadt')).toBeInTheDocument();
                expect(screen.getByText('Muster GmbH')).toBeInTheDocument();
            });
        });

        it('ruft /api/anfragen auf bei Anfrage-Suche', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await waitFor(() => expect(screen.getByRole('menuitem', { name: 'Zuordnen' })).toBeInTheDocument());

            await user.click(screen.getByRole('menuitem', { name: 'Zuordnen' }));
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
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            fetchMock = mockFetchResponses({
                '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
            });
            global.fetch = fetchMock as unknown as typeof fetch;

            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            await user.click(screen.getByText('Angebot für Treppe'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await waitFor(() => expect(screen.getByRole('menuitem', { name: 'Zuordnen' })).toBeInTheDocument());

            await user.click(screen.getByRole('menuitem', { name: 'Zuordnen' }));
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
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            renderEmailCenter();

            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            // Select email to show detail pane
            await user.click(screen.getByText('Angebot für Treppe'));

            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByRole('menuitem', { name: 'Als Spam markieren' }));

            // Email should be optimistically removed
            await waitFor(() => {
                expect(screen.queryByText('Angebot für Treppe')).not.toBeInTheDocument();
            });

            // API should have been called with mark-spam (nicht move-to-folder, damit das Spam-Modell lernt)
            expect(fetchMock).toHaveBeenCalledWith('/api/emails/1/mark-spam', {
                method: 'POST'
            });
        });

        it('entfernt E-Mail sofort bei Löschung', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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

            const inboxMatcher = (c: [string, ...unknown[]]) =>
                typeof c[0] === 'string' && c[0].startsWith('/api/emails/inbox');

            await waitFor(() => expect(fetchMock.mock.calls.some(inboxMatcher)).toBe(true));

            const callsBefore = fetchMock.mock.calls.filter(inboxMatcher).length;

            // Advance timers by 30 seconds
            await act(async () => {
                vi.advanceTimersByTime(30000);
            });

            await waitFor(() => {
                const callsAfter = fetchMock.mock.calls.filter(inboxMatcher).length;
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
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
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

    describe('Resizable Columns und Collapsible Sidebar', () => {
        it('initialisiert Spaltenbreiten und Splitter', async () => {
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            expect(screen.getByTitle('Ordnerspalte verschieben')).toBeInTheDocument();
            expect(screen.getByTitle('Listenbreite verschieben')).toBeInTheDocument();
        });

        it('kann Ordnerleiste ein- und ausklappen', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            renderEmailCenter();
            await waitFor(() => expect(screen.getByText('Angebot für Treppe')).toBeInTheDocument());

            const collapseBtn = screen.getByTitle('Ordnerleiste einklappen');
            await user.click(collapseBtn);

            expect(screen.getByTitle('Ordnerleiste ausklappen')).toBeInTheDocument();
            expect(localStorage.getItem('email_center_sidebar_collapsed')).toBe('true');
        });
    });

    describe('Verläufe in der Liste und Allen antworten', () => {
        const anfrage = {
            id: 16, type: 'EMAIL', direction: 'IN' as const, subject: 'Anfrage Terrasse',
            fromAddress: 'max@example.com', body: 'Ich hätte gerne ein Angebot.',
            sentAt: '2026-09-16T09:00:00', isRead: false, zuordnungTyp: 'KEINE', attachments: [],
            threadRootId: 16, replyCount: 2,
        };
        // Hängt an unserer Antwort (id 99, liegt in "Gesendet") – der Parent ist im Posteingang nicht geladen.
        const nachfrage = {
            id: 17, type: 'EMAIL', direction: 'IN' as const, subject: 'Re: Anfrage Terrasse',
            fromAddress: 'max@example.com', recipient: 'handwerk@example.com, erika@example.com',
            cc: 'architekt@example.org', body: 'Wie gewünscht die Bilder.',
            sentAt: '2026-09-17T09:00:00', isRead: false, zuordnungTyp: 'KEINE', attachments: [],
            parentEmailId: 99, threadRootId: 16, replyCount: 2,
        };
        const threadOf = (focusedEmailId: number) => ({
            rootEmailId: 16, focusedEmailId,
            emails: [anfrage, nachfrage].map(e => ({ ...e, htmlBody: `<p>${e.body}</p>` })),
        });

        function setup() {
            const mock = mockFetchResponses({
                '/api/emails/inbox': [nachfrage, anfrage],
                '/api/emails/16': anfrage, '/api/emails/17': nachfrage,
                '/api/emails/16/thread': threadOf(16), '/api/emails/17/thread': threadOf(17),
                '/api/emails/absender-postfaecher': [{ id: 1, emailAdresse: 'handwerk@example.com', anzeigename: null, eigenes: false, hauptpostfach: true }],
            });
            vi.stubGlobal('fetch', mock);
            return mock;
        }

        it('zeigt einen Verlauf als eine Zeile, auch wenn die verbindende Antwort in "Gesendet" liegt', async () => {
            setup();
            renderEmailCenter();
            expect(await screen.findByText('Re: Anfrage Terrasse')).toBeInTheDocument();
            expect(screen.queryByText('Anfrage Terrasse')).not.toBeInTheDocument();
            expect(screen.getAllByText('3 Nachrichten')).toHaveLength(1);
        });

        it('markiert beim Öffnen alle ungelesenen Nachrichten des Verlaufs als gelesen', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const mock = setup();
            renderEmailCenter();
            await user.click(await screen.findByText('Re: Anfrage Terrasse'));
            await waitFor(() => {
                expect(mock).toHaveBeenCalledWith('/api/emails/16/mark-read', { method: 'POST' });
                expect(mock).toHaveBeenCalledWith('/api/emails/17/mark-read', { method: 'POST' });
            });
        });

        it('legt beim Löschen einer Zeile den ganzen Verlauf in den Papierkorb', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const mock = setup();
            renderEmailCenter();
            await user.click(await screen.findByText('Re: Anfrage Terrasse'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByText('In Papierkorb'));
            await waitFor(() => {
                expect(mock).toHaveBeenCalledWith('/api/emails/16', { method: 'DELETE' });
                expect(mock).toHaveBeenCalledWith('/api/emails/17', { method: 'DELETE' });
            });
            expect(screen.queryByText('Re: Anfrage Terrasse')).not.toBeInTheDocument();
        });

        it('belegt bei "Allen antworten" An und CC ohne eigene Adresse vor', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            setup();
            renderEmailCenter();
            await user.click(await screen.findByText('Re: Anfrage Terrasse'));
            const header = await screen.findByTestId('email-detail-header');
            await user.click(within(header).getByRole('button', { name: /Allen antworten/i }));
            await waitFor(() => expect(screen.getByText('E-Mail senden')).toBeInTheDocument());
            expect(screen.getByTitle('max@example.com')).toBeInTheDocument();
            expect(screen.getByTitle('erika@example.com')).toBeInTheDocument();
            expect(screen.getByTitle('architekt@example.org')).toBeInTheDocument();
            expect(screen.queryByTitle(/handwerk@example\.com/)).not.toBeInTheDocument();
        });
    });

    describe('Thread-Kundenempfänger und Antwort-Logik', () => {
        it('setzt beim Antworten auf eine Ausgangsmail den Kunden als Zieladresse, nicht die eigene Adresse', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const outEmail = {
                id: 42,
                type: 'EMAIL',
                direction: 'OUT' as const,
                subject: 'Angebot Metallgeländer',
                sender: 'Musterbetrieb GmbH',
                fromAddress: 'info@musterbetrieb.example',
                recipient: 'kunde@architekt-muster.example',
                body: 'Hier ist das Angebot.',
                sentAt: new Date().toISOString(),
                isRead: true,
                zuordnungTyp: 'KEINE',
                attachments: []
            };

            vi.stubGlobal('fetch', mockFetchResponses({
                '/api/emails/sent': [outEmail],
                '/api/emails/42': outEmail,
                '/api/emails/42/thread': {
                    rootEmailId: 42,
                    focusedEmailId: 42,
                    emails: [{
                        id: 42,
                        subject: 'Angebot Metallgeländer',
                        fromAddress: 'info@musterbetrieb.example',
                        recipient: 'kunde@architekt-muster.example',
                        direction: 'OUT',
                        sentAt: new Date().toISOString(),
                        attachments: []
                    }]
                }
            }));

            renderEmailCenter('sent');
            await waitFor(() => expect(screen.getByText('Angebot Metallgeländer')).toBeInTheDocument());

            // E-Mail auswählen
            await user.click(screen.getByText('Angebot Metallgeländer'));
            await waitFor(() => expect(screen.getByText('Hier ist das Angebot.')).toBeInTheDocument());

            // Auf "Antworten" im Header klicken
            const replyBtn = screen.getAllByRole('button', { name: /Antworten/i })[0];
            await user.click(replyBtn);

            // Compose-Formular öffnet sich: Empfängerfeld muss den Kunden enthalten und NICHT die eigene Adresse!
            await waitFor(() => expect(screen.getByText('E-Mail senden')).toBeInTheDocument());
            expect(screen.getByTitle('kunde@architekt-muster.example')).toBeInTheDocument();
        });
        it('behält beim Antworten auf eine Rundmail alle Empfänger mit ihren jeweiligen Namen', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const roundmail = {
                id: 43,
                type: 'EMAIL',
                direction: 'OUT' as const,
                subject: 'Rundmail Protokoll',
                sender: 'Musterbetrieb GmbH',
                fromAddress: 'info@musterbetrieb.example',
                recipient: '"Anna" <anna@example.com>, "Ben" <ben@example.com>',
                body: 'Anbei das Protokoll.',
                sentAt: new Date().toISOString(),
                isRead: true,
                zuordnungTyp: 'KEINE',
                kundeName: 'Muster Architekten',
                attachments: []
            };

            vi.stubGlobal('fetch', mockFetchResponses({
                '/api/emails/sent': [roundmail],
                '/api/emails/43': roundmail,
                '/api/emails/43/thread': {
                    rootEmailId: 43,
                    focusedEmailId: 43,
                    emails: [{
                        id: 43,
                        subject: 'Rundmail Protokoll',
                        fromAddress: 'info@musterbetrieb.example',
                        recipient: '"Anna" <anna@example.com>, "Ben" <ben@example.com>',
                        direction: 'OUT',
                        sentAt: new Date().toISOString(),
                        attachments: []
                    }]
                }
            }));

            renderEmailCenter('sent');
            await waitFor(() => expect(screen.getByText('Rundmail Protokoll')).toBeInTheDocument());

            await user.click(screen.getByText('Rundmail Protokoll'));
            await waitFor(() => expect(screen.getByText('Anbei das Protokoll.')).toBeInTheDocument());

            const replyBtn = screen.getAllByRole('button', { name: /Antworten/i })[0];
            await user.click(replyBtn);

            await waitFor(() => expect(screen.getByText('E-Mail senden')).toBeInTheDocument());

            // Beide Empfänger müssen mit ihren eigenen Namen enthalten sein
            expect(screen.getByTitle('"Anna" <anna@example.com>')).toBeInTheDocument();
            expect(screen.getByTitle('"Ben" <ben@example.com>')).toBeInTheDocument();
        });

        it('behält E-Mail-Adresse im Zitatkopf sichtbar dank HTML-Maskierung', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const emailWithBrackets = {
                id: 44,
                type: 'EMAIL',
                direction: 'OUT' as const,
                subject: 'Re: Sichtbarer Zitatkopf',
                sender: 'Musterbetrieb GmbH',
                fromAddress: 'info@musterbetrieb.example',
                recipient: '"Anna" <anna@example.com>',
                body: 'Klarer Textkörper',
                sentAt: '2026-09-11T16:00:00',
                isRead: true,
                zuordnungTyp: 'KEINE',
                attachments: []
            };

            vi.stubGlobal('fetch', mockFetchResponses({
                '/api/emails/sent': [emailWithBrackets],
                '/api/emails/44': emailWithBrackets,
                '/api/emails/44/thread': {
                    rootEmailId: 44,
                    focusedEmailId: 44,
                    emails: [emailWithBrackets]
                }
            }));

            renderEmailCenter('sent');
            await waitFor(() => expect(screen.getByText('Re: Sichtbarer Zitatkopf')).toBeInTheDocument());

            await user.click(screen.getByText('Re: Sichtbarer Zitatkopf'));
            await waitFor(() => expect(screen.getByText('Klarer Textkörper')).toBeInTheDocument());

            const replyBtn = screen.getAllByRole('button', { name: /Antworten/i })[0];
            await user.click(replyBtn);

            await waitFor(() => expect(screen.getByText('E-Mail senden')).toBeInTheDocument());

            // Die Adresse darf im Editor nicht als HTML-Tag verschwinden, sondern muss als Text sichtbar sein
            await waitFor(() => {
                expect(screen.getByText(/schrieben Sie an/i)).toBeInTheDocument();
                expect(screen.getByText(/<anna@example.com>/i)).toBeInTheDocument();
            });
        });

        it('zeigt Toast-Fehler wenn /api/emails/absender-postfaecher fehlschlaegt', async () => {
            vi.stubGlobal('fetch', vi.fn((url: string) => {
                if (url.includes('/api/emails/absender-postfaecher')) {
                    return Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve([]) } as Response);
                }
                if (url.includes('/api/emails/stats')) {
                    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(mockStats) } as Response);
                }
                if (url.includes('/api/emails/inbox')) {
                    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) } as Response);
                }
                return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) } as Response);
            }));

            renderEmailCenter('inbox');
            expect(await screen.findByText('Eigene Absenderadressen konnten nicht geladen werden.')).toBeInTheDocument();
        });

        it('laesst Empfaengerfeld leer und warnt per Toast wenn alle Empfaenger eigene Adressen sind', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const selfSentEmail = {
                id: 45,
                type: 'EMAIL',
                direction: 'OUT' as const,
                subject: 'Selbst gesendete Notiz',
                sender: 'Musterbetrieb GmbH',
                fromAddress: 'info@musterbetrieb.example',
                recipient: 'info@musterbetrieb.example',
                body: 'Nur eine interne Notiz an mich selbst.',
                sentAt: '2026-09-11T16:00:00',
                isRead: true,
                zuordnungTyp: 'KEINE',
                attachments: []
            };

            vi.stubGlobal('fetch', mockFetchResponses({
                '/api/emails/sent': [selfSentEmail],
                '/api/emails/45': selfSentEmail,
                '/api/emails/45/thread': {
                    rootEmailId: 45,
                    focusedEmailId: 45,
                    emails: [selfSentEmail]
                },
                '/api/emails/absender-postfaecher': [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }]
            }));

            renderEmailCenter('sent');
            await waitFor(() => expect(screen.getByText('Selbst gesendete Notiz')).toBeInTheDocument());

            await user.click(screen.getByText('Selbst gesendete Notiz'));
            await waitFor(() => expect(screen.getByText('Nur eine interne Notiz an mich selbst.')).toBeInTheDocument());

            const replyBtn = screen.getAllByRole('button', { name: /Antworten/i })[0];
            await user.click(replyBtn);

            await waitFor(() => expect(screen.getByText('E-Mail senden')).toBeInTheDocument());

            // Toast-Hinweis muss erscheinen
            expect(await screen.findByText('Kein externer Empfänger gefunden – bitte Empfänger manuell eingeben.')).toBeInTheDocument();

            // Eigene Adresse darf keinesfalls im Empfängerfeld stehen!
            expect(screen.queryByTitle('info@musterbetrieb.example')).not.toBeInTheDocument();

            // Ohne ermittelbaren Empfänger muss das An-Feld eintragbar sein (sonst Sackgasse)
            expect(screen.getByRole('textbox', { name: 'Empfänger' })).not.toHaveAttribute('readonly');

            // Zitatkopf ohne unvollständiges "an :"
            expect(screen.getByText(/schrieben Sie:/i)).toBeInTheDocument();
            expect(screen.queryByText(/schrieben Sie an :/i)).not.toBeInTheDocument();
        });


    });

    describe('Löschen mit Fehlerprüfung', () => {
        /** Lässt DELETE auf den genannten Adressen scheitern, alles andere wie gehabt. */
        function mitLoeschFehlern(basis: ReturnType<typeof mockFetchResponses>, fehler: Record<string, () => Response>) {
            const mock = vi.fn((url: string, options?: RequestInit) => {
                if (options?.method === 'DELETE' && fehler[url]) return Promise.resolve(fehler[url]());
                return basis(url, options);
            });
            vi.stubGlobal('fetch', mock);
            return mock;
        }

        it('zeigt die Mail wieder an und meldet den Grund vom Server, wenn das Löschen scheitert', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            mitLoeschFehlern(mockFetchResponses({
                '/api/emails/1': mockEmails[0],
                '/api/emails/1/thread': { rootEmailId: 1, focusedEmailId: 1, emails: [mockEmails[0]] },
            }), {
                '/api/emails/1': () => new Response(JSON.stringify({ message: 'Der Mailserver ist gerade nicht erreichbar.' }), { status: 503 }),
            });
            renderEmailCenter();
            await user.click(await screen.findByText('Angebot für Treppe'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByText('In Papierkorb'));

            expect(await screen.findByText('Der Mailserver ist gerade nicht erreichbar.')).toBeInTheDocument();
            // Die Liste kommt neu vom Server – die Mail steht wieder drin.
            expect(await screen.findByText('Angebot für Treppe')).toBeInTheDocument();
        });

        it('meldet „n von m“, wenn nur ein Teil eines Verlaufs gelöscht werden konnte', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            const anfrage = {
                id: 16, type: 'EMAIL', direction: 'IN' as const, subject: 'Anfrage Terrasse',
                fromAddress: 'max@example.com', body: 'Ich hätte gerne ein Angebot.',
                sentAt: '2026-09-16T09:00:00', isRead: true, zuordnungTyp: 'KEINE', attachments: [], threadRootId: 16,
            };
            const nachfrage = { ...anfrage, id: 17, subject: 'Re: Anfrage Terrasse', parentEmailId: 16, sentAt: '2026-09-17T09:00:00' };
            const mock = mitLoeschFehlern(mockFetchResponses({
                '/api/emails/inbox': [nachfrage, anfrage],
                '/api/emails/16': anfrage, '/api/emails/17': nachfrage,
                '/api/emails/17/thread': { rootEmailId: 16, focusedEmailId: 17, emails: [anfrage, nachfrage] },
            }), {
                '/api/emails/17': () => new Response('', { status: 500 }),
            });
            renderEmailCenter();
            await user.click(await screen.findByText('Re: Anfrage Terrasse'));
            await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
            await user.click(await screen.findByText('In Papierkorb'));

            expect(await screen.findByText('1 von 2 E-Mails konnten nicht gelöscht werden.')).toBeInTheDocument();
            expect(mock).toHaveBeenCalledWith('/api/emails/16', { method: 'DELETE' });
            expect(await screen.findByText('Re: Anfrage Terrasse')).toBeInTheDocument();
        });

        it('lässt einen Entwurf stehen und meldet den Fehler, wenn er nicht gelöscht werden konnte', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
            mitLoeschFehlern(mockFetchResponses({
                '/api/emails/drafts': [{ id: 71, recipient: 'max.mustermann@example.com', subject: 'Angebot Treppe', body: '<p>Hallo</p>', updatedAt: '2026-10-10T09:00:00' }],
            }), {
                '/api/emails/drafts/71': () => new Response('', { status: 500 }),
            });
            renderEmailCenter('drafts');
            await screen.findByText('Angebot Treppe');
            await user.click(screen.getByRole('button', { name: 'Entwurf löschen' }));

            expect(await screen.findByText('Der Entwurf konnte nicht gelöscht werden.')).toBeInTheDocument();
            expect(screen.getByText('Angebot Treppe')).toBeInTheDocument();
        });
    });
});

describe('EmailCenter – Zuordnen, Entwürfe und Ordner-Hinweise', () => {
    beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
    });

    afterEach(() => {
        anmeldung.wert = null;
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        vi.useRealTimers();
    });

    it('ordnet eine Mail einem Lieferanten zu', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
        const basis = mockFetchResponses({
            '/api/emails/1': mockEmails[0],
            '/api/emails/1/thread': { rootEmailId: 1, focusedEmailId: 1, emails: [mockEmails[0]] },
            '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
        });
        const mock = vi.fn((url: string, options?: RequestInit) => {
            if (url.startsWith('/api/lieferanten?')) {
                return Promise.resolve({ ok: true, json: () => Promise.resolve({ lieferanten: [{ id: 33, lieferantenname: 'Musterstahl GmbH' }], gesamt: 1 }) });
            }
            return basis(url, options);
        });
        vi.stubGlobal('fetch', mock);
        renderEmailCenter();

        await user.click(await screen.findByText('Angebot für Treppe'));
        await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Zuordnen' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).queryByRole('button', { name: 'Zuordnung aufheben' })).toBeNull();

        await user.click(within(dialog).getByRole('button', { name: 'Lieferant' }));
        await user.type(within(dialog).getByPlaceholderText('Lieferant suchen...'), 'muster');
        await act(async () => { vi.advanceTimersByTime(400); });
        await user.click(await within(dialog).findByRole('button', { name: /Musterstahl GmbH/ }));

        await waitFor(() => expect(mock).toHaveBeenCalledWith('/api/emails/1/assign/lieferant/33', { method: 'POST' }));
    });

    it('hebt die Zuordnung einer zugeordneten Mail auf', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
        const zugeordnet = { ...mockEmails[0], zuordnungTyp: 'PROJEKT', projektId: 5, projektName: 'Garagentor', projektAuftragsnummer: '2026-001' };
        const mock = mockFetchResponses({
            '/api/emails/inbox': [zugeordnet, mockEmails[1]],
            '/api/emails/1': zugeordnet,
            '/api/emails/1/thread': { rootEmailId: 1, focusedEmailId: 1, emails: [zugeordnet] },
            '/api/emails/1/possible-assignments': { projekte: [], anfragen: [] },
        });
        vi.stubGlobal('fetch', mock);
        renderEmailCenter();

        await user.click(await screen.findByText('Angebot für Treppe'));
        await user.click(await screen.findByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
        await user.click(await screen.findByRole('menuitem', { name: 'Zuordnen' }));
        const dialog = await screen.findByRole('dialog');
        expect(within(dialog).getByText('Projekt 2026-001 · Garagentor')).toBeInTheDocument();

        await user.click(within(dialog).getByRole('button', { name: 'Zuordnung aufheben' }));

        await waitFor(() => expect(mock).toHaveBeenCalledWith('/api/emails/1/unassign', { method: 'POST' }));
        expect(await screen.findByText('Zuordnung wurde aufgehoben.')).toBeInTheDocument();
    });

    it('kennzeichnet fremde Entwürfe mit „von …“ und erklärt die Entwürfe per Info-Knopf', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
        anmeldung.wert = { user: { displayName: 'Max Mustermann' }, isAdmin: true };
        vi.stubGlobal('fetch', mockFetchResponses({
            '/api/emails/drafts': [
                { id: 71, recipient: 'kunde@example.com', subject: 'Eigener Entwurf', body: '<p>Hallo</p>', updatedAt: '2026-10-10T09:00:00', erstelltVon: 'Max Mustermann' },
                { id: 72, recipient: 'kunde@example.com', subject: 'Fremder Entwurf', body: '<p>Hallo</p>', updatedAt: '2026-10-11T09:00:00', erstelltVon: 'Erika Musterfrau' },
                { id: 73, recipient: 'kunde@example.com', subject: 'Alter Entwurf', body: '<p>Hallo</p>', updatedAt: '2026-10-09T09:00:00', erstelltVon: null },
            ],
        }));
        renderEmailCenter('drafts');

        await screen.findByText('Fremder Entwurf');
        expect(screen.getByText('von Erika Musterfrau')).toBeInTheDocument();
        expect(screen.queryByText('von Max Mustermann')).toBeNull();
        expect(screen.getAllByText(/^von /)).toHaveLength(1);

        await user.click(screen.getByRole('button', { name: 'Mehr zu: Entwürfe' }));
        expect(await screen.findByText(/Du siehst nur deine eigenen Entwürfe/)).toBeInTheDocument();
    });

    it.each(['trash', 'spam', 'newsletter'])('zeigt im Ordner %s den 30-Tage-Hinweis', async (ordner) => {
        vi.stubGlobal('fetch', mockFetchResponses());
        renderEmailCenter(ordner);
        expect(await screen.findByText('E-Mails in diesem Ordner werden nach 30 Tagen automatisch endgültig gelöscht.')).toBeInTheDocument();
    });

    it('zeigt den 30-Tage-Hinweis nicht im Posteingang und erklärt „Nicht zugeordnet“', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime, delay: null });
        vi.stubGlobal('fetch', mockFetchResponses({ '/api/emails/unassigned': [] }));
        renderEmailCenter('inbox');
        await screen.findByText('Angebot für Treppe');
        expect(screen.queryByText(/nach 30 Tagen automatisch/)).toBeNull();

        await user.click(screen.getByText('Nicht zugeordnet'));
        await user.click(await screen.findByRole('button', { name: 'Mehr zu: Nicht zugeordnet' }));
        expect(await screen.findByText(/Mails von unbekannten Absendern bleiben im Posteingang/)).toBeInTheDocument();
    });
});
