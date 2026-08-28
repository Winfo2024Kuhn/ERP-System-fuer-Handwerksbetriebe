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
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
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
