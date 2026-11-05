import { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BERICHT_DEFINITIONEN } from './berichtDefinitionen';
import { BerichtFilter } from './BerichtFilter';
import type { FilterWerte } from './berichtTypen';

const MITARBEITER = [{ id: 1, name: 'Max Mustermann' }, { id: 2, name: 'Erika Musterfrau' }];
const [STEUERBERATER, MITARBEITER_BERICHT] = BERICHT_DEFINITIONEN;
const HEUTE = new Date(2026, 10, 5);

function Testrahmen({ definition, beobachter, laedt = false, fehler = null, mitarbeiter = MITARBEITER }: {
    definition: typeof STEUERBERATER;
    beobachter?: (w: FilterWerte) => void;
    laedt?: boolean;
    fehler?: string | null;
    mitarbeiter?: typeof MITARBEITER;
}) {
    const [werte, setWerte] = useState(definition.standardwerte(HEUTE));
    return (
        <BerichtFilter felder={definition.filterFelder} werte={werte} onChange={w => { setWerte(w); beobachter?.(w); }}
            mitarbeiter={mitarbeiter} mitarbeiterLaden={laedt} mitarbeiterFehler={fehler} aktuellesJahr={2026} />
    );
}

const haken = (name: string) => screen.getByRole('checkbox', { name }) as HTMLInputElement;

describe('BerichtFilter: Steuerberater', () => {
    it('zeigt Vormonat, alle Mitarbeiter gewählt und beide Schalter an', () => {
        render(<Testrahmen definition={STEUERBERATER} />);
        expect(screen.getByRole('combobox', { name: 'Monat' })).toHaveTextContent('Oktober');
        expect(screen.getByRole('combobox', { name: 'Jahr' })).toHaveTextContent('2026');
        expect(haken('Max Mustermann').checked).toBe(true);
        expect(haken('Erika Musterfrau').checked).toBe(true);
        expect(screen.getByRole('switch', { name: 'Resturlaub zeigen' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('switch', { name: 'Stundenkonto zeigen' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('button', { name: 'Mehr zu: Resturlaub' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Stundenkonto' })).toBeInTheDocument();
    });

    it('„Keine“ und „Alle“ ändern die Auswahl, einzelne Haken bleiben in Listenreihenfolge', () => {
        const beobachter = vi.fn();
        render(<Testrahmen definition={STEUERBERATER} beobachter={beobachter} />);
        fireEvent.click(screen.getByRole('button', { name: 'Keine' }));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ mitarbeiterIds: [] }));
        expect(screen.getByRole('group', { name: /0 von 2 gewählt/ })).toBeInTheDocument();
        fireEvent.click(haken('Erika Musterfrau'));
        fireEvent.click(haken('Max Mustermann'));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ mitarbeiterIds: [1, 2] }));
        fireEvent.click(haken('Max Mustermann'));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ mitarbeiterIds: [2] }));
        fireEvent.click(screen.getByRole('button', { name: 'Alle' }));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ mitarbeiterIds: [1, 2] }));
    });

    it('schaltet einen Schalter aus', () => {
        const beobachter = vi.fn();
        render(<Testrahmen definition={STEUERBERATER} beobachter={beobachter} />);
        fireEvent.click(screen.getByRole('switch', { name: 'Resturlaub zeigen' }));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ schalter: { mitResturlaub: false, mitStundenkonto: true } }));
    });

    it('wählt Monat und Jahr über die eigene Auswahlliste', () => {
        const beobachter = vi.fn();
        render(<Testrahmen definition={STEUERBERATER} beobachter={beobachter} />);
        fireEvent.click(screen.getByRole('combobox', { name: 'Monat' }));
        fireEvent.click(screen.getByRole('option', { name: 'Mai' }));
        fireEvent.click(screen.getByRole('combobox', { name: 'Jahr' }));
        fireEvent.click(screen.getByRole('option', { name: '2025' }));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ monat: 5, jahr: 2025 }));
    });
});

describe('BerichtFilter: Mitarbeiter-Bericht', () => {
    it('wählt niemanden vor und zeigt die Unterschrift-Erklärung', () => {
        render(<Testrahmen definition={MITARBEITER_BERICHT} />);
        expect(haken('Max Mustermann').checked).toBe(false);
        expect(screen.getByRole('switch', { name: 'Tagesliste' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.getByRole('switch', { name: 'Unterschriftszeile' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Unterschriftszeile' }));
        const info = screen.getByRole('dialog', { name: 'Unterschriftszeile' });
        expect(within(info).getByText(/Zeile für Datum und Unterschrift/)).toBeInTheDocument();
        expect(within(info).getByText('So kann der Mitarbeiter seine Stunden schriftlich bestätigen.')).toBeInTheDocument();
    });

    it('wechselt den Zeitraum zwischen Monat, ganzem Jahr und von–bis', () => {
        const beobachter = vi.fn();
        render(<Testrahmen definition={MITARBEITER_BERICHT} beobachter={beobachter} />);
        expect(screen.getByRole('combobox', { name: 'Monat' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('radio', { name: 'Ganzes Jahr' }));
        expect(screen.queryByRole('combobox', { name: 'Monat' })).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Jahr' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('radio', { name: 'Von–bis' }));
        expect(beobachter).toHaveBeenLastCalledWith(expect.objectContaining({ zeitraumArt: 'bereich', von: '2026-10-01', bis: '2026-10-31' }));
        expect(screen.getByText('Höchstens 366 Tage, also etwa ein Jahr.')).toBeInTheDocument();
        expect(screen.getByLabelText('Von')).toBeInTheDocument();
        expect(screen.getByLabelText('Bis')).toBeInTheDocument();
    });

    it('zeigt Laden, Fehler und leere Liste unterschiedlich', () => {
        const { unmount } = render(<Testrahmen definition={MITARBEITER_BERICHT} laedt />);
        expect(screen.getByText(/Mitarbeiter werden geladen/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Alle' })).toBeDisabled();
        unmount();
        const zweiter = render(<Testrahmen definition={MITARBEITER_BERICHT} fehler="Die Mitarbeiter konnten nicht geladen werden." mitarbeiter={[]} />);
        expect(screen.getByRole('alert')).toHaveTextContent('Die Mitarbeiter konnten nicht geladen werden.');
        zweiter.unmount();
        render(<Testrahmen definition={MITARBEITER_BERICHT} mitarbeiter={[]} />);
        expect(screen.getByText(/noch keine Mitarbeiter mit Zeitkonto/)).toBeInTheDocument();
    });
});
