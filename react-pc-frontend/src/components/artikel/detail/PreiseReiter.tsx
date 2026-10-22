import { useRef, useState } from "react";
import { Loader2, Plus, Save, Search } from "lucide-react";
import { Button } from "../../ui/button";
import { DecimalInput } from "../../ui/decimal-input";
import { Input } from "../../ui/input";
import { Label } from "../../ui/label";
import { useToast } from "../../ui/toast";
import { SupplierSelectModal } from "../../SupplierSelectModal";
import { parseDecimal } from "../../verrechnungslohnFormat";
import type { ArtikelDetail } from "../../../types";
import { LieferantenPreise } from "./LieferantenPreise";
import { PreisAendernInfo } from "./PreisAendernInfo";
import { Preisverlauf } from "./Preisverlauf";
import { letzteKategorie } from "./format";
import { speichereLieferantenpreis } from "./lieferantenpreis";

interface Props {
    daten: ArtikelDetail;
    onGespeichert: () => Promise<void>;
}

/** Reiter „Preise & Lieferanten“: Preise direkt in der Zeile ändern, weitere Lieferanten eintragen, Verlauf ansehen. */
export function PreiseReiter({ daten, onGespeichert }: Props) {
    const { artikel, lieferanten } = daten;
    return (
        <div className="space-y-6">
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <div className="mb-4">
                    <div className="flex items-center gap-1">
                        <h2 className="text-lg font-semibold text-slate-900">Preise &amp; Lieferanten</h2>
                        <PreisAendernInfo />
                    </div>
                    <p className="text-sm text-slate-500">
                        Jeder Lieferant führt den Artikel unter seiner eigenen Nummer.
                        Preis oder Nummer anklicken, um sie direkt zu ändern.
                    </p>
                </div>

                {lieferanten.length === 0 ? (
                    <p className="text-sm text-slate-500 py-6 text-center">
                        Für diesen Artikel ist noch kein Lieferantenpreis hinterlegt.
                    </p>
                ) : (
                    <LieferantenPreise artikelId={artikel.id} lieferanten={lieferanten} onGespeichert={onGespeichert} />
                )}

                <PreisNachtragen daten={daten} onGespeichert={onGespeichert} />
            </section>

            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-1">Preisverlauf</h2>
                <p className="text-sm text-slate-500 mb-4">
                    Wie sich der Preis über die Zeit entwickelt hat, je Lieferant.
                </p>
                <Preisverlauf staende={daten.preisverlauf} />
            </section>
        </div>
    );
}

const LEER = { lieferantId: 0, lieferantName: "", externeArtikelnummer: "", preis: "" };

/**
 * Preis eines weiteren Lieferanten eintragen - etwa nach einem Telefonat,
 * wenn kein Angebot per Mail vorliegt. Bekannte Lieferanten werden mit ihrer
 * Nummer vorbelegt; deren Preis aendert man aber schneller direkt in der Zeile.
 */
