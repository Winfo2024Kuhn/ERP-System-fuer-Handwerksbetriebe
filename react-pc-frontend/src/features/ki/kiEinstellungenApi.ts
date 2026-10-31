import { parseErrorMessage } from '../../components/settings/settingsApi';
import type { TestResult } from '../../components/settings/settingsApi';

/**
 * Typen, Server-Aufrufe und Anzeige-Texte für die Wahl des KI-Anbieters
 * (Einstellungen → KI-Funktionen).
 *
 * <p>Der Server liefert nach jeder Änderung den kompletten neuen Stand
 * ({@link KiEinstellungen}) zurück. Die Oberfläche zeichnet sich daraus neu,
 * statt selbst auszurechnen, welche Funktion über welchen Anbieter läuft –
 * so kann die Anzeige nie etwas anderes behaupten als das, was der Server
 * tatsächlich tut.</p>
 *
 * <p>Anbieter, Fähigkeiten und Stufen sind bewusst offen typisiert
 * ({@code | (string & {})}): Kommen später weitere Anbieter dazu, zeigt die
 * Oberfläche sie aus {@code anbieterListe} an, ohne dass hier etwas
 * nachgezogen werden muss. Hart verdrahtet sind nur die Anzeige-Texte.</p>
 */

/** Bekannte Anbieter; weitere kommen als freier Text vom Server. */
export type KiAnbieterTyp = 'GEMINI' | 'MISTRAL' | (string & {});

/** Was ein Anbieter in diesem Programm kann bzw. was eine Funktion braucht. */
export type KiFaehigkeit = 'TEXT' | 'BILD' | 'PDF' | 'AUDIO' | 'WERKZEUGE' | 'WEBSUCHE' | (string & {});

/** Modell-Stufen: kurze Aufgaben, Normalfall, zweiter Versuch bei schweren Dokumenten. */
export type KiModellStufe = 'SCHNELL' | 'STANDARD' | 'GRUENDLICH' | (string & {});

export type KiDatenstandort = 'EU' | 'WELTWEIT' | (string & {});

/** AKTIV = läuft über den gewählten Anbieter, ERSATZ = ein anderer springt ein, AUS = kein passender Anbieter. */
export type KiFunktionStatus = 'AKTIV' | 'ERSATZ' | 'AUS' | (string & {});

export interface KiModell {
    stufe: KiModellStufe;
    /** Das gerade wirksame Modell. */
    modell: string;
    /** Der Programm-Standard (Platzhalter, „Standard wiederherstellen“). */
    standardModell: string;
}

export interface KiAnbieter {
    typ: KiAnbieterTyp;
    name: string;
    beschreibung: string;
    datenstandort: KiDatenstandort;
    schluesselGesetzt: boolean;
    /** Wird der Schlüssel verschlüsselt gespeichert? */
    schluesselVerschluesselt: boolean;
    faehigkeiten: KiFaehigkeit[];
    modelle: KiModell[];
}

/** Eigene Wahl für eine Funktion; `modell: null` = Standard-Modell dieses Anbieters. */
export interface KiEigeneWahl {
    anbieter: KiAnbieterTyp;
    modell: string | null;
}

export interface KiFunktion {
    funktion: string;
    name: string;
    braucht: KiFaehigkeit[];
    status: KiFunktionStatus;
    anbieter: KiAnbieterTyp | null;
    anbieterName: string | null;
    modell: string | null;
    /** Fertiger Satz zum Anzeigen; bei AKTIV meist `null`, sonst z. B. „Läuft ohne Internet-Suche …“. */
    hinweis: string | null;
    /** `null` = folgt dem oben gewählten Standard-Anbieter. */
    eigeneWahl: KiEigeneWahl | null;
}

/** Ein Modell, wie es der Anbieter gerade anbietet (`GET /api/settings/ki/modelle`). */
export interface KiModellEintrag {
    anbieter: KiAnbieterTyp;
    anbieterName: string;
    /** Technischer Name, der gespeichert wird. */
    modell: string;
    /** Anzeigename beim Anbieter. */
    name: string;
    /** Kann das Modell Bilder lesen? `null` = unbekannt. */
    bilder: boolean | null;
}

