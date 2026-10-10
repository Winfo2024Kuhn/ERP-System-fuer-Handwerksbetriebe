import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { InfoAbschnitt, InfoButton } from './info-button';

function zeige() {
    return render(
        <div>
            <input aria-label="Anderes Feld" />
            <p>Leere Fläche</p>
            <InfoButton titel="Start der Zeiterfassung">
                <InfoAbschnitt titel="Was bewirkt das?">
                    <p>Monate davor müssen nicht abgeschlossen werden.</p>
                </InfoAbschnitt>
            </InfoButton>
        </div>,
    );
}

const knopf = () => screen.getByRole('button', { name: 'Mehr zu: Start der Zeiterfassung' });

describe('InfoButton', () => {
    it('ist ein echter Knopf mit Namen und zunächst zugeklappt', () => {
        zeige();
        expect(knopf()).toHaveAttribute('type', 'button');
        expect(knopf()).toHaveAttribute('aria-expanded', 'false');
        expect(knopf()).toHaveAttribute('aria-haspopup', 'dialog');
        expect(knopf()).not.toHaveAttribute('aria-controls');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('öffnet auf Klick, zeigt Überschrift und Inhalt und verknüpft beides per aria', () => {
        zeige();
        fireEvent.click(knopf());
        const popover = screen.getByRole('dialog', { name: 'Start der Zeiterfassung' });
        expect(popover).toBeVisible();
        expect(knopf()).toHaveAttribute('aria-expanded', 'true');
        expect(knopf()).toHaveAttribute('aria-controls', popover.id);
        expect(screen.getByText('Was bewirkt das?')).toBeVisible();
        expect(screen.getByText('Monate davor müssen nicht abgeschlossen werden.')).toBeVisible();
        expect(popover).toHaveFocus();
    });

    it('schließt bei erneutem Klick und behält den Fokus am Knopf', () => {
        zeige();
        fireEvent.click(knopf());
        fireEvent.click(knopf());
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(knopf()).toHaveAttribute('aria-expanded', 'false');
        expect(knopf()).toHaveFocus();
    });

    it('schließt bei Escape und gibt den Fokus an den Knopf zurück', () => {
        zeige();
        fireEvent.click(knopf());
        fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(knopf()).toHaveFocus();
    });

    it('schließt bei Tab und lässt den Fokus beim Knopf weiterwandern', () => {
        zeige();
        fireEvent.click(knopf());
        fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Tab', shiftKey: true });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(knopf()).toHaveFocus();
    });

    it('schließt bei Klick daneben – auf leere Fläche mit Fokus zurück, auf ein Feld ohne', () => {
        zeige();
        fireEvent.click(knopf());
        fireEvent.mouseDown(screen.getByText('Leere Fläche'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(knopf()).toHaveFocus();

        fireEvent.click(knopf());
        const feld = screen.getByRole('textbox', { name: 'Anderes Feld' });
        fireEvent.mouseDown(feld);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(knopf()).not.toHaveFocus();
    });

    it('bleibt bei Klick ins Popover offen', () => {
        zeige();
        fireEvent.click(knopf());
        fireEvent.mouseDown(screen.getByText('Monate davor müssen nicht abgeschlossen werden.'));
        expect(screen.getByRole('dialog')).toBeVisible();
    });

    it('bleibt innerhalb des Fensters, auch rechts am Rand', () => {
        zeige();
        const fensterBreite = window.innerWidth;
        knopf().getBoundingClientRect = () => ({ left: fensterBreite - 10, right: fensterBreite, top: 10, bottom: 34, width: 24, height: 24, x: fensterBreite - 10, y: 10, toJSON: () => ({}) });
        fireEvent.click(knopf());
        const stil = screen.getByRole('dialog').style;
        expect(parseFloat(stil.left) + parseFloat(stil.width)).toBeLessThanOrEqual(fensterBreite - 8);
        expect(parseFloat(stil.left)).toBeGreaterThanOrEqual(8);
    });
});
