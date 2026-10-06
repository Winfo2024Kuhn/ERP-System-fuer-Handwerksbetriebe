/**
 * Baut aus den Dokumenten einer Bestellung und ihren Verknüpfungen einen
 * Graphen wie bei git: Bestelldokumente oben, Rechnungen unten, Spur 0 ist der
 * Stamm, höhere Spuren sind Äste (Teillieferungen, Teilrechnungen).
 *
 * Reine Funktion ohne React – das Zeichnen übernimmt `KettenGabel.tsx`.
 */
import { parseIsoDatum, type KettenDokumentTyp } from './bestellungenListe';

export interface GraphDokument {
    id: number;
    typ: KettenDokumentTyp;
    dokumentDatum: string | null;
    eingangsDatum?: string | null;
    ausgeblendet?: boolean;
}

/** Kante innerhalb der Kette: `vonId` ist der Nachfolger (z. B. Rechnung), `zuId` der Vorgänger. */
export interface KettenVerbindung {
    vonId: number;
    zuId: number;
}

export interface GraphZeile<D extends GraphDokument> {
    /** `offen` = Platzhalter für die fehlende Rechnung am Ende einer laufenden Bestellung. */
    art: 'dokument' | 'offen';
    dokument: D | null;
    /** 0 = Stamm, ab 1 = Ast */
    spur: number;
    istRechnung: boolean;
    /** Hat das Dokument echte Verknüpfungen? Nur dann lässt es sich abhängen. */
    hatVerbindung: boolean;
    /** Beleg steht weiter oben schon einmal (nur bei der Darstellung je Rechnung). */
    auchOben?: boolean;
}

/** Linie von einer Zeile (oben) zu einer späteren Zeile (unten). */
export interface GraphKante {
    vonZeile: number;
    vonSpur: number;
    zuZeile: number;
    zuSpur: number;
    /** Spur, in der die Linie zwischen den beiden Punkten verläuft. */
    spur: number;
    /** Führt zur fehlenden Rechnung. */
    gestrichelt: boolean;
}

export interface KettenGraph<D extends GraphDokument> {
    zeilen: GraphZeile<D>[];
    kanten: GraphKante[];
    /** Anzahl der benötigten Spuren (mindestens 1). */
    spuren: number;
    /** true, wenn äußere Spuren wegen `maxSpuren` in die letzte Spur gelegt wurden. */
    zusammengefasst: boolean;
}

/** Ausgeschriebene Namen für die Gabel (in der Karte ist Platz für „Auftragsbestätigung“). */
export const GABEL_LABELS: Record<KettenDokumentTyp, string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    LIEFERSCHEIN: 'Lieferschein',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
};

const RECHNUNGS_TYPEN: ReadonlySet<KettenDokumentTyp> = new Set(['RECHNUNG', 'GUTSCHRIFT']);

const BESTELL_STUFE: Partial<Record<KettenDokumentTyp, number>> = {
    ANGEBOT: 1,
    AUFTRAGSBESTAETIGUNG: 2,
    LIEFERSCHEIN: 3,
};

export function istRechnungsTyp(typ: KettenDokumentTyp): boolean {
    return RECHNUNGS_TYPEN.has(typ);
}

function datumWert(dok: GraphDokument): number {
    const datum = parseIsoDatum(dok.dokumentDatum) ?? parseIsoDatum(dok.eingangsDatum);
    return datum ? datum.getTime() : Number.POSITIVE_INFINITY;
}

/** Bestelldokumente nach Stufe (Angebot, AB, Lieferschein), dann Rechnungen; jeweils nach Datum. */
function sortiere<D extends GraphDokument>(dokumente: D[]): D[] {
    const rang = (d: D) => (istRechnungsTyp(d.typ) ? 10 : BESTELL_STUFE[d.typ] ?? 4);
    return [...dokumente].sort((a, b) => rang(a) - rang(b) || datumWert(a) - datumWert(b) || a.id - b.id);
}

