import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { LeistungenReiter, type LeistungenReiterProps } from './LeistungenReiter';
import { baueBaum } from './ordnerLogik';
import type { Leistung, Ordner } from './typen';

const o = (id: number, name: string, parentId: number | null, beschreibung = ''): Ordner => ({
    id, name, parentId, bildUrl: null, beschreibung, einheit: 'LAUFENDE_METER', einheitAnzeige: 'Laufende Meter', projektAnzahl: 0,
});
const ORDNER = [o(1, 'Metallbau', null, '<p>Meisterbetrieb</p>'), o(2, 'Geländer', 1, '<p>DIN 18065</p>'), o(3, 'Treppen', 1)];
const baum = baueBaum(ORDNER);
const LEISTUNGEN: Leistung[] = [
    { id: 10, name: 'Geländer verzinkt', beschreibung: '<p>Flachstahl</p>', preis: 149, einheit: 'LAUFENDE_METER', ordnerId: 2 },
    { id: 11, name: 'Glasbrüstung', beschreibung: '<p>VSG</p>', preis: 420.5, einheit: 'LAUFENDE_METER', ordnerId: 2 },
    { id: 12, name: 'Wangentreppe', beschreibung: '<p>Stahl</p>', preis: 4800, einheit: 'STUECK', ordnerId: 3 },
];

function zeige(teil: Partial<LeistungenReiterProps> = {}) {
    const props: LeistungenReiterProps = {
        ordner: baum.byId.get(2)!, baum, leistungen: LEISTUNGEN, suchtext: '', onSucheLeeren: vi.fn(), oeffnen: null,
        onSpeichern: vi.fn(async (l) => ({ ...l, id: l.id ?? 99 } as Leistung)), onLoeschen: vi.fn(), onVerschieben: vi.fn(),
        onZiehenStart: vi.fn(), onZiehenEnde: vi.fn(), onUngespeichert: vi.fn(), ...teil,
    };
    const ergebnis = render(<ToastProvider><LeistungenReiter {...props} /></ToastProvider>);
    return { props, ...ergebnis };
}
const formular = () => screen.getByRole('region', { name: 'Leistung bearbeiten' });

