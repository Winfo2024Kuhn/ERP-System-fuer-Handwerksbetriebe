import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import RechnungsuebersichtEditor from './RechnungsuebersichtEditor';
import { ToastProvider } from '../components/ui/toast';
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
it('behält einen leeren Betragsentwurf und importiert erst einen vollständigen Kommawert', async () => {
    const writes: Record<string, unknown>[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        if (String(url).endsWith('import-upload')) {
            writes.push(JSON.parse(init.body.get('metadata')));
            return { ok: true, json: async () => ({}) };
        }
        return { ok: true, json: async () => String(url).endsWith('analyze-upload') ? [{ analyzeResponse: { betragBrutto: 0, dokumentTyp: 'RECHNUNG', dokumentDatum: '2026-09-09', lieferantName: 'Testlieferant' } }] : String(url).startsWith('/api/lieferanten?') ? { lieferanten: [{ id: 1, lieferantenname: 'Testlieferant' }], gesamt: 1, seite: 0, seitenGroesse: 1000 } : [] };
    }));
    render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
    fireEvent.click(screen.getByRole('button', { name: /hochladen/i }));
    await waitFor(() => expect(document.querySelector('input[type=file]')).not.toBeNull());
    fireEvent.change(document.querySelector('input[type=file]')!, { target: { files: [new File(['dummy'], 'test.pdf', { type: 'application/pdf' })] } });
    const amount = await screen.findByRole('textbox', { name: 'Betrag Brutto' });
    expect(amount).toHaveValue('0');
    fireEvent.focus(amount);
    expect(amount).toHaveValue('');
    const save = screen.getByRole('button', { name: /speichern/i });
    fireEvent.click(save);
    expect(writes).toHaveLength(0);
    fireEvent.change(amount, { target: { value: '12,501' } });
    fireEvent.click(save);
    expect(writes).toHaveLength(0);
    fireEvent.change(amount, { target: { value: '12,50' } });
    fireEvent.click(save);
    await waitFor(() => expect(writes).toEqual([expect.objectContaining({ betragBrutto: 12.5, lieferantId: 1 })]));
});
it('übernimmt bei erneutem Upload mit nicht erkanntem Betrag keinen alten Entwurf',async()=>{
 let analyses=0;let imports=0;
 vi.stubGlobal('fetch',vi.fn(async(url)=>{
  if(String(url).endsWith('import-upload')){imports++;return{ok:true,json:async()=>({})};}
  return{ok:true,json:async()=>String(url).endsWith('analyze-upload')?[{analyzeResponse:{betragBrutto:analyses++===0?12.5:null,dokumentTyp:'RECHNUNG',lieferantName:'Testlieferant'}}]:String(url).startsWith('/api/lieferanten?')?{lieferanten:[{id:1,lieferantenname:'Testlieferant'}],gesamt:1,seite:0,seitenGroesse:1000}:[]};
 }));
 render(<ToastProvider><RechnungsuebersichtEditor/></ToastProvider>);
 const openFile=async()=>{
  fireEvent.click(screen.getByRole('button',{name:/hochladen/i}));
  await waitFor(()=>expect(document.querySelector('input[type=file]')).not.toBeNull());
  fireEvent.change(document.querySelector('input[type=file]')!,{target:{files:[new File(['dummy'],'test.pdf',{type:'application/pdf'})]}});
  return screen.findByRole('textbox',{name:'Betrag Brutto'});
 };
 expect(await openFile()).toHaveValue('12,5');fireEvent.click(screen.getByRole('button',{name:/speichern/i}));await waitFor(()=>expect(imports).toBe(1));
 await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
 expect(await openFile()).toHaveValue('');fireEvent.click(screen.getByRole('button',{name:/speichern/i}));expect(imports).toBe(1);
});

