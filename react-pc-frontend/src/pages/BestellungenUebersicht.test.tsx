import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';
import BestellungenUebersicht from './BestellungenUebersicht';

vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

const heute = new Date();
const vorTagen = (tage: number) => {
    const d = new Date(heute);
    d.setDate(d.getDate() - tage);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

function dok(id: number, typ: string, nummer: string, datum: string, extra: Record<string, unknown> = {}) {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: null, betragNetto: null,
        liefertermin: null, dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}.pdf`, ausgeblendet: false, ...extra,
    };
}

const uebersicht = {
    offeneAnfragen: [],
    laufendeBestellungen: [{
        id: 'k1', lieferantId: 1, lieferantName: 'Max Mustermann GmbH',
        dokumente: [
            dok(1, 'AUFTRAGSBESTAETIGUNG', 'AB-1', vorTagen(10)),
            dok(2, 'LIEFERSCHEIN', 'LS-1', vorTagen(5)),
            dok(3, 'LIEFERSCHEIN', 'LS-2', vorTagen(4)),
        ],
        verbindungen: [{ vonId: 2, zuId: 1 }, { vonId: 3, zuId: 1 }],
        rechnungsVorschlag: null,
    }],
    abgeschlossen: [],
    zugeordnet: [],
    ausgeblendet: [{
        id: 'k2', lieferantId: 2, lieferantName: 'Erika Musterfrau KG',
        dokumente: [
            dok(4, 'LIEFERSCHEIN', 'LS-9', vorTagen(30)),
            dok(5, 'RECHNUNG', 'RE-9', vorTagen(20), { ausgeblendet: true, betragBrutto: 119 }),
        ],
        verbindungen: [{ vonId: 5, zuId: 4 }],
    }],
};

/** Vorschläge für „Dokument hinzufügen“ – je Test setzbar. */
let kettenVorschlaege: unknown[] = [];

function antwortFuer(url: string): Response {
    if (url === '/api/bestellungen-uebersicht') return { ok: true, json: async () => uebersicht } as Response;
    if (url.startsWith('/api/bestellungen-uebersicht/belege-offen')) return { ok: true, json: async () => [] } as Response;
    if (url.startsWith('/api/bestellungen-uebersicht/ketten-vorschlaege')) return { ok: true, json: async () => kettenVorschlaege } as Response;
    if (url === '/api/bestellungen-uebersicht/ketten-verknuepfen') return { ok: true, json: async () => ({ success: true }) } as Response;
    if (url === '/api/bestellungen-uebersicht/abhaengen') return { ok: true, json: async () => ({ geloest: 1 }) } as Response;
    return { ok: false, json: async () => ({}) } as Response;
}

function zeige() {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <BestellungenUebersicht />
            </ConfirmProvider>
        </ToastProvider>,
    );
}

describe('BestellungenUebersicht – Gabel', () => {
    beforeEach(() => {
        kettenVorschlaege = [];
        vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => antwortFuer(String(url))));
    });
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt laufende Bestellungen als Gabel mit offenem Ende und Suche', async () => {
        zeige();
        const liste = await screen.findByRole('list', { name: 'Belege der Bestellung' });
        expect(within(liste).getAllByRole('listitem')).toHaveLength(4);
        expect(within(liste).getByText('Rechnung fehlt noch')).toBeInTheDocument();
        // Karten im Mauerwerk statt im Raster
        expect(liste.closest('.columns-1')).not.toBeNull();

        await userEvent.click(within(liste).getByRole('button', { name: 'Suchen' }));
        expect(await screen.findByRole('dialog', { name: /Rechnung suchen – Max Mustermann GmbH/ })).toBeInTheDocument();
        await screen.findByText('Von Max Mustermann GmbH ist noch keine Rechnung da.');
        const vorschlagsAufruf = vi.mocked(fetch).mock.calls.map(c => String(c[0])).find(u => u.includes('ketten-vorschlaege'));
        expect(vorschlagsAufruf).toContain('dokumentIds=1&dokumentIds=2&dokumentIds=3&alleLieferanten=false&typ=RECHNUNG');
    });

    it('„Dokument hinzufügen“ in der Fußzeile sucht alle Arten und ordnet zu', async () => {
        kettenVorschlaege = [{
            dokument: dok(9, 'WERKSTOFFZEUGNIS', 'WZ-9', vorTagen(4)),
            lieferantName: 'Max Mustermann GmbH', kettenDokumentId: 2, kettenDokumentTyp: 'LIEFERSCHEIN', kettenDokumentNummer: 'LS-1',
            trefferquote: 90, sicher: true, eindeutig: true, gruende: ['Belegnummer wird genannt'], gehoertSchonZu: null,
        }];
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('button', { name: 'Dokument hinzufügen' }));

        const dialog = await screen.findByRole('dialog', { name: /Dokument zur Kette hinzufügen – Max Mustermann GmbH/ });
        await within(dialog).findByText('Belegnummer wird genannt');
        const vorschlagsAufruf = vi.mocked(fetch).mock.calls.map(c => String(c[0])).find(u => u.includes('ketten-vorschlaege'));
        expect(vorschlagsAufruf).toBe('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=1&dokumentIds=2&dokumentIds=3&alleLieferanten=false');
        expect(within(dialog).getByRole('button', { name: 'Alle' })).toHaveAttribute('aria-pressed', 'true');

        await userEvent.click(within(dialog).getByRole('button', { name: 'Werkstoffzeugnis WZ-9 gehört dazu' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: /Dokument zur Kette hinzufügen/ })).not.toBeInTheDocument());
        const post = vi.mocked(fetch).mock.calls.find(c => String(c[0]) === '/api/bestellungen-uebersicht/ketten-verknuepfen');
        expect(JSON.parse((post?.[1] as RequestInit).body as string)).toEqual({ kettenDokumentId: 2, dokumentId: 9 });
        // danach wird still neu geladen
        await waitFor(() => expect(vi.mocked(fetch).mock.calls.filter(c => String(c[0]) === '/api/bestellungen-uebersicht')).toHaveLength(2));
    });

    it('zeigt „Dokument hinzufügen“ nicht bei ausgeblendeten Bestellungen', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        expect(screen.getByRole('button', { name: 'Dokument hinzufügen' })).toBeInTheDocument();
        await userEvent.click(screen.getByRole('tab', { name: /Ausgeblendet/ }));
        await screen.findByText('Erika Musterfrau KG');
        expect(screen.queryByRole('button', { name: 'Dokument hinzufügen' })).not.toBeInTheDocument();
    });

    it('öffnet einen Beleg per Zeilenklick in der Vorschau', async () => {
        zeige();
        await userEvent.click(await screen.findByRole('button', { name: /^Lieferschein LS-2 \d/ }));
        expect(await screen.findByTestId('pdf')).toHaveTextContent('/pdf/LS-2.pdf');
    });

    it('zeigt ausgeblendete Rechnungen gedämpft, öffnet sie und hängt nach Rückfrage ab', async () => {
        zeige();
        await screen.findByRole('list', { name: 'Belege der Bestellung' });
        await userEvent.click(screen.getByRole('tab', { name: /Ausgeblendet/ }));
        const karte = await screen.findByText('Erika Musterfrau KG');
        const liste = within(karte.closest('div.p-4') as HTMLElement).getByRole('list', { name: 'Belege der Bestellung' });
        expect(within(liste).getByText('ausgeblendet')).toBeInTheDocument();
        expect(within(liste).queryByText('Rechnung fehlt noch')).not.toBeInTheDocument();

        await userEvent.click(within(liste).getByRole('button', { name: 'Rechnung RE-9 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        await waitFor(() => expect(vi.mocked(fetch).mock.calls.some(c => String(c[0]) === '/api/bestellungen-uebersicht/abhaengen')).toBe(true));
        // danach wird still neu geladen
        await waitFor(() => expect(vi.mocked(fetch).mock.calls.filter(c => String(c[0]) === '/api/bestellungen-uebersicht')).toHaveLength(2));
    });
});
