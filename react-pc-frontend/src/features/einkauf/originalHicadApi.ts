import { einkaufApi } from './api';
import { nutztEchtesBackend } from './originalBedarfApi';
import type { BedarfCreate, BedarfResponse, HiCadImportFortschritt, HiCadZeile, HiCadZeilenAuswahl, PositionSnapshot } from './types';

/**
 * Adapter für das „HiCAD-Sägeliste importieren“-Fenster aus dem EN1090-Stand.
 * Das Fenster arbeitet mit Profilgruppen und Sägelisten-Zeilen; das aktuelle Backend liefert
 * je Excel-Zeile einen Positions-Snapshot (`/api/einkauf/hicad/...`). Hier wird beides ineinander
 * übersetzt. Gruppierung und Zuschnitt-Optimierung (FFD) sind reine Berechnung und laufen hier.
 */

// ==================== Formen des Fensters (aus HicadImportDtos.java) ====================
export interface SaegelisteZeile {
    posNr?: number | string;
    anzahl: number;
    bezeichnung: string;
    laengeMm?: number;
    werkstoff?: string;
    anschnittSteg?: string;
    anschnittFlansch?: string;
    gewichtProStueckKg?: number;
    gesamtGewichtKg?: number;
    /** Mantelfläche der ganzen Position in m² (HiCAD-Profilsummenliste). */
    mantelflaecheM2?: number;
    /** URLs der Schnittbilder aus der HiCAD-Excel (null, wenn nicht vorhanden). */
    anschnittbildStegUrl?: string | null;
    anschnittbildFlanschUrl?: string | null;
    /** Nur echtes Backend: Bezug zur gespeicherten Importzeile. */
    zeilennummer?: number;
    snapshot?: PositionSnapshot;
    bildDateiIds?: number[];
    benennung?: string | null;
    /** Technisch eindeutiger Katalogartikel laut Backend (nur bei genau einem Kandidaten). */
    artikelKandidat?: number | null;
}

export interface ProfilGruppe {
    groupKey: string;
    bezeichnung: string;
    werkstoff?: string;
    artikelId?: number | null;
    artikelProduktname?: string | null;
    /** Interne Artikelnummer des zugeordneten Stammartikels (nur Anzeige). */
    artikelnummer?: string | null;
    verpackungseinheitM?: number | null;
    defaultAggregieren: boolean;
    summeMeter?: number;
    summeStueck?: number;
    /** Summe der bekannten Positionsgewichte (kg) und Mantelflächen (m²); `undefined`, wenn keine Zeile Werte hat. */
    summeKg?: number;
    summeMantelflaecheM2?: number;
    berechneteStaebe?: number;
    zeilen: SaegelisteZeile[];
}

export interface PreviewResponse {
    zeichnungsnr?: string;
    auftragsnummer?: string;
    auftragstext?: string;
    kunde?: string;
    ersteller?: string;
    erstelltAm?: string;
    erkannteProjektId?: number | null;
    erkannteProjektName?: string | null;
    gruppen: ProfilGruppe[];
    /** Nur echtes Backend. */
    importId?: number;
    importVersion?: number;
    dateiSchonImportiert?: boolean;
    /** Zeilen, die HiCAD nicht lesbar geliefert hat (Zeilennummer + Grund). */
    unlesbareZeilen?: Array<{ zeilennummer: number; grund: string }>;
}

export interface GruppenEntscheidung {
    aggregieren: boolean;
    artikelId: number | null;
    artikelProduktname: string | null;
    /** Interne Artikelnummer des zugeordneten Artikels (nur Anzeige). */
    artikelnummer?: string | null;
    /** Optional: Ohne Lieferant wird die Position trotzdem angelegt. */
    lieferantId: number | null;
    lieferantName: string | null;
    stangenlaengeM: number | null;
}

export interface ZuschnittPlan { anzahlStangen: number; belegtMm: number; verschnittMm: number; ueberlange: number }

