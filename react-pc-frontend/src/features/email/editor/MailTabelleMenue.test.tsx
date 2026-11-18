/**
 * Tabellen im Mail-Textfeld: Menü „Tabelle“ (Größe per Raster mit Maus und
 * Tastatur, Zeilen/Spalten im Kontext) und Tab-Navigation zwischen Zellen.
 * DSGVO: nur Dummy-Daten.
 */
import { createRef } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MailNachrichtFeld } from './MailNachrichtFeld';

vi.mock('../../../components/ui/toast', () => ({
    useToast: () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() }),
}));

type ExecCommand = (befehl: string, ui?: boolean, wert?: string) => boolean;
const dok = document as unknown as { execCommand?: ExecCommand; queryCommandState?: unknown };

/** Wie der Browser: insertHTML ersetzt die Auswahl (und landet im Rückgängig-Stapel). */
function browserBefehle() {
    const exec = vi.fn<ExecCommand>((befehl, _ui, wert) => {
        if (befehl !== 'insertHTML' || wert === undefined) return false;
        const range = window.getSelection()!.getRangeAt(0);
        const vorlage = document.createElement('template');
        vorlage.innerHTML = wert;
        range.deleteContents();
        range.insertNode(vorlage.content);
        return true;
    });
    dok.execCommand = exec;
    return exec;
}

function feldMit(html: string) {
    const ref = createRef<HTMLDivElement>();
    const onInput = vi.fn();
    render(<><MailNachrichtFeld editorRef={ref} ariaLabel="Nachricht" onInput={onInput} /><button type="button">Weiter</button></>);
    const editor = screen.getByRole('textbox', { name: 'Nachricht' });
    editor.innerHTML = html;
    return { editor, onInput };
}

function cursorIn(knoten: Node, offset = 0) {
    const range = document.createRange();
    range.setStart(knoten, offset);
    range.collapse(true);
    window.getSelection()!.removeAllRanges();
    window.getSelection()!.addRange(range);
    act(() => { document.dispatchEvent(new Event('selectionchange')); });
}

const zellen = (editor: HTMLElement) => Array.from(editor.querySelectorAll('tr'))
    .map(zeile => Array.from(zeile.cells).map(zelle => zelle.textContent));

const RASTER = '<table><tbody><tr><td>A1</td><td>B1</td></tr><tr><td>A2</td><td>B2</td></tr></tbody></table><p>Gruß</p>';

beforeEach(() => {
    delete dok.execCommand;
    delete dok.queryCommandState;
});

describe('Menü „Tabelle“', () => {
    it('fügt per Tastatur eine Tabelle in gewählter Größe ein, Cursor in die erste Zelle', async () => {
        const { editor, onInput } = feldMit('<p>Liste:</p>');
        cursorIn(editor.querySelector('p')!.firstChild!, 6);

        const knopf = within(screen.getByRole('toolbar', { name: 'Formatierung' })).getByRole('button', { name: 'Tabelle' });
        expect(knopf).toHaveAttribute('aria-haspopup', 'menu');
        knopf.focus();
        await userEvent.keyboard('{Enter}');
        const menue = await screen.findByRole('menu');
        expect(within(menue).getByText(/Cursor in eine Tabelle setzen/)).toBeInTheDocument();
        const raster = within(menue).getByRole('menuitem', { name: /Tabelle mit 1 Zeile und 1 Spalte einfügen/ });
        await waitFor(() => expect(raster).toHaveFocus());

        await userEvent.keyboard('{ArrowRight}{ArrowRight}{ArrowDown}');
        expect(raster).toHaveAccessibleName('Tabelle mit 2 Zeilen und 3 Spalten einfügen');
        expect(within(menue).getByText('2 × 3')).toBeInTheDocument();
        await userEvent.keyboard('{Enter}');

        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        const tabelle = editor.querySelector('table')!;
        expect(tabelle.rows).toHaveLength(2);
        expect(tabelle.rows[0].cells).toHaveLength(3);
        expect(tabelle.hasAttribute('data-mail-tabelle-neu')).toBe(false);
        expect(onInput).toHaveBeenCalled();
        expect(document.activeElement).toBe(editor);
        expect(window.getSelection()!.getRangeAt(0).startContainer).toBe(tabelle.rows[0].cells[0]);
    });

    it('wählt die Größe mit der Maus', async () => {
        const { editor } = feldMit('<p>x</p>');
        cursorIn(editor.querySelector('p')!.firstChild!, 1);
        await userEvent.click(screen.getByRole('button', { name: 'Tabelle' }));
        const menue = await screen.findByRole('menu');
        await userEvent.click(within(menue).getByTestId('tabellen-raster-3-4'));
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(editor.querySelector('table')!.rows).toHaveLength(3);
        expect(editor.querySelector('table')!.rows[0].cells).toHaveLength(4);
    });

    it('bietet in einer Tabelle Zeilen und Spalten an und ändert die Tabelle', async () => {
        const { editor, onInput } = feldMit(RASTER);
        cursorIn(editor.querySelectorAll('td')[0].firstChild!, 1);

        await userEvent.click(screen.getByRole('button', { name: 'Tabelle' }));
        let menue = await screen.findByRole('menu');
        for (const name of ['Zeile darüber', 'Zeile darunter', 'Spalte links', 'Spalte rechts', 'Zeile löschen', 'Spalte löschen', 'Tabelle löschen']) {
            expect(within(menue).getByRole('menuitem', { name })).toBeInTheDocument();
        }
        expect(within(menue).queryByRole('menuitem', { name: /einfügen/ })).toBeNull();
        await userEvent.click(within(menue).getByRole('menuitem', { name: 'Zeile darunter' }));
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(zellen(editor)).toEqual([['A1', 'B1'], ['', ''], ['A2', 'B2']]);
        expect(onInput).toHaveBeenCalled();

        cursorIn(editor.querySelectorAll('td')[1].firstChild!, 0);
        await userEvent.click(screen.getByRole('button', { name: 'Tabelle' }));
        menue = await screen.findByRole('menu');
        await userEvent.click(within(menue).getByRole('menuitem', { name: 'Spalte löschen' }));
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(zellen(editor)).toEqual([['A1'], [''], ['A2']]);

        cursorIn(editor.querySelector('td')!.firstChild!, 0);
        await userEvent.click(screen.getByRole('button', { name: 'Tabelle' }));
        menue = await screen.findByRole('menu');
        await userEvent.click(within(menue).getByRole('menuitem', { name: 'Tabelle löschen' }));
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(editor.innerHTML).toBe('<p>Gruß</p>');
    });
});

