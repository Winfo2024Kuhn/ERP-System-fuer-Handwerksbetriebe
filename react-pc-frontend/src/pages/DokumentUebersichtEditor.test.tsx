import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import DokumentUebersichtEditor from './DokumentUebersichtEditor';
import { ToastProvider } from '../components/ui/toast';
import { vergissDokumentPositionen } from '../features/bestellungen/dokumentPositionen';

/** Testdaten mit Dummy-Kunde (DSGVO: kein echter Name). */
const ausgangsDokument = {
    id: 42,
    dokumentNummer: '2026/01/00001',
    typ: 'RECHNUNG',
    datum: '2026-01-05T00:00:00Z',
    betreff: 'Rechnung',
    betragNetto: 100,
    betragBrutto: 119,
    gebucht: false,
    storniert: false,
    digitalAngenommen: false,
    kundeId: 1,
    kundenName: 'Max Mustermann',
    projektId: null,
    projektAuftragsnummer: null,
};

/** Eingangsdokumente: eine Rechnung mit Belegdatum, ein Werkstoffzeugnis nur mit Eingangsdatum. */
function eingangsDokument(id: number, typ: string, nummer: string, dokumentDatum: string | null, eingangsDatum: string | null) {
    return {
        id, dokumentId: id, lieferantId: 3, lieferantName: 'Stahlhandel Beispiel GmbH', dokumentNummer: nummer, typ,
        dokumentDatum, eingangsDatum, betragNetto: null, betragBrutto: null, bezahlt: false, originalDateiname: `${nummer}.pdf`, pdfUrl: null,
    };
}

const eingangsDokumente = [
    eingangsDokument(7, 'RECHNUNG', 'R-900', '2026-04-02', '2026-04-03'),
    eingangsDokument(8, 'WERKSTOFFZEUGNIS', '4107891', null, '2026-03-20'),
    eingangsDokument(9, 'SONSTIG', 'SO-1', '2026-03-01', null),
];

const ZEUGNIS_POSITION = {
    id: 1, positionNr: 1, positionsArt: 'WARE', externeArtikelnummer: null, bezeichnung: 'Flachstahl', menge: 12, mengeneinheit: 'Stk',
    einzelpreis: null, preiseinheit: null, gesamtpreisNetto: null, projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null,
    werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5',
};

/** Antwort des Positionen-Endpoints je Dokument – im Test setzbar. */
let positionenAntwort: (id: string) => { status: number; body: unknown } = () => ({ status: 404, body: {} });

function mockFetch() {
    vi.stubGlobal('fetch', vi.fn((url: string) => {
        if (url.includes('/ausgang')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([ausgangsDokument]) });
        }
        const positionen = /\/api\/bestellungen-uebersicht\/positionen\/(\d+)$/.exec(url);
        if (positionen) {
            const { status, body } = positionenAntwort(positionen[1]);
            return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) });
        }
        if (url.includes('/eingang')) {
            return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(eingangsDokumente) });
        }
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
    }));
}

