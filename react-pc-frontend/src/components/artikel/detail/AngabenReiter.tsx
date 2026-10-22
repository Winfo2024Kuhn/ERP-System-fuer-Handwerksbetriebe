import { Pencil, ShieldCheck, ShieldX } from "lucide-react";
import { Button } from "../../ui/button";
import { ArtikelStammdatenForm } from "../ArtikelStammdatenForm";
import type { Artikel } from "../../../types";
import { einheitText, formatZahl } from "./format";

interface Props {
    artikel: Artikel;
    /** Ist die Bearbeiten-Maske offen? Steht in der Seite, damit sie einen Reiterwechsel übersteht. */
    bearbeiten: boolean;
    onBearbeiten: () => void;
    onAbbrechen: () => void;
    onGespeichert: () => void;
}

/** Reiter „Angaben zum Teil“: Merkmale, Maße, Normen und Oberfläche - und hier wird der Artikel bearbeitet. */
export function AngabenReiter({ artikel, bearbeiten, onBearbeiten, onAbbrechen, onGespeichert }: Props) {
    if (bearbeiten) {
        return (
            <ArtikelStammdatenForm
                key={`${artikel.id}-${artikel.version ?? 0}`}
                artikel={artikel}
                onAbbrechen={onAbbrechen}
                onGespeichert={onGespeichert}
            />
        );
    }

    return (
        <div className="space-y-6">
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                    <div>
                        <h2 className="text-lg font-semibold text-slate-900">Was ist das für ein Teil?</h2>
                        <p className="text-sm text-slate-500">Name, Nummern, Einheit, Kategorie, Werkstoff und Maße.</p>
                    </div>
                    <Button size="sm" onClick={onBearbeiten}>
                        <Pencil className="w-4 h-4 mr-2" aria-hidden="true" />
                        Stammdaten bearbeiten
                    </Button>
                </div>

                <div className="flex flex-wrap gap-2 mb-5">
                    {artikel.profilform && <Merkmalchip text={artikel.profilform.anzeigename} titel={artikel.profilform.erklaerung} />}
                    {artikel.herstellverfahren && <Merkmalchip text={artikel.herstellverfahren.anzeigename} titel={artikel.herstellverfahren.erklaerung} />}
                    {artikel.fertigungszustand && <Merkmalchip text={artikel.fertigungszustand.anzeigename} titel={artikel.fertigungszustand.erklaerung} />}
                    {artikel.werkstoffName && <Merkmalchip text={artikel.werkstoffName} />}
                </div>

                <dl className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 text-sm">
                    <Datenfeld label="Artikelnummer" wert={artikel.artikelnummer} />
                    <Datenfeld label="EAN / Strichcode" wert={artikel.ean ?? undefined} />
                    <Datenfeld label="Einheit" wert={einheitText(artikel.verrechnungseinheit)} />
                    <Datenfeld label="Kategorie" wert={artikel.kategoriePfad} />
                    <Datenfeld label="Produktlinie" wert={artikel.produktlinie} />
                    <Datenfeld label="Abmessung" wert={artikel.abmessung} />
                    <Datenfeld label="Durchmesser" wert={artikel.durchmesser ? `${formatZahl(artikel.durchmesser, 1)} mm` : undefined} />
                    <Datenfeld label="Wandstärke" wert={artikel.wandstaerke ? `${formatZahl(artikel.wandstaerke, 1)} mm` : undefined} />
                    <Datenfeld label="Höhe × Breite" wert={artikel.hoehe && artikel.breite
                        ? `${formatZahl(artikel.hoehe, 1)} × ${formatZahl(artikel.breite, 1)} mm` : undefined} />
                    <Datenfeld label="Gewicht je Meter" wert={artikel.kgProMeter ? `${formatZahl(artikel.kgProMeter)} kg/m` : undefined} />
                    <Datenfeld label="Gewicht je m²" wert={artikel.kgProQm ? `${formatZahl(artikel.kgProQm)} kg/m²` : undefined} />
                    <Datenfeld label="Oberfläche je Meter" wert={artikel.mantelflaeche ? `${formatZahl(artikel.mantelflaeche, 4)} m²/m` : undefined}
                        hinweis="Wird für die Kosten von Pulverbeschichtung und Anstrich gebraucht." />
                    <Datenfeld label="Übliche Länge" wert={artikel.standardlaenge ? `${formatZahl(artikel.standardlaenge, 0)} mm` : undefined} />
                    <Datenfeld label="Maßnorm" wert={artikel.massnorm} />
                    <Datenfeld label="Werkstoffnorm" wert={artikel.werkstoffnorm} />
                    <Datenfeld label="Verpackungseinheit" wert={artikel.verpackungseinheit && artikel.verpackungseinheit > 1
                        ? formatZahl(artikel.verpackungseinheit, 0) : undefined} />
                    <Datenfeld label="Preiseinheit" wert={artikel.preiseinheit && artikel.preiseinheit !== "1" ? artikel.preiseinheit : undefined} />
                </dl>
                {artikel.produkttext && (
                    <p className="mt-4 text-sm text-slate-600">
                        <span className="text-slate-500">Beschreibung (intern): </span>{artikel.produkttext}
                    </p>
                )}
            </section>

            {/* ============ Oberflächenbehandlung ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-1">Oberfläche</h2>
                <p className="text-sm text-slate-500 mb-4">
                    Was mit diesem Werkstoff möglich ist — wichtig für die Kalkulation.
                </p>
                <div className="flex flex-col sm:flex-row gap-3">
                    <Eignung ok={!!artikel.verzinkungsgeeignet} text="Feuerverzinken" quelle={eignungsQuelle(artikel.verzinkungsgeeignetEigen)} />
                    <Eignung ok={!!artikel.pulverbeschichtungsgeeignet} text="Pulverbeschichten" quelle={eignungsQuelle(artikel.pulverbeschichtungsgeeignetEigen)} />
                </div>
                {artikel.beschichtungshinweis && (
                    <p className="text-sm text-slate-600 mt-3">{artikel.beschichtungshinweis}</p>
                )}
            </section>
        </div>
    );
}

function Merkmalchip({ text, titel }: { text: string; titel?: string }) {
    return (
        <span title={titel}
            className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50
                       px-3 py-1 text-sm text-slate-700">
            {text}
        </span>
    );
}

/** Eine Angabe; fehlt der Wert, entfällt die ganze Zeile statt eines leeren Strichs. */
export function Datenfeld({ label, wert, hinweis }: { label: string; wert?: string | null; hinweis?: string }) {
    if (!wert) return null;
    return (
        <div className="min-w-0">
            <dt className="text-slate-500" title={hinweis}>{label}</dt>
            <dd className="font-medium text-slate-900 tabular-nums break-words">{wert}</dd>
        </div>
    );
}

/** Woher die Eignung kommt: vom Werkstoff oder für diesen Artikel festgelegt. */
const eignungsQuelle = (eigen?: boolean | null) => (eigen == null ? "vom Werkstoff" : "für diesen Artikel festgelegt");

/** Eignung mit Symbol UND Text - Farbe allein darf die Aussage nicht tragen. */
function Eignung({ ok, text, quelle }: { ok: boolean; text: string; quelle?: string }) {
    return (
        <div className={`flex items-center gap-2 rounded-lg border px-4 py-3 ${
            ok ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50"
        }`}>
            {ok
                ? <ShieldCheck className="w-5 h-5 text-emerald-700 shrink-0" aria-hidden="true" />
                : <ShieldX className="w-5 h-5 text-slate-500 shrink-0" aria-hidden="true" />}
            <div>
                <p className={`text-sm font-semibold ${ok ? "text-emerald-800" : "text-slate-700"}`}>{text}</p>
                <p className={`text-xs ${ok ? "text-emerald-700" : "text-slate-500"}`}>
                    <span>{ok ? "möglich" : "nicht möglich"}</span>
                    {quelle && <span className="text-slate-500" data-testid="eignung-quelle"> ({quelle})</span>}
                </p>
            </div>
        </div>
    );
}
