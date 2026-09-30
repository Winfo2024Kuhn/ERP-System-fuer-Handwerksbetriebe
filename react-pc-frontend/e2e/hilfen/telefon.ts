import type { Page, Route } from '@playwright/test';

/**
 * Gestubbte API fuer die Telefon-Anbindung (Anrufliste, Anrufbeantworter,
 * Live-Anruf-Fenster, Einstellungen). Nur Dummy-Daten (DSGVO).
 *
 * Das Live-Fenster laeuft ueber echtes Server-Sent-Events-Protokoll: jede
 * Verbindung auf /api/telefon/live wird festgehalten, bis der Test mit
 * `sendeLive()` ein Ereignis schickt. Die Antwort traegt `retry: 200`, damit
 * der Browser sofort die naechste Verbindung oeffnet -- die dann wieder auf
 * das naechste Ereignis wartet.
 */

export const KUNDE_MAX = { typ: 'KUNDE', id: 7, name: 'Max Mustermann', nummer: 'K-1007', ort: 'Musterstadt' };
export const KUNDE_ERIKA = { typ: 'KUNDE', id: 8, name: 'Erika Mustermann', nummer: 'K-1008', ort: 'Beispielhausen' };
export const LIEFERANT_GMBH = { typ: 'LIEFERANT', id: 3, name: 'Mustermann GmbH', nummer: null, ort: 'Würzburg' };
export const KANZLEI_BEISPIEL = { typ: 'STEUERBERATER', id: 30, name: 'Kanzlei Beispiel', nummer: null, ort: null };

/** Kanzleien zur Auswahl beim Zuordnen, mit Ansprechpartnern (einer hat schon eine Nummer). */
export const KANZLEI_AUSWAHL = [
    {
        id: 30, name: 'Kanzlei Beispiel', ansprechpartner: [
            { id: 40, name: 'Erika Beispiel', telefon: null },
            { id: 41, name: 'Max Muster', telefon: '0931 66666' },
        ],
    },
    { id: 31, name: 'Steuerbüro Muster', ansprechpartner: [] },
];

/** Überblick für das Anruf-Fenster: Ansprechpartner, Adresse, Projekte, Anfragen. */
export const UEBERBLICK_MAX = {
    typ: 'KUNDE', id: 7, name: 'Max Mustermann', nummer: 'K-1007', ansprechpartner: 'Erika Mustermann',
    strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt',
    projekte: [
        { id: 21, bauvorhaben: 'Wintergarten Musterweg', auftragsnummer: '2026-001', ort: 'Musterstadt', abgeschlossen: false },
        { id: 23, bauvorhaben: 'Treppengeländer Außentreppe mit langem Namen für den Zeilenumbruch', auftragsnummer: '2026-009', ort: 'Beispielhausen', abgeschlossen: false },
        { id: 24, bauvorhaben: 'Vordach Eingang', auftragsnummer: '2025-031', ort: 'Musterstadt', abgeschlossen: true },
        { id: 22, bauvorhaben: 'Carport', auftragsnummer: '2025-017', ort: 'Musterstadt', abgeschlossen: true },
        { id: 25, bauvorhaben: 'Zaunanlage', auftragsnummer: '2024-102', ort: 'Musterstadt', abgeschlossen: true },
    ],
    projekteGesamt: 5,
    anfragen: [
        { id: 31, bauvorhaben: 'Balkongeländer', angebotsnummer: 'AN-2026-044', ort: 'Beispielhausen', abgeschlossen: false },
        { id: 32, bauvorhaben: 'Gartentor', angebotsnummer: null, ort: null, abgeschlossen: false },
    ],
    anfragenGesamt: 2,
};

/** Telefone der FRITZ!Box für „Telefon an diesem Rechner" (Namen wie von der FRITZ!Box geliefert). */
export const WAEHL_TELEFONE = [{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }];