export interface UebernahmeErgebnis { angelegtePositionen: number; erledigteGruppen: string[] }

/** Teilweise fehlgeschlagene Übernahme: bereits angelegte Gruppen dürfen beim Wiederholen nicht doppelt entstehen. */
export class HicadUebernahmeFehler extends Error {
    readonly erledigteGruppen: string[];
    readonly angelegtePositionen: number;
    constructor(message: string, erledigteGruppen: string[], angelegtePositionen: number) {
        super(message);
        this.name = 'HicadUebernahmeFehler';
        this.erledigteGruppen = erledigteGruppen;
        this.angelegtePositionen = angelegtePositionen;
    }
}

export const FALLBACK_STANGE_M = 6;
/** Profil-Präfixe, die per Default als „selbst schneiden“ vorgeschlagen werden (wie im EN1090-Backend). */
const DEFAULT_AGGREGIEREN_PREFIXES = ['FRR', 'FRQ', 'RR', 'RQ', 'L '];

interface HiCadKopf { zeichnungsnummer: string | null; auftragsnummer: string | null; auftragstext: string | null; kunde: string | null }
interface HiCadVorschauMitKopf { id: number; dateiHash: string; dateiSchonImportiert: boolean; zeilen: HiCadZeile[]; kopf?: HiCadKopf | null }
interface ArtikelTreffer { id: number; produktname: string; artikelnummer?: string | null; werkstoffName?: string | null; verpackungseinheit?: number | string | null }

const vergleichswert = (text?: string | null) => (text ?? '').replace(/\s+/g, '').toLowerCase();
/** Kaufmännisch runden (HALF_UP wie im Backend); toPrecision entfernt Binär-Rundungsreste wie 6,43499… */
const rund = (wert: number, stellen: number) => {
    const faktor = 10 ** stellen;
    return Math.round(Number(wert.toPrecision(12)) * faktor) / faktor;
};

async function fehlermeldungAus(antwort: Response, rueckfall: string): Promise<string> {
    try {
        const inhalt = await antwort.json() as { message?: unknown };
        if (typeof inhalt?.message === 'string' && inhalt.message.trim()) return inhalt.message.trim();
    } catch { /* leere oder Nicht-JSON-Antwort */ }
    return `${rueckfall} (HTTP ${antwort.status}).`;
}

// ==================== Reine Berechnung ====================

/**
 * Zuschnitt-Optimierung per First-Fit-Decreasing: minimale Stangenzahl für alle Zuschnitte.
 * Gerechnet wird in Zehntelmillimetern, damit HiCAD-Längen wie 763,6 mm ohne Rundungsfehler passen.
 * Zuschnitte, die länger sind als die Stange, zählen als eigene (zu kurze) Stange.
 */
export function optimiereStangen(zeilen: SaegelisteZeile[], stangenlaengeM: number): ZuschnittPlan {
    const stange = Math.round(stangenlaengeM * 10_000);
    const teile: number[] = [];
    for (const zeile of zeilen) {
        const laenge = Math.round((zeile.laengeMm ?? 0) * 10);
        if (!(zeile.anzahl > 0) || !(laenge > 0)) continue;
        for (let i = 0; i < zeile.anzahl; i++) teile.push(laenge);
    }
    teile.sort((a, b) => b - a);
    const belegung: number[] = [];
    let belegt = 0;
    let ueberlange = 0;
    for (const laenge of teile) {
        belegt += laenge;
        if (laenge > stange) { ueberlange++; belegung.push(laenge); continue; }
        const index = belegung.findIndex(summe => summe + laenge <= stange);
        if (index >= 0) belegung[index] += laenge; else belegung.push(laenge);
    }
    const verschnitt = Math.max(0, belegung.length * stange - belegt);
    return { anzahlStangen: belegung.length, belegtMm: belegt / 10, verschnittMm: verschnitt / 10, ueberlange };
}

