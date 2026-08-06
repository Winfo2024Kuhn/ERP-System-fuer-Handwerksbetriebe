/**
 * Vitest-Suite fuer SectionHeaderBlock – Schwerpunkt: Startzustand.
 *
 * Ein Bauabschnitt startet zugeklappt, damit ein Dokument mit mehreren
 * Abschnitten beim Oeffnen auf einen Bildschirm passt. Der Zustand ist reine
 * Ansichtssache und wird bewusst nicht persistiert.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SectionHeaderBlock } from './SectionHeaderBlock';
import type { DocBlock } from './types';

vi.mock('@dnd-kit/core', () => ({ useDroppable: () => ({ setNodeRef: vi.fn(), isOver: false }) }));
vi.mock('./ServiceBlock', () => ({ ServiceBlock: () => <div data-testid="service" /> }));

const block: DocBlock = {
    id: 'sec', type: 'SECTION_HEADER', sectionLabel: 'Stahlbau',
    children: [{ id: 'k1', type: 'SERVICE', title: 'Träger', quantity: 1, unit: 'Stk', price: 500 }],
};

const props = {
    block, isLocked: false, isActive: false, activeEditorId: null,
    editorRefs: { current: {} } as never,
    onUpdate: vi.fn(), onUpdateChild: vi.fn(), onRemove: vi.fn(), onRemoveChild: vi.fn(),
    onEjectChild: vi.fn(), onChildModusWechsel: vi.fn(), onAlternativOeffnen: vi.fn(),
    onFocus: vi.fn(), onEditorFocus: vi.fn(),
    getPositionString: () => '1.1', sectionPosition: '1',
};

describe('SectionHeaderBlock', () => {
    it('ist beim Oeffnen zugeklappt und zeigt nur die Zusammenfassung', () => {
        render(<SectionHeaderBlock {...props} />);

        expect(screen.getByText('Stahlbau')).toBeInTheDocument();
        expect(screen.getByText(/1 Leistung/)).toBeInTheDocument();
        expect(screen.queryByTestId('service')).not.toBeInTheDocument();
    });

    it('zeigt die Kinder nach dem Aufklappen', () => {
        render(<SectionHeaderBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByTestId('service')).toBeInTheDocument();
    });
});
