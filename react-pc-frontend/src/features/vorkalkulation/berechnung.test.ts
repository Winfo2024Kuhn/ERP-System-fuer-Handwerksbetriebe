import { describe, expect, it } from 'vitest';
import {
    aufCent,
    berechneGesamt,
    beschichtungskosten,
    entwurfAlsZahl,
    lohnSummen,
    materialSummen,
    positionsMengen,
    pruefeEntwuerfe,
    zeitzeilenKosten,
} from './berechnung';
import { beispielKalkulation, leereKalkulation } from './beispieldaten';
import type { ArbeitszeitPosition, MaterialPosition, VorkalkulationDaten } from './types';

/**
 * Die Zahlen in dieser Datei stammen aus der Excel-Mappe des Betriebs
 * (Material-, Zeit- und Gesamt-Kalkulation). Sie sind der eigentliche
 * Pruefstein: Trifft der Rechenkern sie, rechnet die Vor-Kalkulation so wie
 * bisher von Hand gerechnet wurde.
 */

const materialzeile = (werte: Partial<MaterialPosition>): MaterialPosition => ({
    id: 'm1',
    artikelId: null,
    artikelnummer: '',
    bezeichnung: 'Dummy-Profil',
    menge: '0',
    einheit: 'METER',
    preis: '0',
    preisbezug: 'EINHEIT',
    kgJeEinheit: '0',
    qmJeEinheit: '0',
    verzinkbar: false,
    pulverbeschichtbar: false,
    verzinken: false,
    pulverbeschichten: false,
    verzinkungsart: 'SCHLOSSERWARE',
    beschaffung: 'LAGER',
    ...werte,
});

const zeitzeile = (werte: Partial<ArbeitszeitPosition>): ArbeitszeitPosition => ({
    id: 'z1',
    arbeitsgangId: null,
    arbeitsgang: 'Montage',
    beschreibung: '',
    stunden: '0',
    stundensatz: '0',
    art: 'HAND',
    ...werte,
});

describe('entwurfAlsZahl', () => {
    it('liest deutsches Dezimalkomma', () => {
        expect(entwurfAlsZahl('12,5')).toBe(12.5);
        expect(entwurfAlsZahl('3,70')).toBe(3.7);
    });

    it('zaehlt unvollstaendige Entwuerfe als 0, damit die Anzeige nicht flackert', () => {
        expect(entwurfAlsZahl('')).toBe(0);
        expect(entwurfAlsZahl('12,')).toBe(0);
        expect(entwurfAlsZahl('abc')).toBe(0);
    });
});

describe('positionsMengen — Gewicht und Preis einer Materialzeile', () => {
    it('rechnet Flachstahl Fl 100x10 wie in der Material-Kalkulation', () => {
        // 1,872 m × 7,85 kg/m = 14,70 kg × 1,20 EUR/kg = 17,63 EUR
        const ergebnis = positionsMengen(materialzeile({
            bezeichnung: 'Normalflachstahl Fl 100x10',
            menge: '1,872',
            einheit: 'METER',
            kgJeEinheit: '7,85',
            preis: '1,2',
            preisbezug: 'KILOGRAMM',
        }));
        expect(ergebnis.kilogramm).toBeCloseTo(14.6952, 4);
        expect(ergebnis.kosten).toBe(17.63);
    });

    it('rechnet Quadratrohr HQ 50x50x3 wie in der Material-Kalkulation', () => {
        // 18,3888 m × 4,25 kg/m = 78,15 kg × 1,20 EUR/kg = 93,78 EUR
        const ergebnis = positionsMengen(materialzeile({
            bezeichnung: 'Quadratrohr HQ 50x50x3',
            menge: '18,3888',
            einheit: 'METER',
            kgJeEinheit: '4,25',
            preis: '1,2',
            preisbezug: 'KILOGRAMM',
        }));
        expect(ergebnis.kilogramm).toBeCloseTo(78.1524, 4);
        expect(ergebnis.kosten).toBe(93.78);
    });

    it('rechnet bei Preisbezug Einheit mit der Menge statt mit dem Gewicht', () => {
        const ergebnis = positionsMengen(materialzeile({
            menge: '10',
            einheit: 'METER',
            kgJeEinheit: '7,85',
            preis: '9,42',
            preisbezug: 'EINHEIT',
        }));
        expect(ergebnis.kosten).toBe(94.2);
        expect(ergebnis.kilogramm).toBeCloseTo(78.5, 4);
    });

    it('nimmt bei der Einheit Kilogramm die Menge selbst als Gewicht', () => {
        const ergebnis = positionsMengen(materialzeile({
            menge: '250',
            einheit: 'KILOGRAMM',
            kgJeEinheit: '',
            preis: '1,48',
            preisbezug: 'EINHEIT',
        }));
        expect(ergebnis.kilogramm).toBe(250);
        expect(ergebnis.kosten).toBe(370);
    });

    it('rechnet die Oberflaeche aus der Mantelflaeche je Meter', () => {
        // Quadratrohr 50x50: Mantelflaeche 4 × 50 mm = 0,2 m² je lfm
        const ergebnis = positionsMengen(materialzeile({
            menge: '18,3888',
            qmJeEinheit: '0,2',
        }));
        expect(ergebnis.quadratmeter).toBeCloseTo(3.6778, 4);
    });
});

