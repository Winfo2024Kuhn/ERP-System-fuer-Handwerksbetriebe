import { describe, expect, it } from 'vitest';
import { baueKettenGraph, gruppiereNachRechnung, istRechnungsTyp, juengstesBestellDokument, linearerGraph, type GraphDokument, type KettenGruppierung } from './kettenGraph';

function dok(id: number, typ: GraphDokument['typ'], datum: string | null, extra: Partial<GraphDokument> = {}): GraphDokument {
    return { id, typ, dokumentDatum: datum, ...extra };
}

const AB = dok(1, 'AUFTRAGSBESTAETIGUNG', '2026-08-01');
const LS = dok(2, 'LIEFERSCHEIN', '2026-08-10');
const RE = dok(3, 'RECHNUNG', '2026-08-20');

/** Kurzform zum Vergleichen: [Zeile oben, Zeile unten, Laufspur]. */
function kurz(graph: ReturnType<typeof baueKettenGraph>) {
    return graph.kanten.map(k => [k.vonZeile, k.zuZeile, k.spur]);
}

describe('baueKettenGraph', () => {
    it('legt eine lineare Kette AB → Lieferschein → Rechnung auf den Stamm', () => {
        const graph = baueKettenGraph([RE, LS, AB], [{ vonId: 3, zuId: 2 }, { vonId: 2, zuId: 1 }]);
        expect(graph.zeilen.map(z => z.dokument?.id)).toEqual([1, 2, 3]);
        expect(graph.zeilen.map(z => z.spur)).toEqual([0, 0, 0]);
        expect(graph.zeilen.map(z => z.istRechnung)).toEqual([false, false, true]);
        expect(graph.zeilen.every(z => z.hatVerbindung)).toBe(true);
        expect(kurz(graph)).toEqual([[0, 1, 0], [1, 2, 0]]);
        expect(graph.spuren).toBe(1);
    });

    it('verzweigt vier Lieferscheine als Äste und führt sie in eine Rechnung zusammen', () => {
        const lieferscheine = [10, 11, 12, 13].map((id, i) => dok(id, 'LIEFERSCHEIN', `2026-08-1${i}`));
        const rechnung = dok(20, 'RECHNUNG', '2026-09-01');
        const verbindungen = [
            ...lieferscheine.map(l => ({ vonId: l.id, zuId: AB.id })),
            ...lieferscheine.map(l => ({ vonId: rechnung.id, zuId: l.id })),
            // Umweg-Kante: wird weggelassen, weil die Rechnung schon über die Lieferscheine an der AB hängt
            { vonId: rechnung.id, zuId: AB.id },
        ];
        const graph = baueKettenGraph([rechnung, ...lieferscheine, AB], verbindungen);

        expect(graph.zeilen.map(z => z.dokument?.id)).toEqual([1, 10, 11, 12, 13, 20]);
        expect(graph.zeilen.map(z => z.spur)).toEqual([0, 0, 1, 2, 3, 0]);
        expect(graph.spuren).toBe(4);
        // Die Äste gehen von der AB ab und münden in die Rechnung auf dem Stamm
        const inRechnung = graph.kanten.filter(k => k.zuZeile === 5);
        expect(inRechnung).toHaveLength(4);
        expect(inRechnung.every(k => k.zuSpur === 0)).toBe(true);
        expect(graph.kanten.some(k => k.vonZeile === 0 && k.zuZeile === 5)).toBe(false);
        expect(graph.kanten.filter(k => k.vonZeile === 0).map(k => k.spur)).toEqual([0, 1, 2, 3]);
    });

    it('verzweigt zwei Teilrechnungen unten an derselben AB', () => {
        const re1 = dok(30, 'RECHNUNG', '2026-09-01');
        const re2 = dok(31, 'RECHNUNG', '2026-09-15');
        const graph = baueKettenGraph([re2, AB, re1], [{ vonId: 30, zuId: 1 }, { vonId: 31, zuId: 1 }]);
        expect(graph.zeilen.map(z => [z.dokument?.id, z.spur])).toEqual([[1, 0], [30, 0], [31, 1]]);
        expect(graph.kanten).toEqual([
            { vonZeile: 0, vonSpur: 0, zuZeile: 1, zuSpur: 0, spur: 0, gestrichelt: false },
            { vonZeile: 0, vonSpur: 0, zuZeile: 2, zuSpur: 1, spur: 1, gestrichelt: false },
        ]);
        expect(graph.spuren).toBe(2);
    });

    it('hängt ohne Rechnung einen offenen Platzhalter an, in den alle Enden münden', () => {
        const ls1 = dok(40, 'LIEFERSCHEIN', '2026-08-05');
        const ls2 = dok(41, 'LIEFERSCHEIN', '2026-08-06');
        const graph = baueKettenGraph([AB, ls1, ls2], [{ vonId: 40, zuId: 1 }, { vonId: 41, zuId: 1 }], { offenesEnde: true });
        const letzte = graph.zeilen[graph.zeilen.length - 1];
        expect(letzte).toMatchObject({ art: 'offen', dokument: null, spur: 0, istRechnung: false, hatVerbindung: false });
        const offen = graph.kanten.filter(k => k.zuZeile === 3);
        expect(offen.map(k => [k.vonZeile, k.gestrichelt])).toEqual([[1, true], [2, true]]);
        expect(graph.kanten.filter(k => !k.gestrichelt).every(k => k.zuZeile < 3)).toBe(true);
    });

    it('hängt den offenen Platzhalter ohne Bestelldokument an die letzte Zeile', () => {
        const graph = baueKettenGraph([RE], [], { offenesEnde: true });
        expect(kurz(graph)).toEqual([[0, 1, 0]]);
        expect(graph.kanten[0].gestrichelt).toBe(true);
    });

    it('zeigt ausgeblendete Dokumente weiter im Graphen', () => {
        const versteckt = dok(50, 'RECHNUNG', '2026-09-01', { ausgeblendet: true });
        const graph = baueKettenGraph([AB, versteckt], [{ vonId: 50, zuId: 1 }]);
        expect(graph.zeilen.map(z => z.dokument?.ausgeblendet ?? false)).toEqual([false, true]);
        expect(kurz(graph)).toEqual([[0, 1, 0]]);
    });

    it('ignoriert Kanten auf Dokumente außerhalb der Kette, doppelte Kanten und Schleifen', () => {
        const graph = baueKettenGraph([AB, LS], [
            { vonId: 2, zuId: 999 },
            { vonId: 999, zuId: 1 },
            { vonId: 2, zuId: 1 },
            { vonId: 1, zuId: 2 },
            { vonId: 1, zuId: 1 },
        ]);
        expect(kurz(graph)).toEqual([[0, 1, 0]]);
        expect(graph.zeilen.map(z => z.hatVerbindung)).toEqual([true, true]);
    });

    it('verbindet ohne Verknüpfungen linear wie bisher', () => {
        const angebot = dok(60, 'ANGEBOT', '2026-07-01');
        const graph = baueKettenGraph([RE, LS, angebot, AB], undefined);
        expect(graph.zeilen.map(z => z.dokument?.id)).toEqual([60, 1, 2, 3]);
        expect(kurz(graph)).toEqual([[0, 1, 0], [1, 2, 0], [2, 3, 0]]);
        expect(graph.zeilen.every(z => !z.hatVerbindung)).toBe(true);
    });

    it('hängt eine lose zweite Rechnung an das letzte Bestelldokument statt an die erste Rechnung', () => {
        const re1 = dok(70, 'RECHNUNG', '2026-09-01');
        const re2 = dok(71, 'RECHNUNG', '2026-09-02');
        const graph = baueKettenGraph([AB, LS, re1, re2], [{ vonId: 2, zuId: 1 }, { vonId: 70, zuId: 2 }]);
        expect(kurz(graph)).toEqual([[0, 1, 0], [1, 2, 0], [1, 3, 1]]);
        expect(graph.zeilen.map(z => z.spur)).toEqual([0, 0, 0, 1]);
    });

    it('hängt ein loses erstes Dokument an das darunter und sortiert ohne Datum nach hinten', () => {
        const angebot = dok(80, 'ANGEBOT', null);
        const graph = baueKettenGraph([LS, angebot, AB], [{ vonId: 2, zuId: 1 }]);
        expect(graph.zeilen.map(z => z.dokument?.id)).toEqual([80, 1, 2]);
        expect(kurz(graph)).toEqual([[0, 1, 0], [1, 2, 0]]);
    });

    describe('viele Spuren (Angebot, 2 ABs, 2 Lieferscheine, 2 Rechnungen)', () => {
        const viele = [
            dok(50, 'ANGEBOT', '2026-04-08'), dok(51, 'AUFTRAGSBESTAETIGUNG', '2026-04-08'), dok(52, 'AUFTRAGSBESTAETIGUNG', '2026-04-08'),
            dok(53, 'LIEFERSCHEIN', '2026-04-08'), dok(54, 'LIEFERSCHEIN', '2026-04-14'),
            dok(55, 'RECHNUNG', '2026-04-16'), dok(56, 'RECHNUNG', '2026-04-20'),
        ];
        const kreuz = [[51, 50], [52, 50], [53, 50], [54, 50], [55, 51], [55, 53], [55, 54], [56, 52], [56, 53], [56, 54]]
            .map(([vonId, zuId]) => ({ vonId, zuId }));

        it('braucht ohne Grenze sechs Spuren', () => {
            const graph = baueKettenGraph(viele, kreuz);
            expect(graph.spuren).toBe(6);
            expect(graph.zusammengefasst).toBe(false);
        });

        it('legt mit maxSpuren alle äußeren Spuren in die letzte erlaubte', () => {
            const graph = baueKettenGraph(viele, kreuz, { maxSpuren: 4 });
            expect(graph.spuren).toBe(4);
            expect(graph.zusammengefasst).toBe(true);
            expect(graph.zeilen.map(z => z.spur)).toEqual([0, 0, 1, 2, 3, 0, 1]);
            for (const k of graph.kanten) {
                expect(Math.max(k.vonSpur, k.zuSpur, k.spur)).toBeLessThanOrEqual(3);
            }
            // keine Kante geht verloren
            expect(graph.kanten).toHaveLength(baueKettenGraph(viele, kreuz).kanten.length);
        });

        it('lässt schmale Graphen unter der Grenze unverändert', () => {
            expect(baueKettenGraph(viele, kreuz, { maxSpuren: 6 })).toEqual(baueKettenGraph(viele, kreuz));
            expect(baueKettenGraph([AB, LS], [], { maxSpuren: 0 }).spuren).toBe(1);
        });
    });

    it('liefert für eine leere Kette nichts', () => {
        expect(baueKettenGraph([], [], { offenesEnde: true })).toEqual({ zeilen: [], kanten: [], spuren: 1, zusammengefasst: false });
    });

    it('nutzt das Eingangsdatum, wenn kein Belegdatum erkannt wurde', () => {
        const spaet = dok(90, 'LIEFERSCHEIN', null, { eingangsDatum: '2026-08-30' });
        const frueh = dok(91, 'LIEFERSCHEIN', '2026-08-02');
        const graph = baueKettenGraph([spaet, frueh], []);
        expect(graph.zeilen.map(z => z.dokument?.id)).toEqual([91, 90]);
    });
});

