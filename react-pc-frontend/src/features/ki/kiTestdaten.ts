import type { AuthUser } from '../../auth/AuthContext';
import type { KiAnbieter, KiAnbieterTyp, KiEigeneWahl, KiEinstellungen, KiFunktion, KiModellListe } from './kiEinstellungenApi';

/**
 * Dummy-Benutzer für Tests rund um „KI an/aus“ und die Ersteinrichtung.
 * Nur erfundene Namen (DSGVO). Standard: angemeldet, kein Admin, KI an.
 */
export function benutzer(teil: Partial<AuthUser> = {}): AuthUser {
    return {
        id: 1,
        displayName: 'Max Mustermann',
        username: 'max',
        active: true,
        roles: [],
        admin: false,
        requiresInitialSetup: false,
        kiAktiv: true,
        ...teil,
    };
}

/** Admin mit Rolle ADMIN – sonst wie {@link benutzer}. */
export function admin(teil: Partial<AuthUser> = {}): AuthUser {
    return benutzer({ roles: ['ADMIN'], admin: true, ...teil });
}

// ---------------------------------------------------------------------------
// KI-Anbieter (Einstellungen → KI-Funktionen). Nur Test-Werte, keine echten Schlüssel.
// ---------------------------------------------------------------------------

/** Mistral wie vom Server: EU, ohne Sprache und Programm-Werkzeuge, Schlüssel verschlüsselt. */
export function mistral(teil: Partial<KiAnbieter> = {}): KiAnbieter {
    return {
        typ: 'MISTRAL',
        name: 'Mistral',
        beschreibung: 'Europäischer Anbieter – die Daten werden nach Angabe von Mistral in der EU verarbeitet.',
        datenstandort: 'EU',
        schluesselGesetzt: false,
        schluesselVerschluesselt: true,
        faehigkeiten: ['TEXT', 'BILD', 'PDF'],
        modelle: [
            { stufe: 'SCHNELL', modell: 'mistral-small-latest', standardModell: 'mistral-small-latest' },
            { stufe: 'STANDARD', modell: 'mistral-medium-latest', standardModell: 'mistral-medium-latest' },
            { stufe: 'GRUENDLICH', modell: 'mistral-large-latest', standardModell: 'mistral-large-latest' },
        ],
        ...teil,
    };
}

/** Google Gemini wie vom Server: kann alles, Daten auch außerhalb der EU, Schlüssel (historisch) unverschlüsselt. */
export function gemini(teil: Partial<KiAnbieter> = {}): KiAnbieter {
    return {
        typ: 'GEMINI',
        name: 'Google Gemini',
        beschreibung: 'KI von Google – die Daten können auch außerhalb der EU verarbeitet werden.',
        datenstandort: 'WELTWEIT',
        schluesselGesetzt: false,
        schluesselVerschluesselt: false,
        faehigkeiten: ['TEXT', 'BILD', 'PDF', 'AUDIO', 'WERKZEUGE', 'WISSENSSUCHE'],
        modelle: [
            { stufe: 'SCHNELL', modell: 'gemini-test-flash', standardModell: 'gemini-test-flash' },
            { stufe: 'STANDARD', modell: 'gemini-test-flash', standardModell: 'gemini-test-flash' },
            { stufe: 'GRUENDLICH', modell: 'gemini-test-pro', standardModell: 'gemini-test-pro' },
        ],
        ...teil,
    };
}

export function funktion(teil: Partial<KiFunktion> = {}): KiFunktion {
    return {
        funktion: 'DOKUMENT_ANALYSE',
        name: 'Rechnungen, Lieferscheine und Belege auslesen',
        braucht: ['PDF', 'BILD'],
        status: 'AKTIV',
        anbieter: 'GEMINI',
        anbieterName: 'Google Gemini',
        modell: 'gemini-test-flash',
        hinweis: null,
        eigeneWahl: null,
        ...teil,
    };
}