/** Heutiger bzw. gestriger Tag als ISO-Datum, wie ihn der Tagesfilter schickt. */
export function isoTag(tageZurueck = 0): string {
    const d = new Date();
    d.setDate(d.getDate() - tageZurueck);
    const zwei = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${zwei(d.getMonth() + 1)}-${zwei(d.getDate())}`;
}

function heute(uhrzeit: string): string {
    const d = new Date();
    const zwei = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${zwei(d.getMonth() + 1)}-${zwei(d.getDate())}T${uhrzeit}:00`;
}
function gestern(uhrzeit: string): string {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    const zwei = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${zwei(d.getMonth() + 1)}-${zwei(d.getDate())}T${uhrzeit}:00`;
}

type Anruf = Record<string, unknown> & { id: number; art: string; kontakt: unknown; nummer: string };
type Nachricht = Record<string, unknown> & { id: number; neu: boolean; anrufbeantworter: number };

function beispielAnrufe(): Anruf[] {
    return [
        { id: 1, zeitpunkt: heute('11:55'), art: 'VERPASST', anrufbeantworter: null, nummer: '0931 1234567', eigeneNummer: '0931 7654321', dauerMinuten: 0, nameFritzbox: null, zuordnung: 'AUTOMATISCH', kontakt: KUNDE_MAX, kandidaten: [], sprachnachrichtId: null },
        { id: 2, zeitpunkt: heute('11:40'), art: 'ANRUFBEANTWORTER', anrufbeantworter: 1, nummer: '0931 2222222', eigeneNummer: '0931 7654321', dauerMinuten: 1, nameFritzbox: null, zuordnung: 'KEINE', kontakt: null, kandidaten: [], sprachnachrichtId: 11 },
        { id: 3, zeitpunkt: heute('10:12'), art: 'ANGENOMMEN', anrufbeantworter: null, nummer: '0931 3333333', eigeneNummer: '0931 7654321', dauerMinuten: 7, nameFritzbox: null, zuordnung: 'KEINE', kontakt: null, kandidaten: [KUNDE_MAX, KUNDE_ERIKA], sprachnachrichtId: null },
        { id: 4, zeitpunkt: gestern('09:52'), art: 'AUSGEHEND', anrufbeantworter: null, nummer: '0931 4444444', eigeneNummer: '0931 7654321', dauerMinuten: 3, nameFritzbox: null, zuordnung: 'AUTOMATISCH', kontakt: LIEFERANT_GMBH, kandidaten: [], sprachnachrichtId: null },
        { id: 5, zeitpunkt: gestern('08:05'), art: 'ABGEWIESEN', anrufbeantworter: null, nummer: '', eigeneNummer: '0931 7654321', dauerMinuten: 0, nameFritzbox: null, zuordnung: 'KEINE', kontakt: null, kandidaten: [], sprachnachrichtId: null },
    ];
}

function beispielNachrichten(): Nachricht[] {
    return [
        { id: 11, anrufbeantworter: 1, zeitpunkt: heute('11:40'), nummer: '0931 2222222', dauerSekunden: 3, neu: true, abgehoertAm: null, abgehoertVon: null, zuordnung: 'KEINE', kontakt: null, kandidaten: [], nameFritzbox: null },
        { id: 12, anrufbeantworter: 0, zeitpunkt: gestern('16:20'), nummer: '0931 1234567', dauerSekunden: 3, neu: false, abgehoertAm: gestern('17:00'), abgehoertVon: 'Erika Mustermann', zuordnung: 'AUTOMATISCH', kontakt: KUNDE_MAX, kandidaten: [], nameFritzbox: null },
    ];
}

/** Eine Sekunde Stille als WAV (8 kHz, 16 Bit, mono) -- genug, damit Chromium wirklich abspielt. */
function stilleWav(sekunden = 3): Buffer {
    const rate = 8000;
    const daten = rate * 2 * sekunden;
    const puffer = Buffer.alloc(44 + daten);
    puffer.write('RIFF', 0);
    puffer.writeUInt32LE(36 + daten, 4);
    puffer.write('WAVE', 8);
    puffer.write('fmt ', 12);
    puffer.writeUInt32LE(16, 16);
    puffer.writeUInt16LE(1, 20);
    puffer.writeUInt16LE(1, 22);
    puffer.writeUInt32LE(rate, 24);
    puffer.writeUInt32LE(rate * 2, 28);
    puffer.writeUInt16LE(2, 32);
    puffer.writeUInt16LE(16, 34);
    puffer.write('data', 36);
    puffer.writeUInt32LE(daten, 40);
    return puffer;
}

export interface TelefonStub {
    /** Alle Schreibaufrufe, zum Auswerten im Test. */
    mitschrift: { methode: string; pfad: string; query: string; body: unknown }[];
    /** Schickt ein Live-Ereignis ueber die naechste offene SSE-Verbindung. */
    sendeLive: (anruf: Record<string, unknown>) => Promise<void>;
}

export async function stubbeTelefonApi(page: Page, optionen: {
    darf?: boolean;
    admin?: boolean;
    eingerichtet?: boolean;
    /** Weitere Anrufe zusätzlich zu den Beispielanrufen (z. B. vom Steuerberater). */
    zusatzAnrufe?: Anruf[];
    /** Telefone der FRITZ!Box für „Telefon an diesem Rechner" (Standard: zwei Beispiel-Telefone). */
    telefone?: { name: string }[];
    /** Antwort auf POST /anrufen – Standard 204. Wird bei jedem Aufruf neu gefragt. */
    anrufenAntwort?: () => { status: number; body?: unknown };
} = {}): Promise<TelefonStub> {
    const darf = optionen.darf ?? true;
    const admin = optionen.admin ?? true;
    const anrufe = [...beispielAnrufe(), ...(optionen.zusatzAnrufe ?? [])];
    const nachrichten = beispielNachrichten();
    const mitschrift: TelefonStub['mitschrift'] = [];
    const wartendeLive: ((body: string) => void)[] = [];
    let einstellungen = {
        aktiv: true, host: 'fritz.box', benutzer: 'erp', passwortGesetzt: true, verschluesselungEingerichtet: true,
        geschaeftsnummern: ['0931 7654321'], anrufbeantworter: [{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }],
        aufbewahrungAnrufeMonate: 12, aufbewahrungSprachnachrichtenMonate: 12, landesvorwahl: '49', ortsvorwahl: '931',
        letzteAbholung: heute('11:58'), letzterFehler: null as string | null, anrufmonitorVerbunden: true,
    };

    const json = (route: Route, body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

    // Auffangnetz zuerst registrieren: Playwright fragt die zuletzt registrierte Route zuerst.
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: admin ? ['ADMIN'] : [], admin, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') {
            if (!darf) return json(route, { totalCount: 0, categories: [], recentItems: [] });
            return json(route, {
                totalCount: 2,
                categories: [
                    { type: 'SPRACHNACHRICHTEN', label: 'Neue Nachrichten', count: 1, icon: 'Voicemail', link: '/telefon/anrufbeantworter' },
                    { type: 'VERPASSTE_ANRUFE', label: 'Verpasste Anrufe', count: 1, icon: 'PhoneMissed', link: '/telefon/anrufe?offen=1' },
                ],
                recentItems: [
                    { type: 'SPRACHNACHRICHT', title: 'Nachricht von 0931 2222222', subtitle: 'AB Nacht · 0:03', timestamp: heute('11:40'), link: '/telefon/anrufbeantworter?nachricht=11' },
                    { type: 'VERPASSTER_ANRUF', title: 'Verpasst: Max Mustermann', subtitle: 'Heute 11:55', timestamp: heute('11:55'), link: '/telefon/anrufe?anruf=1' },
                ],
            });
        }
        if (pfad === '/api/kunden') {
            return json(route, { kunden: [{ id: 7, name: 'Max Mustermann', kundennummer: 'K-1007', ort: 'Musterstadt' }, { id: 8, name: 'Erika Mustermann', kundennummer: 'K-1008', ort: 'Beispielhausen' }], gesamt: 2 });
        }
        if (pfad === '/api/kunden/7') {
            return json(route, { id: 7, kundennummer: 'K-1007', name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', telefon: '0931 1234567', kundenEmails: [], kommunikation: [], projekte: [], anfragen: [], geschaeftsdokumente: [], notizen: [] });
        }
        if (pfad === '/api/abteilungen/berechtigungen') {
            return json(route, [{ abteilungId: 1, abteilungName: 'Büro', berechtigungen: [], darfMonatAbschliessen: false, darfTelefonSehen: true, darfRechnungenGenehmigen: false, darfRechnungenSehen: false, darfFreigabeAnnahmePushen: false, darfWebseitenAnfragenPushen: false }]);
        }
        if (pfad.startsWith('/api/settings/')) return json(route, {});
        return json(route, []);
    });

    await page.route('**/api/telefon/**', async (route) => {
        const anfrage = route.request();
        const url = new URL(anfrage.url());
        const pfad = url.pathname;
        const methode = anfrage.method();
        if (methode !== 'GET') {
            let body: unknown = null;
            try { body = anfrage.postDataJSON(); } catch { body = null; }
            mitschrift.push({ methode, pfad, query: url.search, body });
        }

        if (pfad === '/api/telefon/berechtigung') return json(route, { darfTelefonSehen: darf });
        if (!darf && !pfad.startsWith('/api/telefon/kontakt-rufnummern')) return json(route, { message: 'Kein Recht' }, 403);

        if (pfad === '/api/telefon/live') {
            const body = await new Promise<string>((r) => wartendeLive.push(r));
            return route.fulfill({ status: 200, headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' }, body });
        }
        if (pfad === '/api/telefon/status') {
            return json(route, {
                eingerichtet: optionen.eingerichtet ?? true,
                anrufbeantworter: einstellungen.anrufbeantworter,
                letzteAbholung: einstellungen.letzteAbholung,
                letzterFehler: null,
                neueSprachnachrichten: nachrichten.filter((n) => n.neu).length,
            });
        }
        if (pfad === '/api/telefon/sprachnachrichten/anzahl-neu') return json(route, { anzahl: nachrichten.filter((n) => n.neu).length });
        if (pfad === '/api/telefon/anrufe') {
            let liste = anrufe;
            if (url.searchParams.get('art')) liste = liste.filter((a) => a.art === url.searchParams.get('art'));
            if (url.searchParams.get('nurUnbekannt') === 'true') liste = liste.filter((a) => !a.kontakt);
            const suche = url.searchParams.get('suche')?.toLowerCase();
            if (suche) liste = liste.filter((a) => a.nummer.includes(suche) || JSON.stringify(a.kontakt ?? '').toLowerCase().includes(suche));
            if (url.searchParams.get('kundeId')) liste = liste.filter((a) => (a.kontakt as { id?: number } | null)?.id === Number(url.searchParams.get('kundeId')));
            const tag = url.searchParams.get('tag');
            if (tag) liste = liste.filter((a) => String(a.zeitpunkt).startsWith(tag));
            const kontaktart = url.searchParams.get('kontaktart');
            if (kontaktart) liste = liste.filter((a) => (a.kontakt as { typ?: string } | null)?.typ === kontaktart);
            return json(route, { content: liste, totalElements: liste.length, totalPages: 1, number: 0, size: 50 });
        }
        const zuordnungAnruf = /^\/api\/telefon\/anrufe\/(\d+)\/zuordnung$/.exec(pfad);
        if (zuordnungAnruf) {
            const eintrag = anrufe.find((a) => a.id === Number(zuordnungAnruf[1]))!;
            if (methode === 'DELETE') Object.assign(eintrag, { kontakt: null, zuordnung: 'KEINE' });
            else {
                const { kundeId, lieferantId, steuerberaterId, ansprechpartnerId } = anfrage.postDataJSON() as {
                    kundeId: number | null; lieferantId: number | null; steuerberaterId: number | null; ansprechpartnerId: number | null;
                };
                const person = KANZLEI_AUSWAHL[0].ansprechpartner.find((a) => a.id === ansprechpartnerId)?.name ?? null;
                const kontakt = kundeId === 8 ? KUNDE_ERIKA : kundeId ? KUNDE_MAX : lieferantId ? LIEFERANT_GMBH
                    : steuerberaterId ? { ...KANZLEI_BEISPIEL, ansprechpartner: person } : null;
                Object.assign(eintrag, { kontakt, kandidaten: [], zuordnung: 'MANUELL' });
            }
            return json(route, eintrag);
        }
        if (pfad === '/api/telefon/sprachnachrichten') {
            const ab = url.searchParams.get('anrufbeantworter');
            const tag = url.searchParams.get('tag');
            return json(route, nachrichten
                .filter((n) => ab === null || n.anrufbeantworter === Number(ab))
                .filter((n) => !tag || String(n.zeitpunkt).startsWith(tag)));
        }
        const audio = /^\/api\/telefon\/sprachnachrichten\/(\d+)\/audio$/.exec(pfad);
        if (audio) return route.fulfill({ status: 200, contentType: 'audio/wav', body: stilleWav() });
        const nachricht = /^\/api\/telefon\/sprachnachrichten\/(\d+)$/.exec(pfad);
        if (nachricht && methode === 'PATCH') {
            const eintrag = nachrichten.find((n) => n.id === Number(nachricht[1]))!;
            const { abgehoert } = anfrage.postDataJSON() as { abgehoert: boolean };
            Object.assign(eintrag, { neu: !abgehoert, abgehoertAm: abgehoert ? heute('12:04') : null, abgehoertVon: abgehoert ? 'Max Mustermann' : null });
            return json(route, eintrag);
        }
        if (pfad === '/api/telefon/abholen') return json(route, { erfolgreich: true, meldung: 'ok', neueAnrufe: 2, neueSprachnachrichten: 1, nachtraeglichZugeordnet: 0 });
        if (pfad === '/api/telefon/steuerberater') return json(route, KANZLEI_AUSWAHL);
        if (pfad === '/api/telefon/telefone') return json(route, optionen.telefone ?? WAEHL_TELEFONE);
        if (pfad === '/api/telefon/anrufen' && methode === 'POST') {
            const antwort = optionen.anrufenAntwort?.() ?? { status: 204 };
            if (antwort.status === 204) return route.fulfill({ status: 204 });
            return json(route, antwort.body ?? {}, antwort.status);
        }
        if (pfad === '/api/telefon/kontakt-ueberblick') {
            if (url.searchParams.get('steuerberaterId') === '30') {
                return json(route, { ...UEBERBLICK_MAX, typ: 'STEUERBERATER', id: 30, name: 'Kanzlei Beispiel', nummer: null, ansprechpartner: 'Christine Beispiel', strasse: null, plz: null, ort: null, projekte: [], projekteGesamt: 0, anfragen: [], anfragenGesamt: 0 });
            }
            if (url.searchParams.get('kundeId') === '7') return json(route, UEBERBLICK_MAX);
            if (url.searchParams.get('lieferantId') === '3') {
                return json(route, { ...UEBERBLICK_MAX, typ: 'LIEFERANT', id: 3, name: 'Mustermann GmbH', nummer: null, ansprechpartner: 'Hans Beispiel', ort: 'Würzburg', projekte: [], projekteGesamt: 0, anfragen: [], anfragenGesamt: 0 });
            }
            return json(route, { message: 'Kunde nicht gefunden' }, 404);
        }
        if (pfad === '/api/telefon/kontakt-rufnummern') return json(route, [{ id: 5, nummer: '0931 5555555' }]);
        if (pfad === '/api/telefon/einstellungen/test') {
            return json(route, {
                erfolgreich: true, meldung: 'Verbindung zur FRITZ!Box steht.',
                eigeneNummern: ['0931 7654321', '0931 1111111'],
                anrufbeantworter: [{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }],
                landesvorwahl: '49', ortsvorwahl: '931',
            });
        }
        if (pfad === '/api/telefon/einstellungen') {
            if (!admin) return json(route, { message: 'Nur für Administratoren' }, 403);
            if (methode === 'PUT') {
                const neu = anfrage.postDataJSON() as Record<string, unknown>;
                einstellungen = { ...einstellungen, ...neu, passwortGesetzt: einstellungen.passwortGesetzt || !!neu.passwort } as typeof einstellungen;
            }
            return json(route, einstellungen);
        }
        if (pfad === '/api/telefon/admin/nachholen') return json(route, { erfolgreich: true, meldung: 'ok', neueAnrufe: 40, neueSprachnachrichten: 2, nachtraeglichZugeordnet: 1 });
        return json(route, { message: 'Unbekannt' }, 404);
    });

    const sendeLive = async (anruf: Record<string, unknown>) => {
        const ende = Date.now() + 10_000;
        while (wartendeLive.length === 0) {
            if (Date.now() > ende) throw new Error('Keine offene Live-Verbindung');
            await page.waitForTimeout(50);
        }
        const daten = { nummer: '0931 1234567', kontakt: null, kandidaten: [], angenommen: false, ...anruf };
        wartendeLive.shift()!(`retry: 200\nevent: anruf\ndata: ${JSON.stringify(daten)}\n\n`);
    };

    return { mitschrift, sendeLive };
}
