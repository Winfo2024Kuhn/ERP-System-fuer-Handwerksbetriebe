import { ToastProvider } from '../../../components/ui/toast';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { KommunikationsVerlauf } from './KommunikationsVerlauf';
describe('KommunikationsVerlauf',()=>{beforeEach(()=>{global.fetch=vi.fn(async()=>({ok:true,json:async()=>({content:[{emailId:4,subject:'Anfrage Revision 1',fromAddress:'einkauf@example.test',status:'ERFOLGREICH',quelle:'AUSGANG',revisionId:1}]})} as Response));});it('kennzeichnet ältere Nachrichten mit ihrer Anfragefassung',async()=>{render(<ToastProvider><KommunikationsVerlauf typ="ANFRAGE" vorgangId={41}/></ToastProvider>);expect((await screen.findAllByText(/Revision 1/)).length).toBeGreaterThan(0);});});