describe('materialSummen', () => {
    it('trennt Schlosserware, Traegerware und Pulverflaeche nach der Entscheidung je Zeile', () => {
        const summen = materialSummen([
            materialzeile({ id: 'a', menge: '10', kgJeEinheit: '5', verzinken: true, verzinkungsart: 'SCHLOSSERWARE' }),
            materialzeile({ id: 'b', menge: '10', kgJeEinheit: '8', verzinken: true, verzinkungsart: 'TRAEGERWARE' }),
            materialzeile({ id: 'c', menge: '10', kgJeEinheit: '2', qmJeEinheit: '0,3', pulverbeschichten: true }),
        ], '0');

        expect(summen.kilogrammSchlosserware).toBe(50);
        expect(summen.kilogrammTraegerware).toBe(80);
        expect(summen.kilogrammVerzinken).toBe(130);
        expect(summen.gesamtKilogramm).toBe(150);
        expect(summen.quadratmeterPulver).toBe(3);
    });

    it('zaehlt eine Zeile nicht zur Verzinkung, nur weil sie verzinkbar waere', () => {
        const summen = materialSummen([
            materialzeile({ menge: '10', kgJeEinheit: '5', verzinkbar: true, verzinken: false }),
        ], '0');
        expect(summen.kilogrammVerzinken).toBe(0);
    });

    it('schlaegt den Gemeinkostenzuschlag auf die Materialkosten (Mappe: 20 %)', () => {
        const summen = materialSummen([
            materialzeile({ menge: '1', preis: '3587,46', preisbezug: 'EINHEIT' }),
        ], '20');

        expect(summen.materialkosten).toBe(3587.46);
        expect(summen.gkzBetrag).toBe(717.49);
        expect(summen.materialkostenGesamt).toBe(4304.95);
    });
});

