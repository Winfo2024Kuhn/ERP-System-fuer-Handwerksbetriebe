import { useState } from "react";
import { ArrowRight, ImagePlus, Pencil, Plus } from "lucide-react";
import { Button } from "../../ui/button";
import { ImageViewer } from "../../ui/image-viewer";
import { ThumbnailImage } from "../../ui/ThumbnailImage";
import { toSafeResourceUrl } from "../../../lib/htmlSanitizer";
import type { ArtikelDetail } from "../../../types";
import { Datenfeld } from "./AngabenReiter";
import { AngebotsZeile } from "./AngebotsZeile";
import { LieferantenPreise } from "./LieferantenPreise";
import { PreisAendernInfo } from "./PreisAendernInfo";
import { einheitText, formatCurrency, formatDate, formatZahl } from "./format";

export type ArtikelReiterId = "uebersicht" | "preise" | "angaben" | "angebote" | "bilder";

interface Props {
    daten: ArtikelDetail;
    onGespeichert: () => Promise<void>;
    onZuReiter: (reiter: ArtikelReiterId) => void;
    onBearbeiten: () => void;
}

/**
 * Reiter „Übersicht“: das Wichtigste auf einen Blick - Bild, Kerndaten, bester
 * Preis und die Preise je Lieferant. Preise lassen sich hier direkt ändern,
 * ohne erst den Reiter zu wechseln.
 */
export function UebersichtReiter({ daten, onGespeichert, onZuReiter, onBearbeiten }: Props) {
    const { artikel, lieferanten } = daten;
    const [grossesBild, setGrossesBild] = useState(false);
    const bildUrl = toSafeResourceUrl(artikel.vorschaubildUrl);
    const bester = lieferanten.find((l) => l.guenstigster && l.preis != null);

    return (
        <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6">
                {/* ---- Bild ---- */}
                <div>
                    {bildUrl ? (
                        <button
                            type="button"
                            onClick={() => setGrossesBild(true)}
                            title="Bild groß anzeigen"
                            aria-label={`Bild von ${artikel.produktname} groß anzeigen`}
                            className="block w-full aspect-square rounded-lg border border-slate-200 overflow-hidden bg-white hover:opacity-90 transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                        >
                            <ThumbnailImage src={bildUrl} alt={`Vorschaubild: ${artikel.produktname}`} className="object-contain" />
                        </button>
                    ) : (
                        <div className="flex flex-col items-center justify-center gap-3 w-full aspect-square rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 text-center px-3">
                            <ImagePlus className="w-8 h-8 text-slate-300" aria-hidden="true" />
                            <p className="text-xs text-slate-500">Noch kein Bild hinterlegt</p>
                            <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                onClick={() => onZuReiter("bilder")}>
                                <Plus className="w-4 h-4 mr-1.5" aria-hidden="true" />
                                Bild hinzufügen
                            </Button>
                        </div>
                    )}
                </div>

                {/* ---- Kerndaten und bester Preis ---- */}
                <section className="bg-white border border-slate-200 rounded-lg p-5 min-w-0" aria-labelledby="uebersicht-kerndaten">
                    <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                        <h2 id="uebersicht-kerndaten" className="text-lg font-semibold text-slate-900">Auf einen Blick</h2>
                        <Button variant="ghost" size="sm" onClick={onBearbeiten}>
                            <Pencil className="w-4 h-4 mr-1.5" aria-hidden="true" />
                            Angaben ändern
                        </Button>
                    </div>

                    <dl className="grid grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-4 text-sm">
                        <Datenfeld label="Artikel-Nr." wert={artikel.artikelnummer} />
                        <Datenfeld label="Einheit" wert={einheitText(artikel.verrechnungseinheit)} />
                        <Datenfeld label="Kategorie" wert={artikel.kategoriePfad} />
                        <Datenfeld label="Werkstoff" wert={artikel.werkstoffName} />
                        <Datenfeld label="Abmessung" wert={artikel.abmessung} />
                        <Datenfeld label="Gewicht je Meter" wert={artikel.kgProMeter ? `${formatZahl(artikel.kgProMeter)} kg/m` : undefined} />
                    </dl>

                    <div className="mt-5 pt-4 border-t border-slate-100 space-y-2">
                        {bester ? (
                            <p className="text-sm text-slate-600" data-testid="bester-preis">
                                Bester Preis:{" "}
                                <span className="text-lg font-semibold text-slate-900 tabular-nums">{formatCurrency(bester.preis)}</span>
                                {" "}bei <span className="font-medium text-slate-900">{bester.lieferantName}</span>
                                <span className="text-slate-500"> · Stand {formatDate(bester.preisDatum)}</span>
                            </p>
                        ) : (
                            <p className="text-sm text-slate-500" data-testid="bester-preis">Noch kein Einkaufspreis hinterlegt.</p>
                        )}
                        <AngebotsZeile artikel={artikel} />
                    </div>
                </section>
            </div>

            {/* ---- Preise je Lieferant, direkt änderbar ---- */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
                    <div>
                        <div className="flex items-center gap-1">
                            <h2 className="text-lg font-semibold text-slate-900">Preise je Lieferant</h2>
                            <PreisAendernInfo />
                        </div>
                        {lieferanten.length > 0 && (
                            <p className="text-sm text-slate-500">Preis anklicken, neuen Preis tippen, Enter – fertig.</p>
                        )}
                    </div>
                    <Button variant="ghost" size="sm" onClick={() => onZuReiter("preise")}>
                        Alle Preise &amp; Verlauf
                        <ArrowRight className="w-4 h-4 ml-1.5" aria-hidden="true" />
                    </Button>
                </div>
                {lieferanten.length === 0 ? (
                    <p className="text-sm text-slate-500 py-4 text-center">
                        Noch kein Lieferantenpreis hinterlegt. Im Reiter „Preise &amp; Lieferanten“ kannst du einen eintragen.
                    </p>
                ) : (
                    <LieferantenPreise kompakt artikelId={artikel.id} lieferanten={lieferanten} onGespeichert={onGespeichert} />
                )}
            </section>

            {grossesBild && bildUrl && (
                <ImageViewer src={bildUrl} alt={artikel.produktname} onClose={() => setGrossesBild(false)} />
            )}
        </div>
    );
}
