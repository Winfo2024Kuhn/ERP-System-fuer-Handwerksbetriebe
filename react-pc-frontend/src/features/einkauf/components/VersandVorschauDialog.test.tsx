import { ToastProvider } from '../../../components/ui/toast';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { VersandVorschauDialog } from './VersandVorschauDialog';
import type { VersandVorschau } from './VersandVorschauDialog';
const vorschau: VersandVorschau={revisionsNummer:2,eigeneKundennummer:'00017',antwortfrist:'2026-10-01',liefertermin:'2026-10-14',anlagen:[{id:8,dateiId:18,bedarfId:70,revision:'B',dateiname:'Profil.pdf',mimeTyp:'application/pdf',byteAnzahl:100,sha256:'dummy',freigegeben:true,versendet:false,hochgeladenAm:'2026-09-24T00:00:00Z'}],version:3,vorschauHash:'hash-3',subject:'Anfrage PA-3',htmlBody:'<p>Guten Tag</p><script>nicht zeigen</script>',empfaenger:'einkauf@example.test',pdfDateiId:1,anlageVersionIds:[8]};
describe('VersandVorschauDialog',()=>{it('zeigt Empfänger und Anlagen vor Freigabe und übergibt den Vorschauhash',async()=>{const user=userEvent.setup();const freigeben=vi.fn(async()=>{});render(<ToastProvider><VersandVorschauDialog vorschau={vorschau} onFreigeben={freigeben} onSchliessen={()=>{}}/></ToastProvider>);expect(screen.getByText('einkauf@example.test')).toBeInTheDocument();expect(screen.getByText(/Profil.pdf · Revision B · Freigegeben/)).toBeInTheDocument();expect(screen.queryByText(/nicht zeigen/)).not.toBeInTheDocument();await user.click(screen.getByRole('button',{name:/freigeben und senden/i}));expect(freigeben).toHaveBeenCalledWith('hash-3');});});

it('verhindert Freigabe ohne prüfbare Anlagenmetadaten',()=>{render(<ToastProvider><VersandVorschauDialog vorschau={{...vorschau,anlagen:[]}} onFreigeben={vi.fn()} onSchliessen={()=>{}}/></ToastProvider>);expect(screen.getByRole('button',{name:/freigeben und senden/i})).toBeDisabled();});
