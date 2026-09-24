import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LieferantKontakte } from './LieferantKontakte';
import { ToastProvider } from '../../../components/ui/toast';

const kontakte = [{ id: 1, version: 0, name: 'M. Beispiel', anrede: null, email: 'anfrage@example.com', standardAnfrage: true, standardBestellung: false, aktiv: true }, { id: 2, version: 0, name: 'Einkauf', anrede: null, email: 'bestellung@example.com', standardAnfrage: false, standardBestellung: true, aktiv: true }];

describe('LieferantKontakte', () => {
  beforeEach(() => { global.fetch = vi.fn(async (url: RequestInfo | URL) => String(url).includes('/einkauf-kontakte') ? ({ ok: true, json: async () => kontakte } as Response) : ({ ok: false, status: 404 } as Response)); });
  it('zeigt getrennte Standardadressen für Anfrage und Bestellung und bewahrt Kennnummern als Text', async () => {
    render(<ToastProvider><LieferantKontakte lieferantId={7} readOnly={false} /></ToastProvider>);
    expect(await screen.findByText('anfrage@example.com')).toBeInTheDocument();
    expect(screen.getByText('bestellung@example.com')).toBeInTheDocument();
    expect(screen.getAllByText('Standard für Anfragen')).toHaveLength(2);
    expect(screen.getAllByText('Standard für Bestellungen')).toHaveLength(2);
  });
});
