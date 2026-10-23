import { describe, expect, it } from 'vitest';
import {
    GRUND, MAX_BILD_BYTES, einfuegenTaste, OBERSTE_EBENE, anzahlText, baueBaum, beschreibungsVorschlag, dokumentTypText,
    einheitJe, einheitKurz, einheitName, ersteDatei, formatDatum, formatPreis, formatZahl, hatUnterordner,
    jahresOptionen, kinderVon, leistungPasst, leistungenImOrdner, leistungenWandernHinweis, leistungsZaehler,
    leistungZielSperre, nachfahren, normalisiereLeistung, normalisiereOrdner, ordnerKette, ordnerZielSperre,
    passtZurSuche, pfadText, projekteSatz, pruefeBilddatei, relativerPfad, sichtbareZeilen, suche,
    umzugZielSperre, weitereAnzahl, zaehlerAriaLabel, zieleFuerLeistung, zieleFuerOrdnerVerschieben,
    zieleFuerUmzug,
} from './ordnerLogik';
import type { Leistung, Ordner } from './typen';
import type { Baum } from './ordnerLogik';

function ordner(id: number, name: string, parentId: number | null, extra: Partial<Ordner> = {}): Ordner {
    return {
        id, name, parentId, bildUrl: null, beschreibung: '', einheit: 'STUECK', einheitAnzeige: 'Stück',
        projektAnzahl: 0, ...extra,
    };
}
function leistung(id: number, name: string, ordnerId: number | null, beschreibung = ''): Leistung {
    return { id, name, beschreibung, preis: 10, einheit: 'STUECK', ordnerId };
}

// Metallbau › Geländer › (Edelstahl, Stahl verzinkt) ; Metallbau › Treppen ; Holzbau (leer)
const ORDNER = [
    ordner(1, 'Metallbau', null, { beschreibung: '<p>Metall</p>' }),
    ordner(2, 'Geländer', 1, { beschreibung: '<p>Geländer allgemein</p>' }),
    ordner(3, 'Edelstahl', 2, { beschreibung: '<p></p>' }),
    ordner(4, 'Stahl verzinkt', 2),
    ordner(5, 'Treppen', 1),
    ordner(6, 'Holzbau', null),
];
const LEISTUNGEN = [
    leistung(10, 'Geländer V2A', 3, '<p>Handlauf rund</p>'),
    leistung(11, 'Geländer Stahl', 4),
    leistung(12, 'Wangentreppe', 5),
    leistung(13, 'Ohne Ordner', null),
];
const baum = baueBaum(ORDNER);
const zaehler = leistungsZaehler(baum, LEISTUNGEN);

describe('Umwandeln der API-Antworten', () => {
    it('liest Ordner mit Einheit als Objekt oder Enum-Text', () => {
        expect(normalisiereOrdner({
            id: '7', bezeichnung: ' Geländer ', parentId: '1', bildUrl: '/bild.png', beschreibung: '<p>x</p>',
            verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' }, projektAnzahl: 3,
        })).toEqual({
            id: 7, name: 'Geländer', parentId: 1, bildUrl: '/bild.png', beschreibung: '<p>x</p>',
            einheit: 'LAUFENDE_METER', einheitAnzeige: 'Laufende Meter', projektAnzahl: 3,
        });
        const enumText = normalisiereOrdner({ id: 8, bezeichnung: 'Tore', verrechnungseinheit: 'QUADRATMETER' });
        expect(enumText.einheit).toBe('QUADRATMETER');
        expect(enumText.einheitAnzeige).toBe('Quadratmeter');
        expect(enumText.parentId).toBeNull();
        expect(enumText.projektAnzahl).toBe(0);
        expect(enumText.beschreibung).toBe('');
    });

    it('fällt bei fehlendem Namen auf Pfad oder „Ordner“ zurück und kennt unbekannte Einheiten nicht', () => {
        expect(normalisiereOrdner({ id: 1, bezeichnung: '', pfad: 'A / B' }).name).toBe('A / B');
        const leer = normalisiereOrdner({ id: 1, verrechnungseinheit: null, parentId: '' });
        expect(leer.name).toBe('Ordner');
        expect(leer.einheit).toBeNull();
        expect(leer.einheitAnzeige).toBeNull();
        expect(leer.parentId).toBeNull();
        expect(normalisiereOrdner({ id: 1, parentId: 'kaputt' }).parentId).toBeNull();
        expect(normalisiereOrdner({ id: 1, verrechnungseinheit: { name: 'STUECK' } }).einheitAnzeige).toBe('Stück');
    });

    it('liest Leistungen mit Einheit als Text oder Objekt', () => {
        expect(normalisiereLeistung({ id: '5', name: 'Geländer', description: '<p>a</p>', price: '12.5', unit: 'STUECK', folderId: '3' }))
            .toEqual({ id: 5, name: 'Geländer', beschreibung: '<p>a</p>', preis: 12.5, einheit: 'STUECK', ordnerId: 3 });
        expect(normalisiereLeistung({ id: 6, name: 'X', unit: { name: 'KILOGRAMM' }, price: 'abc' }))
            .toEqual({ id: 6, name: 'X', beschreibung: '', preis: 0, einheit: 'KILOGRAMM', ordnerId: null });
        expect(normalisiereLeistung({ id: 7, name: 'Y', unit: null }).einheit).toBe('');
    });

    it('bringt Anzeigenamen der Einheit auf den Enum-Namen', () => {
        expect(einheitName('Laufende Meter')).toBe('LAUFENDE_METER');
        expect(einheitName('STUECK')).toBe('STUECK');
        expect(einheitName('Fass')).toBeNull();
        expect(einheitName(null)).toBeNull();
        expect(einheitName(undefined)).toBeNull();
    });
});

