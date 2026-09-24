/**
 * Vitest-Suite fuer den generischen Aenderungsverlauf (Rueckgaengig/Wiederholen-Kern).
 *
 * Rein funktional, kein React, keine personenbezogenen Daten (Probe-Typ mit
 * text/zahl) - DSGVO insofern nicht einschlaegig fuer dieses Modul.
 */
import { describe, it, expect } from 'vitest';
import {
    BUENDEL_PAUSE_MS,
    MAX_SCHRITTE,
    feldAenderungen,
    istLeer,
    leererVerlauf,
    nachherWerte,
    rueckgaengig,
    schrittAufnehmen,
    tiefGleich,
    verlaufGeleert,
    wiederholen,
    type Verlauf,
} from './aenderungsVerlauf';

type Probe = { text: string; zahl: number };

const START = 1_726_000_000_000; // fester, beliebiger Zeitpunkt (ms)

function neuerVerlauf(): Verlauf<Probe, never> {
    return leererVerlauf<Probe, never>();
}

describe('leererVerlauf', () => {
    it('startet ohne Schritte, ohne Wiederholbares, mit Buendeln erlaubt', () => {
        const v = neuerVerlauf();
        expect(v.schritte).toEqual([]);
        expect(v.wiederholbar).toEqual([]);
        expect(v.buendelnErlaubt).toBe(true);
    });
});

describe('tiefGleich', () => {
    it('(p) vergleicht Objekte und Arrays strukturell und kurzschliesst bei Referenzgleichheit', () => {
        expect(tiefGleich({ a: 1, liste: [1, 2] }, { a: 1, liste: [1, 2] })).toBe(true);
        expect(tiefGleich([1, 2, 3], [1, 2, 3])).toBe(true);
        expect(tiefGleich([1, 2, 3], [1, 2])).toBe(false); // unterschiedliche Laenge
        expect(tiefGleich({ a: 1 }, { a: 2 })).toBe(false);

        // Referenzgleichheit kurzschliesst: NaN ist nur ueber den fruehen
        // `a === b`-Ausstieg "gleich sich selbst" - eine strukturelle Rekursion
        // wuerde bei NaN !== NaN scheitern.
        const mitNaN = { wert: NaN };
        expect(tiefGleich(mitNaN, mitNaN)).toBe(true);
        expect(tiefGleich(mitNaN, { wert: NaN })).toBe(false);
    });
});

describe('feldAenderungen / istLeer / nachherWerte', () => {
    it('liefert nur tatsaechlich geaenderte Felder', () => {
        const vorher: Probe = { text: 'a', zahl: 1 };
        const aenderungen = feldAenderungen(vorher, { text: 'b', zahl: 1 });
        expect(aenderungen).toEqual({ text: { vorher: 'a', nachher: 'b' } });
        expect(aenderungen.zahl).toBeUndefined();
    });

    it('istLeer ist true, wenn kein Feld sich geaendert hat', () => {
        const vorher: Probe = { text: 'a', zahl: 1 };
        expect(istLeer(feldAenderungen(vorher, { text: 'a' }))).toBe(true);
        expect(istLeer(feldAenderungen(vorher, { text: 'b' }))).toBe(false);
    });

    it('nachherWerte extrahiert die nachher-Werte', () => {
        const aenderungen = feldAenderungen({ text: 'a', zahl: 1 }, { text: 'b', zahl: 2 });
        expect(nachherWerte(aenderungen)).toEqual({ text: 'b', zahl: 2 });
    });
});

