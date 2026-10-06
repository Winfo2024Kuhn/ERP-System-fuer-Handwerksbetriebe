import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { KettenGabel, MAX_GRAPH_BREITE, type GabelDokument } from './KettenGabel';
import { baueKettenGraph, type KettenVerbindung } from './kettenGraph';

function dok(id: number, typ: GabelDokument['typ'], nummer: string, datum: string, extra: Partial<GabelDokument> = {}): GabelDokument {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: null, liefertermin: null,
        dateiname: `${nummer}.pdf`, pdfUrl: `/pdf/${nummer}`, ...extra,
    };
}

const AB = dok(1, 'AUFTRAGSBESTAETIGUNG', 'AB-1', '2026-08-01', { betragBrutto: 1190, liefertermin: '2026-08-20' });
const LS1 = dok(2, 'LIEFERSCHEIN', 'LS-1', '2026-08-10');
const LS2 = dok(3, 'LIEFERSCHEIN', 'LS-2', '2026-08-12');
const RE = dok(4, 'RECHNUNG', 'RE-1', '2026-09-01', { betragBrutto: 1190 });

interface Optionen {
    dokumente?: GabelDokument[];
    verbindungen?: KettenVerbindung[];
    offenesEnde?: boolean;
    mitSuche?: boolean;
}

function zeige({ dokumente = [AB, LS1, LS2, RE], verbindungen = [], offenesEnde = false, mitSuche = false }: Optionen = {}) {
    const onOpenPdf = vi.fn();
    const onGeaendert = vi.fn();
    const onRechnungSuchen = vi.fn();
    render(
        <ToastProvider>
            <ConfirmProvider>
                <KettenGabel
                    dokumente={dokumente}
                    verbindungen={verbindungen}
                    onOpenPdf={onOpenPdf}
                    offenesEnde={offenesEnde}
                    onRechnungSuchen={mitSuche ? onRechnungSuchen : undefined}
                    onGeaendert={onGeaendert}
                />
            </ConfirmProvider>
        </ToastProvider>,
    );
    return { onOpenPdf, onGeaendert, onRechnungSuchen };
}

const gabel = [
    { vonId: 2, zuId: 1 }, { vonId: 3, zuId: 1 },
    { vonId: 4, zuId: 2 }, { vonId: 4, zuId: 3 },
];

