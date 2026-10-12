import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import RechnungsuebersichtEditor from './RechnungsuebersichtEditor';
import { ToastProvider } from '../components/ui/toast';
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
it('bietet kein Hochladen von Rechnungen mehr an – das läuft über Belege & Kasse', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => [] })));
    render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
    expect(await screen.findByRole('button', { name: /aktualisieren/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /hochladen/i })).not.toBeInTheDocument();
    expect(document.querySelector('input[type=file]')).toBeNull();
});

it('nennt nach dem Gesamt-PDF die Eingangsrechnungen, die mangels PDF-Datei fehlen', async () => {
    const eingang = [
        { id: 11, dokumentNummer: 'ER-11', lieferantName: 'Musterlieferant GmbH', bezahlt: false },
        { id: 12, dokumentNummer: 'ER-12', lieferantName: 'Musterlieferant GmbH', bezahlt: false },
    ];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url.includes('/merge-pdf')) {
            return { ok: true, headers: new Headers({ 'X-Nicht-Enthalten': '12' }), blob: async () => new Blob(['%PDF-1.7']) };
        }
        return { ok: true, json: async () => url.includes('/eingang') ? eingang : [] };
    }));
    // jsdom kennt keine Blob-URLs: nur für diesen Test nachrüsten und danach wieder entfernen.
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:gesamt') });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    try {
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        fireEvent.click(screen.getByRole('button', { name: /Eingangsrechnungen/ }));
        await screen.findByText('ER-12');
        fireEvent.click(screen.getAllByRole('checkbox')[0]);
        fireEvent.click(screen.getByRole('button', { name: /Gesamt-PDF \(2\)/ }));
        expect(await screen.findByText('Nicht im Gesamt-PDF, weil es keine PDF-Datei dazu gibt: ER-12')).toBeInTheDocument();
    } finally {
        Reflect.deleteProperty(URL, 'createObjectURL');
        Reflect.deleteProperty(URL, 'revokeObjectURL');
    }
});

it('zeigt Storno und storniertes Original ohne offene Zahlung und öffnet den Dokumenteditor', async () => {
    const rechnungen = [
        { id: 41, dokumentid: 'RE-2026/09/00001', geschaeftsdokumentart: 'Rechnung', rechnungsdatum: '2026-09-10', bruttoBetrag: 119, bezahlt: false, storniert: true, storno: false, editorUrl: '/dokument-editor?dokumentId=41&dokumentTyp=RECHNUNG' },
        { id: 42, dokumentid: 'ST-2026/09/00001', geschaeftsdokumentart: 'Stornorechnung', rechnungsdatum: '2026-09-13', bruttoBetrag: -119, bezahlt: false, storniert: false, storno: true, editorUrl: '/dokument-editor?dokumentId=42&dokumentTyp=STORNO' },
    ];
    vi.stubGlobal('fetch', vi.fn(async (url) => ({ ok: true, json: async () => String(url).includes('/ausgang') ? rechnungen : [] })));
    render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
    await screen.findByText('ST-2026/09/00001');
    expect(screen.getByText('Storniert')).toBeInTheDocument();
    expect(screen.getByText('Storno')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ST-2026/09/00001 im Dokumenteditor öffnen' })).toHaveAttribute('href', '/dokument-editor?dokumentId=42&dokumentTyp=STORNO');
    expect(screen.queryByText('Offen', { selector: 'span' })).not.toBeInTheDocument();
});

it('bezeichnet fehlenden Zahlungsstatus als unbekannt statt als offene Forderung', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url) => ({ ok: true, json: async () => String(url).includes('/ausgang') ? [
        { id: 43, dokumentid: 'RE-43', geschaeftsdokumentart: 'Rechnung', bruttoBetrag: 119, bezahlt: null },
    ] : [] })));
    render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
    await screen.findByText('RE-43');
    expect(screen.getByText('Unbekannt')).toBeInTheDocument();
    expect(screen.queryByText('Offen', { selector: 'span' })).not.toBeInTheDocument();
});
