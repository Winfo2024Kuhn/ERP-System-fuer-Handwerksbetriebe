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
vi.mock('./ServiceBlock', () => ({
    ServiceBlock: ({ onUpdate, verlaufsModus }: { onUpdate: (id: string, updates: object, art?: string) => void; verlaufsModus?: boolean }) => (
        <button data-testid="service" data-verlaufsmodus={String(!!verlaufsModus)}
                onClick={() => onUpdate('k1', { title: 'neu' }, 'sonstiges')} />
    ),
}));

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

describe('SectionHeaderBlock Verlaufsmodus (DOM-Vertraege fuer "Stelle zeigen")', () => {
    it('traegt data-block-id des Kindes am Kind-Wrapper', () => {
        const { container } = render(<SectionHeaderBlock {...props} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(container.querySelector('[data-block-id="k1"]')).not.toBeNull();
    });

    it('traegt am Namensfeld data-verlauf-feld="sectionLabel" und data-eigenes-rueckgaengig="true" nach Klick zum Bearbeiten', () => {
        render(<SectionHeaderBlock {...props} />);

        // Das Namensfeld ist erst nach Klick ein <input> - vorher ein reiner Text.
        fireEvent.click(screen.getByText('Stahlbau'));

        const input = screen.getByPlaceholderText('z.B. Rohbauarbeiten, Stahlkonstruktion...');
        expect(input).toHaveAttribute('data-verlauf-feld', 'sectionLabel');
        expect(input).toHaveAttribute('data-eigenes-rueckgaengig', 'true');
    });

    it('reicht verlaufsModus an die Kind-ServiceBlocks durch', () => {
        render(<SectionHeaderBlock {...props} verlaufsModus />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        expect(screen.getByTestId('service')).toHaveAttribute('data-verlaufsmodus', 'true');
    });

    it('reicht die Aenderungsart eines Kindes an onUpdateChild durch', () => {
        const onUpdateChild = vi.fn();
        render(<SectionHeaderBlock {...props} onUpdateChild={onUpdateChild} />);
        fireEvent.click(screen.getByRole('button', { name: /aufklappen/i }));

        fireEvent.click(screen.getByTestId('service'));

        expect(onUpdateChild).toHaveBeenCalledWith('sec', 'k1', { title: 'neu' }, 'sonstiges');
    });
});
