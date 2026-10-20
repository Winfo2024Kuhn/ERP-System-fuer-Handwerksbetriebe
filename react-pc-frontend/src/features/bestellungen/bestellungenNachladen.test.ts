import { describe, expect, it } from 'vitest';
import type { KettenDokument } from './bestellungenListe';
import {
    SEITEN_GROESSE,
    ausblendenOptimistisch,
    einblendenOptimistisch,
    gesamtJeGruppe,
    inLadeBloecke,
    ladeStand,
    normalisiereUebersicht,
    uebersichtUrl,
    wirksameSuche,
    type UebersichtListen,
} from './bestellungenNachladen';

const HEUTE = new Date(2026, 9, 5);

interface TestKette { id: string; lieferantName: string | null; dokumente: KettenDokument[] }

function kette(id: string, datum: string | null = '2026-10-01'): TestKette {
    return {
        id, lieferantName: 'Max Mustermann GmbH',
        dokumente: [{ typ: 'ANGEBOT', dokumentNummer: id, dokumentDatum: datum, betragBrutto: null, dateiname: `${id}.pdf` }],
    };
}

function daten(teil: Partial<UebersichtListen<TestKette>> = {}): UebersichtListen<TestKette> {
    return { offeneAnfragen: [], laufendeBestellungen: [], abgeschlossen: [], zugeordnet: [], ausgeblendet: [], ...teil };
}

describe('uebersichtUrl', () => {
    it('schickt immer die Anzahl, die Suche nur, wenn etwas drinsteht', () => {
        expect(uebersichtUrl({ anzahl: 25, suche: '' })).toBe('/api/bestellungen-uebersicht?anzahl=25');
        expect(uebersichtUrl({ anzahl: 50, suche: '   ' })).toBe('/api/bestellungen-uebersicht?anzahl=50');
        // ein Zeichen gilt wie keine Suche
        expect(uebersichtUrl({ anzahl: 25, suche: ' a ' })).toBe('/api/bestellungen-uebersicht?anzahl=25');
    });

    it('wirksameSuche trimmt und verwirft zu Kurzes', () => {
        expect(wirksameSuche('  x ')).toBe('');
        expect(wirksameSuche(' ab ')).toBe('ab');
    });

    it('kodiert den Suchbegriff und schneidet Leerzeichen ab', () => {
        const url = new URL(uebersichtUrl({ anzahl: 25, suche: ' Müller & Söhne 1.234,50 € ' }), 'http://localhost');
        expect(url.searchParams.get('suche')).toBe('Müller & Söhne 1.234,50 €');
        expect(url.searchParams.get('anzahl')).toBe('25');
    });
});

describe('normalisiereUebersicht', () => {
    it('macht aus fehlenden oder kaputten Listen leere Listen', () => {
        expect(normalisiereUebersicht(null)).toEqual(daten({ gesamt: null }));
        expect(normalisiereUebersicht({ offeneAnfragen: 'kaputt', gesamt: 3 })).toEqual(daten({ gesamt: null }));
    });

    it('übernimmt Listen und gesamt', () => {
        const gesamt = { offeneAnfragen: 1, offeneAnfragenAktuell: 1, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 0 };
        const ergebnis = normalisiereUebersicht<TestKette>({ offeneAnfragen: [kette('a')], gesamt });
        expect(ergebnis.offeneAnfragen).toHaveLength(1);
        expect(ergebnis.gesamt).toEqual(gesamt);
    });
});

describe('gesamtJeGruppe', () => {
    it('nimmt die Zahlen vom Server', () => {
        const gesamt = { offeneAnfragen: 40, offeneAnfragenAktuell: 12, laufendeBestellungen: 140, laufendeBestellungenAktuell: 140, abgeschlossen: 3, zugeordnet: 500, ausgeblendet: 9 };
        expect(gesamtJeGruppe(daten({ gesamt }), HEUTE)).toEqual(gesamt);
    });

    it('zählt selbst, wenn der Server nichts oder Unsinn schickt', () => {
        expect(gesamtJeGruppe(daten({ laufendeBestellungen: [kette('l1')] }), HEUTE).laufendeBestellungen).toBe(1);
        const d = daten({
            offeneAnfragen: [kette('neu'), kette('alt', '2025-01-01'), kette('ohne', null)],
            zugeordnet: [kette('z')],
            gesamt: { offeneAnfragen: -1, offeneAnfragenAktuell: Number.NaN } as never,
        });
        expect(gesamtJeGruppe(d, HEUTE)).toEqual({
            offeneAnfragen: 3, offeneAnfragenAktuell: 2, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 0, zugeordnet: 1, ausgeblendet: 0,
        });
    });
});

describe('inLadeBloecke', () => {
    const alle = Array.from({ length: 60 }, (_, i) => ({ id: `k${i}` }));

    it('teilt einen Teil nach Lade-Runde (je 25)', () => {
        expect(inLadeBloecke(alle).map(b => b.length)).toEqual([SEITEN_GROESSE, SEITEN_GROESSE, 10]);
        expect(inLadeBloecke(alle, 40).map(b => b[0].id)).toEqual(['k0', 'k40']);
    });

    it('liefert für leere Listen keine Blöcke', () => {
        expect(inLadeBloecke([])).toEqual([]);
    });
});

