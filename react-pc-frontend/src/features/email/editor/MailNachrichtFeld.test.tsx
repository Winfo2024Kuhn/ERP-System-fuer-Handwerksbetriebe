import { createRef, useRef } from 'react';
import { act, createEvent, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MailNachrichtFeld } from './MailNachrichtFeld';
import { MailFormatLeiste } from './MailFormatLeiste';
import { MailTextFeld } from './MailTextFeld';
import { useMailEditor } from './useMailEditor';

const { toastFehler, toastSpy } = vi.hoisted(() => {
    const fehler = vi.fn();
    return { toastFehler: fehler, toastSpy: { error: fehler, success: vi.fn(), warning: vi.fn(), info: vi.fn() } };
});
vi.mock('../../../components/ui/toast', () => ({ useToast: () => toastSpy }));

type ExecCommand = (befehl: string, ui?: boolean, wert?: string) => boolean;
const dok = document as unknown as { execCommand?: ExecCommand; queryCommandState?: (b: string) => boolean };

const ONEDRIVE = 'https://onedrive.example.com/f/s!AbCdEf123?e=XyZ';

function feldMit(html: string, onInput = vi.fn()) {
    const ref = createRef<HTMLDivElement>();
    render(<MailNachrichtFeld editorRef={ref} ariaLabel="Nachricht" onInput={onInput} />);
    const editor = screen.getByRole('textbox', { name: 'Nachricht' });
    editor.innerHTML = html;
    return { editor, onInput, ref };
}

const linkLeiste = () => within(screen.getByRole('toolbar', { name: 'Link' }));

function markiere(knoten: Node, start: number, ende: number) {
    const range = document.createRange();
    range.setStart(knoten, start);
    range.setEnd(knoten, ende);
    const auswahl = window.getSelection()!;
    auswahl.removeAllRanges();
    auswahl.addRange(range);
    act(() => { document.dispatchEvent(new Event('selectionchange')); });
}

beforeEach(() => {
    delete dok.execCommand;
    delete dok.queryCommandState;
    toastFehler.mockReset();
    toastSpy.success.mockReset();
    toastSpy.warning.mockReset();
});
afterEach(() => { vi.restoreAllMocks(); });