describe('Rückgängig', () => {
    it('ändert Zeilen und Spalten über insertHTML, damit Strg+Z greift, Cursor in der passenden Zelle', async () => {
        const exec = browserBefehle();
        const { editor, onInput } = feldMit(RASTER);
        cursorIn(editor.querySelectorAll('td')[3].firstChild!, 1);

        await userEvent.click(screen.getByRole('button', { name: 'Tabelle' }));
        await userEvent.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: 'Spalte links' }));
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());

        expect(exec).toHaveBeenCalledWith('insertHTML', false, expect.stringContaining('<table'));
        expect(zellen(editor)).toEqual([['A1', '', 'B1'], ['A2', '', 'B2']]);
        expect(editor.querySelectorAll('table')).toHaveLength(1);
        expect(editor.innerHTML).not.toMatch(/data-mail-/);
        const ziel = editor.querySelectorAll('tr')[1].cells[1];
        expect(window.getSelection()!.getRangeAt(0).startContainer).toBe(ziel);
        expect(onInput).toHaveBeenCalled();
    });

    it('hängt auch mit Tab in der letzten Zelle über insertHTML an', () => {
        const exec = browserBefehle();
        const { editor } = feldMit(RASTER);
        cursorIn(editor.querySelectorAll('td')[3].firstChild!, 2);
        fireEvent.keyDown(editor, { key: 'Tab' });
        expect(exec).toHaveBeenCalledWith('insertHTML', false, expect.stringContaining('<table'));
        expect(zellen(editor)).toEqual([['A1', 'B1'], ['A2', 'B2'], ['', '']]);
        expect(window.getSelection()!.getRangeAt(0).startContainer).toBe(editor.querySelectorAll('tr')[2].cells[0]);
    });
});

describe('Tab in Tabellen', () => {
    it('springt mit Tab in die nächste Zelle und mit Shift+Tab zurück', () => {
        const { editor } = feldMit(RASTER);
        const [a1, b1, a2] = Array.from(editor.querySelectorAll('td'));
        cursorIn(a1.firstChild!, 1);

        const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        act(() => { editor.dispatchEvent(tab); });
        expect(tab.defaultPrevented).toBe(true);
        expect(window.getSelection()!.toString()).toBe('B1');
        expect(b1.contains(window.getSelection()!.anchorNode)).toBe(true);

        fireEvent.keyDown(editor, { key: 'Tab' });
        expect(window.getSelection()!.toString()).toBe('A2');
        fireEvent.keyDown(editor, { key: 'Tab', shiftKey: true });
        expect(window.getSelection()!.toString()).toBe('B1');
        expect(a2.textContent).toBe('A2');
    });

    it('hängt mit Tab in der letzten Zelle eine Zeile an', () => {
        const { editor, onInput } = feldMit(RASTER);
        cursorIn(editor.querySelectorAll('td')[3].firstChild!, 2);
        fireEvent.keyDown(editor, { key: 'Tab' });
        expect(zellen(editor)).toEqual([['A1', 'B1'], ['A2', 'B2'], ['', '']]);
        const neu = editor.querySelectorAll('tr')[2].cells[0];
        expect(window.getSelection()!.getRangeAt(0).startContainer).toBe(neu);
        expect(onInput).toHaveBeenCalled();
    });

    it('lässt Shift+Tab in der ersten Zelle und Tab außerhalb von Tabellen beim Browser', () => {
        const { editor } = feldMit(RASTER);
        cursorIn(editor.querySelector('td')!.firstChild!, 0);
        const zurueck = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
        act(() => { editor.dispatchEvent(zurueck); });
        expect(zurueck.defaultPrevented).toBe(false);

        cursorIn(editor.querySelector('p')!.firstChild!, 1);
        const weiter = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        act(() => { editor.dispatchEvent(weiter); });
        expect(weiter.defaultPrevented).toBe(false);
    });
});
