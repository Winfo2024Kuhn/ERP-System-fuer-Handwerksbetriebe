import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EmailThreadView } from './EmailThreadView';
import type { EmailThread } from './EmailThreadView';

// EmailContentFrame nutzt iframe + srcDoc – im jsdom nicht vollständig unterstützt.
// Wir mocken die Komponente als einfaches div mit dem HTML-Inhalt.
vi.mock('./EmailContentFrame', () => ({
    EmailContentFrame: ({ html }: { html: string }) => (
        <div data-testid="email-content-frame" dangerouslySetInnerHTML={{ __html: html }} />
    ),
}));

// jsdom implementiert scrollIntoView nicht – global mocken
beforeEach(() => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    // matchMedia für prefers-reduced-motion mocken
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: vi.fn().mockImplementation((query: string) => ({
            matches: false,
            media: query,
            onchange: null,
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
        })),
    });
});

// ─────────────────────────────────────────────────────────────────
// TEST-HILFSFUNKTIONEN
// ─────────────────────────────────────────────────────────────────

function makeThread(overrides?: Partial<EmailThread>): EmailThread {
    return {
        rootEmailId: 1,
        focusedEmailId: 2,
        emails: [
            {
                id: 1,
                subject: 'Anfrage Sanierung Bad',
                fromAddress: '"Max Mustermann" <max@example.com>',
                recipient: '"Handwerk GmbH" <info@handwerk-test.example.com>',
                sentAt: '2026-03-10T09:14:00',
                direction: 'IN',
                snippet: 'Hallo, ich hätte Interesse an einem Angebot für die Sanierung des Bades.',
                attachments: [],
            },
            {
                id: 2,
                subject: 'Re: Anfrage Sanierung Bad',
                fromAddress: '"Handwerk GmbH" <info@handwerk-test.example.com>',
                recipient: '"Max Mustermann" <max@example.com>',
                sentAt: '2026-03-11T14:02:00',
                direction: 'OUT',
                snippet: 'Guten Tag Herr Mustermann, vielen Dank für Ihre Anfrage.',
                attachments: [],
            },
        ],
        ...overrides,
    };
}

// ─────────────────────────────────────────────────────────────────
// TESTS
// ─────────────────────────────────────────────────────────────────

