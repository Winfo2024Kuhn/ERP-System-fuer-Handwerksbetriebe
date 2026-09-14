import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { KassenbuchAbschlussLeiste } from './KassenbuchAbschlussLeiste';
import { ToastProvider } from '../ui/toast';
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('zählt nur vollständige ganze Stückzahlen und leert Null beim Fokus', async () => {
    const writes: unknown[] = [];
    vi.stubGlobal('fetch', vi.fn(async (_url, init) => { if (init?.method)
        writes.push(JSON.parse(init.body)); return { ok: true, json: async () => ({ rechnerischerBestand: 400 }) }; }));
    render(<ToastProvider><KassenbuchAbschlussLeiste letzterAbschluss={null} offeneBewegungen={1} onGeaendert={() => { }}/></ToastProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Kasse zählen' }));
    const field = screen.getByRole('textbox', { name: 'Anzahl 200 €' });
    expect(field).toHaveValue('0');
    fireEvent.focus(field);
    expect(field).toHaveValue('');
    fireEvent.change(field, { target: { value: '2,5' } });
    expect(writes).toHaveLength(0);
    fireEvent.change(field, { target: { value: '2' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Zählung festhalten' })).toBeEnabled());
    const second = screen.getByRole('textbox', { name: 'Anzahl 100 €' });
    fireEvent.focus(second);
    fireEvent.click(screen.getByRole('button', { name: 'Zählung festhalten' }));
    expect(writes).toHaveLength(0);
    fireEvent.change(second, { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Zählung festhalten' }));
    await waitFor(() => expect(writes).toEqual([{ stichtag: expect.any(String), stueckelung: { '200': 2 }, bemerkung: null, differenzBuchen: true }]));
});
