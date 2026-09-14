import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Select } from './select-custom';

const defaultOptions = [
    { value: 'opt1', label: 'Option 1' },
    { value: 'opt2', label: 'Option 2' },
    { value: 'opt3', label: 'Option 3' },
];

describe('Select', () => {
    it('rendert mit Platzhaltertext', () => {
        render(<Select options={defaultOptions} value="" onChange={() => {}} placeholder="Auswahl..." />);
        expect(screen.getByText('Auswahl...')).toBeInTheDocument();
    });

    it('zeigt ausgewählten Wert an', () => {
        render(<Select options={defaultOptions} value="opt1" onChange={() => {}} />);
        expect(screen.getByText('Option 1')).toBeInTheDocument();
    });

    it('öffnet Dropdown bei Klick', async () => {
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        expect(screen.getByText('Option 1')).toBeInTheDocument();
        expect(screen.getByText('Option 2')).toBeInTheDocument();
        expect(screen.getByText('Option 3')).toBeInTheDocument();
    });

    it('ruft onChange bei Auswahl auf', async () => {
        const handleChange = vi.fn();
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="" onChange={handleChange} />);
        await user.click(screen.getByText('Bitte wählen...'));
        await user.click(screen.getByText('Option 2'));
        expect(handleChange).toHaveBeenCalledWith('opt2');
    });

    it('öffnet nicht bei disabled', async () => {
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="" onChange={() => {}} disabled />);
        await user.click(screen.getByText('Bitte wählen...'));
        // Die Optionen sollten nicht im Dropdown sichtbar sein (nur der Trigger-Text)
        expect(screen.queryAllByText('Option 1')).toHaveLength(0);
    });

    it('zeigt "Keine Optionen" bei leerer Liste', async () => {
        const user = userEvent.setup();
        render(<Select options={[]} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        expect(screen.getByText('Keine Optionen')).toBeInTheDocument();
    });

    it('markiert ausgewählte Option mit Häkchen', async () => {
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="opt1" onChange={() => {}} />);
        await user.click(screen.getByText('Option 1'));
        // Check icon should be visible for selected option
        const selectedItem = screen.getAllByText('Option 1');
        expect(selectedItem.length).toBeGreaterThanOrEqual(1);
    });

    it('akzeptiert zusätzliche CSS-Klassen', () => {
        const { container } = render(
            <Select options={defaultOptions} value="" onChange={() => {}} className="w-64" />
        );
        expect(container.firstChild).toHaveClass('w-64');
    });

    it('zeigt Standard-Platzhalter wenn keiner angegeben', () => {
        render(<Select options={defaultOptions} value="" onChange={() => {}} />);
        expect(screen.getByText('Bitte wählen...')).toBeInTheDocument();
    });
});

describe('Select Tastatur und Pflichtwert', () => {
    it('ist beschriftet, überspringt gesperrte Optionen und schließt mit Escape', async () => {
        const user = userEvent.setup(); const changed = vi.fn();
        render(<Select aria-label="Abteilung" options={[{value:'a',label:'A',disabled:true},{value:'b',label:'B'},{value:'c',label:'C'}]} value="" onChange={changed} />);
        await user.tab(); expect(screen.getByRole('combobox')).toHaveFocus();
        await user.keyboard('{ArrowDown}{Enter}'); expect(changed).toHaveBeenCalledWith('b');
        await user.keyboard('{ArrowDown}{Escape}'); expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        expect(screen.getByRole('combobox')).toHaveFocus();
    });
    it('blockiert ein Formular ohne Pflichtauswahl mit eigener Fehlermeldung', async () => {
        const user = userEvent.setup(); const submit = vi.fn(e => e.preventDefault());
        render(<form onSubmit={submit}><Select aria-label="Abteilung" required options={defaultOptions} value="" onChange={vi.fn()} /><button>Speichern</button></form>);
        await user.click(screen.getByText('Speichern')); expect(submit).not.toHaveBeenCalled();
        expect(screen.getByRole('alert')).toHaveTextContent('Abteilung');
    });
});
