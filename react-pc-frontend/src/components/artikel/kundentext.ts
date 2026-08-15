import type { Artikel } from '../../types';

/**
 * Kundentext einer Materialposition: der Text, den der Kunde auf PDF, Rechnung
 * und Freigabe-Seite liest.
 *
 * <b>Warum es diese Datei gibt.</b> Der PDF-Druck hat einen alten Notnagel
 * (`RechnungPdfService:881-889`): Ist die Beschreibung leer, druckt er
 * ersatzweise den Block-Titel. Fuer Leistungen ist das richtig — lieber die
 * Innensicht auf dem Papier als eine namenlose Position mit Preis. Fuer Material
 * bricht es aber die zentrale Regel des Entwurfs: Die Kurzbeschreibung ist
 * Innensicht und darf niemals auf ein Kundendokument. Solange am Artikel kein
 * Kundentext gepflegt ist, wuerde genau das passieren.
 *
 * Deshalb baut das Auswahlfenster den Text notfalls selbst — nach denselben
 * Regeln wie `scripts/artikel_dokumenttexte_backfill.py` (`baue_beschreibung`),
 * damit ein spaeter nachgezogener Backfill dasselbe erzeugt und der Kunde in
 * beiden Faellen denselben Satz liest.
 *
 * Drei Grundsaetze, woertlich vom Backfill-Skript uebernommen:
 *   1. Nichts erfinden — fehlt eine Angabe, entfaellt sie ersatzlos.
 *   2. Keine halben Saetze — reicht es nicht fuer einen brauchbaren Text, kommt
 *      gar keiner. Ein leeres Feld, auf das der Editor sichtbar hinweist, ist
 *      ehrlicher als "Rundrohr , mm" auf einem verbindlichen Angebot.
 *   3. Handwerker-Sprache — der Kunde liest "Edelstahl 1.4301"; Normkuerzel wie
 *      "EN 10219-2" bleiben ganz weg.
 */

/**
 * Kundenfreundliche Namen fuer die Werkstoffcodes aus den Stammdaten.
 * Identisch mit `WERKSTOFF_KLARTEXT` im Backfill-Skript — beide Wege muessen
 * denselben Satz erzeugen. Unbekannte Werkstoffe behalten ihren technischen
 * Namen.
 */
const WERKSTOFF_KLARTEXT: Record<string, string> = {
    'S235JR': 'Baustahl S235JR',
    'S355J2': 'Baustahl S355J2',
    'Stahl': 'Baustahl',
    '1.4301': 'Edelstahl 1.4301',
    '1.4571': 'Edelstahl 1.4571',
    '1.4404': 'Edelstahl 1.4404',
    '1.4541': 'Edelstahl 1.4541',
    'Edelstahl': 'Edelstahl',
    'EN AW-6060': 'Aluminium EN AW-6060',
    'EN AW-5754': 'Aluminium EN AW-5754',
    'Aluminium': 'Aluminium',
    'DX51D+Z': 'verzinktem Stahl (DX51D+Z)',
    'Kunststoff': 'Kunststoff',
};

/**
 * Abweichungen vom Profilform-Anzeigenamen fuer das Kundendokument:
 * - Winkel: ob gleich- oder ungleichschenklig, sagt die Abmessung (40 x 40 bzw.
 *   60 x 40) dem Kunden bereits — das Fachwort waere nur Ballast.
 * - SONSTIGES ist ein interner Sammelwert und darf nie als "Sonstiges" auf einem
 *   Kundendokument stehen.
 */
const PROFILFORM_KUNDE: Record<string, string | null> = {
    'WINKEL_GLEICHSCHENKLIG': 'Winkel',
    'WINKEL_UNGLEICHSCHENKLIG': 'Winkel',
    'SONSTIGES': null,
};

/**
 * Fertigungsmerkmale, die dem Kunden etwas sagen und zwei sonst identisch
 * beschriebene Positionen unterscheiden (nahtloses vs. geschweisstes Rohr,
 * geschliffene vs. rohe Oberflaeche). Alle uebrigen Verfahren und Zustaende
 * ("gewalzt", "warmgefertigt", ...) sind der Normalfall und bleiben weg.
 */
const VERFAHREN_FUER_KUNDEN = new Set(['NAHTLOS']);
const ZUSTAND_FUER_KUNDEN = new Set(['KALTGEZOGEN', 'GESCHLIFFEN']);

/**
 * Vergleichsform einer Abmessung: "42,4 x 2" und "42.4x2.0" sollen sich finden.
 * Kleinbuchstaben, Komma zu Punkt, ohne Leerzeichen, "mm" und
 * Durchmesserzeichen entfernt.
 */
const normalisiert = (text: string): string =>
    text.toLowerCase()
        .replace(/,/g, '.')
        .replace(/ø/g, '')
        .replace(/\s+/g, '')
        .replace(/mm/g, '');

/**
 * Enthaelt der Text ein echtes Wort (mindestens drei Buchstaben am Stueck)?
 * "42.4x2" nicht, "Tuerschliesser TS 3000" schon.
 */
const hatKlartextWort = (text: string): boolean => /[A-Za-zÄÖÜäöüß]{3,}/.test(text);

/**
 * Kann der Produktname allein als Kundentext dienen? Nur wenn er mit einem
 * echten Wort beginnt ("Tuerschliesser TS 3000", "Edelstahlrohr 42,4x2"). Reine
 * Masszeichenfolgen ("42.4x2 nahtlos") oder kryptische Lieferantenkuerzel
 * ("RO 42,4X2,0") fallen durch — genau die duerfen nie auf ein Angebot.
 */
