import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { it, expect, vi, afterEach } from 'vitest';
import { StundenlohnHistorieList } from './StundenlohnHistorieList';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
afterEach(()=>vi.unstubAllGlobals());
it('übernimmt deutschen Stundenlohn numerisch und blockiert ein unvollständiges Komma',async()=>{
 const user=userEvent.setup();const fetcher=vi.fn().mockResolvedValue({ok:true,json:async()=>[]});vi.stubGlobal('fetch',fetcher);
 render(<ToastProvider><ConfirmProvider><StundenlohnHistorieList mitarbeiterId={1}/></ConfirmProvider></ToastProvider>);
 await user.click(screen.getByRole('button',{name:'Neuer Eintrag'}));
 const input=screen.getByRole('textbox',{name:'Brutto-Stundenlohn (€)'});await user.type(input,'8,');
 await user.click(screen.getByRole('button',{name:'Speichern'}));expect(fetcher.mock.calls.some(c=>c[1]?.method==='POST')).toBe(false);
 await user.type(input,'5');await user.click(screen.getByRole('button',{name:'Speichern'}));
 const call=fetcher.mock.calls.find(c=>c[1]?.method==='POST');expect(JSON.parse(call?.[1].body)).toMatchObject({stundenlohn:8.5});
});

it('zeigt einen Netzwerkfehler und erhält den Stundenlohnentwurf', async () => {
 const user=userEvent.setup();const fetcher=vi.fn().mockImplementation(async (_url,init)=>{if(init)throw new Error('offline');return {ok:true,json:async()=>[]};});vi.stubGlobal('fetch',fetcher);
 render(<ToastProvider><ConfirmProvider><StundenlohnHistorieList mitarbeiterId={1}/></ConfirmProvider></ToastProvider>);
 await user.click(screen.getByRole('button',{name:'Neuer Eintrag'}));
 await user.type(screen.getByRole('textbox',{name:'Brutto-Stundenlohn (€)'}),'8,5');
 await user.click(screen.getByRole('button',{name:'Speichern'}));
 expect(await screen.findByText('Stundenlohn konnte nicht gespeichert werden.')).toBeVisible();
 expect(screen.getByRole('textbox',{name:'Brutto-Stundenlohn (€)'})).toHaveValue('8,5');
});
