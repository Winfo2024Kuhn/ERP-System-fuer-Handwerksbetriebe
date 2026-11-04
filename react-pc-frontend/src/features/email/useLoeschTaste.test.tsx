import { fireEvent, render, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useLoeschTaste } from './useLoeschTaste';

function Flaeche() {
    return <div>
        <input aria-label="Suche" />
        <textarea aria-label="Text" />
        <div contentEditable suppressContentEditableWarning data-testid="editor"><p data-testid="absatz">Hallo</p></div>
        <div role="dialog" aria-label="Fenster"><button type="button">Im Dialog</button></div>
        <div role="menu"><div role="menuitem" tabIndex={-1}>Menüpunkt</div></div>
        <button type="button">Normaler Knopf</button>
    </div>;
}

describe('useLoeschTaste', () => {
    afterEach(() => { document.body.innerHTML = ''; });

    it.each(['Delete', 'Backspace'])('löscht mit %s, wenn der Fokus auf der Seite liegt', (key) => {
        const loeschen = vi.fn();
        renderHook(() => useLoeschTaste(true, loeschen));
        fireEvent.keyDown(document.body, { key });
        expect(loeschen).toHaveBeenCalledTimes(1);
    });

    it('löscht auch, wenn ein normaler Knopf (z. B. die Listenzeile) den Fokus hat', () => {
        const loeschen = vi.fn();
        const { getByText } = render(<Flaeche />);
        renderHook(() => useLoeschTaste(true, loeschen));
        fireEvent.keyDown(getByText('Normaler Knopf'), { key: 'Delete' });
        expect(loeschen).toHaveBeenCalledTimes(1);
    });

    it('löscht nicht beim Tippen in Eingabefeld, Textfeld oder Editor', () => {
        const loeschen = vi.fn();
        const { getByLabelText, getByTestId } = render(<Flaeche />);
        renderHook(() => useLoeschTaste(true, loeschen));
        fireEvent.keyDown(getByLabelText('Suche'), { key: 'Backspace' });
        fireEvent.keyDown(getByLabelText('Text'), { key: 'Delete' });
        fireEvent.keyDown(getByTestId('absatz'), { key: 'Backspace' });
        expect(loeschen).not.toHaveBeenCalled();
    });

    it('löscht nicht in Dialogen und Menüs', () => {
        const loeschen = vi.fn();
        const { getByText } = render(<Flaeche />);
        renderHook(() => useLoeschTaste(true, loeschen));
        fireEvent.keyDown(getByText('Im Dialog'), { key: 'Delete' });
        fireEvent.keyDown(getByText('Menüpunkt'), { key: 'Delete' });
        expect(loeschen).not.toHaveBeenCalled();
    });

    it('löscht nicht, solange irgendwo ein Dialog offen ist', () => {
        const loeschen = vi.fn();
        render(<div role="dialog" aria-modal="true" aria-label="Rückfrage" />);
        renderHook(() => useLoeschTaste(true, loeschen));
        fireEvent.keyDown(document.body, { key: 'Delete' });
        expect(loeschen).not.toHaveBeenCalled();
    });

    it('übergeht Tastenkürzel, gedrückt gehaltene Tasten und den ausgeschalteten Zustand', () => {
        const loeschen = vi.fn();
        const { rerender } = renderHook(({ aktiv }) => useLoeschTaste(aktiv, loeschen), { initialProps: { aktiv: true } });
        fireEvent.keyDown(document.body, { key: 'Backspace', metaKey: true });
        fireEvent.keyDown(document.body, { key: 'Delete', ctrlKey: true });
        fireEvent.keyDown(document.body, { key: 'Delete', altKey: true });
        fireEvent.keyDown(document.body, { key: 'Delete', repeat: true });
        rerender({ aktiv: false });
        fireEvent.keyDown(document.body, { key: 'Delete' });
        expect(loeschen).not.toHaveBeenCalled();
    });

    it('verhindert das Zurückspringen des Browsers bei ⌫', () => {
        renderHook(() => useLoeschTaste(true, vi.fn()));
        const ereignis = new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true, cancelable: true });
        document.body.dispatchEvent(ereignis);
        expect(ereignis.defaultPrevented).toBe(true);
    });
});
