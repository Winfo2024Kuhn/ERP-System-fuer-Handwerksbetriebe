import { preisHinweisText, type PreisHinweis } from "../preisHinweis";
import type { Artikel } from "../../../types";
import { formatCurrency } from "./format";

/** „So steht es im Angebot: 1 m à …“ – oder der Hinweis, warum sich noch kein Preis ergibt. */
export function AngebotsZeile({ artikel }: { artikel: Artikel }) {
    const hinweis = (artikel.preisHinweis ?? "KEIN_PREIS") as PreisHinweis;
    if (hinweis === "OK") {
        return (
            <p className="text-sm text-slate-600">
                So steht es im Angebot:{" "}
                <span className="font-semibold text-slate-900">
                    1 {artikel.positionsEinheit} à {formatCurrency(artikel.positionsEinzelpreis)}
                </span>
            </p>
        );
    }
    return (
        <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
            {preisHinweisText[hinweis]}
        </p>
    );
}