/**
 * 0° ist in HiCAD ein gerader Schnitt, kein Anschnitt („0“, „0°“, „0,0°“, „0.0°“).
 * Das Backend liefert solche Seiten schon als `null`; hier wird es für ältere Importe zusätzlich abgesichert.
 */
export function echterWinkel(winkel?: string | null): string | null {
    const text = winkel?.trim();
    if (!text) return null;
    const zahl = Number(text.replace(/°/g, '').replace(',', '.').trim());
    return Number.isFinite(zahl) && zahl === 0 ? null : text;
}

/** Übersetzt eine gespeicherte Importzeile in eine Sägelisten-Zeile; unlesbare Zeilen liefern `null`. */
export function saegelisteZeile(zeile: HiCadZeile): SaegelisteZeile | null {
    const p = zeile.vorschlag;
    const menge = p?.basis?.menge;
    if (!p || menge == null || !(menge > 0)) return null;
    const profil = p.abmessung?.trim() || p.bezeichnung?.trim() || 'Ohne Bezeichnung';
    const links = echterWinkel(p.winkelLinks);
    const rechts = echterWinkel(p.winkelRechts);
    // Nur echte Winkel anzeigen; bei 45°/0° bleibt allein die 45°-Seite stehen.
    const winkel = links && rechts ? `${links} ${rechts}` : (links ?? rechts ?? undefined);
    // Ohne echten Winkel ist die Zeile ein normaler Fixzuschnitt: kein Anschnittbild in der Anzeige.
    // Die Bild-IDs bleiben für die Übernahme erhalten, weil das Backend alle Bilder der Zeile bestätigt haben will.
    const bilder = winkel ? zeile.bilder.map(bild => bild.url) : [];
    const nurFlansch = p.schnittForm === 'Anschnitt Flansch';
    const anzahl = p.basis?.einheit === 'STUECK' ? menge : (p.basis?.stueckzahl ?? menge);
    const laengeMm = p.basis?.einzelLaengeMm ?? undefined;
    const kgJeMeter = p.basis?.kgJeMeter ?? null;
    const gesamtAusProfilsumme = p.basis?.gesamtgewichtKg ?? null;
    // Gewicht aus der Profilsummenliste hat Vorrang; sonst aus dem Stückgewicht der Sägeliste.
    const gewicht = gesamtAusProfilsumme != null && anzahl > 0 ? rund(gesamtAusProfilsumme / anzahl, 3)
        : kgJeMeter != null && laengeMm != null ? rund(kgJeMeter * laengeMm / 1000, 2) : undefined;
    const gesamtGewichtKg = gesamtAusProfilsumme ?? (gewicht == null ? undefined : rund(gewicht * anzahl, 2));
    return {
        zeilennummer: zeile.zeilennummer, snapshot: p, bildDateiIds: zeile.bilder.map(bild => bild.dateiId),
        artikelKandidat: zeile.artikelKandidaten.length === 1 ? zeile.artikelKandidaten[0] : null,
        posNr: p.positionsnummer ?? p.interneReferenz ?? undefined, anzahl, bezeichnung: profil, benennung: p.bezeichnung,
        laengeMm, werkstoff: p.werkstoff ?? undefined,
        anschnittSteg: nurFlansch ? undefined : winkel, anschnittFlansch: nurFlansch ? winkel : undefined,
        anschnittbildStegUrl: nurFlansch ? null : bilder[0] ?? null,
        anschnittbildFlanschUrl: nurFlansch ? bilder[0] ?? null : bilder[1] ?? null,
        gewichtProStueckKg: gewicht, gesamtGewichtKg, mantelflaecheM2: p.basis?.mantelflaecheM2 ?? undefined,
    };
}