describe('lohnSummen — die Zeit-Kalkulation der Mappe', () => {
    /** Die sechs belegten Zeilen aus dem Blatt "Zeit-Kalkulation". */
    const zeilenDerMappe: ArbeitszeitPosition[] = [
        zeitzeile({ id: '1', arbeitsgang: 'Meister', beschreibung: 'Aufmaß + Angebot', stunden: '3,7', stundensatz: '55' }),
        zeitzeile({ id: '2', arbeitsgang: 'Konstrukteur', beschreibung: 'Zeichnung Planung', stunden: '12,78', stundensatz: '55' }),
        zeitzeile({ id: '3', arbeitsgang: 'Anfertigung', beschreibung: 'Anfertigung', stunden: '47,4', stundensatz: '50' }),
        zeitzeile({ id: '4', arbeitsgang: 'Fahrzeit', beschreibung: 'Fahrzeit', stunden: '8,52', stundensatz: '50' }),
        zeitzeile({ id: '5', arbeitsgang: 'Montage', beschreibung: 'Montage', stunden: '28,65', stundensatz: '50' }),
        zeitzeile({ id: '6', arbeitsgang: 'Puffer', beschreibung: 'Puffer', stunden: '9', stundensatz: '50' }),
    ];

    it('trifft die Einzelbetraege der Mappe', () => {
        expect(zeitzeilenKosten(zeilenDerMappe[0])).toBe(203.5);
        expect(zeitzeilenKosten(zeilenDerMappe[1])).toBe(702.9);
        expect(zeitzeilenKosten(zeilenDerMappe[2])).toBe(2370);
        expect(zeitzeilenKosten(zeilenDerMappe[3])).toBe(426);
        expect(zeitzeilenKosten(zeilenDerMappe[4])).toBe(1432.5);
        expect(zeitzeilenKosten(zeilenDerMappe[5])).toBe(450);
    });

    it('summiert auf die 5.584,90 EUR Lohnkosten Hand der Gesamt-Kalkulation', () => {
        const summen = lohnSummen(zeilenDerMappe, '0', '0');
        expect(summen.lohnHand).toBe(5584.9);
        expect(summen.lohnkostenGesamt).toBe(5584.9);
        expect(summen.stundenGesamt).toBeCloseTo(110.05, 2);
    });

    it('nimmt halbe Stunden an', () => {
        expect(zeitzeilenKosten(zeitzeile({ stunden: '0,5', stundensatz: '55' }))).toBe(27.5);
    });

    it('haelt Hand- und Maschinenstunden getrennt und bezuschlagt sie einzeln', () => {
        const summen = lohnSummen([
            zeitzeile({ id: 'h', stunden: '10', stundensatz: '50', art: 'HAND' }),
            zeitzeile({ id: 'm', stunden: '4', stundensatz: '80', art: 'MASCHINE' }),
        ], '10', '25');

        expect(summen.lohnHand).toBe(500);
        expect(summen.gkzHandBetrag).toBe(50);
        expect(summen.lohnMaschine).toBe(320);
        expect(summen.gkzMaschineBetrag).toBe(80);
        expect(summen.lohnkostenGesamt).toBe(950);
    });
});

describe('beschichtungskosten', () => {
    const basis: VorkalkulationDaten = {
        ...leereKalkulation(),
        verzinkenSchlosserwareJeKg: '1,48',
        verzinkenTraegerwareJeKg: '0,8',
        verzinkenFracht: '50',
        feinverputzenAufschlagProzent: '55',
        pulverbeschichtenJeQm: '0',
    };

    const summen = materialSummen([
        materialzeile({ id: 'a', menge: '100', kgJeEinheit: '1', verzinken: true, verzinkungsart: 'SCHLOSSERWARE' }),
        materialzeile({ id: 'b', menge: '200', kgJeEinheit: '1', verzinken: true, verzinkungsart: 'TRAEGERWARE' }),
    ], '0');

    it('rechnet beide Warenarten mit ihrem eigenen Kilopreis', () => {
        const kosten = beschichtungskosten(summen, basis);
        expect(kosten.verzinkenSchlosserware).toBe(148);
        expect(kosten.verzinkenTraegerware).toBe(160);
        expect(kosten.verzinkenBasis).toBe(308);
    });

    it('laesst das Feinverputzen weg, solange der Schalter aus ist', () => {
        const kosten = beschichtungskosten(summen, { ...basis, feinverputzen: false });
        expect(kosten.feinverputzen).toBe(0);
        expect(kosten.verzinkenGesamt).toBe(358); // 308 + 50 Fracht
    });

    it('schlaegt das Feinverputzen auf die reine Verzinkung, nicht auf die Fracht', () => {
        const kosten = beschichtungskosten(summen, { ...basis, feinverputzen: true });
        expect(kosten.feinverputzen).toBe(169.4); // 55 % von 308, Fracht bleibt aussen vor
        expect(kosten.verzinkenGesamt).toBe(527.4);
    });

    it('rechnet die Pulverbeschichtung ueber die Flaeche', () => {
        const mitFlaeche = materialSummen([
            materialzeile({ menge: '10', qmJeEinheit: '0,5', pulverbeschichten: true }),
        ], '0');
        const kosten = beschichtungskosten(mitFlaeche, { ...basis, pulverbeschichtenJeQm: '18,50' });
        expect(kosten.pulverbeschichten).toBe(92.5); // 5 m² × 18,50 EUR
    });
});

