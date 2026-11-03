import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { DokumentDialog } from './DokumentDialog';
import { OrdnerNameDialog } from './OrdnerNameDialog';
import type { DokumentAngaben } from './typen';

function zeigeDokumentDialog(onSpeichern: (a: DokumentAngaben) => Promise<void>, start: DokumentAngaben = { name: 'scan_0042', ordnerId: 2, faelligAm: null }) {
    const onAbbrechen = vi.fn();
    render(
        <ToastProvider>
            <DokumentDialog titel="Datei hochladen" beschreibung="Datei: scan_0042.pdf" start={start} ordnerText="Kommt in: Prüfungen › Tore"
                knopfText="Hochladen" knopfSymbol={null} onAbbrechen={onAbbrechen} onSpeichern={onSpeichern} />
        </ToastProvider>,
    );
    return { onAbbrechen };
}

describe('DokumentDialog', () => {
    it('lehnt einen Namen mit Programmendung vorab ab', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(undefined);
        zeigeDokumentDialog(onSpeichern);
        const name = screen.getByLabelText('Name *');
        await user.clear(name);
        await user.type(name, 'setup.exe');
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Der Name darf nicht auf eine Programmendung wie .exe enden.');
        expect(onSpeichern).not.toHaveBeenCalled();
    });

    it('füllt den Namen vor, verlangt ihn aber als Pflichtfeld', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(undefined);
        zeigeDokumentDialog(onSpeichern);
        const name = screen.getByLabelText('Name *');
        expect(name).toHaveValue('scan_0042');
        await user.clear(name);
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Bitte einen Namen für das Dokument eingeben.');
        expect(name).toHaveAttribute('aria-invalid', 'true');
        expect(onSpeichern).not.toHaveBeenCalled();

        await user.type(name, '  Torprüfung Halle 2 – 2026 ');
        expect(screen.queryByRole('alert')).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(onSpeichern).toHaveBeenCalledWith({ name: 'Torprüfung Halle 2 – 2026', ordnerId: 2, faelligAm: null });
    });

    it('zeigt den Ordner nur als Text – ohne Auswahlfeld – und speichert den Ordner, mit dem er geöffnet wurde', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(undefined);
        zeigeDokumentDialog(onSpeichern, { name: 'Torprüfung', ordnerId: 7, faelligAm: null });
        expect(screen.getByText('Kommt in: Prüfungen › Tore')).toBeInTheDocument();
        expect(screen.queryByRole('combobox')).toBeNull();
        expect(screen.queryByText('Ordner *')).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(onSpeichern).toHaveBeenCalledWith({ name: 'Torprüfung', ordnerId: 7, faelligAm: null });
    });

    it('zeigt die Meldung des Servers im Dialog und als Toast', async () => {
        const user = userEvent.setup();
        zeigeDokumentDialog(vi.fn().mockRejectedValue(new Error('Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.')));
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(await screen.findAllByText('Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.')).toHaveLength(2);
        expect(screen.getByRole('button', { name: 'Hochladen' })).toBeEnabled();
    });

    it('nimmt ein vorhandenes Datum mit und kann es entfernen', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(undefined);
        zeigeDokumentDialog(onSpeichern, { name: 'Torprüfung', ordnerId: 1, faelligAm: '2027-10-31' });
        expect(screen.getByRole('button', { name: 'Mehr zu: Fällig am' })).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Datum entfernen' }));
        expect(screen.queryByRole('button', { name: 'Datum entfernen' })).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Hochladen' }));
        expect(onSpeichern).toHaveBeenCalledWith({ name: 'Torprüfung', ordnerId: 1, faelligAm: null });
    });

    it('erklärt „Fällig am“ im Info-Knopf', async () => {
        const user = userEvent.setup();
        zeigeDokumentDialog(vi.fn());
        await user.click(screen.getByRole('button', { name: 'Mehr zu: Fällig am' }));
        expect(screen.getByText(/Es geht keine Erinnerung per Glocke oder E-Mail raus\./)).toBeInTheDocument();
        expect(screen.getByText(/Ab 30 Tagen vor dem Datum ist das Dokument gelb markiert, danach rot\./)).toBeInTheDocument();
    });

    it('schließt über Abbrechen', async () => {
        const user = userEvent.setup();
        const { onAbbrechen } = zeigeDokumentDialog(vi.fn());
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onAbbrechen).toHaveBeenCalled();
    });
});

describe('OrdnerNameDialog', () => {
    function zeige(onSpeichern: (name: string) => Promise<void>) {
        render(
            <ToastProvider>
                <OrdnerNameDialog titel="Unterordner anlegen" beschreibung="Der neue Ordner kommt in „Prüfungen“." knopfText="Ordner anlegen"
                    onAbbrechen={vi.fn()} onSpeichern={onSpeichern} />
            </ToastProvider>,
        );
    }

    it('verlangt einen Namen und schickt ihn getrimmt', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(undefined);
        zeige(onSpeichern);
        await user.click(screen.getByRole('button', { name: 'Ordner anlegen' }));
        expect(screen.getByRole('alert')).toHaveTextContent('Bitte einen Namen für den Ordner eingeben.');
        await user.type(screen.getByLabelText('Name des Ordners *'), ' Tore {Enter}');
        expect(onSpeichern).toHaveBeenCalledWith('Tore');
    });

    it('zeigt die Meldung des Servers am Feld', async () => {
        const user = userEvent.setup();
        zeige(vi.fn().mockRejectedValue(new Error('Hier gibt es schon einen Ordner „Tore“.')));
        await user.type(screen.getByLabelText('Name des Ordners *'), 'Tore{Enter}');
        expect(await screen.findAllByText('Hier gibt es schon einen Ordner „Tore“.')).toHaveLength(2);
    });
});