/** Gruppiert nach Profil + Werkstoff und berechnet Summen und Stangenbedarf (wie das EN1090-Backend). */
export function gruppiereZeilen(zeilen: SaegelisteZeile[]): ProfilGruppe[] {
    const gruppen = new Map<string, ProfilGruppe>();
    for (const zeile of zeilen) {
        const bezeichnung = zeile.bezeichnung.trim();
        const werkstoff = zeile.werkstoff?.trim() ?? '';
        const key = `${bezeichnung.toLocaleLowerCase('de-DE')}||${werkstoff.toLocaleLowerCase('de-DE')}`;
        let gruppe = gruppen.get(key);
        if (!gruppe) {
            gruppe = { groupKey: key, bezeichnung, werkstoff: werkstoff || undefined, artikelId: null, artikelProduktname: null,
                verpackungseinheitM: null, zeilen: [],
                defaultAggregieren: DEFAULT_AGGREGIEREN_PREFIXES.some(prefix => bezeichnung.toLocaleUpperCase('de-DE').startsWith(prefix)) };
            gruppen.set(key, gruppe);
        }
        gruppe.zeilen.push(zeile);
    }
    return [...gruppen.values()].map(gruppe => {
        const kandidaten = new Set(gruppe.zeilen.map(z => z.artikelKandidat ?? null));
        return mitSummen({ ...gruppe, artikelId: kandidaten.size === 1 ? [...kandidaten][0] : null });
    });
}

export function mitSummen(gruppe: ProfilGruppe): ProfilGruppe {
    const summeStueck = gruppe.zeilen.reduce((summe, z) => summe + (z.anzahl > 0 ? z.anzahl : 0), 0);
    const summeMm = gruppe.zeilen.reduce((summe, z) => summe + Math.max(0, z.anzahl) * Math.max(0, z.laengeMm ?? 0), 0);
    const stange = gruppe.verpackungseinheitM && gruppe.verpackungseinheitM > 0 ? gruppe.verpackungseinheitM : FALLBACK_STANGE_M;
    return { ...gruppe, summeStueck, summeMeter: rund(summeMm / 1000, 2), berechneteStaebe: optimiereStangen(gruppe.zeilen, stange).anzahlStangen,
        summeKg: summeBekannt(gruppe.zeilen.map(z => z.gesamtGewichtKg), 3),
        summeMantelflaecheM2: summeBekannt(gruppe.zeilen.map(z => z.mantelflaecheM2), 4) };
}

function summeBekannt(werte: Array<number | undefined>, stellen: number): number | undefined {
    const bekannt = werte.filter((wert): wert is number => wert != null && wert > 0);
    return bekannt.length ? rund(bekannt.reduce((summe, wert) => summe + wert, 0), stellen) : undefined;
}

/** Pos.-Nummern einer Gruppe in Listenreihenfolge, ohne Doppelte (z. B. „1100, 1102, 1104“). */
export function positionsnummern(gruppe: ProfilGruppe): string | null {
    const nummern = [...new Set(gruppe.zeilen.map(z => String(z.posNr ?? '').trim()).filter(Boolean))];
    return nummern.length ? nummern.join(', ') : null;
}

/**
 * Gewicht je Meter einer Gruppe, gewichtet über alle Zuschnitte mit bekanntem Gewicht.
 * Das Gewicht aus der Profilsummenliste hat Vorrang vor dem Stückgewicht der Sägeliste.
 */
export function gruppenKgJeMeter(gruppe: ProfilGruppe): { kgJeMeter: number; quelle: string } | null {
    let gewicht = 0; let meter = 0; let ausProfilsumme = false;
    for (const zeile of gruppe.zeilen) {
        const laenge = zeile.anzahl * (zeile.laengeMm ?? 0) / 1000;
        const basis = zeile.snapshot?.basis;
        const kg = basis?.gesamtgewichtKg ?? (basis?.kgJeMeter != null ? basis.kgJeMeter * laenge : null);
        if (kg == null || !(kg > 0) || !(laenge > 0)) continue;
        gewicht += kg; meter += laenge;
        if (basis?.gesamtgewichtKg != null) ausProfilsumme = true;
    }
    return meter > 0 ? { kgJeMeter: rund(gewicht / meter, 6), quelle: ausProfilsumme ? 'HiCAD-Profilsummenliste' : 'HiCAD-Stückgewicht' } : null;
}

