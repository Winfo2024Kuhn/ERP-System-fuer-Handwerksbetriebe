import type { Page, Route } from '@playwright/test';
import type { KiAnbieter, KiEigeneWahl, KiEinstellungen, KiFunktion, KiModellListe } from '../../src/features/ki/kiEinstellungenApi';

/**
 * Stub für Einstellungen → KI-Funktionen (`/api/settings/ki/**`).
 *
 * Er hält einen Stand und rechnet – stark vereinfacht wie der Server – nach
 * jeder Änderung neu aus, welche Funktion über welchen Anbieter läuft. Jede
 * Antwort enthält, wie beim echten Backend, den kompletten neuen Stand.
 * Nur Test-Werte, keine echten Schlüssel.
 */

/** Bewusst ein Anbieter, der nicht alles kann – so lassen sich Ersatz und „aus“ durchspielen. */
export function mistralAnbieter(schluesselGesetzt = false): KiAnbieter {
    return {
        typ: 'MISTRAL',
        name: 'Mistral',
        beschreibung: 'Europäischer Anbieter – die Daten werden nach Angabe von Mistral in der EU verarbeitet.',
        datenstandort: 'EU',
        schluesselGesetzt,
        schluesselVerschluesselt: true,
        faehigkeiten: ['TEXT', 'BILD', 'PDF'],
        modelle: [
            { stufe: 'SCHNELL', modell: 'mistral-small-latest', standardModell: 'mistral-small-latest' },
            { stufe: 'STANDARD', modell: 'mistral-medium-latest', standardModell: 'mistral-medium-latest' },
            { stufe: 'GRUENDLICH', modell: 'mistral-large-latest', standardModell: 'mistral-large-latest' },
        ],
    };
}

export function geminiAnbieter(schluesselGesetzt = false): KiAnbieter {
    return {
        typ: 'GEMINI',
        name: 'Google Gemini',
        beschreibung: 'KI von Google – die Daten können auch außerhalb der EU verarbeitet werden.',
        datenstandort: 'WELTWEIT',
        schluesselGesetzt,
        schluesselVerschluesselt: false,
        faehigkeiten: ['TEXT', 'BILD', 'PDF', 'AUDIO', 'WERKZEUGE', 'WEBSUCHE'],
        modelle: [
            { stufe: 'SCHNELL', modell: 'gemini-test-flash', standardModell: 'gemini-test-flash' },
            { stufe: 'STANDARD', modell: 'gemini-test-flash', standardModell: 'gemini-test-flash' },
            { stufe: 'GRUENDLICH', modell: 'gemini-test-pro', standardModell: 'gemini-test-pro' },
        ],
    };
}

const FUNKTIONEN = [
    { funktion: 'DOKUMENT_ANALYSE', name: 'Rechnungen, Lieferscheine und Belege auslesen', braucht: ['PDF', 'BILD'] },
    { funktion: 'PROGRAMM_HILFE', name: 'KI-Hilfe in der Menüleiste', braucht: ['WERKZEUGE'] },
    { funktion: 'SPRACHEINGABE', name: 'Spracheingabe in der Handy-App', braucht: ['AUDIO'] },
];

