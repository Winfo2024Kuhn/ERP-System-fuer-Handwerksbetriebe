/**
 * Vitest-Suite fuer PhasenZeitleiste.
 *
 * Prueft das vorgeschriebene Wording ("Lohnfortzahlung durch den Betrieb" statt
 * "Entgeltfortzahlungszeitraum" usw.), die Badge-Farben je Phasentyp, die
 * Zeitraum-Darstellung (offen vs. abgeschlossen) und die Hervorhebung der
 * heute laufenden Phase.
 *
 * DSGVO: ausschliesslich Dummy-Daten, keine echten Namen.
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PhasenZeitleiste } from './PhasenZeitleiste';
import type { Phase } from './phasen';

const phase = (overrides: Partial<Phase> = {}): Phase => ({
    id: 1,
    typ: 'LOHNFORTZAHLUNG',
    label: 'Lohnfortzahlung durch den Betrieb',
    vonDatum: '2026-01-01',
    bisDatum: '2026-01-28',
    stundenProTag: null,
    ...overrides,
});

describe('PhasenZeitleiste', () => {
    it('zeigt fuer jeden Phasentyp das vorgeschriebene Wording und die passende Badge-Farbe', () => {
        render(
            <PhasenZeitleiste
                phasen={[
                    phase({ id: 1, typ: 'LOHNFORTZAHLUNG' }),
                    phase({ id: 2, typ: 'KRANKENGELD', label: 'Krankengeld der Krankenkasse', vonDatum: '2026-01-29', bisDatum: '2026-02-25' }),
                    phase({ id: 3, typ: 'WIEDEREINGLIEDERUNG', label: 'Wiedereingliederung', vonDatum: '2026-02-26', bisDatum: null, stundenProTag: 2 }),
                ]}
                heute="2026-01-05"
            />,
        );

        const lohn = screen.getByText('Lohnfortzahlung durch den Betrieb');
        expect(lohn).toHaveClass('bg-amber-100', 'text-amber-800');

        const krankengeld = screen.getByText('Krankengeld der Krankenkasse');
        expect(krankengeld).toHaveClass('bg-blue-100', 'text-blue-800');

        const wiedereingliederung = screen.getByText('Wiedereingliederung');
        expect(wiedereingliederung).toHaveClass('bg-teal-100', 'text-teal-800');

        // Verboten laut Vorgabe: buchhalterisches Fachchinesisch.
        expect(screen.queryByText(/Entgeltfortzahlungszeitraum/)).not.toBeInTheDocument();
        expect(screen.queryByText(/AU-Zeitraum/)).not.toBeInTheDocument();
        expect(screen.queryByText(/^Phase 1$/)).not.toBeInTheDocument();
    });

    it('zeigt einen abgeschlossenen Zeitraum mit Start- und Enddatum, einen offenen mit "ab"', () => {
        render(
            <PhasenZeitleiste
                phasen={[
                    phase({ id: 1, typ: 'LOHNFORTZAHLUNG', vonDatum: '2026-01-01', bisDatum: '2026-01-28' }),
                    phase({ id: 2, typ: 'KRANKENGELD', vonDatum: '2026-01-29', bisDatum: null }),
                ]}
                heute="2026-02-01"
            />,
        );

        expect(screen.getByText('01.01.2026 – 28.01.2026')).toBeInTheDocument();
        expect(screen.getByText('ab 29.01.2026')).toBeInTheDocument();
    });

    it('zeigt bei einer Wiedereingliederungs-Phase die Stunden pro Tag', () => {
        render(
            <PhasenZeitleiste
                phasen={[phase({ id: 1, typ: 'WIEDEREINGLIEDERUNG', vonDatum: '2026-04-01', bisDatum: null, stundenProTag: 2 })]}
                heute="2026-04-01"
            />,
        );

        expect(screen.getByText('2 Std. pro Tag')).toBeInTheDocument();
    });

    // Nachbesserung Abschnitt 5, Befund 1 (BLOCKER): halbe Stunden muessen mit
    // deutschem Komma erscheinen ("4,5 Std."), nicht mit englischem Punkt.
    it('zeigt halbe Stunden pro Tag mit deutschem Komma statt englischem Punkt', () => {
        render(
            <PhasenZeitleiste
                phasen={[phase({ id: 1, typ: 'WIEDEREINGLIEDERUNG', vonDatum: '2026-04-01', bisDatum: null, stundenProTag: 4.5 })]}
                heute="2026-04-01"
            />,
        );

        expect(screen.getByText('4,5 Std. pro Tag')).toBeInTheDocument();
        expect(screen.queryByText('4.5 Std. pro Tag')).not.toBeInTheDocument();
    });

    it('hebt nur die heute tatsaechlich laufende Phase hervor', () => {
        render(
            <PhasenZeitleiste
                phasen={[
                    phase({ id: 1, typ: 'LOHNFORTZAHLUNG', vonDatum: '2026-01-01', bisDatum: '2026-01-28' }),
                    phase({ id: 2, typ: 'KRANKENGELD', vonDatum: '2026-01-29', bisDatum: null }),
                ]}
                heute="2026-02-05"
            />,
        );

        expect(screen.getByTestId('phase-1')).not.toHaveClass('ring-2');
        expect(screen.getByTestId('phase-2')).toHaveClass('ring-2', 'ring-rose-400');
    });

    it('zeigt einen Hinweis statt einer leeren Flaeche, wenn keine Phasen hinterlegt sind', () => {
        render(<PhasenZeitleiste phasen={[]} />);
        expect(screen.getByText(/keine Phasen/i)).toBeInTheDocument();
    });
});
