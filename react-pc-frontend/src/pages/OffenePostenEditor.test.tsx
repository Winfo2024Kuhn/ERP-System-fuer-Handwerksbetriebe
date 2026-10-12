import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import OffenePostenEditor from './OffenePostenEditor';

vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('bietet kein manuelles Erfassen von Ausgangsrechnungen mehr an', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => [] })));
    render(<MemoryRouter><OffenePostenEditor /></MemoryRouter>);
    expect(await screen.findByRole('button', { name: /aktualisieren/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /manuell erfassen/i })).not.toBeInTheDocument();
});
