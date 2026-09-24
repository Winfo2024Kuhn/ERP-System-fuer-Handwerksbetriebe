import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import PreisanfragenPage from './PreisanfragenPage';

vi.mock('../features/einkauf/originalBedarfApi', () => ({ nutztEchtesBackend: true }));
afterEach(() => vi.unstubAllGlobals());
it('zeigt einen Ladefehler statt fälschlich eine leere Datenbank', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 503 })));
    render(<MemoryRouter><ToastProvider><ConfirmProvider><PreisanfragenPage /></ConfirmProvider></ToastProvider></MemoryRouter>);
    expect(await screen.findByRole('button', { name: 'Erneut laden' })).toBeVisible();
    expect(screen.queryByText('Noch keine Preisanfragen')).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/einkauf/anfragen?page=0&size=20', expect.anything());
});
