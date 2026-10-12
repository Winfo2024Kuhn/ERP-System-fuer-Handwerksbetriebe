import { describe, expect, it } from 'vitest';
import { leseSseEreignisse, type SseEreignis } from './sseLeser';

const kodierer = new TextEncoder();

/** Antwort, deren Body genau in den angegebenen Blöcken ankommt. */
function antwortAus(bloecke: (string | Uint8Array)[]): Response {
    const strom = new ReadableStream<Uint8Array>({
        start(steuerung) {
            for (const block of bloecke) steuerung.enqueue(typeof block === 'string' ? kodierer.encode(block) : block);
            steuerung.close();
        },
    });
    return new Response(strom, { headers: { 'Content-Type': 'text/event-stream' } });
}

async function alle(antwort: Response): Promise<SseEreignis[]> {
    const ereignisse: SseEreignis[] = [];
    for await (const ereignis of leseSseEreignisse(antwort)) ereignisse.push(ereignis);
    return ereignisse;
}

describe('leseSseEreignisse', () => {
    it('liest Ereignisse im Format von Spring (ohne Leerzeichen nach dem Doppelpunkt)', async () => {
        const ereignisse = await alle(antwortAus([
            'event:schritt\ndata:{"text":"Passende Stellen im Programm gesucht"}\n\n'
            + 'event:antwort\ndata:{"reply":"Fertig"}\n\n',
        ]));
        expect(ereignisse).toEqual([
            { event: 'schritt', data: '{"text":"Passende Stellen im Programm gesucht"}' },
            { event: 'antwort', data: '{"reply":"Fertig"}' },
        ]);
    });

    it('entfernt genau ein Leerzeichen nach dem Doppelpunkt', async () => {
        const ereignisse = await alle(antwortAus(['event: schritt\ndata:  eingerückt\n\n']));
        expect(ereignisse).toEqual([{ event: 'schritt', data: ' eingerückt' }]);
    });

    it('setzt Ereignisse aus Blöcken zusammen, die mitten in der Zeile abreißen', async () => {
        const ereignisse = await alle(antwortAus(['eve', 'nt:schr', 'itt\nda', 'ta:{"text":"Sucht"}', '\n', '\n']));
        expect(ereignisse).toEqual([{ event: 'schritt', data: '{"text":"Sucht"}' }]);
    });

    it('setzt UTF-8-Zeichen zusammen, die über zwei Blöcke verteilt sind', async () => {
        const bytes = kodierer.encode('data:Prüfung für Würth\n\n');
        // „ü“ ist zwei Bytes lang – der Schnitt liegt genau dazwischen.
        const schnitt = bytes.indexOf(0xc3) + 1;
        const ereignisse = await alle(antwortAus([bytes.slice(0, schnitt), bytes.slice(schnitt)]));
        expect(ereignisse).toEqual([{ event: 'message', data: 'Prüfung für Würth' }]);
    });

    it('versteht CRLF, auch wenn \\r und \\n in verschiedenen Blöcken stehen', async () => {
        const ereignisse = await alle(antwortAus(['event:schritt\r\ndata:eins\r', '\n\r', '\nevent:antwort\r\ndata:zwei\r\n\r\n']));
        expect(ereignisse).toEqual([
            { event: 'schritt', data: 'eins' },
            { event: 'antwort', data: 'zwei' },
        ]);
    });

    it('versteht ein einzelnes \\r als Zeilenende', async () => {
        const ereignisse = await alle(antwortAus(['data:eins\r\rdata:zwei\r', 'event:x\r\r']));
        expect(ereignisse).toEqual([
            { event: 'message', data: 'eins' },
            { event: 'x', data: 'zwei' },
        ]);
    });

    it('überspringt Kommentare und Pings', async () => {
        const ereignisse = await alle(antwortAus([':ping\n\n: noch ein Kommentar\nevent:schritt\n:mittendrin\ndata:los\n\n']));
        expect(ereignisse).toEqual([{ event: 'schritt', data: 'los' }]);
    });

    it('verbindet mehrere data-Zeilen mit einem Zeilenumbruch', async () => {
        const ereignisse = await alle(antwortAus(['event:antwort\ndata:Zeile 1\ndata:\ndata:Zeile 3\n\n']));
        expect(ereignisse).toEqual([{ event: 'antwort', data: 'Zeile 1\n\nZeile 3' }]);
    });

    it('nennt ein Ereignis ohne event-Zeile „message“ und vergisst den Namen danach wieder', async () => {
        const ereignisse = await alle(antwortAus(['event:schritt\ndata:a\n\ndata:b\n\n']));
        expect(ereignisse).toEqual([
            { event: 'schritt', data: 'a' },
            { event: 'message', data: 'b' },
        ]);
    });

    it('gibt Ereignisse ohne data-Zeile nicht weiter und ignoriert id, retry und unbekannte Felder', async () => {
        const ereignisse = await alle(antwortAus(['event:leer\n\nid:7\nretry:1000\nunbekannt\ndata\n\n']));
        // „data“ ohne Doppelpunkt ist eine data-Zeile mit leerem Wert.
        expect(ereignisse).toEqual([{ event: 'message', data: '' }]);
    });

    it('gibt das letzte Ereignis auch ohne abschließende Leerzeile weiter', async () => {
        expect(await alle(antwortAus(['event:antwort\ndata:{"reply":"Hallo"}']))).toEqual([
            { event: 'antwort', data: '{"reply":"Hallo"}' },
        ]);
        expect(await alle(antwortAus(['event:antwort\ndata:{"reply":"Hallo"}\n']))).toEqual([
            { event: 'antwort', data: '{"reply":"Hallo"}' },
        ]);
        expect(await alle(antwortAus(['data:x\r']))).toEqual([{ event: 'message', data: 'x' }]);
    });

    it('liefert nichts bei leerem Strom, nur Kommentaren oder ohne Body', async () => {
        expect(await alle(antwortAus([]))).toEqual([]);
        expect(await alle(antwortAus([':ping\n\n:ping\n']))).toEqual([]);
        expect(await alle(antwortAus(['event:halb']))).toEqual([]);
        expect(await alle(new Response(null))).toEqual([]);
    });

    it('schließt den Strom, wenn der Aufrufer vorzeitig aufhört', async () => {
        let abgebrochen = false;
        const strom = new ReadableStream<Uint8Array>({
            start(steuerung) {
                steuerung.enqueue(kodierer.encode('data:eins\n\ndata:zwei\n\n'));
            },
            cancel() {
                abgebrochen = true;
            },
        });
        const antwort = new Response(strom);
        for await (const ereignis of leseSseEreignisse(antwort)) {
            expect(ereignis.data).toBe('eins');
            break;
        }
        expect(abgebrochen).toBe(true);
        // Der Strom ist wieder frei und schon geschlossen.
        expect(antwort.body?.locked).toBe(false);
    });

    it('reicht einen Fehler im Strom (z. B. Abbruch) weiter und gibt den Strom frei', async () => {
        const abbruch = new DOMException('Abgebrochen', 'AbortError');
        let steuerungMerken: ReadableStreamDefaultController<Uint8Array> | undefined;
        const strom = new ReadableStream<Uint8Array>({
            start(steuerung) {
                steuerungMerken = steuerung;
                steuerung.enqueue(kodierer.encode('data:eins\n\n'));
            },
        });
        const antwort = new Response(strom);
        const gelesen: string[] = [];
        await expect((async () => {
            for await (const ereignis of leseSseEreignisse(antwort)) {
                gelesen.push(ereignis.data);
                // Erst nach dem ersten Ereignis bricht die Verbindung ab.
                steuerungMerken?.error(abbruch);
            }
        })()).rejects.toBe(abbruch);
        expect(gelesen).toEqual(['eins']);
        expect(antwort.body?.locked).toBe(false);
    });
});