/**
 * Wird die Gruppe ohne Artikel nach Gewicht angelegt? Fixzuschnitt: das Backend rechnet jede Zeile mit
 * Gewicht aus der Profilsummenliste in kg um. Stangenware: kg über die Stangen, sobald ein Gewicht bekannt ist.
 */
export function anlageInKg(gruppe: ProfilGruppe, e: Pick<GruppenEntscheidung, 'artikelId' | 'aggregieren'>): 'alle' | 'teilweise' | 'keine' {
    if (e.artikelId) return 'keine';
    if (e.aggregieren) return gruppenKgJeMeter(gruppe) ? 'alle' : 'keine';
    const mitGewicht = gruppe.zeilen.filter(z => (z.snapshot?.basis?.gesamtgewichtKg ?? 0) > 0).length;
    return mitGewicht === 0 ? 'keine' : mitGewicht === gruppe.zeilen.length ? 'alle' : 'teilweise';
}

/** Katalogartikel ohne Leerzeichen/Groß-Klein-Unterschied; ein abweichender Werkstoff ist kein Treffer. */
export function passenderArtikel(gruppe: ProfilGruppe, treffer: ArtikelTreffer[]): ArtikelTreffer | undefined {
    return treffer.find(artikel => vergleichswert(artikel.produktname) === vergleichswert(gruppe.bezeichnung)
        && (!artikel.werkstoffName || !gruppe.werkstoff || vergleichswert(artikel.werkstoffName) === vergleichswert(gruppe.werkstoff)));
}

async function ergaenzeStammartikel(gruppen: ProfilGruppe[]): Promise<ProfilGruppe[]> {
    // Best effort: ein fehlgeschlagener Katalogabgleich lässt die Gruppe als Freitext stehen.
    const ergebnisse = await Promise.allSettled(gruppen.map(async gruppe => {
        if (gruppe.artikelId) return gruppe;
        const params = new URLSearchParams({ q: gruppe.bezeichnung, page: '0', size: '20' });
        const antwort = await einkaufApi.get<{ artikel?: ArtikelTreffer[] }>(`/api/artikel?${params}`);
        const artikel = passenderArtikel(gruppe, Array.isArray(antwort?.artikel) ? antwort.artikel : []);
        if (!artikel) return gruppe;
        const ve = Number(artikel.verpackungseinheit);
        return mitSummen({ ...gruppe, artikelId: artikel.id, artikelProduktname: artikel.produktname, artikelnummer: artikel.artikelnummer ?? null,
            verpackungseinheitM: Number.isFinite(ve) && ve > 0 ? ve : null });
    }));
    return ergebnisse.map((ergebnis, index) => ergebnis.status === 'fulfilled' ? ergebnis.value : gruppen[index]);
}

// ==================== API ====================