export interface KiModellListe {
    modelle: KiModellEintrag[];
    /** Fertige Sätze, z. B. warum ein Anbieter fehlt. */
    hinweise: string[];
}

export interface KiEinstellungen {
    anbieter: KiAnbieterTyp;
    ersatzErlaubt: boolean;
    /** false: Schlüssel, die verschlüsselt gespeichert werden, lassen sich nicht speichern. */
    verschluesselungBereit: boolean;
    anbieterListe: KiAnbieter[];
    funktionen: KiFunktion[];
}

/** Stufe → Modellname. Leerer Text = auf das Standard-Modell zurücksetzen. */
export type KiModellAuswahl = Record<string, string>;

const BASIS = '/api/settings/ki';

/** Anbieter-Pfad mit sicher kodiertem Typ. */
function anbieterPfad(typ: KiAnbieterTyp, rest: string): string {
    return `${BASIS}/anbieter/${encodeURIComponent(typ)}/${rest}`;
}

/**
 * Schickt eine Anfrage und gibt den neuen Stand zurück. Bei einem Fehler
 * wirft sie die Meldung des Servers (deutscher Satz) oder den Ersatztext.
 */
async function stand(url: string, init: RequestInit | undefined, fehlertext: string): Promise<KiEinstellungen> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new Error('Verbindung zum Server fehlgeschlagen.');
    }
    if (!res.ok) throw new Error(await parseErrorMessage(res, fehlertext));
    const daten: unknown = await res.json().catch(() => null);
    return normalisiereKiEinstellungen(daten);
}

const liste = <T>(wert: unknown): T[] => (Array.isArray(wert) ? (wert as T[]) : []);

/**
 * Macht die Antwort robust: fehlende Listen werden leer, statt die Seite
 * abstürzen zu lassen. Inhalte werden nicht umgedeutet – was der Server
 * schickt, wird angezeigt.
 */
export function normalisiereKiEinstellungen(daten: unknown): KiEinstellungen {
    const d = (daten && typeof daten === 'object' ? daten : {}) as Partial<KiEinstellungen>;
    return {
        anbieter: typeof d.anbieter === 'string' ? d.anbieter : '',
        ersatzErlaubt: d.ersatzErlaubt === true,
        verschluesselungBereit: d.verschluesselungBereit === true,
        anbieterListe: liste<KiAnbieter>(d.anbieterListe).map((a) => ({
            ...a,
            faehigkeiten: liste<KiFaehigkeit>(a.faehigkeiten),
            modelle: liste<KiModell>(a.modelle),
        })),
        funktionen: liste<KiFunktion>(d.funktionen).map((f) => ({
            ...f,
            braucht: liste<KiFaehigkeit>(f.braucht),
            eigeneWahl: f.eigeneWahl && typeof f.eigeneWahl === 'object' && typeof f.eigeneWahl.anbieter === 'string'
                ? { anbieter: f.eigeneWahl.anbieter, modell: typeof f.eigeneWahl.modell === 'string' && f.eigeneWahl.modell ? f.eigeneWahl.modell : null }
                : null,
        })),
    };
}