const traegtSichSelbst = (produktname: string): boolean => {
    const ersterTeil = produktname.trim().split(/\s+/)[0] ?? '';
    return hatKlartextWort(ersterTeil);
};

const profilformFuerKunden = (artikel: Artikel): string | null => {
    const name = artikel.profilform?.name;
    if (!name) return null;
    if (name in PROFILFORM_KUNDE) return PROFILFORM_KUNDE[name];
    return artikel.profilform?.anzeigename || null;
};

/**
 * Abmessung mit Einheit. Die Backend-Seite haengt beim Blech schon "mm" an
 * ("2 mm") — dann kein zweites Mal anhaengen, sonst stuende "2 mm mm" auf dem
 * Angebot.
 */
const abmessungMitEinheit = (artikel: Artikel): string | null => {
    const abmessung = (artikel.abmessung ?? '').trim();
    if (!abmessung) return null;
    return abmessung.endsWith('mm') ? abmessung : `${abmessung} mm`;
};

const werkstoffFuerKunden = (artikel: Artikel): string | null => {
    const name = (artikel.werkstoffName ?? '').trim();
    if (!name) return null;
    return WERKSTOFF_KLARTEXT[name] ?? name;
};

/**
 * Steht das Werkstoffwort schon im Produktnamen? Sonst wird aus
 * "Allzweckduebel Kunststoff" der Satz "Allzweckduebel Kunststoff aus
 * Kunststoff" — beim echten Bestand real aufgetreten.
 */
const werkstoffStecktSchonImNamen = (artikel: Artikel, text: string): boolean => {
    const roh = (artikel.werkstoffName ?? '').trim();
    if (!roh || !text) return false;
    return text.toLowerCase().includes(roh.toLowerCase());
};

const fertigungsmerkmale = (artikel: Artikel): string[] => {
    const merkmale: (string | undefined)[] = [];
    if (artikel.herstellverfahren && VERFAHREN_FUER_KUNDEN.has(artikel.herstellverfahren.name)) {
        merkmale.push(artikel.herstellverfahren.anzeigename || artikel.herstellverfahren.name);
    }
    if (artikel.fertigungszustand && ZUSTAND_FUER_KUNDEN.has(artikel.fertigungszustand.name)) {
        merkmale.push(artikel.fertigungszustand.anzeigename || artikel.fertigungszustand.name);
    }
    return merkmale.filter((m): m is string => !!m);
};

const escape = (text: string): string =>
    text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/**
 * Traegt dieses Rich-Text-HTML ueberhaupt Text? `<p></p>` und `<p>&nbsp;</p>`
 * sind fuer den Leser leer, auch wenn der String es nicht ist — und genau solche
 * Reste hinterlaesst ein Rich-Text-Editor, wenn jemand ein Feld leert.
 */
export function hatKundentext(html?: string | null): boolean {
    if (!html) return false;
    return html.replace(/<[^>]*>/g, '')
        .replace(/&nbsp;/gi, ' ')
        .trim().length > 0;
}

/**
 * Baut aus den Stammdaten einen Kundentext als schlichtes HTML — ein Satz ohne
 * Normkuerzel.
 *
 * Regelfall (Halbzeug mit Profilform und Abmessung):
 *   "Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301, nahtlos"
 * Rueckfallebene (z.B. Beschlaege): Der Produktname traegt den Text,
 *   "Tuerschliesser TS 3000 silber aus Baustahl".
 *
 * @returns das fertige `<p>...</p>` oder `''`, wenn die Angaben fuer keinen
 *          brauchbaren Satz reichen. Der leere String ist eine Aussage, keine
 *          Panne: Die Position geht dann bewusst ohne Text raus und der Editor
 *          weist sichtbar darauf hin.
 */
export function baueKundentext(artikel: Artikel): string {
    const form = profilformFuerKunden(artikel);
    const abmessung = abmessungMitEinheit(artikel);
    const werkstoff = werkstoffFuerKunden(artikel);

    if (form && abmessung) {
        let satz = `${form} ${abmessung}`;
        if (werkstoff) satz += ` aus ${werkstoff}`;
        const merkmale = fertigungsmerkmale(artikel);
        if (merkmale.length > 0) satz += `, ${merkmale.join(', ')}`;
        return `<p>${escape(satz)}</p>`;
    }

    const produktname = (artikel.produktname ?? '').trim();
    if (!produktname || !traegtSichSelbst(produktname)) return '';

    let satz = produktname;
    if (werkstoff && !werkstoffStecktSchonImNamen(artikel, produktname)) {
        satz += ` aus ${werkstoff}`;
    }
    if (abmessung && !normalisiert(produktname).includes(normalisiert(abmessung))) {
        satz += `, ${abmessung}`;
    }
    return `<p>${escape(satz)}</p>`;
}

/**
 * Der Text, mit dem eine Materialposition in den Editor einzieht.
 *
 * Erste Wahl ist immer der von Hand gepflegte Kundentext am Artikel — ein
 * geschriebener Satz schlaegt jeden generierten. Fehlt er, springt
 * {@link baueKundentext} ein. Bleibt auch das leer, geht die Position bewusst
 * ohne Text raus; das ist der einzige Fall, in dem der Bediener selbst schreiben
 * muss, und der Editor sagt es ihm an der Position.
 */
export function kundentextFuerPosition(artikel: Artikel): string {
    if (hatKundentext(artikel.beschreibung)) return artikel.beschreibung!.trim();
    return baueKundentext(artikel);
}