describe('Formatierungsleiste', () => {
    it('zeigt eine Zeile mit Direktknöpfen und drei Menüs', () => {
        feldMit('<p>Hallo</p>');
        const leiste = screen.getByRole('toolbar', { name: 'Formatierung' });
        for (const name of ['Fett', 'Kursiv', 'Unterstrichen']) {
            expect(within(leiste).getByRole('button', { name })).toHaveAttribute('aria-pressed', 'false');
        }
        expect(within(leiste).getByRole('button', { name: 'Link einfügen' })).toBeInTheDocument();
        expect(within(leiste).getByRole('button', { name: 'Schrift' })).toHaveAttribute('aria-haspopup', 'menu');
        expect(within(leiste).getByRole('button', { name: 'Absatz' })).toHaveAttribute('aria-haspopup', 'menu');
        expect(within(leiste).getByRole('button', { name: 'Tabelle' })).toHaveAttribute('aria-haspopup', 'menu');
    });

    it('führt Fett aus und zeigt den aktiven Zustand', async () => {
        const exec = vi.fn<ExecCommand>(() => true);
        dok.execCommand = exec;
        let fett = false;
        dok.queryCommandState = (befehl) => befehl === 'bold' && fett;
        const { editor, onInput } = feldMit('<p>Hallo</p>');
        markiere(editor.querySelector('p')!.firstChild!, 0, 5);

        fett = true;
        await userEvent.click(screen.getByRole('button', { name: 'Fett' }));

        expect(exec).toHaveBeenCalledWith('bold', false, undefined);
        expect(screen.getByRole('button', { name: 'Fett' })).toHaveAttribute('aria-pressed', 'true');
        expect(onInput).toHaveBeenCalled();
        expect(document.activeElement).toBe(editor);
    });

    it('lässt sich mit den Pfeiltasten durchlaufen', async () => {
        feldMit('<p>x</p>');
        screen.getByRole('button', { name: 'Fett' }).focus();
        await userEvent.keyboard('{ArrowRight}');
        expect(screen.getByRole('button', { name: 'Kursiv' })).toHaveFocus();
        await userEvent.keyboard('{ArrowLeft}{ArrowLeft}');
        expect(screen.getByRole('button', { name: 'Tabelle' })).toHaveFocus();
    });

    it('Menü „Schrift“ bietet Größe, Farben und Marker und setzt die Farbe', async () => {
        const exec = vi.fn<ExecCommand>(() => true);
        dok.execCommand = exec;
        const { editor } = feldMit('<p>Wichtig</p>');
        markiere(editor.querySelector('p')!.firstChild!, 0, 7);

        await userEvent.click(screen.getByRole('button', { name: 'Schrift' }));
        const menue = await screen.findByRole('menu');
        for (const name of ['Klein', 'Normal', 'Groß', 'Überschrift', 'Gelb markieren', 'Markierung entfernen',
            'Durchgestrichen', 'Formatierung entfernen']) {
            expect(within(menue).getByRole(name === 'Durchgestrichen' ? 'menuitemcheckbox' : 'menuitem', { name })).toBeInTheDocument();
        }
        await userEvent.click(within(menue).getByRole('menuitem', { name: 'Textfarbe Rot' }));

        expect(exec).toHaveBeenCalledWith('styleWithCSS', false, 'true');
        expect(exec).toHaveBeenCalledWith('foreColor', false, '#dc2626');
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    });

    it('Menü „Absatz“ setzt Listen, Ausrichtung und Einzug', async () => {
        const exec = vi.fn<ExecCommand>(() => true);
        dok.execCommand = exec;
        feldMit('<p>Punkt</p>');

        await userEvent.click(screen.getByRole('button', { name: 'Absatz' }));
        const menue = await screen.findByRole('menu');
        expect(within(menue).getByRole('menuitemcheckbox', { name: 'Aufzählung' })).toBeInTheDocument();
        expect(within(menue).getByRole('menuitemcheckbox', { name: 'Nummerierung' })).toBeInTheDocument();
        for (const name of ['Linksbündig', 'Zentriert', 'Rechtsbündig', 'Einzug vergrößern', 'Einzug verkleinern']) {
            expect(within(menue).getByRole('menuitem', { name })).toBeInTheDocument();
        }
        await userEvent.click(within(menue).getByRole('menuitemcheckbox', { name: 'Aufzählung' }));
        expect(exec).toHaveBeenCalledWith('insertUnorderedList', false, undefined);
    });

    it('Escape schließt ein Menü, der Fokus kehrt zum Knopf zurück', async () => {
        feldMit('<p>x</p>');
        const knopf = screen.getByRole('button', { name: 'Absatz' });
        knopf.focus();
        await userEvent.keyboard('{Enter}');
        expect(await screen.findByRole('menu')).toBeInTheDocument();
        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
        expect(knopf).toHaveFocus();
    });

    it('lässt sich außerhalb des Felds platzieren', async () => {
        const exec = vi.fn<ExecCommand>(() => true);
        dok.execCommand = exec;
        function Aussen() {
            const ref = useRef<HTMLDivElement>(null);
            const editor = useMailEditor({ editorRef: ref });
            return (
                <div>
                    <header data-testid="kopf"><MailFormatLeiste editor={editor} /></header>
                    <MailTextFeld editor={editor} ariaLabel="Nachricht" />
                </div>
            );
        }
        render(<Aussen />);
        expect(within(screen.getByTestId('kopf')).getByRole('toolbar', { name: 'Formatierung' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Kursiv' }));
        expect(exec).toHaveBeenCalledWith('italic', false, undefined);
        expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'Nachricht' }));
    });
});

