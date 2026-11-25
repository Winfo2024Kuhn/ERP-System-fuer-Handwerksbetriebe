import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LieferantAnsprechpartnerDialog } from './LieferantAnsprechpartnerDialog';
import type { LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

/** Dummy-Daten (DSGVO): Max Mustermann. */
const MAX: LieferantAnsprechpartner = {
    id: 1, anrede: 'HERR', vorname: 'Max', nachname: 'Mustermann', aufgabe: 'Außendienst',
    telefon: null, mobiltelefon: '0171 1234567', email: 'max@muster.de', notiz: null,
};

function rendern(props: Partial<Parameters<typeof LieferantAnsprechpartnerDialog>[0]> = {}) {
    const onSpeichern = vi.fn().mockResolvedValue(true);
    const onSchliessen = vi.fn();
    render(
        <LieferantAnsprechpartnerDialog
            offen
            ansprechpartner={null}
            speichert={false}
            onSpeichern={onSpeichern}
            onSchliessen={onSchliessen}
            {...props}
        />
    );
    return { onSpeichern, onSchliessen };
}

describe('LieferantAnsprechpartnerDialog', () => {
    it('zeigt alle Felder in Ausfüllreihenfolge und markiert den Nachnamen als Pflicht', () => {
        rendern();
        const dialog = screen.getByRole('dialog', { name: 'Ansprechpartner hinzufügen' });
        const d = within(dialog);
        const felder = ['Anrede', 'Vorname', 'Nachname *', 'Aufgabe', 'Telefon', 'Handy', 'E-Mail', 'Notiz'].map(name => d.getByLabelText(name));
        for (let i = 1; i < felder.length; i++) {
            // Jedes Feld steht im DOM hinter dem vorigen.
            expect(felder[i - 1].compareDocumentPosition(felder[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        }
        expect(d.getByLabelText('Nachname *')).toBeRequired();
        expect(d.getByLabelText('Aufgabe')).toHaveAttribute('placeholder', 'z. B. Außendienst, Innendienst, Buchhaltung');
        expect(d.getByLabelText('Nachname *')).toHaveAttribute('maxLength', '255');
    });

    it('sperrt Speichern mit sichtbarem Grund, bis ein Nachname da ist', async () => {
        const user = userEvent.setup();
        const { onSpeichern, onSchliessen } = rendern();
        const speichern = screen.getByRole('button', { name: 'Speichern' });
        expect(speichern).toBeDisabled();
        expect(speichern).toHaveAccessibleDescription('Bitte einen Nachnamen eintragen.');

        await user.type(screen.getByLabelText('Nachname *'), 'Mustermann');
        expect(speichern).toBeEnabled();
        await user.click(speichern);

        expect(onSpeichern).toHaveBeenCalledWith(expect.objectContaining({ nachname: 'Mustermann', email: '' }));
        expect(onSchliessen).toHaveBeenCalled();
    });

    it('prüft das E-Mail-Format vor dem Speichern und erklärt es am Feld', async () => {
        const user = userEvent.setup();
        rendern();
        await user.type(screen.getByLabelText('Nachname *'), 'Mustermann');
        const email = screen.getByLabelText('E-Mail');
        await user.type(email, 'max@muster');
        await user.tab();

        const meldung = 'Die E-Mail-Adresse sieht nicht richtig aus (Beispiel: name@firma.de).';
        expect(email).toHaveAttribute('aria-invalid', 'true');
        expect(email).toHaveAccessibleDescription(meldung);
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await user.type(email, '.de');
        expect(email).not.toHaveAttribute('aria-invalid');
        expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled();
    });

    it('füllt beim Bearbeiten die Felder vor und bleibt bei Fehlschlag offen', async () => {
        const user = userEvent.setup();
        const onSpeichern = vi.fn().mockResolvedValue(false);
        const { onSchliessen } = rendern({ ansprechpartner: MAX, onSpeichern });
        const dialog = screen.getByRole('dialog', { name: 'Ansprechpartner bearbeiten' });
        expect(within(dialog).getByLabelText('Vorname')).toHaveValue('Max');
        expect(within(dialog).getByLabelText('Handy')).toHaveValue('0171 1234567');

        await user.click(within(dialog).getByRole('button', { name: 'Speichern' }));
        expect(onSpeichern).toHaveBeenCalledWith(expect.objectContaining({ anrede: 'HERR', nachname: 'Mustermann' }));
        expect(onSchliessen).not.toHaveBeenCalled();
    });

    it('sperrt während des Speicherns beide Knöpfe', () => {
        rendern({ ansprechpartner: MAX, speichert: true });
        expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Speichert/ })).toBeDisabled();
    });
});