describe('KettenGabel', () => {
    beforeEach(() => vi.stubGlobal('fetch', vi.fn()));
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt je Beleg eine Zeile in der Reihenfolge der Bestellung und öffnet das Dokument', async () => {
        const { onOpenPdf } = zeige({ dokumente: [RE, LS2, AB, LS1], verbindungen: gabel });
        const liste = screen.getByRole('list', { name: 'Belege der Bestellung' });
        const zeilen = within(liste).getAllByRole('listitem');
        expect(zeilen.map(z => z.textContent)).toEqual([
            expect.stringContaining('Auftragsbestätigung'),
            expect.stringContaining('LS-1'),
            expect.stringContaining('LS-2'),
            expect.stringContaining('Rechnung'),
        ]);
        expect(screen.getByText(/Liefertermin 20\.8\.2026|Liefertermin 20\.08\.2026/)).toBeInTheDocument();
        expect(screen.getAllByText('1.190,00 €')).toHaveLength(2);
        // Gabel: zwei Lieferscheine nebeneinander → zwei Spuren
        expect(screen.getByTestId('ketten-graph').querySelectorAll('path')).toHaveLength(4);

        await userEvent.click(screen.getByRole('button', { name: /^Lieferschein LS-2 \d/ }));
        expect(onOpenPdf).toHaveBeenCalledWith('/pdf/LS-2', 'LS-2');
    });

    it('öffnet auch ausgeblendete Belege und zeigt sie gedämpft mit Hinweis', async () => {
        const versteckt = dok(5, 'RECHNUNG', 'RE-9', '2026-09-02', { ausgeblendet: true });
        const { onOpenPdf } = zeige({ dokumente: [AB, versteckt] });
        expect(screen.getByText('ausgeblendet')).toBeInTheDocument();
        await userEvent.click(screen.getByRole('button', { name: /^Rechnung RE-9 \d/ }));
        expect(onOpenPdf).toHaveBeenCalledWith('/pdf/RE-9', 'RE-9');
    });

    it('sperrt Zeilen ohne Vorschau', () => {
        zeige({ dokumente: [dok(6, 'LIEFERSCHEIN', 'LS-7', '2026-08-01', { pdfUrl: null })] });
        expect(screen.getByRole('button', { name: /^Lieferschein LS-7 \d/ })).toBeDisabled();
    });

    it('bietet Abhängen nur bei verknüpften Belegen an und ruft nach Bestätigung die API', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ geloest: 1 }) } as Response);
        const { onGeaendert } = zeige({ dokumente: [AB, LS1, RE], verbindungen: [{ vonId: 2, zuId: 1 }] });
        expect(screen.queryByRole('button', { name: /Rechnung RE-1 von der Bestellung abhängen/ })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Lieferschein LS-1 von der Bestellung abhängen' }));
        const frage = await screen.findByRole('dialog', { name: 'Beleg abhängen?' });
        expect(within(frage).getByText('Dieser Beleg wird von der Bestellung gelöst und nicht mehr automatisch zugeordnet.')).toBeInTheDocument();
        await userEvent.click(within(frage).getByRole('button', { name: 'Abhängen' }));

        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
        const [url, init] = vi.mocked(fetch).mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/abhaengen');
        expect(JSON.parse((init as RequestInit).body as string)).toEqual({ dokumentId: 2 });
        expect(await screen.findByText('Lieferschein LS-1 abgehängt.')).toBeInTheDocument();
    });

    it('hängt nichts ab, wenn die Rückfrage abgebrochen wird, und meldet Fehler', async () => {
        const { onGeaendert } = zeige({ dokumente: [AB, LS1], verbindungen: [{ vonId: 2, zuId: 1 }] });
        await userEvent.click(screen.getByRole('button', { name: 'Auftragsbestätigung AB-1 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abbrechen' }));
        await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Beleg abhängen?' })).not.toBeInTheDocument());
        expect(fetch).not.toHaveBeenCalled();

        vi.mocked(fetch).mockResolvedValueOnce({ ok: false, json: async () => ({ message: 'Beleg nicht gefunden' }) } as Response);
        await userEvent.click(screen.getByRole('button', { name: 'Auftragsbestätigung AB-1 von der Bestellung abhängen' }));
        await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
        expect(await screen.findByText('Beleg nicht gefunden')).toBeInTheDocument();
        expect(onGeaendert).not.toHaveBeenCalled();
    });

    it('zeigt ohne Rechnung ein offenes Ende und lädt eine Rechnung zum jüngsten Bestelldokument hoch', async () => {
        vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 99, typ: 'RECHNUNG' }) } as Response);
        const { onGeaendert, onRechnungSuchen } = zeige({ dokumente: [AB, LS1, LS2], verbindungen: gabel.slice(0, 2), offenesEnde: true, mitSuche: true });
        expect(screen.getByText('Rechnung fehlt noch')).toBeInTheDocument();
        const graph = screen.getByTestId('ketten-graph');
        expect(graph.querySelectorAll('path[stroke-dasharray]')).toHaveLength(2);

        await userEvent.click(screen.getByRole('button', { name: 'Suchen' }));
        expect(onRechnungSuchen).toHaveBeenCalled();

        const datei = new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
        await userEvent.upload(screen.getByTestId('rechnung-datei'), datei);
        await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
        const [url, init] = vi.mocked(fetch).mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-hochladen');
        const body = (init as RequestInit).body as FormData;
        expect(body).toBeInstanceOf(FormData);
        expect(body.get('bestellDokumentId')).toBe('3');
        expect((body.get('datei') as File).name).toBe('rechnung.pdf');
        expect(await screen.findByText(/Rechnung hochgeladen und zugeordnet/)).toBeInTheDocument();
    });

    it('lehnt andere Dateitypen ab und meldet Upload-Fehler', async () => {
        const { onGeaendert } = zeige({ dokumente: [LS1], offenesEnde: true, mitSuche: true });
        const eingabe = screen.getByTestId('rechnung-datei');
        await userEvent.upload(eingabe, new File(['x'], 'liste.exe', { type: 'application/octet-stream' }), { applyAccept: false });
        expect(await screen.findByText('Bitte eine PDF-, JPG- oder PNG-Datei wählen.')).toBeInTheDocument();
        expect(fetch).not.toHaveBeenCalled();

        vi.mocked(fetch).mockResolvedValueOnce({ ok: false, json: async () => ({ error: 'Datei ist leer' }) } as Response);
        await userEvent.upload(eingabe, new File(['x'], 'scan.png', { type: 'image/png' }));
        expect(await screen.findByText('Datei ist leer')).toBeInTheDocument();
        expect(onGeaendert).not.toHaveBeenCalled();
        expect(screen.getByRole('button', { name: /Rechnung hochladen/ })).toBeEnabled();
    });

    it('zeigt das offene Ende ohne Knöpfe, wenn keine Suche angeboten wird', () => {
        zeige({ dokumente: [AB], offenesEnde: true });
        expect(screen.getByText('Rechnung fehlt noch')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Rechnung hochladen/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Suchen' })).not.toBeInTheDocument();
    });

    it('begrenzt die Graphbreite bei vielen Spuren und hält den Betrag in eigener Spalte', () => {
        // Eine Rechnung, fünf Teillieferungen auf zwei ABs verteilt → ohne Grenze mindestens fünf Spuren
        const viele = [
            dok(50, 'ANGEBOT', 'AN-7000123', '2026-04-08'),
            dok(51, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/01', '2026-04-08', { betragBrutto: 1604.68, liefertermin: '2026-04-14' }),
            dok(52, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/02', '2026-04-08', { betragBrutto: 1647.25, liefertermin: '2026-04-14' }),
            ...[53, 54, 55, 56, 57].map((id, i) => dok(id, 'LIEFERSCHEIN', `7000123/0${i + 1}`, `2026-04-1${i}`)),
            dok(58, 'RECHNUNG', '8000777', '2026-04-20', { betragBrutto: 1517.64 }),
        ];
        const kreuz = [[51, 50], [52, 50], [53, 51], [54, 51], [55, 51], [56, 52], [57, 52], [58, 53], [58, 54], [58, 55], [58, 56], [58, 57]]
            .map(([vonId, zuId]) => ({ vonId, zuId }));
        expect(baueKettenGraph(viele, kreuz).spuren).toBeGreaterThanOrEqual(5);
        zeige({ dokumente: viele, verbindungen: kreuz });

        const graph = screen.getByTestId('ketten-graph');
        expect(Number(graph.getAttribute('width'))).toBeLessThanOrEqual(MAX_GRAPH_BREITE);
        for (const kreis of Array.from(graph.querySelectorAll('circle'))) {
            expect(Number(kreis.getAttribute('cx'))).toBeLessThanOrEqual(MAX_GRAPH_BREITE);
        }
        // Lange Nummer: gekürzt erlaubt, aber per Tooltip lesbar
        expect(screen.getByTitle('RL 99 - 7000123/01')).toHaveAttribute('data-kuerzung-erlaubt');
        // Betrag ist ein eigenes, nicht schrumpfendes Element neben – nicht über – dem Abhängen-Knopf
        const betrag = screen.getByText('1.604,68 €');
        expect(betrag).toHaveClass('flex-shrink-0', 'whitespace-nowrap');
        const zeile = betrag.closest('li') as HTMLElement;
        expect(within(zeile).getByRole('button', { name: /^Auftragsbestätigung RL 99 - 7000123\/01 \d/ })).toContainElement(betrag);
        expect(within(zeile).getByRole('button', { name: /abhängen$/ })).not.toContainElement(betrag);
    });

    describe('mehrere Rechnungen: eine kleine Gabel je Rechnung', () => {
        const angebot = dok(60, 'ANGEBOT', 'AN-7000123', '2026-04-08');
        const ab1 = dok(61, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/1', '2026-04-08');
        const ab2 = dok(62, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/2', '2026-04-08');
        const ls03 = dok(63, 'LIEFERSCHEIN', '7000123/03', '2026-04-08');
        const ls01 = dok(64, 'LIEFERSCHEIN', '7000123/01', '2026-04-14');
        const ls05 = dok(65, 'LIEFERSCHEIN', '7000123/05', '2026-04-22');
        const re1 = dok(66, 'RECHNUNG', '8000777', '2026-04-16', { betragBrutto: 1517.64 });
        const re2 = dok(67, 'RECHNUNG', '8000778', '2026-04-20', { betragBrutto: 99 });
        const verbindungen = [[61, 60], [62, 60], [63, 61], [64, 62], [65, 62], [66, 64], [67, 61], [67, 62], [67, 63], [67, 64]]
            .map(([vonId, zuId]) => ({ vonId, zuId }));

        it('zeigt oben die Bestellung, je Rechnung einen Kasten und offene Lieferscheine am Ende', async () => {
            vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 99, typ: 'RECHNUNG' }) } as Response);
            const { onOpenPdf, onRechnungSuchen, onGeaendert } = zeige({
                dokumente: [re2, ls05, ls01, angebot, re1, ab2, ls03, ab1], verbindungen, mitSuche: true,
            });

            const bestellung = screen.getByRole('list', { name: 'Bestellung' });
            expect(within(bestellung).getAllByRole('listitem').map(z => z.textContent)).toEqual([
                expect.stringContaining('AN-7000123'), expect.stringContaining('7000123/1'), expect.stringContaining('7000123/2'),
            ]);

            const kasten1 = screen.getByRole('list', { name: 'Belege zu Rechnung 8000777' });
            expect(within(kasten1).getAllByRole('listitem').map(z => z.textContent)).toEqual([
                expect.stringContaining('7000123/01'), expect.stringContaining('8000777'),
            ]);
            expect(within(kasten1).queryByText('auch oben')).not.toBeInTheDocument();

            const kasten2 = screen.getByRole('list', { name: 'Belege zu Rechnung 8000778' });
            const zeilen2 = within(kasten2).getAllByRole('listitem');
            expect(zeilen2.map(z => z.textContent)).toEqual([
                expect.stringContaining('7000123/03'), expect.stringContaining('7000123/01'), expect.stringContaining('8000778'),
            ]);
            // Lieferschein 01 steht schon in der ersten Rechnung
            expect(within(zeilen2[1]).getByText('auch oben')).toBeInTheDocument();
            expect(within(zeilen2[0]).queryByText('auch oben')).not.toBeInTheDocument();

            // Jede Zeile öffnet ihr Dokument, auch die Wiederholung
            await userEvent.click(within(zeilen2[1]).getByRole('button', { name: /^Lieferschein 7000123\/01 \d/ }));
            expect(onOpenPdf).toHaveBeenCalledWith('/pdf/7000123/01', '7000123/01');

            // Lieferschein ohne Rechnung: eigener Kasten mit offenem Ende
            const offen = screen.getByRole('list', { name: 'Lieferscheine ohne Rechnung' });
            expect(within(offen).getByText('Rechnung fehlt noch')).toBeInTheDocument();
            await userEvent.click(within(offen).getByRole('button', { name: 'Suchen' }));
            expect(onRechnungSuchen).toHaveBeenCalled();
            await userEvent.upload(within(offen).getByTestId('rechnung-datei'), new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' }));
            await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
            expect(((vi.mocked(fetch).mock.calls[0][1] as RequestInit).body as FormData).get('bestellDokumentId')).toBe('65');

            // Je Kasten ein gerader Stamm: nur eine Spur
            for (const svg of screen.getAllByTestId('ketten-graph')) {
                expect(new Set(Array.from(svg.querySelectorAll('circle')).map(c => c.getAttribute('cx'))).size).toBe(1);
            }
        });

        it('hängt auch in den Kästen ab', async () => {
            vi.mocked(fetch).mockResolvedValueOnce({ ok: true, json: async () => ({ geloest: 1 }) } as Response);
            const { onGeaendert } = zeige({ dokumente: [ab1, ab2, ls03, ls01, re1, re2], verbindungen });
            const kasten2 = screen.getByRole('list', { name: 'Belege zu Rechnung 8000778' });
            await userEvent.click(within(kasten2).getByRole('button', { name: 'Rechnung 8000778 von der Bestellung abhängen' }));
            await userEvent.click(within(await screen.findByRole('dialog', { name: 'Beleg abhängen?' })).getByRole('button', { name: 'Abhängen' }));
            await waitFor(() => expect(onGeaendert).toHaveBeenCalled());
            expect(JSON.parse((vi.mocked(fetch).mock.calls[0][1] as RequestInit).body as string)).toEqual({ dokumentId: 67 });
            expect(screen.queryByRole('list', { name: 'Lieferscheine ohne Rechnung' })).not.toBeInTheDocument();
        });
    });

    it('zeigt nichts ohne Belege', () => {
        zeige({ dokumente: [] });
        expect(screen.queryByRole('list', { name: 'Belege der Bestellung' })).not.toBeInTheDocument();
    });
});
