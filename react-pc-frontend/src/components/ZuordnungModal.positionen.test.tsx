import { act, render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import type { DragEndEvent } from '@dnd-kit/core';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ZuordnungModal } from './ZuordnungModal';
import type { DokumentPosition, PositionsUebersicht } from './zuordnung/positionen';

/**
 * Modus „Nach Positionen“ im Zuordnungsdialog: jede Warenposition geht an
 * genau ein Projekt bzw. eine Kostenstelle, die Beträge rechnet das Backend.
 * Nur Dummy-Daten (DSGVO).
 */

const toastError = vi.hoisted(() => vi.fn());
// Letztes onDragEnd des DndContext – so lässt sich das Ablegen ohne echte Zeigerbewegung auslösen.
const dnd = vi.hoisted(() => ({ onDragEnd: undefined as undefined | ((event: DragEndEvent) => void) }));

vi.mock('@dnd-kit/core', async (importOriginal) => {
    const echt = await importOriginal<typeof import('@dnd-kit/core')>();
    return {
        ...echt,
        DndContext: (props: ComponentProps<typeof echt.DndContext>) => {
            dnd.onDragEnd = props.onDragEnd;
            return <echt.DndContext {...props} />;
        },
    };
});

vi.mock('./ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: () => <div data-testid="pdf-viewer" />,
}));
vi.mock('./ui/toast', () => ({
    useToast: () => ({ error: toastError, success: vi.fn() }),
}));
vi.mock('./ProjektSearchModal', () => ({
    ProjektSearchModal: ({ isOpen, onSelect }: { isOpen: boolean; onSelect: (p: { id: number; bauvorhaben: string }) => void }) => (isOpen
        ? <button onClick={() => onSelect({ id: 30, bauvorhaben: 'Garage Musterweg' })}>Testprojekt wählen</button>
        : null),
}));
vi.mock('./KostenstelleSelectModal', () => ({
    KostenstelleSelectModal: () => null,
}));

const mockFetch = vi.fn();
global.fetch = mockFetch;

const pos = (id: number, art: DokumentPosition['positionsArt'], bezeichnung: string, gesamt: number, extra: Partial<DokumentPosition> = {}): DokumentPosition => ({
    id,
    positionNr: id,
    positionsArt: art,
    externeArtikelnummer: art === 'WARE' ? `ART-${id}` : null,
    bezeichnung,
    menge: art === 'WARE' ? 2 : null,
    mengeneinheit: art === 'WARE' ? 'Stk' : null,
    einzelpreis: art === 'WARE' ? gesamt / 2 : null,
    preiseinheit: null,
    gesamtpreisNetto: gesamt,
    projektId: null,
    projektName: null,
    kostenstelleId: null,
    kostenstelleName: null,
    ...extra,
});

const POSITIONEN = [
    pos(1, 'WARE', 'Stahlträger HEB 200', 300),
    pos(2, 'WARE', 'Schrauben M12', 50),
    pos(3, 'NEBENKOSTEN', 'Fracht', 30),
];

const uebersicht = (extra: Partial<PositionsUebersicht> = {}): PositionsUebersicht => ({
    geschaeftsdokumentId: 1,
    dokumentTyp: 'RECHNUNG',
    auslesbar: true,
    betragNetto: 380,
    betragBrutto: 452.2,
    summePositionen: 380,
    abweichung: 0,
    abweichungAuffaellig: false,
    nachPositionenAufgeteilt: false,
    positionen: POSITIONEN,
    ...extra,
});

interface Szenario {
    zuordnungen?: unknown[];
    positionen?: PositionsUebersicht | null;
    positionenStatus?: number;
    auslesen?: { status: number; body: unknown };
    zuordnenStatus?: { status: number; body: unknown };
}

const antwort = (body: unknown, status = 200) => Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
});