/** Das jüngste Bestelldokument – daran wird eine hochgeladene Rechnung gehängt. */
export function juengstesBestellDokument<D extends GraphDokument>(dokumente: D[]): D | null {
    const bestellung = dokumente.filter(d => !istRechnungsTyp(d.typ) && d.typ !== 'SONSTIG');
    if (bestellung.length === 0) return null;
    const sichtbar = bestellung.filter(d => !d.ausgeblendet);
    const auswahl = sichtbar.length > 0 ? sichtbar : bestellung;
    const datum = (d: D) => {
        const wert = datumWert(d);
        return Number.isFinite(wert) ? wert : Number.NEGATIVE_INFINITY;
    };
    return auswahl.reduce((best, d) => {
        const unterschied = datum(d) - datum(best);
        if (unterschied !== 0) return unterschied > 0 ? d : best;
        const stufe = (BESTELL_STUFE[d.typ] ?? 0) - (BESTELL_STUFE[best.typ] ?? 0);
        if (stufe !== 0) return stufe > 0 ? d : best;
        return d.id > best.id ? d : best;
    });
}

/** Entfernt Kanten, die schon über einen Umweg verbunden sind (A→B→C macht A→C überflüssig). */
function ohneUmwege(kanten: Array<[number, number]>, anzahl: number): Array<[number, number]> {
    const nachfolger: number[][] = Array.from({ length: anzahl }, () => []);
    kanten.forEach(([oben, unten]) => nachfolger[oben].push(unten));
    const erreichbarOhneDirekt = (start: number, ziel: number): boolean => {
        const stapel = nachfolger[start].filter(n => n !== ziel);
        const besucht = new Set<number>();
        while (stapel.length > 0) {
            const knoten = stapel.pop() as number;
            if (knoten === ziel) return true;
            if (besucht.has(knoten)) continue;
            besucht.add(knoten);
            stapel.push(...nachfolger[knoten]);
        }
        return false;
    };
    return kanten.filter(([oben, unten]) => !erreichbarOhneDirekt(oben, unten));
}

/**
 * Ordnet Dokumente und Verknüpfungen in Zeilen und Spuren an.
 *
 * - Verknüpfungen auf Dokumente außerhalb der Kette werden ignoriert.
 * - Dokumente ohne jede Verknüpfung hängen am Dokument darüber (ohne
 *   Verknüpfungen ergibt das die bisherige lineare Kette).
 * - `offenesEnde`: hängt einen Platzhalter für die fehlende Rechnung an, in den
 *   alle Bestelldokumente ohne Nachfolger münden.
 * - `maxSpuren`: begrenzt die Breite; alle Spuren ab der letzten erlaubten
 *   laufen in dieser zusammen, damit die Textspalte lesbar bleibt.
 */