describe('Lieferant aus der Analyse vorauswählen', () => {
    type LieferantEintrag = { id: number; lieferantenname?: string | null };
    const LIEFERANTEN: LieferantEintrag[] = [{ id: 1, lieferantenname: 'Testlieferant' }, { id: 2, lieferantenname: 'Mustermann Metallbau' }];

    function aufgeschoben() {
        let freigeben!: () => void;
        const promise = new Promise<void>((resolve) => { freigeben = resolve; });
        return { promise, freigeben };
    }

    /** Stubbt die Upload-Endpunkte; GET /api/lieferanten in seiner echten Form (eine Seite, kein Array). */
    function stubbeUpload({ listeWartetAuf, analyseWartetAuf, lieferanten = LIEFERANTEN, erkannteNamen = ['Testlieferant'], listeOk = true }: {
        listeWartetAuf?: Promise<void>;
        analyseWartetAuf?: Promise<void>;
        lieferanten?: LieferantEintrag[];
        erkannteNamen?: (string | null)[];
        listeOk?: boolean;
    }) {
        const listeGelesen = vi.fn();
        let analysen = 0;
        vi.stubGlobal('fetch', vi.fn(async (url) => {
            const pfad = String(url);
            if (pfad.startsWith('/api/lieferanten?')) {
                await listeWartetAuf;
                return {
                    ok: listeOk,
                    status: listeOk ? 200 : 500,
                    json: async () => {
                        listeGelesen();
                        return { lieferanten, gesamt: lieferanten.length, seite: 0, seitenGroesse: 1000 };
                    },
                };
            }
            if (pfad.endsWith('analyze-upload')) {
                await analyseWartetAuf;
                const lieferantName = erkannteNamen[Math.min(analysen++, erkannteNamen.length - 1)];
                return { ok: true, json: async () => [{ analyzeResponse: { betragBrutto: 12.5, dokumentTyp: 'RECHNUNG', lieferantName } }] };
            }
            if (pfad.endsWith('import-upload')) return { ok: true, json: async () => ({}) };
            return { ok: true, json: async () => [] };
        }));
        return { listeGelesen };
    }

    async function dateiHochladen() {
        fireEvent.click(screen.getByRole('button', { name: /hochladen/i }));
        await waitFor(() => expect(document.querySelector('input[type=file]')).not.toBeNull());
        fireEvent.change(document.querySelector('input[type=file]')!, { target: { files: [new File(['dummy'], 'test.pdf', { type: 'application/pdf' })] } });
    }

    const speichern = () => screen.getByRole('button', { name: /speichern/i });

    it('fragt die Lieferanten in voller Seitengröße ab', async () => {
        stubbeUpload({});
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        fireEvent.click(screen.getByRole('button', { name: /hochladen/i }));
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/lieferanten?size=1000'));
    });

    it('wählt den Lieferanten auch, wenn die Liste erst nach der Analyse ankommt', async () => {
        const liste = aufgeschoben();
        stubbeUpload({ listeWartetAuf: liste.promise });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        expect(auswahl).toHaveTextContent('Bitte wählen...');
        expect(speichern()).toBeDisabled();

        liste.freigeben();
        await waitFor(() => expect(auswahl).toHaveTextContent(/^Testlieferant$/));
        expect(speichern()).toBeEnabled();
    });

    it('wählt den Lieferanten, wenn die Liste vor der Analyse da ist', async () => {
        const liste = aufgeschoben();
        const analyse = aufgeschoben();
        const { listeGelesen } = stubbeUpload({ listeWartetAuf: liste.promise, analyseWartetAuf: analyse.promise });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        liste.freigeben();
        await waitFor(() => expect(listeGelesen).toHaveBeenCalled());
        await act(async () => {});

        analyse.freigeben();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        await waitFor(() => expect(auswahl).toHaveTextContent(/^Testlieferant$/));
        expect(speichern()).toBeEnabled();
    });

    it('lässt eine Wahl von Hand stehen, auch "Bitte wählen..."', async () => {
        stubbeUpload({});
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        await waitFor(() => expect(auswahl).toHaveTextContent(/^Testlieferant$/));

        fireEvent.click(auswahl);
        fireEvent.click(screen.getByRole('option', { name: 'Mustermann Metallbau' }));
        expect(auswahl).toHaveTextContent(/^Mustermann Metallbau$/);

        fireEvent.click(auswahl);
        fireEvent.click(screen.getByRole('option', { name: 'Bitte wählen...' }));
        expect(auswahl).toHaveTextContent(/^Bitte wählen\.\.\.$/);
        expect(speichern()).toBeDisabled();
    });

    it('übernimmt die Wahl von Hand nicht in die nächste Rechnung', async () => {
        stubbeUpload({ erkannteNamen: ['Testlieferant', null] });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const ersteAuswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        await waitFor(() => expect(ersteAuswahl).toHaveTextContent(/^Testlieferant$/));
        fireEvent.click(ersteAuswahl);
        fireEvent.click(screen.getByRole('option', { name: 'Mustermann Metallbau' }));
        fireEvent.click(speichern());
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

        await dateiHochladen();
        const zweiteAuswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        expect(zweiteAuswahl).toHaveTextContent(/^Bitte wählen\.\.\.$/);
        expect(speichern()).toBeDisabled();
    });

    it('nimmt den genauen Namen vor einem Teilnamen', async () => {
        stubbeUpload({
            lieferanten: [{ id: 5, lieferantenname: 'Max Mustermann Bau GmbH' }, { id: 6, lieferantenname: 'Mustermann Bau' }],
            erkannteNamen: ['Mustermann Bau'],
        });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        await waitFor(() => expect(auswahl).toHaveTextContent(/^Mustermann Bau$/));
    });

    it('nimmt unter mehreren Teilnamen den längsten und übergeht Einträge ohne Namen', async () => {
        stubbeUpload({
            lieferanten: [{ id: 3, lieferantenname: null }, { id: 7, lieferantenname: 'Müller' }, { id: 8, lieferantenname: 'Müller Stahlbau' }],
            erkannteNamen: ['Müller Stahlbau GmbH'],
        });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        await waitFor(() => expect(auswahl).toHaveTextContent(/^Müller Stahlbau$/));
    });

    it('übergeht leere Namen, ohne abzustürzen', async () => {
        stubbeUpload({ lieferanten: [{ id: 3, lieferantenname: null }, ...LIEFERANTEN], erkannteNamen: ['   '] });
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        await dateiHochladen();
        const auswahl = await screen.findByRole('combobox', { name: 'Lieferant' });
        fireEvent.click(auswahl);
        expect(await screen.findByRole('option', { name: 'Ohne Namen (Nr. 3)' })).toBeInTheDocument();
        expect(auswahl).toHaveTextContent(/^Bitte wählen\.\.\.$/);
        expect(speichern()).toBeDisabled();
    });

    it('meldet per Toast, wenn die Lieferanten nicht geladen werden konnten', async () => {
        stubbeUpload({ listeOk: false });
        vi.spyOn(console, 'error').mockImplementation(() => {});
        render(<ToastProvider><RechnungsuebersichtEditor /></ToastProvider>);
        fireEvent.click(screen.getByRole('button', { name: /hochladen/i }));
        expect(await screen.findByText('Lieferanten konnten nicht geladen werden.')).toBeInTheDocument();
    });
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
