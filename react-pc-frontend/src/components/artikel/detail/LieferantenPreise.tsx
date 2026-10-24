import { useRef, useState, type FocusEvent, type KeyboardEvent, type ReactNode } from "react";
import { Check, Loader2, Pencil, TrendingDown, X } from "lucide-react";
import { Button } from "../../ui/button";
import { DecimalInput } from "../../ui/decimal-input";
import { Input } from "../../ui/input";
import { useToast } from "../../ui/toast";
import { cn } from "../../../lib/utils";
import { parseDecimal } from "../../verrechnungslohnFormat";
import type { ArtikelLieferantEintrag } from "../../../types";
import { formatCurrency, formatDate, formatZahl, zahlAlsEingabe } from "./format";
import { speichereLieferantenpreis } from "./lieferantenpreis";

interface Props {
    artikelId: number;
    lieferanten: ArtikelLieferantEintrag[];
    /** Nach dem Speichern: Artikeldaten neu laden, damit Bestpreis und Verlauf stimmen. */
    onGespeichert: () => Promise<void>;
    /** Schmale Fassung für die Übersicht: nur Lieferant, Preis und Stand. */
    kompakt?: boolean;
}

/**
 * Lieferanten mit ihrem aktuellen Preis. Preis und Lieferanten-Nummer lassen
 * sich direkt in der Zeile ändern: anklicken, neuen Wert tippen, Enter.
 * Escape verwirft die Eingabe. Der alte Preis bleibt im Preisverlauf stehen.
 */
