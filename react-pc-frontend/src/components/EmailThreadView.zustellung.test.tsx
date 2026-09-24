import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EmailThreadView } from './EmailThreadView';
import type { EmailThread } from './EmailThreadView';

/**
 * Regressionstest fuer die Rückläufer-Warnung im Thread.
 *
 * Hintergrund: Antworten innerhalb eines Verlaufs tauchen in der Mail-Liste
 * gar nicht auf — dort stehen nur die Wurzeln der Threads. Wenn eine Antwort
 * nicht zugestellt werden kann, ist die Thread-Ansicht die einzige Stelle, an
 * der der Nutzer das je erfährt.
 *
 * Alle Daten sind Dummy-Daten (DSGVO).
 */

vi.mock('./EmailContentFrame', () => ({
    EmailContentFrame: ({ html }: { html: string }) => (
        <div data-testid="email-content-frame" dangerouslySetInnerHTML={{ __html: html }} />
    ),
}));

beforeEach(() => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
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

function threadMitAntwort(zustellung?: {
    zustellStatus?: 'OFFEN' | 'UNZUSTELLBAR';
    zustellFehler?: string;
}): EmailThread {
    return {
        rootEmailId: 1,
        focusedEmailId: 2,
        emails: [
            {
                id: 1,
                subject: 'Anfrage Balkongeländer',
                fromAddress: '"Max Mustermann" <max.mustermann@example.com>',
                recipient: '"Handwerk GmbH" <info@example.com>',
                sentAt: '2026-07-10T09:14:00',
                direction: 'IN',
                snippet: 'Hallo, ich hätte Interesse an einem Angebot.',
                attachments: [],
            },
            {
                id: 2,
                subject: 'Re: Anfrage Balkongeländer',
                fromAddress: '"Handwerk GmbH" <info@example.com>',
                recipient: '"Max Mustermann" <max.mustermann@example.com>',
                sentAt: '2026-07-17T08:02:00',
                direction: 'OUT',
                snippet: 'Guten Tag, anbei unser Angebot.',
                attachments: [],
                ...zustellung,
            },
        ],
    };
}

describe('EmailThreadView – Zustell-Warnung', () => {
    it('warnt, wenn eine Antwort im Thread nicht zugestellt werden konnte', () => {
        render(<EmailThreadView thread={threadMitAntwort({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: '550 5.1.1 User unknown',
        })} />);

        expect(screen.getByText('Nicht angekommen')).toBeInTheDocument();
    });

    it('nennt den Grund in Handwerker-Sprache', () => {
        render(<EmailThreadView thread={threadMitAntwort({
            zustellStatus: 'UNZUSTELLBAR',
            zustellFehler: '550 5.1.1 User unknown',
        })} />);

        expect(screen.getByText('Diese E-Mail-Adresse gibt es nicht. Bitte Adresse prüfen und erneut senden.'))
            .toBeInTheDocument();
    });

    it('zeigt keine Warnung bei einer normal zugestellten Antwort (Happy Path)', () => {
        render(<EmailThreadView thread={threadMitAntwort({ zustellStatus: 'OFFEN' })} />);

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
    });

    it('zeigt keine Warnung bei Alt-Daten ohne das Feld', () => {
        render(<EmailThreadView thread={threadMitAntwort()} />);

        expect(screen.queryByText('Nicht angekommen')).not.toBeInTheDocument();
    });
});
