import userEvent from '@testing-library/user-event';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../components/ui/toast';
import Einkaufsanfragen from './Einkaufsanfragen';

describe('Einkaufsanfragen', () => {
  beforeEach(() => { global.fetch = vi.fn(async () => ({ ok: true, json: async () => ({ content: [{ id: 41, paNummer: 'PA-00041', revisionsNummer: 2, status: 'AUSSTEHEND', antwortfrist: '2026-10-01', liefertermin: '2026-10-10', projektIds: [7, 9], antworten: 1, lieferantenAnzahl: 3 }], totalPages: 1, totalElements: 1 }) } as Response)); });
  it('zeigt Anfragenummer, Fristen und lädt die Liste vom Einkauf-Endpunkt', async () => {
    render(<MemoryRouter><ToastProvider><Einkaufsanfragen /></ToastProvider></MemoryRouter>);
    expect(await screen.findByText('PA-00041')).toBeInTheDocument();
    expect(screen.getByText('01.10.2026')).toBeInTheDocument();
    expect(screen.getByText('#7, #9')).toBeInTheDocument();
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
    await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/einkauf/anfragen?page=0&size=20'));
  });
});

it('lädt die nächste Seite und macht ältere Anfragen erreichbar',async()=>{
 const user=userEvent.setup();global.fetch=vi.fn(async(input:RequestInfo|URL)=>({ok:true,json:async()=>({content:[{id:1,paNummer:String(input).includes('page=1')?'PA-ALT':'PA-NEU',revisionsNummer:1,status:'AUSSTEHEND'}],totalPages:2})}as Response));
 render(<MemoryRouter><ToastProvider><Einkaufsanfragen/></ToastProvider></MemoryRouter>);
 await screen.findByText('PA-NEU');await user.click(screen.getByRole('button',{name:'Nächste Seite'}));
 expect(await screen.findByText('PA-ALT')).toBeInTheDocument();expect(screen.getByRole('button',{name:'Nächste Seite'})).toBeDisabled();
});