describe('EmailThreadView', () => {

    it('rendert Skeleton-Loading wenn loading=true', () => {
        const thread = makeThread();
        const { container } = render(<EmailThreadView thread={thread} loading={true} />);
        // Skeleton-Bubbles haben animate-pulse
        const skeletons = container.querySelectorAll('.animate-pulse');
        expect(skeletons.length).toBeGreaterThan(0);
    });

    it('zeigt alle Nachrichten des Threads an', () => {
        render(<EmailThreadView thread={makeThread()} />);
        // Beide Snippets müssen in kollabiertem Zustand sichtbar sein
        // (unfokussierte Email zeigt 2-Zeilen-Snippet als <p>)
        expect(screen.getByText(/Hallo, ich hätte Interesse/)).toBeInTheDocument();
    });

    it('fokussierte E-Mail ist automatisch expandiert', () => {
        const thread = makeThread();
        render(<EmailThreadView thread={thread} />);
        // Die fokussierte Email (id=2) hat einen EmailContentFrame statt Snippet-Paragraph
        const frames = screen.getAllByTestId('email-content-frame');
        expect(frames.length).toBeGreaterThanOrEqual(1);
    });

    it('Klick auf kollabierte Bubble expandiert sie', () => {
        // Thread mit focusedEmailId=1: Email 2 ist initial kollabiert
        const thread = makeThread({ focusedEmailId: 1 });
        render(<EmailThreadView thread={thread} />);

        // Vor dem Klick: Snippet-Text sichtbar
        expect(screen.getByText(/Guten Tag Herr Mustermann/)).toBeInTheDocument();

        // Klick auf die kollabierte Bubble (Email id=2) – Bubble ist jetzt ein div[role=button]
        const bubble = screen.getByText(/Guten Tag Herr Mustermann/).closest('[role="button"]');
        expect(bubble).toBeTruthy();
        fireEvent.click(bubble!);

        // Nach dem Klick: EmailContentFrame erscheint für diese Bubble
        const frames = screen.getAllByTestId('email-content-frame');
        expect(frames.length).toBeGreaterThanOrEqual(2);
    });

    it('zeigt Datums-Trenner bei Tageswechsel an', () => {
        const thread = makeThread();
        render(<EmailThreadView thread={thread} />);
        // Zwei verschiedene Tage (10.03 und 11.03) → mindestens je ein Element mit diesem Text
        expect(screen.getAllByText(/10\. März 2026/).length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText(/11\. März 2026/).length).toBeGreaterThanOrEqual(1);
    });

    it('Anhänge werden als Chips angezeigt wenn Bubble expandiert ist', () => {
        const thread = makeThread({
            focusedEmailId: 1,
            emails: [
                {
                    id: 1,
                    subject: 'Mit Anhang',
                    fromAddress: 'test@example.com',
                    recipient: 'empf@example.com',
                    sentAt: '2026-03-10T10:00:00',
                    direction: 'IN',
                    snippet: 'Mail mit Anhang.',
                    attachments: [
                        {
                            id: 10,
                            originalFilename: 'angebot.pdf',
                            mimeType: 'application/pdf',
                            sizeBytes: 145000,
                            inline: false,
                        },
                    ],
                },
            ],
        });
        render(<EmailThreadView thread={thread} />);
        // Email ist fokussiert/expandiert → Chip zeigt Dateinamen
        expect(screen.getByText('angebot.pdf')).toBeInTheDocument();
    });

    it('Inline-Anhänge werden nicht als Chips angezeigt', () => {
        const thread = makeThread({
            focusedEmailId: 1,
            emails: [
                {
                    id: 1,
                    subject: 'Mit Inline-Bild',
                    fromAddress: 'absender@example.com',
                    recipient: 'empf@example.com',
                    sentAt: '2026-03-10T10:00:00',
                    direction: 'IN',
                    snippet: 'Mail mit Logo im Inline-Bild.',
                    attachments: [
                        {
                            id: 20,
                            originalFilename: 'logo.png',
                            mimeType: 'image/png',
                            sizeBytes: 3200,
                            inline: true,   // ← inline-Flag gesetzt
                        },
                    ],
                },
            ],
        });
        render(<EmailThreadView thread={thread} />);
        // inline=true → kein Chip
        expect(screen.queryByText('logo.png')).not.toBeInTheDocument();
    });

    it('thread-übergreifende Anhang-Dedup entfernt Duplikate', () => {
        const dupAttachment = {
            id: 30,
            originalFilename: 'signatur-logo.png',
            mimeType: 'image/png',
            sizeBytes: 1500,
            inline: false,
        };
        const thread = makeThread({
            focusedEmailId: 1,
            emails: [
                {
                    id: 1,
                    subject: 'Erste Nachricht',
                    fromAddress: 'a@example.com',
                    recipient: 'b@example.com',
                    sentAt: '2026-03-10T10:00:00',
                    direction: 'IN',
                    snippet: 'Erste Nachricht.',
                    attachments: [dupAttachment],
                },
                {
                    id: 2,
                    subject: 'Zweite Nachricht',
                    fromAddress: 'a@example.com',
                    recipient: 'b@example.com',
                    sentAt: '2026-03-10T11:00:00',
                    direction: 'IN',
                    snippet: 'Zweite Nachricht.',
                    attachments: [{ ...dupAttachment, id: 31 }], // gleicher Name+Größe
                },
            ],
        });
        render(<EmailThreadView thread={thread} />);
        // Dateiname darf nur einmal erscheinen (Dedup greift)
        const chips = screen.queryAllByText('signatur-logo.png');
        expect(chips.length).toBe(1);
    });

    it('leerer Thread zeigt keine Bubbles', () => {
        const thread: EmailThread = { rootEmailId: 0, focusedEmailId: 0, emails: [] };
        const { container } = render(<EmailThreadView thread={thread} />);
        // Kein div[role=button] mit Bubble-Klassen
        const bubbles = container.querySelectorAll('[role="button"][class*="rounded-2xl"]');
        expect(bubbles.length).toBe(0);
    });
});
