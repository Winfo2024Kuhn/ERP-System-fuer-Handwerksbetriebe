import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import { passendeBeschaffungsdetails } from './positionDrafts';
import type { BedarfResponse, BedarfCreate, BedarfUpdate, Dokumentart, Einheit, PositionSnapshot } from './types';

export interface MaterialPosition {
    clientId: string;
    artikelId: number | null;
    produktname: string;
    produkttext: string;
    werkstoffName?: string;
    kgJeMeter: number | null;
    faktorQuelle: string | null;
    externeArtikelnummer?: string;
    kategorieId: number | null;
    menge: string;
    einheit: Einheit;
    fixzuschnitt: boolean;
    sonderzuschnitt: boolean;
    fixmassMm: string;
    schnittForm: string;
    schnittbildBildUrl?: string | null;
    winkelLinks: string;
    winkelRechts: string;
    zeugnis: string;
    zeugnisBestaetigt: boolean;
    kommentar: string;
}
export const MATERIAL_EINHEITEN: Array<{ value: Einheit; label: string }> = [
    { value: 'STUECK', label: 'Stück' }, { value: 'METER', label: 'm (Meter)' },
    { value: 'KILOGRAMM', label: 'kg' }, { value: 'TONNE', label: 't' }, { value: 'QUADRATMETER', label: 'm²' },
];
export const MATERIAL_ZEUGNISSE: Array<{ value: string; label: string }> = [
    { value: '', label: '— Kein Zeugnis —' }, { value: 'ZEUGNIS_2_1', label: 'Werkszeugnis 2.1' },
    { value: 'ZEUGNIS_2_2', label: 'Werkszeugnis 2.2' }, { value: 'ZEUGNIS_3_1', label: 'Abnahmeprüfzeugnis 3.1' },
    { value: 'ZEUGNIS_3_2', label: 'Abnahmeprüfzeugnis 3.2' }, { value: 'CE_NACHWEIS', label: 'CE-Kennzeichnung' },
    { value: 'LEISTUNGSERKLAERUNG', label: 'Leistungserklärung' },
];
export const neueMaterialPosition = (): MaterialPosition => ({
    clientId: crypto.randomUUID(), artikelId: null, produktname: '', produkttext: '', kategorieId: null, kgJeMeter: null, faktorQuelle: null,
    menge: '1', einheit: 'STUECK', fixzuschnitt: false, sonderzuschnitt: false, fixmassMm: '',
    schnittForm: '', winkelLinks: '', winkelRechts: '', zeugnis: '', zeugnisBestaetigt: false, kommentar: '',
});
const winkelEntwurf = (value: string | null | undefined) => value?.trim().replace(/°$/, '').trim().replace('.', ',') ?? '';

export function materialPositionAusBedarf(bedarf: BedarfResponse): MaterialPosition {
    const p = bedarf.position;
    const zahl = (value: number | null | undefined) => value == null ? '' : formatDecimalInput(value);
    return { ...neueMaterialPosition(), artikelId: p.artikelId, produktname: p.bezeichnung ?? '', produkttext: p.abmessung ?? '',
        werkstoffName: p.werkstoff ?? undefined, kgJeMeter: p.basis?.kgJeMeter ?? null, faktorQuelle: p.basis?.faktorQuelle ?? null, menge: zahl(p.basis?.menge), einheit: p.basis?.einheit ?? 'STUECK',
        fixzuschnitt: p.basis?.einzelLaengeMm != null, fixmassMm: zahl(p.basis?.einzelLaengeMm),
        sonderzuschnitt: !!p.schnittForm, schnittForm: p.schnittForm ?? '', winkelLinks: winkelEntwurf(p.winkelLinks),
        winkelRechts: winkelEntwurf(p.winkelRechts), zeugnis: p.dokumente?.[0]?.art ?? '', zeugnisBestaetigt: p.dokumente?.[0]?.fachlichBestaetigt ?? false, kommentar: p.bearbeitung ?? '' };
}

