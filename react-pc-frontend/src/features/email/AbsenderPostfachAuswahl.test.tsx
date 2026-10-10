import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AbsenderPostfachAuswahl } from './AbsenderPostfachAuswahl';
import type { AbsenderPostfach } from './postfach';

const info: AbsenderPostfach = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', eigenes: false, hauptpostfach: true };
const max: AbsenderPostfach = { id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann', eigenes: true, hauptpostfach: false };
const rechnungen: AbsenderPostfach = { id: 9, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: false };

describe('AbsenderPostfachAuswahl – Auswahl', () => {
    it('zeigt die Postfächer mit Kennzeichnung und meldet die Wahl', async () => {
        const onChange = vi.fn();
        render(<AbsenderPostfachAuswahl id="von" modus="auswahl" postfaecher={[max, info, rechnungen]} value={7} onChange={onChange} />);
        const feld = screen.getByRole('combobox', { name: 'Senden von' });
        expect(feld).toHaveTextContent('Max Mustermann <max@musterbetrieb.example> – Ihr Postfach');
        await userEvent.click(feld);
        expect(screen.getByRole('option', { name: /Musterbetrieb <info@musterbetrieb.example> – Hauptpostfach/ })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('option', { name: 'rechnungen@musterbetrieb.example' }));
        expect(onChange).toHaveBeenCalledWith(9);
    });

    it('zeigt bei nur einem Postfach eine feste Zeile statt eines Feldes', () => {
        render(<AbsenderPostfachAuswahl id="von" modus="auswahl" postfaecher={[info]} value={3} onChange={vi.fn()} />);
        expect(screen.getByTestId('absender-fest')).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>');
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });

    it('erklärt Ladefehler und fehlende Postfächer', () => {
        const { rerender } = render(<AbsenderPostfachAuswahl id="von" modus="auswahl" postfaecher={[]} value={null} onChange={vi.fn()} ladeFehler inline />);
        expect(screen.getByText(/konnten nicht geladen werden/)).toHaveClass('col-span-2', 'text-rose-700');
        rerender(<AbsenderPostfachAuswahl id="von" modus="auswahl" postfaecher={[]} value={null} onChange={vi.fn()} />);
        expect(screen.getByText(/Noch kein Postfach eingerichtet/)).not.toHaveClass('col-span-2');
        expect(screen.getByRole('combobox', { name: 'Senden von' })).toHaveAttribute('aria-describedby', 'von-hinweis');
        rerender(<AbsenderPostfachAuswahl id="von" modus="auswahl" postfaecher={[]} value={null} onChange={vi.fn()} laedt />);
        expect(screen.getByRole('combobox', { name: 'Senden von' })).toHaveTextContent('Postfächer werden geladen');
    });
});

describe('AbsenderPostfachAuswahl – fest', () => {
    it('zeigt das Antwort-Postfach als Textzeile mit Hinweis', () => {
        render(<AbsenderPostfachAuswahl id="von" modus="fest" postfach={{ id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' }}
            hinweis="Antworten gehen über das Postfach raus, in dem die Mail ankam." inline />);
        const zeile = screen.getByTestId('absender-fest');
        expect(zeile).toHaveTextContent('Musterbetrieb <info@musterbetrieb.example>');
        expect(zeile).toHaveAttribute('aria-describedby', 'von-hinweis');
        expect(screen.getByText(/in dem die Mail ankam/)).toHaveClass('col-span-2');
    });

    it('zeigt reine Adressen, Ladezustand und Rückfall', () => {
        const { rerender } = render(<AbsenderPostfachAuswahl id="von" modus="fest" postfach="rechnungen@musterbetrieb.example" />);
        expect(screen.getByTestId('absender-fest')).toHaveTextContent('rechnungen@musterbetrieb.example');
        expect(screen.getByTestId('absender-fest')).not.toHaveAttribute('aria-describedby');
        rerender(<AbsenderPostfachAuswahl id="von" modus="fest" postfach={undefined} />);
        expect(screen.getByTestId('absender-fest')).toHaveTextContent('Absender wird ermittelt');
        rerender(<AbsenderPostfachAuswahl id="von" modus="fest" postfach={null} />);
        expect(screen.getByTestId('absender-fest')).toHaveTextContent('Standard-Absender des Betriebs');
    });
});