describe('juengstesBestellDokument', () => {
    it('wählt das jüngste sichtbare Bestelldokument', () => {
        const ls2 = dok(5, 'LIEFERSCHEIN', '2026-08-30', { ausgeblendet: true });
        expect(juengstesBestellDokument([AB, LS, RE, ls2])?.id).toBe(2);
    });

    it('nimmt bei gleichem Datum die spätere Stufe, dann die höhere ID', () => {
        const ab = dok(7, 'AUFTRAGSBESTAETIGUNG', '2026-08-10');
        const ls = dok(6, 'LIEFERSCHEIN', '2026-08-10');
        expect(juengstesBestellDokument([ls, ab])?.id).toBe(6);
        const ls2 = dok(8, 'LIEFERSCHEIN', '2026-08-10');
        expect(juengstesBestellDokument([ls, ls2])?.id).toBe(8);
        expect(juengstesBestellDokument([ls2, ls])?.id).toBe(8);
    });

    it('nimmt ausgeblendete nur, wenn nichts anderes da ist, und Dokumente ohne Datum zuletzt', () => {
        const versteckt = dok(9, 'LIEFERSCHEIN', '2026-08-10', { ausgeblendet: true });
        expect(juengstesBestellDokument([versteckt, RE])?.id).toBe(9);
        const ohneDatum = dok(11, 'LIEFERSCHEIN', null);
        expect(juengstesBestellDokument([ohneDatum, AB])?.id).toBe(1);
        expect(juengstesBestellDokument([AB, ohneDatum])?.id).toBe(1);
    });

    it('liefert null ohne Bestelldokument', () => {
        expect(juengstesBestellDokument([RE, dok(12, 'SONSTIG', null)])).toBeNull();
    });
});

