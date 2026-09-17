/**
 * Rechenkern der Vor-Kalkulation — reine Funktionen, kein React.
 *
 * Die Formelkette bildet die Excel-Mappe des Betriebs nach:
 *
 *   Materialkosten + GKZ Material          = Materialkosten gesamt
 *   Lohn Hand + GKZ, Lohn Maschine + GKZ   = Lohnkosten gesamt
 *   Beschichtung + freie Zusatzkosten      = Variable Kosten
 *   Summe der drei                         = Herstellkosten
 *   + Verwaltung und Vertrieb %            = Selbstkosten
 *   + Wagnis und Gewinn %                  = Verkaufspreis netto
 *
 * Bewusst getrennt vom UI-Code: So laesst sich jede Stufe gegen die Zahlen aus
 * der Mappe testen (siehe `berechnung.test.ts`), und die spaetere Herleitung
 * der Zuschlagssaetze aus der Nachkalkulation tauscht nur die Eingabewerte.
 */
import { validateDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import type {
    ArbeitszeitPosition,
    MaterialPosition,
    VorkalkulationDaten,
    Zusatzkostenzeile,
} from './types';

/**
 * Entwurfs-String zu Zahl fuer die **laufende Anzeige**.
 *
 * Ein unvollstaendiger Entwurf ("", "12,") zaehlt hier als 0, damit die Summen
 * waehrend des Tippens nicht flackern oder verschwinden. Das ersetzt **keine**
 * Pruefung: Vor einer echten Aktion laeuft `pruefeEntwuerfe` und lehnt
 * unvollstaendige Werte mit einer eigenen Meldung ab.
 */
export function entwurfAlsZahl(entwurf: string): number {
    const ergebnis = validateDecimalInput(entwurf, { label: 'Wert' });
    return ergebnis.valid ? ergebnis.value ?? 0 : 0;
}

/** Prozent-Entwurf als Faktor: "3,5" -> 0,035. */
function prozentAlsFaktor(entwurf: string): number {
    return entwurfAlsZahl(entwurf) / 100;
}

/** Auf Cent runden — sonst schleppt jede Stufe Gleitkomma-Reste mit. */
export function aufCent(betrag: number): number {
    return Math.round((betrag + Number.EPSILON) * 100) / 100;
}

// ─── Material ───────────────────────────────────────────────────────────────

export interface PositionsMengen {
    menge: number;
    /** Gesamtgewicht der Zeile in Kilogramm. */
    kilogramm: number;
    /** Zu beschichtende Oberflaeche der Zeile in m². */
    quadratmeter: number;
    kosten: number;
}

/**
 * Gewicht, Oberflaeche und Kosten einer Materialzeile.
 *
 * Das Gewicht ist der Dreh- und Angelpunkt: Ohne es laesst sich weder ein
 * Kilo-Preis noch die Verzinkung rechnen. Bei der Einheit Kilogramm ist die
 * Menge bereits das Gewicht — ein fehlender Faktor zaehlt dort als 1, statt
 * die Zeile stillschweigend auf 0 kg zu setzen.
 */
export function positionsMengen(position: MaterialPosition): PositionsMengen {
    const menge = entwurfAlsZahl(position.menge);
    const kgFaktorEntwurf = entwurfAlsZahl(position.kgJeEinheit);
    const kgFaktor = position.einheit === 'KILOGRAMM'
        ? kgFaktorEntwurf || 1
        : kgFaktorEntwurf;

    const kilogramm = menge * kgFaktor;
    const quadratmeter = menge * entwurfAlsZahl(position.qmJeEinheit);
    const preis = entwurfAlsZahl(position.preis);
    const kosten = position.preisbezug === 'KILOGRAMM' ? kilogramm * preis : menge * preis;

    return { menge, kilogramm, quadratmeter, kosten: aufCent(kosten) };
}

export interface MaterialSummen {
    materialkosten: number;
    gkzBetrag: number;
    materialkostenGesamt: number;
    gesamtKilogramm: number;
    gesamtQuadratmeter: number;
    /** Nur die Zeilen, die tatsaechlich verzinkt werden sollen. */
    kilogrammSchlosserware: number;
    kilogrammTraegerware: number;
    kilogrammVerzinken: number;
    /** Nur die Zeilen, die tatsaechlich pulverbeschichtet werden sollen. */
    quadratmeterPulver: number;
}

export function materialSummen(
    positionen: MaterialPosition[],
    gkzMaterialProzent: string,
): MaterialSummen {
    let materialkosten = 0;
    let gesamtKilogramm = 0;
    let gesamtQuadratmeter = 0;
    let kilogrammSchlosserware = 0;
    let kilogrammTraegerware = 0;
    let quadratmeterPulver = 0;

    for (const position of positionen) {
        const { kilogramm, quadratmeter, kosten } = positionsMengen(position);
        materialkosten += kosten;
        gesamtKilogramm += kilogramm;
        gesamtQuadratmeter += quadratmeter;

        if (position.verzinken) {
            if (position.verzinkungsart === 'TRAEGERWARE') kilogrammTraegerware += kilogramm;
            else kilogrammSchlosserware += kilogramm;
        }
        if (position.pulverbeschichten) quadratmeterPulver += quadratmeter;
    }

    materialkosten = aufCent(materialkosten);
    const gkzBetrag = aufCent(materialkosten * prozentAlsFaktor(gkzMaterialProzent));

    return {
        materialkosten,
        gkzBetrag,
        materialkostenGesamt: aufCent(materialkosten + gkzBetrag),
        gesamtKilogramm,
        gesamtQuadratmeter,
        kilogrammSchlosserware,
        kilogrammTraegerware,
        kilogrammVerzinken: kilogrammSchlosserware + kilogrammTraegerware,
        quadratmeterPulver,
    };
}

// ─── Arbeitszeit ────────────────────────────────────────────────────────────

export interface LohnSummen {
    stundenHand: number;
    stundenMaschine: number;
    stundenGesamt: number;
    lohnHand: number;
    gkzHandBetrag: number;
    lohnMaschine: number;
    gkzMaschineBetrag: number;
    lohnkostenGesamt: number;
}

/** Kosten einer einzelnen Zeitzeile: Stunden mal Stundensatz. */
export function zeitzeilenKosten(position: ArbeitszeitPosition): number {
    return aufCent(entwurfAlsZahl(position.stunden) * entwurfAlsZahl(position.stundensatz));
}

export function lohnSummen(
    positionen: ArbeitszeitPosition[],
    gkzHandProzent: string,
    gkzMaschineProzent: string,
): LohnSummen {
    let stundenHand = 0;
    let stundenMaschine = 0;
    let lohnHand = 0;
    let lohnMaschine = 0;

    for (const position of positionen) {
        const stunden = entwurfAlsZahl(position.stunden);
        const kosten = zeitzeilenKosten(position);
        if (position.art === 'MASCHINE') {
            stundenMaschine += stunden;
            lohnMaschine += kosten;
        } else {
            stundenHand += stunden;
            lohnHand += kosten;
        }
    }

    lohnHand = aufCent(lohnHand);
    lohnMaschine = aufCent(lohnMaschine);
    const gkzHandBetrag = aufCent(lohnHand * prozentAlsFaktor(gkzHandProzent));
    const gkzMaschineBetrag = aufCent(lohnMaschine * prozentAlsFaktor(gkzMaschineProzent));

    return {
        stundenHand,
        stundenMaschine,
        stundenGesamt: stundenHand + stundenMaschine,
        lohnHand,
        gkzHandBetrag,
        lohnMaschine,
        gkzMaschineBetrag,
        lohnkostenGesamt: aufCent(lohnHand + gkzHandBetrag + lohnMaschine + gkzMaschineBetrag),
    };
}

// ─── Oberflaeche ────────────────────────────────────────────────────────────

export interface Beschichtungskosten {
    verzinkenSchlosserware: number;
    verzinkenTraegerware: number;
    /** Reine Verzinkung ohne Feinverputzen und ohne Fracht. */
    verzinkenBasis: number;
    feinverputzen: number;
    fracht: number;
    verzinkenGesamt: number;
    pulverbeschichten: number;
    gesamt: number;
}

/**
 * Verzinken, Feinverputzen und Pulverbeschichten.
 *
 * Feinverputzen ist ein prozentualer Aufschlag auf die **reine
 * Verzinkungsleistung** — die Fracht bleibt aussen vor, weil sie unabhaengig
 * davon anfaellt, wie aufwaendig das Nacharbeiten wird.
 *
 * Die Fracht faellt nur an, wenn ueberhaupt etwas zur Verzinkerei geht. Ohne
 * diese Bedingung wuerde eine Kalkulation ganz ohne Verzinkung die 50 EUR
 * Anfahrt mitschleppen — ein Fehler, der sich bis in den Verkaufspreis
 * durchzieht und beim Nachrechnen kaum auffaellt.
 */
export function beschichtungskosten(
    material: MaterialSummen,
    daten: VorkalkulationDaten,
): Beschichtungskosten {
    const verzinkenSchlosserware = aufCent(
        material.kilogrammSchlosserware * entwurfAlsZahl(daten.verzinkenSchlosserwareJeKg),
    );
    const verzinkenTraegerware = aufCent(
        material.kilogrammTraegerware * entwurfAlsZahl(daten.verzinkenTraegerwareJeKg),
    );
    const verzinkenBasis = aufCent(verzinkenSchlosserware + verzinkenTraegerware);
    const feinverputzen = daten.feinverputzen
        ? aufCent(verzinkenBasis * prozentAlsFaktor(daten.feinverputzenAufschlagProzent))
        : 0;
    const fracht = material.kilogrammVerzinken > 0
        ? aufCent(entwurfAlsZahl(daten.verzinkenFracht))
        : 0;
    const verzinkenGesamt = aufCent(verzinkenBasis + feinverputzen + fracht);
    const pulverbeschichten = aufCent(
        material.quadratmeterPulver * entwurfAlsZahl(daten.pulverbeschichtenJeQm),
    );

    return {
        verzinkenSchlosserware,
        verzinkenTraegerware,
        verzinkenBasis,
        feinverputzen,
        fracht,
        verzinkenGesamt,
        pulverbeschichten,
        gesamt: aufCent(verzinkenGesamt + pulverbeschichten),
    };
}

export function zusatzkostenSumme(zeilen: Zusatzkostenzeile[]): number {
    return aufCent(zeilen.reduce((summe, zeile) => summe + entwurfAlsZahl(zeile.betrag), 0));
}

// ─── Schlusskette ───────────────────────────────────────────────────────────

export interface Gesamtergebnis {
    material: MaterialSummen;
    lohn: LohnSummen;
    beschichtung: Beschichtungskosten;
    zusatzkosten: number;
    variableKosten: number;
    herstellkosten: number;
    verwaltungVertriebBetrag: number;
    selbstkosten: number;
    wagnisGewinnBetrag: number;
    verkaufspreisVorSkonto: number;
    skontoBetrag: number;
    verkaufspreisNetto: number;
}

/**
 * Die komplette Kette bis zum Verkaufspreis netto.
 *
 * Reihenfolge der Zuschlaege ist nicht beliebig: Verwaltung und Vertrieb
 * rechnet auf die Herstellkosten, Wagnis und Gewinn danach auf die
 * Selbstkosten. Andersherum kaeme ein anderer Preis heraus.
 *
 * Skonto wird **aufgeschlagen**, nicht abgezogen: Zieht der Kunde es spaeter
 * ab, bleibt der kalkulierte Preis uebrig. (Offener Punkt — in der Mappe steht
 * die Zeile auf 0 %, der Fall ist dort also nicht entscheidbar.)
 */
export function berechneGesamt(daten: VorkalkulationDaten): Gesamtergebnis {
    const material = materialSummen(daten.material, daten.gkzMaterialProzent);
    const lohn = lohnSummen(daten.arbeitszeit, daten.gkzHandProzent, daten.gkzMaschineProzent);
    const beschichtung = beschichtungskosten(material, daten);
    const zusatzkosten = zusatzkostenSumme(daten.zusatzkosten);

    const variableKosten = aufCent(beschichtung.gesamt + zusatzkosten);
    const herstellkosten = aufCent(
        material.materialkostenGesamt + lohn.lohnkostenGesamt + variableKosten,
    );

    const verwaltungVertriebBetrag = aufCent(
        herstellkosten * prozentAlsFaktor(daten.verwaltungVertriebProzent),
    );
    const selbstkosten = aufCent(herstellkosten + verwaltungVertriebBetrag);

    const wagnisGewinnBetrag = aufCent(selbstkosten * prozentAlsFaktor(daten.wagnisGewinnProzent));
    const verkaufspreisVorSkonto = aufCent(selbstkosten + wagnisGewinnBetrag);

    const skontoBetrag = aufCent(verkaufspreisVorSkonto * prozentAlsFaktor(daten.skontoProzent));

    return {
        material,
        lohn,
        beschichtung,
        zusatzkosten,
        variableKosten,
        herstellkosten,
        verwaltungVertriebBetrag,
        selbstkosten,
        wagnisGewinnBetrag,
        verkaufspreisVorSkonto,
        skontoBetrag,
        verkaufspreisNetto: aufCent(verkaufspreisVorSkonto + skontoBetrag),
    };
}

// ─── Pruefung vor einer echten Aktion ───────────────────────────────────────

export interface Entwurfsfehler {
    /** Klartext fuer den Toast, z. B. "Position 2: Menge …". */
    meldung: string;
}

/**
 * Prueft alle Zahlenfelder vollstaendig, bevor der Preis uebernommen wird.
 *
 * Gibt den **ersten** Fehler zurueck statt einer Liste: Im Toast ist eine
 * konkrete Meldung brauchbarer als fuenf abgeschnittene.
 */
export function pruefeEntwuerfe(daten: VorkalkulationDaten): Entwurfsfehler | null {
    /**
     * Jede Pruefung als eigener Aufruf von `validateNumberDrafts` (aus
     * `lib/numberDrafts.ts`), damit auch die Nachkommastellen-Regel greift:
     * Ein Preis von "1,239 EUR" waere sonst durchgerutscht und still
     * verrechnet worden.
     */
    const pruefe = (
        entwurf: string,
        regel: { label: string; required?: boolean; min?: number; max?: number; maxDecimalPlaces?: number },
    ): Entwurfsfehler | null => {
        const ergebnis = validateNumberDrafts({ wert: entwurf }, { wert: regel });
        return ergebnis.valid ? null : { meldung: ergebnis.message };
    };

    /** Geldbetraege: nie mehr als Cent-Genauigkeit. */
    const GELD = { min: 0, maxDecimalPlaces: 2 } as const;

    for (const [index, position] of daten.material.entries()) {
        const name = position.bezeichnung.trim() || `Materialzeile ${index + 1}`;
        // Menge und Preis sind Pflicht: Eine leere Zahl wuerde als 0 in den
        // Angebotspreis wandern, ohne dass es jemand merkt. Genau das passiert
        // bei Artikeln ohne gepflegten Lieferantenpreis.
        const fehler = pruefe(position.menge, { label: `die Menge bei „${name}“`, required: true, min: 0, maxDecimalPlaces: 4 })
            ?? pruefe(position.preis, { label: `den Preis bei „${name}“`, required: true, ...GELD })
            ?? ((position.verzinken || (position.preisbezug === 'KILOGRAMM' && position.einheit !== 'KILOGRAMM'))
                ? pruefe(position.kgJeEinheit, { label: `das Gewicht je Einheit bei „${name}“`, required: true, min: 0, maxDecimalPlaces: 4 })
                : null)
            ?? (position.pulverbeschichten
                ? pruefe(position.qmJeEinheit, { label: `die Fläche je Einheit bei „${name}“`, required: true, min: 0, maxDecimalPlaces: 4 })
                : null);
        if (fehler) return fehler;
    }

    for (const [index, position] of daten.arbeitszeit.entries()) {
        const name = position.arbeitsgang.trim() || `Arbeitszeile ${index + 1}`;
        const fehler = pruefe(position.stunden, { label: `die Stunden bei „${name}“`, required: true, min: 0, maxDecimalPlaces: 2 })
            ?? pruefe(position.stundensatz, { label: `den Stundensatz bei „${name}“`, required: true, ...GELD });
        if (fehler) return fehler;
    }

    for (const [index, zeile] of daten.zusatzkosten.entries()) {
        const name = zeile.bezeichnung.trim() || `Kostenzeile ${index + 1}`;
        // Nicht Pflicht und ohne Untergrenze: Eine leere Zeile ist ein
        // Platzhalter fuer spaeter, ein negativer Betrag eine Gutschrift.
        const fehler = pruefe(zeile.betrag, { label: `den Betrag bei „${name}“`, maxDecimalPlaces: 2 });
        if (fehler) return fehler;
    }

    /**
     * Zuschlagssaetze sind NICHT pflichtig: Ein leeres Prozentfeld heisst
     * fachlich "kein Zuschlag" und rechnet als 0 %. Waere es Pflicht, wuerde
     * schon ein Klick in ein "0"-Feld (die Null verschwindet beim Fokus) und
     * ein Klick daneben das Uebernehmen blockieren.
     *
     * Preise und Mengen sind dagegen Pflicht (siehe oben) — dort heisst leer
     * nicht "null Euro", sondern "noch nicht bekannt".
     */
    const PROZENT = { min: 0, max: 100, maxDecimalPlaces: 2 } as const;

    /**
     * Beschichtungspreise sind Pflicht, SOBALD es etwas zu beschichten gibt.
     * Sonst waere genau die stille Null wieder da, die bei Material und Lohn
     * beseitigt ist: Zeilen stehen auf "verzinken", die Kilogramm sind
     * gefuehrt — aber ohne Kilopreis kostet die Verzinkung 0 EUR im Angebot.
     */
    const summen = materialSummen(daten.material, daten.gkzMaterialProzent);
    const PREIS_JE_MENGE = { min: 0, maxDecimalPlaces: 4 } as const;

    const fehler = pruefe(daten.gkzMaterialProzent, { label: 'den Zuschlag auf das Material', ...PROZENT })
        ?? pruefe(daten.gkzHandProzent, { label: 'den Zuschlag auf die Handarbeit', ...PROZENT })
        ?? pruefe(daten.gkzMaschineProzent, { label: 'den Zuschlag auf die Maschinenarbeit', ...PROZENT })
        ?? pruefe(daten.verzinkenSchlosserwareJeKg, {
            label: 'den Verzinkungspreis für Schlosserware',
            required: summen.kilogrammSchlosserware > 0,
            ...PREIS_JE_MENGE,
        })
        ?? pruefe(daten.verzinkenTraegerwareJeKg, {
            label: 'den Verzinkungspreis für Trägerware',
            required: summen.kilogrammTraegerware > 0,
            ...PREIS_JE_MENGE,
        })
        ?? pruefe(daten.verzinkenFracht, { label: 'die Fracht zur Verzinkerei', ...GELD })
        ?? pruefe(daten.pulverbeschichtenJeQm, {
            label: 'den Preis fürs Pulverbeschichten',
            required: summen.quadratmeterPulver > 0,
            ...PREIS_JE_MENGE,
        })
        ?? (daten.feinverputzen
            ? pruefe(daten.feinverputzenAufschlagProzent, { label: 'den Aufschlag fürs Feinverputzen', min: 0, maxDecimalPlaces: 2 })
            : null)
        ?? pruefe(daten.verwaltungVertriebProzent, { label: 'den Aufschlag für Verwaltung und Vertrieb', ...PROZENT })
        ?? pruefe(daten.wagnisGewinnProzent, { label: 'den Aufschlag für Wagnis und Gewinn', ...PROZENT })
        ?? pruefe(daten.skontoProzent, { label: 'das Skonto', ...PROZENT });

    return fehler;
}