export async function ladeHicadVorschau(file: File, projektId: number): Promise<PreviewResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (!nutztEchtesBackend) {
        const res = await fetch('/api/bestellungen/import/hicad/preview', { method: 'POST', body: formData });
        if (!res.ok) throw new Error(res.headers.get('X-Error-Reason') || 'Upload fehlgeschlagen');
        return await res.json() as PreviewResponse;
    }
    const antwort = await fetch(`/api/einkauf/hicad/vorschau?projektId=${encodeURIComponent(projektId)}`, { method: 'POST', body: formData });
    if (!antwort.ok) throw new Error(await fehlermeldungAus(antwort, 'Die HiCAD-Sägeliste konnte nicht gelesen werden'));
    const vorschau = await antwort.json() as HiCadVorschauMitKopf;
    const fortschritt = await einkaufApi.get<HiCadImportFortschritt>(`/api/einkauf/hicad/${vorschau.id}`);
    const unlesbareZeilen: Array<{ zeilennummer: number; grund: string }> = [];
    const zeilen: SaegelisteZeile[] = [];
    for (const zeile of vorschau.zeilen) {
        const offen = fortschritt.zeilen.find(z => z.zeilennummer === zeile.zeilennummer);
        if (zeile.bereitsUebernommen || offen?.vollstaendigUebernommen) continue;
        const uebersetzt = saegelisteZeile(zeile);
        if (uebersetzt) zeilen.push(uebersetzt);
        else unlesbareZeilen.push({ zeilennummer: zeile.zeilennummer, grund: zeile.hinweise[0] ?? 'Menge fehlt.' });
    }
    if (zeilen.length === 0) {
        throw new Error(unlesbareZeilen.length
            ? `Keine Zeile der Sägeliste konnte gelesen werden (Zeile ${unlesbareZeilen[0].zeilennummer}: ${unlesbareZeilen[0].grund})`
            : 'Die Sägeliste enthält keine Positionen.');
    }
    const kopf = vorschau.kopf;
    return {
        zeichnungsnr: kopf?.zeichnungsnummer ?? undefined, auftragsnummer: kopf?.auftragsnummer ?? undefined,
        auftragstext: kopf?.auftragstext ?? undefined, kunde: kopf?.kunde ?? undefined,
        gruppen: await ergaenzeStammartikel(gruppiereZeilen(zeilen)),
        importId: vorschau.id, importVersion: fortschritt.version, dateiSchonImportiert: vorschau.dateiSchonImportiert,
        unlesbareZeilen,
    };
}

/** Neuberechnung der Stangenzahl bei geänderter Stangenlänge. */
export async function optimiereZuschnitt(stangenlaengeM: number, zeilen: SaegelisteZeile[]): Promise<ZuschnittPlan> {
    if (nutztEchtesBackend) return optimiereStangen(zeilen, stangenlaengeM);
    const res = await fetch('/api/bestellungen/import/hicad/optimiere', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ stangenlaengeM, zeilen }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json() as ZuschnittPlan;
}

const kommentar = (prefix: string, text: string) => `${prefix} · ${text}`;
const beschaffung = (e: GruppenEntscheidung) => ({ lieferantId: e.lieferantId, kategorieId: null, schnittbildId: null, schnittAchseId: null, externeArtikelnummer: null });
const format = (wert: number) => wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Fixzuschnitt: jede Sägelisten-Zeile wird über die Importübernahme zu einem Bedarf (inkl. Schnittbilder). */
export function fixzuschnittAuswahl(gruppe: ProfilGruppe, e: GruppenEntscheidung, prefix: string): HiCadZeilenAuswahl[] {
    return gruppe.zeilen.map(zeile => {
        const p = zeile.snapshot;
        if (!p || zeile.zeilennummer == null || p.basis?.menge == null) throw new Error(`Die Zeile „${gruppe.bezeichnung}“ ist nicht mehr lesbar. Bitte die Datei neu einlesen.`);
        const benennung = zeile.benennung && vergleichswert(zeile.benennung) !== vergleichswert(gruppe.bezeichnung) ? ` · ${zeile.benennung}` : '';
        // Menge bleibt in Stück; ohne Artikel rechnet das Backend mit dem Gewicht der Importzeile in kg um.
        const korrigiert: PositionSnapshot = {
            ...p, art: e.artikelId ? 'ARTIKEL' : 'FREITEXT', artikelId: e.artikelId ?? null,
            positionsnummer: p.positionsnummer ?? (zeile.posNr != null ? String(zeile.posNr) : null),
            bezeichnung: gruppe.bezeichnung, zeichnungsrevision: null, anlageVersionIds: [],
            bearbeitung: kommentar(prefix, `Pos ${zeile.posNr ?? '-'}${benennung}`),
            beschaffungsdetails: beschaffung(e),
        };
        return { zeilennummer: zeile.zeilennummer, menge: p.basis.menge, korrigiert, bestaetigteBildDateiIds: zeile.bildDateiIds ?? [] };
    });
}

