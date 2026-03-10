import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import NetworkStatusBadge from './NetworkStatusBadge';

describe('NetworkStatusBadge', () => {
    beforeEach(() => {
        // Default: online
        Object.defineProperty(navigator, 'onLine', { writable: true, value: true });
    });

    it('zeigt Online-Status an', () => {
        render(<NetworkStatusBadge />);
        expect(screen.getByText('Online')).toBeInTheDocument();
    });

    it('zeigt Offline-Status an', () => {
        Object.defineProperty(navigator, 'onLine', { writable: true, value: false });
        render(<NetworkStatusBadge />);
        expect(screen.getByText('Offline')).toBeInTheDocument();
    });

    it('zeigt Synchronisierungs-Status an', () => {
        render(<NetworkStatusBadge syncStatus="syncing" />);
        expect(screen.getByText('Synchronisiere...')).toBeInTheDocument();
    });

    it('zeigt Sync-Fehler-Status an', () => {
        render(<NetworkStatusBadge syncStatus="error" />);
        expect(screen.getByText('Sync-Fehler')).toBeInTheDocument();
    });

    it('ruft onSync beim Klick im Online-Modus auf', async () => {
        const handleSync = vi.fn();
        const user = userEvent.setup();
        render(<NetworkStatusBadge onSync={handleSync} />);
        await user.click(screen.getByText('Online'));
        expect(handleSync).toHaveBeenCalledOnce();
    });

    it('ruft onSync beim Klick auf Sync-Fehler auf', async () => {
        const handleSync = vi.fn();
        const user = userEvent.setup();
        render(<NetworkStatusBadge syncStatus="error" onSync={handleSync} />);
        await user.click(screen.getByText('Sync-Fehler'));
        expect(handleSync).toHaveBeenCalledOnce();
    });

    it('zeigt kompakte Ansicht (nur Punkt)', () => {
        const { container } = render(<NetworkStatusBadge compact />);
        const dot = container.querySelector('.w-2.h-2.rounded-full');
        expect(dot).toBeInTheDocument();
        expect(dot).toHaveAttribute('title', 'Online');
    });

    it('zeigt roten Punkt im Compact-Offline-Modus', () => {
        Object.defineProperty(navigator, 'onLine', { writable: true, value: false });
        const { container } = render(<NetworkStatusBadge compact />);
        const dot = container.querySelector('.w-2.h-2.rounded-full');
        expect(dot).toHaveAttribute('title', 'Offline');
        expect(dot?.className).toContain('bg-red-500');
    });

    it('zeigt grünen Punkt im Compact-Online-Modus', () => {
        const { container } = render(<NetworkStatusBadge compact />);
        const dot = container.querySelector('.w-2.h-2.rounded-full');
        expect(dot?.className).toContain('bg-green-500');
    });
});
