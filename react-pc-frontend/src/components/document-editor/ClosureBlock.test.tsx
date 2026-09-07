/**
 * Vitest-Suite fuer ClosureBlock – Schwerpunkt: Ausweis des Pauschalrabatts.
 *
 * Hintergrund (Regressions-Bug, 2026-08-28): Der Dokument-Pauschalrabatt lag nur
 * als Metadatum in positionenJson. Der Abschluss zeigte deshalb die Summe VOR
 * Rabatt, waehrend Rechnung und PDF den rabattierten Betrag auswiesen. Der
 * Abschluss muss den Betrag zeigen, der auch berechnet und versendet wird.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ClosureBlock } from './ClosureBlock';
import type { ClosureSummary } from './helpers';

const summary: ClosureSummary = {
    sections: [],
    sonstigeTotal: 3412.2,
    hasSonstige: false,
    gesamtNetto: 3412.2,
};

const summaryMitAbschnitten: ClosureSummary = {
    sections: [{ label: 'Stahlbau', total: 1000, position: '1' }],
    sonstigeTotal: 0,
    hasSonstige: false,
    gesamtNetto: 1000,
};

describe('ClosureBlock – Pauschalrabatt', () => {
    it('weist Zwischensumme, Rabattzeile und Endsumme aus', () => {
        render(<ClosureBlock summary={summary} globalRabatt={3} />);

        expect(screen.getByText('Zwischensumme Netto')).toBeInTheDocument();
        expect(screen.getByText(/Rabatt 3,00 %/)).toBeInTheDocument();
        expect(screen.getByText('Gesamtsumme Netto nach Rabatt')).toBeInTheDocument();
        // 3412,20 − 3% = 3309,83
        expect(screen.getByText(/3\.309,83/)).toBeInTheDocument();
        expect(screen.getByText(/−\s*102,37/)).toBeInTheDocument();
    });

    it('zeigt ohne Rabatt weiterhin nur die schlichte Gesamtsumme', () => {
        render(<ClosureBlock summary={summary} />);

        expect(screen.getByText('Gesamtsumme Netto')).toBeInTheDocument();
        expect(screen.queryByText('Zwischensumme Netto')).not.toBeInTheDocument();
        expect(screen.queryByText(/^Rabatt /)).not.toBeInTheDocument();
        expect(screen.getByText(/3\.412,20/)).toBeInTheDocument();
    });

    it('weist den Rabatt auch bei aufgegliederten Bauabschnitten aus', () => {
        render(<ClosureBlock summary={summaryMitAbschnitten} globalRabatt={10} />);

        expect(screen.getByText('Stahlbau')).toBeInTheDocument();
        expect(screen.getByText('Gesamtsumme Netto nach Rabatt')).toBeInTheDocument();
        expect(screen.getByText(/900,00/)).toBeInTheDocument();
    });

    it('ignoriert unplausible Rabattwerte', () => {
        // Negativ = kein Rabatt: die Zeile darf nicht auftauchen.
        render(<ClosureBlock summary={summary} globalRabatt={-5} />);

        expect(screen.getByText('Gesamtsumme Netto')).toBeInTheDocument();
        expect(screen.queryByText('Zwischensumme Netto')).not.toBeInTheDocument();
    });

    it('blendet den Rabatt bei leerem Dokument nicht ein', () => {
        const leer: ClosureSummary = { sections: [], sonstigeTotal: 0, hasSonstige: false, gesamtNetto: 0 };
        render(<ClosureBlock summary={leer} globalRabatt={5} />);

        expect(screen.getByText('Keine Leistungen vorhanden')).toBeInTheDocument();
        expect(screen.queryByText('Zwischensumme Netto')).not.toBeInTheDocument();
    });
});

/**
 * Der Abrechnungsstand einer Schlussrechnung im Editor.
 *
 * Kern der Regel: Eine Schlussrechnung rechnet die Leistungen ab, die
 * tatsaechlich in ihr stehen — nicht den rechnerischen Rest zur Auftragssumme.
 * Der Unterschied zur Auftragsbestaetigung steht als eigene Zeile darunter, und
 * der Auftrag gilt danach per Definition als zu 100 % abgerechnet.
 */
