import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    anbieterName,
    datenstandortText,
    entferneKiSchluessel,
    faehigkeitText,
    grundNichtMoeglich,
    ladeKiModelle,
    speichereKiFunktion,
    fehlendeFaehigkeiten,
    hatEigeneModelle,
    ladeKiEinstellungen,
    modellFeldwert,
    MODELLNAME_MAX,
    normalisiereKiEinstellungen,
    pruefeModellname,
    speichereKiModelle,
    speichereKiSchluessel,
    speichereKiWahl,
    stufeText,
    testeKiAnbieter,
} from './kiEinstellungenApi';
import { funktion, gemini, kiEinstellungen, mistral } from './kiTestdaten';
import { antwort, stubbeFetch } from '../telefon/telefonTestdaten';

afterEach(() => vi.unstubAllGlobals());

describe('kiEinstellungenApi – Anzeige-Texte', () => {
    it('übersetzt Fähigkeiten, Stufen und Datenstandort in Handwerker-Sprache und lässt Unbekanntes roh stehen', () => {
        expect(['TEXT', 'BILD', 'PDF', 'AUDIO', 'WERKZEUGE', 'WISSENSSUCHE'].map(faehigkeitText))
            .toEqual(['Text', 'Bilder', 'PDF-Dokumente', 'Sprache', 'Nachschlagen im Programm', 'Programm-Wissen']);
        expect(faehigkeitText('VIDEO')).toBe('VIDEO');
        expect(['SCHNELL', 'STANDARD', 'GRUENDLICH', 'TURBO'].map(stufeText)).toEqual(['Schnell', 'Standard', 'Gründlich', 'TURBO']);
        expect(datenstandortText('EU')).toBe('Daten in der EU');
        expect(datenstandortText('WELTWEIT')).toBe('Daten auch außerhalb der EU');
        expect(datenstandortText('MOND')).toBeNull();
    });

    it('nennt, was ein Anbieter nicht kann – auch Fähigkeiten, die nur eine Funktion braucht', () => {
        const stand = kiEinstellungen({ funktionen: [funktion({ braucht: ['VIDEO'] })] });
        expect(fehlendeFaehigkeiten(mistral(), stand)).toEqual(['AUDIO', 'WERKZEUGE', 'WISSENSSUCHE', 'VIDEO']);
        expect(fehlendeFaehigkeiten(gemini(), stand)).toEqual(['VIDEO']);
    });

    it('prüft Modellnamen wie der Server', () => {
        expect(pruefeModellname('')).toBeNull();
        expect(pruefeModellname('  ')).toBeNull();
        expect(pruefeModellname('mistral-medium-2508')).toBeNull();
        expect(pruefeModellname('models/gemini-test:1.0_a')).toBeNull();
        expect(pruefeModellname('mistral klein')).toBe('Der Modellname „mistral klein“ ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -');
        expect(pruefeModellname('<script>alert(1)</script>')).toMatch(/ist ungültig/);
        expect(pruefeModellname('a'.repeat(MODELLNAME_MAX + 1))).toMatch(/zu lang/);
    });

    it('zeigt das Standard-Modell als leeres Feld und erkennt eigene Modelle', () => {
        expect(modellFeldwert({ stufe: 'SCHNELL', modell: 'a', standardModell: 'a' })).toBe('');
        expect(modellFeldwert({ stufe: 'SCHNELL', modell: 'b', standardModell: 'a' })).toBe('b');
        expect(hatEigeneModelle(mistral())).toBe(false);
        expect(hatEigeneModelle(mistral({ modelle: [{ stufe: 'SCHNELL', modell: 'b', standardModell: 'a' }] }))).toBe(true);
    });

    it('findet den Anzeigenamen und fällt auf den Typ zurück', () => {
        const stand = kiEinstellungen();
        expect(anbieterName(stand, 'MISTRAL')).toBe('Mistral');
        expect(anbieterName(stand, 'NEU')).toBe('NEU');
    });

    it('macht unvollständige Antworten robust', () => {
        expect(normalisiereKiEinstellungen(null)).toEqual({
            anbieter: '', ersatzErlaubt: false, verschluesselungBereit: false, anbieterListe: [], funktionen: [],
        });
        const stand = normalisiereKiEinstellungen({ anbieter: 'MISTRAL', anbieterListe: [{ typ: 'MISTRAL' }], funktionen: [{ funktion: 'X' }] });
        expect(stand.anbieterListe[0].faehigkeiten).toEqual([]);
        expect(stand.anbieterListe[0].modelle).toEqual([]);
        expect(stand.funktionen[0].braucht).toEqual([]);
        expect(stand.funktionen[0].eigeneWahl).toBeNull();
        const mitWahl = normalisiereKiEinstellungen({ funktionen: [
            { funktion: 'A', eigeneWahl: { anbieter: 'MISTRAL', modell: 'm-1' } },
            { funktion: 'B', eigeneWahl: { anbieter: 'MISTRAL', modell: '' } },
            { funktion: 'C', eigeneWahl: { modell: 'x' } },
        ] });
        expect(mitWahl.funktionen.map((f) => f.eigeneWahl)).toEqual([
            { anbieter: 'MISTRAL', modell: 'm-1' }, { anbieter: 'MISTRAL', modell: null }, null,
        ]);
    });

    it('sagt, warum ein Anbieter eine Funktion nicht übernehmen kann', () => {
        const sprache = funktion({ braucht: ['AUDIO'] });
        expect(grundNichtMoeglich(mistral(), sprache)).toBe('Mistral kann das in diesem Programm noch nicht (Sprache).');
        expect(grundNichtMoeglich(gemini(), sprache)).toBeNull();
        expect(grundNichtMoeglich(mistral(), funktion({ braucht: ['AUDIO', 'WERKZEUGE'] })))
            .toBe('Mistral kann das in diesem Programm noch nicht (Sprache, Nachschlagen im Programm).');
    });
});

