/**
 * Bearbeiten-Dialog der Textbausteine mit echtem Tiptap-Editor.
 * DSGVO: nur Dummy-Daten.
 */
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { Editor } from '@tiptap/core';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import { TextbausteinBearbeitenDialog } from './TextbausteinBearbeitenDialog';
import { TextbausteinFehler } from './textbausteinApi';
import { entwurfAus, neuerEntwurf } from './textbausteinEntwurf';
import { TEST_GRUPPEN, TEST_KATALOG, testBaustein } from './__tests__/testdaten';
import type { TextbausteinEntwurf } from './typen';

const VORHANDEN = testBaustein({
    id: 9,
    name: 'Anschreiben',
    html: '<p>Guten Tag {{Kundenname}}</p>',
    dokumenttypen: ['Angebot'],
});

function zeige(entwurf: TextbausteinEntwurf, optionen: { speichern?: ReturnType<typeof vi.fn>; loeschen?: ReturnType<typeof vi.fn> } = {}) {
    const onSpeichern = optionen.speichern ?? vi.fn().mockResolvedValue(VORHANDEN);
    const onLoeschen = optionen.loeschen ?? vi.fn().mockResolvedValue(true);
    const onSchliessen = vi.fn();
    render(
        <ToastProvider>
            <ConfirmProvider>
                <TextbausteinBearbeitenDialog
                    entwurf={entwurf}
                    katalog={TEST_KATALOG}
                    gruppen={TEST_GRUPPEN}
                    onSpeichern={onSpeichern}
                    onLoeschen={onLoeschen}
                    onSchliessen={onSchliessen}
                />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onSpeichern, onLoeschen, onSchliessen };
}

const dialog = () => screen.getByRole('dialog', { name: /Textbaustein/ });

