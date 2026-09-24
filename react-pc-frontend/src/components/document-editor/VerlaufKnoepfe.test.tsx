/**
 * Vitest-Suite fuer VerlaufKnoepfe.
 *
 * Die Liste haengt per Portal an `document.body`, genau wie bei
 * `WahlpositionMenu` -- inline gerendert wuerde sie in der schmalen
 * Kopfleiste des Dokumenteditors abgeschnitten. Diese Suite haelt die
 * beiden Symbol-Knoepfe, das Dropdown mit Mehrfach-Rueckgaengig und die
 * Tastaturwege fest.
 *
 * DSGVO: keine personenbezogenen Daten in den Testdaten noetig, die
 * Schritt-Bezeichnungen sind reine Fachbegriffe.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { VerlaufKnoepfe } from './VerlaufKnoepfe';
import type { VerlaufsEintrag, VerlaufKnoepfeProps } from './VerlaufKnoepfe';

const schritte: VerlaufsEintrag[] = [
    { id: 3, bezeichnung: 'Position gelöscht' },
    { id: 2, bezeichnung: 'Menge geändert' },
    { id: 1, bezeichnung: 'Titel geändert' },
];

const props: VerlaufKnoepfeProps = {
    kannRueckgaengig: true,
    kannWiederholen: true,
    naechstesRueckgaengig: 'Position gelöscht',
    naechstesWiederholen: 'Titel geändert',
    schritte,
    onRueckgaengig: vi.fn(),
    onWiederholen: vi.fn(),
};

const oeffne = () => fireEvent.click(screen.getByRole('button', { name: 'Liste der letzten Änderungen' }));
const eintraege = () => screen.getAllByRole('menuitem');

beforeEach(() => vi.clearAllMocks());

describe('VerlaufKnoepfe Knoepfe', () => {
    it('beschriftet die beiden Symbol-Knoepfe mit aria-label', () => {
        render(<VerlaufKnoepfe {...props} />);

        expect(screen.getByRole('button', { name: 'Rückgängig' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Wiederholen' })).toBeInTheDocument();
    });

    it('zeigt die Bezeichnung des naechsten Schritts im Tooltip', () => {
        render(<VerlaufKnoepfe {...props} />);

        expect(screen.getByRole('button', { name: 'Rückgängig' }))
            .toHaveAttribute('title', 'Rückgängig: Position gelöscht (Strg+Z)');
        expect(screen.getByRole('button', { name: 'Wiederholen' }))
            .toHaveAttribute('title', 'Wiederholen: Titel geändert (Strg+Y)');
    });

    it('deaktiviert beide Knoepfe und den Listen-Pfeil ohne Schritte, mit erklaerendem Tooltip', () => {
        render(<VerlaufKnoepfe {...props}
            kannRueckgaengig={false} kannWiederholen={false}
            naechstesRueckgaengig={null} naechstesWiederholen={null} />);

        const rueckgaengig = screen.getByRole('button', { name: 'Rückgängig' });
        const wiederholen = screen.getByRole('button', { name: 'Wiederholen' });
        const pfeil = screen.getByRole('button', { name: 'Liste der letzten Änderungen' });

        expect(rueckgaengig).toBeDisabled();
        expect(wiederholen).toBeDisabled();
        expect(pfeil).toBeDisabled();
        expect(rueckgaengig).toHaveAttribute('title', 'Nichts zum Rückgängigmachen');
        expect(wiederholen).toHaveAttribute('title', 'Nichts zum Wiederholen');
    });

    it('Klick auf Rueckgaengig ruft onRueckgaengig(1) auf', () => {
        render(<VerlaufKnoepfe {...props} />);
        fireEvent.click(screen.getByRole('button', { name: 'Rückgängig' }));

        expect(props.onRueckgaengig).toHaveBeenCalledWith(1);
    });

    it('Klick auf Wiederholen ruft onWiederholen auf', () => {
        render(<VerlaufKnoepfe {...props} />);
        fireEvent.click(screen.getByRole('button', { name: 'Wiederholen' }));

        expect(props.onWiederholen).toHaveBeenCalledTimes(1);
    });
});

describe('VerlaufKnoepfe Dropdown', () => {
    it('haengt die Liste an document.body statt in den Baum der Kopfleiste', () => {
        const { container } = render(<VerlaufKnoepfe {...props} />);
        oeffne();

        const menu = screen.getByRole('menu');
        expect(container.contains(menu)).toBe(false);
        expect(document.body.contains(menu)).toBe(true);
    });

    it('zeigt die Schritte in der gegebenen Reihenfolge (neuester oben)', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        expect(eintraege().map(el => el.textContent)).toEqual([
            'Position gelöscht', 'Menge geändert', 'Titel geändert',
        ]);
    });

    it('markiert beim Ueberfahren des dritten Eintrags die ersten drei und zeigt die Anzahl', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.mouseEnter(eintraege()[2]);

        expect(eintraege()[0]).toHaveClass('bg-rose-50');
        expect(eintraege()[1]).toHaveClass('bg-rose-50');
        expect(eintraege()[2]).toHaveClass('bg-rose-50');
        expect(screen.getByText('3 Schritte rückgängig machen')).toBeInTheDocument();
    });

    it('zeigt bei einem einzelnen Eintrag die Einzahl "1 Schritt rückgängig machen"', () => {
        render(<VerlaufKnoepfe {...props} schritte={[schritte[0]]} />);
        oeffne();

        fireEvent.mouseEnter(screen.getByRole('menuitem'));
        expect(screen.getByText('1 Schritt rückgängig machen')).toBeInTheDocument();
    });

    it('Klick auf den dritten Eintrag ruft onRueckgaengig(3) auf', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.click(eintraege()[2]);
        expect(props.onRueckgaengig).toHaveBeenCalledWith(3);
    });
});

describe('VerlaufKnoepfe Tastatur', () => {
    it('ArrowDown springt vom Pfeil auf den ersten Eintrag', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'ArrowDown' });
        expect(document.activeElement).toBe(eintraege()[0]);
    });

    it('ArrowUp springt vom Pfeil auf den letzten Eintrag', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'ArrowUp' });
        expect(document.activeElement).toBe(eintraege()[eintraege().length - 1]);
    });

    it('laeuft mit ArrowDown am Ende wieder auf den Anfang', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'End' });
        fireEvent.keyDown(document, { key: 'ArrowDown' });
        expect(document.activeElement).toBe(eintraege()[0]);
    });

    it('erreicht mit Home und End die Raender', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'End' });
        expect(document.activeElement).toBe(eintraege()[eintraege().length - 1]);

        fireEvent.keyDown(document, { key: 'Home' });
        expect(document.activeElement).toBe(eintraege()[0]);
    });

    it('Enter loest den fokussierten Eintrag aus', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'ArrowDown' });
        fireEvent.keyDown(document, { key: 'ArrowDown' });
        fireEvent.keyDown(document, { key: 'Enter' });

        expect(props.onRueckgaengig).toHaveBeenCalledWith(2);
    });

    it('Escape schliesst die Liste und gibt den Fokus an den Pfeil zurueck', () => {
        render(<VerlaufKnoepfe {...props} />);
        const pfeil = screen.getByRole('button', { name: 'Liste der letzten Änderungen' });
        fireEvent.click(pfeil);

        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
        expect(document.activeElement).toBe(pfeil);
    });

    it('schliesst mit Tab, damit der Fokus nicht hinter der Liste verschwindet', () => {
        render(<VerlaufKnoepfe {...props} />);
        oeffne();

        fireEvent.keyDown(document, { key: 'Tab' });
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
});