describe('kiEinstellungenApi – Server-Aufrufe', () => {
    it('schickt Wahl, Schlüssel und Modelle an die richtigen Adressen und gibt den neuen Stand zurück', async () => {
        const stand = kiEinstellungen();
        const mock = stubbeFetch(() => antwort(stand));

        await expect(ladeKiEinstellungen()).resolves.toEqual(stand);
        await speichereKiWahl('MISTRAL', false);
        await speichereKiSchluessel('MISTRAL', 'test-schluessel-123');
        await entferneKiSchluessel('GEMINI');
        await speichereKiModelle('A/B', { SCHNELL: '' });

        const aufrufe = mock.mock.calls.map(([url, init]) => [String(url), init?.method ?? 'GET', init?.body ?? null]);
        expect(aufrufe).toEqual([
            ['/api/settings/ki', 'GET', null],
            ['/api/settings/ki', 'PUT', JSON.stringify({ anbieter: 'MISTRAL', ersatzErlaubt: false })],
            ['/api/settings/ki/anbieter/MISTRAL/schluessel', 'PUT', JSON.stringify({ apiKey: 'test-schluessel-123' })],
            ['/api/settings/ki/anbieter/GEMINI/schluessel', 'DELETE', null],
            // Der Typ wird für die Adresse kodiert.
            ['/api/settings/ki/anbieter/A%2FB/modelle', 'PUT', JSON.stringify({ SCHNELL: '' })],
        ]);
    });

    it('speichert die Wahl je Funktion – ohne Anbieter wird auch kein Modell geschickt', async () => {
        const mock = stubbeFetch(() => antwort(kiEinstellungen()));
        await speichereKiFunktion('DOKUMENT_ANALYSE', 'MISTRAL', ' mistral-large-latest ');
        await speichereKiFunktion('DOKUMENT_ANALYSE', 'MISTRAL', '');
        await speichereKiFunktion('DOKUMENT_ANALYSE', null, 'egal');
        expect(mock.mock.calls.map(([url, init]) => [String(url), init?.method, init?.body])).toEqual([
            ['/api/settings/ki/funktionen/DOKUMENT_ANALYSE', 'PUT', JSON.stringify({ anbieter: 'MISTRAL', modell: 'mistral-large-latest' })],
            ['/api/settings/ki/funktionen/DOKUMENT_ANALYSE', 'PUT', JSON.stringify({ anbieter: 'MISTRAL', modell: null })],
            ['/api/settings/ki/funktionen/DOKUMENT_ANALYSE', 'PUT', JSON.stringify({ anbieter: null, modell: null })],
        ]);
    });

    it('lädt die Modellliste robust', async () => {
        stubbeFetch(() => antwort({ modelle: [{ anbieter: 'MISTRAL', modell: 'm-1', name: 'M 1', bilder: true }, { anbieter: 'X' }], hinweise: ['Hinweis', '', 3] }));
        await expect(ladeKiModelle()).resolves.toEqual({
            modelle: [{ anbieter: 'MISTRAL', modell: 'm-1', name: 'M 1', bilder: true }],
            hinweise: ['Hinweis'],
        });
        stubbeFetch(() => antwort(null));
        await expect(ladeKiModelle()).resolves.toEqual({ modelle: [], hinweise: [] });
        stubbeFetch(() => antwort({ message: 'Mistral antwortet nicht.' }, 502));
        await expect(ladeKiModelle()).rejects.toThrow('Mistral antwortet nicht.');
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('offline'))));
        await expect(ladeKiModelle()).rejects.toThrow('Verbindung zum Server fehlgeschlagen.');
    });

    it('wirft die Server-Meldung oder einen verständlichen Ersatztext', async () => {
        stubbeFetch(() => antwort({ message: 'Diesen KI-Anbieter gibt es nicht.' }, 400));
        await expect(speichereKiWahl('X', true)).rejects.toThrow('Diesen KI-Anbieter gibt es nicht.');

        stubbeFetch(() => new Response('', { status: 500 }));
        await expect(ladeKiEinstellungen()).rejects.toThrow('KI-Einstellungen konnten nicht geladen werden.');

        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('offline'))));
        await expect(ladeKiEinstellungen()).rejects.toThrow('Verbindung zum Server fehlgeschlagen.');
    });

    it('der Verbindungstest wirft nie und liefert immer eine Meldung', async () => {
        stubbeFetch(() => antwort({ success: true, message: 'Verbindung klappt.' }));
        await expect(testeKiAnbieter('MISTRAL', null)).resolves.toEqual({ success: true, message: 'Verbindung klappt.' });

        stubbeFetch(() => antwort({ success: false }));
        await expect(testeKiAnbieter('MISTRAL', 'x')).resolves.toEqual({ success: false, message: 'Die Verbindung klappt nicht.' });

        stubbeFetch(() => antwort({ success: true, message: ' ' }));
        await expect(testeKiAnbieter('MISTRAL', 'x')).resolves.toEqual({ success: true, message: 'Verbindung klappt.' });

        stubbeFetch(() => antwort({ message: 'Unbekannter Anbieter.' }, 400));
        await expect(testeKiAnbieter('X', null)).resolves.toEqual({ success: false, message: 'Unbekannter Anbieter.' });

        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('offline'))));
        await expect(testeKiAnbieter('MISTRAL', null)).resolves.toEqual({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
    });
});
