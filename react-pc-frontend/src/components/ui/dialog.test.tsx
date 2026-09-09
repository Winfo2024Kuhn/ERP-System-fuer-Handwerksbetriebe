import { useState } from 'react';
import { Select } from './select-custom';
import { DatePicker } from './datepicker';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
    DialogFooter,
} from './dialog';

describe('Dialog', () => {
    it('rendert nichts wenn open=false', () => {
        const { container } = render(
            <Dialog open={false}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        expect(container.firstChild).toBeNull();
    });

    it('rendert Inhalt wenn open=true', () => {
        render(
            <Dialog open={true}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Titel</DialogTitle>
                        <DialogDescription>Beschreibung</DialogDescription>
                    </DialogHeader>
                </DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Titel')).toBeInTheDocument();
        expect(screen.getByText('Beschreibung')).toBeInTheDocument();
    });

    it('hat einen Schließen-Button', () => {
        render(
            <Dialog open={true}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Close')).toBeInTheDocument();
    });

    it('ruft onOpenChange beim Klick auf X auf', async () => {
        const handleOpenChange = vi.fn();
        const user = userEvent.setup();
        render(
            <Dialog open={true} onOpenChange={handleOpenChange}>
                <DialogContent>Inhalt</DialogContent>
            </Dialog>
        );
        // Der X-Button hat sr-only "Close"
        await user.click(screen.getByText('Close').closest('button')!);
        expect(handleOpenChange).toHaveBeenCalledWith(false);
    });

    it('rendert DialogFooter', () => {
        render(
            <Dialog open={true}>
                <DialogContent>
                    <DialogFooter>
                        <button>Speichern</button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        );
        expect(screen.getByText('Speichern')).toBeInTheDocument();
    });
});

// Regression: keyboard and pointer focus must remain in the uppermost dialog,
// while real picker portals retain their own keyboard behavior.

function FocusExample() {
    const [open, setOpen] = useState(false);
    const [nested, setNested] = useState(false);
    const [option, setOption] = useState('a');
    const [date, setDate] = useState('2026-09-09');
    return <><button onClick={() => setOpen(true)}>Öffnen</button><button>Außerhalb</button>
        <Dialog open={open} onOpenChange={setOpen} aria-label="Hauptdialog">
            <input aria-label="Grund" /><button disabled>Gesperrt</button>
            <Select aria-label="Auswahl" value={option} onChange={setOption} options={[{ value: 'a', label: 'Erste' }, { value: 'b', label: 'Zweite' }]} />
            <DatePicker aria-label="Datum" value={date} onChange={setDate} />
            <button onClick={() => setNested(true)}>Unterdialog</button>
            <button onClick={() => setOpen(false)}>Abbrechen</button>
            <Dialog open={nested} onOpenChange={setNested} aria-label="Unterdialog">
                <input aria-label="Notiz" /><button onClick={() => setNested(false)}>Zurück</button>
            </Dialog>
        </Dialog>
    </>;
}
it('fängt Tab und ShiftTab und führt nach Abbrechen zum Öffner zurück', async () => {
    const user = userEvent.setup(); render(<FocusExample />);
    const opener = screen.getByRole('button', { name: 'Öffnen' }); await user.click(opener);
    const first = screen.getByRole('textbox', { name: 'Grund' }); expect(first).toHaveFocus();
    const close = within(screen.getByRole('dialog', { name: 'Hauptdialog' })).getByRole('button', { name: 'Close' });
    await user.tab({ shift: true }); expect(close).toHaveFocus(); await user.tab(); expect(first).toHaveFocus();
    await user.click(screen.getByRole('button', { name: 'Abbrechen' })); await waitFor(() => expect(opener).toHaveFocus());
});
it('weist Klickfokus außerhalb zurück und schließt nur den obersten verschachtelten Dialog', async () => {
    const user = userEvent.setup(); render(<FocusExample />); await user.click(screen.getByRole('button', { name: 'Öffnen' }));
    await user.click(screen.getByRole('button', { name: 'Unterdialog' })); expect(screen.getByRole('textbox', { name: 'Notiz' })).toHaveFocus();
    await user.click(screen.getByRole('button', { name: 'Außerhalb' })); expect(screen.getByRole('textbox', { name: 'Notiz' })).toHaveFocus();
    await user.keyboard('{Escape}'); await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Unterdialog' })).not.toBeInTheDocument());
    expect(screen.getByRole('dialog', { name: 'Hauptdialog' })).toBeVisible(); expect(screen.getByRole('button', { name: 'Unterdialog' })).toHaveFocus();
    await user.keyboard('{Escape}'); await waitFor(() => expect(screen.getByRole('button', { name: 'Öffnen' })).toHaveFocus());
});
it('gibt bei Unmount den Fokus zurück und entfernt die Fokuswache', async () => {
    const opener = document.createElement('button'); document.body.append(opener); opener.focus();
    const view = render(<Dialog open><input aria-label="Feld" /></Dialog>);
    expect(screen.getByRole('textbox', { name: 'Feld' })).toHaveFocus();
    view.unmount(); await waitFor(() => expect(opener).toHaveFocus()); opener.remove();
});
it('lässt eigene Select- und Kalenderportale arbeiten; Escape schließt zuerst den Picker', async () => {
    const user = userEvent.setup(); render(<FocusExample />); await user.click(screen.getByRole('button', { name: 'Öffnen' }));
    const select = screen.getByRole('combobox', { name: 'Auswahl' }); await user.click(select); await user.keyboard('{ArrowDown}{Enter}'); expect(select).toHaveTextContent('Zweite');
    await user.click(select); await user.keyboard('{Escape}'); expect(screen.queryByRole('listbox')).not.toBeInTheDocument(); expect(select).toHaveFocus();
    const date = screen.getByRole('button', { name: 'Datum', exact: true }); await user.click(date);
    expect(screen.getByRole('button', { name: '09.09.2026' })).toHaveFocus(); await user.keyboard('{ArrowRight}{Enter}'); expect(date).toHaveTextContent('10.09.2026');
    await user.click(date); await user.keyboard('{Escape}'); expect(screen.queryByRole('dialog', { name: 'Datum auswählen' })).not.toBeInTheDocument(); expect(date).toHaveFocus(); expect(screen.getByRole('dialog', { name: 'Hauptdialog' })).toBeVisible();
});
it('lässt eine darüber geöffnete externe Bestätigung ihren Fokus behalten', async () => {
    const user = userEvent.setup(); render(<FocusExample />); await user.click(screen.getByRole('button', { name: 'Öffnen' }));
    const confirmation = document.createElement('div'); confirmation.setAttribute('role', 'dialog'); confirmation.setAttribute('aria-modal', 'true'); confirmation.style.zIndex = '10001';
    const button = document.createElement('button'); confirmation.append(button); document.body.append(confirmation);
    await act(async () => button.focus()); expect(button).toHaveFocus(); fireEvent.keyDown(button, { key: 'Escape' }); expect(screen.getByRole('dialog', { name: 'Hauptdialog' })).toBeVisible(); confirmation.remove();
});

it('bewahrt autoFocus und stellt trotzdem den ursprünglichen Öffner wieder her', async () => {
    const opener = document.createElement('button'); document.body.append(opener); opener.focus();
    const view = render(<Dialog open><input aria-label="Erstes" /><input aria-label="Autofokus" autoFocus /></Dialog>);
    expect(screen.getByRole('textbox', { name: 'Autofokus' })).toHaveFocus(); view.unmount();
    await waitFor(() => expect(opener).toHaveFocus()); opener.remove();
});
it('kehrt beim Abbrechen des Unterdialogs zu dessen Öffner zurück', async () => {
    const user = userEvent.setup(); render(<FocusExample />); await user.click(screen.getByRole('button', { name: 'Öffnen' }));
    const opener = screen.getByRole('button', { name: 'Unterdialog' }); await user.click(opener); await user.click(screen.getByRole('button', { name: 'Zurück' }));
    await waitFor(() => expect(opener).toHaveFocus()); expect(screen.getByRole('dialog', { name: 'Hauptdialog' })).toBeVisible();
});
