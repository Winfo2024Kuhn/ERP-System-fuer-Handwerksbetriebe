/**
 * Vitest-Suite fuer StufenplanTabelle.
 *
 * Prueft die Anzeige bestehender Stufen, den Leerzustand, die Validierung
 * beim Hinzufuegen (Stunden ueber dem Tageslimit loesen keinen
 * onHinzufuegen-Aufruf aus, sondern eine Fehlermeldung), die
 * Rueckfrage vor dem Loeschen und die disabled-Begruendung.
 *
 * DSGVO: ausschliesslich Dummy-Daten, keine echten Namen.
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { StufenplanTabelle } from './StufenplanTabelle';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
import type { Phase } from './phasen';

const stufe = (overrides: Partial<Phase> = {}): Phase => ({
    id: 1,
    typ: 'WIEDEREINGLIEDERUNG',
    label: 'Wiedereingliederung',
    vonDatum: '2026-04-01',
    bisDatum: '2026-04-14',
    stundenProTag: 2,
    ...overrides,
});

function renderTabelle(overrides: {
    phasen?: Phase[];
    onHinzufuegen?: ReturnType<typeof vi.fn>;
    onLoeschen?: ReturnType<typeof vi.fn>;
    maxStundenProTag?: number;
    disabled?: boolean;
} = {}) {
    const onHinzufuegen = overrides.onHinzufuegen ?? vi.fn().mockResolvedValue(undefined);
    const onLoeschen = overrides.onLoeschen ?? vi.fn().mockResolvedValue(undefined);
    render(
        <ToastProvider>
            <ConfirmProvider>
                <StufenplanTabelle
                    phasen={overrides.phasen ?? []}
                    onHinzufuegen={onHinzufuegen}
                    onLoeschen={onLoeschen}
                    maxStundenProTag={overrides.maxStundenProTag ?? 4}
                    disabled={overrides.disabled}
                />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onHinzufuegen, onLoeschen };
}

/**
 * Fehlermeldungen erscheinen zweimal mit role="alert": einmal inline am Feld
 * und einmal als Toast (Fehler-Toasts sind bewusst role="alert", damit
 * Screenreader sie sofort ansagen). Hier gezielt den Inline-Text pruefen.
 */
const inlineFehler = () =>
    screen.getAllByRole('alert').find(element => !element.closest('[data-pc-toasts]'));

