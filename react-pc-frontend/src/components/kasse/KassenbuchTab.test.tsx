import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ToastProvider } from '../ui/toast';
import { KassenbuchTab } from './KassenbuchTab';
import type { Kassenbuch } from '../../types';

const kassenbuch: Kassenbuch = {
    saldoStart: 100, saldoEnde: 100, summeEinnahmen: 0, summeAusgaben: 0,
    summePrivateinlagen: 0, summePrivatentnahmen: 0, bewegungen: [],
};
const props = {
    sachkonten: [], kassenbuch, kassenLoading: false,
    kasseVon: '2026-09-01', kasseBis: '2026-09-30', kasseSearch: '',
    onVonChange: vi.fn(), onBisChange: vi.fn(), onSearchChange: vi.fn(),
    onSelectBeleg: vi.fn(), onGeaendert: vi.fn(),
};
let aktuellerSaldo = 20;
beforeEach(() => {
    aktuellerSaldo = 20;
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ saldo: aktuellerSaldo, mindestbestand: 50 }) })));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it('lädt den aktuellen Stand nur einmal und zeigt ihn einmal mit Mindestbestandwarnung', async () => {
    render(<ToastProvider><KassenbuchTab {...props} /></ToastProvider>);
    await waitFor(() => expect(screen.getByTestId('kasse-jetzt')).toHaveTextContent('20,00 €'));
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText('20,00 €')).toHaveLength(1);
    expect(screen.queryByText('Aktueller Kassenstand')).not.toBeInTheDocument();
    const anzeige = screen.getByTestId('kasse-jetzt').parentElement!;
    expect(within(anzeige).getByText(/Mindestbestand: 50,00 €/)).toBeVisible();
    expect(within(anzeige).getByText(/unter Mindestbestand/)).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Bank → Kasse', exact: true }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Betrag (€)' }), { target: { value: '10' } });
    expect(within(screen.getByRole('dialog')).getByText('30,00 €')).toBeVisible();
    expect(fetch).toHaveBeenCalledTimes(1);
});

it('aktualisiert nach einer Änderung auch die gemeinsame Warnung', async () => {
    const view = render(<ToastProvider><KassenbuchTab {...props} /></ToastProvider>);
    await waitFor(() => expect(screen.getByTestId('kasse-jetzt')).toHaveTextContent('20,00 €'));
    aktuellerSaldo = 80;
    view.rerender(<ToastProvider><KassenbuchTab {...props} kassenbuch={{ ...kassenbuch }} /></ToastProvider>);
    await waitFor(() => expect(screen.getByTestId('kasse-jetzt')).toHaveTextContent('80,00 €'));
    expect(screen.queryByText(/unter Mindestbestand/)).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
});

it('erhält das aria-controls-Ziel beim Einklappen der Erklärung', async () => {
    render(<ToastProvider><KassenbuchTab {...props} /></ToastProvider>);
    await waitFor(() => expect(screen.getByTestId('kasse-jetzt')).toHaveTextContent('20,00 €'));
    const toggle = screen.getByRole('button', { name: 'So funktioniert die Kasse', exact: true });
    const id = toggle.getAttribute('aria-controls')!;
    fireEvent.click(screen.getByRole('button', { name: 'Erklärung ausblenden' }));
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(document.getElementById(id)).toBeInTheDocument();
    expect(document.getElementById(id)).not.toBeVisible();
});