function PreisNachtragen({ daten, onGespeichert }: Props) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [zeigeLieferantenWahl, setZeigeLieferantenWahl] = useState(false);
    const [neuerPreis, setNeuerPreis] = useState(LEER);
    const [speichert, setSpeichert] = useState(false);
    const preisFeldRef = useRef<HTMLDivElement>(null);
    /**
     * Nummer, die dieser Lieferant schon fuer den Artikel hat. Steht sie
     * unveraendert im Feld, wird sie nicht mitgeschickt - der Server behaelt
     * die gespeicherte Nummer dann ohnehin.
     */
    const [bisherigeNummer, setBisherigeNummer] = useState<string | null>(null);

    const schliessen = () => {
        setOffen(false);
        setNeuerPreis(LEER);
        setBisherigeNummer(null);
    };

    /**
     * Uebernimmt einen Lieferanten ins Formular. Hat er den Artikel schon,
     * steht seine Nummer gleich im Feld - man muss sie nicht noch einmal
     * heraussuchen, nur um einen neuen Preis einzutragen.
     */
    const lieferantUebernehmen = (lieferantId: number, lieferantName: string) => {
        const vorhanden = daten.lieferanten.find((l) => l.lieferantId === lieferantId);
        const nummer = vorhanden?.externeArtikelnummer ?? "";
        setBisherigeNummer(vorhanden ? nummer : null);
        setNeuerPreis((p) => ({ ...p, lieferantId, lieferantName, externeArtikelnummer: nummer }));
        requestAnimationFrame(() => preisFeldRef.current?.querySelector("input")?.focus());
    };

    const speichern = async () => {
        if (!neuerPreis.lieferantId) {
            toast.error("Bitte zuerst einen Lieferanten auswählen.");
            return;
        }
        const betrag = parseDecimal(neuerPreis.preis);
        if (betrag === null || !Number.isFinite(betrag) || betrag < 0) {
            toast.error("Bitte einen gültigen Preis eingeben.");
            return;
        }
        // Leer oder unveraendert: nichts schicken, der Server behaelt die bisherige Nummer.
        const nummer = neuerPreis.externeArtikelnummer.trim();
        const neueNummer = nummer === "" || nummer === bisherigeNummer ? null : nummer;
        setSpeichert(true);
        try {
            await speichereLieferantenpreis(neuerPreis.lieferantId, {
                artikelId: daten.artikel.id,
                preis: betrag,
                externeArtikelnummer: neueNummer,
            });
            toast.success("Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.");
            schliessen();
            await onGespeichert();
        } catch (err) {
            console.error(err);
            toast.error(err instanceof Error ? err.message : "Der Preis konnte nicht gespeichert werden.");
        } finally {
            setSpeichert(false);
        }
    };

    if (!offen) {
        return (
            <div className="mt-4 pt-4 border-t border-slate-100">
                <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50"
                    onClick={() => { setOffen(true); setZeigeLieferantenWahl(true); }}>
                    <Plus className="w-4 h-4 mr-2" aria-hidden="true" />
                    Preis von weiterem Lieferanten eintragen
                </Button>
            </div>
        );
    }

    return (
        <div className="mt-5 pt-5 border-t border-slate-200">
            <h3 className="text-sm font-semibold text-slate-900 mb-3">
                {bisherigeNummer !== null ? "Neuer Preis" : "Preis nachtragen"}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-start">
                <div>
                    <Label htmlFor="lieferant-wahl">Lieferant</Label>
                    <button
                        id="lieferant-wahl"
                        type="button"
                        className="flex h-11 w-full items-center justify-between rounded-md border border-slate-300 bg-white px-3 text-left text-sm text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                        onClick={() => setZeigeLieferantenWahl(true)}
                    >
                        <span className="truncate">{neuerPreis.lieferantName || "Lieferant auswählen"}</span>
                        <Search className="w-4 h-4 shrink-0 text-slate-400" aria-hidden="true" />
                    </button>
                </div>
                <div>
                    <Label htmlFor="externe-nr">Dessen Artikelnummer</Label>
                    <Input id="externe-nr" className="min-h-[44px]"
                        value={neuerPreis.externeArtikelnummer}
                        onChange={(e) => setNeuerPreis((p) => ({ ...p, externeArtikelnummer: e.target.value }))}
                        aria-describedby="externe-nr-hinweis"
                        placeholder="z. B. 100234" />
                    <p id="externe-nr-hinweis" className="text-xs text-slate-500 mt-1">
                        Leer lassen = die bisherige Nummer bleibt.
                    </p>
                </div>
                <div>
                    <Label htmlFor="preis-eingabe">Preis in Euro</Label>
                    {/* Rahmen fuer den Fokus: DecimalInput reicht keinen ref durch. */}
                    <div ref={preisFeldRef}>
                    <DecimalInput id="preis-eingabe" className="min-h-[44px]" min={0} nullBehalten
                        value={neuerPreis.preis}
                        onChange={(preis) => setNeuerPreis((p) => ({ ...p, preis }))}
                        onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); void speichern(); } }}
                        placeholder="z. B. 12,50" />
                    </div>
                </div>
                <div className="flex gap-2 md:pt-[26px]">
                    <Button
                        className="flex-1 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 min-h-[44px]"
                        size="sm"
                        disabled={speichert}
                        onClick={speichern}
                    >
                        {speichert
                            ? <Loader2 className="w-4 h-4 mr-2 motion-safe:animate-spin" aria-hidden="true" />
                            : <Save className="w-4 h-4 mr-2" aria-hidden="true" />}
                        Preis speichern
                    </Button>
                    <Button variant="ghost" size="sm" className="min-h-[44px] text-slate-600 hover:bg-slate-100"
                        disabled={speichert} onClick={schliessen}>
                        Abbrechen
                    </Button>
                </div>
            </div>

            {zeigeLieferantenWahl && (
                <SupplierSelectModal
                    kategorieId={daten.artikel.kategorieId}
                    kategorieName={letzteKategorie(daten.artikel.kategoriePfad)}
                    onClose={() => setZeigeLieferantenWahl(false)}
                    onSelect={(lieferant: { id: number; name: string }) => {
                        lieferantUebernehmen(lieferant.id, lieferant.name);
                        setZeigeLieferantenWahl(false);
                    }}
                />
            )}
        </div>
    );
}
