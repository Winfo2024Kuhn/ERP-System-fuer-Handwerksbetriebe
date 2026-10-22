// Dieselbe Waehrungsformatierung wie in der Trefferliste - inklusive der
// zusaetzlichen Nachkommastellen bei Kleinstpreisen (Schrauben, Muttern).
import { formatCurrency as formatEuro } from "../formatCurrency";
import type { Artikel } from "../../../types";

/** Wie in der Liste - nur steht hier ein Strich, wo gar kein Preis gepflegt ist. */
export const formatCurrency = (val?: number | null) =>
    val === undefined || val === null ? "—" : formatEuro(val);

export const formatDate = (dateStr?: string) =>
    dateStr ? new Date(dateStr).toLocaleDateString("de-DE") : "—";

export const formatZahl = (val?: number | null, nachkomma = 3) =>
    val === undefined || val === null
        ? "—"
        : new Intl.NumberFormat("de-DE", { maximumFractionDigits: nachkomma }).format(val);

/** Preis als Eingabetext im deutschen Format ("12,5") - ohne Tausenderpunkte, ohne Euro-Zeichen. */
export const zahlAlsEingabe = (wert?: number | null) =>
    wert == null ? "" : String(wert).replace(".", ",");

/** Letzter Teil des Kategorie-Pfads ("Werkstoffe > Rohre" -> "Rohre"). */
export const letzteKategorie = (pfad?: string) => pfad?.split(">").pop()?.trim() || undefined;

/** Einheit des Artikels im Klartext ("Laufende Meter"), egal ob als Text oder als Objekt geliefert. */
export function einheitText(einheit: Artikel["verrechnungseinheit"]): string | undefined {
    if (!einheit) return undefined;
    if (typeof einheit === "string") return EINHEIT_NAMEN[einheit] ?? einheit;
    return einheit.anzeigename || EINHEIT_NAMEN[einheit.name] || einheit.name;
}

const EINHEIT_NAMEN: Record<string, string> = {
    LAUFENDE_METER: "Laufende Meter",
    QUADRATMETER: "Quadratmeter",
    KILOGRAMM: "Kilogramm",
    STUECK: "Stück",
    LITER: "Liter",
};
