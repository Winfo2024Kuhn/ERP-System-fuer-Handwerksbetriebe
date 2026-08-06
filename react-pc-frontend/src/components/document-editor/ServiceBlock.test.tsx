/**
 * Vitest-Suite fuer ServiceBlock – Schwerpunkt: Einklappen und Wording.
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
    onToggleOptional: vi.fn(), onFocus: vi.fn(), onEditorFocus: vi.fn(),
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