describe('LeistungenReiter', () => {
    it('legt eine neue Leistung mit Starttext aus der Ordnerkette an – nach vollständiger Prüfung', async () => {
        const { props } = zeige();
        await userEvent.click(screen.getByRole('button', { name: 'Neue Leistung' }));
        expect(within(formular()).getByText('Meisterbetrieb')).toBeInTheDocument();
        expect(within(formular()).getByText('DIN 18065')).toBeInTheDocument();

        await userEvent.click(within(formular()).getByRole('button', { name: 'Leistung anlegen' }));
        expect(within(formular()).getByText(/Bitte geben Sie der Leistung einen Namen\./)).toBeInTheDocument();
        expect(props.onSpeichern).not.toHaveBeenCalled();

        await userEvent.type(within(formular()).getByLabelText('Name *'), 'Handlauf Eiche');
        await userEvent.type(within(formular()).getByLabelText('Preis in € *'), '89,5');
        await userEvent.click(within(formular()).getByRole('button', { name: 'Leistung anlegen' }));
        expect(props.onSpeichern).toHaveBeenCalledWith(expect.objectContaining({
            id: undefined, name: 'Handlauf Eiche', preis: 89.5, einheit: 'LAUFENDE_METER', ordnerId: 2,
        }));
        expect(await screen.findByText('Leistung „Handlauf Eiche“ angelegt.')).toBeInTheDocument();
    });

    it('lehnt einen unvollständigen Preis ab', async () => {
        const { props } = zeige();
        await userEvent.click(screen.getByRole('button', { name: 'Neue Leistung' }));
        await userEvent.type(within(formular()).getByLabelText('Name *'), 'Handlauf');
        await userEvent.type(within(formular()).getByLabelText('Preis in € *'), '12,');
        await userEvent.click(within(formular()).getByRole('button', { name: 'Leistung anlegen' }));
        expect(props.onSpeichern).not.toHaveBeenCalled();
        expect(screen.getAllByText(/als vollständige Zahl mit Dezimalkomma/).length).toBeGreaterThan(0);
    });

    it('bearbeitet eine Leistung, meldet „ungespeichert“ und zeigt Fehler des Servers', async () => {
        const onUngespeichert = vi.fn();
        const onSpeichern = vi.fn().mockRejectedValue(new Error('Leistungen können nur in einem Ordner ohne Unterordner liegen.'));
        zeige({ onUngespeichert, onSpeichern });
        await userEvent.click(screen.getByRole('button', { name: /^Glasbrüstung\s*420,50/ }));
        expect(within(formular()).getByLabelText('Preis in € *')).toHaveValue('420,5');
        await userEvent.type(within(formular()).getByLabelText('Name *'), ' VSG');
        expect(onUngespeichert).toHaveBeenLastCalledWith(true);
        expect(within(formular()).getByText('Nicht gespeichert')).toBeInTheDocument();
        await userEvent.click(within(formular()).getByRole('button', { name: 'Speichern' }));
        expect(await within(formular()).findByText('Leistungen können nur in einem Ordner ohne Unterordner liegen.')).toBeInTheDocument();
        await userEvent.click(within(formular()).getByRole('button', { name: /Verwerfen/ }));
        expect(screen.queryByRole('textbox', { name: 'Name *' })).not.toBeInTheDocument();
        expect(onUngespeichert).toHaveBeenLastCalledWith(false);
    });

    it('bietet je Leistung „Verschieben nach …“ und „Löschen“ an und lässt sie ziehen', async () => {
        const { props } = zeige();
        await userEvent.click(screen.getByRole('button', { name: '„Glasbrüstung“ verschieben nach …' }));
        expect(props.onVerschieben).toHaveBeenCalledWith(LEISTUNGEN[1]);
        await userEvent.click(screen.getByRole('button', { name: '„Glasbrüstung“ löschen' }));
        expect(props.onLoeschen).toHaveBeenCalledWith(LEISTUNGEN[1]);
        await userEvent.click(screen.getByRole('button', { name: /^Geländer verzinkt/ }));
        await userEvent.click(within(formular()).getByRole('button', { name: /Leistung löschen/ }));
        expect(props.onLoeschen).toHaveBeenCalledWith(LEISTUNGEN[0]);

        const eintrag = screen.getAllByRole('listitem')[0];
        const ereignis = new Event('dragstart', { bubbles: true });
        Object.assign(ereignis, { dataTransfer: { setData: vi.fn(), effectAllowed: '' } });
        eintrag.dispatchEvent(ereignis);
        expect(props.onZiehenStart).toHaveBeenCalledWith({ art: 'leistung', id: 10 });
        eintrag.dispatchEvent(new Event('dragend', { bubbles: true }));
        expect(props.onZiehenEnde).toHaveBeenCalled();
    });

    it('filtert nach dem Suchtext und öffnet eine Leistung aus der Suche', async () => {
        const onSucheLeeren = vi.fn();
        const { rerender, props } = zeige({ suchtext: 'glas', onSucheLeeren });
        expect(screen.getByText('Gefiltert nach „glas“')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Geländer verzinkt/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Filter löschen' }));
        expect(onSucheLeeren).toHaveBeenCalled();
        rerender(<ToastProvider><LeistungenReiter {...props} suchtext="xyz" oeffnen={{ id: 11, zaehler: 1 }} /></ToastProvider>);
        expect(screen.getByText('Keine Leistung in diesem Ordner passt zur Suche.')).toBeInTheDocument();
        await waitFor(() => expect(within(formular()).getByLabelText('Name *')).toHaveValue('Glasbrüstung'));
    });

    it('sperrt „Neue Leistung“ in Ordnern mit Unterordnern und zeigt Leistungen der Unterordner mit Pfad', () => {
        zeige({ ordner: baum.byId.get(1)! });
        expect(screen.getByRole('button', { name: 'Neue Leistung' })).toBeDisabled();
        expect(screen.getByText(/Neue Leistungen kommen in den untersten Ordner/)).toBeInTheDocument();
        expect(screen.getByText(/4\.800,00 € \/ Stk · Treppen/)).toBeInTheDocument();
    });

    it('erklärt einen leeren Ordner', () => {
        zeige({ ordner: o(5, 'Tore', null), baum: baueBaum([...ORDNER, o(5, 'Tore', null)]) });
        expect(screen.getByText('In diesem Ordner gibt es noch keine Leistungen.')).toBeInTheDocument();
        expect(screen.getByText('Wählen Sie links eine Leistung aus oder legen Sie eine neue an.')).toBeInTheDocument();
    });
});
