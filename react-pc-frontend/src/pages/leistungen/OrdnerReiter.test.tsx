import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { OrdnerReiter } from './OrdnerReiter';
import type { Ordner } from './typen';

vi.mock('../../components/TiptapEditor', () => ({
    TiptapEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
        <textarea aria-label="Beschreibung bearbeiten" value={value} onChange={(e) => onChange(e.target.value)} />
    ),
}));

const GELAENDER: Ordner = {
    id: 2, name: 'Geländer', parentId: 1, bildUrl: null, beschreibung: '<p>Alt</p>',
    einheit: 'LAUFENDE_METER', einheitAnzeige: 'Laufende Meter', projektAnzahl: 3,
};

function zeige(onSpeichern = vi.fn().mockResolvedValue(undefined), ordner = GELAENDER) {
    const onUngespeichert = vi.fn();
    const ansicht = render(<ToastProvider><OrdnerReiter ordner={ordner} onSpeichern={onSpeichern} onUngespeichert={onUngespeichert} /></ToastProvider>);
    return { onSpeichern, onUngespeichert, ansicht };
}

describe('OrdnerReiter', () => {
    it('speichert geänderten Namen und Beschreibung', async () => {
        const { onSpeichern, onUngespeichert } = zeige();
        expect(screen.getByText('Alles gespeichert')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Mehr zu: Einheit des Ordners' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Mehr zu: Beschreibung des Ordners' })).toBeInTheDocument();

        const name = screen.getByLabelText('Name *');
        await userEvent.clear(name);
        await userEvent.type(name, '  Edelstahlgeländer ');
        await userEvent.type(screen.getByLabelText('Beschreibung bearbeiten'), ' neu');
        expect(screen.getByText('Nicht gespeichert')).toBeInTheDocument();
        expect(onUngespeichert).toHaveBeenLastCalledWith(true);

        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(onSpeichern).toHaveBeenCalledWith({
            name: 'Edelstahlgeländer', einheit: 'LAUFENDE_METER', beschreibung: '<p>Alt</p> neu', bild: null, bildEntfernen: false,
        });
        expect(await screen.findByText('Ordner „Edelstahlgeländer“ gespeichert.')).toBeInTheDocument();
        expect(screen.getByText('Alles gespeichert')).toBeInTheDocument();
    });

    it('leerer Name wird abgelehnt, ohne zu speichern', async () => {
        const { onSpeichern } = zeige();
        await userEvent.clear(screen.getByLabelText('Name *'));
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(screen.getByText('Bitte geben Sie dem Ordner einen Namen.', { selector: 'p[role="alert"]' })).toBeInTheDocument();
        expect(onSpeichern).not.toHaveBeenCalled();
    });

    it('zeigt Fehler vom Server und „Abbrechen“ stellt den alten Stand her', async () => {
        zeige(vi.fn().mockRejectedValueOnce(new Error('Diesen Ordner gibt es nicht mehr.')).mockRejectedValueOnce('kaputt'));
        const name = screen.getByLabelText('Name *');
        await userEvent.type(name, 'x');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Diesen Ordner gibt es nicht mehr.', { selector: 'p[role="alert"]' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Der Ordner konnte nicht gespeichert werden.', { selector: 'p[role="alert"]' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(name).toHaveValue('Geländer');
        expect(document.querySelector('p[role="alert"]')).toBeNull();
    });

    it('übernimmt Änderungen von außen, solange nichts offen ist', () => {
        const onSpeichern = vi.fn();
        const { ansicht, onUngespeichert } = zeige(onSpeichern);
        ansicht.rerender(<ToastProvider><OrdnerReiter ordner={{ ...GELAENDER, name: 'Treppengeländer' }} onSpeichern={onSpeichern} onUngespeichert={onUngespeichert} /></ToastProvider>);
        expect(screen.getByLabelText('Name *')).toHaveValue('Treppengeländer');
        expect(screen.getByText('3 Projekte', { exact: false })).toBeInTheDocument();
    });
});