describe('Baum', () => {
    it('sortiert Kinder nach Name und hängt verwaiste Ordner an die oberste Ebene', () => {
        expect(kinderVon(baum, null).map((o) => o.name)).toEqual(['Holzbau', 'Metallbau']);
        expect(kinderVon(baum, 2).map((o) => o.name)).toEqual(['Edelstahl', 'Stahl verzinkt']);
        expect(kinderVon(baum, 99)).toEqual([]);
        const verwaist = baueBaum([ordner(1, 'Allein', 42)]);
        expect(kinderVon(verwaist, null).map((o) => o.id)).toEqual([1]);
    });

    it('kennt Unterordner, Nachfahren, Kette und Pfad', () => {
        expect(hatUnterordner(baum, 2)).toBe(true);
        expect(hatUnterordner(baum, 3)).toBe(false);
        expect([...nachfahren(baum, 1)].sort()).toEqual([2, 3, 4, 5]);
        expect(nachfahren(baum, 6).size).toBe(0);
        expect(ordnerKette(baum, 3).map((o) => o.id)).toEqual([1, 2, 3]);
        expect(ordnerKette(baum, 99)).toEqual([]);
        expect(pfadText(baum, 3)).toBe('Metallbau › Geländer › Edelstahl');
        expect(pfadText(baum, 3, ' / ')).toBe('Metallbau / Geländer / Edelstahl');
        expect(relativerPfad(baum, 1, 3)).toBe('Geländer › Edelstahl');
        expect(relativerPfad(baum, 3, 3)).toBe('');
    });

    it('übersteht zyklische Daten ohne Endlosschleife', () => {
        // a → b → a: kommt nur bei kaputten Daten vor, darf die Seite aber nicht einfrieren.
        const zyklus: Baum = {
            byId: new Map([[1, ordner(1, 'A', 2)], [2, ordner(2, 'B', 1)]]),
            kinder: new Map([[1, [ordner(2, 'B', 1)]], [2, [ordner(1, 'A', 2)]]]),
        };
        expect(ordnerKette(zyklus, 1).map((o) => o.id)).toEqual([2, 1]);
        expect([...nachfahren(zyklus, 1)]).toEqual([2]);
        expect(sichtbareZeilen({ ...zyklus, kinder: new Map([[null, [ordner(1, 'A', null)]], [1, [ordner(1, 'A', null)]]]) }, new Set([1])))
            .toHaveLength(1);
    });

    it('zählt Leistungen direkt und inkl. Unterordner', () => {
        expect(zaehler.get(1)).toEqual({ direkt: 0, gesamt: 3 });
        expect(zaehler.get(2)).toEqual({ direkt: 0, gesamt: 2 });
        expect(zaehler.get(3)).toEqual({ direkt: 1, gesamt: 1 });
        expect(zaehler.get(6)).toEqual({ direkt: 0, gesamt: 0 });
    });

    it('liefert Leistungen eines Ordners inkl. Unterordner, sortiert', () => {
        expect(leistungenImOrdner(baum, LEISTUNGEN, 2).map((l) => l.id)).toEqual([11, 10]);
        expect(leistungenImOrdner(baum, LEISTUNGEN, 5).map((l) => l.id)).toEqual([12]);
    });

    it('zeigt nur aufgeklappte Zweige und beachtet einen Filter', () => {
        expect(sichtbareZeilen(baum, new Set()).map((z) => z.ordner.id)).toEqual([6, 1]);
        const offen = sichtbareZeilen(baum, new Set([1, 2]));
        expect(offen.map((z) => [z.ordner.id, z.ebene])).toEqual([[6, 0], [1, 0], [2, 1], [3, 2], [4, 2], [5, 1]]);
        expect(offen[1]).toMatchObject({ hatKinder: true, aufgeklappt: true });
        expect(offen[0]).toMatchObject({ hatKinder: false, aufgeklappt: false });
        const gefiltert = sichtbareZeilen(baum, new Set([1, 2]), new Set([1, 2, 3]));
        expect(gefiltert.map((z) => z.ordner.id)).toEqual([1, 2, 3]);
        // Gefiltert ohne sichtbare Kinder: keine Aufklapp-Möglichkeit
        expect(sichtbareZeilen(baum, new Set([1]), new Set([1]))[0]).toMatchObject({ hatKinder: false, aufgeklappt: false });
    });
});