export function baueKettenGraph<D extends GraphDokument>(
    dokumente: D[],
    verbindungen: KettenVerbindung[] | null | undefined,
    optionen: { offenesEnde?: boolean; maxSpuren?: number } = {},
): KettenGraph<D> {
    const sortiert = sortiere(dokumente);
    const zeileVon = new Map<number, number>();
    sortiert.forEach((d, i) => zeileVon.set(d.id, i));

    // Echte Kanten als (obere Zeile, untere Zeile), ohne Doppelte und Schleifen
    const gesehen = new Set<string>();
    const echteKanten: Array<[number, number]> = [];
    const verbunden = new Set<number>();
    for (const v of verbindungen ?? []) {
        const a = zeileVon.get(v.vonId);
        const b = zeileVon.get(v.zuId);
        if (a === undefined || b === undefined || a === b) continue;
        verbunden.add(v.vonId);
        verbunden.add(v.zuId);
        const oben = Math.min(a, b);
        const unten = Math.max(a, b);
        const schluessel = `${oben}-${unten}`;
        if (gesehen.has(schluessel)) continue;
        gesehen.add(schluessel);
        echteKanten.push([oben, unten]);
    }

    // Lose Dokumente an das Dokument darüber hängen (das erste an das darunter).
    // Eine lose Rechnung hängt am letzten Bestelldokument, nicht an einer anderen Rechnung.
    const kanten: Array<[number, number]> = [...echteKanten];
    const hatKante = new Set<number>();
    echteKanten.forEach(([a, b]) => { hatKante.add(a); hatKante.add(b); });
    sortiert.forEach((d, i) => {
        if (hatKante.has(i) || sortiert.length < 2) return;
        let partner = i === 0 ? 1 : i - 1;
        if (i > 0 && istRechnungsTyp(d.typ)) {
            const bestellung = sortiert.slice(0, i).map(x => istRechnungsTyp(x.typ)).lastIndexOf(false);
            if (bestellung >= 0) partner = bestellung;
        }
        kanten.push([Math.min(i, partner), Math.max(i, partner)]);
        hatKante.add(i);
        hatKante.add(partner);
    });

    const zeilen: GraphZeile<D>[] = sortiert.map(d => ({
        art: 'dokument',
        dokument: d,
        spur: 0,
        istRechnung: istRechnungsTyp(d.typ),
        hatVerbindung: verbunden.has(d.id),
    }));

    const reduziert = ohneUmwege(kanten, sortiert.length);
    const gestrichelteKanten = new Set<string>();

    if (optionen.offenesEnde && sortiert.length > 0) {
        const offenIndex = sortiert.length;
        zeilen.push({ art: 'offen', dokument: null, spur: 0, istRechnung: false, hatVerbindung: false });
        const hatNachfolger = new Set(reduziert.map(([oben]) => oben));
        const enden = sortiert
            .map((d, i) => ({ d, i }))
            .filter(({ d, i }) => !hatNachfolger.has(i) && !istRechnungsTyp(d.typ))
            .map(({ i }) => i);
        (enden.length > 0 ? enden : [sortiert.length - 1]).forEach(i => {
            reduziert.push([i, offenIndex]);
            gestrichelteKanten.add(`${i}-${offenIndex}`);
        });
    }

    // Nachfolger je Zeile, nach Zeile sortiert
    const nachfolger: number[][] = zeilen.map(() => []);
    reduziert.forEach(([oben, unten]) => nachfolger[oben].push(unten));
    nachfolger.forEach(liste => liste.sort((a, b) => a - b));

    // Spuren wie bei git vergeben: jede belegte Spur trägt eine Linie zu einer späteren Zeile
    const spurBelegung: Array<{ ziel: number; kante: number } | null> = [];
    const ergebnis: GraphKante[] = [];
    const freieSpur = (abSpur = 0): number => {
        for (let s = abSpur; s < spurBelegung.length; s++) if (spurBelegung[s] === null) return s;
        spurBelegung.push(null);
        return spurBelegung.length - 1;
    };

    zeilen.forEach((zeile, index) => {
        const ankommend = spurBelegung
            .map((belegung, spur) => ({ belegung, spur }))
            .filter(({ belegung }) => belegung?.ziel === index);
        const spur = ankommend.length > 0 ? Math.min(...ankommend.map(a => a.spur)) : freieSpur();
        for (const { belegung, spur: s } of ankommend) {
            const kante = ergebnis[(belegung as { kante: number }).kante];
            kante.zuSpur = spur;
            spurBelegung[s] = null;
        }
        zeile.spur = spur;

        nachfolger[index].forEach((ziel, n) => {
            const laufSpur = n === 0 ? spur : freieSpur(spur + 1);
            ergebnis.push({
                vonZeile: index,
                vonSpur: spur,
                zuZeile: ziel,
                zuSpur: laufSpur,
                spur: laufSpur,
                gestrichelt: gestrichelteKanten.has(`${index}-${ziel}`),
            });
            spurBelegung[laufSpur] = { ziel, kante: ergebnis.length - 1 };
        });
    });

    const spuren = Math.max(1, ...zeilen.map(z => z.spur + 1), ...ergebnis.map(k => k.spur + 1));
    const grenze = optionen.maxSpuren !== undefined ? Math.max(1, Math.floor(optionen.maxSpuren)) : Number.POSITIVE_INFINITY;
    if (spuren <= grenze) return { zeilen, kanten: ergebnis, spuren, zusammengefasst: false };

    const letzte = grenze - 1;
    const begrenzt = (spur: number) => Math.min(spur, letzte);
    zeilen.forEach(z => { z.spur = begrenzt(z.spur); });
    ergebnis.forEach(k => {
        k.vonSpur = begrenzt(k.vonSpur);
        k.zuSpur = begrenzt(k.zuSpur);
        k.spur = begrenzt(k.spur);
    });
    return { zeilen, kanten: ergebnis, spuren: grenze, zusammengefasst: true };
}