/** Vorschau wie das Backend: Ware je Ziel, Nebenkosten anteilig; speicherbar erst ohne offene Position. */
function vorschauFuer(body: { positionen: { positionId: number; projektId?: number; kostenstelleId?: number }[] }) {
    const preise: Record<number, number> = { 1: 300, 2: 50 };
    const jeZiel = new Map<string, { projektId: number | null; kostenstelleId: number | null; warenwert: number }>();
    let offen = 0;
    for (const p of body.positionen) {
        if (p.projektId == null && p.kostenstelleId == null) { offen++; continue; }
        const key = p.projektId != null ? `p${p.projektId}` : `k${p.kostenstelleId}`;
        const eintrag = jeZiel.get(key) ?? { projektId: p.projektId ?? null, kostenstelleId: p.kostenstelleId ?? null, warenwert: 0 };
        eintrag.warenwert += preise[p.positionId];
        jeZiel.set(key, eintrag);
    }
    const ziele = [...jeZiel.values()].map(z => {
        const anteil = z.warenwert / 350;
        const netto = Math.round(380 * anteil * 100) / 100;
        const brutto = Math.round(netto * 1.19 * 100) / 100;
        return { ...z, anteilProzent: anteil * 100, betragNetto: netto, betragBrutto: brutto, betrag: z.projektId != null ? brutto : netto };
    });
    return {
        ziele,
        nichtZugeordnet: offen,
        warenwert: 350,
        nebenkosten: 30,
        abweichung: 0,
        speicherbar: offen === 0,
        hinweis: offen > 0 ? `Noch ${offen} Warenposition(en) ohne Projekt oder Kostenstelle.` : null,
    };
}

function richteEin(s: Szenario) {
    mockFetch.mockImplementation((url: string, options?: RequestInit) => {
        if (url.includes('/positionen/1/vorschau')) return antwort(vorschauFuer(JSON.parse(options!.body as string)));
        if (url.includes('/positionen/1/auslesen')) return antwort(s.auslesen?.body, s.auslesen?.status ?? 200);
        if (url.includes('/positionen/1/zuordnen')) return antwort(s.zuordnenStatus?.body ?? { success: true }, s.zuordnenStatus?.status ?? 200);
        if (url.includes('/positionen/')) return antwort(s.positionen ?? {}, s.positionenStatus ?? (s.positionen ? 200 : 404));
        if (url.includes('/geschaeftsdaten/')) {
            return antwort({ id: 1, dokumentNummer: 'RE-2026-001', dokumentDatum: '2026-09-01', betragNetto: 380, betragBrutto: 452.2, mwstSatz: 0.19 });
        }
        if (url.includes('/belegdaten/')) return antwort({ id: 5, dokumentNummer: 'B-1', betragNetto: 10, betragBrutto: 11.9 });
        if (url.includes('zuordnungen/')) return antwort(s.zuordnungen ?? []);
        if (options?.method === 'POST') return antwort({ success: true });
        return antwort({});
    });
}

const ZWEI_PROJEKTE = [
    { projektId: 10, projektName: 'Halle Mustermann', prozentanteil: 50, betrag: 190, beschreibung: '' },
    { projektId: 20, projektName: 'Carport Musterfrau', prozentanteil: 50, betrag: 190, beschreibung: '' },
];

const props = { geschaeftsdokumentId: 1, onClose: vi.fn(), onSuccess: vi.fn() };

const modusKnopf = () => screen.queryByRole('button', { name: /Nach Positionen/ });
const speichernKnopf = () => screen.getByRole('button', { name: /Zuordnen & Abschließen/ });
const postBody = (pfad: string) => {
    const call = mockFetch.mock.calls.find(([url, options]) => String(url).includes(pfad) && options?.method === 'POST');
    return call ? JSON.parse(call[1].body as string) : undefined;
};

/** Legt die gezogene Position auf einer Ablagefläche ab (`''` = Nicht zugeordnet, `null` = daneben). */
function ablegen(positionId: number, ziel: string | null) {
    act(() => {
        dnd.onDragEnd?.({
            active: { id: `position:${positionId}`, data: { current: { positionId, anzahl: 1 } } },
            over: ziel === null ? null : { id: `ablage:${ziel}` },
        } as unknown as DragEndEvent);
    });
}

const zielVon = (nr: number) => screen.getByRole('combobox', { name: `Ziel für Position ${nr}` });

async function waehle(combobox: HTMLElement, option: string) {
    fireEvent.click(combobox);
    fireEvent.click(await screen.findByRole('option', { name: option }));
}