describe('Suche', () => {
    it('liefert null ohne Suchtext', () => {
        expect(suche(baum, LEISTUNGEN, '  ')).toBeNull();
    });

    it('findet Ordner per Name und klappt die übergeordneten auf', () => {
        const ergebnis = suche(baum, LEISTUNGEN, 'edel')!;
        expect([...ergebnis.ordnerIds].sort()).toEqual([1, 2, 3]);
        expect([...ergebnis.aufklappen].sort()).toEqual([1, 2]);
        expect([...ergebnis.namensTreffer]).toEqual([3]);
        expect(ergebnis.leistungen).toEqual([]);
    });

    it('findet Leistungen per Name oder Beschreibung, aber nicht ohne Ordner', () => {
        const perBeschreibung = suche(baum, LEISTUNGEN, 'handlauf')!;
        expect(perBeschreibung.leistungen.map((l) => l.id)).toEqual([10]);
        expect([...perBeschreibung.ordnerIds].sort()).toEqual([1, 2, 3]);
        expect(suche(baum, LEISTUNGEN, 'ohne ordner')!.leistungen).toEqual([]);
        expect(passtZurSuche('Geländer', ' GELÄN ')).toBe(true);
        expect(leistungPasst(LEISTUNGEN[1], 'stahl')).toBe(true);
        expect(leistungPasst(LEISTUNGEN[1], 'holz')).toBe(false);
    });
});

describe('Beschreibungsvorschlag', () => {
    it('verbindet die nicht leeren Beschreibungen der Ordnerkette von oben nach unten', () => {
        expect(beschreibungsVorschlag(baum, 3)).toBe('<p>Metall</p><p><br></p><p>Geländer allgemein</p>');
        expect(beschreibungsVorschlag(baum, 6)).toBe('');
    });
});

