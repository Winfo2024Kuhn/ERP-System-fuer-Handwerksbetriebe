import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { ConfirmProvider, useConfirm } from './confirm-dialog';

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

    it('wirft Fehler wenn useConfirm ohne Provider verwendet wird', () => {
        function BadComponent() {
            useConfirm();
            return null;
        }
        expect(() => render(<BadComponent />)).toThrow('useConfirm must be used within a ConfirmProvider');
    });
});