describe('ladeStand', () => {
    const aktuell = (n: number) => Array.from({ length: n }, (_, i) => kette(`a${i}`));
    const alt = (n: number) => Array.from({ length: n }, (_, i) => kette(`x${i}`, '2025-01-01'));
    const gesamt = (laufend: number, laufendAktuell: number, zugeordnet = 0) => ({
        offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: laufend, laufendeBestellungenAktuell: laufendAktuell,
        abgeschlossen: 0, zugeordnet, ausgeblendet: 0,
    });

    it('einteilige Gruppen: vollständig, sobald alles geladen ist', () => {
        expect(ladeStand(daten({ zugeordnet: aktuell(25), gesamt: gesamt(0, 0, 40) }), 'zugeordnet', HEUTE))
            .toEqual({ geladen: 25, gesamt: 40, vollstaendig: false, alt: null });
        expect(ladeStand(daten({ zugeordnet: aktuell(3), gesamt: gesamt(0, 0, 3) }), 'zugeordnet', HEUTE).vollstaendig).toBe(true);
    });

    it('laufende Bestellungen: fehlt nur bei den alten etwas, ist trotzdem mehr zu laden', () => {
        const stand = ladeStand(daten({ laufendeBestellungen: [...aktuell(10), ...alt(25)], gesamt: gesamt(70, 10) }), 'laufendeBestellungen', HEUTE);
        expect(stand).toEqual({ geladen: 35, gesamt: 70, vollstaendig: false, alt: { geladen: 25, gesamt: 60 } });
    });

    it('fehlt nur bei den aktuellen etwas, ist ebenfalls mehr zu laden', () => {
        const stand = ladeStand(daten({ offeneAnfragen: [...aktuell(25), ...alt(2)], gesamt: { ...gesamt(0, 0), offeneAnfragen: 32, offeneAnfragenAktuell: 30 } }), 'offeneAnfragen', HEUTE);
        expect(stand.vollstaendig).toBe(false);
        expect(stand.alt).toEqual({ geladen: 2, gesamt: 2 });
    });

    it('ist vollständig, wenn beide Teile ganz da sind (auch ohne gesamt vom Server)', () => {
        expect(ladeStand(daten({ laufendeBestellungen: [...aktuell(3), ...alt(2)] }), 'laufendeBestellungen', HEUTE))
            .toEqual({ geladen: 5, gesamt: 5, vollstaendig: true, alt: { geladen: 2, gesamt: 2 } });
    });
});

describe('ausblendenOptimistisch', () => {
    it('verschiebt Ketten nach „Ausgeblendet“ und zieht die Zähler mit', () => {
        const aktuell = kette('a1');
        const alt = kette('a2', '2025-01-01');
        const erledigt = kette('z1');
        const d = daten({
            offeneAnfragen: [aktuell, alt, kette('a3')],
            abgeschlossen: [kette('r1')],
            zugeordnet: [erledigt],
            ausgeblendet: [kette('x1')],
            gesamt: { offeneAnfragen: 50, offeneAnfragenAktuell: 20, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 1, zugeordnet: 80, ausgeblendet: 5 },
        });
        const neu = ausblendenOptimistisch(d, [aktuell, alt, erledigt], HEUTE);
        expect(neu.offeneAnfragen.map(k => k.id)).toEqual(['a3']);
        expect(neu.zugeordnet).toEqual([]);
        expect(neu.abgeschlossen.map(k => k.id)).toEqual(['r1']);
        expect(neu.ausgeblendet.map(k => k.id)).toEqual(['a1', 'a2', 'z1', 'x1']);
        expect(neu.gesamt).toEqual({ offeneAnfragen: 48, offeneAnfragenAktuell: 19, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 1, zugeordnet: 79, ausgeblendet: 8 });
    });

    it('senkt bei laufenden Bestellungen auch den Zähler der aktuellen, aber nur für aktuelle', () => {
        const neuK = kette('l1');
        const altK = kette('l2', '2025-01-01');
        const d = daten({ laufendeBestellungen: [neuK, altK], gesamt: { offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: 10, laufendeBestellungenAktuell: 4, abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 0 } });
        const neu = ausblendenOptimistisch(d, [neuK, altK], HEUTE);
        expect(neu.gesamt?.laufendeBestellungen).toBe(8);
        expect(neu.gesamt?.laufendeBestellungenAktuell).toBe(3);
    });

    it('zählt schon ausgeblendete nicht doppelt und geht nie unter null', () => {
        const x = kette('x1');
        const d = daten({ ausgeblendet: [x], laufendeBestellungen: [kette('l1')], gesamt: { offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 1 } });
        const neu = ausblendenOptimistisch(d, [x, d.laufendeBestellungen[0]], HEUTE);
        expect(neu.gesamt?.ausgeblendet).toBe(2);
        expect(neu.gesamt?.laufendeBestellungen).toBe(0);
        expect(neu.gesamt?.laufendeBestellungenAktuell).toBe(0);
        expect(neu.ausgeblendet.map(k => k.id)).toEqual(['x1', 'l1']);
    });
});

describe('einblendenOptimistisch', () => {
    it('nimmt die Kette aus „Ausgeblendet“ und senkt den Zähler', () => {
        const x = kette('x1');
        const d = daten({ ausgeblendet: [x, kette('x2')], gesamt: { offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 30 } });
        const neu = einblendenOptimistisch(d, x, HEUTE);
        expect(neu.ausgeblendet.map(k => k.id)).toEqual(['x2']);
        expect(neu.gesamt?.ausgeblendet).toBe(29);
    });

    it('lässt den Zähler stehen, wenn die Kette gar nicht geladen war', () => {
        const d = daten({ ausgeblendet: [], gesamt: { offeneAnfragen: 0, offeneAnfragenAktuell: 0, laufendeBestellungen: 0, laufendeBestellungenAktuell: 0, abgeschlossen: 0, zugeordnet: 0, ausgeblendet: 0 } });
        expect(einblendenOptimistisch(d, kette('x9'), HEUTE).gesamt?.ausgeblendet).toBe(0);
    });
});
