import { StrictMode, useEffect } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
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


describe('Schwebende Meldungen', () => {
    it('behält auch acht Meldungen in einer begrenzten scrollbaren Fläche erreichbar', async () => {
        render(<ToastProvider><TestComponent /></ToastProvider>);
        await act(async () => { for (let i = 0; i < 8; i++) screen.getByText('Error').click(); });
        expect(screen.getAllByRole('alert')).toHaveLength(8);
        expect(screen.getAllByRole('button', { name: 'Meldung schließen' })).toHaveLength(8);
        expect(screen.getByRole('region', { name: 'Meldungen' })).toBeInTheDocument();
    });

    it('hält die Toast-API stabil, damit Fehlermeldungen keine Formulare neu laden', async () => {
        const geladen = vi.fn();
        function Formular() {
            const toast = useToast();
            useEffect(geladen, [toast]);
            return <button onClick={() => toast.error('Prüfen')}>Prüfen</button>;
        }
        render(<ToastProvider><Formular /></ToastProvider>);
        expect(geladen).toHaveBeenCalledTimes(1);
        await act(async () => { screen.getByRole('button', { name: 'Prüfen' }).click(); });
        expect(geladen).toHaveBeenCalledTimes(1);
    });

    it('verändert auch im StrictMode keine globale Layout-Höhe', async () => {
        const { unmount } = render(<StrictMode><ToastProvider><TestComponent /></ToastProvider></StrictMode>);
        await act(async () => { screen.getByText('Error').click(); });
        expect(document.documentElement.style.getPropertyValue('--pc-toast-height')).toBe('');
        act(() => window.dispatchEvent(new Event('resize')));
        expect(document.documentElement.style.getPropertyValue('--pc-toast-height')).toBe('');
        unmount();
        expect(document.documentElement.style.getPropertyValue('--pc-toast-height')).toBe('');
    });
});

it('blendet Meldungen nach Ablauf aus und beendet Timer beim Unmount', async () => {
    vi.useFakeTimers();
    const { unmount } = render(<ToastProvider><TestComponent /></ToastProvider>);
    try {
        act(() => screen.getByText('Error').click());
        expect(screen.getByRole('alert')).toBeVisible();
        act(() => vi.advanceTimersByTime(5000));
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
        expect(screen.getByTestId('toast-container')).not.toBeVisible();
        act(() => screen.getByText('Error').click());
        unmount();
        expect(vi.getTimerCount()).toBe(0);
    } finally {
        unmount(); vi.useRealTimers();
    }
});
