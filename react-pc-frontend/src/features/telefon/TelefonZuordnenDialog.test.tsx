import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { TelefonZuordnenDialog } from './TelefonZuordnenDialog';
import { anruf, antwort, aufrufe, KANZLEI_BEISPIEL, KUNDE_MAX, stubbeFetch } from './telefonTestdaten';
import type { SteuerberaterAuswahl } from './types';

const AUSWAHL_BEISPIEL: SteuerberaterAuswahl = {
    id: 30,
    name: 'Kanzlei Beispiel',
    ansprechpartner: [
        { id: 40, name: 'Erika Beispiel', telefon: null },
        { id: 41, name: 'Max Muster', telefon: '0931 66666' },
    ],
};
const AUSWAHL_OHNE_PERSONEN: SteuerberaterAuswahl = { id: 31, name: 'Steuerbüro Muster', ansprechpartner: [] };

function stubbeKanzleien(zuordnen = anruf({ kontakt: KANZLEI_BEISPIEL, zuordnung: 'MANUELL' })) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/telefon/steuerberater' ? antwort([AUSWAHL_BEISPIEL, AUSWAHL_OHNE_PERSONEN]) : undefined),
        (url, init) => (url.pathname.endsWith('/zuordnung') && init?.method === 'POST' ? antwort(zuordnen) : undefined),
    );
}

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
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: 7, lieferantId: null, steuerberaterId: null, nummerMerken: true, ansprechpartnerId: null });
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
        expect(JSON.parse(String(init?.body))).toEqual({ kundeId: null, lieferantId: 3, steuerberaterId: null, nummerMerken: false, ansprechpartnerId: null });
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

    describe('Steuerberater', () => {
        it('lädt die Kanzleien erst bei Bedarf und ordnet eine zu', async () => {
            const fetchMock = stubbeKanzleien();
            const { onZugeordnet } = zeige();
            expect(aufrufe(fetchMock, '/api/telefon/steuerberater')).toHaveLength(0);

            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            const auswahl = await screen.findByRole('radiogroup', { name: 'Steuerberater wählen' });
            fireEvent.click(within(auswahl).getByRole('radio', { name: 'Steuerbüro Muster' }));
            expect(screen.queryByRole('checkbox', { name: /Nummer beim Kontakt merken/ })).toBeNull();
            expect(screen.getByText(/unter Firma › Steuerberater/)).toBeInTheDocument();
            expect(within(auswahl).getByRole('radio', { name: 'Steuerbüro Muster' })).toHaveAttribute('aria-checked', 'true');
            fireEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));

            await waitFor(() => expect(onZugeordnet).toHaveBeenCalled());
            const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
            expect(JSON.parse(String(init?.body))).toEqual({ kundeId: null, lieferantId: null, steuerberaterId: 31, nummerMerken: false, ansprechpartnerId: null });
            expect(aufrufe(fetchMock, '/api/telefon/steuerberater')).toHaveLength(1);
        });

        it('speichert die Nummer beim gewählten Ansprechpartner', async () => {
            const fetchMock = stubbeKanzleien(anruf({ kontakt: { ...KANZLEI_BEISPIEL, ansprechpartner: 'Erika Beispiel' }, zuordnung: 'MANUELL' }));
            const { onZugeordnet } = zeige();
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            fireEvent.click(within(await screen.findByRole('radiogroup', { name: 'Steuerberater wählen' })).getByRole('radio', { name: 'Kanzlei Beispiel' }));

            const personen = screen.getByRole('radiogroup', { name: 'Ansprechpartner wählen' });
            expect(within(personen).getByRole('radio', { name: /Nicht speichern/ })).toHaveAttribute('aria-checked', 'true');
            // Wer schon eine Nummer hat, wird nicht überschrieben.
            const max = within(personen).getByRole('radio', { name: /Max Muster/ });
            expect(max).toBeDisabled();
            expect(max).toHaveTextContent('Hat schon 0931 66666');
            expect(max).toHaveAttribute('title', expect.stringContaining('Firma › Steuerberater'));

            fireEvent.click(within(personen).getByRole('radio', { name: /Erika Beispiel/ }));
            expect(within(personen).getByRole('radio', { name: /Erika Beispiel/ })).toHaveAttribute('aria-checked', 'true');
            fireEvent.click(screen.getByRole('button', { name: 'Zuordnen' }));

            await waitFor(() => expect(onZugeordnet).toHaveBeenCalled());
            const [, init] = aufrufe(fetchMock, '/api/telefon/anrufe/1/zuordnung', 'POST')[0];
            expect(JSON.parse(String(init?.body))).toEqual({ kundeId: null, lieferantId: null, steuerberaterId: 30, nummerMerken: true, ansprechpartnerId: 40 });
            expect(await screen.findByText('Anruf Kanzlei Beispiel zugeordnet. Nummer bei Erika Beispiel gespeichert.')).toBeInTheDocument();
        });

        it('vergisst den Ansprechpartner beim Wechsel der Kanzlei und zeigt ohne Ansprechpartner den Hinweis', async () => {
            const fetchMock = stubbeKanzleien();
            zeige();
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            const kanzleien = await screen.findByRole('radiogroup', { name: 'Steuerberater wählen' });
            expect(screen.getByText(/unter Firma › Steuerberater bei der Kanzlei/)).toBeInTheDocument();
            fireEvent.click(within(kanzleien).getByRole('radio', { name: 'Kanzlei Beispiel' }));
            fireEvent.click(screen.getByRole('radio', { name: /Erika Beispiel/ }));
            fireEvent.click(within(kanzleien).getByRole('radio', { name: 'Kanzlei Beispiel' }));
            expect(screen.getByRole('radio', { name: /Erika Beispiel/ })).toHaveAttribute('aria-checked', 'true');

            fireEvent.click(within(kanzleien).getByRole('radio', { name: 'Steuerbüro Muster' }));
            expect(screen.queryByRole('radiogroup', { name: 'Ansprechpartner wählen' })).toBeNull();
            expect(screen.getByText(/unter Firma › Steuerberater bei der Kanzlei/)).toBeInTheDocument();
            fireEvent.click(within(kanzleien).getByRole('radio', { name: 'Kanzlei Beispiel' }));
            expect(screen.getByRole('radio', { name: /Nicht speichern/ })).toHaveAttribute('aria-checked', 'true');

            fireEvent.click(screen.getByRole('radio', { name: 'Kunde' }));
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            expect(screen.queryByRole('radiogroup', { name: 'Ansprechpartner wählen' })).toBeNull();
            expect(aufrufe(fetchMock, '/api/telefon/steuerberater')).toHaveLength(1);
        });

        it('bietet bei unterdrückter Nummer keine Ansprechpartner an', async () => {
            stubbeKanzleien();
            zeige('');
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            fireEvent.click(within(await screen.findByRole('radiogroup', { name: 'Steuerberater wählen' })).getByRole('radio', { name: 'Kanzlei Beispiel' }));
            expect(screen.queryByRole('radiogroup', { name: 'Ansprechpartner wählen' })).toBeNull();
        });

        it('erklärt, wenn noch keine Kanzlei angelegt ist', async () => {
            stubbeFetch((url) => (url.pathname === '/api/telefon/steuerberater' ? antwort([]) : undefined));
            zeige();
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            expect(await screen.findByText(/Noch kein Steuerberater angelegt/)).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Zuordnen' })).toBeDisabled();
        });

        it('meldet einen Ladefehler im Dialog und als Toast', async () => {
            stubbeFetch((url) => (url.pathname === '/api/telefon/steuerberater' ? antwort({ message: 'Kanzleien nicht erreichbar' }, 500) : undefined));
            zeige();
            fireEvent.click(screen.getByRole('radio', { name: 'Steuerberater' }));
            expect(await screen.findAllByText('Kanzleien nicht erreichbar')).toHaveLength(2);
        });
    });
});
