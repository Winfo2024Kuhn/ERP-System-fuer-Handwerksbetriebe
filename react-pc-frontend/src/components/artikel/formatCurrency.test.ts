import { describe, it, expect } from "vitest";
import { formatCurrency, nachkommastellenFuerPreis } from "./formatCurrency";

/**
 * Intl setzt zwischen Zahl und Waehrungszeichen ein schmales geschuetztes
 * Leerzeichen (U+00A0). Fuer die Erwartungen unten stoert das nur.
 */
const ohneSonderleerzeichen = (text: string) => text.replace(/ /g, " ");

describe("nachkommastellenFuerPreis", () => {
    it("laesst gewoehnliche Preise bei Cent-Genauigkeit", () => {
        expect(nachkommastellenFuerPreis(12.5)).toBe(2);
        expect(nachkommastellenFuerPreis(0.05)).toBe(2);
        // Ab einem halben Cent zeigt die Cent-Darstellung schon etwas an (0,01).
        expect(nachkommastellenFuerPreis(0.005)).toBe(2);
    });

    it("dreht bei Kleinstpreisen so weit auf, dass eine Ziffer sichtbar wird", () => {
        expect(nachkommastellenFuerPreis(0.0043)).toBe(4);
        expect(nachkommastellenFuerPreis(0.0009)).toBe(5);
        expect(nachkommastellenFuerPreis(0.00002)).toBe(6);
    });

    it("deckelt bei sechs Stellen", () => {
        expect(nachkommastellenFuerPreis(0.000000123)).toBe(6);
    });

    it("behandelt Null und ungueltige Werte wie einen gewoehnlichen Preis", () => {
        expect(nachkommastellenFuerPreis(0)).toBe(2);
        expect(nachkommastellenFuerPreis(Number.NaN)).toBe(2);
    });

    it("richtet sich nach dem Betrag, nicht nach dem Vorzeichen", () => {
        expect(nachkommastellenFuerPreis(-0.0043)).toBe(4);
        expect(nachkommastellenFuerPreis(-12.5)).toBe(2);
    });
});

describe("formatCurrency", () => {
    it("schreibt gewoehnliche Preise unveraendert mit zwei Nachkommastellen", () => {
        expect(ohneSonderleerzeichen(formatCurrency(12.5))).toBe("12,50 €");
        expect(ohneSonderleerzeichen(formatCurrency(1234.567))).toBe("1.234,57 €");
    });

    it("macht Kleinstpreise sichtbar, statt 0,00 € zu zeigen", () => {
        // Genau der Fall aus der Artikelliste: Schrauben im Zehntelcent-Bereich.
        expect(ohneSonderleerzeichen(formatCurrency(0.0043))).toBe("0,0043 €");
        expect(ohneSonderleerzeichen(formatCurrency(0.004))).toBe("0,004 €");
        expect(ohneSonderleerzeichen(formatCurrency(0.00002))).toBe("0,00002 €");
    });

    it("zeigt bei fehlendem Wert und bei Null die gewohnten 0,00 €", () => {
        expect(ohneSonderleerzeichen(formatCurrency(undefined))).toBe("0,00 €");
        expect(ohneSonderleerzeichen(formatCurrency(0))).toBe("0,00 €");
    });
});
