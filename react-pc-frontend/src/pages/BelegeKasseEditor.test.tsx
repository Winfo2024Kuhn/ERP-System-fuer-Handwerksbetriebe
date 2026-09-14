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
    const beleg = { id: 1, belegNummer: 'TEST-BELEG', belegKategorie: 'SONSTIGER_BELEG', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 0, betragNetto: 10, mwstSatz: 19, zahlungsart: 'Bar', kostenstellenSplits: [] };
    const fetchMock = vi.fn(async (url, init) => { if (init?.method === 'PUT') {
        writes.push(JSON.parse(init.body));
        return { ok: true, json: async () => beleg };
    } return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : String(url).endsWith('/belege/1') ? beleg : [] }; });
    vi.stubGlobal('fetch', fetchMock);
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /TEST-BELEG/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/buchhaltung/belege/1'));
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
    const beleg = { id: 2, belegNummer: 'TEST-KASSE', belegKategorie: 'KASSE_AUSGABE', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 20, zahlungsart: 'Bar', kostenstellenSplits: [] };
    const fetchMock = vi.fn(async (url, init) => {
        if (init?.method) {
            writes.push(init.method);
            return { ok: false, status: 409, json: async () => ({ projizierterSaldo: -20, mindestbestand: 0, message: 'Kasse reicht nicht.' }) };
        }
        return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : String(url).endsWith('/belege/2') ? beleg : String(url).endsWith('/saldo') ? { saldo: 0, mindestbestand: 0 } : [] };
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /TEST-KASSE/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/buchhaltung/belege/2'));
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

it('übernimmt Zahlungsstatus und Split aus dem Detail statt die leeren Listenwerte zu speichern', async () => {
    const writes: Record<string, unknown>[] = [];
    const liste = { id: 31, belegNummer: 'DETAIL-STATUS', belegKategorie: 'BANK', dokumentTyp: 'RECHNUNG', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 50, zahlungsart: 'Überweisung', kostenstellenSplits: [] };
    const detail = { ...liste, eingangsrechnungBezahlt: true, eingangsrechnungBezahltAm: '2026-09-08', kostenstellenSplits: [{ kostenstelleId: 7, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: 2026 }] };
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        if (init?.method === 'PUT') { writes.push(JSON.parse(init.body)); return { ok: true, json: async () => detail }; }
        return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [liste] : String(url).endsWith('/belege/31') ? detail : [] };
    }));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /DETAIL-STATUS/ }));
    await waitFor(() => expect(screen.getByRole('radio', { name: /Ja, bezahlt/ })).toBeChecked());
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0]).toMatchObject({ zahlungsstatus: 'BEZAHLT', bezahltAm: '2026-09-08', kostenstellenSplits: [expect.objectContaining({ kostenstelleId: 7, prozent: 100 })] });
});

it('sendet keine Splits mehr, wenn die einfache Zuordnung gelöscht wird', async () => {
    const writes: Record<string, unknown>[] = [];
    const beleg = { id: 32, belegNummer: 'SPLIT-LOESCHEN', belegKategorie: 'BANK', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 50, zahlungsart: 'Bar', kostenstellenSplits: [{ kostenstelleId: 7, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: 2026 }] };
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        if (init?.method === 'PUT') { writes.push(JSON.parse(init.body)); return { ok: true, json: async () => beleg }; }
        return { ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : String(url).endsWith('/belege/32') ? beleg : String(url).includes('kostenstellen') ? [{ id: 7, bezeichnung: 'Musterbaustelle' }] : [] };
    }));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /SPLIT-LOESCHEN/ }));
    const zuordnung = await screen.findByRole('combobox', { name: 'Baustelle oder Bereich' });
    fireEvent.click(zuordnung); fireEvent.click(screen.getByRole('option', { name: '– keine Zuordnung –' }));
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen & Übernehmen' }));
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0]?.kostenstellenSplits).toEqual([]);
});

it('behält eine vor der verzögerten Detailantwort gewählte Zahlung bei', async () => {
    let liefereDetail: ((value: unknown) => void) | undefined;
    const beleg = { id: 33, belegNummer: 'ZAHLUNG-BEARBEITET', belegKategorie: 'BANK', dokumentTyp: 'RECHNUNG', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 50, zahlungsart: 'Überweisung', kostenstellenSplits: [] };
    vi.stubGlobal('fetch', vi.fn(async url => ({ ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : String(url).endsWith('/belege/33') ? new Promise(resolve => { liefereDetail = resolve; }) : [] })));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: /ZAHLUNG-BEARBEITET/ }));
    fireEvent.click(screen.getByRole('radio', { name: /Ja, bezahlt/ }));
    liefereDetail?.({ ...beleg, eingangsrechnungBezahlt: false, eingangsrechnungBezahltAm: null });
    await waitFor(() => expect(screen.getByRole('radio', { name: /Ja, bezahlt/ })).toBeChecked());
});

it.each([
    ['RE-MUSTER-1', 'Musterbaustoffe GmbH', 'KI-Musterbetrieb', 'RE-MUSTER-1'],
    [null, 'Musterbaustoffe GmbH', null, 'Musterbaustoffe GmbH'],
    [null, 'Musterbaustoffe GmbH', 'KI-Musterbetrieb', 'Musterbaustoffe GmbH'],
    [null, null, 'KI-Musterbetrieb', 'KI-Musterbetrieb'],
    [null, null, null, 'quittung-muster.jpg'],
])('zeigt im Belegtitel bestätigte Daten vor KI und Dateiname (%s, %s, %s)', async (belegNummer, lieferantName, kiVorgeschlagenerLieferant, titel) => {
    const beleg = { id: 7, belegNummer, lieferantName, kiVorgeschlagenerLieferant, originalDateiname: 'quittung-muster.jpg',
        belegKategorie: 'KASSE_AUSGABE', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', betragBrutto: 20 };
    vi.stubGlobal('fetch', vi.fn(async url => ({ ok: true, json: async () => String(url) === '/api/buchhaltung/belege' ? [beleg] : [] })));
    render(<ToastProvider><BelegeKasseEditor /></ToastProvider>);
    const zeile = await screen.findByRole('button', { name: /Zu prüfen/ });
    expect(zeile.querySelector('.font-semibold')).toHaveTextContent(titel);
});
