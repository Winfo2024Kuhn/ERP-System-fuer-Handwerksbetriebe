import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EmailThreadBubble } from './EmailThreadBubble';
import type { EmailThreadEntry } from './threadTypes';

vi.mock('../../../components/EmailContentFrame', () => ({
    EmailContentFrame: () => <div data-testid="email-content-frame" />,
}));
vi.mock('../../../lib/scrollInDenBlick', () => ({ scrolleMittigInDenBlick: vi.fn() }));

beforeEach(() => { window.HTMLElement.prototype.scrollIntoView = vi.fn(); });

/** Dummy-Daten (DSGVO): Max Mustermann, eigener Betrieb „Handwerk GmbH“. */
function nachricht(direction: 'IN' | 'OUT'): EmailThreadEntry {
    return {
        id: direction === 'IN' ? 1 : 2,
        fromAddress: direction === 'IN' ? '"Max Mustermann" <max@example.com>' : '"Handwerk GmbH" <info@handwerk-test.example.com>',
        sentAt: '2026-11-04T10:00:00',
        direction,
        snippet: 'Guten Tag',
        attachments: [],
    };
}

function blase(direction: 'IN' | 'OUT', isFocused = false) {
    return render(<EmailThreadBubble entry={nachricht(direction)} isFocused={isFocused} showAvatar showSenderName isLast={false} />);
}

describe('EmailThreadBubble – Richtungsfarben', () => {
    it('zeigt eigene, ausgehende Mails in Slate statt Grün', () => {
        const { container } = blase('OUT');
        expect(container.innerHTML).not.toContain('emerald');
        expect(screen.getByText('Handwerk GmbH')).toHaveClass('text-slate-700');
        expect(screen.getByText('H')).toHaveClass('bg-slate-600');
    });

    it('zeigt auch die aufgeklappte ausgehende Mail ohne Grün', () => {
        const { container } = blase('OUT', true);
        expect(container.innerHTML).not.toContain('emerald');
        expect(screen.getByText('H')).toHaveClass('bg-slate-600');
    });

    it('lässt eingehende Mails in Rose', () => {
        blase('IN');
        expect(screen.getByText('Max Mustermann')).toHaveClass('text-rose-700');
        expect(screen.getByText('M')).toHaveClass('bg-rose-500');
    });
});
