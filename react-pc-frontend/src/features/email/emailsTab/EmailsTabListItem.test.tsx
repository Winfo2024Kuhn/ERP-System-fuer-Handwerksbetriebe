import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EmailsTabListItem } from './EmailsTabListItem';
import type { EmailsTabEintrag } from './emailsTabTypes';

/** Dummy-Daten (DSGVO): Max Mustermann, max@example.com. */
function mail(overrides: Partial<EmailsTabEintrag> = {}): EmailsTabEintrag {
    return {
        id: 1,
        subject: 'Anfrage Geländer',
        fromAddress: 'max@example.com',
        recipient: 'firma@example.com',
        direction: 'IN',
        sentAt: '2026-10-09T10:05:00',
        vorschau: 'Bitte um Angebot',
        nachrichtenAnzahl: 1,
        mitAnhang: false,
        zustellStatus: null,
        zustellFehler: null,
        ...overrides,
    };
}

function rendere(email: EmailsTabEintrag, onOpen = vi.fn()) {
    const view = render(<EmailsTabListItem email={email} onOpen={onOpen} />);
    return { ...view, onOpen };
}

describe('EmailsTabListItem', () => {
    it('zeigt Eingang, Datum, Betreff, Absender und Vorschau', () => {
        rendere(mail());
        expect(screen.getByText('Eingang')).toBeInTheDocument();
        expect(screen.getByText('09.10.2026, 10:05')).toBeInTheDocument();
        expect(screen.getByText('Anfrage Geländer')).toBeInTheDocument();
        expect(screen.getByText(/Von:/)).toHaveTextContent('Von: max@example.com');
        expect(screen.getByText('Bitte um Angebot')).toBeInTheDocument();
        expect(screen.queryByText(/Nachrichten$/)).not.toBeInTheDocument();
    });

    it('zeigt bei Ausgang den Empfänger mit „An:“', () => {
        rendere(mail({ direction: 'OUT', fromAddress: 'firma@example.com', recipient: 'max@example.com' }));
        expect(screen.getByText(/An:/)).toHaveTextContent('An: max@example.com');
    });

    it('zeigt Ersatztexte ohne Betreff, Empfänger und Datum und lässt die leere Vorschau weg', () => {
        const { container } = rendere(mail({
            direction: 'OUT', subject: null, recipient: null, vorschau: '', sentAt: null,
        }));
        expect(screen.getByText('Ausgang')).toBeInTheDocument();
        expect(screen.getByText('(kein Betreff)')).toBeInTheDocument();
        expect(screen.getByText(/An:/)).toHaveTextContent('An: Unbekannt');
        expect(screen.getByText('-')).toBeInTheDocument();
        expect(container.querySelector('.line-clamp-2')).toBeNull();
    });

    it('zählt die Nachrichten eines Verlaufs im Chip, hell in Rose statt gefüllt', () => {
        rendere(mail({ nachrichtenAnzahl: 3 }));
        const chip = screen.getByTitle('3 Nachrichten in diesem Verlauf');
        expect(chip).toHaveTextContent('3 Nachrichten');
        expect(chip.className).toContain('bg-rose-50');
        expect(chip.className).toContain('text-rose-700');
        expect(chip.className).not.toContain('bg-rose-600');
    });

    it('zeigt die Vorschau in lesbarem Grau (slate-500)', () => {
        rendere(mail());
        expect(screen.getByText('Bitte um Angebot').className).toContain('text-slate-500');
    });

    it('warnt bei einer Mail, die nicht angekommen ist', () => {
        rendere(mail({
            direction: 'OUT', zustellStatus: 'UNZUSTELLBAR', zustellFehler: '550 5.1.1 User unknown',
        }));
        expect(screen.getByText('Nicht angekommen')).toBeInTheDocument();
        expect(screen.getByTitle('550 5.1.1 User unknown')).toHaveTextContent('Diese E-Mail-Adresse gibt es nicht.');
    });

    it('zeigt die Büroklammer nur, wenn der Server einen Anhang meldet', () => {
        const { container, rerender } = rendere(mail({ mitAnhang: false }));
        expect(container.querySelector('.lucide-paperclip')).toBeNull();

        expect(screen.getByRole('button', { name: 'Anfrage Geländer' })).not.toHaveAccessibleDescription(/mit Anhang/);

        rerender(<EmailsTabListItem email={mail({ mitAnhang: true })} onOpen={vi.fn()} />);
        expect(container.querySelector('.lucide-paperclip')).not.toBeNull();
        expect(screen.getByRole('button', { name: 'Anfrage Geländer' })).toHaveAccessibleDescription(/mit Anhang/);
    });

    it('öffnet die Mail per Klick', () => {
        const email = mail();
        const { onOpen } = rendere(email);
        fireEvent.click(screen.getByText('Anfrage Geländer'));
        expect(onOpen).toHaveBeenCalledWith(email);
    });

    it('ist ein Knopf mit dem Betreff als Namen und öffnet per Enter und Leertaste', async () => {
        const email = mail();
        const { onOpen } = rendere(email);
        const zeile = screen.getByRole('button', { name: 'Anfrage Geländer' });
        expect(zeile).toHaveAttribute('type', 'button');
        expect(zeile).toHaveAccessibleDescription(/Eingang.*Von: max@example\.com/);
        expect(zeile.className).toContain('focus-visible:ring-2');

        const nutzer = userEvent.setup();
        await nutzer.tab();
        expect(zeile).toHaveFocus();
        await nutzer.keyboard('{Enter}');
        await nutzer.keyboard(' ');
        expect(onOpen).toHaveBeenCalledTimes(2);
    });

    it('zeigt die Richtung als Slate-Chip mit Pfeil, ohne Blau oder Grün, und ohne Animation auf der Zeile', () => {
        const { container, rerender } = rendere(mail());
        const eingang = screen.getByText('Eingang').closest('span')!;
        expect(eingang.className).toContain('bg-slate-100');
        expect(eingang.querySelector('.lucide-arrow-down-left')).toHaveAttribute('aria-hidden', 'true');
        expect(container.innerHTML).not.toMatch(/blue-|green-|transition-all/);

        rerender(<EmailsTabListItem email={mail({ direction: 'OUT' })} onOpen={vi.fn()} />);
        expect(screen.getByText('Ausgang').closest('span')!.querySelector('.lucide-arrow-up-right')).not.toBeNull();
    });
});
