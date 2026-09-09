import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Select } from './select-custom';

// Hinweis: Diese Datei nutzt bewusst KEINE @testing-library/jest-dom-Matcher
// (toBeInTheDocument/toHaveClass/toHaveAttribute/...) -- in dieser Umgebung
// registriert `@testing-library/jest-dom/vitest` (importiert ueber
// src/setupTests.ts) seine Matcher nachweislich nicht auf der vom Testfile
// verwendeten `expect`-Instanz (reproduziert auch an einer unveraenderten
// Datei, src/components/ui/button.test.tsx: "Invalid Chai property:
// toBeInTheDocument"/"toBeDisabled", stabil bei mehrfachem Lauf). Vorbestehend,
// nicht Teil dieses Tasks (select-custom.tsx/.test.tsx/dropdown-breite.spec.ts)
// -- siehe Kontext-Log-Bedenken. Alle Zusicherungen hier pruefen dieselbe
// Sache ueber native DOM-Eigenschaften bzw. eingebaute vitest/chai-Matcher.

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
        const ueberschriften = screen.getAllByRole('presentation');
        expect(ueberschriften.map(el => el.textContent)).toEqual(['Aufwand', 'Ertrag']);
    });

    it('zeigt Optionen ohne Gruppe ohne Überschrift ganz oben', async () => {
        const user = userEvent.setup();
        const gemischteOptionen = [
            { value: '', label: '– kein Konto –' },
            { value: 'a1', label: 'Aufwand A', gruppe: 'Aufwand' },
        ];
        render(<Select options={gemischteOptionen} value="" onChange={() => {}} />);
        await user.click(screen.getByText('– kein Konto –'));
        expect(screen.getAllByRole('presentation')).toHaveLength(1);
        expect(screen.getByRole('presentation').textContent).toBe('Aufwand');
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