describe('schrittAufnehmen', () => {
    it('(a) legt aus feldAenderungen einen Schritt an', () => {
        const vorher: Probe = { text: '', zahl: 0 };
        const aenderungen = feldAenderungen(vorher, { text: 'a' });
        const v1 = schrittAufnehmen(neuerVerlauf(), { bezeichnung: 'Text geändert', aenderungen }, START);

        expect(v1.schritte).toHaveLength(1);
        expect(v1.schritte[0]).toMatchObject({
            id: 1,
            bezeichnung: 'Text geändert',
            aenderungen: { text: { vorher: '', nachher: 'a' } },
            zeitpunkt: START,
        });
        expect(v1.naechsteId).toBe(2);
    });

    it('(b) ein unveraenderter Wert (istLeer) liefert dieselbe Verlauf-Referenz', () => {
        const vorher: Probe = { text: 'a', zahl: 1 };
        const aenderungen = feldAenderungen(vorher, { text: 'a' });
        expect(istLeer(aenderungen)).toBe(true);

        const v0 = neuerVerlauf();
        const v1 = schrittAufnehmen(v0, { bezeichnung: 'Text geändert', aenderungen }, START);
        expect(v1).toBe(v0);
    });

    it('(c) kappt bei MAX_SCHRITTE und wirft den aeltesten Schritt raus', () => {
        let v = neuerVerlauf();
        for (let i = 0; i < 21; i++) {
            v = schrittAufnehmen(v, {
                bezeichnung: `Schritt ${i}`,
                aenderungen: feldAenderungen({ text: `t${i}`, zahl: i }, { text: `t${i + 1}` }),
                buendelSchluessel: null,
            }, START + i * (BUENDEL_PAUSE_MS + 1));
        }
        expect(v.schritte).toHaveLength(MAX_SCHRITTE);
        expect(v.schritte[0].bezeichnung).toBe('Schritt 1'); // "Schritt 0" (aeltester) ist raus
        expect(v.schritte[v.schritte.length - 1].bezeichnung).toBe('Schritt 20');
    });

    it('(d) buendelt bei gleichem Schluessel innerhalb der Pause (vorher des ersten, nachher des letzten)', () => {
        const schluessel = 'feld:text';
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: '', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: schluessel,
        }, START);
        v = schrittAufnehmen(v, {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: schluessel,
        }, START + 1000);

        expect(v.schritte).toHaveLength(1);
        expect(v.schritte[0].aenderungen.text).toEqual({ vorher: '', nachher: 'ab' });
    });

    it('(e) buendelt NICHT ausserhalb der Pause (Abstand >= BUENDEL_PAUSE_MS)', () => {
        const schluessel = 'feld:text';
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: '', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: schluessel,
        }, START);
        v = schrittAufnehmen(v, {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: schluessel,
        }, START + 2500);

        expect(v.schritte).toHaveLength(2);
    });

    it('(f) buendelt NICHT bei unterschiedlichem Schluessel', () => {
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'A',
            aenderungen: feldAenderungen({ text: '', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: 'a',
        }, START);
        v = schrittAufnehmen(v, {
            bezeichnung: 'B',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: 'b',
        }, START + 500);

        expect(v.schritte).toHaveLength(2);
    });

    it('(g) buendelSchluessel: null buendelt nie', () => {
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'A',
            aenderungen: feldAenderungen({ text: '', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: null,
        }, START);
        v = schrittAufnehmen(v, {
            bezeichnung: 'B',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: null,
        }, START + 500);

        expect(v.schritte).toHaveLength(2);
    });

    it('(h) buendelnErlaubt=false nach rueckgaengig/wiederholen verhindert Buendeln des naechsten Schritts', () => {
        const schluessel = 'feld:text';
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: '', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: schluessel,
        }, START);

        v = rueckgaengig(v)!.verlauf;
        expect(v.buendelnErlaubt).toBe(false);

        v = wiederholen(v)!.verlauf;
        expect(v.schritte).toHaveLength(1);
        expect(v.buendelnErlaubt).toBe(false);

        v = schrittAufnehmen(v, {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: schluessel,
        }, START + 500);

        expect(v.schritte).toHaveLength(2); // kein Buendeln trotz gleichem Schluessel + Pause
        expect(v.buendelnErlaubt).toBe(true); // danach wieder erlaubt
    });

    it('(i) ein Buendel, das per Saldo nichts aendert (tippen + zuruecklöschen), faellt komplett weg', () => {
        const schluessel = 'feld:text';
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'ab' }),
            buendelSchluessel: schluessel,
        }, START);
        expect(v.schritte).toHaveLength(1);

        v = schrittAufnehmen(v, {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'ab', zahl: 0 }, { text: 'a' }),
            buendelSchluessel: schluessel,
        }, START + 500);

        expect(v.schritte).toHaveLength(0);
    });
});