describe('istRechnungsTyp', () => {
    it('erkennt Rechnung und Gutschrift', () => {
        expect(istRechnungsTyp('RECHNUNG')).toBe(true);
        expect(istRechnungsTyp('GUTSCHRIFT')).toBe(true);
        expect(istRechnungsTyp('LIEFERSCHEIN')).toBe(false);
    });
});

describe('gruppiereNachRechnung', () => {
    const v = (paare: number[][]) => paare.map(([vonId, zuId]) => ({ vonId, zuId }));
    /** Kurzform: IDs und „auch oben“ als „id*“. */
    function kurzGruppen(g: KettenGruppierung<GraphDokument>) {
        if (g.art === 'einfach') return 'einfach';
        const ids = (liste: { dokument: GraphDokument; auchOben: boolean }[]) => liste.map(b => `${b.dokument.id}${b.auchOben ? '*' : ''}`);
        return {
            bestellung: ids(g.bestellung),
            gruppen: g.gruppen.map(gr => ({ rechnung: gr.rechnung.dokument.id, zuleitungen: ids(gr.zuleitungen), gutschriften: ids(gr.gutschriften) })),
            ohneRechnung: ids(g.ohneRechnung),
        };
    }

    // Fall wie bei einem echten Lieferanten (erfundene Nummern): Angebot, zwei ABs, zwei Lieferscheine, zwei Rechnungen
    const angebot = dok(1, 'ANGEBOT', '2026-04-08');
    const ab1 = dok(2, 'AUFTRAGSBESTAETIGUNG', '2026-04-08');
    const ab2 = dok(3, 'AUFTRAGSBESTAETIGUNG', '2026-04-08');
    const ls03 = dok(4, 'LIEFERSCHEIN', '2026-04-08');
    const ls01 = dok(5, 'LIEFERSCHEIN', '2026-04-14');
    const re1 = dok(6, 'RECHNUNG', '2026-04-16');
    const re2 = dok(7, 'RECHNUNG', '2026-04-20');
    const kette = [re2, ls01, angebot, re1, ab2, ls03, ab1];
    const verbindungen = v([[2, 1], [3, 1], [4, 2], [5, 3], [6, 5], [7, 2], [7, 3], [7, 4], [7, 5]]);

    it('teilt mehrere Rechnungen in je eine kleine Gabel', () => {
        expect(kurzGruppen(gruppiereNachRechnung(kette, verbindungen))).toEqual({
            bestellung: ['1', '2', '3'],
            gruppen: [
                { rechnung: 6, zuleitungen: ['5'], gutschriften: [] },
                // Lieferschein 5 gehört zu beiden Rechnungen: beim zweiten Mal „auch oben“
                { rechnung: 7, zuleitungen: ['4', '5*'], gutschriften: [] },
            ],
            ohneRechnung: [],
        });
    });

    it('bleibt bei höchstens einer Rechnung oder Gutschrift bei der einen Gabel', () => {
        expect(gruppiereNachRechnung([ab1, ls03, re1], v([[6, 4]]))).toEqual({ art: 'einfach' });
        expect(gruppiereNachRechnung([ab1, ls03], [])).toEqual({ art: 'einfach' });
    });

    it('nimmt die Lieferscheine der ABs, wenn die Rechnung nur an ABs hängt – sonst die ABs selbst', () => {
        const ohneLs = dok(8, 'RECHNUNG', '2026-05-01');
        const g = gruppiereNachRechnung([ab1, ab2, ls03, re1, ohneLs], v([[4, 2], [6, 2], [8, 3]]));
        expect(kurzGruppen(g)).toEqual({
            bestellung: ['2', '3'],
            gruppen: [
                { rechnung: 6, zuleitungen: ['4'], gutschriften: [] },
                { rechnung: 8, zuleitungen: ['3*'], gutschriften: [] },
            ],
            ohneRechnung: [],
        });
    });

    it('sammelt Lieferscheine ohne Rechnung am Ende', () => {
        const ls9 = dok(9, 'LIEFERSCHEIN', '2026-05-02');
        const g = gruppiereNachRechnung([ab1, ls03, ls01, ls9, re1, re2], v([[6, 4], [7, 5], [9, 2]]));
        expect(kurzGruppen(g)).toMatchObject({ ohneRechnung: ['9'] });
    });

    it('hängt Gutschriften unter ihre Rechnung, sonst in eine eigene Gruppe', () => {
        const gs1 = dok(10, 'GUTSCHRIFT', '2026-04-01'); // direkt an Rechnung 7, trotz früherem Datum darunter
        const gs2 = dok(11, 'GUTSCHRIFT', '2026-05-01'); // über gemeinsamen Lieferschein 5 an Rechnung 6
        const gs3 = dok(12, 'GUTSCHRIFT', '2026-06-01'); // ohne Bezug
        const g = gruppiereNachRechnung([ls03, ls01, re1, re2, gs1, gs2, gs3], v([[6, 5], [7, 4], [10, 7], [11, 5]]));
        expect(kurzGruppen(g)).toEqual({
            bestellung: [],
            gruppen: [
                { rechnung: 6, zuleitungen: ['5'], gutschriften: ['11'] },
                { rechnung: 7, zuleitungen: ['4'], gutschriften: ['10'] },
                { rechnung: 12, zuleitungen: [], gutschriften: [] },
            ],
            ohneRechnung: [],
        });
    });

    it('ignoriert fremde Kanten und Schleifen und merkt sich echte Verbindungen', () => {
        const g = gruppiereNachRechnung([ls03, re1, re2], v([[6, 4], [7, 999], [7, 7]]));
        if (g.art !== 'je-rechnung') throw new Error('erwartet je-rechnung');
        expect(g.gruppen.map(gr => gr.rechnung.hatVerbindung)).toEqual([true, false]);
        expect(g.gruppen[1].zuleitungen).toEqual([]);
        expect(gruppiereNachRechnung([re1, re2], undefined).art).toBe('je-rechnung');
    });
});

describe('linearerGraph', () => {
    it('legt die Belege auf einen geraden Stamm, optional mit offenem Ende', () => {
        const belege = [
            { dokument: LS, hatVerbindung: true, auchOben: false },
            { dokument: RE, hatVerbindung: false, auchOben: true },
        ];
        const graph = linearerGraph(belege, { offenesEnde: true });
        expect(graph.zeilen.map(z => [z.art, z.spur, z.istRechnung, z.auchOben ?? false])).toEqual([
            ['dokument', 0, false, false], ['dokument', 0, true, true], ['offen', 0, false, false],
        ]);
        expect(graph.kanten.map(k => [k.vonZeile, k.zuZeile, k.gestrichelt])).toEqual([[0, 1, false], [1, 2, true]]);
        expect(graph.spuren).toBe(1);
        expect(linearerGraph([], { offenesEnde: true }).zeilen).toEqual([]);
    });
});