describe('ZuordnungModal – Aufteilung nach Positionen', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('zeigt den Modus beim Kassenbeleg nicht und fragt keine Positionen ab', async () => {
        richteEin({ positionen: uebersicht() });
        render(<ZuordnungModal belegId={5} onClose={vi.fn()} onSuccess={vi.fn()} />);

        await waitFor(() => expect(screen.getByText('Verteilungsmodus:')).toBeInTheDocument());
        expect(modusKnopf()).toBeNull();
        expect(mockFetch.mock.calls.some(([url]) => String(url).includes('/positionen/'))).toBe(false);
    });

    it('blendet den Modus aus, wenn das Dokument keine Positionen haben kann', async () => {
        richteEin({ positionen: uebersicht({ auslesbar: false, positionen: [] }) });
        render(<ZuordnungModal {...props} />);

        await waitFor(() => expect(screen.getByText('Verteilungsmodus:')).toBeInTheDocument());
        expect(modusKnopf()).toBeNull();
    });

    it('bietet den Modus bei einem Werkstoffzeugnis nicht an – es hat keine Preise', async () => {
        richteEin({ positionen: uebersicht({ dokumentTyp: 'WERKSTOFFZEUGNIS', auslesbar: true }) });
        render(<ZuordnungModal {...props} />);

        await waitFor(() => expect(mockFetch.mock.calls.some(([url]) => String(url).includes('/positionen/1'))).toBe(true));
        await waitFor(() => expect(screen.getByText('Verteilungsmodus:')).toBeInTheDocument());
        expect(modusKnopf()).toBeNull();
    });

    it('lädt die Positionen und zeigt Ware mit Auswahl, Nebenkosten ohne', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: ZWEI_PROJEKTE });
        render(<ZuordnungModal {...props} />);

        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));

        const liste = await screen.findByRole('list', { name: 'Positionen der Rechnung' });
        const zeilen = within(liste).getAllByRole('listitem');
        expect(zeilen).toHaveLength(3);
        expect(within(zeilen[0]).getByText('Stahlträger HEB 200')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('Art.-Nr. ART-1')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('2 Stk')).toBeInTheDocument();
        expect(within(zeilen[0]).getByText('300,00 €')).toBeInTheDocument();
        expect(within(zeilen[0]).getByRole('combobox', { name: 'Ziel für Position 1' })).toBeInTheDocument();
        expect(within(zeilen[2]).getByText(/wird anteilig verteilt/)).toBeInTheDocument();
        expect(within(zeilen[2]).queryByRole('combobox')).toBeNull();
        expect(screen.getByText('Noch 2 Positionen nicht zugeordnet')).toBeInTheDocument();
    });

    it('sperrt Speichern, solange Positionen offen sind, und schickt die gewählten Ziele', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: ZWEI_PROJEKTE });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        await screen.findByRole('list', { name: 'Positionen der Rechnung' });

        expect(speichernKnopf()).toBeDisabled();
        expect(screen.getAllByText('Noch 2 Positionen nicht zugeordnet.').length).toBeGreaterThan(0);

        // Position 1 per Auswahl an „Carport Musterfrau“, den Rest an „Halle Mustermann“
        fireEvent.click(screen.getByRole('checkbox', { name: 'Position 1 markieren' }));
        await waehle(screen.getByRole('combobox', { name: 'Ausgewählte zuordnen zu' }), 'Carport Musterfrau');
        expect(screen.getByRole('checkbox', { name: 'Position 1 markieren' })).not.toBeChecked();
        expect(speichernKnopf()).toBeDisabled();

        await waehle(screen.getByRole('combobox', { name: 'Alle übrigen zu' }), 'Halle Mustermann');
        expect(screen.getByText('Alle 2 zugeordnet')).toBeInTheDocument();

        await waitFor(() => expect(speichernKnopf()).toBeEnabled());
        fireEvent.click(speichernKnopf());

        await waitFor(() => expect(props.onSuccess).toHaveBeenCalled());
        expect(postBody('/positionen/1/zuordnen')).toEqual({
            positionen: [
                { positionId: 1, projektId: 20 },
                { positionId: 2, projektId: 10 },
            ],
            ziele: [
                { projektId: 10, beschreibung: '' },
                { projektId: 20, beschreibung: '' },
            ],
        });
        // Prozent-/Betrags-Endpunkt bleibt unberührt
        expect(postBody('/bestellungen-uebersicht/zuordnen')).toBeUndefined();
    });

    it('zeigt die Live-Vorschau je Ziel mit Betrag und Anteil', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: ZWEI_PROJEKTE });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        await screen.findByRole('list', { name: 'Positionen der Rechnung' });

        await waehle(screen.getByRole('combobox', { name: 'Ziel für Position 1' }), 'Halle Mustermann');

        // 300 von 350 Ware → 85,7 %, 380 * 300/350 = 325,71 netto → 387,59 brutto
        await waitFor(() => expect(screen.getByText('387,59 €')).toBeInTheDocument());
        expect(screen.getByText('1 Position · 85,7 % der Ware')).toBeInTheDocument();
        expect(screen.getByText('Noch keine Position')).toBeInTheDocument();
        expect(screen.getAllByText('Noch 1 Warenposition(en) ohne Projekt oder Kostenstelle.').length).toBeGreaterThan(0);
        expect(screen.getByText(/Nebenkosten und Rabatte/).closest('div')).toHaveTextContent('30,00 €');
    });

    it('startet bei gespeicherter Positionsaufteilung direkt im Positionsmodus mit vorbelegten Zielen', async () => {
        const gespeichert = POSITIONEN.map(p => p.positionsArt === 'WARE'
            ? { ...p, kostenstelleId: 3, kostenstelleName: 'Werkstatt' }
            : p);
        richteEin({ positionen: uebersicht({ nachPositionenAufgeteilt: true, positionen: gespeichert }), zuordnungen: [] });
        render(<ZuordnungModal {...props} />);

        await waitFor(() => expect(modusKnopf()).toHaveClass('text-rose-600'));
        expect(await screen.findByRole('list', { name: 'Positionen der Rechnung' })).toBeInTheDocument();
        // Ziel aus den Positionen erscheint in der Zielliste und in den Auswahlfeldern
        expect(screen.getByRole('button', { name: 'Werkstatt entfernen' })).toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Ziel für Position 1' })).toHaveTextContent('Werkstatt');
        await waitFor(() => expect(speichernKnopf()).toBeEnabled());

        // Kostenstelle über drei Jahre verteilen – geht als Zieldetail mit
        fireEvent.change(screen.getByLabelText('Kosten verteilen über'), { target: { value: '3' } });
        expect(screen.getByText(/\/ Jahr ab 2026/)).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Beschreibung für Werkstatt'), { target: { value: 'Schweißgerät' } });
        fireEvent.click(speichernKnopf());
        await waitFor(() => expect(props.onSuccess).toHaveBeenCalled());
        expect(postBody('/positionen/1/zuordnen').ziele).toEqual([{ kostenstelleId: 3, beschreibung: 'Schweißgerät', streckungJahre: 3 }]);
    });

    it('liest fehlende Positionen per Knopf aus und belegt bei einem Ziel alles vor', async () => {
        richteEin({
            positionen: uebersicht({ positionen: [], summePositionen: 0 }),
            zuordnungen: [ZWEI_PROJEKTE[0]],
            auslesen: { status: 200, body: uebersicht() },
        });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));

        expect(screen.getByText('Für diese Rechnung sind noch keine Positionen ausgelesen.')).toBeInTheDocument();
        expect(speichernKnopf()).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: /Positionen auslesen/ }));

        expect(await screen.findByRole('list', { name: 'Positionen der Rechnung' })).toBeInTheDocument();
        expect(screen.getByText('Alle 2 zugeordnet')).toBeInTheDocument();
        expect(mockFetch).toHaveBeenCalledWith('/api/bestellungen-uebersicht/positionen/1/auslesen', expect.objectContaining({ method: 'POST' }));
        await waitFor(() => expect(speichernKnopf()).toBeEnabled());
    });

    it('zeigt einen Fehler beim Auslesen freundlich an', async () => {
        richteEin({
            positionen: uebersicht({ positionen: [], summePositionen: 0 }),
            auslesen: { status: 422, body: { error: 'Auf dem Dokument ist kein Text lesbar.' } },
        });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        fireEvent.click(screen.getByRole('button', { name: /Positionen auslesen/ }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Auf dem Dokument ist kein Text lesbar.');
        expect(toastError).toHaveBeenCalledWith('Auf dem Dokument ist kein Text lesbar.');
        expect(screen.getByRole('button', { name: /Nochmal versuchen/ })).toBeInTheDocument();
    });

    it('weist auf eine auffällige Differenz zwischen Positionen und Rechnung hin', async () => {
        richteEin({ positionen: uebersicht({ betragNetto: 400, summePositionen: 380, abweichung: 20, abweichungAuffaellig: true }), zuordnungen: ZWEI_PROJEKTE });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));

        expect(await screen.findByRole('note')).toHaveTextContent(
            'Die Positionen ergeben 380,00 €, die Rechnung 400,00 € netto – die Differenz wird anteilig verteilt.',
        );
    });

    it('meldet einen Fehler beim Speichern per Toast und bleibt offen', async () => {
        richteEin({
            positionen: uebersicht(),
            zuordnungen: [ZWEI_PROJEKTE[0]],
            zuordnenStatus: { status: 400, body: { error: 'Projekt nicht gefunden: 10' } },
        });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        await screen.findByRole('list', { name: 'Positionen der Rechnung' });

        await waitFor(() => expect(speichernKnopf()).toBeEnabled());
        fireEvent.click(speichernKnopf());

        await waitFor(() => expect(toastError).toHaveBeenCalledWith('Projekt nicht gefunden: 10'));
        expect(props.onSuccess).not.toHaveBeenCalled();
        expect(props.onClose).not.toHaveBeenCalled();
    });

    it('nimmt beim Entfernen eines Ziels dessen Positionen wieder auf offen', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: [ZWEI_PROJEKTE[0]] });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        await screen.findByText('Alle 2 zugeordnet');

        fireEvent.click(screen.getByRole('button', { name: 'Halle Mustermann entfernen' }));
        expect(screen.getByText('Noch 2 Positionen nicht zugeordnet')).toBeInTheDocument();
        expect(speichernKnopf()).toBeDisabled();
    });

    it('zeigt mit „Nur offene zeigen“ nur Positionen ohne Ziel', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: ZWEI_PROJEKTE });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
        await screen.findByRole('list', { name: 'Positionen der Rechnung' });
        await waehle(screen.getByRole('combobox', { name: 'Ziel für Position 1' }), 'Halle Mustermann');

        fireEvent.click(screen.getByRole('checkbox', { name: 'Nur offene zeigen' }));
        const zeilen = within(screen.getByRole('list', { name: 'Positionen der Rechnung' })).getAllByRole('listitem');
        expect(zeilen.map(z => z.getAttribute('aria-label'))).toEqual(['Position 2: Schrauben M12', 'Position 3: Fracht']);

        // Position wieder öffnen
        fireEvent.click(screen.getByRole('checkbox', { name: 'Nur offene zeigen' }));
        await waehle(screen.getByRole('combobox', { name: 'Ziel für Position 1' }), 'Nicht zugeordnet');
        expect(screen.getByText('Noch 2 Positionen nicht zugeordnet')).toBeInTheDocument();
    });
    describe('Ziehen und Ablegen', () => {
        async function oeffnePositionen() {
            richteEin({ positionen: uebersicht(), zuordnungen: ZWEI_PROJEKTE });
            render(<ZuordnungModal {...props} />);
            fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));
            await screen.findByRole('list', { name: 'Positionen der Rechnung' });
        }

        it('zeigt jedes Projekt als Ablagefläche plus „Nicht zugeordnet“', async () => {
            await oeffnePositionen();
            expect(screen.getByRole('group', { name: 'Ziel Halle Mustermann' })).toBeInTheDocument();
            expect(screen.getByRole('group', { name: 'Ziel Carport Musterfrau' })).toBeInTheDocument();
            expect(screen.getByRole('group', { name: 'Nicht zugeordnet' })).toHaveTextContent('2 Positionen');
        });

        it('ordnet eine Position per Ablegen auf ein Projekt zu', async () => {
            await oeffnePositionen();
            ablegen(1, 'p:20');
            expect(zielVon(1)).toHaveTextContent('Carport Musterfrau');
            expect(zielVon(2)).toHaveTextContent('Zuordnen zu …');
            expect(screen.getByRole('group', { name: 'Ziel Carport Musterfrau' })).toHaveTextContent('1 Position');
        });

        it('nimmt beim Ziehen einer markierten Zeile alle markierten mit', async () => {
            await oeffnePositionen();
            fireEvent.click(screen.getByRole('checkbox', { name: 'Position 1 markieren' }));
            fireEvent.click(screen.getByRole('checkbox', { name: 'Position 2 markieren' }));
            ablegen(2, 'p:10');
            expect(zielVon(1)).toHaveTextContent('Halle Mustermann');
            expect(zielVon(2)).toHaveTextContent('Halle Mustermann');
            // Markierung ist nach dem Ablegen aufgehoben
            expect(screen.getByRole('checkbox', { name: 'Position 1 markieren' })).not.toBeChecked();
            expect(screen.getByText('Alle 2 zugeordnet')).toBeInTheDocument();
        });

        it('zieht eine nicht markierte Zeile allein, auch wenn andere markiert sind', async () => {
            await oeffnePositionen();
            fireEvent.click(screen.getByRole('checkbox', { name: 'Position 1 markieren' }));
            ablegen(2, 'p:20');
            expect(zielVon(1)).toHaveTextContent('Zuordnen zu …');
            expect(zielVon(2)).toHaveTextContent('Carport Musterfrau');
            expect(screen.getByRole('checkbox', { name: 'Position 1 markieren' })).toBeChecked();
        });

        it('hebt die Zuordnung auf „Nicht zugeordnet“ auf und ignoriert Ablegen daneben', async () => {
            await oeffnePositionen();
            ablegen(1, 'p:10');
            expect(zielVon(1)).toHaveTextContent('Halle Mustermann');
            ablegen(1, null);
            expect(zielVon(1)).toHaveTextContent('Halle Mustermann');
            ablegen(1, '');
            expect(zielVon(1)).toHaveTextContent('Zuordnen zu …');
            expect(screen.getByText('Noch 2 Positionen nicht zugeordnet')).toBeInTheDocument();
        });

        it('zeigt Kennfarbe des Projekts in der Zeile', async () => {
            await oeffnePositionen();
            ablegen(1, 'p:10');
            const zeile = screen.getByRole('listitem', { name: 'Position 1: Stahlträger HEB 200' });
            expect(zeile.className).toContain('border-l-emerald-500');
            const offen = screen.getByRole('listitem', { name: 'Position 2: Schrauben M12' });
            expect(offen.className).toContain('border-l-transparent');
        });

        it('behält die Kennfarbe, wenn ein anderes Ziel entfernt wird, und vergibt freie Farben neu', async () => {
            await oeffnePositionen();
            ablegen(2, 'p:20');
            const schrauben = () => screen.getByRole('listitem', { name: 'Position 2: Schrauben M12' });
            // zweites Ziel → zweite Farbe der Reihe
            expect(schrauben().className).toContain('border-l-cyan-500');

            fireEvent.click(screen.getByRole('button', { name: 'Halle Mustermann entfernen' }));
            // Carport bleibt cyan, obwohl es jetzt an erster Stelle steht
            expect(schrauben().className).toContain('border-l-cyan-500');

            // neues Projekt bekommt die frei gewordene erste Farbe
            fireEvent.click(screen.getByRole('button', { name: /Projekt hinzufügen/ }));
            fireEvent.click(await screen.findByRole('button', { name: 'Testprojekt wählen' }));
            ablegen(1, 'p:30');
            expect(screen.getByRole('listitem', { name: 'Position 1: Stahlträger HEB 200' }).className).toContain('border-l-emerald-500');
            expect(schrauben().className).toContain('border-l-cyan-500');
        });

        it('Nebenkosten lassen sich nicht ziehen', async () => {
            await oeffnePositionen();
            const fracht = screen.getByRole('listitem', { name: 'Position 3: Fracht' });
            expect(fracht.querySelector('[data-griff]')).toBeNull();
            expect(screen.getByRole('listitem', { name: 'Position 1: Stahlträger HEB 200' }).querySelector('[data-griff]')).not.toBeNull();
            ablegen(3, 'p:10');
            expect(screen.getByText('Noch 2 Positionen nicht zugeordnet')).toBeInTheDocument();
        });
    });

    it('fordert ohne Projekte zuerst die Projektauswahl und übernimmt das gewählte Projekt', async () => {
        richteEin({ positionen: uebersicht(), zuordnungen: [] });
        render(<ZuordnungModal {...props} />);
        fireEvent.click(await screen.findByRole('button', { name: /Nach Positionen/ }));

        expect(await screen.findByText('Zuerst die Projekte auswählen, die zu diesem Beleg gehören.')).toBeInTheDocument();
        expect(zielVon(1)).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Projekte auswählen' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Testprojekt wählen' }));

        expect(screen.getByRole('group', { name: 'Ziel Garage Musterweg' })).toBeInTheDocument();
        // genau ein Projekt → alle Warenpositionen gehen gleich dorthin
        expect(screen.getByText('Alle 2 zugeordnet')).toBeInTheDocument();
        expect(zielVon(1)).toHaveTextContent('Garage Musterweg');
    });
});
