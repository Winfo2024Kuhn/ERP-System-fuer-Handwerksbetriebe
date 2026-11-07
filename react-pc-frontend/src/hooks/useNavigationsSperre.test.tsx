import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, Link, RouterProvider, type BlockerFunction } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useNavigationsSperre } from './useNavigationsSperre';

type SperreProps = { sperren: boolean | BlockerFunction; entscheiden: () => Promise<boolean>; beimSchliessenWarnen?: boolean };

function Sperre(props: SperreProps) {
    useNavigationsSperre(props);
    return null;
}

function zeige(props: SperreProps) {
    const router = createMemoryRouter([
        { path: '/', element: <><Sperre {...props} /><Link to="/weg">Weg</Link><Link to="/?x=1">Gleich</Link><p>Start</p></> },
        { path: '/weg', element: <p>Ziel</p> },
    ]);
    render(<RouterProvider router={router} />);
    return router;
}

describe('useNavigationsSperre', () => {
    afterEach(() => vi.restoreAllMocks());

    it('ohne Sperre geht der Wechsel durch, ohne zu fragen', async () => {
        const entscheiden = vi.fn().mockResolvedValue(true);
        zeige({ sperren: false, entscheiden });
        await userEvent.click(screen.getByText('Weg'));
        expect(await screen.findByText('Ziel')).toBeInTheDocument();
        expect(entscheiden).not.toHaveBeenCalled();
    });

    it('gesperrt: fragt einmal und wechselt bei Ja', async () => {
        const entscheiden = vi.fn().mockResolvedValue(true);
        zeige({ sperren: true, entscheiden });
        await userEvent.click(screen.getByText('Weg'));
        expect(await screen.findByText('Ziel')).toBeInTheDocument();
        expect(entscheiden).toHaveBeenCalledTimes(1);
    });

    it('gesperrt: bleibt bei Nein auf der Seite', async () => {
        const entscheiden = vi.fn().mockResolvedValue(false);
        const router = zeige({ sperren: true, entscheiden });
        await userEvent.click(screen.getByText('Weg'));
        await waitFor(() => expect(entscheiden).toHaveBeenCalledTimes(1));
        expect(screen.getByText('Start')).toBeInTheDocument();
        expect(router.state.location.pathname).toBe('/');
    });

    it('eine Bedingung als Funktion sperrt nur die passenden Wechsel', async () => {
        const entscheiden = vi.fn().mockResolvedValue(false);
        const router = zeige({ sperren: ({ nextLocation }) => nextLocation.pathname === '/weg', entscheiden });
        await userEvent.click(screen.getByText('Gleich'));
        await waitFor(() => expect(router.state.location.search).toBe('?x=1'));
        expect(entscheiden).not.toHaveBeenCalled();
        await userEvent.click(screen.getByText('Weg'));
        await waitFor(() => expect(entscheiden).toHaveBeenCalledTimes(1));
        expect(router.state.location.pathname).toBe('/');
    });

    it('warnt beim Schließen des Fensters, wenn gewünscht', () => {
        const hinzu = vi.spyOn(window, 'addEventListener');
        zeige({ sperren: true, entscheiden: vi.fn(), beimSchliessenWarnen: true });
        const lauscher = hinzu.mock.calls.find(([art]) => art === 'beforeunload')?.[1] as EventListener;
        expect(lauscher).toBeDefined();
        const ereignis = new Event('beforeunload', { cancelable: true });
        lauscher(ereignis);
        expect(ereignis.defaultPrevented).toBe(true);
    });

    it('ohne Wunsch kein beforeunload-Lauscher', () => {
        const hinzu = vi.spyOn(window, 'addEventListener');
        zeige({ sperren: true, entscheiden: vi.fn() });
        expect(hinzu.mock.calls.some(([art]) => art === 'beforeunload')).toBe(false);
    });
});