describe('StufenplanTabelle', () => {
    it('zeigt den Leerzustand, wenn noch kein Stufenplan hinterlegt ist', () => {
        renderTabelle();
        expect(screen.getByText('Noch kein Stufenplan hinterlegt.')).toBeInTheDocument();
    });

    it('zeigt bestehende Stufen mit Datum und Stunden pro Tag', () => {
        renderTabelle({ phasen: [stufe({ id: 1, vonDatum: '2026-04-01', bisDatum: '2026-04-14', stundenProTag: 2 })] });

        expect(screen.getByText('01.04.2026')).toBeInTheDocument();
        expect(screen.getByText('14.04.2026')).toBeInTheDocument();
        expect(screen.getByText('2 Std.')).toBeInTheDocument();
        expect(screen.queryByText('Noch kein Stufenplan hinterlegt.')).not.toBeInTheDocument();
    });

    // Nachbesserung Abschnitt 5, Befund 1 (BLOCKER): halbe Stunden muessen mit
    // deutschem Komma erscheinen ("5,5 Std."), nicht mit englischem Punkt --
    // die Handy-App macht das schon richtig mit toLocaleString('de-DE').
    it('zeigt halbe Stunden mit deutschem Komma statt englischem Punkt', () => {
        renderTabelle({ phasen: [stufe({ id: 1, vonDatum: '2026-04-01', bisDatum: null, stundenProTag: 5.5 })] });

        expect(screen.getByText('5,5 Std.')).toBeInTheDocument();
        expect(screen.queryByText('5.5 Std.')).not.toBeInTheDocument();
    });

    // Nachbesserung Abschnitt 5, Befund 1: das Tageslimit taucht nicht nur in
    // der Tabellenzeile auf, sondern auch im Platzhalter des Stunden-Feldes
    // und im Fehlertext -- beides muss ebenfalls deutsch formatiert sein.
    it('zeigt ein nicht-ganzzahliges Tageslimit im Platzhalter und im Fehlertext mit Komma', async () => {
        const user = userEvent.setup();
        renderTabelle({ maxStundenProTag: 7.5 });

        const stundenFeld = screen.getByLabelText('Stunden pro Tag');
        expect(stundenFeld.getAttribute('placeholder')).toBe('max. 7,5');

        await user.click(screen.getByText('Startdatum'));
        await user.click(screen.getByText('Heute'));
        await user.type(stundenFeld, '9');
        await user.click(screen.getByRole('button', { name: /Zeile hinzufügen/ }));

        expect(inlineFehler()).toHaveTextContent(/größer als 0 und höchstens 7,5 sein/);
    });

    it('ignoriert Phasen, die kein Stufenplan-Schritt sind', () => {
        renderTabelle({
            phasen: [
                { id: 9, typ: 'LOHNFORTZAHLUNG', label: 'Lohnfortzahlung durch den Betrieb', vonDatum: '2026-01-01', bisDatum: '2026-01-28', stundenProTag: null },
            ],
        });

        expect(screen.getByText('Noch kein Stufenplan hinterlegt.')).toBeInTheDocument();
    });

    it('lehnt Stunden ueber dem Tageslimit ab, ohne onHinzufuegen aufzurufen', async () => {
        const user = userEvent.setup();
        const { onHinzufuegen } = renderTabelle({ maxStundenProTag: 4 });

        await user.click(screen.getByText('Startdatum'));
        await user.click(screen.getByText('Heute'));
        await user.type(screen.getByLabelText('Stunden pro Tag'), '6');
        await user.click(screen.getByRole('button', { name: /Zeile hinzufügen/ }));

        expect(onHinzufuegen).not.toHaveBeenCalled();
        // Inline-Fehlertext (role="alert") UND Toast zeigen dieselbe Meldung -
        // gezielt auf den Inline-Text pruefen, statt auf beide Treffer zu stossen.
        expect(inlineFehler()).toHaveTextContent(/größer als 0 und höchstens 4/);
    });

    it('erlaubt halbe Stunden, weil die Pruefung selbst keine Ganzzahl verlangt', async () => {
        // Regression Nachbesserung Abschnitt 2, Befund 4: der Fehlertext sagte
        // vorher "zwischen 1 und N", die Pruefung liess aber schon immer jeden
        // Wert > 0 durch. 0,5 Std. sind bei einer Wiedereingliederung ueblich
        // und muessen weiterhin funktionieren.
        const user = userEvent.setup();
        const { onHinzufuegen } = renderTabelle({ maxStundenProTag: 4 });

        await user.click(screen.getByText('Startdatum'));
        await user.click(screen.getByText('Heute'));
        await user.type(screen.getByLabelText('Stunden pro Tag'), '0,5');
        await user.click(screen.getByRole('button', { name: /Zeile hinzufügen/ }));

        await waitFor(() =>
            expect(onHinzufuegen).toHaveBeenCalledWith(
                expect.objectContaining({ bisDatum: null, stundenProTag: 0.5 }),
            ),
        );
    });

    it('verknuepft das Startdatum-Feld ueber eine ARIA-Gruppe mit seinem Label', () => {
        // Regression Nachbesserung Abschnitt 2, Befund 4: das Stundenfeld hatte
        // schon ein htmlFor, das Startdatum-Feld (DatePicker, kein natives
        // Formularfeld) nicht -- jetzt per role="group" + aria-labelledby.
        renderTabelle();
        expect(screen.getByRole('group', { name: 'ab' })).toBeInTheDocument();
    });

    it('legt eine neue Zeile an, wenn Datum und Stunden gueltig sind', async () => {
        const user = userEvent.setup();
        const { onHinzufuegen } = renderTabelle({ maxStundenProTag: 4 });

        await user.click(screen.getByText('Startdatum'));
        await user.click(screen.getByText('Heute'));
        await user.type(screen.getByLabelText('Stunden pro Tag'), '2');
        await user.click(screen.getByRole('button', { name: /Zeile hinzufügen/ }));

        await waitFor(() =>
            expect(onHinzufuegen).toHaveBeenCalledWith(
                expect.objectContaining({ bisDatum: null, stundenProTag: 2 }),
            ),
        );
    });

    it('fragt vor dem Loeschen nach und loescht erst nach Bestaetigung', async () => {
        const user = userEvent.setup();
        const { onLoeschen } = renderTabelle({ phasen: [stufe({ id: 5, vonDatum: '2026-04-01' })] });

        await user.click(screen.getByRole('button', { name: /löschen/i }));
        expect(await screen.findByText(/wirklich löschen/)).toBeInTheDocument();

        await user.click(screen.getByText('Abbrechen'));
        expect(onLoeschen).not.toHaveBeenCalled();

        await user.click(screen.getByRole('button', { name: /löschen/i }));
        await user.click(await screen.findByText('Ja, löschen'));

        await waitFor(() => expect(onLoeschen).toHaveBeenCalledWith(5));
    });

    it('deaktiviert die Aktionen mit einer Begruendung, wenn disabled gesetzt ist', () => {
        renderTabelle({ disabled: true, phasen: [stufe({ id: 1 })] });

        const hinzuButton = screen.getByRole('button', { name: /Zeile hinzufügen/ });
        expect(hinzuButton).toBeDisabled();
        expect(hinzuButton.getAttribute('title')).toBeTruthy();

        const loeschButton = screen.getByRole('button', { name: /löschen/i });
        expect(loeschButton).toBeDisabled();
        expect(loeschButton.getAttribute('title')).toBeTruthy();
    });
});
