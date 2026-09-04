import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ToastProvider, useToast } from './toast';
import { act } from '@testing-library/react';

// Hilfskomponente zum Testen des Toast-Hooks
function TestComponent() {
    const toast = useToast();

    return (
        <div>
            <button onClick={() => toast.success('Gespeichert!')}>Success</button>
            <button onClick={() => toast.error('Fehler aufgetreten')}>Error</button>
            <button onClick={() => toast.warning('Achtung!')}>Warning</button>
            <button onClick={() => toast.info('Hinweis')}>Info</button>
        </div>
    );
}

describe('Toast', () => {
    it('zeigt Success-Toast an', async () => {
        render(
            <ToastProvider>
                <TestComponent />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Success').click();
        });
        expect(screen.getByText('Gespeichert!')).toBeInTheDocument();
    });

    it('zeigt Error-Toast an', async () => {
        render(
            <ToastProvider>
                <TestComponent />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Error').click();
        });
        expect(screen.getByText('Fehler aufgetreten')).toBeInTheDocument();
    });

    it('zeigt Warning-Toast an', async () => {
        render(
            <ToastProvider>
                <TestComponent />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Warning').click();
        });
        expect(screen.getByText('Achtung!')).toBeInTheDocument();
    });

    it('zeigt Info-Toast an', async () => {
        render(
            <ToastProvider>
                <TestComponent />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Info').click();
        });
        expect(screen.getByText('Hinweis')).toBeInTheDocument();
    });

    it('wirft Fehler wenn useToast ohne Provider verwendet wird', () => {
        function BadComponent() {
            useToast();
            return null;
        }
        expect(() => render(<BadComponent />)).toThrow('useToast must be used within a ToastProvider');
    });

    it('kann mehrere Toasts gleichzeitig anzeigen', async () => {
        render(
            <ToastProvider>
                <TestComponent />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Success').click();
            screen.getByText('Error').click();
        });
        expect(screen.getByText('Gespeichert!')).toBeInTheDocument();
        expect(screen.getByText('Fehler aufgetreten')).toBeInTheDocument();
    });
});

/**
 * Regressionsschutz fuer einen Design-Review-Befund (Abschnitt 6, Task 6b
 * Nachbesserung 1): der Toast-Container liegt fest unten rechts -- genau
 * dort, wo die Fussleiste ("Abbrechen"/"Speichern") praktisch jedes Modals im
 * Projekt sitzt. Ein Fehler-Toast beim Oeffnen von LieferantDokumentModal
 * legte sich auf 1440x900 fuenf Sekunden lang ueber genau diese Knoepfe --
 * ein Klick in ihre Mitte traf den Toast statt den Knopf.
 */
function TestKomponenteMitDialog({ dialogOffen }: { dialogOffen: boolean }) {
    const toast = useToast();
    return (
        <div>
            <button onClick={() => toast.error('Sperre konnte nicht geholt werden — bitte neu laden.')}>
                Fehler ausloesen
            </button>
            {dialogOffen && (
                <div role="dialog" aria-modal="true" aria-label="Testdialog">
                    Modal-Inhalt
                </div>
            )}
        </div>
    );
}

describe('Toast-Container - Positionierung bei offenem Dialog', () => {
    it('liegt unten rechts, wenn kein Dialog offen ist', async () => {
        render(
            <ToastProvider>
                <TestKomponenteMitDialog dialogOffen={false} />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Fehler ausloesen').click();
        });

        const container = screen.getByTestId('toast-container');
        expect(container.className).toContain('bottom-6');
        expect(container.className).not.toContain('top-6');
    });

    it('wandert nach oben rechts, solange ein Dialog offen ist', async () => {
        render(
            <ToastProvider>
                <TestKomponenteMitDialog dialogOffen={true} />
            </ToastProvider>
        );
        await act(async () => {
            screen.getByText('Fehler ausloesen').click();
        });

        const container = screen.getByTestId('toast-container');
        expect(container.className).toContain('top-6');
        expect(container.className).not.toContain('bottom-6');
    });
});
