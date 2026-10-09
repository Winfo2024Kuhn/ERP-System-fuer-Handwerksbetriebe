import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { DokumentSuchenDialog } from './DokumentSuchenDialog';
import type { KettenVorschlag, VorschlagsTyp } from './kettenVorschlag';
import type { KettenDokumentTyp } from './bestellungenListe';

vi.mock('../../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

/** DSGVO: nur Fantasie-Namen. */
function vorschlag(id: number, typ: KettenDokumentTyp, nummer: string, quote: number, extra: Partial<KettenVorschlag> = {}): KettenVorschlag {
    return {
        dokument: {
            id, typ, dokumentNummer: nummer, dokumentDatum: '2026-09-01', betragBrutto: 119 * id, betragNetto: 100 * id,
            liefertermin: null, dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}`,
        },
        lieferantName: id === 1 ? 'Erika Musterfrau KG' : 'Max Mustermann GmbH',
        kettenDokumentId: 11, kettenDokumentTyp: 'LIEFERSCHEIN', kettenDokumentNummer: 'LS-1',
        trefferquote: quote, sicher: quote >= 70, eindeutig: true, gruende: ['Belegnummer wird genannt'], ...extra,
    };
}

const kette = {
    lieferantName: 'Erika Musterfrau KG',
    dokumente: [
        { id: 10, typ: 'AUFTRAGSBESTAETIGUNG' as const, dokumentNummer: 'AB-1', dokumentDatum: '2026-08-01', betragBrutto: 100, betragNetto: 84, liefertermin: null, dateiname: 'ab.pdf', pdfUrl: '/pdf/AB-1' },
        { id: 11, typ: 'LIEFERSCHEIN' as const, dokumentNummer: 'LS-1', dokumentDatum: '2026-08-10', betragBrutto: null, betragNetto: null, liefertermin: null, dateiname: 'ls.pdf', pdfUrl: '/pdf/LS-1' },
    ],
};

function zeige(startTyp: VorschlagsTyp | null = null, onClose = vi.fn(), onVerknuepft = vi.fn()) {
    render(
        <ToastProvider>
            <ConfirmProvider>
                <DokumentSuchenDialog kette={kette} startTyp={startTyp} onClose={onClose} onVerknuepft={onVerknuepft} />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onClose, onVerknuepft };
}

/** Bestätigt die Rückfrage bei unsicheren Vorschlägen (unter 70 % oder Gleichstand). */
async function bestaetigeRueckfrage() {
    const frage = await screen.findByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' });
    await userEvent.click(within(frage).getByRole('button', { name: 'Hinzufügen' }));
}

function aufrufUrl(index: number): string {
    return String(vi.mocked(fetch).mock.calls[index][0]);
}

function postBody(index: number): unknown {
    return JSON.parse((vi.mocked(fetch).mock.calls[index][1] as RequestInit).body as string);
}

describe('DokumentSuchenDialog', () => {
    beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
    afterEach(() => vi.unstubAllGlobals());

    function antworte(...antworten: Array<{ ok: boolean; body?: unknown }>) {
        const mock = vi.mocked(fetch);
        antworten.forEach(a => mock.mockResolvedValueOnce({ ok: a.ok, json: async () => a.body } as Response));
        return mock;
    }

    it('lädt alle Arten zur Kette, zeigt Art, Quote, Gründe und „passt zu“', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'WERKSTOFFZEUGNIS', 'WZ-100', 85), vorschlag(2, 'RECHNUNG', 'RE-200', 45, { eindeutig: false, kettenDokumentTyp: 'AUFTRAGSBESTAETIGUNG', kettenDokumentNummer: 'AB-1' })] });
        zeige();
        expect(screen.getByRole('status', { name: 'Vorschläge werden geladen' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Dokument zur Kette hinzufügen – Erika Musterfrau KG' })).toBeInTheDocument();

        await screen.findByRole('button', { name: /Werkstoffzeugnis\s*WZ-100/, pressed: true });
        expect(aufrufUrl(0)).toBe('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=10&dokumentIds=11&alleLieferanten=false');
        expect(screen.getByRole('button', { name: 'Alle' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByText('85 %')).toBeInTheDocument();
        expect(screen.getAllByText('Belegnummer wird genannt')).toHaveLength(2);
        const liste = screen.getByRole('list', { name: 'Vorschläge' });
        expect(within(liste).getByText('Lieferschein LS-1')).toBeInTheDocument();
        expect(within(liste).getByText('AB AB-1')).toBeInTheDocument();
        expect(screen.getByText('Ein weiteres Dokument passt gleich gut.')).toBeInTheDocument();
        expect(screen.getByText('2 Dokumente von Erika Musterfrau KG')).toBeInTheDocument();
        // vorgewählt: der sichere Treffer, rechts in der Vorschau
        expect(screen.getAllByTestId('pdf').map(e => e.textContent)).toEqual(['/pdf/AB-1', '/pdf/WZ-100']);
    });

    it('ordnet den gewählten Vorschlag mit Ketten- und Dokument-ID zu', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'WERKSTOFFZEUGNIS', 'WZ-100', 85)] }, { ok: true, body: { success: true } });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /WZ-100/, pressed: true });
        // Sicher und eindeutig: keine Rückfrage
        await userEvent.click(screen.getByRole('button', { name: 'Gehört dazu' }));
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(aufrufUrl(1)).toBe('/api/bestellungen-uebersicht/ketten-verknuepfen');
        expect(postBody(1)).toEqual({ kettenDokumentId: 11, dokumentId: 1 });
        expect(await screen.findByText('Werkstoffzeugnis WZ-100 zur Kette hinzugefügt.')).toBeInTheDocument();
    });

    it('startet mit Rechnungs-Filter als „Rechnung suchen“ und lädt beim Chip-Wechsel neu', async () => {
        antworte(
            { ok: true, body: [vorschlag(2, 'RECHNUNG', 'RE-200', 82)] },
            { ok: true, body: [vorschlag(3, 'WERKSTOFFZEUGNIS', 'WZ-300', 75)] },
            { ok: true, body: [] },
        );
        zeige('RECHNUNG');
        expect(screen.getByRole('heading', { name: 'Rechnung suchen – Erika Musterfrau KG' })).toBeInTheDocument();
        await screen.findByRole('button', { name: /RE-200/, pressed: true });
        expect(aufrufUrl(0)).toContain('&typ=RECHNUNG');
        expect(screen.getByRole('button', { name: 'Rechnung' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByText('1 Rechnung von Erika Musterfrau KG')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Werkstoffzeugnis' }));
        await screen.findByRole('button', { name: /WZ-300/, pressed: true });
        expect(aufrufUrl(1)).toContain('&typ=WERKSTOFFZEUGNIS');
        expect(screen.getByRole('button', { name: 'Werkstoffzeugnis' })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('heading', { name: /^Dokument zur Kette hinzufügen/ })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Alle' }));
        await screen.findByText('Von Erika Musterfrau KG ist noch kein Dokument da, das passen könnte.');
        expect(aufrufUrl(2)).not.toContain('typ=');
    });

    it('schaltet zwischen den Dokumenten der Kette um', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82)] });
        zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
        await userEvent.click(screen.getByRole('tab', { name: /Lieferschein LS-1/ }));
        expect(screen.getAllByTestId('pdf')[0]).toHaveTextContent('/pdf/LS-1');
    });

    it('filtert die Liste per Suche und leert sie wieder', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82), vorschlag(2, 'LIEFERSCHEIN', 'LS-200', 45)] });
        zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
        await userEvent.type(screen.getByLabelText('Vorschläge durchsuchen'), 'LS-200');
        const liste = screen.getByRole('list', { name: 'Vorschläge' });
        expect(within(liste).queryByText('RE-100')).not.toBeInTheDocument();
        expect(within(liste).getByText('LS-200')).toBeInTheDocument();

        await userEvent.clear(screen.getByLabelText('Vorschläge durchsuchen'));
        await userEvent.type(screen.getByLabelText('Vorschläge durchsuchen'), 'zzz');
        expect(screen.getByText(/Kein Vorschlag passt zu/)).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Suche löschen' }));
        expect(screen.getByRole('list', { name: 'Vorschläge' })).toBeInTheDocument();
    });

    it('zeigt beim Rechnungs-Filter einen Leerzustand mit Hochladen und Suche bei anderen Lieferanten', async () => {
        antworte({ ok: true, body: [] }, { ok: true, body: [vorschlag(2, 'RECHNUNG', 'RE-200', 30, { gruende: ['Anderer Lieferant'] })] });
        zeige('RECHNUNG');
        expect(await screen.findByText('Von Erika Musterfrau KG ist noch keine Rechnung da.')).toBeInTheDocument();
        expect(screen.getByText('0 Rechnungen von Erika Musterfrau KG')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Gehört dazu' })).toBeDisabled();
        expect(screen.getByRole('button', { name: /Rechnung hochladen/ })).toBeEnabled();
        expect(aufrufUrl(0)).toContain('alleLieferanten=false');

        await userEvent.click(screen.getByRole('button', { name: 'Bei anderen Lieferanten suchen' }));
        await screen.findByRole('button', { name: /RE-200/, pressed: false });
        expect(aufrufUrl(1)).toContain('alleLieferanten=true');
        expect(aufrufUrl(1)).toContain('typ=RECHNUNG');
        expect(screen.getByText('1 Rechnung von allen Lieferanten')).toBeInTheDocument();
        expect(screen.getByText('Anderer Lieferant')).toBeInTheDocument();
    });

    it('bietet ohne Rechnungs-Filter kein Hochladen an, aber „Alle Dokumentarten“', async () => {
        antworte({ ok: true, body: [] }, { ok: true, body: [] });
        zeige('LIEFERSCHEIN');
        expect(await screen.findByText('Von Erika Musterfrau KG ist noch kein Lieferschein da, der passen könnte.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Rechnung hochladen/ })).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: 'Alle Dokumentarten zeigen' }));
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
        expect(aufrufUrl(1)).not.toContain('typ=');
        expect(screen.getByRole('button', { name: 'Alle' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('lädt über den Link mit allen Lieferanten und wieder zurück', async () => {
        antworte(
            { ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82)] },
            { ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82), vorschlag(2, 'RECHNUNG', 'RE-200', 45)] },
            { ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82)] },
        );
        zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
        expect(screen.getByText('1 Dokument von Erika Musterfrau KG')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Auch bei anderen Lieferanten suchen' }));
        await screen.findByRole('button', { name: /RE-200/, pressed: false });
        expect(aufrufUrl(1)).toContain('alleLieferanten=true');
        expect(screen.getByText('2 Dokumente von allen Lieferanten')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Nur Erika Musterfrau KG zeigen' }));
        await waitFor(() => expect(screen.queryByRole('button', { name: /RE-200/, pressed: false })).not.toBeInTheDocument());
        expect(aufrufUrl(2)).toContain('alleLieferanten=false');
    });

    it('fragt nach, wenn das Dokument schon an einer anderen Bestellung hängt', async () => {
        const schonZugeordnet = vorschlag(1, 'WERKSTOFFZEUGNIS', 'WZ-100', 82, { gehoertSchonZu: 'Lieferschein LS-9' });
        schonZugeordnet.dokument.ausgeblendet = true;
        antworte({ ok: true, body: [schonZugeordnet] }, { ok: true, body: { success: true } });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /WZ-100/, pressed: true });
        expect(screen.getByText('Hängt schon an Lieferschein LS-9')).toBeInTheDocument();
        expect(screen.getByText('ausgeblendet')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Werkstoffzeugnis WZ-100 gehört dazu' }));
        const frage = await screen.findByRole('dialog', { name: 'Dokument hängt schon an einer anderen Bestellung' });
        expect(within(frage).getByText(/Beide Bestellungen werden dann zusammengefasst\./)).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Zusammenfassen' }));
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(postBody(1)).toEqual({ kettenDokumentId: 11, dokumentId: 1 });
    });

    it('hebt Vorschläge mit 0 % nicht hervor und wählt sie nicht vor', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 0, { eindeutig: false }), vorschlag(2, 'RECHNUNG', 'RE-200', 0, { eindeutig: false })] });
        zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: false });
        expect(screen.queryByText('0 %')).not.toBeInTheDocument();
        expect(screen.queryByText('Ein weiteres Dokument passt gleich gut.')).not.toBeInTheDocument();
        expect(screen.getByText('Noch kein Dokument gewählt.')).toBeInTheDocument();
    });

    it('lädt im Rechnungs-Leerzustand eine Rechnung für das jüngste Bestelldokument hoch', async () => {
        const mock = antworte({ ok: true, body: [] }, { ok: true, body: { id: 77, typ: 'RECHNUNG' } });
        const { onVerknuepft } = zeige('RECHNUNG');
        await screen.findByText('Von Erika Musterfrau KG ist noch keine Rechnung da.');
        const datei = new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
        await userEvent.upload(screen.getByTestId('rechnung-datei'), datei);
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        const [url, init] = mock.mock.calls[1];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-hochladen');
        expect(((init as RequestInit).body as FormData).get('bestellDokumentId')).toBe('11');
    });

    it('zeigt Ladefehler als Toast und bietet Wiederholen an', async () => {
        antworte({ ok: false, body: { message: 'Keine sichtbaren Dokumente' } }, { ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82)] });
        zeige();
        expect(await screen.findByText('Die Vorschläge konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.getAllByText('Keine sichtbaren Dokumente').length).toBeGreaterThan(0);
        await userEvent.click(screen.getByRole('button', { name: /Nochmal versuchen/ }));
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
    });

    it('meldet Fehler beim Zuordnen und bleibt offen', async () => {
        antworte({ ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82)] }, { ok: false, body: { message: 'Dokumentarten passen nicht zusammen.' } });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
        await userEvent.click(screen.getByRole('button', { name: 'Gehört dazu' }));
        expect(await screen.findByText('Dokumentarten passen nicht zusammen.')).toBeInTheDocument();
        expect(onVerknuepft).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: 'Gehört dazu' })).toBeEnabled();
    });

    it('öffnet je Vorschlag ein großes Vorschaufenster und ordnet von dort zu', async () => {
        antworte(
            { ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 82), vorschlag(2, 'LIEFERSCHEIN', 'LS-200', 45)] },
            { ok: true, body: { success: true } },
        );
        const { onVerknuepft, onClose } = zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau Lieferschein LS-200' }));
        const fenster = await screen.findByRole('dialog', { name: 'Lieferschein LS-200' });
        expect(within(fenster).getByTestId('pdf')).toHaveTextContent('/pdf/LS-200');
        expect(within(fenster).getByText('45 %')).toBeInTheDocument();
        expect(within(fenster).getByText('passt zu Lieferschein LS-1')).toBeInTheDocument();

        await userEvent.click(within(fenster).getByRole('button', { name: 'Schließen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Lieferschein LS-200' })).not.toBeInTheDocument());
        expect(onClose).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('button', { name: 'Vorschau Lieferschein LS-200' }));
        const nochmal = await screen.findByRole('dialog', { name: 'Lieferschein LS-200' });
        await userEvent.click(within(nochmal).getByRole('button', { name: 'Gehört dazu' }));
        await bestaetigeRueckfrage();
        await waitFor(() => expect(onVerknuepft).toHaveBeenCalled());
        expect(postBody(1)).toEqual({ kettenDokumentId: 11, dokumentId: 2 });
    });

    it('sperrt die Vorschau, wenn das Dokument kein PDF hat', async () => {
        const ohnePdf = vorschlag(1, 'RECHNUNG', 'RE-100', 82);
        ohnePdf.dokument.pdfUrl = null;
        antworte({ ok: true, body: [ohnePdf] });
        zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: true });
        expect(screen.getByRole('button', { name: 'Vorschau Rechnung RE-100' })).toBeDisabled();
        expect(screen.getByText('Zu diesem Dokument gibt es keine Vorschau.')).toBeInTheDocument();
    });

    it('wählt einen geratenen Vorschlag nicht vor und fragt vor dem Zuordnen nach', async () => {
        const mock = antworte({ ok: true, body: [vorschlag(1, 'RECHNUNG', 'RE-100', 15), vorschlag(2, 'RECHNUNG', 'RE-200', 10)] });
        const { onVerknuepft } = zeige();
        await screen.findByRole('button', { name: /RE-100/, pressed: false });
        expect(screen.getByRole('button', { name: 'Gehört dazu' })).toBeDisabled();

        await userEvent.click(screen.getByRole('button', { name: 'Rechnung RE-100 gehört dazu' }));
        const frage = await screen.findByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' });
        expect(within(frage).getByText(/nur bei 15 %/)).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' })).not.toBeInTheDocument());
        expect(mock).toHaveBeenCalledTimes(1);
        expect(onVerknuepft).not.toHaveBeenCalled();
    });

    it('schließt über Abbrechen', async () => {
        antworte({ ok: true, body: [] });
        const { onClose } = zeige();
        await screen.findByText(/ist noch kein Dokument da/);
        await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }));
        expect(onClose).toHaveBeenCalled();
    });
});
