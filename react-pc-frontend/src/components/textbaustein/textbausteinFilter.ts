import { htmlToText } from '../../lib/htmlSanitizer';
import type { DokumenttypGruppe, TextbausteinDto } from './typen';

/** Filterwert „alle Dokumentarten“ (leer = kein Filter, auch in der URL). */
export const ALLE_DOKUMENTARTEN = '';
/** Filterwert für Textbausteine ohne Dokumentart – so steht er auch in der URL. */
export const OHNE_ZUORDNUNG = 'ohne-zuordnung';

export interface TextbausteinFilter {
    suche: string;
    dokumentart: string;
}

/** Ein Textbaustein mit vorbereitetem Suchtext, damit nicht bei jedem Tastendruck HTML geparst wird. */
export interface DurchsuchbarerTextbaustein {
    baustein: TextbausteinDto;
    suchtext: string;
}

export function durchsuchbarMachen(liste: readonly TextbausteinDto[]): DurchsuchbarerTextbaustein[] {
    return liste.map((baustein) => ({
        baustein,
        suchtext: `${baustein.name} ${htmlToText(baustein.html)}`.toLocaleLowerCase('de-DE'),
    }));
}

function passtZurDokumentart(baustein: TextbausteinDto, dokumentart: string): boolean {
    if (dokumentart === ALLE_DOKUMENTARTEN) return true;
    if (dokumentart === OHNE_ZUORDNUNG) return baustein.dokumenttypen.length === 0;
    return baustein.dokumenttypen.includes(dokumentart);
}

/** Suche (in Name und Text, Wörter in beliebiger Reihenfolge) plus Dokumentart. */
export function filtereTextbausteine(
    liste: readonly DurchsuchbarerTextbaustein[],
    filter: TextbausteinFilter,
): TextbausteinDto[] {
    const woerter = filter.suche.toLocaleLowerCase('de-DE').split(/\s+/).filter(Boolean);
    return liste
        .filter(({ baustein, suchtext }) => passtZurDokumentart(baustein, filter.dokumentart)
            && woerter.every((wort) => suchtext.includes(wort)))
        .map(({ baustein }) => baustein);
}

export interface DokumentartZaehler {
    alle: number;
    ohneZuordnung: number;
    proDokumentart: Map<string, number>;
}

/** Wie viele Textbausteine es je Dokumentart gibt (Zahlen im Filter-Dropdown). */
export function zaehleDokumentarten(liste: readonly TextbausteinDto[]): DokumentartZaehler {
    const proDokumentart = new Map<string, number>();
    let ohneZuordnung = 0;
    liste.forEach((baustein) => {
        if (baustein.dokumenttypen.length === 0) ohneZuordnung++;
        new Set(baustein.dokumenttypen).forEach((art) => proDokumentart.set(art, (proDokumentart.get(art) ?? 0) + 1));
    });
    return { alle: liste.length, ohneZuordnung, proDokumentart };
}

export interface FilterOption {
    value: string;
    label: string;
    gruppe?: string;
}

/** Optionen für das Dropdown „Verwendet in“, nach Gruppen und mit Anzahl. */
export function dokumentartOptionen(gruppen: readonly DokumenttypGruppe[], zaehler: DokumentartZaehler): FilterOption[] {
    return [
        { value: ALLE_DOKUMENTARTEN, label: `Alle Dokumentarten (${zaehler.alle})` },
        ...gruppen.flatMap(({ gruppe, dokumenttypen }) => dokumenttypen.map((art) => ({
            value: art,
            label: `${art} (${zaehler.proDokumentart.get(art) ?? 0})`,
            gruppe,
        }))),
        { value: OHNE_ZUORDNUNG, label: `Ohne Zuordnung (${zaehler.ohneZuordnung})`, gruppe: 'Weitere' },
    ];
}

/**
 * Filterwert aus der URL nur übernehmen, wenn das Programm ihn kennt; ein
 * veralteter oder vertippter Wert fällt auf „alle Dokumentarten“ zurück.
 */
export function gueltigeDokumentart(wert: string, gruppen: readonly DokumenttypGruppe[]): string {
    if (wert === ALLE_DOKUMENTARTEN || wert === OHNE_ZUORDNUNG) return wert;
    return gruppen.some((g) => g.dokumenttypen.includes(wert)) ? wert : ALLE_DOKUMENTARTEN;
}

/** Lesbarer Name des Filterwerts, z. B. für „Keine Textbausteine für „Rechnung““. */
export function dokumentartBezeichnung(dokumentart: string): string {
    if (dokumentart === OHNE_ZUORDNUNG) return 'Ohne Zuordnung';
    return dokumentart;
}