describe('TextbausteinBearbeitenDialog', () => {
    it('zeigt Platzhalter im Editor als Chip mit Beispiel', async () => {
        zeige(entwurfAus(VORHANDEN));
        await waitFor(() => expect(dialog().querySelector('.ProseMirror [data-platzhalter-chip="{{Kundenname}}"]')).not.toBeNull());
        expect(dialog().querySelector('.ProseMirror [data-chip-beispiel]')?.textContent).toBe('Max Mustermann');
    });

    it('verlangt einen Namen, bevor gespeichert wird', async () => {
        const { onSpeichern } = zeige(neuerEntwurf());
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        expect(await within(dialog()).findByText('Bitte geben Sie einen Namen ein.')).toBeInTheDocument();
        expect(screen.getByLabelText(/Name/)).toHaveAttribute('aria-invalid', 'true');
        expect(onSpeichern).not.toHaveBeenCalled();
    });

    it('schickt typ, beschreibung und sortOrder unverändert und die Kürzel statt der Beispiele', async () => {
        const { onSpeichern, onSchliessen } = zeige(entwurfAus(VORHANDEN));
        fireEvent.change(screen.getByLabelText(/Name/), { target: { value: 'Anschreiben neu' } });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(onSpeichern).toHaveBeenCalledTimes(1));
        expect(onSpeichern).toHaveBeenCalledWith(9, {
            name: 'Anschreiben neu',
            typ: 'VORTEXT',
            beschreibung: 'Einleitung',
            sortOrder: 3,
            html: '<p>Guten Tag {{Kundenname}}</p>',
            placeholders: ['{{Kundenname}}'],
            dokumenttypen: ['Angebot'],
        });
        await waitFor(() => expect(onSchliessen).toHaveBeenCalled());
    });

    it('schaltet eine ganze Gruppe an und wieder aus', async () => {
        const { onSpeichern } = zeige(entwurfAus(VORHANDEN));
        const rechnungen = within(dialog()).getByRole('group', { name: 'Rechnungen' });
        fireEvent.click(within(rechnungen).getByRole('button', { name: 'Ganze Gruppe „Rechnungen“ einschalten' }));
        within(rechnungen).getAllByRole('switch').forEach((schalter) => expect(schalter).toHaveAttribute('aria-checked', 'true'));
        fireEvent.click(within(rechnungen).getByRole('button', { name: 'Ganze Gruppe „Rechnungen“ ausschalten' }));
        within(rechnungen).getAllByRole('switch').forEach((schalter) => expect(schalter).toHaveAttribute('aria-checked', 'false'));
        fireEvent.click(within(rechnungen).getByRole('switch', { name: 'Teilrechnung' }));
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(onSpeichern).toHaveBeenCalled());
        expect(onSpeichern.mock.calls[0][1].dokumenttypen).toEqual(['Angebot', 'Teilrechnung']);
    });

    it('fügt einen Platzhalter per Klick ein und speichert sein Kürzel', async () => {
        const { onSpeichern } = zeige({ ...neuerEntwurf(), name: 'Gruß', html: '<p>Hallo</p>' });
        const knopf = within(dialog()).getByRole('button', { name: /Dokumentnummer/ });
        await waitFor(() => expect(knopf).toBeEnabled());
        fireEvent.click(knopf);
        await waitFor(() => expect(dialog().querySelector('.ProseMirror [data-platzhalter-chip="{{DOKUMENTNUMMER}}"]')).not.toBeNull());
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(onSpeichern).toHaveBeenCalled());
        expect(onSpeichern.mock.calls[0][1].html).toContain('{{DOKUMENTNUMMER}}');
        expect(onSpeichern.mock.calls[0][1].placeholders).toEqual(['{{DOKUMENTNUMMER}}']);
        expect(onSpeichern.mock.calls[0][1].typ).toBe('FREITEXT');
    });

    it('zeigt die Meldung des Servers, wenn Speichern abgelehnt wird', async () => {
        const speichern = vi.fn().mockRejectedValue(new TextbausteinFehler('Unbekannte Dokumentart: Lieferschein'));
        const { onSchliessen } = zeige(entwurfAus(VORHANDEN), { speichern });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        expect(await screen.findByText('Unbekannte Dokumentart: Lieferschein')).toBeInTheDocument();
        expect(onSchliessen).not.toHaveBeenCalled();
        expect(within(dialog()).getByRole('button', { name: 'Speichern' })).toBeEnabled();
    });

    it('markiert das Namensfeld, wenn der Server den Namen ablehnt', async () => {
        const speichern = vi.fn().mockRejectedValue(new TextbausteinFehler('Der Name ist zu lang.', ['name']));
        zeige(entwurfAus(VORHANDEN), { speichern });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Speichern' }));
        await waitFor(() => expect(screen.getByLabelText(/Name/)).toHaveAttribute('aria-invalid', 'true'));
    });

    it('löscht erst nach Rückfrage', async () => {
        const { onLoeschen, onSchliessen } = zeige(entwurfAus(VORHANDEN));
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Löschen' }));
        const frage = await screen.findByRole('dialog', { name: 'Textbaustein löschen?' });
        expect(frage).toHaveTextContent('„Anschreiben“ wird endgültig gelöscht.');
        expect(frage).toHaveTextContent('Bereits erstellte Dokumente behalten ihren Text.');
        fireEvent.click(within(frage).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Textbaustein löschen?' })).toBeNull());
        expect(onLoeschen).not.toHaveBeenCalled();

        fireEvent.click(within(dialog()).getByRole('button', { name: 'Löschen' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Textbaustein löschen?' })).getByRole('button', { name: 'Löschen' }));
        await waitFor(() => expect(onLoeschen).toHaveBeenCalledWith(9));
        await waitFor(() => expect(onSchliessen).toHaveBeenCalled());
    });

    it('bietet beim neuen Textbaustein kein Löschen an', () => {
        zeige(neuerEntwurf());
        expect(within(dialog()).queryByRole('button', { name: 'Löschen' })).toBeNull();
        expect(within(dialog()).getByRole('heading', { name: 'Neuer Textbaustein' })).toBeInTheDocument();
    });

    it('fragt beim Schließen nach, wenn etwas geändert wurde', async () => {
        const { onSchliessen } = zeige(entwurfAus(VORHANDEN));
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        expect(onSchliessen).toHaveBeenCalledTimes(1);

        onSchliessen.mockClear();
        fireEvent.change(screen.getByLabelText(/Name/), { target: { value: 'Geändert' } });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        const frage = await screen.findByRole('dialog', { name: 'Änderungen verwerfen?' });
        fireEvent.click(within(frage).getByRole('button', { name: 'Weiter bearbeiten' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Änderungen verwerfen?' })).toBeNull());
        expect(onSchliessen).not.toHaveBeenCalled();
    });

    it('fragt nach Tippen und Rückgängig nicht nach, auch wenn Tiptap das HTML umformt', async () => {
        // <b> formt Tiptap zu <strong> um – das allein ist keine Änderung des Nutzers.
        const { onSchliessen } = zeige(entwurfAus(testBaustein({ id: 3, name: 'Fett', html: '<p>Hallo <b>Welt</b> {{Kundenname}}</p>' })));
        const prosemirror = await waitFor(() => {
            const element = dialog().querySelector<HTMLElement & { editor?: Editor }>('.ProseMirror');
            expect(element?.editor).toBeTruthy();
            return element!;
        });
        const editor = prosemirror.editor!;
        act(() => { editor.commands.insertContentAt(1, 'X'); });
        act(() => { editor.commands.undo(); });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(onSchliessen).toHaveBeenCalledTimes(1));
        expect(screen.queryByRole('dialog', { name: 'Änderungen verwerfen?' })).toBeNull();
    });

    it('fragt nach, wenn der Text wirklich geändert wurde', async () => {
        const { onSchliessen } = zeige(entwurfAus(VORHANDEN));
        const prosemirror = await waitFor(() => {
            const element = dialog().querySelector<HTMLElement & { editor?: Editor }>('.ProseMirror');
            expect(element?.editor).toBeTruthy();
            return element!;
        });
        act(() => { prosemirror.editor!.commands.insertContentAt(1, 'X'); });
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        expect(await screen.findByRole('dialog', { name: 'Änderungen verwerfen?' })).toBeInTheDocument();
        expect(onSchliessen).not.toHaveBeenCalled();
    });

    it('fragt nicht nach, wenn ohne Eingabe geschlossen wird und Tiptap das HTML umformt', async () => {
        const { onSchliessen } = zeige(entwurfAus(testBaustein({ id: 4, name: 'Fett', html: '<p><b>fett</b></p>' })));
        await waitFor(() => expect(dialog().querySelector<HTMLElement & { editor?: Editor }>('.ProseMirror')?.editor).toBeTruthy());
        expect(dialog().querySelector('.ProseMirror strong')).not.toBeNull();
        fireEvent.click(within(dialog()).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(onSchliessen).toHaveBeenCalledTimes(1));
        expect(screen.queryByRole('dialog', { name: 'Änderungen verwerfen?' })).toBeNull();
    });
});
