import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DraftThreadBubble } from './DraftThreadBubble';
import type { EmailThreadEntry } from './threadTypes';

/** Dummy-Daten (DSGVO): Entwurf an Max Mustermann. */
const entwurf: EmailThreadEntry = {
    id: 9, draftId: 42, isDraft: true, direction: 'OUT', sentAt: '2026-11-04T10:00:00',
    recipient: '"Max Mustermann" <max@example.com>', subject: 'Re: Geländer', snippet: 'Guten Tag Herr Mustermann',
    attachments: [],
};

describe('DraftThreadBubble – Papierkorb', () => {
    it('ist per Rolle und Name auffindbar und löscht genau diesen Entwurf', () => {
        const loeschen = vi.fn();
        render(<DraftThreadBubble entry={entwurf} onDeleteDraft={loeschen} />);
        fireEvent.click(screen.getByRole('button', { name: 'Entwurf löschen' }));
        expect(loeschen).toHaveBeenCalledWith(42);
    });

    it('hat eine ausreichend große Tippfläche, kräftiges Icon und Fokus-Ring', () => {
        render(<DraftThreadBubble entry={entwurf} onDeleteDraft={() => {}} />);
        const knopf = screen.getByRole('button', { name: 'Entwurf löschen' });
        expect(knopf).toHaveClass('w-7', 'h-7', 'focus-visible:ring-2');
        expect(knopf).toHaveClass('text-amber-700');
        expect(knopf).not.toHaveClass('text-amber-500');
        expect(knopf.querySelector('svg')).toHaveClass('w-4', 'h-4');
    });

    it('zeigt keinen Papierkorb ohne Löschfunktion', () => {
        render(<DraftThreadBubble entry={entwurf} />);
        expect(screen.queryByRole('button', { name: 'Entwurf löschen' })).not.toBeInTheDocument();
    });
});
