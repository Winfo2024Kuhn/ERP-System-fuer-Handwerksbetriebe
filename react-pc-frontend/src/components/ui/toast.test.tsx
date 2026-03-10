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