// ---------------------------------------------------------------------------
// Darstellung je Rechnung: bei zwei oder mehr Rechnungen eine kleine Gabel pro Rechnung
// ---------------------------------------------------------------------------

export interface GruppenBeleg<D extends GraphDokument> {
    dokument: D;
    hatVerbindung: boolean;
    /** Steht schon weiter oben (Lieferschein in einer früheren Rechnung oder AB im Abschnitt „Bestellung“). */
    auchOben: boolean;
}

export interface RechnungsGruppe<D extends GraphDokument> {
    rechnung: GruppenBeleg<D>;
    /** Lieferscheine dieser Rechnung – ersatzweise die Lieferscheine ihrer ABs oder die ABs selbst. */
    zuleitungen: GruppenBeleg<D>[];
    /** Gutschriften zu dieser Rechnung, unter ihr. */
    gutschriften: GruppenBeleg<D>[];
}

export type KettenGruppierung<D extends GraphDokument> =
    /** Höchstens eine Rechnung/Gutschrift: eine Gabel über die ganze Kette (`baueKettenGraph`). */
    | { art: 'einfach' }
    | {
        art: 'je-rechnung';
        /** Angebote, ABs und Sonstiges – alles außer Lieferscheinen und Rechnungen. */
        bestellung: GruppenBeleg<D>[];
        gruppen: RechnungsGruppe<D>[];
        /** Lieferscheine, die noch zu keiner Rechnung gehören (Teillieferung, noch nicht abgerechnet). */
        ohneRechnung: GruppenBeleg<D>[];
    };

/**
 * Teilt eine Kette mit mehreren Rechnungen in kleine Gruppen: oben die Bestellung
 * (Angebote, ABs), darunter je Rechnung ihre Lieferscheine, am Ende die
 * Lieferscheine ohne Rechnung. Mit höchstens einer Rechnung/Gutschrift bleibt
 * es bei der einen Gabel (`art: 'einfach'`).
 */
