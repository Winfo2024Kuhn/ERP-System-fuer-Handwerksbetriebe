import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { ConfirmProvider, useConfirm, type ConfirmOptions } from './confirm-dialog';

// Hilfskomponente zum Testen des Hooks
function TestComponent() {
    const confirm = useConfirm();

    const handleDelete = async () => {
        const result = await confirm({
            title: 'Löschen?',
            message: 'Diesen Eintrag wirklich löschen?',
            confirmLabel: 'Ja, löschen',
            cancelLabel: 'Nein',
            variant: 'danger',
        });
        // Ergebnis in DOM schreiben zum Testen
        document.getElementById('result')!.textContent = result ? 'confirmed' : 'cancelled';
    };

    return (
        <div>
            <button onClick={handleDelete}>Löschen</button>
            <span id="result" />
        </div>
    );
}

/** Hilfskomponente, um beliebige confirm()-Optionen (z.B. eine andere Variante) durchzureichen. */
function TestComponentMitOptionen({ optionen }: { optionen: ConfirmOptions }) {
    const confirm = useConfirm();

    const handleClick = async () => {
        const result = await confirm(optionen);
        document.getElementById('result')!.textContent = result ? 'confirmed' : 'cancelled';
    };

    return (
        <div>
            <button onClick={handleClick}>Auslösen</button>
            <span id="result" />
        </div>
    );
}

/** Wie TestComponentMitOptionen, aber mit frei waehlbarer Knopf-Beschriftung -- noetig, um zwei Ausloeser in einem Test unterscheidbar zu machen. */
function TestComponentMitLabel({ label, optionen }: { label: string; optionen: ConfirmOptions }) {
    const confirm = useConfirm();
    return <button onClick={() => { void confirm(optionen); }}>{label}</button>;
}

describe('ConfirmDialog', () => {
    it('zeigt Bestätigungsdialog an', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));
        expect(screen.getByText('Löschen?')).toBeInTheDocument();
        expect(screen.getByText('Diesen Eintrag wirklich löschen?')).toBeInTheDocument();
    });

    it('zeigt benutzerdefinierte Button-Labels', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));
        expect(screen.getByText('Ja, löschen')).toBeInTheDocument();
        expect(screen.getByText('Nein')).toBeInTheDocument();
    });

    it('gibt true zurück bei Bestätigung', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));
        await user.click(screen.getByText('Ja, löschen'));
        expect(document.getElementById('result')!.textContent).toBe('confirmed');
    });

    it('gibt false zurück bei Abbrechen', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));
        await user.click(screen.getByText('Nein'));
        expect(document.getElementById('result')!.textContent).toBe('cancelled');
    });

    it('schließt den Dialog nach Bestätigung', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));
        expect(screen.getByText('Löschen?')).toBeInTheDocument();
        await user.click(screen.getByText('Ja, löschen'));
        expect(screen.queryByText('Löschen?')).not.toBeInTheDocument();
    });

    it('traegt role="dialog"/aria-modal/aria-labelledby auf den Titel (Task 8a) -- sonst greift der Toast-Umzug bei offenem Dialog hier nicht, und Screenreader sehen keinen Dialog', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponent />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Löschen'));

        const dialog = screen.getByRole('dialog', { name: 'Löschen?' });
        expect(dialog).toHaveAttribute('aria-modal', 'true');
    });

    it('Variante "fehlschlag" zeigt ein amber-AlertTriangle-Icon und einen rose-Bestaetigungsknopf (Task 8c: "info" lieferte fuer Fehlschlaege ein irrefuehrend freundliches blaues Fragezeichen)', async () => {
        const user = userEvent.setup();
        render(
            <ConfirmProvider>
                <TestComponentMitOptionen
                    optionen={{
                        title: 'Nicht gespeichert',
                        message: 'Ihre Änderungen wurden nicht übernommen — bitte neu laden.',
                        confirmLabel: 'Neu laden',
                        cancelLabel: 'Abbrechen',
                        variant: 'fehlschlag',
                    }}
                />
            </ConfirmProvider>
        );
        await user.click(screen.getByText('Auslösen'));

        const dialog = screen.getByRole('dialog', { name: 'Nicht gespeichert' });
        const icon = dialog.querySelector('svg');
        expect(icon).not.toBeNull();
        expect(icon?.getAttribute('class')).toContain('text-amber-600');
        expect(icon?.parentElement?.className).toContain('bg-amber-100');
        expect(dialog.innerHTML).not.toContain('sky-100');
        expect(dialog.innerHTML).not.toContain('sky-600');
    });

    it('vergibt pro Dialog eine eigene ID fuer aria-labelledby, statt einer festen ID (Task 8c Nachtrag) -- sonst kollidieren zwei gleichzeitig offene Dialoge im DOM auf dieselbe ID', async () => {
        const user = userEvent.setup();
        render(
            <>
                <ConfirmProvider>
                    <TestComponentMitLabel label="Öffne A" optionen={{ title: 'Dialog A', message: 'Nachricht A' }} />
                </ConfirmProvider>
                <ConfirmProvider>
                    <TestComponentMitLabel label="Öffne B" optionen={{ title: 'Dialog B', message: 'Nachricht B' }} />
                </ConfirmProvider>
            </>
        );

        await user.click(screen.getByText('Öffne A'));
        await user.click(screen.getByText('Öffne B'));

        const dialoge = screen.getAllByRole('dialog');
        expect(dialoge).toHaveLength(2);

        const ids = dialoge.map(d => d.getAttribute('aria-labelledby'));
        expect(ids[0]).toBeTruthy();
        expect(ids[1]).toBeTruthy();
        expect(ids[0]).not.toBe(ids[1]);

        // Keine doppelte ID im Dokument -- Grundvoraussetzung fuer ein korrekt
        // aufloesbares aria-labelledby (sonst gewinnt eine ID-basierte Suche
        // immer das ERSTE Element mit dieser ID, unabhaengig davon, zu
        // welchem der beiden Dialoge es eigentlich gehoert).
        for (const id of ids) {
            expect(document.querySelectorAll(`[id="${id}"]`).length).toBe(1);
        }
    });

    it('wirft Fehler wenn useConfirm ohne Provider verwendet wird', () => {
        function BadComponent() {
            useConfirm();
            return null;
        }
        expect(() => render(<BadComponent />)).toThrow('useConfirm must be used within a ConfirmProvider');
    });
});