describe('Gültige Ziele', () => {
    it('Ordner verschieben: nicht in sich selbst, nicht in eigene Unterordner, nicht zu Leistungen', () => {
        expect(ordnerZielSperre(baum, zaehler, 2, 2)).toBe(GRUND.selbst);
        expect(ordnerZielSperre(baum, zaehler, 2, 3)).toBe(GRUND.darin);
        expect(ordnerZielSperre(baum, zaehler, 2, 1)).toBe(GRUND.schonHier);
        expect(ordnerZielSperre(baum, zaehler, 6, 5)).toBe(GRUND.hatLeistungenKeinUnterordner);
        expect(ordnerZielSperre(baum, zaehler, 2, 6)).toBeNull();
        expect(ordnerZielSperre(baum, zaehler, 1, null)).toBe(GRUND.schonHier);
        expect(ordnerZielSperre(baum, zaehler, 2, null)).toBeNull();
        expect(ordnerZielSperre(baum, zaehler, 99, null)).toBeNull();
        expect(ordnerZielSperre(baum, new Map(), 6, 5)).toBeNull();
    });

    it('Ordner verschieben: Liste beginnt mit der obersten Ebene, danach alle Ordner eingerückt', () => {
        const ziele = zieleFuerOrdnerVerschieben(baum, zaehler, 2);
        expect(ziele[0]).toEqual({ id: null, name: OBERSTE_EBENE, ebene: 0, gesperrt: null });
        expect(ziele.map((z) => z.id)).toEqual([null, 6, 1, 2, 3, 4, 5]);
        expect(ziele.find((z) => z.id === 3)).toMatchObject({ ebene: 2, gesperrt: GRUND.darin });
    });

    it('Leistung verschieben: nur in Ordner ohne Unterordner, nicht in den eigenen', () => {
        expect(leistungZielSperre(baum, LEISTUNGEN[0], 2)).toBe(GRUND.hatUnterordnerKeineLeistungen);
        expect(leistungZielSperre(baum, LEISTUNGEN[0], 3)).toBe(GRUND.schonHier);
        expect(leistungZielSperre(baum, LEISTUNGEN[0], 6)).toBeNull();
        expect(zieleFuerLeistung(baum, LEISTUNGEN[0]).filter((z) => !z.gesperrt).map((z) => z.id)).toEqual([6, 4, 5]);
    });

    it('Umzug beim Löschen: Regeln für Unterordner und Leistungen wie im Backend', () => {
        expect(umzugZielSperre(baum, zaehler, 2, 2)).toBe(GRUND.selbst);
        expect(umzugZielSperre(baum, zaehler, 2, 4)).toBe(GRUND.darin);
        // Geländer hat Unterordner → Treppen (mit Leistungen) geht nicht
        expect(umzugZielSperre(baum, zaehler, 2, 5)).toMatch(/Leistungen – Unterordner passen nicht/);
        // Edelstahl hat direkte Leistungen → Metallbau (mit Unterordnern) geht nicht
        expect(umzugZielSperre(baum, zaehler, 3, 1)).toMatch(/Unterordner – Leistungen passen nicht/);
        expect(umzugZielSperre(baum, zaehler, 3, 4)).toBeNull();
        expect(umzugZielSperre(baum, new Map(), 2, 5)).toBeNull();
        expect(zieleFuerUmzug(baum, zaehler, 3).filter((z) => !z.gesperrt).map((z) => z.id)).toEqual([6, 4, 5]);
    });
});

describe('Bilder', () => {
    it('nimmt Bilder bis 10 MB an', () => {
        expect(pruefeBilddatei({ type: 'image/png', size: 1000, name: 'a.png' })).toBeNull();
        expect(pruefeBilddatei({ type: '', size: 1000, name: 'foto.JPG' })).toBeNull();
    });

    it('lehnt andere Dateien und zu große Bilder verständlich ab', () => {
        expect(pruefeBilddatei({ type: 'application/pdf', size: 10, name: 'plan.pdf' })).toBe(
            '„plan.pdf“ passt nicht als Ordnerbild. Erlaubt sind JPEG, PNG, GIF und WebP.');
        expect(pruefeBilddatei({ type: '', size: 10, name: 'liste.txt' })).toMatch(/passt nicht als Ordnerbild/);
        // Bilder, die das Backend nicht annimmt (z. B. HEIC, SVG), schon vorab ablehnen
        expect(pruefeBilddatei({ type: 'image/heic', size: 10, name: 'foto.heic' })).toMatch(/Erlaubt sind JPEG, PNG, GIF und WebP/);
        expect(pruefeBilddatei({ type: 'image/WEBP', size: 10, name: 'a.webp' })).toBeNull();
        expect(pruefeBilddatei({ type: 'image/jpeg', size: MAX_BILD_BYTES + 1024 * 1024, name: 'gross.jpg' }))
            .toBe('Das Bild ist mit 11 MB zu groß. Bitte höchstens 10 MB.');
    });

    it('nennt das Einfügen-Kürzel passend zum Gerät', () => {
        expect(einfuegenTaste('MacIntel')).toBe('⌘V');
        expect(einfuegenTaste('Win32')).toBe('Strg+V');
    });

    it('wählt aus einer Dateiliste bevorzugt das erste Bild', () => {
        const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
        const png = new File(['x'], 'b.png', { type: 'image/png' });
        expect(ersteDatei([pdf, png])).toBe(png);
        expect(ersteDatei([pdf])).toBe(pdf);
        expect(ersteDatei([])).toBeNull();
        expect(ersteDatei(null)).toBeNull();
        expect(ersteDatei(undefined)).toBeNull();
    });
});