describe('ClosureBlock – Abrechnungsstand der Schlussrechnung', () => {
    /** Positionssumme der aktuellen Rechnung, ohne Bauabschnitte. */
    const summaryFuer = (gesamtNetto: number): ClosureSummary => ({
        gesamtNetto,
        sections: [],
        sonstigeTotal: gesamtNetto,
        hasSonstige: true,
    });

    /**
     * Auftrag über 10.000 € netto, davon 4.000 € als Teilrechnung gestellt.
     * `positionenNetto` ist die Summe der Positionen, die in der Schlussrechnung
     * stehen geblieben sind.
     */
    const schlussrechnung = (positionenNetto: number, extra = {}) => (
        <ClosureBlock
            summary={summaryFuer(positionenNetto)}
            dokumentTyp="SCHLUSSRECHNUNG"
            bereitsAbgerechnetDurchAndere={4000}
            basisdokumentBetragNetto={10000}
            {...extra}
        />
    );

    it('fordert nur die tatsaechlich enthaltenen Leistungen abzueglich der Teilrechnung', () => {
        // 8.000 € angefallen (Gerüst entfiel), 4.000 € gestellt -> 4.000 € netto offen.
        render(schlussrechnung(8000));

        // Steht mehrfach da: einmal als "diese Schlussrechnung", einmal als Zahlbetrag.
        expect(screen.getAllByText(/netto 4\.000,00 €/).length).toBeGreaterThan(0);
        // Der alte Rechenweg (10.000 - 4.000) haette 6.000 € gefordert.
        expect(screen.queryByText(/netto 6\.000,00 €/)).toBeNull();
    });

    it('weist die entfallene Differenz als eigene Zeile mit Leistungsnamen aus', () => {
        render(schlussrechnung(8000, { differenzHinweis: 'Gerüststellung' }));

        expect(screen.getByText('Nicht angefallen')).toBeTruthy();
        expect(screen.getByText('Gerüststellung')).toBeTruthy();
    });

    it('weist unvorhergesehene Zusatzleistungen als Zuschlag aus', () => {
        // 11.000 € geleistet bei 10.000 € Auftrag.
        render(schlussrechnung(11000, { differenzHinweis: 'Kernbohrung Kellerwand' }));

        expect(screen.getByText('Zusätzliche Leistungen')).toBeTruthy();
        expect(screen.getByText('Kernbohrung Kellerwand')).toBeTruthy();
    });

    it('erklaert die Differenzzeile auch ohne aufloesbaren Positionsvergleich', () => {
        render(schlussrechnung(8000));

        expect(screen.getByText('Nicht angefallen')).toBeTruthy();
        expect(screen.getByText('Unterschied zur Auftragsbestätigung')).toBeTruthy();
    });

    it('zeigt ohne Abweichung gar keine Differenzzeile', () => {
        render(schlussrechnung(10000));

        expect(screen.queryByText('Nicht angefallen')).toBeNull();
        expect(screen.queryByText('Zusätzliche Leistungen')).toBeNull();
    });

    it('meldet den Auftrag als zu 100 % abgerechnet, auch wenn weniger berechnet wurde', () => {
        render(schlussrechnung(8000));

        expect(screen.getByText(/sind 100 % des Auftrags abgerechnet/)).toBeTruthy();
    });

    it('laesst nach der Schlussrechnung nichts offen', () => {
        render(schlussrechnung(8000));

        expect(screen.getByText('Noch offen nach dieser Rechnung')).toBeTruthy();
        expect(screen.getByText(/netto 0,00 €/)).toBeTruthy();
    });

    it('rechnet bei einer Teilrechnung weiterhin anteilig', () => {
        // Gegenprobe: nur die Schlussrechnung bekommt die 100-%-Regel.
        render(
            <ClosureBlock
                summary={summaryFuer(2500)}
                dokumentTyp="TEILRECHNUNG"
                bereitsAbgerechnetDurchAndere={0}
                basisdokumentBetragNetto={10000}
            />
        );

        expect(screen.getByText(/sind 25 % des Auftrags abgerechnet/)).toBeTruthy();
    });

    describe('Fortschrittsbalken auf der Rechnung', () => {
        it('ist standardmaessig sichtbar', () => {
            render(schlussrechnung(8000));

            expect(screen.getByText(/% des Auftrags abgerechnet/)).toBeTruthy();
        });

        it('verschwindet, wenn er abgeschaltet ist', () => {
            render(schlussrechnung(8000, { balkenAnzeigen: false }));

            expect(screen.queryByText(/% des Auftrags abgerechnet/)).toBeNull();
        });

        it('laesst die Pflichtangaben stehen, wenn er abgeschaltet ist', () => {
            // §14 Abs. 5 UStG verlangt den Ausweis der bereits abgerechneten Betraege.
            render(schlussrechnung(8000, { balkenAnzeigen: false, differenzHinweis: 'Gerüststellung' }));

            expect(screen.getByText('Auftragssumme')).toBeTruthy();
            expect(screen.getByText('Nicht angefallen')).toBeTruthy();
            expect(screen.getByText('Noch offen nach dieser Rechnung')).toBeTruthy();
        });

        it('bietet ohne Umschalt-Callback kein Bedienelement an (gesperrte Rechnung)', () => {
            render(schlussrechnung(8000));

            expect(screen.queryByRole('checkbox')).toBeNull();
        });

        it('meldet das Umschalten an den Editor, damit es gespeichert wird', () => {
            const onBalkenAnzeigenChange = vi.fn();
            render(schlussrechnung(8000, { onBalkenAnzeigenChange }));

            fireEvent.click(screen.getByRole('checkbox'));

            expect(onBalkenAnzeigenChange).toHaveBeenCalledWith(false);
        });
    });
});