/**
 * Stangenware: ein Bedarf über die optimierte Stangenzahl (selbst schneiden). Mit Artikel in Metern, ohne Artikel
 * in kg: Gekauft werden ganze Stangen, also Stangen × Stangenlänge × kg/m (inkl. Verschnitt) – nicht nur die
 * Summe der Teilegewichte. Die Mantelfläche ist die Summe der Teile (das, was beschichtet wird).
 */
export function stangenwareBedarf(gruppe: ProfilGruppe, e: GruppenEntscheidung, projektId: number, prefix: string, zeichnungsnr?: string): BedarfCreate {
    const stangeM = e.stangenlaengeM ?? gruppe.verpackungseinheitM ?? FALLBACK_STANGE_M;
    if (!Number.isInteger(stangeM) || stangeM < 1 || stangeM > 50) throw new Error(`Bitte für „${gruppe.bezeichnung}“ eine Stangenlänge zwischen 1 und 50 ganzen Metern eingeben.`);
    const plan = optimiereStangen(gruppe.zeilen, stangeM);
    if (plan.anzahlStangen < 1) throw new Error(`Für „${gruppe.bezeichnung}“ fehlen Zuschnittlängen – bitte als Fixzuschnitt anlegen.`);
    const gewicht = gruppenKgJeMeter(gruppe);
    const kgJeMeter = gewicht?.kgJeMeter ?? null;
    const meter = plan.anzahlStangen * stangeM;
    const stangenKg = kgJeMeter == null ? null : rund(meter * kgJeMeter, 3);
    const inKg = !e.artikelId && stangenKg != null && stangenKg > 0;
    let hinweis = `Stangenware · ${plan.anzahlStangen} Stk à ${stangeM} m (${format(gruppe.summeMeter ?? plan.belegtMm / 1000)} m benötigt, `
        + `${gruppe.summeStueck ?? 0} Zuschnitte, ${format(plan.verschnittMm / 1000)} m Verschnitt)`;
    if (plan.ueberlange > 0) hinweis += ` · ${plan.ueberlange} Zuschnitt(e) länger als die Stange`;
    return {
        position: {
            art: e.artikelId ? 'ARTIKEL' : 'FREITEXT', artikelId: e.artikelId ?? null, interneReferenz: null,
            zeichnungsnummer: zeichnungsnr ?? null, zeichnungsrevision: null, bezeichnung: gruppe.bezeichnung,
            werkstoff: gruppe.werkstoff ?? null, abmessung: gruppe.bezeichnung,
            basis: { menge: inKg ? stangenKg : meter, einheit: inKg ? 'KILOGRAMM' : 'METER', stueckzahl: plan.anzahlStangen, einzelLaengeMm: stangeM * 1000,
                kgJeMeter, faktorQuelle: gewicht?.quelle ?? null,
                gesamtgewichtKg: stangenKg, mantelflaecheM2: gruppe.summeMantelflaecheM2 ?? summeBekannt(gruppe.zeilen.map(z => z.mantelflaecheM2), 4) ?? null },
            positionsnummer: positionsnummern(gruppe),
            schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: kommentar(prefix, hinweis), oberflaeche: null,
            dokumente: [], anlageVersionIds: [], beschaffungsdetails: beschaffung(e),
        },
        liefergruppe: { lieferadresse: null, bedarfstermin: null, projektId, lagerzweck: null },
        artikelInProjektId: null,
    };
}

export interface UebernahmeAuftrag {
    preview: PreviewResponse;
    entscheidungen: Record<string, GruppenEntscheidung>;
    projektId: number;
    /** Je Vorschau einmal erzeugt: eine wiederholte Übernahme legt dieselben Zeilen nicht doppelt an. */
    idempotenzKey: string;
    /** Gruppen, die ein vorheriger, teilweise fehlgeschlagener Versuch schon angelegt hat. */
    erledigteGruppen?: string[];
    duplikatBewusst?: boolean;
}

