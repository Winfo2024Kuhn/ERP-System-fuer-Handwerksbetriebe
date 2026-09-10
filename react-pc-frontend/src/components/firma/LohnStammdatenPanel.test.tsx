import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { it, expect, vi, afterEach } from 'vitest';
import { LohnStammdatenPanel } from './LohnStammdatenPanel';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';
afterEach(()=>vi.unstubAllGlobals());
it('prüft Pflichtprozentsatz und übernimmt Komma numerisch',async()=>{
 const user=userEvent.setup();const fetcher=vi.fn().mockResolvedValue({ok:true,json:async()=>[]});vi.stubGlobal('fetch',fetcher);
 render(<ToastProvider><ConfirmProvider><LohnStammdatenPanel/></ConfirmProvider></ToastProvider>);
 await user.click(screen.getByRole('button',{name:'Neue Krankenkasse'}));
 await user.type(screen.getAllByRole('textbox')[0],'Testkasse');
 const input=screen.getByRole('textbox',{name:'Zusatzbeitrag in %'});
 await user.click(screen.getByRole('button',{name:'Speichern'}));expect(fetcher.mock.calls.some(c=>c[1]?.method==='POST')).toBe(false);
 await user.type(input,'8,5');await user.click(screen.getByRole('button',{name:'Speichern'}));
 const call=fetcher.mock.calls.find(c=>c[1]?.method==='POST');expect(JSON.parse(call?.[1].body)).toMatchObject({zusatzbeitragProzent:8.5});
});

it('zeigt einen Netzwerkfehler und erhält den Stammdatenentwurf', async () => {
 const user=userEvent.setup();const fetcher=vi.fn().mockImplementation(async (_url,init)=>{if(init)throw new Error('offline');return {ok:true,json:async()=>[]};});vi.stubGlobal('fetch',fetcher);
 render(<ToastProvider><ConfirmProvider><LohnStammdatenPanel/></ConfirmProvider></ToastProvider>);
 await user.click(screen.getByRole('button',{name:'Neue Krankenkasse'}));await user.type(screen.getAllByRole('textbox')[0],'Testkasse');
 await user.type(screen.getByRole('textbox',{name:'Zusatzbeitrag in %'}),'8,5');
 await user.click(screen.getByRole('button',{name:'Speichern'}));
 expect(await screen.findByText('Krankenkasse konnte nicht gespeichert werden.')).toBeVisible();
 expect(screen.getByRole('textbox',{name:'Zusatzbeitrag in %'})).toHaveValue('8,5');
});