describe('Texte und Zahlen', () => {
    it('formatiert Zahlen, Preise und Daten deutsch', () => {
        expect(formatZahl(1234.56)).toBe('1.234,6');
        expect(formatZahl(1.744, 2)).toBe('1,74');
        expect(formatZahl(3)).toBe('3');
        expect(formatPreis(12.5).replace(/\s/g, ' ')).toBe('12,50 €');
        expect(formatDatum('2026-03-02')).toBe('02.03.2026');
        expect(formatDatum('2026-03-02T10:00:00')).toBe('02.03.2026');
        expect(formatDatum(null)).toBe('');
        expect(formatDatum('gestern')).toBe('');
    });

    it('kennt Kurzformen der Einheiten', () => {
        expect(einheitKurz('LAUFENDE_METER')).toBe('m');
        expect(einheitKurz('Quadratmeter')).toBe('m²');
        expect(einheitKurz('Fass')).toBe('Fass');
        expect(einheitKurz(null)).toBe('');
        expect(einheitJe('Laufende Meter')).toBe('Meter');
        expect(einheitJe('STUECK')).toBe('Stück');
        expect(einheitJe('Fass')).toBe('Fass');
        expect(einheitJe(null)).toBe('Einheit');
    });

    it('bildet Mengen-Texte mit Einzahl und Mehrzahl', () => {
        expect(anzahlText(1, 'Leistung', 'Leistungen')).toBe('1 Leistung');
        expect(anzahlText(1200, 'Leistung', 'Leistungen')).toBe('1.200 Leistungen');
        expect(zaehlerAriaLabel(4, 3)).toBe('4 Leistungen, 3 Projekte');
        expect(zaehlerAriaLabel(1, 1)).toBe('1 Leistung, 1 Projekt');
        expect(projekteSatz(0)).toBe('Diese Kategorie hängt noch an keinem Projekt.');
        expect(projekteSatz(1)).toBe('Diese Kategorie hängt an einem Projekt.');
        expect(projekteSatz(3)).toBe('Diese Kategorie hängt an 3 Projekten.');
        expect(leistungenWandernHinweis(4, 'Geländer')).toBe('Die 4 Leistungen aus „Geländer“ wandern in den neuen Unterordner.');
        expect(leistungenWandernHinweis(1, 'Geländer')).toBe('Die Leistung aus „Geländer“ wandert in den neuen Unterordner.');
        expect(weitereAnzahl(25, 20)).toBe(5);
        expect(weitereAnzahl(3, 20)).toBe(0);
    });

    it('übersetzt Dokumenttypen in Alltagssprache', () => {
        expect(dokumentTypText('AUFTRAGSBESTAETIGUNG')).toBe('Auftragsbestätigung');
        expect(dokumentTypText('ERSTE_MAHNUNG')).toBe('1. Mahnung');
        expect(dokumentTypText('NEUER_TYP')).toBe('Neuer typ');
        expect(dokumentTypText(null)).toBe('Dokument');
    });

    it('bietet „Alle Jahre“ und die letzten 10 Jahre an', () => {
        const optionen = jahresOptionen(new Date(2026, 9, 23));
        expect(optionen).toHaveLength(11);
        expect(optionen[0]).toEqual({ value: '', label: 'Alle Jahre' });
        expect(optionen[1].value).toBe('2026');
        expect(optionen[10].value).toBe('2017');
        expect(jahresOptionen()).toHaveLength(11);
    });
});