describe('rueckgaengig / wiederholen', () => {
    it('(j) rueckgaengig liefert die vorher-Werte und schiebt den Schritt nach wiederholbar', () => {
        const vorher: Probe = { text: 'a', zahl: 1 };
        const v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen(vorher, { text: 'b' }),
        }, START);

        const sprung = rueckgaengig(v);
        expect(sprung).not.toBeNull();
        expect(sprung!.werte).toEqual({ text: 'a' });
        expect(sprung!.verlauf.schritte).toHaveLength(0);
        expect(sprung!.verlauf.wiederholbar).toHaveLength(1);
        expect(sprung!.verlauf.wiederholbar[0].aenderungen.text).toEqual({ vorher: 'a', nachher: 'b' });
    });

    it('rueckgaengig auf einem leeren Verlauf liefert null', () => {
        expect(rueckgaengig(neuerVerlauf())).toBeNull();
    });

    it('(k) wiederholen kehrt rueckgaengig um', () => {
        const vorher: Probe = { text: 'a', zahl: 1 };
        const v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen(vorher, { text: 'b' }),
        }, START);

        const nachRueckgaengig = rueckgaengig(v)!;
        const nachWiederholen = wiederholen(nachRueckgaengig.verlauf);

        expect(nachWiederholen).not.toBeNull();
        expect(nachWiederholen!.werte).toEqual({ text: 'b' });
        expect(nachWiederholen!.verlauf.schritte).toHaveLength(1);
        expect(nachWiederholen!.verlauf.wiederholbar).toHaveLength(0);
    });

    it('wiederholen auf einem Verlauf ohne Wiederholbares liefert null', () => {
        expect(wiederholen(neuerVerlauf())).toBeNull();
    });

    it('(l) eine neue Aenderung nach rueckgaengig leert wiederholbar', () => {
        let v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 1 }, { text: 'b' }),
        }, START);
        v = rueckgaengig(v)!.verlauf;
        expect(v.wiederholbar).toHaveLength(1);

        v = schrittAufnehmen(v, {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 1 }, { text: 'c' }),
        }, START + 5000);

        expect(v.wiederholbar).toHaveLength(0);
    });

    it('(m) rueckgaengig(3): je Feld gewinnt der aelteste der zurueckgenommenen Schritte', () => {
        let v = neuerVerlauf();
        // Schritt A (aeltester der 3): text 1 -> 2
        v = schrittAufnehmen(v, {
            bezeichnung: 'A',
            aenderungen: feldAenderungen({ text: '1', zahl: 0 }, { text: '2' }),
            buendelSchluessel: null,
        }, START);
        // Schritt B (mittlerer): zahl 5 -> 6
        v = schrittAufnehmen(v, {
            bezeichnung: 'B',
            aenderungen: feldAenderungen({ text: '2', zahl: 5 }, { zahl: 6 }),
            buendelSchluessel: null,
        }, START + 3000);
        // Schritt C (neuester der 3): text 2 -> 3
        v = schrittAufnehmen(v, {
            bezeichnung: 'C',
            aenderungen: feldAenderungen({ text: '2', zahl: 6 }, { text: '3' }),
            buendelSchluessel: null,
        }, START + 6000);

        const sprung = rueckgaengig(v, 3);
        expect(sprung).not.toBeNull();
        expect(sprung!.werte).toEqual({ text: '1', zahl: 5 });
        expect(sprung!.verlauf.schritte).toHaveLength(0);
        expect(sprung!.schritte.map(s => s.bezeichnung)).toEqual(['C', 'B', 'A']); // neuester zuerst
    });

    it('(n) ist feldgenau: ein Schritt, der nur text aendert, liefert kein zahl in werte', () => {
        const v = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 42 }, { text: 'b' }),
        }, START);

        const sprung = rueckgaengig(v);
        expect(sprung!.werte).toEqual({ text: 'a' });
        expect(sprung!.werte).not.toHaveProperty('zahl');
    });

    it('wiederholen(2): je Feld gewinnt der neueste der wiederhergestellten Schritte', () => {
        let v = neuerVerlauf();
        v = schrittAufnehmen(v, {
            bezeichnung: 'A',
            aenderungen: feldAenderungen({ text: '1', zahl: 0 }, { text: '2' }),
            buendelSchluessel: null,
        }, START);
        v = schrittAufnehmen(v, {
            bezeichnung: 'B',
            aenderungen: feldAenderungen({ text: '2', zahl: 0 }, { text: '3' }),
            buendelSchluessel: null,
        }, START + 3000);

        v = rueckgaengig(v, 2)!.verlauf;
        const sprung = wiederholen(v, 2);

        expect(sprung).not.toBeNull();
        expect(sprung!.werte).toEqual({ text: '3' }); // B (neuester) gewinnt gegenueber A
        expect(sprung!.schritte.map(s => s.bezeichnung)).toEqual(['A', 'B']); // Ausfuehrungsreihenfolge
    });
});

describe('verlaufGeleert', () => {
    it('(o) gibt bei leerem Verlauf dieselbe Referenz zurueck', () => {
        const v0 = neuerVerlauf();
        expect(verlaufGeleert(v0)).toBe(v0);
    });

    it('leert schritte und wiederholbar bei nicht-leerem Verlauf', () => {
        const v1 = schrittAufnehmen(neuerVerlauf(), {
            bezeichnung: 'Text geändert',
            aenderungen: feldAenderungen({ text: 'a', zahl: 0 }, { text: 'b' }),
        }, START);

        const geleert = verlaufGeleert(v1);
        expect(geleert).not.toBe(v1);
        expect(geleert.schritte).toHaveLength(0);
        expect(geleert.wiederholbar).toHaveLength(0);
    });
});
