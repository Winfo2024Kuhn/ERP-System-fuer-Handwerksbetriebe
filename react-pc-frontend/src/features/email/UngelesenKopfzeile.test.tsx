import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { UngelesenKopfzeile } from './UngelesenKopfzeile';

const infoKnopf = () => screen.getByRole('button', { name: 'Mehr zu: Gelesen gilt nur für dich' });

describe('UngelesenKopfzeile', () => {
    it('zeigt die Zahl der ungelesenen Mails und ruft „Alle gelesen“ auf', () => {
        const onAlleGelesen = vi.fn();
        render(<UngelesenKopfzeile anzahl={3} onAlleGelesen={onAlleGelesen} />);
        expect(screen.getByText('3 ungelesen')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Alle gelesen' }));
        expect(onAlleGelesen).toHaveBeenCalledTimes(1);
    });

    it('hat das (i) direkt neben „Alle gelesen“', () => {
        render(<UngelesenKopfzeile anzahl={1} onAlleGelesen={() => {}} />);
        const alleGelesen = screen.getByRole('button', { name: 'Alle gelesen' });
        expect(alleGelesen.nextElementSibling).toBe(infoKnopf());
        expect(alleGelesen).toHaveAttribute('type', 'button');
    });

    it('erklärt nach Klick, dass „gelesen“ nur für den eigenen Benutzer gilt', () => {
        render(<UngelesenKopfzeile anzahl={1} onAlleGelesen={() => {}} />);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        fireEvent.click(infoKnopf());
        const popover = screen.getByRole('dialog', { name: 'Gelesen gilt nur für dich' });
        expect(within(popover).getByText('Was passiert im Hintergrund?')).toBeVisible();
        expect(within(popover).getByText(/für jeden Benutzer einzeln/)).toBeVisible();
        expect(within(popover).getByText('Was bewirkt das?')).toBeVisible();
        expect(within(popover).getByText(/ist sie nur bei dir gelesen/)).toBeVisible();
        expect(within(popover).getByText(/ihre Glocke meldet sie weiter/)).toBeVisible();
        expect(within(popover).getByText('Gesendete Mails gelten immer als gelesen.')).toBeVisible();
    });
});
