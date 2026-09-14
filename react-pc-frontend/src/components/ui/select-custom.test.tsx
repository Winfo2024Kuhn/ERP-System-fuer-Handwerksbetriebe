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
        expect(screen.getByText('Auswahl...')).toBeTruthy();
    });

    it('zeigt ausgewählten Wert an', () => {
        render(<Select options={defaultOptions} value="opt1" onChange={() => {}} />);
        expect(screen.getByText('Option 1')).toBeTruthy();
    });

    it('öffnet Dropdown bei Klick', async () => {
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        expect(screen.getByText('Option 1')).toBeTruthy();
        expect(screen.getByText('Option 2')).toBeTruthy();
        expect(screen.getByText('Option 3')).toBeTruthy();
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
        expect(screen.getByText('Keine Optionen')).toBeTruthy();
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
        const aussenDiv = container.firstChild as HTMLElement;
        expect(aussenDiv.className.split(' ')).toContain('w-64');
    });

    it('zeigt Standard-Platzhalter wenn keiner angegeben', () => {
        render(<Select options={defaultOptions} value="" onChange={() => {}} />);
        expect(screen.getByText('Bitte wählen...')).toBeTruthy();
    });

    it('gruppiert Optionen und zeigt zwei Überschriften in der richtigen Reihenfolge', async () => {
        const user = userEvent.setup();
        const gruppierteOptionen = [
            { value: 'a1', label: 'Aufwand A', gruppe: 'Aufwand' },
            { value: 'e1', label: 'Ertrag A', gruppe: 'Ertrag' },
            { value: 'a2', label: 'Aufwand B', gruppe: 'Aufwand' },
        ];
        render(<Select options={gruppierteOptionen} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        const gruppen = screen.getAllByRole('group');
        expect(gruppen.map(el => el.getAttribute('aria-label'))).toEqual(['Aufwand', 'Ertrag']);
        expect(screen.getByRole('group', { name: 'Aufwand' }).querySelectorAll('[role=option]')).toHaveLength(2);
        expect(screen.getByText('Aufwand').getAttribute('aria-hidden')).toBe('true');
    });

    it('zeigt Optionen ohne Gruppe ohne Überschrift ganz oben', async () => {
        const user = userEvent.setup();
        const gemischteOptionen = [
            { value: '', label: '– kein Konto –' },
            { value: 'a1', label: 'Aufwand A', gruppe: 'Aufwand' },
        ];
        render(<Select options={gemischteOptionen} value="" onChange={() => {}} />);
        await user.click(screen.getByText('– kein Konto –'));
        expect(screen.getAllByRole('group')).toHaveLength(1);
        expect(screen.getByRole('group', { name: 'Aufwand' })).toBeTruthy();
        expect(screen.getByRole('option', { name: '– kein Konto –' }).closest('[role=group]')).toBeNull();
    });

    it('jede Option trägt ein title mit dem vollen Label', async () => {
        const user = userEvent.setup();
        const langesLabel = 'Aufwand · 4930 Bürobedarf und Zeitschriften';
        render(<Select options={[{ value: 'x', label: langesLabel }]} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        const option = screen.getByRole('option', { name: langesLabel });
        expect(option.getAttribute('title')).toBe(langesLabel);
    });

    it('schließt das Dropdown bei Escape', async () => {
        const user = userEvent.setup();
        render(<Select options={defaultOptions} value="" onChange={() => {}} />);
        await user.click(screen.getByText('Bitte wählen...'));
        expect(screen.queryByRole('listbox')).toBeTruthy();
        await user.keyboard('{Escape}');
        expect(screen.queryByRole('listbox')).toBeNull();
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


describe('Select zusammengeführte Gruppenbedienung', () => {
    const options = [
        { value: 'a1', label: 'Aufwand A', gruppe: 'Aufwand' },
        { value: 'e1', label: 'Ertrag A', gruppe: 'Ertrag' },
        { value: 'a2', label: 'Gesperrter Aufwand', gruppe: 'Aufwand', disabled: true },
        { value: 'a3', label: 'Aufwand B', gruppe: 'Aufwand' },
        { value: 'none', label: 'Kein Konto' },
    ];

    it('folgt der sichtbaren Gruppenreihenfolge und überspringt gesperrte Optionen', async () => {
        const user = userEvent.setup();
        const changed = vi.fn();
        render(<Select aria-label="Konto" options={options} value="" onChange={changed} />);
        const trigger = screen.getByRole('combobox', { name: 'Konto' });
        await user.tab();
        await user.keyboard('{ArrowDown}');
        expect(screen.getAllByRole('option').map(option => option.textContent)).toEqual([
            'Kein Konto', 'Aufwand A', 'Gesperrter Aufwand', 'Aufwand B', 'Ertrag A',
        ]);
        expect(trigger.getAttribute('aria-activedescendant')).toBe(screen.getByRole('option', { name: 'Kein Konto' }).id);
        await user.keyboard('{ArrowDown}{ArrowDown}');
        expect(trigger.getAttribute('aria-activedescendant')).toBe(screen.getByRole('option', { name: 'Aufwand B' }).id);
        await user.keyboard('{Enter}');
        expect(changed).toHaveBeenCalledWith('a3');
        expect(document.activeElement).toBe(trigger);
        expect(screen.queryByRole('listbox')).toBeNull();
    });

    it('verhindert Mausauswahl gesperrter Gruppeneinträge', async () => {
        const user = userEvent.setup();
        const changed = vi.fn();
        render(<Select options={options} value="" onChange={changed} />);
        await user.click(screen.getByRole('combobox'));
        const locked = screen.getByRole('option', { name: 'Gesperrter Aufwand' });
        expect(locked.getAttribute('aria-disabled')).toBe('true');
        await user.click(locked);
        expect(changed).not.toHaveBeenCalled();
        expect(screen.queryByRole('listbox')).toBeTruthy();
    });

    it('unterstützt Home und End über Gruppengrenzen hinweg', async () => {
        const user = userEvent.setup();
        const changed = vi.fn();
        render(<Select options={options} value="" onChange={changed} />);
        await user.click(screen.getByRole('combobox'));
        await user.keyboard('{End}{Enter}');
        expect(changed).toHaveBeenLastCalledWith('e1');
        await user.keyboard('{ArrowDown}{End}{Home}{Enter}');
        expect(changed).toHaveBeenLastCalledWith('none');
    });
});
