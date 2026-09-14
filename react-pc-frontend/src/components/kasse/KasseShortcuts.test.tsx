import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { KasseShortcuts } from './KasseShortcuts';
import { ToastProvider } from '../ui/toast';
const writes: {
    path: string;
    body: Record<string, unknown>;
}[] = [];
beforeEach(() => {
    writes.length = 0;
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
        const path = String(url);
        if (init?.method)
            writes.push({ path, body: JSON.parse(init.body) });
        return { ok: true, json: async () => path.endsWith('saldo') ? { saldo: 50, mindestbestand: 0 } : { mindestbestand: 0, ehegattengehaltAktiv: true, ehegattengehaltBetrag: 12.5, ehegattengehaltTag: 5 } };
    }));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
it('öffnet den zentralen Dialog statt einzelner Kassenaktionen', async () => {
    render(<ToastProvider><KasseShortcuts sachkonten={[]} onChanged={() => { }}/></ToastProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Neue Buchung', exact: true }));
    expect(screen.getByText('Geld eingenommen', { exact: true })).toBeVisible();
    expect(screen.getByText('Geld privat entnommen', { exact: true })).toBeVisible();
});
it('prüft alle Einstellungen vor dem Speichern und erhält Nichtnullwerte', async () => {
    render(<ToastProvider><KasseShortcuts sachkonten={[]} onChanged={() => { }}/></ToastProvider>);
    fireEvent.click(screen.getByTitle('Mindestbestand & Automatik einstellen'));
    const minimum = await screen.findByRole('textbox', { name: 'Mindestbestand (€)' });
    expect(minimum).toHaveValue('0');
    fireEvent.focus(minimum);
    expect(minimum).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
    expect(writes).toHaveLength(0);
    fireEvent.change(minimum, { target: { value: '0' } });
    const amount = screen.getByRole('textbox', { name: 'Monatlicher Betrag (€)' });
    fireEvent.focus(amount);
    expect(amount).toHaveValue('12,5');
    const day = screen.getByRole('textbox', { name: 'Tag des Monats (1–28)' });
    fireEvent.change(day, { target: { value: '1,5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
    expect(writes).toHaveLength(0);
    fireEvent.change(day, { target: { value: '28' } });
    fireEvent.click(screen.getByRole('button', { name: 'Speichern', exact: true }));
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0].body).toMatchObject({ mindestbestand: 0, ehegattengehaltBetrag: 12.5, ehegattengehaltTag: 28 });
});
it('öffnet Ehegattengehalt aus den Kassen-Einstellungen', async () => {
    render(<ToastProvider><KasseShortcuts sachkonten={[]} onChanged={() => { }}/></ToastProvider>);
    fireEvent.click(screen.getByTitle('Mindestbestand & Automatik einstellen'));
    await screen.findByRole('heading', { name: 'Für den Steuerberater' });
    fireEvent.click(screen.getByRole('button', { name: /Jetzt einmalig auszahlen/ }));
    const input = await screen.findByRole('textbox', { name: 'Betrag (€)' });
    await waitFor(() => expect(input).toHaveValue('12,5'));
    fireEvent.change(input, { target: { value: '12,501' } });
    fireEvent.click(screen.getByRole('button', { name: /^(Buchen|Auszahlen|Abhebung buchen|Lohn buchen)$/ }));
    expect(writes).toHaveLength(0);
    fireEvent.change(input, { target: { value: '12,50' } });
    fireEvent.click(screen.getByRole('button', { name: /^(Buchen|Auszahlen|Abhebung buchen|Lohn buchen)$/ }));
    await waitFor(() => expect(writes).toHaveLength(1));
    expect(writes[0].body.betrag).toBe(12.5);
});
it('deaktiviert die Automatik auch nach einem verworfenen unvollständigen Betragsentwurf',async()=>{
 render(<ToastProvider><KasseShortcuts sachkonten={[]} onChanged={()=>{}}/></ToastProvider>);
 fireEvent.click(screen.getByTitle('Mindestbestand & Automatik einstellen'));
 const amount=await screen.findByRole('textbox',{name:'Monatlicher Betrag (€)'});fireEvent.change(amount,{target:{value:'12,'}});
 fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'Speichern',exact:true}));
 await waitFor(()=>expect(writes).toHaveLength(1));expect(writes[0].body).toMatchObject({ehegattengehaltAktiv:false,ehegattengehaltBetrag:12.5});
});