describe('Link einfügen', () => {
    it('Strg+K öffnet den Dialog, prüft die Adresse und macht den markierten Text zum Link', async () => {
        const { editor, onInput } = feldMit('<p>Bitte hier klicken</p>');
        markiere(editor.querySelector('p')!.firstChild!, 6, 10);

        fireEvent.keyDown(editor, { key: 'k', ctrlKey: true });

        const dialog = await screen.findByRole('dialog', { name: 'Link einfügen' });
        expect(within(dialog).getByLabelText('Angezeigter Text')).toHaveValue('hier');
        const adresse = within(dialog).getByLabelText('Link-Adresse');
        expect(adresse).toHaveFocus();
        expect(adresse).toHaveAttribute('placeholder', 'https://… z. B. OneDrive-Freigabelink');
        const einfuegen = within(dialog).getByRole('button', { name: 'Link einfügen' });
        expect(einfuegen).toBeDisabled();
        expect(within(dialog).getByRole('button', { name: 'Mehr zu: Link-Adresse' })).toBeInTheDocument();

        await userEvent.type(adresse, 'javascript:alert(1)');
        await userEvent.click(einfuegen);
        expect(within(dialog).getByRole('alert')).toHaveTextContent('Bitte eine Web-Adresse (https://…) oder E-Mail-Adresse eintragen.');
        expect(adresse).toHaveAttribute('aria-invalid', 'true');
        expect(editor.querySelector('a')).toBeNull();

        await userEvent.clear(adresse);
        await userEvent.type(adresse, ONEDRIVE.replace('https://', ''));
        await userEvent.click(einfuegen);

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(editor.innerHTML).toBe(`<p>Bitte <a href="${ONEDRIVE.replace(/&/g, '&amp;')}">hier</a> klicken</p>`);
        expect(onInput).toHaveBeenCalled();
    });

    it('setzt einen Link mit eigenem Text an den Cursor; ohne Text steht die Adresse da', async () => {
        const { editor } = feldMit('<p>Gruß</p>');
        markiere(editor.querySelector('p')!.firstChild!, 4, 4);

        await userEvent.click(screen.getByRole('button', { name: 'Link einfügen' }));
        let dialog = await screen.findByRole('dialog', { name: 'Link einfügen' });
        expect(within(dialog).getByLabelText('Angezeigter Text')).toHaveFocus();
        await userEvent.type(within(dialog).getByLabelText('Angezeigter Text'), '<img src=x onerror=alert(1)>');
        await userEvent.type(within(dialog).getByLabelText('Link-Adresse'), 'www.example.com');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Link einfügen' }));

        const link = editor.querySelector('a')!;
        expect(link.getAttribute('href')).toBe('https://www.example.com');
        expect(link.textContent).toBe('<img src=x onerror=alert(1)>');
        expect(editor.querySelector('img')).toBeNull();

        markiere(editor.querySelector('p')!.firstChild!, 0, 0);
        await userEvent.click(screen.getByRole('button', { name: 'Link einfügen' }));
        dialog = await screen.findByRole('dialog', { name: 'Link einfügen' });
        await userEvent.type(within(dialog).getByLabelText('Link-Adresse'), 'max.mustermann@example.com');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Link einfügen' }));
        const mail = editor.querySelector('a[href^="mailto:"]')!;
        expect(mail.textContent).toBe('max.mustermann@example.com');
    });

    it('Abbrechen lässt den Text unverändert', async () => {
        const { editor } = feldMit('<p>Text</p>');
        await userEvent.click(screen.getByRole('button', { name: 'Link einfügen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Link einfügen' });
        await userEvent.click(within(dialog).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(editor.innerHTML).toBe('<p>Text</p>');
    });
});

describe('Vorhandenen Link bearbeiten', () => {
    function mitLink() {
        const daten = feldMit('<p>Siehe <a href="https://alt.example.com/freigabe">alter Link</a> bitte.</p>');
        markiere(daten.editor.querySelector('a')!.firstChild!, 2, 2);
        return daten;
    }

    it('zeigt am Link eine Leiste mit gekürzter Adresse', () => {
        mitLink();
        const leiste = screen.getByRole('toolbar', { name: 'Link' });
        expect(leiste).toHaveTextContent('alt.example.com/freigabe');
        for (const name of ['Link ändern', 'Link öffnen', 'Link entfernen']) {
            expect(within(leiste).getByRole('button', { name })).toBeInTheDocument();
        }
    });

    it('ändert Text und Adresse über den Dialog', async () => {
        const { editor, onInput } = mitLink();
        await userEvent.click(linkLeiste().getByRole('button', { name: 'Link ändern' }));
        const dialog = await screen.findByRole('dialog', { name: 'Link ändern' });
        const text = within(dialog).getByLabelText('Angezeigter Text');
        expect(text).toHaveValue('alter Link');
        expect(within(dialog).getByLabelText('Link-Adresse')).toHaveValue('https://alt.example.com/freigabe');
        await userEvent.clear(text);
        await userEvent.type(text, 'Hier klicken');
        await userEvent.clear(within(dialog).getByLabelText('Link-Adresse'));
        await userEvent.type(within(dialog).getByLabelText('Link-Adresse'), ONEDRIVE);
        await userEvent.click(within(dialog).getByRole('button', { name: 'Übernehmen' }));

        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        const link = editor.querySelector('a')!;
        expect(link.textContent).toBe('Hier klicken');
        expect(link.getAttribute('href')).toBe(ONEDRIVE);
        expect(onInput).toHaveBeenCalled();
    });

    it('zeigt im Link nicht „Unterstrichen“ als aktiv (Links sind ohnehin unterstrichen)', () => {
        dok.queryCommandState = (befehl) => befehl === 'underline';
        mitLink();
        expect(screen.getByRole('button', { name: 'Unterstrichen' })).toHaveAttribute('aria-pressed', 'false');
    });

    it('Strg+K im Link öffnet „Link ändern“', async () => {
        const { editor } = mitLink();
        fireEvent.keyDown(editor, { key: 'K', metaKey: true });
        expect(await screen.findByRole('dialog', { name: 'Link ändern' })).toBeInTheDocument();
    });

    it('entfernt den Link, der Text bleibt', async () => {
        const { editor, onInput } = mitLink();
        await userEvent.click(linkLeiste().getByRole('button', { name: 'Link entfernen' }));
        expect(editor.innerHTML).toBe('<p>Siehe alter Link bitte.</p>');
        expect(onInput).toHaveBeenCalled();
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();
    });

    it('öffnet nur erlaubte Adressen, ohne Zugriff auf diese Seite', async () => {
        const oeffnen = vi.spyOn(window, 'open').mockReturnValue(null);
        const { editor } = mitLink();
        await userEvent.click(linkLeiste().getByRole('button', { name: 'Link öffnen' }));
        expect(oeffnen).toHaveBeenCalledWith('https://alt.example.com/freigabe', '_blank', 'noopener,noreferrer');

        oeffnen.mockClear();
        editor.querySelector('a')!.setAttribute('href', 'javascript:alert(1)');
        markiere(editor.querySelector('a')!.firstChild!, 1, 1);
        await userEvent.click(linkLeiste().getByRole('button', { name: 'Link öffnen' }));
        expect(oeffnen).not.toHaveBeenCalled();
        expect(toastFehler).toHaveBeenCalledWith('Dieser Link lässt sich nicht öffnen.');
    });

    it('Escape schließt die Leiste, ohne das Feld zu verlassen', () => {
        const { editor } = mitLink();
        const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        act(() => { editor.dispatchEvent(escape); });
        expect(escape.defaultPrevented).toBe(true);
        fireEvent.keyUp(editor, { key: 'Escape' });
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();
    });

    it('Link-Knopf der Leiste heißt im Link „Link ändern“ und ist gedrückt', () => {
        mitLink();
        const knopf = within(screen.getByRole('toolbar', { name: 'Formatierung' })).getByRole('button', { name: 'Link ändern' });
        expect(knopf).toHaveAttribute('aria-pressed', 'true');
        expect(knopf.getAttribute('title')).toMatch(/^Link ändern/);
    });

    it('Tab aus der Leiste verlässt das Feld, Shift+Tab geht zurück ins Feld', () => {
        const ref = createRef<HTMLDivElement>();
        render(<><MailNachrichtFeld editorRef={ref} ariaLabel="Brieftext" /><button type="button">Weiter</button></>);
        const editor = screen.getByRole('textbox', { name: 'Brieftext' });
        editor.innerHTML = '<p>Siehe <a href="https://example.com/a">Link</a> bitte.</p>';
        markiere(editor.querySelector('a')!.firstChild!, 2, 2);
        editor.focus();

        fireEvent.keyDown(editor, { key: 'Tab' });
        const aendern = linkLeiste().getByRole('button', { name: 'Link ändern' });
        expect(aendern).toHaveFocus();
        fireEvent.keyDown(aendern, { key: 'Tab' });
        expect(screen.getByRole('button', { name: 'Weiter' })).toHaveFocus();
        act(() => { document.dispatchEvent(new Event('selectionchange')); });
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();

        // Zurück ins Feld: die Leiste bleibt zu, bis man den Link wieder anklickt.
        editor.focus();
        fireEvent.keyDown(editor, { key: 'Tab' });
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();
        markiere(editor.querySelector('a')!.firstChild!, 1, 1);
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();
        fireEvent.mouseUp(editor);
        fireEvent.keyDown(editor, { key: 'Tab' });
        fireEvent.keyDown(linkLeiste().getByRole('button', { name: 'Link ändern' }), { key: 'Tab', shiftKey: true });
        expect(editor).toHaveFocus();
        act(() => { document.dispatchEvent(new Event('selectionchange')); });
        expect(screen.queryByRole('toolbar', { name: 'Link' })).not.toBeInTheDocument();
    });

    it('Tab springt vom Link in die Leiste', async () => {
        const { editor } = mitLink();
        editor.focus();
        fireEvent.keyDown(editor, { key: 'Tab' });
        expect(linkLeiste().getByRole('button', { name: 'Link ändern' })).toHaveFocus();
    });
});

describe('Adresse über markierten Text einfügen', () => {
    function einfuegen(ziel: HTMLElement, text: string) {
        const ereignis = new Event('paste', { bubbles: true, cancelable: true }) as Event & { clipboardData: unknown };
        ereignis.clipboardData = { getData: (typ: string) => (typ === 'text/plain' ? text : '') };
        act(() => { ziel.dispatchEvent(ereignis); });
        return ereignis;
    }

    it('macht den markierten Text zum Link', () => {
        const { editor, onInput } = feldMit('<p>Bitte hier klicken</p>');
        markiere(editor.querySelector('p')!.firstChild!, 6, 10);
        const ereignis = einfuegen(editor, ` ${ONEDRIVE} `);
        expect(ereignis.defaultPrevented).toBe(true);
        expect(editor.querySelector('a')!.getAttribute('href')).toBe(ONEDRIVE);
        expect(editor.querySelector('a')!.textContent).toBe('hier');
        expect(onInput).toHaveBeenCalled();
    });

    it('fügt eine Adresse ohne Markierung direkt als Link ein, der Text ist die Adresse', () => {
        const { editor, onInput } = feldMit('<p>Hier: </p>');
        markiere(editor.querySelector('p')!.firstChild!, 6, 6);
        expect(einfuegen(editor, ` ${ONEDRIVE}\n`).defaultPrevented).toBe(true);
        const link = editor.querySelector('a')!;
        expect(link.getAttribute('href')).toBe(ONEDRIVE);
        expect(link.textContent).toBe(ONEDRIVE);
        expect(onInput).toHaveBeenCalled();
    });

    it('fügt alles andere normal ein', () => {
        const { editor } = feldMit('<p>Bitte hier klicken</p>');
        markiere(editor.querySelector('p')!.firstChild!, 6, 10);
        expect(einfuegen(editor, 'javascript:alert(1)').defaultPrevented).toBe(false);
        expect(einfuegen(editor, 'Hallo Welt').defaultPrevented).toBe(false);
        expect(einfuegen(editor, `${ONEDRIVE} und mehr`).defaultPrevented).toBe(false);
        expect(editor.querySelector('a')).toBeNull();
    });
});

describe('Bilder und Dateien einfügen oder hineinziehen', () => {
    const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

    function mitDateien(onDateien?: (dateien: File[]) => unknown) {
        const ref = createRef<HTMLDivElement>();
        render(<MailNachrichtFeld editorRef={ref} ariaLabel="Nachricht" onDateien={onDateien} />);
        const editor = screen.getByRole('textbox', { name: 'Nachricht' });
        editor.innerHTML = '<p>Hallo </p>';
        markiere(editor.querySelector('p')!.firstChild!, 6, 6);
        return editor;
    }

    function zwischenablage(daten: { html?: string; text?: string; dateien?: File[] }) {
        return {
            files: daten.dateien ?? [],
            types: [...(daten.dateien?.length ? ['Files'] : []), ...(daten.html ? ['text/html'] : []), 'text/plain'],
            getData: (typ: string) => (typ === 'text/html' ? daten.html ?? '' : typ === 'text/plain' ? daten.text ?? '' : ''),
        };
    }

    function einfuegen(ziel: HTMLElement, daten: Parameters<typeof zwischenablage>[0]) {
        const ereignis = new Event('paste', { bubbles: true, cancelable: true }) as Event & { clipboardData: unknown };
        ereignis.clipboardData = zwischenablage(daten);
        act(() => { ziel.dispatchEvent(ereignis); });
        return ereignis;
    }

    it('hängt eine eingefügte Bilddatei an, statt sie in den Text zu setzen', async () => {
        const onDateien = vi.fn(() => Promise.resolve(1));
        const editor = mitDateien(onDateien);
        const ereignis = einfuegen(editor, { dateien: [new File(['x'], 'image.png', { type: 'image/png' })] });

        expect(ereignis.defaultPrevented).toBe(true);
        expect(editor.querySelector('img')).toBeNull();
        expect(onDateien).toHaveBeenCalledTimes(1);
        const [dateien] = onDateien.mock.calls[0] as unknown as [File[]];
        expect(dateien.map(d => d.name)).toEqual(['Bild-1.png']);
        await waitFor(() => expect(toastSpy.success).toHaveBeenCalledWith('Bild als Anhang hinzugefügt.'));
    });

    it('meldet ohne Anhangsliste, dass hier keine Dateien hineingehen', () => {
        const editor = mitDateien();
        const ereignis = einfuegen(editor, { dateien: [new File(['%PDF'], 'Plan.pdf', { type: 'application/pdf' })] });
        expect(ereignis.defaultPrevented).toBe(true);
        expect(toastSpy.warning).toHaveBeenCalledWith('In dieses Feld lassen sich keine Bilder oder Dateien einfügen.');
        expect(editor.innerHTML).toBe('<p>Hallo </p>');
    });

    it('setzt eingefügtes HTML bereinigt ein: Tabelle bleibt, eingebettetes Bild wird Anhang', async () => {
        const onDateien = vi.fn();
        const editor = mitDateien(onDateien);
        const onInput = vi.fn();
        editor.addEventListener('input', onInput);
        einfuegen(editor, {
            html: `<table><tbody><tr><td><b>Träger</b></td><td>2 Stk</td></tr></tbody></table><img src="${PNG}"><img src=x onerror=alert(1)><script>alert(2)</script>`,
            text: 'Träger 2 Stk',
        });

        expect(editor.querySelector('td b')?.textContent).toBe('Träger');
        expect(editor.querySelector('img, script')).toBeNull();
        expect(onInput).toHaveBeenCalled();
        const [dateien] = onDateien.mock.calls[0] as [File[]];
        expect(dateien.map(d => d.name)).toEqual(['Bild-1.png']);
        await waitFor(() => expect(toastSpy.success).toHaveBeenCalledWith('Bild als Anhang hinzugefügt.'));
        expect(toastSpy.warning).toHaveBeenCalledWith('1 Bild konnte nicht übernommen werden. Bitte als Datei anhängen.');
    });

    it('entfernt Bilder aus dem Internet mit Hinweis, außer die Bilddatei kam mit', () => {
        const onDateien = vi.fn();
        const editor = mitDateien(onDateien);
        einfuegen(editor, { html: '<p>Katalog</p><img src="https://example.com/a.png"><img src="https://example.com/b.png">' });
        expect(editor.textContent).toContain('Katalog');
        expect(editor.querySelector('img')).toBeNull();
        expect(toastSpy.warning).toHaveBeenCalledWith('2 Bilder aus dem Internet wurden nicht übernommen. Bitte als Datei anhängen.');
        expect(onDateien).not.toHaveBeenCalled();

        toastSpy.warning.mockClear();
        einfuegen(editor, {
            html: '<img src="https://example.com/a.png">',
            dateien: [new File(['x'], 'image.png', { type: 'image/png' })],
        });
        expect(toastSpy.warning).not.toHaveBeenCalled();
        expect(onDateien).toHaveBeenCalledTimes(1);
        expect((onDateien.mock.calls[0] as [File[]])[0]).toHaveLength(1);
    });

    it('lässt reinen Text und Adressen wie bisher', () => {
        const onDateien = vi.fn();
        const editor = mitDateien(onDateien);
        expect(einfuegen(editor, { text: 'Hallo Welt' }).defaultPrevented).toBe(false);
        expect(einfuegen(editor, { text: ONEDRIVE, html: `<a href="${ONEDRIVE}">${ONEDRIVE}</a>` }).defaultPrevented).toBe(true);
        expect(editor.querySelector('a')?.getAttribute('href')).toBe(ONEDRIVE);
        expect(onDateien).not.toHaveBeenCalled();
    });

    it('hängt hineingezogene Dateien an und lässt das Ziehen im Text in Ruhe', async () => {
        const onDateien = vi.fn(() => Promise.resolve(2));
        const editor = mitDateien(onDateien);
        const daten = zwischenablage({ dateien: [
            new File(['a'], 'Aufmass.pdf', { type: 'application/pdf' }),
            new File(['b'], 'Foto.jpg', { type: 'image/jpeg' }),
        ] });

        const ueber = new Event('dragover', { bubbles: true, cancelable: true }) as Event & { dataTransfer: unknown };
        ueber.dataTransfer = { ...daten, dropEffect: 'none' };
        act(() => { editor.dispatchEvent(ueber); });
        expect(ueber.defaultPrevented).toBe(true);

        fireEvent.drop(editor, { dataTransfer: daten });
        expect(onDateien).toHaveBeenCalledWith([expect.objectContaining({ name: 'Aufmass.pdf' }), expect.objectContaining({ name: 'Foto.jpg' })]);
        await waitFor(() => expect(toastSpy.success).toHaveBeenCalledWith('2 Dateien als Anhang hinzugefügt.'));

        onDateien.mockClear();
        fireEvent.dragStart(editor.querySelector('p')!);
        const intern = createEvent.drop(editor, { dataTransfer: zwischenablage({ html: '<p>Hallo</p>' }) });
        fireEvent(editor, intern);
        expect(intern.defaultPrevented).toBe(false);
        expect(onDateien).not.toHaveBeenCalled();
    });
});