export function LieferantenPreise({ artikelId, lieferanten, onGespeichert, kompakt = false }: Props) {
    return (
        <div className="overflow-x-auto -m-1 p-1">
            <table className="w-full text-sm">
                <caption className="sr-only">
                    {kompakt
                        ? "Lieferanten mit aktuellem Preis, günstigster zuerst"
                        : "Lieferanten mit Artikelnummer und aktuellem Preis, günstigster zuerst"}
                </caption>
                <thead>
                    <tr className="text-left text-slate-500 border-b border-slate-200">
                        <th scope="col" className="py-2 pr-4 font-medium">Lieferant</th>
                        {!kompakt && <th scope="col" className="py-2 pr-4 font-medium">Dessen Artikelnummer</th>}
                        {/* Feste Breite: Beim Bearbeiten (Feld plus Haken und X) springen die Spalten nicht. */}
                        <th scope="col" className="py-2 pr-4 font-medium text-right w-56">Preis</th>
                        {!kompakt && <th scope="col" className="py-2 pr-4 font-medium text-right">Unterschied</th>}
                        <th scope="col" className={cn("py-2 font-medium", !kompakt && "pr-4")}>Stand</th>
                        {!kompakt && <th scope="col" className="py-2 font-medium">Herkunft</th>}
                    </tr>
                </thead>
                <tbody>
                    {lieferanten.map((l) => (
                        // Schluessel ist der Lieferant, nicht der Preisstand: Nach dem
                        // Speichern bekommt die Zeile einen neuen Preisstand, soll aber
                        // dieselbe bleiben, damit der Fokus auf ihrem Knopf landet.
                        <PreisZeile key={l.lieferantId} eintrag={l} artikelId={artikelId}
                            onGespeichert={onGespeichert} kompakt={kompakt} />
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function PreisZeile({ eintrag: l, artikelId, onGespeichert, kompakt }: {
    eintrag: ArtikelLieferantEintrag;
    artikelId: number;
    onGespeichert: () => Promise<void>;
    kompakt: boolean;
}) {
    const toast = useToast();

    const preisSpeichern = async (entwurf: string): Promise<boolean> => {
        const betrag = parseDecimal(entwurf);
        if (betrag === null || !Number.isFinite(betrag) || betrag < 0) {
            toast.error("Bitte einen gültigen Preis eingeben, z. B. 12,50.");
            return false;
        }
        // Nichts geaendert: kein neuer Preisstand, einfach zurueck zur Anzeige.
        if (betrag === l.preis) return true;
        try {
            // Nummer bleibt, wie sie ist - nur der Preis ist neu.
            await speichereLieferantenpreis(l.lieferantId, { artikelId, preis: betrag, externeArtikelnummer: null });
            toast.success("Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.");
            await onGespeichert();
            return true;
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Der Preis konnte nicht gespeichert werden.");
            return false;
        }
    };

    const nummerSpeichern = async (entwurf: string): Promise<boolean> => {
        const nummer = entwurf.trim();
        if (nummer === (l.externeArtikelnummer ?? "")) return true;
        if (nummer === "") {
            toast.error("Bitte eine Nummer eingeben. Leer lassen geht hier nicht – die bisherige Nummer bliebe sonst stehen.");
            return false;
        }
        // Der Server speichert die Nummer immer zusammen mit einem Preis.
        if (l.preis == null) {
            toast.error("Bitte zuerst einen Preis eintragen – die Nummer wird zusammen mit dem Preis gespeichert.");
            return false;
        }
        try {
            await speichereLieferantenpreis(l.lieferantId, { artikelId, preis: l.preis, externeArtikelnummer: nummer });
            toast.success("Die Nummer beim Lieferanten ist gespeichert.");
            await onGespeichert();
            return true;
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Die Nummer konnte nicht gespeichert werden.");
            return false;
        }
    };

    return (
        <tr className="border-b border-slate-100 last:border-0">
            <td className="py-2 pr-4">
                <span className="font-medium text-slate-900">{l.lieferantName}</span>
                {l.guenstigster && (
                    // Nicht nur Farbe: Text plus Symbol kennzeichnen den guenstigsten.
                    <span className="ml-2 inline-flex items-center gap-1 text-xs font-semibold whitespace-nowrap
                        text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-1.5 py-0.5">
                        <TrendingDown className="w-3 h-3" aria-hidden="true" />
                        günstigster
                    </span>
                )}
            </td>
            {!kompakt && (
                <td className="py-2 pr-4 text-slate-600">
                    <DirektFeld
                        was="Nummer"
                        anzeige={l.externeArtikelnummer || "—"}
                        knopfLabel={`Nummer ändern bei ${l.lieferantName}${l.externeArtikelnummer ? `, bisher ${l.externeArtikelnummer}` : ""}`}
                        eingabeLabel={`Neue Nummer bei ${l.lieferantName}`}
                        startwert={l.externeArtikelnummer ?? ""}
                        onSpeichern={nummerSpeichern}
                    />
                </td>
            )}
            <td className="py-2 pr-4 text-right tabular-nums font-medium text-slate-900">
                <DirektFeld
                    was="Preis"
                    rechts
                    anzeige={formatCurrency(l.preis)}
                    knopfLabel={l.preis == null
                        ? `Preis eintragen bei ${l.lieferantName}`
                        : `Preis ändern bei ${l.lieferantName}, bisher ${formatCurrency(l.preis)}`}
                    eingabeLabel={`Neuer Preis in Euro bei ${l.lieferantName}`}
                    startwert={zahlAlsEingabe(l.preis)}
                    inputMode="decimal"
                    onSpeichern={preisSpeichern}
                />
            </td>
            {!kompakt && (
                <td className="py-2 pr-4 text-right tabular-nums text-slate-600 whitespace-nowrap">
                    {l.guenstigster
                        ? <span className="text-slate-400">—</span>
                        : l.aufschlagProzent !== undefined
                            ? <span className="text-amber-700">+{formatZahl(l.aufschlagProzent, 1)} %</span>
                            : "—"}
                </td>
            )}
            <td className={cn("py-2 text-slate-600 whitespace-nowrap", !kompakt && "pr-4")}>{formatDate(l.preisDatum)}</td>
            {!kompakt && <td className="py-2 text-slate-500">{l.quelle?.anzeigename ?? "—"}</td>}
        </tr>
    );
}

/**
 * Wert in einer Tabellenzelle, der sich an Ort und Stelle ändern lässt.
 * Klick auf den Wert oder den Stift öffnet ein Eingabefeld; Enter oder das
 * Häkchen speichern, Escape oder das X verwerfen. Danach steht der Fokus
 * wieder auf dem Wert, damit man per Tastatur gleich weitermachen kann.
 */
function DirektFeld({ was, anzeige, knopfLabel, eingabeLabel, startwert, inputMode, rechts = false, onSpeichern }: {
    /** Was hier geändert wird ("Preis", "Nummer") - für die Namen von Häkchen und X. */
    was: string;
    anzeige: ReactNode;
    knopfLabel: string;
    eingabeLabel: string;
    startwert: string;
    inputMode?: "decimal" | "text";
    rechts?: boolean;
    /** Liefert `true`, wenn fertig (Feld schließen), `false`, wenn das Feld offen bleiben soll. */
    onSpeichern: (entwurf: string) => Promise<boolean>;
}) {
    const [bearbeitet, setBearbeitet] = useState(false);
    const [entwurf, setEntwurf] = useState("");
    const [speichert, setSpeichert] = useState(false);
    const knopfRef = useRef<HTMLButtonElement>(null);
    // Ueber den Rahmen statt direkt am Feld: DecimalInput reicht keinen ref durch.
    const feldRahmenRef = useRef<HTMLDivElement>(null);

    const zurueckZumKnopf = () => requestAnimationFrame(() => knopfRef.current?.focus());

    const abbrechen = () => {
        if (speichert) return;
        setBearbeitet(false);
        zurueckZumKnopf();
    };

    const speichern = async () => {
        if (speichert) return;
        setSpeichert(true);
        let fertig: boolean;
        try {
            fertig = await onSpeichern(entwurf);
        } finally {
            setSpeichert(false);
        }
        if (fertig) {
            setBearbeitet(false);
            zurueckZumKnopf();
        } else {
            requestAnimationFrame(() => feldRahmenRef.current?.querySelector("input")?.focus());
        }
    };

    if (!bearbeitet) {
        return (
            <button
                ref={knopfRef}
                type="button"
                aria-label={knopfLabel}
                title={knopfLabel}
                onClick={() => { setEntwurf(startwert); setBearbeitet(true); }}
                className={cn(
                    "group inline-flex items-center gap-1.5 rounded-md px-2 py-1 -mx-2 -my-1 min-h-[36px]",
                    "hover:bg-rose-50 hover:text-rose-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500",
                    rechts && "flex-row-reverse",
                )}
            >
                <Pencil className="w-3.5 h-3.5 text-slate-400 group-hover:text-rose-600" aria-hidden="true" />
                <span>{anzeige}</span>
            </button>
        );
    }

    const feldProps = {
        autoFocus: true,
        "aria-label": eingabeLabel,
        disabled: speichert,
        onFocus: (e: FocusEvent<HTMLInputElement>) => e.currentTarget.select(),
        onKeyDown: (e: KeyboardEvent<HTMLInputElement>) => {
            if (e.key === "Enter") { e.preventDefault(); void speichern(); }
            else if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); abbrechen(); }
        },
        className: cn("h-9 w-28 border-rose-300 tabular-nums", rechts && "text-right"),
    };

    return (
        <div ref={feldRahmenRef} className={cn("inline-flex items-center gap-1", rechts && "justify-end")}>
            {inputMode === "decimal"
                ? <DecimalInput {...feldProps} value={entwurf} onChange={setEntwurf} nullBehalten min={0} />
                : <Input {...feldProps} value={entwurf} onChange={(e) => setEntwurf(e.target.value)} />}
            <Button type="button" variant="ghost" size="sm" onClick={() => void speichern()} disabled={speichert}
                aria-label={`${was} speichern`} title={`${was} speichern (Enter)`}
                className="h-9 w-9 px-0">
                {speichert
                    ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                    : <Check className="w-4 h-4" aria-hidden="true" />}
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={abbrechen} disabled={speichert}
                aria-label={`${was} nicht ändern`} title="Verwerfen (Escape)"
                className="h-9 w-9 px-0 text-slate-500 hover:bg-slate-100">
                <X className="w-4 h-4" aria-hidden="true" />
            </Button>
            {speichert && <span className="sr-only" role="status">Wird gespeichert …</span>}
        </div>
    );
}
