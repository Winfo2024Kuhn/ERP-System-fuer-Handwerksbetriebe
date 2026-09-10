import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import BelegeKasseEditor from './BelegeKasseEditor';
import { ToastProvider } from '../components/ui/toast';
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('prüft den ganzen Beleg vor dem Speichern und übergibt Kommawerte numerisch', async () => {
    const writes: Record<string, unknown>[] = [];
    const beleg = { id: 1, belegNummer: 'TEST-BELEG', belegKategorie: 'SONSTIGER_BELEG', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 0, betragNetto: 10, mwstSatz: 19, kostenstellenSplits: [] };
    vi.stubGlobal('fetch', vi.fn(async (url, init) => { if (init?.method === 'PUT') {
        writes.push(JSON.parse(init.body));
        return { ok: true, json: async () => beleg };
    } return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : [] }; }));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /TEST-BELEG/ }));
    const amount = screen.getByRole('textbox', { name: 'Betrag (€)' });
    expect(amount).toHaveValue('0');
    fireEvent.focus(amount);
    expect(amount).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    expect(writes).toHaveLength(0);
    fireEvent.change(amount, { target: { value: '12,' } });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    expect(writes).toHaveLength(0);
    fireEvent.change(amount, { target: { value: '12,50' } });
    fireEvent.click(screen.getByRole('button', { name: /Mehr Details/ }));
    const netto = screen.getByRole('textbox', { name: 'Netto (€)' });
    fireEvent.focus(netto);
    expect(netto).toHaveValue('10');
    fireEvent.click(screen.getByRole('button', {name: 'Wählen', exact: true}));
    fireEvent.keyDown(document, {key: 'Escape'});
    expect(screen.getByRole('textbox', {name: 'Betrag (€)'})).toHaveValue('12,50');
    const tax = screen.getByRole('textbox', { name: 'MwSt-Satz (%)' });
    fireEvent.change(tax, { target: { value: '101' } });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    expect(writes).toHaveLength(0);
    fireEvent.change(amount, { target: { value: '-12,5' } });
    fireEvent.click(screen.getByRole('button', { name: '7 %', exact: true }));
    expect(netto).toHaveValue('10');
    fireEvent.change(amount, { target: { value: '12,50' } });
    fireEvent.change(tax, { target: { value: '19' } });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    await waitFor(() => expect(writes).toEqual([expect.objectContaining({ betragBrutto: 12.5, betragNetto: 10, mwstSatz: 19 })]));
});
it('bucht bei Unterdeckung keine Vorab-Einlage für einen geänderten oder ungültigen Entwurf', async () => {
    const writes: string[] = [];
    const beleg = { id: 2, belegNummer: 'TEST-KASSE', belegKategorie: 'KASSE_AUSGABE', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 20, kostenstellenSplits: [] };
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        if (init?.method) {
            writes.push(init.method);
            return { ok: false, status: 409, json: async () => ({ projizierterSaldo: -20, mindestbestand: 0, message: 'Kasse reicht nicht.' }) };
        }
        return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : String(url).endsWith('/saldo') ? { saldo: 0, mindestbestand: 0 } : [] };
    }));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /TEST-KASSE/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    await screen.findByRole('button', { name: /Privateinlage in Höhe/ });
    expect(writes).toEqual(['PUT']);
    const amount = screen.getByRole('textbox', { name: 'Betrag (€)' });
    fireEvent.change(amount, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: /Privateinlage in Höhe/ }));
    expect(writes).toEqual(['PUT']);
    fireEvent.change(amount, { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: /Privateinlage in Höhe/ }));
    await waitFor(() => expect(writes).toEqual(['PUT', 'PUT']));
});