export function gruppiereNachRechnung<D extends GraphDokument>(
    dokumente: D[],
    verbindungen: KettenVerbindung[] | null | undefined,
): KettenGruppierung<D> {
    if (dokumente.filter(d => istRechnungsTyp(d.typ)).length <= 1) return { art: 'einfach' };

    const sortiert = sortiere(dokumente);
    const nachId = new Map(sortiert.map(d => [d.id, d] as const));
    const nachbarn = new Map<number, Set<number>>(sortiert.map(d => [d.id, new Set<number>()]));
    for (const v of verbindungen ?? []) {
        if (v.vonId === v.zuId || !nachId.has(v.vonId) || !nachId.has(v.zuId)) continue;
        nachbarn.get(v.vonId)?.add(v.zuId);
        nachbarn.get(v.zuId)?.add(v.vonId);
    }
    const verbunden = (id: number) => (nachbarn.get(id)?.size ?? 0) > 0;
    const nachbarnVomTyp = (id: number, typ: KettenDokumentTyp): D[] =>
        sortiert.filter(d => d.typ === typ && nachbarn.get(id)?.has(d.id));
    const beleg = (d: D, auchOben = false): GruppenBeleg<D> => ({ dokument: d, hatVerbindung: verbunden(d.id), auchOben });

    const rechnungen = sortiert.filter(d => d.typ === 'RECHNUNG');
    const gutschriften = sortiert.filter(d => d.typ === 'GUTSCHRIFT');

    // Gutschrift unter die Rechnung, mit der sie direkt oder über einen gemeinsamen Beleg verbunden ist
    const gutschriftenZu = new Map<number, D[]>();
    const eigeneGruppen: D[] = [];
    for (const gs of gutschriften) {
        const direkt = rechnungen.find(r => nachbarn.get(gs.id)?.has(r.id));
        const gemeinsam = rechnungen.find(r => [...(nachbarn.get(gs.id) ?? [])].some(n => nachbarn.get(r.id)?.has(n)));
        const ziel = direkt ?? gemeinsam;
        if (ziel) gutschriftenZu.set(ziel.id, [...(gutschriftenZu.get(ziel.id) ?? []), gs]);
        else eigeneGruppen.push(gs);
    }
    const hauptbelege = sortiere([...rechnungen, ...eigeneGruppen]);

    const gezeigteLieferscheine = new Set<number>();
    const gruppen: RechnungsGruppe<D>[] = hauptbelege.map(haupt => {
        let zuleitungen = nachbarnVomTyp(haupt.id, 'LIEFERSCHEIN');
        if (zuleitungen.length === 0) {
            const abs = nachbarnVomTyp(haupt.id, 'AUFTRAGSBESTAETIGUNG');
            const lieferscheineDerAbs = sortiert.filter(d => d.typ === 'LIEFERSCHEIN' && abs.some(ab => nachbarn.get(ab.id)?.has(d.id)));
            zuleitungen = lieferscheineDerAbs.length > 0 ? lieferscheineDerAbs : abs;
        }
        const gruppe: RechnungsGruppe<D> = {
            rechnung: beleg(haupt),
            zuleitungen: zuleitungen.map(d => {
                // ABs stehen immer schon oben im Abschnitt „Bestellung“
                const schonGezeigt = d.typ !== 'LIEFERSCHEIN' || gezeigteLieferscheine.has(d.id);
                if (d.typ === 'LIEFERSCHEIN') gezeigteLieferscheine.add(d.id);
                return beleg(d, schonGezeigt);
            }),
            gutschriften: (gutschriftenZu.get(haupt.id) ?? []).map(d => beleg(d)),
        };
        return gruppe;
    });

    return {
        art: 'je-rechnung',
        bestellung: sortiert.filter(d => d.typ !== 'LIEFERSCHEIN' && !istRechnungsTyp(d.typ)).map(d => beleg(d)),
        gruppen,
        ohneRechnung: sortiert.filter(d => d.typ === 'LIEFERSCHEIN' && !gezeigteLieferscheine.has(d.id)).map(d => beleg(d)),
    };
}

/**
 * Gerader Stamm ohne Äste: die Belege in der gegebenen Reihenfolge untereinander,
 * optional mit gestricheltem offenen Ende. Für die kleinen Gabeln je Rechnung.
 */
export function linearerGraph<D extends GraphDokument>(
    belege: GruppenBeleg<D>[],
    optionen: { offenesEnde?: boolean } = {},
): KettenGraph<D> {
    const zeilen: GraphZeile<D>[] = belege.map(b => ({
        art: 'dokument',
        dokument: b.dokument,
        spur: 0,
        istRechnung: istRechnungsTyp(b.dokument.typ),
        hatVerbindung: b.hatVerbindung,
        auchOben: b.auchOben,
    }));
    if (optionen.offenesEnde && zeilen.length > 0) {
        zeilen.push({ art: 'offen', dokument: null, spur: 0, istRechnung: false, hatVerbindung: false });
    }
    const kanten: GraphKante[] = zeilen.slice(1).map((zeile, i) => ({
        vonZeile: i, vonSpur: 0, zuZeile: i + 1, zuSpur: 0, spur: 0, gestrichelt: zeile.art === 'offen',
    }));
    return { zeilen, kanten, spuren: 1, zusammengefasst: false };
}