describe('berechneGesamt — die Schlusskette der Gesamt-Kalkulation', () => {
    /**
     * Rekonstruktion des Blatts "Gesamt-Kalkulation". Material und Lohn sind
     * dort Summen ueber mehrere Seiten, deshalb stehen sie hier als je eine
     * Sammelzeile — die Kette darueber ist identisch.
     */
    const mappe: VorkalkulationDaten = {
        ...leereKalkulation(),
        material: [materialzeile({ menge: '1', preis: '3587,46', preisbezug: 'EINHEIT' })],
        gkzMaterialProzent: '20',
        arbeitszeit: [zeitzeile({ stunden: '1', stundensatz: '5584,90' })],
        gkzHandProzent: '0',
        gkzMaschineProzent: '0',
        zusatzkosten: [
            { id: 'f1', bezeichnung: 'Materiallieferung', betrag: '70' },
            { id: 'f2', bezeichnung: 'Sprit', betrag: '30' },
        ],
        verzinkenSchlosserwareJeKg: '1,48',
        verzinkenTraegerwareJeKg: '0,8',
        verzinkenFracht: '0',
        feinverputzen: false,
        feinverputzenAufschlagProzent: '55',
        pulverbeschichtenJeQm: '0',
        verwaltungVertriebProzent: '3,5',
        wagnisGewinnProzent: '5',
        skontoProzent: '0',
    };

    it('trifft Materialkosten, Lohnkosten und variable Kosten der Mappe', () => {
        // Die Verzinkung der Mappe (193,88 EUR) steckt hier als Kostenzeile,
        // weil ihre Kilogramm auf den Materialseiten stehen.
        const ergebnis = berechneGesamt({
            ...mappe,
            zusatzkosten: [...mappe.zusatzkosten, { id: 'v', bezeichnung: 'Feuerverzinken inkl. Fracht', betrag: '243,88' }],
        });

        expect(ergebnis.material.materialkostenGesamt).toBe(4304.95);
        expect(ergebnis.lohn.lohnkostenGesamt).toBe(5584.9);
        expect(ergebnis.variableKosten).toBe(343.88);
    });

    it('trifft Herstellkosten, Selbstkosten und Verkaufspreis der Mappe', () => {
        const ergebnis = berechneGesamt({
            ...mappe,
            zusatzkosten: [...mappe.zusatzkosten, { id: 'v', bezeichnung: 'Feuerverzinken inkl. Fracht', betrag: '243,88' }],
        });

        expect(ergebnis.herstellkosten).toBe(10233.73);
        expect(ergebnis.verwaltungVertriebBetrag).toBe(358.18);
        expect(ergebnis.selbstkosten).toBe(10591.91);
        expect(ergebnis.wagnisGewinnBetrag).toBe(529.6);
        expect(ergebnis.verkaufspreisNetto).toBe(11121.51);
    });

    it('rechnet Verwaltung und Vertrieb auf die Herstellkosten, Wagnis und Gewinn danach auf die Selbstkosten', () => {
        const ergebnis = berechneGesamt({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '1', preis: '1000', preisbezug: 'EINHEIT' })],
            gkzMaterialProzent: '0',
            verwaltungVertriebProzent: '10',
            wagnisGewinnProzent: '10',
        });

        expect(ergebnis.herstellkosten).toBe(1000);
        expect(ergebnis.selbstkosten).toBe(1100);
        // 10 % auf 1.100, nicht auf 1.000 — sonst kaeme 1.200 heraus.
        expect(ergebnis.wagnisGewinnBetrag).toBe(110);
        expect(ergebnis.verkaufspreisNetto).toBe(1210);
    });

    it('schlaegt das Skonto auf, damit nach dem Abzug der kalkulierte Preis bleibt', () => {
        const ergebnis = berechneGesamt({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '1', preis: '1000', preisbezug: 'EINHEIT' })],
            gkzMaterialProzent: '0',
            verwaltungVertriebProzent: '0',
            wagnisGewinnProzent: '0',
            skontoProzent: '3',
        });

        expect(ergebnis.verkaufspreisVorSkonto).toBe(1000);
        expect(ergebnis.skontoBetrag).toBe(30);
        expect(ergebnis.verkaufspreisNetto).toBe(1030);
    });

    it('bleibt bei einer leeren Kalkulation ueberall bei 0', () => {
        const ergebnis = berechneGesamt(leereKalkulation());
        expect(ergebnis.herstellkosten).toBe(0);
        expect(ergebnis.verkaufspreisNetto).toBe(0);
    });

    it('berechnet keine Anfahrt zur Verzinkerei, wenn nichts verzinkt wird', () => {
        const ohneVerzinkung = berechneGesamt({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '10', kgJeEinheit: '5', verzinken: false })],
            verzinkenFracht: '50',
        });
        expect(ohneVerzinkung.beschichtung.fracht).toBe(0);

        const mitVerzinkung = berechneGesamt({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '10', kgJeEinheit: '5', verzinken: true })],
            verzinkenFracht: '50',
        });
        expect(mitVerzinkung.beschichtung.fracht).toBe(50);
    });
});

