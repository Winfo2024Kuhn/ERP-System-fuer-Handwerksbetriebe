import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RechnungstypInfo } from './RechnungstypInfo';

const oeffnen = () => fireEvent.click(screen.getByRole('button', { name: 'Mehr zu: Welche Rechnung passt?' }));

describe('RechnungstypInfo', () => {
    it('erklärt alle Rechnungsarten samt einfacher Rechnung, wenn sie angeboten wird', () => {
        render(<RechnungstypInfo mitEinfacherRechnung />);
        oeffnen();

        const popover = screen.getByRole('dialog', { name: 'Welche Rechnung passt?' });
        expect(popover).toHaveTextContent('Einfache Rechnung');
        expect(popover).toHaveTextContent('danach kommt keine Rechnung mehr');
        expect(popover).toHaveTextContent('Teilrechnung');
        expect(popover).toHaveTextContent('Abschlagsrechnung');
        expect(popover).toHaveTextContent('Schlussrechnung');
        expect(popover).toHaveTextContent('zieht Berechnetes ab');
    });

    it('lässt die einfache Rechnung weg, wo sie nicht zur Auswahl steht', () => {
        render(<RechnungstypInfo mitEinfacherRechnung={false} />);
        oeffnen();

        const popover = screen.getByRole('dialog', { name: 'Welche Rechnung passt?' });
        expect(popover).not.toHaveTextContent('Einfache Rechnung');
        expect(popover).toHaveTextContent('Teilrechnung');
        expect(popover).toHaveTextContent('zieht Berechnetes ab');
    });
});
