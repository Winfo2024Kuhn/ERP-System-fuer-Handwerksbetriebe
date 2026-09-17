/**
 * Vitest-Suite fuer TextBlock – Schwerpunkt: WYSIWYG-Metriken des Freitext-Editors.
 *
 * Hintergrund: Ein Textbaustein laeuft im PDF ueber die volle Contentbreite. Der
 * Editor muss ihn im selben Mass setzen, sonst bricht der Text im Ausdruck an
 * anderer Stelle um als in der Bearbeitung.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent, screen } from '@testing-library/react';
import { TextBlock } from './TextBlock';
import type { DocBlock } from './types';

vi.mock('../TiptapEditor', () => ({
    TiptapEditor: ({ onChange, verlaufsModus }: { onChange: (v: string, a: string) => void; verlaufsModus?: boolean }) => (
        <button data-testid="tiptap" data-verlaufsmodus={String(!!verlaufsModus)}
                onClick={() => onChange('<p>neu</p>', 'sonstiges')} />
    ),
}));

const block: DocBlock = {
    id: 't1',
    type: 'TEXT',
    content: '<p>Vielen Dank fuer Ihre Anfrage.</p>',
};

const props = {
    block,
    isLocked: false,
    isActive: false,
    editorRefs: { current: {} } as never,
    onEditorReady: vi.fn(),
    onUpdate: vi.fn(),
    onRemove: vi.fn(),
    onFocus: vi.fn(),
    onEditorFocus: vi.fn(),
    prepareContent: (text: string) => text,
    serializeContent: (html: string) => html,
};

describe('TextBlock WYSIWYG-Metriken', () => {
    it('rendert den Freitext im Mass der vollen PDF-Contentbreite', () => {
        // Die Klasse traegt Schriftgroesse, Zeilen-/Absatzabstand und Breite des PDF.
        const { container } = render(<TextBlock {...props} />);

        expect(container.querySelector('.doc-pdf-metrics--voll')).not.toBeNull();
    });
});

describe('TextBlock Verlaufsmodus', () => {
    // DOM-Vertrag fuer den Dokumentverlauf (Task 4): "Stelle zeigen" springt
    // ueber data-verlauf-feld ins richtige Editor-Feld.
    it('traegt data-verlauf-feld="content" am Tiptap-Wrapper', () => {
        const { container } = render(<TextBlock {...props} />);

        const wrapper = container.querySelector('[data-verlauf-feld="content"]');
        expect(wrapper).not.toBeNull();
        expect(wrapper?.querySelector('[data-testid="tiptap"]')).not.toBeNull();
    });

    it('reicht die von TiptapEditor gemeldete Aenderungsart an onUpdate durch', () => {
        const onUpdate = vi.fn();
        render(<TextBlock {...props} onUpdate={onUpdate} />);

        fireEvent.click(screen.getByTestId('tiptap'));

        expect(onUpdate).toHaveBeenCalledWith('t1', { content: '<p>neu</p>' }, 'sonstiges');
    });

    it('reicht verlaufsModus an TiptapEditor durch', () => {
        render(<TextBlock {...props} verlaufsModus />);

        expect(screen.getByTestId('tiptap')).toHaveAttribute('data-verlaufsmodus', 'true');
    });

    it('setzt ohne die verlaufsModus-Prop keinen Verlaufsmodus am Editor', () => {
        render(<TextBlock {...props} />);

        expect(screen.getByTestId('tiptap')).toHaveAttribute('data-verlaufsmodus', 'false');
    });
});