function jsonAnfrage(method: string, body: unknown): RequestInit {
    return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export function ladeKiEinstellungen(): Promise<KiEinstellungen> {
    return stand(BASIS, undefined, 'KI-Einstellungen konnten nicht geladen werden.');
}

export function speichereKiWahl(anbieter: KiAnbieterTyp, ersatzErlaubt: boolean): Promise<KiEinstellungen> {
    return stand(BASIS, jsonAnfrage('PUT', { anbieter, ersatzErlaubt }), 'Die Auswahl konnte nicht gespeichert werden.');
}

export function speichereKiSchluessel(typ: KiAnbieterTyp, apiKey: string): Promise<KiEinstellungen> {
    return stand(anbieterPfad(typ, 'schluessel'), jsonAnfrage('PUT', { apiKey }), 'Der Schlüssel konnte nicht gespeichert werden.');
}

export function entferneKiSchluessel(typ: KiAnbieterTyp): Promise<KiEinstellungen> {
    return stand(anbieterPfad(typ, 'schluessel'), { method: 'DELETE' }, 'Der Schlüssel konnte nicht entfernt werden.');
}

export function speichereKiModelle(typ: KiAnbieterTyp, modelle: KiModellAuswahl): Promise<KiEinstellungen> {
    return stand(anbieterPfad(typ, 'modelle'), jsonAnfrage('PUT', modelle), 'Die Modelle konnten nicht gespeichert werden.');
}

/**
 * Legt fest, welcher Anbieter und welches Modell eine Funktion übernimmt.
 * `anbieter: null` = zurück auf den Standard-Anbieter; `modell: null` = dessen Standard-Modell.
 */
export function speichereKiFunktion(funktion: string, anbieter: KiAnbieterTyp | null, modell: string | null): Promise<KiEinstellungen> {
    return stand(
        `${BASIS}/funktionen/${encodeURIComponent(funktion)}`,
        jsonAnfrage('PUT', { anbieter, modell: anbieter ? (modell?.trim() || null) : null }),
        'Die Auswahl für diese Funktion konnte nicht gespeichert werden.',
    );
}

/**
 * Holt die Modelle, die die Anbieter gerade anbieten. Der Server fragt sie
 * live ab und merkt sich die Liste ein paar Minuten – deshalb erst laden,
 * wenn jemand eine Modellauswahl öffnet.
 */
export async function ladeKiModelle(): Promise<KiModellListe> {
    let res: Response;
    try {
        res = await fetch(`${BASIS}/modelle`);
    } catch {
        throw new Error('Verbindung zum Server fehlgeschlagen.');
    }
    if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Modelle konnten nicht geladen werden.'));
    const d = ((await res.json().catch(() => null)) ?? {}) as Partial<KiModellListe>;
    return {
        modelle: liste<KiModellEintrag>(d.modelle).filter((m) => m && typeof m.modell === 'string' && m.modell),
        hinweise: liste<unknown>(d.hinweise).filter((h): h is string => typeof h === 'string' && h.trim() !== ''),
    };
}

/**
 * Kann der Anbieter alles, was die Funktion braucht? Wenn nicht: der Grund
 * als fertiger Satz (gleicher Wortlaut wie die Server-Meldung), sonst `null`.
 */
export function grundNichtMoeglich(anbieter: KiAnbieter, funktion: KiFunktion): string | null {
    const fehlt = funktion.braucht.filter((b) => !anbieter.faehigkeiten.includes(b));
    if (fehlt.length === 0) return null;
    return `${anbieter.name} kann das in diesem Programm noch nicht (${fehlt.map(faehigkeitText).join(', ')}).`;
}

/**
 * Prüft die Verbindung. Ohne eingetippten Schlüssel nimmt der Server den
 * gespeicherten. Wirft nie: Ein Fehlschlag kommt als `success: false` zurück,
 * damit die Oberfläche ihn im Ergebnis-Kasten zeigen kann.
 */
export async function testeKiAnbieter(typ: KiAnbieterTyp, apiKey: string | null): Promise<TestResult> {
    try {
        const res = await fetch(anbieterPfad(typ, 'test'), jsonAnfrage('POST', { apiKey }));
        if (!res.ok) {
            return { success: false, message: await parseErrorMessage(res, 'Die Verbindung konnte nicht geprüft werden.') };
        }
        const data = (await res.json()) as Partial<TestResult>;
        return {
            success: data.success === true,
            message: typeof data.message === 'string' && data.message.trim()
                ? data.message
                : (data.success === true ? 'Verbindung klappt.' : 'Die Verbindung klappt nicht.'),
        };
    } catch {
        return { success: false, message: 'Verbindung zum Server fehlgeschlagen.' };
    }
}

// ---------------------------------------------------------------------------
// Anzeige-Texte in Handwerker-Sprache
// ---------------------------------------------------------------------------

/** Reihenfolge, in der Fähigkeiten angezeigt werden. */
export const BEKANNTE_FAEHIGKEITEN: readonly KiFaehigkeit[] = ['TEXT', 'BILD', 'PDF', 'AUDIO', 'WERKZEUGE', 'WEBSUCHE'];

const FAEHIGKEIT_TEXT: Record<string, string> = {
    TEXT: 'Text',
    BILD: 'Bilder',
    PDF: 'PDF-Dokumente',
    AUDIO: 'Sprache',
    WERKZEUGE: 'Nachschlagen im Programm',
    WEBSUCHE: 'Internet-Suche',
};

/** Unbekannte Werte erscheinen so, wie der Server sie schickt – lieber roh als gar nicht. */
export function faehigkeitText(faehigkeit: KiFaehigkeit): string {
    return FAEHIGKEIT_TEXT[faehigkeit] ?? faehigkeit;
}

/**
 * Was ein Anbieter in diesem Programm (noch) nicht kann: alles, was irgendein
 * Anbieter kann oder eine Funktion braucht, abzüglich seiner eigenen Fähigkeiten.
 */
export function fehlendeFaehigkeiten(anbieter: KiAnbieter, einstellungen: KiEinstellungen): KiFaehigkeit[] {
    const alle = new Set<KiFaehigkeit>(BEKANNTE_FAEHIGKEITEN);
    einstellungen.anbieterListe.forEach((a) => a.faehigkeiten.forEach((f) => alle.add(f)));
    einstellungen.funktionen.forEach((f) => f.braucht.forEach((b) => alle.add(b)));
    const eigene = new Set(anbieter.faehigkeiten);
    return [...alle].filter((f) => !eigene.has(f));
}

const STUFE_TEXT: Record<string, string> = {
    SCHNELL: 'Schnell',
    STANDARD: 'Standard',
    GRUENDLICH: 'Gründlich',
};

export function stufeText(stufe: KiModellStufe): string {
    return STUFE_TEXT[stufe] ?? stufe;
}

export function datenstandortText(standort: KiDatenstandort): string | null {
    if (standort === 'EU') return 'Daten in der EU';
    if (standort === 'WELTWEIT') return 'Daten auch außerhalb der EU';
    return null;
}

/** Erlaubte Zeichen in Modellnamen – derselbe Satz wie auf dem Server. */
const MODELLNAME = /^[A-Za-z0-9._:/-]+$/;
export const MODELLNAME_MAX = 200;

/**
 * Prüft einen Modellnamen, bevor er zum Server geht. Leer ist erlaubt
 * (= Standard-Modell). Gibt die Fehlermeldung zurück oder `null`.
 */
export function pruefeModellname(name: string): string | null {
    const wert = name.trim();
    if (!wert) return null;
    if (wert.length > MODELLNAME_MAX) return `Der Modellname ist zu lang (höchstens ${MODELLNAME_MAX} Zeichen).`;
    // „..“ sperrt der Server auch: Der Name landet bei manchen Anbietern im Adresspfad.
    if (!MODELLNAME.test(wert) || wert.includes('..')) {
        return `Der Modellname „${wert}“ ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -`;
    }
    return null;
}

/**
 * Feldwert für ein Modell: leer, solange das Standard-Modell gilt (der
 * Platzhalter zeigt es dann), sonst der eingestellte Name.
 */
export function modellFeldwert(modell: KiModell): string {
    return modell.modell === modell.standardModell ? '' : modell.modell;
}

/** Hat der Betrieb bei diesem Anbieter eigene Modelle eingetragen? */
export function hatEigeneModelle(anbieter: KiAnbieter): boolean {
    return anbieter.modelle.some((m) => m.modell !== m.standardModell);
}

/** Anzeigename eines Anbieters aus der Liste – Rückfall auf den Typ. */
export function anbieterName(einstellungen: KiEinstellungen, typ: KiAnbieterTyp): string {
    return einstellungen.anbieterListe.find((a) => a.typ === typ)?.name ?? typ;
}