/** Adapts the original EN1090 entry fields without dropping other stored technical details. */
export function materialbedarfPayload(draft: MaterialPosition, projektId: number | null, basis?: BedarfResponse): BedarfCreate | BedarfUpdate {
    if (!draft.produktname.trim()) throw new Error('Bitte einen Produktnamen eingeben.');
    if (!MATERIAL_EINHEITEN.some(e => e.value === draft.einheit)) throw new Error('Bitte eine gültige Einheit auswählen.');
    const numbers = validateNumberDrafts({ menge: draft.menge, fixmass: draft.fixzuschnitt ? draft.fixmassMm : '', links: draft.sonderzuschnitt ? draft.winkelLinks : '', rechts: draft.sonderzuschnitt ? draft.winkelRechts : '' }, {
        menge: { label: 'Menge', required: true, min: 0.000001, maxDecimalPlaces: 6, integer: draft.einheit === 'STUECK' },
        fixmass: { label: 'Fixmaß', required: draft.fixzuschnitt, min: 0.000001, maxDecimalPlaces: 6 },
        links: { label: 'Winkel links', min: -360, max: 360, maxDecimalPlaces: 2 },
        rechts: { label: 'Winkel rechts', min: -360, max: 360, maxDecimalPlaces: 2 },
    });
    if (!numbers.valid) throw new Error(numbers.message);
    if (draft.sonderzuschnitt && !draft.schnittForm) throw new Error('Bitte ein Schnittbild auswählen.');
    const previous = basis?.position;
    const unveraenderteWinkel = !!previous && draft.sonderzuschnitt === !!previous.schnittForm
        && draft.schnittForm === (previous.schnittForm ?? '')
        && draft.winkelLinks === winkelEntwurf(previous.winkelLinks) && draft.winkelRechts === winkelEntwurf(previous.winkelRechts);
    if (draft.zeugnis && !draft.zeugnisBestaetigt) throw new Error('Bitte die Zeugnisanforderung fachlich prüfen und bestätigen.');
    const unveraendertesZeugnis = draft.zeugnis === (previous?.dokumente?.[0]?.art ?? '')
        && draft.zeugnisBestaetigt === (previous?.dokumente?.[0]?.fachlichBestaetigt ?? false);
    let stueckzahl = draft.einheit === 'STUECK' ? numbers.values.menge : previous?.basis?.stueckzahl ?? null;
    const geaenderterZuschnitt = numbers.values.menge !== previous?.basis?.menge
        || numbers.values.fixmass !== previous?.basis?.einzelLaengeMm || draft.einheit !== previous?.basis?.einheit;
    if (draft.einheit === 'METER' && stueckzahl != null && numbers.values.fixmass != null && geaenderterZuschnitt) {
        // Exact decimal arithmetic: metres and millimetres each allow six decimal places.
        const millionstel = (text: string) => {
            const [ganz, dezimal = ''] = text.trim().split(',');
            return BigInt(ganz) * 1_000_000n + BigInt(dezimal.replace(/0+$/, '').padEnd(6, '0'));
        };
        const gesamtlaenge = millionstel(draft.menge) * 1000n;
        const einzellaenge = millionstel(draft.fixmassMm);
        if (gesamtlaenge % einzellaenge !== 0n || gesamtlaenge / einzellaenge > BigInt(Number.MAX_SAFE_INTEGER)) {
            throw new Error('Die Menge muss zum Fixmaß passen und eine ganze Stückzahl ergeben. Bitte Menge oder Fixmaß anpassen.');
        }
        stueckzahl = Number(gesamtlaenge / einzellaenge);
    }
    const art = previous?.art === 'ZEICHNUNGSTEIL' ? 'ZEICHNUNGSTEIL' : draft.artikelId ? 'ARTIKEL' : 'FREITEXT';
    const schnittForm = draft.sonderzuschnitt ? draft.schnittForm : null;
    const position: PositionSnapshot = {
        art,
        artikelId: draft.artikelId, interneReferenz: previous?.interneReferenz ?? null,
        zeichnungsnummer: previous?.zeichnungsnummer ?? null, zeichnungsrevision: previous?.zeichnungsrevision ?? null,
        bezeichnung: draft.produktname.trim(), werkstoff: draft.werkstoffName?.trim() || null, abmessung: draft.produkttext.trim() || null,
        basis: { menge: numbers.values.menge, einheit: draft.einheit, stueckzahl,
            einzelLaengeMm: numbers.values.fixmass, kgJeMeter: draft.kgJeMeter, faktorQuelle: draft.faktorQuelle },
        schnittForm,
        winkelLinks: unveraenderteWinkel ? previous.winkelLinks : draft.sonderzuschnitt ? formatDecimalInput(numbers.values.links ?? 90) : null,
        winkelRechts: unveraenderteWinkel ? previous.winkelRechts : draft.sonderzuschnitt ? formatDecimalInput(numbers.values.rechts ?? 90) : null,
        bearbeitung: draft.kommentar.trim() || null, oberflaeche: previous?.oberflaeche ?? null,
        dokumente: unveraendertesZeugnis ? previous?.dokumente ?? [] : [
            ...(draft.zeugnis ? [{ art: draft.zeugnis as Dokumentart, grundlage: 'Manuelle Materialanforderung', grundlageVersion: '1', fachlichBestaetigt: draft.zeugnisBestaetigt }] : []),
            ...(previous?.dokumente.slice(1) ?? []),
        ],
        anlageVersionIds: previous?.anlageVersionIds ?? [],
        beschaffungsdetails: previous ? passendeBeschaffungsdetails(previous.beschaffungsdetails, previous,
            { art, artikelId: draft.artikelId, schnittForm }) : null,
    };
    const liefergruppe = { projektId, lagerzweck: projektId ? null : basis?.liefergruppe.lagerzweck || 'Werkstatt / auf Vorrat',
        lieferadresse: basis?.liefergruppe.lieferadresse ?? null, bedarfstermin: basis?.liefergruppe.bedarfstermin ?? null };
    return basis ? { version: basis.version, position, liefergruppe } : { position, liefergruppe, artikelInProjektId: null };
}

/** Map actual catalog units and technical fields consistently for single and multiple selection. */
export function materialPositionAusArtikel(artikel: {
    id: number; produktname: string; produkttext?: string | null; abmessung?: string | null;
    werkstoffName?: string | null; externeArtikelnummer?: string | null; artikelnummer?: string | null;
    kategorieId?: number | null; kgProMeter?: number | null;
    verrechnungseinheit?: string | { name: string; anzeigename?: string } | null;
}): Partial<MaterialPosition> {
    const katalogEinheit = typeof artikel.verrechnungseinheit === 'string' ? artikel.verrechnungseinheit : artikel.verrechnungseinheit?.name;
    const einheit: Einheit = katalogEinheit === 'LAUFENDE_METER' ? 'METER'
        : MATERIAL_EINHEITEN.find(option => option.value === katalogEinheit)?.value ?? 'STUECK';
    return { artikelId: artikel.id, produktname: artikel.produktname, produkttext: artikel.abmessung ?? '',
        werkstoffName: artikel.werkstoffName ?? undefined, externeArtikelnummer: artikel.externeArtikelnummer ?? artikel.artikelnummer ?? undefined,
        kategorieId: artikel.kategorieId ?? null, einheit, kgJeMeter: artikel.kgProMeter ?? null,
        faktorQuelle: artikel.kgProMeter == null ? null : 'Artikelstamm' };
}
