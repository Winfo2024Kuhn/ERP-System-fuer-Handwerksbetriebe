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

/**
 * Ersatz fuer den echten Editor. Er kann beides, was die Tests unten brauchen:
 * eine Aenderung melden (Verlaufsmodus) und den aufbereiteten Inhalt ausgeben,
 * damit der Zahlungsziel-Chip ueberhaupt im DOM steht — im echten Editor
 * rendert ihn ProseMirror.
 */
vi.mock('../TiptapEditor', () => ({
    TiptapEditor: ({ value, onChange, verlaufsModus }: { value?: string; onChange: (v: string, a: string) => void; verlaufsModus?: boolean }) => (
        <button data-testid="tiptap" data-verlaufsmodus={String(!!verlaufsModus)}
                onClick={() => onChange('<p>neu</p>', 'sonstiges')}
                dangerouslySetInnerHTML={{ __html: value ?? '' }} />
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

describe('TextBlock Zahlungsziel-Chip', () => {
    // Der Chip oeffnet das Bearbeitungs-Popover. Erkannt wird er beim
    // mousedown, weil der erste Klick in einen noch nicht aktiven Textbaustein
    // sonst beim Fokussieren des Editors verloren geht.
    const chipBlock: DocBlock = {
        id: 't2',
        type: 'TEXT',
        content: '<p>Zahlbar innerhalb von <span data-zahlungsziel-chip="tage">14</span> Tagen.</p>',
    };

    function renderMitChip(ueberschreibungen: Partial<typeof props> = {}) {
        const onZahlungszielChipClick = vi.fn();
        render(
            <TextBlock
                {...props}
                block={chipBlock}
                onZahlungszielChipClick={onZahlungszielChipClick}
                {...ueberschreibungen}
            />
        );
        return { onZahlungszielChipClick };
    }

    it('meldet einen Klick auf den Chip mit dessen Position', () => {
        const { onZahlungszielChipClick } = renderMitChip();

        fireEvent.mouseDown(document.querySelector('[data-zahlungsziel-chip]')!, { button: 0 });

        expect(onZahlungszielChipClick).toHaveBeenCalledTimes(1);
        expect(onZahlungszielChipClick.mock.calls[0][0]).toHaveProperty('bottom');
    });

    it('meldet nichts bei einem Klick auf normalen Text', () => {
        // Sonst ginge der Cursor im Textbaustein verloren.
        const { onZahlungszielChipClick } = renderMitChip();

        fireEvent.mouseDown(screen.getByTestId('tiptap'), { button: 0 });

        expect(onZahlungszielChipClick).not.toHaveBeenCalled();
    });

    it('meldet nichts bei einem gesperrten Dokument', () => {
        const { onZahlungszielChipClick } = renderMitChip({ isLocked: true });

        fireEvent.mouseDown(document.querySelector('[data-zahlungsziel-chip]')!, { button: 0 });

        expect(onZahlungszielChipClick).not.toHaveBeenCalled();
    });

    it('meldet nichts bei einem Klick mit der rechten Maustaste', () => {
        // Sonst stuenden Popover und Kontextmenue gleichzeitig offen.
        const { onZahlungszielChipClick } = renderMitChip();

        fireEvent.mouseDown(document.querySelector('[data-zahlungsziel-chip]')!, { button: 2 });

        expect(onZahlungszielChipClick).not.toHaveBeenCalled();
    });
});
