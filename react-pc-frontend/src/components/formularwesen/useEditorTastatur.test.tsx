import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, renderHook } from '@testing-library/react';
import { PFEIL_SCHRITT_GROSS, useEditorTastatur, type EditorTastaturAktionen } from './useEditorTastatur';

const aktionen = (teil: Partial<EditorTastaturAktionen> = {}): EditorTastaturAktionen => ({
    hatAuswahl: true,
    verschieben: vi.fn(),
    loeschen: vi.fn(),
    duplizieren: vi.fn(),
    auswahlAufheben: vi.fn(),
    rueckgaengig: vi.fn(),
    wiederholen: vi.fn(),
    ...teil,
});

const taste = (key: string, extra: Partial<KeyboardEventInit> = {}, ziel: EventTarget = window) => fireEvent.keyDown(ziel, { key, ...extra });

describe('useEditorTastatur', () => {
    afterEach(() => { document.body.innerHTML = ''; });

    it('Pfeiltasten verschieben, mit Umschalt um 10', () => {
        const a = aktionen();
        renderHook(() => useEditorTastatur(a));
        taste('ArrowLeft');
        taste('ArrowRight');
        taste('ArrowUp', { shiftKey: true });
        taste('ArrowDown');
        expect(a.verschieben).toHaveBeenNthCalledWith(1, -1, 0);
        expect(a.verschieben).toHaveBeenNthCalledWith(2, 1, 0);
        expect(a.verschieben).toHaveBeenNthCalledWith(3, 0, -PFEIL_SCHRITT_GROSS);
        expect(a.verschieben).toHaveBeenNthCalledWith(4, 0, 1);
    });

    it('Entf und Rücktaste löschen, Escape hebt die Auswahl auf, Strg/Cmd+D dupliziert', () => {
        const a = aktionen();
        renderHook(() => useEditorTastatur(a));
        taste('Delete');
        taste('Backspace');
        taste('Escape');
        taste('d', { ctrlKey: true });
        taste('D', { metaKey: true });
        expect(a.loeschen).toHaveBeenCalledTimes(2);
        expect(a.auswahlAufheben).toHaveBeenCalledTimes(1);
        expect(a.duplizieren).toHaveBeenCalledTimes(2);
    });

    it('Strg+Z, Strg+Umschalt+Z und Strg+Y – auch ohne Auswahl', () => {
        const a = aktionen({ hatAuswahl: false });
        renderHook(() => useEditorTastatur(a));
        taste('z', { ctrlKey: true });
        taste('Z', { metaKey: true, shiftKey: true });
        taste('y', { ctrlKey: true });
        taste('ArrowLeft');
        taste('Delete');
        taste('d', { ctrlKey: true });
        expect(a.rueckgaengig).toHaveBeenCalledTimes(1);
        expect(a.wiederholen).toHaveBeenCalledTimes(2);
        expect(a.verschieben).not.toHaveBeenCalled();
        expect(a.loeschen).not.toHaveBeenCalled();
        expect(a.duplizieren).not.toHaveBeenCalled();
    });

    it('lässt Eingabefelder, offene Dialoge und andere Kombinationen in Ruhe', () => {
        const a = aktionen();
        renderHook(() => useEditorTastatur(a));
        const { container } = render(<div><input aria-label="Feld" /><textarea aria-label="Text" /><div contentEditable /></div>);
        taste('Delete', {}, container.querySelector('input')!);
        const editierbar = container.querySelector('[contenteditable]') as HTMLElement;
        Object.defineProperty(editierbar, 'isContentEditable', { value: true });
        taste('Delete', {}, editierbar);
        taste('z', { ctrlKey: true }, container.querySelector('textarea')!);
        taste('ArrowLeft', { altKey: true });
        taste('x', { ctrlKey: true });
        taste('a');
        const dialog = document.createElement('div');
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');
        document.body.appendChild(dialog);
        taste('Delete');
        expect(a.loeschen).not.toHaveBeenCalled();
        expect(a.rueckgaengig).not.toHaveBeenCalled();
        expect(a.verschieben).not.toHaveBeenCalled();
    });

    it('reagiert nicht, wenn abgeschaltet oder schon behandelt', () => {
        const a = aktionen();
        const { rerender } = renderHook(({ an }) => useEditorTastatur(a, an), { initialProps: { an: false } });
        taste('Delete');
        rerender({ an: true });
        const ereignis = new KeyboardEvent('keydown', { key: 'Delete', cancelable: true });
        ereignis.preventDefault();
        window.dispatchEvent(ereignis);
        expect(a.loeschen).not.toHaveBeenCalled();
        taste('Delete');
        expect(a.loeschen).toHaveBeenCalledTimes(1);
    });
});