export async function uebernehmeHicad(auftrag: UebernahmeAuftrag): Promise<UebernahmeErgebnis> {
    const { preview, entscheidungen, projektId } = auftrag;
    const prefix = preview.auftragsnummer ? `HiCAD ${preview.auftragsnummer}` : 'HiCAD-Import';
    if (!nutztEchtesBackend) {
        const body = {
            projektId, kommentarPrefix: prefix,
            gruppen: preview.gruppen.map(g => {
                const e = entscheidungen[g.groupKey];
                return { groupKey: g.groupKey, projektId, lieferantId: e?.lieferantId ?? null, artikelId: e?.artikelId ?? null, kategorieId: null,
                    aggregieren: e?.aggregieren ?? g.defaultAggregieren, stangenlaengeM: e?.stangenlaengeM ?? g.verpackungseinheitM ?? null };
            }),
            preview: preview.gruppen,
        };
        const res = await fetch('/api/bestellungen/import/hicad/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!res.ok) throw new Error(res.headers.get('X-Error-Reason') || 'Import fehlgeschlagen');
        const result = await res.json() as { angelegtePositionen: number };
        return { angelegtePositionen: result.angelegtePositionen, erledigteGruppen: preview.gruppen.map(g => g.groupKey) };
    }
    if (preview.importId == null || preview.importVersion == null) throw new Error('Die Vorschau ist unvollständig. Bitte die Datei neu einlesen.');
    const erledigt = new Set(auftrag.erledigteGruppen ?? []);
    const offen = preview.gruppen.filter(g => !erledigt.has(g.groupKey));
    const entscheidung = (g: ProfilGruppe) => entscheidungen[g.groupKey]
        ?? { aggregieren: g.defaultAggregieren, artikelId: g.artikelId ?? null, artikelProduktname: null, lieferantId: null, lieferantName: null, stangenlaengeM: null };
    // Alles vor dem ersten Schreibzugriff prüfen und aufbauen.
    const fix = offen.filter(g => !entscheidung(g).aggregieren);
    const zeilen = fix.flatMap(g => fixzuschnittAuswahl(g, entscheidung(g), prefix));
    const stangen = offen.filter(g => entscheidung(g).aggregieren)
        .map(g => ({ gruppe: g, bedarf: stangenwareBedarf(g, entscheidung(g), projektId, prefix, preview.zeichnungsnr) }));

    let angelegt = 0;
    const fertig = [...erledigt];
    if (zeilen.length) {
        const ergebnis = await einkaufApi.post<BedarfResponse[]>(`/api/einkauf/hicad/${preview.importId}/uebernehmen`, {
            version: preview.importVersion, zeilen, duplikatBewusst: !!auftrag.duplikatBewusst, idempotenzKey: auftrag.idempotenzKey,
        });
        angelegt += Array.isArray(ergebnis) ? ergebnis.length : zeilen.length;
        fertig.push(...fix.map(g => g.groupKey));
    }
    for (const { gruppe, bedarf } of stangen) {
        try {
            await einkaufApi.post<BedarfResponse>('/api/einkauf/bedarf', bedarf);
        } catch (fehler) {
            const grund = fehler instanceof Error ? fehler.message : 'Unbekannter Fehler.';
            throw new HicadUebernahmeFehler(`„${gruppe.bezeichnung}“ konnte nicht angelegt werden: ${grund}`
                + (angelegt ? ` ${angelegt} Position${angelegt === 1 ? ' wurde' : 'en wurden'} bereits angelegt.` : ''), fertig, angelegt);
        }
        angelegt += 1;
        fertig.push(gruppe.groupKey);
    }
    return { angelegtePositionen: angelegt, erledigteGruppen: fertig };
}