/** Nur erfundene Test-Modelle. */
export const KI_MODELLE: KiModellListe = {
    modelle: [
        { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-large-latest', name: 'Mistral Large', bilder: true, ton: false },
        { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-medium-latest', name: 'Mistral Medium', bilder: true, ton: false },
        { anbieter: 'MISTRAL', anbieterName: 'Mistral', modell: 'mistral-small-latest', name: 'Mistral Small', bilder: false, ton: false },
        { anbieter: 'GEMINI', anbieterName: 'Google Gemini', modell: 'gemini-test-pro', name: 'Gemini Test Pro', bilder: true, ton: null },
    ],
    hinweise: [],
};

function berechne(stand: Omit<KiEinstellungen, 'funktionen'>, eigene: Record<string, KiEigeneWahl>): KiEinstellungen {
    const funktionen: KiFunktion[] = FUNKTIONEN.map((f) => {
        const eigeneWahl = eigene[f.funktion] ?? null;
        const zustaendig = eigeneWahl?.anbieter ?? stand.anbieter;
        const gewaehlt = stand.anbieterListe.find((a) => a.typ === zustaendig);
        const name = gewaehlt?.name ?? zustaendig;
        const kann = (a: KiAnbieter) => f.braucht.every((b) => a.faehigkeiten.includes(b));
        const modell = (a: KiAnbieter) => a.modelle.find((m) => m.stufe === 'STANDARD')?.modell ?? null;
        if (gewaehlt && gewaehlt.schluesselGesetzt && kann(gewaehlt)) {
            return {
                ...f, status: 'AKTIV', anbieter: gewaehlt.typ, anbieterName: gewaehlt.name,
                modell: eigeneWahl?.modell ?? modell(gewaehlt), hinweis: null, eigeneWahl,
            };
        }
        const grund = gewaehlt && !kann(gewaehlt)
            ? `${name} kann das in diesem Programm noch nicht.`
            : `Für ${name} ist noch kein Schlüssel hinterlegt.`;
        const ersatz = stand.ersatzErlaubt
            ? stand.anbieterListe.find((a) => a.typ !== zustaendig && a.schluesselGesetzt && kann(a))
            : undefined;
        if (ersatz) {
            return {
                ...f, status: 'ERSATZ', anbieter: ersatz.typ, anbieterName: ersatz.name, modell: modell(ersatz),
                hinweis: `${grund} Es springt ${ersatz.name} ein.`, eigeneWahl,
            };
        }
        return { ...f, status: 'AUS', anbieter: null, anbieterName: null, modell: null, hinweis: `${grund} Die Funktion ist aus.`, eigeneWahl };
    });
    return { ...stand, funktionen };
}

export interface KiStub {
    stand: () => KiEinstellungen;
    /** Mitschrift aller ändernden Anfragen: Methode, Pfad, Body. */
    aenderungen: { methode: string; pfad: string; body: unknown }[];
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Registriert den Stub. Nach Auffang-Routen aufrufen, damit er zuerst greift. */
export async function stubbeKiEinstellungen(
    page: Page,
    start: Partial<Omit<KiEinstellungen, 'funktionen'>> = {},
): Promise<KiStub> {
    const eigene: Record<string, KiEigeneWahl> = {};
    let stand = berechne({
        anbieter: 'GEMINI',
        ersatzErlaubt: true,
        verschluesselungBereit: true,
        anbieterListe: [mistralAnbieter(), geminiAnbieter()],
        ...start,
    }, eigene);
    const aenderungen: KiStub['aenderungen'] = [];
    const aendereAnbieter = (typ: string, aenderung: (a: KiAnbieter) => KiAnbieter) => {
        stand = berechne({ ...stand, anbieterListe: stand.anbieterListe.map((a) => (a.typ === typ ? aenderung(a) : a)) }, eigene);
    };

    await page.route(
        (url) => url.pathname === '/api/settings/ki' || url.pathname.startsWith('/api/settings/ki/'),
        (route) => {
            const anfrage = route.request();
            const pfad = new URL(anfrage.url()).pathname;
            const methode = anfrage.method();
            const body: unknown = anfrage.postData() ? anfrage.postDataJSON() : null;
            if (methode !== 'GET') aenderungen.push({ methode, pfad, body });

            if (pfad === '/api/settings/ki') {
                if (methode === 'PUT') {
                    const { anbieter, ersatzErlaubt } = body as { anbieter: string; ersatzErlaubt: boolean };
                    stand = berechne({ ...stand, anbieter, ersatzErlaubt }, eigene);
                }
                return json(route, stand);
            }
            if (pfad === '/api/settings/ki/modelle') {
                // Nur Anbieter mit Schlüssel liefern Modelle – wie der Server.
                const mitSchluessel = new Set(stand.anbieterListe.filter((a) => a.schluesselGesetzt).map((a) => a.typ));
                return json(route, { ...KI_MODELLE, modelle: KI_MODELLE.modelle.filter((m) => mitSchluessel.has(m.anbieter)) });
            }
            const funktion = /^\/api\/settings\/ki\/funktionen\/([^/]+)$/.exec(pfad);
            if (funktion && methode === 'PUT') {
                const wahl = body as { anbieter: string | null; modell: string | null };
                if (wahl.anbieter) eigene[funktion[1]] = { anbieter: wahl.anbieter, modell: wahl.modell || null };
                else delete eigene[funktion[1]];
                stand = berechne(stand, eigene);
                return json(route, stand);
            }
            const treffer = /^\/api\/settings\/ki\/anbieter\/([^/]+)\/(schluessel|test|modelle)$/.exec(pfad);
            if (!treffer) return json(route, { message: 'Nicht gefunden' }, 404);
            const [, typ, was] = treffer;
            if (was === 'test') return json(route, { success: true, message: 'Verbindung klappt.' });
            if (was === 'schluessel') aendereAnbieter(typ, (a) => ({ ...a, schluesselGesetzt: methode === 'PUT' }));
            if (was === 'modelle') {
                const auswahl = body as Record<string, string>;
                aendereAnbieter(typ, (a) => ({ ...a, modelle: a.modelle.map((m) => ({ ...m, modell: auswahl[m.stufe] || m.standardModell })) }));
            }
            return json(route, stand);
        },
    );
    return { stand: () => stand, aenderungen };
}
