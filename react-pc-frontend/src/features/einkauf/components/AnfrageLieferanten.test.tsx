import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { AnfrageLieferanten } from './AnfrageLieferanten';

describe('AnfrageLieferanten', () => {
 beforeEach(() => { global.fetch = vi.fn(async (input: RequestInfo | URL) => { const url=String(input); if(url.includes('/versandstatus')) return {ok:true,json:async()=>[]} as Response; if(url==='/api/email-textvorlagen') return {ok:true,json:async()=>[{id:7,dokumentTyp:'EINKAUF_ANFRAGE',aktiv:true,standard:true}]} as Response; if(url.includes('/vorschau?')) return {ok:true,json:async()=>({revisionsNummer:2,anlagen:[],version:2,vorschauHash:'hash-abc',subject:'Anfrage PA-51',htmlBody:'<p>Guten Tag</p>',empfaenger:'einkauf@example.test',pdfDateiId:2,anlageVersionIds:[]})} as Response; return {ok:true,json:async()=>({status:'ANGENOMMEN'})} as Response; }); });
 it('zeigt individuelle Empfänger und bindet Freigabe an den geladenen Vorschauhash', async () => {
   const user=userEvent.setup(); render(<ToastProvider><AnfrageLieferanten anfrageId={51} revisionId={2} lieferanten={[{id:4,lieferantenname:'Musterlieferant',status:'AUSSTEHEND',version:0,kontakt:null}]} /></ToastProvider>);
   await user.click(await screen.findByRole('button',{name:/vorschau und freigabe/i}));
   expect(await screen.findByText('einkauf@example.test')).toBeInTheDocument();
   await user.click(screen.getByRole('button',{name:/freigeben und senden/i}));
   await waitFor(()=>expect(global.fetch).toHaveBeenCalledWith('/api/einkauf/anfragen/51/lieferanten/4/senden',expect.objectContaining({method:'POST',body:expect.stringContaining('hash-abc')})));
 });
});
