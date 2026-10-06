import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { RechnungSuchenDialog } from './RechnungSuchenDialog';
import type { RechnungsVorschlag } from './rechnungsVorschlag';

vi.mock('../../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

function vorschlag(id: number, nummer: string, quote: number, extra: Partial<RechnungsVorschlag> = {}): RechnungsVorschlag {
    return {
        rechnung: {
            id, typ: 'RECHNUNG', dokumentNummer: nummer, dokumentDatum: '2026-09-01', betragBrutto: 119 * id, betragNetto: 100 * id,
            liefertermin: null, dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}`,
        },
        lieferantName: id === 1 ? 'Erika Musterfrau KG' : 'Max Mustermann GmbH',
        bestellDokumentId: 10, bestellDokumentTyp: 'AUFTRAGSBESTAETIGUNG', bestellDokumentNummer: 'AB-1',
        trefferquote: quote, sicher: quote >= 70, eindeutig: true, gruende: ['Gleicher Lieferant'], ...extra,
    };
}

const kette = {
    id: 'k1', lieferantName: 'Erika Musterfrau KG',
    dokumente: [
        { id: 10, typ: 'AUFTRAGSBESTAETIGUNG' as const, dokumentNummer: 'AB-1', dokumentDatum: '2026-08-01', betragBrutto: 100, betragNetto: 84, liefertermin: null, dateiname: 'ab.pdf', pdfUrl: '/pdf/AB-1' },
        { id: 11, typ: 'LIEFERSCHEIN' as const, dokumentNummer: 'LS-1', dokumentDatum: '2026-08-10', betragBrutto: null, betragNetto: null, liefertermin: null, dateiname: 'ls.pdf', pdfUrl: '/pdf/LS-1' },
    ],
};

function zeige(onClose = vi.fn(), onVerknuepft = vi.fn()) {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <RechnungSuchenDialog kette={kette} onClose={onClose} onVerknuepft={onVerknuepft} />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onClose, onVerknuepft };
}

/** Bestätigt die Rückfrage bei unsicheren Vorschlägen (unter 70 % oder Gleichstand). */
async function bestaetigeRueckfrage() {
    const frage = await screen.findByRole('dialog', { name: 'Rechnung wirklich zuordnen?' });
    await userEvent.click(within(frage).getByRole('button', { name: 'Zuordnen' }));
}

describe('RechnungSuchenDialog', () => {
    beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
    afterEach(() => vi.unstubAllGlobals());

    function antworte(...antworten: Array<{ ok: boolean; body?: unknown }>) {
        const mock = vi.mocked(fetch);
        antworten.forEach(a => mock.mockResolvedValueOnce({ ok: a.ok, json: async () => a.body } as Response));
        return mock;
    }

    it('lädt alle Dokument-IDs, wählt den ersten Vorschlag und verknüpft ihn', async () => {
        const mock = antworte(
            { ok: true, body: [vorschlag(1, 'RE-100', 82), vorschlag(2, 'RE-200', 45, { eindeutig: false })] },
            { ok: true, body: { success: true } },
        );
        const { onVerknuepft } = zeige();
        expect(screen.getByRole('status', { name: 'Rechnungen werden geladen' })).toBeInTheDocument();

        await screen.findByRole('button', { name: /^RE-100/ });
        expect(String(mock.mock.calls[0][0])).toContain('dokumentIds=10&dokumentIds=11');
        expect(screen.getAllByTestId('pdf').map(e => e.textContent)).toEqual(['/pdf/AB-1', '/pdf/RE-100']);
        expect(screen.getByText('82 %')).toBeInTheDocument();
        expect(screen.getByText('Eine weitere Rechnung passt gleich gut.')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: /^RE-200/ }));
        expect(screen.getAllByTestId('pdf')[1]).toHaveTextContent('/pdf/RE-200');

        await userEvent.click(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' }));
        await bestaetigeRueckfrage();
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        const [url, init] = mock.mock.calls[1];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-verknuepfen');
        expect(JSON.parse((init as RequestInit).body as string)).toEqual({ bestellDokumentId: 10, rechnungDokumentId: 2 });
    });

    it('schaltet zwischen den Dokumenten der Bestellung um', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RE-100', 82)] });
        zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        await userEvent.click(screen.getByRole('tab', { name: /Lieferschein LS-1/ }));
        expect(screen.getAllByTestId('pdf')[0]).toHaveTextContent('/pdf/LS-1');
    });

    it('filtert die Liste per Suche und leert sie wieder', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RE-100', 82), vorschlag(2, 'RE-200', 45)] });
        zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        await userEvent.type(screen.getByLabelText('Rechnungen durchsuchen'), 'RE-200');
        const liste = screen.getByRole('list', { name: 'Rechnungen' });
        expect(within(liste).queryByText('RE-100')).not.toBeInTheDocument();
        expect(within(liste).getByText('RE-200')).toBeInTheDocument();

        await userEvent.clear(screen.getByLabelText('Rechnungen durchsuchen'));
        await userEvent.type(screen.getByLabelText('Rechnungen durchsuchen'), 'zzz');
        expect(screen.getByText(/Keine Rechnung passt zu/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Suche löschen' }));
        expect(screen.getByRole('list', { name: 'Rechnungen' })).toBeInTheDocument();
    });

    it('zeigt einen ehrlichen Leerzustand mit Hochladen und Suche bei anderen Lieferanten', async () => {
        const mock = antworte({ ok: true, body: [] }, { ok: true, body: [vorschlag(2, 'RE-200', 30, { gruende: ['Anderer Lieferant'] })] });
        zeige();
        expect(await screen.findByText('Von Erika Musterfrau KG ist noch keine Rechnung da.')).toBeInTheDocument();
        expect(screen.getByText('0 Rechnungen von Erika Musterfrau KG')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Rechnung hochladen/ })).toBeEnabled();
        expect(String(mock.mock.calls[0][0])).toContain('alleLieferanten=false');

        await userEvent.click(screen.getByRole('button', { name: 'Bei anderen Lieferanten suchen' }));
        await screen.findByRole('button', { name: /^RE-200/ });
        expect(String(mock.mock.calls[1][0])).toContain('alleLieferanten=true');
        expect(screen.getByText('1 Rechnung von allen Lieferanten')).toBeInTheDocument();
        expect(screen.getByText('Anderer Lieferant')).toBeInTheDocument();
    });

    it('lädt über den Link mit allen Lieferanten und wieder zurück', async () => {
        const mock = antworte(
            { ok: true, body: [vorschlag(1, 'RE-100', 82)] },
            { ok: true, body: [vorschlag(1, 'RE-100', 82), vorschlag(2, 'RE-200', 45)] },
            { ok: true, body: [vorschlag(1, 'RE-100', 82)] },
        );
        zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        expect(screen.getByText('1 Rechnung von Erika Musterfrau KG')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Auch bei anderen Lieferanten suchen' }));
        await screen.findByRole('button', { name: /^RE-200/ });
        expect(String(mock.mock.calls[1][0])).toContain('alleLieferanten=true');
        expect(screen.getByText('2 Rechnungen von allen Lieferanten')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Nur Erika Musterfrau KG zeigen' }));
        await waitFor(() => expect(screen.queryByRole('button', { name: /^RE-200/ })).not.toBeInTheDocument());
        expect(String(mock.mock.calls[2][0])).toContain('alleLieferanten=false');
    });

    it('zeigt Tags für ausgeblendete und schon zugeordnete Rechnungen und fragt nach Teillieferung', async () => {
        const schonZugeordnet = vorschlag(1, 'RE-100', 82, { gehoertSchonZu: 'Lieferschein LS-9' });
        schonZugeordnet.rechnung.ausgeblendet = true;
        const mock = antworte({ ok: true, body: [schonZugeordnet] }, { ok: true, body: { success: true } });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        expect(screen.getByText('Gehört schon zu Lieferschein LS-9 – Teillieferung')).toBeInTheDocument();
        expect(screen.getByText('ausgeblendet')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' }));
        const frage = await screen.findByRole('dialog', { name: 'Rechnung gehört schon zu einer Bestellung' });
        expect(within(frage).getByText(/Ist das eine Teillieferung\? Dann wird beides zu einer Bestellung zusammengefasst\./)).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Zusammenfassen' }));
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(JSON.parse((mock.mock.calls[1][1] as RequestInit).body as string)).toEqual({ bestellDokumentId: 10, rechnungDokumentId: 1 });
    });

    it('hebt Vorschläge mit 0 % nicht hervor und wählt sie nicht vor', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RE-100', 0, { eindeutig: false }), vorschlag(2, 'RE-200', 0, { eindeutig: false })] });
        zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        expect(screen.queryByText('0 %')).not.toBeInTheDocument();
        expect(screen.queryByText('Eine weitere Rechnung passt gleich gut.')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: /^RE-100/ })).toHaveAttribute('aria-pressed', 'false');
        expect(screen.getByText('Noch keine Rechnung gewählt.')).toBeInTheDocument();
    });

    it('lädt im Leerzustand eine Rechnung für das jüngste Bestelldokument hoch', async () => {
        const mock = antworte({ ok: true, body: [] }, { ok: true, body: { id: 77, typ: 'RECHNUNG' } });
        const { onVerknuepft } = zeige();
        await screen.findByText('Von Erika Musterfrau KG ist noch keine Rechnung da.');
        const datei = new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
        await userEvent.upload(screen.getByTestId('rechnung-datei'), datei);
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        const [url, init] = mock.mock.calls[1];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-hochladen');
        expect(((init as RequestInit).body as FormData).get('bestellDokumentId')).toBe('11');
    });

    it('zeigt Fehler als Toast, bietet Wiederholen an', async () => {
        antworte({ ok: false, body: { message: 'Keine Auftragsbestätigung' } }, { ok: true, body: [vorschlag(1, 'RE-100', 82)] });
        zeige();
        expect(await screen.findByText('Die Rechnungen konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.getAllByText('Keine Auftragsbestätigung').length).toBeGreaterThan(0);
        await userEvent.click(screen.getByRole('button', { name: /Nochmal versuchen/ }));
        await screen.findByRole('button', { name: /^RE-100/ });
    });

    it('meldet Fehler beim Verknüpfen und bleibt offen', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RE-100', 82)] }, { ok: false, body: { message: 'Falscher Typ' } });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        await userEvent.click(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' }));
        expect(await screen.findByText('Falscher Typ')).toBeInTheDocument();
        expect(onVerknuepft).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' })).toBeEnabled();
    });

    it('öffnet je Rechnung ein großes Vorschaufenster und ordnet von dort zu', async () => {
        const mock = antworte(
            { ok: true, body: [vorschlag(1, 'RE-100', 82), vorschlag(2, 'RE-200', 45)] },
            { ok: true, body: { success: true } },
        );
        const { onVerknuepft, onClose } = zeige();
        await screen.findByRole('button', { name: /^RE-100/ });

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau Rechnung RE-200' }));
        const fenster = await screen.findByRole('dialog', { name: 'Rechnung RE-200' });
        expect(within(fenster).getByTestId('pdf')).toHaveTextContent('/pdf/RE-200');
        expect(within(fenster).getByText('45 %')).toBeInTheDocument();

        await userEvent.click(within(fenster).getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rechnung RE-200' })).not.toBeInTheDocument());
        expect(onClose).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau Rechnung RE-200' }));
        const nochmal = await screen.findByRole('dialog', { name: 'Rechnung RE-200' });
        await userEvent.click(within(nochmal).getByRole('button', { name: 'Diese Rechnung gehört dazu' }));
        await bestaetigeRueckfrage();
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(JSON.parse((mock.mock.calls[1][1] as RequestInit).body as string)).toEqual({ bestellDokumentId: 10, rechnungDokumentId: 2 });
    });

    it('ordnet eine Rechnung direkt aus der Liste zu', async () => {
        const mock = antworte(
            { ok: true, body: [vorschlag(1, 'RE-100', 82), vorschlag(2, 'RE-200', 45)] },
            { ok: true, body: { success: true } },
        );
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /^RE-100/ });

        await userEvent.click(screen.getByRole('button', { name: 'Rechnung RE-200 zuordnen' }));
        await bestaetigeRueckfrage();
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(JSON.parse((mock.mock.calls[1][1] as RequestInit).body as string)).toEqual({ bestellDokumentId: 10, rechnungDokumentId: 2 });
    });

    it('sperrt die Vorschau, wenn die Rechnung kein PDF hat', async () => {
        const ohnePdf = vorschlag(1, 'RE-100', 82);
        ohnePdf.rechnung.pdfUrl = null;
        antworte({ ok: true, body: [ohnePdf] });
        zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        expect(screen.getByRole('button', { name: 'Vorschau Rechnung RE-100' })).toBeDisabled();
        expect(screen.getByText('Zu dieser Rechnung gibt es keine Vorschau.')).toBeInTheDocument();
    });

    it('wählt eine geratene Rechnung nicht vor und fragt vor dem Zuordnen nach', async () => {
        const mock = antworte({ ok: true, body: [vorschlag(1, 'RE-100', 15), vorschlag(2, 'RE-200', 10)] });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /^RE-100/ });
        expect(screen.getByRole('button', { name: 'Diese Rechnung gehört dazu' })).toBeDisabled();
        expect(screen.getByText('Noch keine Rechnung gewählt.')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Rechnung RE-100 zuordnen' }));
        const frage = await screen.findByRole('dialog', { name: 'Rechnung wirklich zuordnen?' });
        expect(within(frage).getByText(/nur bei 15 %/)).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rechnung wirklich zuordnen?' })).not.toBeInTheDocument());
        expect(mock).toHaveBeenCalledTimes(1);
        expect(onVerknuepft).not.toHaveBeenCalled();
    });

    it('schließt über Abbrechen', async () => {
        antworte({ ok: true, body: [] });
        const { onClose } = zeige();
        await screen.findByText('Von Erika Musterfrau KG ist noch keine Rechnung da.');
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onClose).toHaveBeenCalled();
    });
});
