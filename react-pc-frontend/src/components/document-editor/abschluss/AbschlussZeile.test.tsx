/**
 * Eine Zeile im Abrechnungsstand: anzeigen, Text ändern, ausblenden.
 * DSGVO: nur Dummy-Texte.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AbschlussZeile } from './AbschlussZeile';
import type { AbschlussZeileAnpassung } from './abschlussZeilen';

function zeile(props: { anpassung?: AbschlussZeileAnpassung; onAendern?: (neu?: AbschlussZeileAnpassung) => void } = {}) {
    return render(
        <AbschlussZeile
            symbol={<span />}
            automatischeBezeichnung="Zusätzliche Leistungen"
            automatischeUnterzeile="Treppengeländer Edelstahl"
            betrag={<span>+ 1.280,14 €</span>}
            {...props}
        />,
    );
}

describe('AbschlussZeile', () => {
    it('zeigt den automatischen Text und ohne Callback keine Knöpfe (gesperrt)', () => {
        zeile();

        expect(screen.getByText('Zusätzliche Leistungen')).toBeInTheDocument();
        expect(screen.getByText('Treppengeländer Edelstahl')).toBeInTheDocument();
        expect(screen.queryByRole('button')).toBeNull();
    });

    it('zeigt eigene Texte statt der automatischen', () => {
        zeile({ anpassung: { bezeichnung: 'Mehrleistung', unterzeile: 'Glasfüllung statt Lochblech' } });

        expect(screen.getByText('Mehrleistung')).toBeInTheDocument();
        expect(screen.getByText('Glasfüllung statt Lochblech')).toBeInTheDocument();
        expect(screen.queryByText('Zusätzliche Leistungen')).toBeNull();
    });

    it('uebernimmt den geaenderten Text mit Enter', () => {
        const onAendern = vi.fn();
        zeile({ onAendern });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));
        const bezeichnung = screen.getByRole('textbox', { name: 'Bezeichnung' });
        expect(bezeichnung).toHaveAttribute('placeholder', 'Zusätzliche Leistungen');
        expect(bezeichnung).toHaveAttribute('maxLength', '80');
        expect(screen.getByRole('textbox', { name: 'Unterzeile' })).toHaveAttribute('maxLength', '200');

        fireEvent.change(bezeichnung, { target: { value: 'Mehrleistung' } });
        fireEvent.keyDown(bezeichnung, { key: 'Enter' });

        expect(onAendern).toHaveBeenCalledWith({ bezeichnung: 'Mehrleistung' });
        expect(screen.queryByRole('textbox')).toBeNull();
    });

    it('verwirft die Eingabe mit Escape', () => {
        const onAendern = vi.fn();
        zeile({ onAendern });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));
        const unterzeile = screen.getByRole('textbox', { name: 'Unterzeile' });
        fireEvent.change(unterzeile, { target: { value: 'Etwas anderes' } });
        fireEvent.keyDown(unterzeile, { key: 'Escape' });

        expect(onAendern).not.toHaveBeenCalled();
        expect(screen.getByText('Treppengeländer Edelstahl')).toBeInTheDocument();
    });

    it('uebernimmt beim Verlassen der Felder', () => {
        const onAendern = vi.fn();
        zeile({ onAendern });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));
        const unterzeile = screen.getByRole('textbox', { name: 'Unterzeile' });
        fireEvent.change(unterzeile, { target: { value: 'Glasfüllung' } });
        fireEvent.focusOut(unterzeile, { relatedTarget: document.body });

        expect(onAendern).toHaveBeenCalledWith({ unterzeile: 'Glasfüllung' });
    });

    it('meldet nichts, wenn der Text gleich bleibt', () => {
        const onAendern = vi.fn();
        zeile({ onAendern });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));
        fireEvent.keyDown(screen.getByRole('textbox', { name: 'Bezeichnung' }), { key: 'Enter' });

        expect(onAendern).not.toHaveBeenCalled();
    });

    it('setzt eigene Texte auf automatisch zurueck', () => {
        const onAendern = vi.fn();
        zeile({ onAendern, anpassung: { bezeichnung: 'Mehrleistung' } });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));
        fireEvent.click(screen.getByRole('button', { name: 'Automatischen Text verwenden' }));

        expect(onAendern).toHaveBeenCalledWith(undefined);
    });

    it('bietet "Automatischen Text verwenden" nur bei eigenen Texten an', () => {
        zeile({ onAendern: vi.fn() });

        fireEvent.click(screen.getByRole('button', { name: 'Text ändern' }));

        expect(screen.queryByRole('button', { name: 'Automatischen Text verwenden' })).toBeNull();
    });

    it('nimmt die Zeile ohne Rueckfrage von der Rechnung', () => {
        const onAendern = vi.fn();
        zeile({ onAendern, anpassung: { bezeichnung: 'Mehrleistung' } });

        const knopf = screen.getByRole('button', { name: 'Zeile von der Rechnung entfernen' });
        // Durchgestrichenes Auge als Gegenstueck zu „Wieder einblenden“ (Auge).
        expect(knopf.querySelector('svg.lucide-eye-off')).not.toBeNull();
        fireEvent.click(knopf);

        expect(onAendern).toHaveBeenCalledWith({ bezeichnung: 'Mehrleistung', ausgeblendet: true });
    });

    it('zeigt eine ausgeblendete Zeile gedaempft und blendet sie wieder ein', () => {
        const onAendern = vi.fn();
        zeile({ onAendern, anpassung: { bezeichnung: 'Mehrleistung', ausgeblendet: true } });

        const hinweis = screen.getByText('Steht nicht auf der Rechnung');
        // Volle Deckkraft fuer den Hinweis, nur Bezeichnung und Betrag sind gedaempft.
        expect(hinweis.closest('.opacity-60')).toBeNull();
        expect(hinweis).toHaveClass('text-slate-600');
        expect(screen.getByText('Mehrleistung').closest('.opacity-60')).not.toBeNull();
        expect(screen.queryByRole('button', { name: 'Text ändern' })).toBeNull();

        fireEvent.click(screen.getByRole('button', { name: 'Wieder einblenden' }));

        expect(onAendern).toHaveBeenCalledWith({ bezeichnung: 'Mehrleistung' });
    });

    it('zeigt eine ausgeblendete Zeile gesperrt ohne Knopf', () => {
        zeile({ anpassung: { ausgeblendet: true } });

        expect(screen.getByText('Steht nicht auf der Rechnung')).toBeInTheDocument();
        expect(screen.queryByRole('button')).toBeNull();
    });
});
