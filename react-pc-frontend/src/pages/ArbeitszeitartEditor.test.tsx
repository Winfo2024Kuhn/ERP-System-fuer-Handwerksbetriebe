import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { it, expect, vi, afterEach } from 'vitest';
import ArbeitszeitartEditor from './ArbeitszeitartEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
vi.mock('../components/TiptapEditor',()=>({TiptapEditor:()=>null}));
afterEach(()=>vi.unstubAllGlobals());
it('behält unvollständigen Stundensatz und speichert nur vollständige Kommawerte',async()=>{
 const user=userEvent.setup();const fetcher=vi.fn().mockImplementation(async(_url,init)=>({ok:true,json:async()=>init?{id:1,bezeichnung:'Montage',stundensatz:8.5,aktiv:true,sortierung:0}:[]}));vi.stubGlobal('fetch',fetcher);
 render(<ToastProvider><ConfirmProvider><ArbeitszeitartEditor/></ConfirmProvider></ToastProvider>);
 await user.click(await screen.findByRole('button',{name:'Neue Arbeitszeitart'}));await user.type(screen.getByLabelText('Bezeichnung *'),'Montage');
 const input=screen.getByRole('textbox',{name:'Stundensatz (€/h) *'});await user.clear(input);await user.type(input,'8,');
 await user.click(screen.getByRole('button',{name:'Speichern'}));expect(fetcher.mock.calls.some(c=>c[1]?.method==='POST')).toBe(false);
 await user.type(input,'5');await user.click(screen.getByRole('button',{name:'Speichern'}));
 expect(JSON.parse(fetcher.mock.calls.find(c=>c[1]?.method==='POST')?.[1].body)).toMatchObject({stundensatz:8.5});
});

it('zeigt fehlgeschlagene Ladeanfragen im eigenen Toast',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:503,json:async()=>[]}));
 render(<ToastProvider><ConfirmProvider><ArbeitszeitartEditor/></ConfirmProvider></ToastProvider>);
 expect(await screen.findByText('Arbeitszeitarten konnten nicht geladen werden.')).toBeVisible();
});