describe('DokumentUebersichtEditor', () => {
    beforeEach(() => {
        vergissDokumentPositionen();
        positionenAntwort = () => ({ status: 404, body: {} });
        mockFetch();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('öffnet den Dokument-Editor ohne noopener, damit sich der Tab selbst schließen kann', async () => {
        const openMock = vi.fn();
        vi.stubGlobal('open', openMock);
        const user = userEvent.setup();

        render(<ToastProvider><DokumentUebersichtEditor /></ToastProvider>);

        const öffnenButton = await screen.findByTitle('Im Dokument-Editor öffnen', {}, { timeout: 2000 });
        await user.click(öffnenButton);

        expect(openMock).toHaveBeenCalledTimes(1);
        // window.open darf hier NICHT mit 'noopener' aufgerufen werden - der
        // Editor-Tab muss sich per window.close() selbst schließen können,
        // was 'noopener' unterbindet.
        expect(openMock.mock.calls[0]).toHaveLength(2);
    });

    it('zeigt ohne Dokumentdatum das Eingangsdatum mit dezentem Hinweis', async () => {
        const user = userEvent.setup();
        render(<ToastProvider><DokumentUebersichtEditor /></ToastProvider>);
        await user.click(await screen.findByRole('button', { name: 'Eingang' }));

        const zeugnisZeile = (await screen.findByText('4107891')).closest('tr') as HTMLElement;
        const hinweis = within(zeugnisZeile).getByText('Eingang');
        expect(hinweis.parentElement).toHaveAttribute('title', 'Kein Dokumentdatum erkannt – Eingangsdatum');
        expect(hinweis.parentElement?.textContent).toMatch(/20\.0?3\.2026/);

        // Mit Dokumentdatum: nur das Datum, kein Hinweis
        const rechnungsZeile = screen.getByText('R-900').closest('tr') as HTMLElement;
        expect(rechnungsZeile.textContent).toMatch(/2\.0?4\.2026/);
        expect(within(rechnungsZeile).queryByText('Eingang')).not.toBeInTheDocument();
    });

    const positionenAufrufe = () => vi.mocked(fetch).mock.calls.map(c => String(c[0])).filter(u => u.includes('/positionen/'));

    it('klappt Artikelpositionen je Eingangsdokument auf und lädt sie nur einmal', async () => {
        positionenAntwort = id => (id === '8' ? { status: 200, body: { positionen: [ZEUGNIS_POSITION] } } : { status: 404, body: {} });
        const user = userEvent.setup();
        render(<ToastProvider><DokumentUebersichtEditor /></ToastProvider>);
        await user.click(await screen.findByRole('button', { name: 'Eingang' }));

        // Kein Pfeil bei Sonstigem
        await screen.findByText('SO-1');
        expect(screen.queryByRole('button', { name: /Positionen von Sonstiges/ })).not.toBeInTheDocument();
        expect(positionenAufrufe()).toHaveLength(0);

        const knopf = screen.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 anzeigen' });
        await user.click(knopf);
        expect(knopf).toHaveAttribute('aria-expanded', 'true');
        const tabelle = await screen.findByRole('table', { name: 'Artikelpositionen' });
        expect(tabelle).toHaveTextContent('Flachstahl');
        // Werkstoffzeugnis: Werkstoff, Charge und Abmessung stehen dabei
        expect(within(tabelle).getByText('Werkstoff S235JR · Charge 123456 · Abmessung 50 x 5')).toBeInTheDocument();
        expect(positionenAufrufe()).toEqual(['/api/bestellungen-uebersicht/positionen/8']);

        await user.click(screen.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 ausblenden' }));
        expect(screen.queryByRole('table', { name: 'Artikelpositionen' })).not.toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 anzeigen' }));
        expect(screen.getByRole('table', { name: 'Artikelpositionen' })).toBeInTheDocument();
        expect(positionenAufrufe()).toHaveLength(1);

        // Rechnung ohne Positionen (404): Leerzustand
        await user.click(screen.getByRole('button', { name: 'Positionen von Rechnung R-900 anzeigen' }));
        expect(await screen.findByText('Keine Artikelpositionen erkannt.')).toBeInTheDocument();
    });

    it('meldet einen Ladefehler der Positionen per Toast und in der Zeile', async () => {
        positionenAntwort = () => ({ status: 500, body: {} });
        const user = userEvent.setup();
        render(<ToastProvider><DokumentUebersichtEditor /></ToastProvider>);
        await user.click(await screen.findByRole('button', { name: 'Eingang' }));
        await user.click(await screen.findByRole('button', { name: 'Positionen von Rechnung R-900 anzeigen' }));
        await waitFor(() => expect(screen.getAllByText('Artikelpositionen konnten nicht geladen werden.')).toHaveLength(2));
    });
});
