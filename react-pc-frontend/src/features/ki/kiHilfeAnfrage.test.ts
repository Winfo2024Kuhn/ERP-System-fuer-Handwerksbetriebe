import { afterEach, describe, expect, it, vi } from 'vitest';
import { KI_VERBINDUNGSFEHLER, KiHilfeFehler, stelleKiFrage, type KiAnfrage } from './kiHilfeAnfrage';

const ANFRAGE: KiAnfrage = {
    messages: [{ role: 'user', text: 'Wie storniere ich eine Rechnung?' }],
    context: { route: '/rechnungsuebersicht', pageTitle: 'Rechnungen' },
};

function strom(body: string, status = 200): Response {
    return new Response(body, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

function json(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function stubbeFetch(antwort: Response) {
    const fetchMock = vi.fn().mockResolvedValue(antwort);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

async function fehlerVon(versprechen: Promise<unknown>): Promise<unknown> {
    try {
        await versprechen;
    } catch (fehler) {
        return fehler;
    }
    throw new Error('Es wurde ein Fehler erwartet.');
}

describe('stelleKiFrage', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('schickt die Frage per POST und meldet jeden Schritt, bevor die Antwort kommt', async () => {
        const fetchMock = stubbeFetch(strom(
            'event:schritt\ndata:{"text":"Passende Stellen im Programm gesucht"}\n\n'
            + 'event:schritt\ndata:{"text":"Schaut nach: Wie man eine Rechnung storniert"}\n\n'
            + 'event:antwort\ndata:{"reply":"So geht es …","sources":[{"title":"DIN","url":"https://example.org/din"}],'
            + '"schritte":["Passende Stellen im Programm gesucht","Schaut nach: Wie man eine Rechnung storniert"]}\n\n',
        ));
        const schritte: string[] = [];
        const signal = new AbortController().signal;

        const antwort = await stelleKiFrage(ANFRAGE, { signal, beiSchritt: (text) => schritte.push(text) });

        expect(schritte).toEqual(['Passende Stellen im Programm gesucht', 'Schaut nach: Wie man eine Rechnung storniert']);
        expect(antwort).toEqual({
            reply: 'So geht es …',
            sources: [{ title: 'DIN', url: 'https://example.org/din' }],
            schritte: ['Passende Stellen im Programm gesucht', 'Schaut nach: Wie man eine Rechnung storniert'],
        });
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/ki-hilfe/chat');
        expect(init).toMatchObject({ method: 'POST', headers: { 'Content-Type': 'application/json' }, signal });
        expect(JSON.parse(init.body)).toEqual(ANFRAGE);
    });

    it('kommt ohne Schritt-Empfänger aus und übergeht leere oder kaputte Schritte', async () => {
        stubbeFetch(strom(
            'event:schritt\ndata:{"text":"  "}\n\n'
            + 'event:schritt\ndata:kein json\n\n'
            + 'event:schritt\ndata:{"text":"Liest genauer: Rechnungen im Projekt"}\n\n'
            + 'event:unbekannt\ndata:{}\n\n'
            + 'event:antwort\ndata:{"reply":"Fertig"}\n\n',
        ));
        await expect(stelleKiFrage(ANFRAGE)).resolves.toEqual({ reply: 'Fertig', sources: [], schritte: [] });

        stubbeFetch(strom(
            'event:schritt\ndata:{"text":"  "}\n\nevent:schritt\ndata:kein json\n\n'
            + 'event:schritt\ndata:{"text":"Liest genauer"}\n\nevent:antwort\ndata:{"reply":"Fertig"}\n\n',
        ));
        const schritte: string[] = [];
        await stelleKiFrage(ANFRAGE, { beiSchritt: (text) => schritte.push(text) });
        expect(schritte).toEqual(['Liest genauer']);
    });

    it('behält nur echte Web-Quellen und Text-Schritte', async () => {
        stubbeFetch(strom('event:antwort\ndata:' + JSON.stringify({
            reply: 'Antwort',
            sources: [
                { title: 'Norm', url: 'https://example.org/norm' },
                { url: 'http://example.org/ohne-titel' },
                { title: 'Böse', url: 'javascript:alert(1)' },
                { title: 'Ohne Adresse' },
                'kaputt',
            ],
            schritte: ['Gesucht', '', 42, '  ', 'Gelesen'],
        }) + '\n\n'));

        await expect(stelleKiFrage(ANFRAGE)).resolves.toEqual({
            reply: 'Antwort',
            sources: [
                { title: 'Norm', url: 'https://example.org/norm' },
                { title: '', url: 'http://example.org/ohne-titel' },
            ],
            schritte: ['Gesucht', 'Gelesen'],
        });
    });

    it('meldet ein fehler-Ereignis mit der Meldung vom Server', async () => {
        stubbeFetch(strom(
            'event:schritt\ndata:{"text":"Passende Stellen im Programm gesucht"}\n\n'
            + 'event:fehler\ndata:{"error":"KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut."}\n\n',
        ));
        const fehler = await fehlerVon(stelleKiFrage(ANFRAGE));
        expect(fehler).toBeInstanceOf(KiHilfeFehler);
        expect((fehler as Error).message).toBe('KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.');
    });

    it('nimmt bei einem fehler-Ereignis ohne lesbare Meldung einen Ersatztext', async () => {
        stubbeFetch(strom('event:fehler\ndata:{}\n\n'));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow('KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.');
    });

    it('liest die Meldung aus einer abgelehnten Frage (400 mit message)', async () => {
        stubbeFetch(json({ status: 400, message: 'Leere Nachricht' }, 400));
        const fehler = await fehlerVon(stelleKiFrage(ANFRAGE));
        expect(fehler).toBeInstanceOf(KiHilfeFehler);
        expect((fehler as Error).message).toBe('Leere Nachricht');
    });

    it('liest auch das ältere Feld error und JSON mit Status 200 als Fehler', async () => {
        stubbeFetch(json({ error: 'Zu viele Nachrichten' }, 200));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow('Zu viele Nachrichten');
    });

    it('liest die Meldung, wenn die KI-Hilfe ausgelastet ist (429)', async () => {
        stubbeFetch(json({ status: 429, message: 'Die KI-Hilfe ist gerade ausgelastet. Bitte versuche es gleich noch einmal.' }, 429));
        const fehler = await fehlerVon(stelleKiFrage(ANFRAGE));
        expect(fehler).toBeInstanceOf(KiHilfeFehler);
        expect((fehler as Error).message).toBe('Die KI-Hilfe ist gerade ausgelastet. Bitte versuche es gleich noch einmal.');
    });

    it('nimmt bei 401/403 ohne Inhalt einen verständlichen Ersatztext', async () => {
        stubbeFetch(new Response(null, { status: 403 }));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow('Die KI-Hilfe konnte die Frage nicht annehmen. Bitte versuche es erneut.');
    });

    it('meldet einen Verbindungsfehler, wenn der Strom ohne Antwort endet', async () => {
        stubbeFetch(strom('event:schritt\ndata:{"text":"Passende Stellen im Programm gesucht"}\n\n'));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow(KI_VERBINDUNGSFEHLER);
    });

    it('meldet einen Verbindungsfehler, wenn die Antwort unlesbar ist', async () => {
        stubbeFetch(strom('event:antwort\ndata:{"reply":\n\n'));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow(KI_VERBINDUNGSFEHLER);

        stubbeFetch(strom('event:antwort\ndata:{"sources":[]}\n\n'));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toThrow(KI_VERBINDUNGSFEHLER);
    });

    it('reicht Fehler von fetch (Abbruch, Server weg) unverändert weiter', async () => {
        const abbruch = new DOMException('Abgebrochen', 'AbortError');
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abbruch));
        await expect(stelleKiFrage(ANFRAGE)).rejects.toBe(abbruch);
    });
});
