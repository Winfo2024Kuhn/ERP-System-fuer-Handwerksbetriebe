/**
 * Vitest-Suite fuer ServiceBlock – Schwerpunkt: Einklappen und Wahlpositions-Menue.
 *
 * Hintergrund: Eine Leistungs-Karte belegt aufgeklappt rund 250 px. Bei 20
 * Positionen ist die Uebersicht weg, deshalb startet jede Karte zugeklappt.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ServiceBlock } from './ServiceBlock';
import type { DocBlock } from './types';

vi.mock('../TiptapEditor', () => ({
    TiptapEditor: () => <div data-testid="tiptap" />,
}));

const block: DocBlock = {
    id: 'b1', type: 'SERVICE', title: 'Geländer Edelstahl',
    quantity: 12, unit: 'm', price: 103.33,
};

const props = {
    block, positionNumber: '3', isLocked: false, isActive: false,
    editorRefs: { current: {} } as never,
    onEditorReady: vi.fn(), onUpdate: vi.fn(), onRemove: vi.fn(),
    onModusWechsel: vi.fn(), onAlternativOeffnen: vi.fn(),
    onFocus: vi.fn(), onEditorFocus: vi.fn(),
};

describe('ServiceBlock Einklappen', () => {
    it('ist standardmaessig zu und zeigt nur Nummer, Kurztext und Summe', () => {
        render(<ServiceBlock {...props} />);

        expect(screen.getByText('3')).toBeInTheDocument();
        expect(screen.getByDisplayValue('Geländer Edelstahl')).toBeInTheDocument();
        expect(screen.getByText(/1.239,96/)).toBeInTheDocument();
        expect(screen.queryByTestId('tiptap')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Menge')).not.toBeInTheDocument();
    });

    it('zeigt nach Klick auf den Pfeil Beschreibung und Kalkulation', () => {
        render(<ServiceBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByTestId('tiptap')).toBeInTheDocument();
        expect(screen.getByLabelText('Menge')).toBeInTheDocument();
    });
});

describe('ServiceBlock WYSIWYG-Metriken', () => {
    it('rendert die Beschreibung im Mass der PDF-Bezeichnungsspalte', () => {
        // Die Klasse traegt Schriftgroesse, Zeilen-/Absatzabstand und Spaltenbreite des PDF.
        // Ohne sie bricht der Text im Editor an anderer Stelle um als im Ausdruck.
        const { container } = render(<ServiceBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(container.querySelector('.doc-pdf-metrics--spalte')).not.toBeNull();
    });
});

describe('ServiceBlock Wahlpositions-Menue', () => {
    it('laedt eine feste Position mit "Kunde waehlt" zum Umschalten ein', () => {
        render(<ServiceBlock {...props} />);
        expect(screen.getByRole('button', { name: /Kunde wählt/ })).toBeInTheDocument();
    });

    it('nennt eine gruppenlose Wahlposition "Optional"', () => {
        render(<ServiceBlock {...props} block={{ ...block, optional: true }} />);
        expect(screen.getByRole('button', { name: /Optional/ })).toBeInTheDocument();
    });

    it('nennt ein Gruppenmitglied "Alternative"', () => {
        render(<ServiceBlock {...props}
            block={{ ...block, optional: true, alternativGruppe: 'Geländer' }} />);
        expect(screen.getByRole('button', { name: /Alternative/ })).toBeInTheDocument();
    });

    it('schaltet ueber den Menuepunkt "Optional" auf optional um', () => {
        const onModusWechsel = vi.fn();
        render(<ServiceBlock {...props} onModusWechsel={onModusWechsel} />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Optional/ }));

        expect(onModusWechsel).toHaveBeenCalledWith('b1', 'optional');
    });

    it('oeffnet ueber "Alternativ" den Gruppen-Dialog statt direkt umzuschalten', () => {
        const onModusWechsel = vi.fn();
        const onAlternativOeffnen = vi.fn();
        render(<ServiceBlock {...props}
            onModusWechsel={onModusWechsel} onAlternativOeffnen={onAlternativOeffnen} />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Alternativ/ }));

        expect(onAlternativOeffnen).toHaveBeenCalledWith('b1');
        expect(onModusWechsel).not.toHaveBeenCalled();
    });

    it('bietet einer Wahlposition den Weg zurueck auf "Fest beauftragt"', () => {
        const onModusWechsel = vi.fn();
        render(<ServiceBlock {...props}
            block={{ ...block, optional: true }} onModusWechsel={onModusWechsel} />);

        fireEvent.click(screen.getByRole('button', { name: /Optional/ }));
        fireEvent.click(screen.getByRole('menuitemradio', { name: /Fest beauftragt/ }));

        expect(onModusWechsel).toHaveBeenCalledWith('b1', 'fest');
    });

    it('oeffnet im gesperrten Dokument kein Menue', () => {
        render(<ServiceBlock {...props} isLocked />);

        fireEvent.click(screen.getByRole('button', { name: /Kunde wählt/ }));

        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
});
