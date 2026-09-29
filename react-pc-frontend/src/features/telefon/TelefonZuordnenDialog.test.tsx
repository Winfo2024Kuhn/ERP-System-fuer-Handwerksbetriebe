import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { TelefonZuordnenDialog } from './TelefonZuordnenDialog';
import { anruf, antwort, aufrufe, KUNDE_MAX, stubbeFetch } from './telefonTestdaten';

function stubbeSuche() {
    return stubbeFetch(
        (url) => (url.pathname === '/api/kunden'
            ? antwort({ kunden: [{ id: 7, name: 'Max Mustermann', kundennummer: 'K-1007', ort: 'Musterstadt' }], gesamt: 1 })
            : undefined),
        (url) => (url.pathname === '/api/lieferanten'
            ? antwort({ lieferanten: [{ id: 3, lieferantenname: 'Mustermann GmbH', ort: 'Würzburg', istAktiv: true }], gesamt: 1 })
            : undefined),
        (url, init) => (url.pathname.endsWith('/zuordnung') && init?.method === 'POST'
            ? antwort(anruf({ kontakt: KUNDE_MAX, zuordnung: 'MANUELL' }))
            : undefined),
    );
}

function zeige(nummer = '0931 1234567') {
    const onZugeordnet = vi.fn();
    const onSchliessen = vi.fn();
    render(
        <ToastProvider>
            <TelefonZuordnenDialog offen ziel={{ art: 'anruf', id: 1 }} nummer={nummer} onSchliessen={onSchliessen} onZugeordnet={onZugeordnet} />
        </ToastProvider>,
    );
    return { onZugeordnet, onSchliessen };
}

describe('TelefonZuordnenDialog', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('ordnet einen Kunden zu und merkt die Nummer', async () => {
        const fetchMock = stubbeSuche();
        const { onZugeordnet, onSchliessen } = zeige();
        const knopf = screen.getByRole('button', { name: 'Zuordnen' });
        expect(knopf).toBeDisabled();
        expect(screen.getByRole('checkbox', { name: /Nummer beim Kontakt merken/ })).toBeChecked();

        fireEvent.click(screen.getByRole('button', { name: /Kunde suchen/ }));
        fireEvent.click(await screen.findByRole('button', { name: /Max Mustermann/ }));
        expect(screen.getByText('K-1007 · Musterstadt')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));
        await waitFor(() => expect(onZugeordnet).toHaveBeenCalled());
        const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: 7, lieferantId: null, nummerMerken: true });
        expect(onSchliessen).toHaveBeenCalled();
    });

    it('ordnet einen Lieferanten ohne Merken zu', async () => {
        const fetchMock = stubbeSuche();
        zeige();
        fireEvent.click(screen.getByRole('radio', { name: 'Lieferant' }));
        fireEvent.click(screen.getByRole('button', { name: /Lieferant suchen/ }));
        fireEvent.click(await screen.findByRole('button', { name: /Mustermann GmbH/ }));
        fireEvent.click(screen.getByRole('checkbox', { name: /Nummer beim Kontakt merken/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')).toHaveLength(1));
        const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: null, lieferantId: 3, nummerMerken: false });
    });

    it('blendet "Nummer merken" bei unterdrückter Nummer aus', () => {
        stubbeSuche();
        zeige('');
        expect(screen.getByText('Nummer unterdrückt')).toBeInTheDocument();
        expect(screen.queryByRole('checkbox', { name: /Nummer beim Kontakt merken/ })).toBeNull();
    });

    it('zeigt Fehler des Servers im Toast und bleibt offen', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/kunden' ? antwort({ kunden: [{ id: 7, name: 'Max Mustermann' }], gesamt: 1 }) : undefined),
            (url) => (url.pathname.endsWith('/zuordnung') ? antwort({ message: 'Kunde nicht gefunden.' }, 404) : undefined),
        );
        const { onSchliessen } = zeige();
        fireEvent.click(screen.getByRole('button', { name: /Kunde suchen/ }));
        fireEvent.click(await screen.findByRole('button', { name: /Max Mustermann/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));
        expect(await screen.findByText('Kunde nicht gefunden.')).toBeInTheDocument();
        expect(onSchliessen).not.toHaveBeenCalled();
    });
});
