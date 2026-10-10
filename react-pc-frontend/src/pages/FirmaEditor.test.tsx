import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import FirmaEditor from './FirmaEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import { MemoryRouter } from 'react-router-dom';
it('erhält leere Tagesentwürfe und prüft ganze Tage sowie Komma-Prozente vor dem Speichern', async () => {
    const user = userEvent.setup();
    const firma = { id: 1, firmenname: 'Testbetrieb', firmenfarbe: '#500010', mahnverfahrenAktiv: true, tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 7, tageBisZweiteMahnung: 7, mahnverfahrenNeuesZahlungszielTage: 7, bgSatzOverride: 0 };
    const fetcher = vi.fn().mockImplementation(async (url, init) => ({ ok: true, json: async () => url === '/api/firma' ? init ? JSON.parse(init.body) : firma : [] }));
    vi.stubGlobal('fetch', fetcher);
    render(<MemoryRouter><ToastProvider><ConfirmProvider><FirmaEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
    const days = await screen.findByRole('textbox', { name: 'Tage nach Fälligkeit' });
    await user.clear(days);
    expect(days).toHaveValue('');
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
    await user.type(days, '8,5');
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
    await user.clear(days); await user.type(days, '8');
    await user.click(screen.getByRole('button', { name: 'Firmenfarbe wählen' }));
    expect(screen.getByRole('dialog', { name: 'Firmenfarbe wählen auswählen' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Rose (#e11d48)' }));
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT' && JSON.parse(init.body).tageBisZahlungserinnerung === 8)).toBe(true));
    await user.click(screen.getByRole('button', { name: /Unfallversicherung/ }));
    const bg = screen.getByRole('textbox', { name: 'Tatsächlicher BG-Satz (aus Bescheid)' });
    await user.click(bg); expect(bg).toHaveValue('');
    await user.type(bg, '8,');
    const before = fetcher.mock.calls.filter(([, init]) => init?.method === 'PUT').length;
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'PUT')).toHaveLength(before);
    await user.type(bg, '5'); await user.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() => expect(fetcher.mock.calls.some(([, init]) => init?.method === 'PUT' && JSON.parse(init.body).bgSatzOverride === 8.5)).toBe(true));
});

it('zeigt fehlgeschlagene Ladeanfragen im eigenen Toast',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:503,json:async()=>[]}));
 render(<MemoryRouter><ToastProvider><ConfirmProvider><FirmaEditor/></ConfirmProvider></ToastProvider></MemoryRouter>);
 expect(await screen.findByText('Firmendaten konnten nicht geladen werden.')).toBeVisible();
});

it('verweist für Postfächer auf Einstellungen → E-Mail statt eigener Absender-Liste', async () => {
    const firma = { id: 1, firmenname: 'Testbetrieb', email: 'info@example.org' };
    const fetcher = vi.fn().mockImplementation(async (url: string) => ({ ok: true, json: async () => url === '/api/firma' ? firma : [] }));
    vi.stubGlobal('fetch', fetcher);
    render(<MemoryRouter><ToastProvider><ConfirmProvider><FirmaEditor /></ConfirmProvider></ToastProvider></MemoryRouter>);
    const link = await screen.findByRole('link', { name: 'Einstellungen → E-Mail' });
    expect(link).toHaveAttribute('href', '/einstellungen#email');
    expect(screen.queryByRole('button', { name: /E-Mail-Absender/ })).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/firma/email-absender'))).toBe(false);
});