describe('pruefeEntwuerfe', () => {
    it('laesst eine vollstaendige Kalkulation durch', () => {
        expect(pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '12,5', preis: '1,48' })],
        })).toBeNull();
    });

    it('nennt die Zeile beim Namen, wenn eine Menge unvollstaendig ist', () => {
        const fehler = pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ bezeichnung: 'Quadratrohr', menge: '12,' })],
        });
        expect(fehler?.meldung).toContain('Quadratrohr');
    });

    it('verlangt ein Gewicht, sobald eine Zeile verzinkt werden soll', () => {
        const fehler = pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ bezeichnung: 'Winkel', menge: '4', kgJeEinheit: '', verzinken: true })],
        });
        expect(fehler?.meldung).toContain('Gewicht je Einheit');
    });

    it('weist einen Prozentsatz ueber 100 zurueck', () => {
        const fehler = pruefeEntwuerfe({ ...leereKalkulation(), wagnisGewinnProzent: '150' });
        expect(fehler?.meldung).toContain('Wagnis und Gewinn');
    });

    it('laesst eine Materialzeile ohne Preis nicht durch — sonst waeren es still 0 EUR', () => {
        // Genau der Fall bei einem Artikel ohne gepflegten Lieferantenpreis.
        const fehler = pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ bezeichnung: 'Winkelstahl', menge: '4', preis: '' })],
        });
        expect(fehler?.meldung).toContain('Preis');
        expect(fehler?.meldung).toContain('Winkelstahl');
    });

    it('laesst eine Materialzeile ohne Menge nicht durch', () => {
        const fehler = pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ bezeichnung: 'Winkelstahl', menge: '', preis: '1,48' })],
        });
        expect(fehler?.meldung).toContain('Menge');
    });

    it('verlangt Stunden und Stundensatz an jeder Arbeitszeile', () => {
        expect(pruefeEntwuerfe({
            ...leereKalkulation(),
            arbeitszeit: [zeitzeile({ arbeitsgang: 'Montage', stunden: '', stundensatz: '50' })],
        })?.meldung).toContain('Stunden');

        expect(pruefeEntwuerfe({
            ...leereKalkulation(),
            arbeitszeit: [zeitzeile({ arbeitsgang: 'Montage', stunden: '4', stundensatz: '' })],
        })?.meldung).toContain('Stundensatz');
    });

    it('weist Geldbetraege mit mehr als zwei Nachkommastellen zurueck', () => {
        const fehler = pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ bezeichnung: 'Rohr', menge: '1', preis: '1,239' })],
        });
        expect(fehler?.meldung).toContain('Nachkommastellen');
    });

    it('laesst eine leere Zusatzkostenzeile als Platzhalter stehen', () => {
        expect(pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '1', preis: '1,48' })],
            zusatzkosten: [{ id: 'z', bezeichnung: 'Werkstoffzeugnisse', betrag: '' }],
        })).toBeNull();
    });

    it('laesst die Beispiel-Kalkulation vollstaendig durch', () => {
        expect(pruefeEntwuerfe(beispielKalkulation())).toBeNull();
    });

    it('verlangt den Verzinkungspreis, sobald Zeilen verzinkt werden sollen', () => {
        const mitVerzinkung = {
            ...leereKalkulation(),
            material: [materialzeile({
                bezeichnung: 'Winkelstahl', menge: '10', preis: '1,48',
                kgJeEinheit: '5', verzinken: true, verzinkungsart: 'SCHLOSSERWARE' as const,
            })],
        };

        // Ohne Kilopreis kostete die Verzinkung sonst still 0 EUR im Angebot.
        expect(pruefeEntwuerfe({ ...mitVerzinkung, verzinkenSchlosserwareJeKg: '' })?.meldung)
            .toContain('Verzinkungspreis für Schlosserware');
        expect(pruefeEntwuerfe({ ...mitVerzinkung, verzinkenSchlosserwareJeKg: '1,48' })).toBeNull();
    });

    it('verlangt den Traegerware-Preis nur, wenn auch Traegerware verzinkt wird', () => {
        const nurSchlosserware = {
            ...leereKalkulation(),
            material: [materialzeile({
                menge: '10', preis: '1,48', kgJeEinheit: '5',
                verzinken: true, verzinkungsart: 'SCHLOSSERWARE' as const,
            })],
            verzinkenTraegerwareJeKg: '',
        };
        expect(pruefeEntwuerfe(nurSchlosserware)).toBeNull();
    });

    it('verlangt den Pulverpreis, sobald eine Zeile beschichtet werden soll', () => {
        const mitPulver = {
            ...leereKalkulation(),
            material: [materialzeile({
                bezeichnung: 'Blech', menge: '3', preis: '20',
                qmJeEinheit: '2', pulverbeschichten: true,
            })],
        };

        expect(pruefeEntwuerfe({ ...mitPulver, pulverbeschichtenJeQm: '' })?.meldung)
            .toContain('Pulverbeschichten');
        expect(pruefeEntwuerfe({ ...mitPulver, pulverbeschichtenJeQm: '18,50' })).toBeNull();
    });

    it('laesst Beschichtungspreise leer, solange nichts beschichtet wird', () => {
        expect(pruefeEntwuerfe({
            ...leereKalkulation(),
            material: [materialzeile({ menge: '1', preis: '1,48' })],
            verzinkenSchlosserwareJeKg: '',
            verzinkenTraegerwareJeKg: '',
            pulverbeschichtenJeQm: '',
        })).toBeNull();
    });

    it('nimmt einen leeren Zuschlagssatz als "kein Zuschlag" hin, statt zu blockieren', () => {
        // Sonst blockierte schon ein Klick in ein "0"-Feld und wieder heraus
        // das Uebernehmen — die Null verschwindet dort beim Fokus.
        const ohneZuschlaege = {
            ...leereKalkulation(),
            material: [materialzeile({ menge: '1', preis: '1000' })],
            gkzMaterialProzent: '',
            gkzHandProzent: '',
            verwaltungVertriebProzent: '',
            wagnisGewinnProzent: '',
            skontoProzent: '',
        };
        expect(pruefeEntwuerfe(ohneZuschlaege)).toBeNull();
        expect(berechneGesamt(ohneZuschlaege).verkaufspreisNetto).toBe(1000);
    });
});

describe('aufCent', () => {
    it('raeumt Gleitkomma-Reste weg', () => {
        expect(aufCent(0.1 + 0.2)).toBe(0.3);
        expect(aufCent(1.005)).toBe(1.01);
    });
});
