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
import { render } from '@testing-library/react';
import { TextBlock } from './TextBlock';
import type { DocBlock } from './types';

vi.mock('../TiptapEditor', () => ({
    TiptapEditor: () => <div data-testid="tiptap" />,
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
