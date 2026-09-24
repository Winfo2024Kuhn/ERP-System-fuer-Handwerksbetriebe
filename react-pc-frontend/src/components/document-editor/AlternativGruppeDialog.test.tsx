/**
 * Vitest-Suite fuer AlternativGruppeDialog.
 *
 * Der Dialog ist der einzige Weg, eine Entweder-Oder-Gruppe anzulegen. Getestet
 * wird deshalb vor allem, dass er nur gueltige Gruppen herauslaesst: mindestens
 * zwei Varianten, eine Ueberschrift und keine Leistung aus einem fremden
 * Container.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AlternativGruppeDialog } from './AlternativGruppeDialog';
import type { DocBlock } from './types';

const svc = (id: string, title: string, extra: Partial<DocBlock> = {}): DocBlock => ({
    id, type: 'SERVICE', title, quantity: 1, unit: 'Stk', price: 100, ...extra,
});

const props = {
    onSpeichern: vi.fn(),
    onAufloesen: vi.fn(),
    onClose: vi.fn(),
};

describe('AlternativGruppeDialog', () => {
    it('zeigt die Leistungen des Containers und haelt den Anker fest angehakt', () => {
        render(<AlternativGruppeDialog {...props}
            blocks={[svc('a', 'Parkett Eiche'), svc('b', 'Vinyl grau')]} ankerId="a" />);

        const anker = screen.getByRole('checkbox', { name: /Parkett Eiche/ });
        expect(anker).toBeChecked();
        expect(anker).toBeDisabled();
        expect(screen.getByRole('checkbox', { name: /Vinyl grau/ })).not.toBeChecked();
    });

    it('sperrt "Uebernehmen", solange nur der Anker gewaehlt ist', () => {
        render(<AlternativGruppeDialog {...props}
            blocks={[svc('a', 'Parkett Eiche'), svc('b', 'Vinyl grau')]} ankerId="a" />);

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });

    it('meldet Auswahl und Ueberschrift, sobald zwei Varianten angehakt sind', () => {
        const onSpeichern = vi.fn();
        render(<AlternativGruppeDialog {...props} onSpeichern={onSpeichern}
            blocks={[svc('a', 'Parkett Eiche'), svc('b', 'Vinyl grau')]} ankerId="a" />);

        fireEvent.click(screen.getByRole('checkbox', { name: /Vinyl grau/ }));
        fireEvent.change(screen.getByLabelText('Überschrift'), { target: { value: 'Bodenbelag' } });
        fireEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onSpeichern).toHaveBeenCalledWith(['a', 'b'], 'Bodenbelag', null);
    });

    it('meldet die bisherige Gruppe mit, damit keine gleichnamige fremde zerfaellt', () => {
        const onSpeichern = vi.fn();
        render(<AlternativGruppeDialog {...props} onSpeichern={onSpeichern}
            blocks={[
                svc('a', 'Parkett Eiche', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('b', 'Vinyl grau', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('c', 'Laminat hell'),
            ]}
            ankerId="a" />);

        fireEvent.click(screen.getByRole('checkbox', { name: /Laminat hell/ }));
        fireEvent.click(screen.getByRole('button', { name: /Übernehmen/ }));

        expect(onSpeichern).toHaveBeenCalledWith(['a', 'b', 'c'], 'Bodenbelag', 'Bodenbelag');
    });

    it('verweigert einen Namen, den es im Dokument schon gibt', () => {
        render(<AlternativGruppeDialog {...props}
            blocks={[
                svc('a', 'Parkett Eiche', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('b', 'Vinyl grau', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('c', 'Wandfarbe weiß'),
                svc('d', 'Wandfarbe grau'),
            ]}
            ankerId="c" />);

        fireEvent.click(screen.getByRole('checkbox', { name: /Wandfarbe grau/ }));
        fireEvent.change(screen.getByLabelText('Überschrift'), { target: { value: 'Bodenbelag' } });

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
        expect(screen.getByText(/gibt es in diesem Dokument schon/)).toBeInTheDocument();
    });

    it('laesst den eigenen Namen beim Bearbeiten zu', () => {
        render(<AlternativGruppeDialog {...props}
            blocks={[
                svc('a', 'Parkett Eiche', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('b', 'Vinyl grau', { optional: true, alternativGruppe: 'Bodenbelag' }),
            ]}
            ankerId="a" />);

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeEnabled();
    });

    it('speichert nicht mit leerer Ueberschrift', () => {
        const onSpeichern = vi.fn();
        render(<AlternativGruppeDialog {...props} onSpeichern={onSpeichern}
            blocks={[svc('a', 'Parkett Eiche'), svc('b', 'Vinyl grau')]} ankerId="a" />);

        fireEvent.click(screen.getByRole('checkbox', { name: /Vinyl grau/ }));
        fireEvent.change(screen.getByLabelText('Überschrift'), { target: { value: '   ' } });

        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });

    it('bietet fuer eine bestehende Gruppe Name, Haken und das Aufloesen an', () => {
        const onAufloesen = vi.fn();
        render(<AlternativGruppeDialog {...props} onAufloesen={onAufloesen}
            blocks={[
                svc('a', 'Parkett Eiche', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('b', 'Vinyl grau', { optional: true, alternativGruppe: 'Bodenbelag' }),
                svc('c', 'Sockelleiste'),
            ]}
            ankerId="a" />);

        expect(screen.getByLabelText('Überschrift')).toHaveValue('Bodenbelag');
        expect(screen.getByRole('checkbox', { name: /Vinyl grau/ })).toBeChecked();
        expect(screen.getByRole('checkbox', { name: /Sockelleiste/ })).not.toBeChecked();

        fireEvent.click(screen.getByRole('button', { name: /Auswahl auflösen/ }));
        expect(onAufloesen).toHaveBeenCalledWith('Bodenbelag');
    });

    it('bietet im Bauabschnitt nur dessen Leistungen an', () => {
        render(<AlternativGruppeDialog {...props}
            blocks={[
                svc('root1', 'Baustelle einrichten'),
                { id: 'sec', type: 'SECTION_HEADER', children: [svc('k1', 'Parkett Eiche'), svc('k2', 'Vinyl grau')] },
            ]}
            ankerId="k1" />);

        expect(screen.getByRole('checkbox', { name: /Vinyl grau/ })).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: /Baustelle einrichten/ })).not.toBeInTheDocument();
    });

    it('erklaert den leeren Fall, wenn es keine zweite Leistung gibt', () => {
        render(<AlternativGruppeDialog {...props} blocks={[svc('a', 'Parkett Eiche')]} ankerId="a" />);

        expect(screen.getByText(/mindestens zwei Varianten/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Übernehmen/ })).toBeDisabled();
    });
});
