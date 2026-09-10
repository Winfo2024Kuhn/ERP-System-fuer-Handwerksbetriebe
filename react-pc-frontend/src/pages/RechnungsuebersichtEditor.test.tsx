import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import RechnungsuebersichtEditor from './RechnungsuebersichtEditor';
import { ToastProvider } from '../components/ui/toast';
vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('behält einen leeren Betragsentwurf und importiert erst einen vollständigen Kommawert', async () => {
    const writes: Record<string, unknown>[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        if (String(url).endsWith('import-upload')) {
            writes.push(JSON.parse(init.body.get('metadata')));
            return { ok: true, json: async () => ({}) };
        }
        return { ok: true, json: async () => String(url).endsWith('analyze-upload') ? [{ analyzeResponse: { betragBrutto: 0, dokumentTyp: 'RECHNUNG', dokumentDatum: '2026-09-09', lieferantName: 'Testlieferant' } }] : String(url) === '/api/lieferanten' ? [{ id: 1, firmenname: 'Testlieferant' }] : [] };
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
  return{ok:true,json:async()=>String(url).endsWith('analyze-upload')?[{analyzeResponse:{betragBrutto:analyses++===0?12.5:null,dokumentTyp:'RECHNUNG',lieferantName:'Testlieferant'}}]:String(url)==='/api/lieferanten'?[{id:1,firmenname:'Testlieferant'}]:[]};
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