/**
 * Rechnet – stark vereinfacht wie der Server – aus, welche Funktion über
 * welchen Anbieter läuft. Reicht, damit Tests nach einer Änderung einen
 * stimmigen neuen Stand bekommen. `eigene` = eigene Wahl je Funktion.
 */
export function berechneFunktionen(
    anbieter: KiAnbieterTyp,
    ersatzErlaubt: boolean,
    liste: KiAnbieter[],
    eigene: Record<string, KiEigeneWahl> = {},
): KiFunktion[] {
    const vorlagen: { funktion: string; name: string; braucht: string[] }[] = [
        { funktion: 'DOKUMENT_ANALYSE', name: 'Rechnungen, Lieferscheine und Belege auslesen', braucht: ['PDF', 'BILD'] },
        { funktion: 'SPRACHEINGABE', name: 'Spracheingabe in der Handy-App', braucht: ['AUDIO'] },
    ];
    return vorlagen.map((v) => {
        const eigeneWahl = eigene[v.funktion] ?? null;
        const zustaendig = eigeneWahl ? eigeneWahl.anbieter : anbieter;
        const gewaehlt = liste.find((a) => a.typ === zustaendig);
        const kann = (a: KiAnbieter) => a.schluesselGesetzt && v.braucht.every((b) => a.faehigkeiten.includes(b));
        const stufe = (a: KiAnbieter) => a.modelle.find((m) => m.stufe === 'STANDARD')?.modell ?? null;
        if (gewaehlt && kann(gewaehlt)) {
            return funktion({
                ...v, status: 'AKTIV', anbieter: gewaehlt.typ, anbieterName: gewaehlt.name,
                modell: eigeneWahl?.modell ?? stufe(gewaehlt), hinweis: null, eigeneWahl,
            });
        }
        const ersatz = ersatzErlaubt ? liste.find((a) => a.typ !== zustaendig && kann(a)) : undefined;
        const name = gewaehlt?.name ?? zustaendig;
        if (ersatz) {
            return funktion({
                ...v, status: 'ERSATZ', anbieter: ersatz.typ, anbieterName: ersatz.name, modell: stufe(ersatz),
                hinweis: `${name} kann das in diesem Programm noch nicht. Es springt ${ersatz.name} ein.`, eigeneWahl,
            });
        }
        return funktion({
            ...v, status: 'AUS', anbieter: null, anbieterName: null, modell: null,
            hinweis: `${name} kann das in diesem Programm noch nicht, und kein Ersatz ist erlaubt.`, eigeneWahl,
        });
    });
}

/** Modelle, wie sie `GET /api/settings/ki/modelle` liefert. Nur erfundene Test-Modelle. */
export function kiModellListe(teil: Partial<KiModellListe> = {}): KiModellListe {
    return {
        modelle: [
            { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-large-latest', name: 'Mistral Large', bilder: true },
            { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-medium-latest', name: 'Mistral Medium', bilder: true },
            { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-small-latest', name: 'Mistral Small', bilder: false },
            { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-test-text', name: 'Mistral Test Text', bilder: null },
            { anbieter: 'GEMINI', anbieterName: 'Google Gemini', modell: 'gemini-test-pro', name: 'Gemini Test Pro', bilder: true },
        ],
        hinweise: [],
        ...teil,
    };
}

/** Kompletter Stand wie von `GET /api/settings/ki`. Standard: Gemini gewählt, ohne Schlüssel, Ersatz an. */
export function kiEinstellungen(teil: Partial<KiEinstellungen> = {}): KiEinstellungen {
    const anbieter = teil.anbieter ?? 'GEMINI';
    const ersatzErlaubt = teil.ersatzErlaubt ?? true;
    const anbieterListe = teil.anbieterListe ?? [mistral(), gemini()];
    return {
        anbieter,
        ersatzErlaubt,
        verschluesselungBereit: true,
        anbieterListe,
        funktionen: berechneFunktionen(anbieter, ersatzErlaubt, anbieterListe),
        ...teil,
    };
}
