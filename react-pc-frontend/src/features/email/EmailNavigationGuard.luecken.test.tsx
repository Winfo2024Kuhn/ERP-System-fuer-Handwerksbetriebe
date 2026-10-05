import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, Link, RouterProvider } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { EmailNavigationGuard } from './EmailNavigationGuard';

function setup(active: boolean, persist: () => Promise<boolean>) {
    const router = createMemoryRouter([
        {
            path: '/',
            element: <><EmailNavigationGuard active={active} persist={persist} /><Link to="/weg">Weg</Link><p>Start</p></>,
        },
        { path: '/weg', element: <p>Ziel</p> },
    ]);
    render(<RouterProvider router={router} />);
    return router;
}

describe('EmailNavigationGuard', () => {
    it('ohne aktiven Entwurf wird direkt navigiert, ohne zu speichern', async () => {
        const persist = vi.fn().mockResolvedValue(true);
        setup(false, persist);
        await userEvent.click(screen.getByText('Weg'));
        expect(await screen.findByText('Ziel')).toBeInTheDocument();
        expect(persist).not.toHaveBeenCalled();
    });

    it('aktiv: speichert zuerst und navigiert danach weiter', async () => {
        const persist = vi.fn().mockResolvedValue(true);
        setup(true, persist);
        await userEvent.click(screen.getByText('Weg'));
        expect(await screen.findByText('Ziel')).toBeInTheDocument();
        expect(persist).toHaveBeenCalledTimes(1);
    });

    it('aktiv: bleibt auf der Seite, wenn das Speichern fehlschlaegt', async () => {
        const persist = vi.fn().mockResolvedValue(false);
        const router = setup(true, persist);
        await userEvent.click(screen.getByText('Weg'));
        await waitFor(() => expect(persist).toHaveBeenCalledTimes(1));
        expect(screen.getByText('Start')).toBeInTheDocument();
        expect(screen.queryByText('Ziel')).toBeNull();
        expect(router.state.location.pathname).toBe('/');
    });
});
