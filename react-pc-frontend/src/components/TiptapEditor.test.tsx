/**
 * Vitest-Suite fuer TiptapEditor – Schwerpunkt: der neue, zuschaltbare
 * Verlaufsmodus (`verlaufsModus`).
 *
 * Laeuft mit einem echten Tiptap-Editor in jsdom (kein Mock): nur so laesst
 * sich pruefen, dass der Editor im Verlaufsmodus wirklich keinen eigenen
 * ProseMirror-Verlauf mehr hat und dass die Pfeil-Knoepfe dafuer verschwinden.
 *
 * Regressionsschutz: Der Standardmodus (verlaufsModus nicht gesetzt) muss sich
 * exakt wie heute verhalten, weil rund zehn andere Seiten TiptapEditor ohne
 * diese Prop benutzen (siehe erster Test).
 *
 * DSGVO: keine Personendaten, nur Beispieltext ("Hallo").
 */
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import type { Editor } from '@tiptap/core';
import { TiptapEditor, TiptapToolbar } from './TiptapEditor';

/** Tiptap haengt die Editor-Instanz selbst ans DOM-Element (Editor.ts, "editor"-Property). */
function editorAus(container: HTMLElement): Editor {
    const pm = container.querySelector('.ProseMirror') as (HTMLElement & { editor: Editor }) | null;
    if (!pm) throw new Error('.ProseMirror nicht gefunden - Editor nicht gemountet?');
    return pm.editor;
}

describe('TiptapEditor Standardmodus (Regressionsschutz)', () => {
    it('meldet eine von aussen geaenderte value weiterhin ueber onChange', () => {
        const onChange = vi.fn();
        const { rerender } = render(<TiptapEditor value="<p>Alt</p>" onChange={onChange} />);
        // Ohne das hier waere die Zusicherung unten schon durch den Mount-Aufruf
        // erfuellt (setEditable-Effekt feuert beim Mounten ein eigenes onChange,
        // siehe Verlaufsmodus-Test unten) -- der Test praeufte dann gar nicht
        // mehr, ob der SYNC-Effekt selbst noch onChange meldet.
        onChange.mockClear();

        rerender(<TiptapEditor value="<p>Neu</p>" onChange={onChange} />);

        expect(onChange).toHaveBeenCalled();
    });

    it('zeigt bei sichtbarer Toolbar die Rueckgaengig/Wiederholen-Pfeile', () => {
        const { getByTitle } = render(
            <TiptapEditor value="<p>Hallo</p>" onChange={vi.fn()} hideToolbar={false} />,
        );

        expect(getByTitle('Rückgängig (Ctrl+Z)')).toBeInTheDocument();
    });
});

describe('TiptapEditor Verlaufsmodus', () => {
    it('meldet eine von aussen geaenderte value NICHT ueber onChange', () => {
        const onChange = vi.fn();
        const { rerender } = render(
            <TiptapEditor value="<p>Alt</p>" onChange={onChange} verlaufsModus />,
        );
        // Vorbestehende Eigenheit (nicht Teil dieses Tasks): editor.setEditable(!readOnly)
        // in TiptapEditor.tsx meldet beim Mount unabhaengig vom Verlaufsmodus einmal
        // ueber onChange (emitUpdate default true, leere Transaktion). Hier interessiert
        // nur, ob die value-Aenderung selbst gemeldet wird.
        onChange.mockClear();

        rerender(<TiptapEditor value="<p>Neu</p>" onChange={onChange} verlaufsModus />);

        expect(onChange).not.toHaveBeenCalled();
    });

    it('hat keinen eigenen Tiptap-Verlauf mehr (undoRedo:false)', () => {
        const { container } = render(
            <TiptapEditor value="<p>Hallo</p>" onChange={vi.fn()} verlaufsModus />,
        );

        const editor = editorAus(container);

        expect(typeof editor.commands.undo).toBe('undefined');
    });

    it('verbirgt die Pfeil-Knoepfe der eingebauten Toolbar ohne abzustuerzen', () => {
        expect(() => {
            const { queryByTitle } = render(
                <TiptapEditor value="<p>Hallo</p>" onChange={vi.fn()} hideToolbar={false} verlaufsModus />,
            );
            expect(queryByTitle('Rückgängig (Ctrl+Z)')).not.toBeInTheDocument();
            expect(queryByTitle('Wiederholen (Ctrl+Y)')).not.toBeInTheDocument();
        }).not.toThrow();
    });

    it('meldet die Aenderungsart ueber onChange: tippen bei Text, sonstiges bei Formatierung', () => {
        const onChange = vi.fn();
        const { container } = render(
            <TiptapEditor value="<p>Hallo</p>" onChange={onChange} verlaufsModus />,
        );
        const editor = editorAus(container);

        editor.commands.insertContent('x');
        expect(onChange).toHaveBeenLastCalledWith(editor.getHTML(), 'tippen');

        editor.chain().selectAll().toggleBold().run();
        expect(onChange).toHaveBeenLastCalledWith(editor.getHTML(), 'sonstiges');
    });
});

describe('TiptapToolbar (eigenstaendig exportierte Leiste)', () => {
    it('rendert ohne Pfeile und ohne Absturz, wenn der Editor keinen eigenen Verlauf hat', () => {
        let erfassterEditor: Editor | null = null;
        render(
            <TiptapEditor
                value="<p>Hallo</p>"
                onChange={vi.fn()}
                verlaufsModus
                hideToolbar
                onEditorReady={(ed) => { erfassterEditor = ed as Editor; }}
            />,
        );
        expect(erfassterEditor).not.toBeNull();

        expect(() => {
            const { queryByTitle } = render(<TiptapToolbar editor={erfassterEditor} />);
            expect(queryByTitle('Rükgängig (Ctrl+Z)')).not.toBeInTheDocument();
            expect(queryByTitle('Wiederholen (Ctrl+Y)')).not.toBeInTheDocument();
        }).not.toThrow();
    });
});
