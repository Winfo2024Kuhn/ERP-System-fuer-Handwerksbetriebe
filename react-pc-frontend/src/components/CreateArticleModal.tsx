import { useEffect, useRef, useState } from "react";
import { ArrowRight, FileText, ImageOff, ImagePlus, Loader2, Paperclip, Plus, Search, Trash2, X } from "lucide-react";
import { Button } from "./ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { DecimalInput } from "./ui/decimal-input";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select } from "./ui/select-custom";
import { Reiter, ReiterPanel, type ReiterEintrag } from "./ui/reiter";
import { CategoryTreeModal } from "./CategoryTreeModal";
import { SupplierSelectModal } from "./SupplierSelectModal";
import { KategorieAuswahlFeld } from "./artikel/KategorieAuswahlFeld";
import { useToast } from './ui/toast';
import { InfoButton } from './ui/info-button';
import { parseDecimal } from './verrechnungslohnFormat';
import { leseFehlermeldung } from '../lib/apiFehler';
import { ARTIKEL_DOKUMENT_TYPEN, type Artikel, type ArtikelDokumentTyp } from '../types';

interface CreateArticleModalProps {
    onClose: () => void;
    /** Der Artikel ist angelegt (Dateien sind hochgeladen oder als Warnung gemeldet). */
    onSave: (artikel: Artikel) => void;
}

const VERRECHNUNGSEINHEITEN = [
    { value: "STUECK", label: "Stück" },
    { value: "LAUFENDE_METER", label: "Laufende Meter" },
    { value: "QUADRATMETER", label: "Quadratmeter" },
    { value: "KILOGRAMM", label: "Kilogramm" },
    { value: "LITER", label: "Liter" },
];

/** Warum Preis und Lieferanten-Nummer gesperrt sind, solange kein Lieferant gewählt ist. */
const ERST_LIEFERANT_HINWEIS =
    "Erst den Lieferanten wählen – Preis und Lieferanten-Nummer gehören immer zu einem Lieferanten.";

/** Wie auf der Artikelseite (ArtikelDokumente): Bilder für das Vorschaubild, PDF und Bilder für Unterlagen. */
const BILD_TYPEN = ".png,.jpg,.jpeg,.webp,.gif,image/png,image/jpeg,image/webp,image/gif";
const UNTERLAGEN_TYPEN = ".pdf,.png,.jpg,.jpeg,.webp,.gif,application/pdf,image/png,image/jpeg,image/webp,image/gif";
const UNTERLAGEN_ARTEN = ARTIKEL_DOKUMENT_TYPEN.filter((t) => t.value !== "VORSCHAUBILD");

type AnlegenReiterId = "wichtig" | "preis" | "bilder" | "mehr";
const REIHENFOLGE: AnlegenReiterId[] = ["wichtig", "preis", "bilder", "mehr"];

interface Unterlage { schluessel: number; datei: File; typ: ArtikelDokumentTyp }

const typLabel = (typ: ArtikelDokumentTyp) => ARTIKEL_DOKUMENT_TYPEN.find((t) => t.value === typ)?.label ?? typ;

/**
 * Neuen Artikel anlegen - aufgeteilt in Reiter von Pflicht bis optional,
 * damit niemand von zwanzig Feldern auf einmal erschlagen wird. Nur der Name
 * ist Pflicht; "Artikel anlegen" geht von jedem Reiter aus. Die Artikel-Nr.
 * vergibt der Server automatisch, solange man keine eigene eintraegt.
 * Gewaehlte Bilder und Unterlagen werden direkt nach dem Anlegen hochgeladen.
 */
export function CreateArticleModal({ onClose, onSave }: CreateArticleModalProps) {
    const toast = useToast();
    const [reiter, setReiter] = useState<AnlegenReiterId>("wichtig");

    // Das Wichtigste
    const [produktname, setProduktname] = useState("");
    const [nameFehlt, setNameFehlt] = useState(false);
    const [einheit, setEinheit] = useState("STUECK");
    const [kategorie, setKategorie] = useState<{ id: number; name: string } | null>(null);
    const [werkstoffId, setWerkstoffId] = useState("");
    // Werkstoffe mit ID, weil der Artikel die ID braucht - nicht nur den Namen.
    const [werkstoffe, setWerkstoffe] = useState<{ id: number; name: string }[]>([]);
    const [nummerModus, setNummerModus] = useState<"automatisch" | "eigen">("automatisch");
    const [eigeneNummer, setEigeneNummer] = useState("");
    const [vorschau, setVorschau] = useState<{ status: "laedt" | "da" | "fehler"; nummer?: string }>({ status: "laedt" });

    // Preis & Lieferant
    const [lieferant, setLieferant] = useState<{ id: number; name: string } | null>(null);
    const [lieferantenNummer, setLieferantenNummer] = useState("");
    // Als Text: leer heisst "kein Preis", nicht 0 Euro.
    const [preis, setPreis] = useState("");

    // Bilder & Unterlagen
    const [bild, setBild] = useState<File | null>(null);
    const [unterlagen, setUnterlagen] = useState<Unterlage[]>([]);
    const [unterlagenArt, setUnterlagenArt] = useState<ArtikelDokumentTyp>("DATENBLATT");
    const naechsterSchluessel = useRef(1);

    // Mehr Angaben
    const [produktlinie, setProduktlinie] = useState("");
    const [produkttext, setProdukttext] = useState("");
    const [verpackungseinheit, setVerpackungseinheit] = useState("1");
    const [preiseinheit, setPreiseinheit] = useState("1");

    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [loading, setLoading] = useState(false);
    // Sperrt einen zweiten Klick, bevor React den Ladezustand gezeichnet hat.
    const sendet = useRef(false);

    const nameRef = useRef<HTMLInputElement>(null);
    const eigeneNummerRef = useRef<HTMLInputElement>(null);
    const bildInputRef = useRef<HTMLInputElement>(null);
    const unterlagenInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => { nameRef.current?.focus(); }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/artikel/werkstoffe/details')
            .then(res => {
                if (!res.ok) throw new Error("Werkstoffe konnten nicht geladen werden");
                return res.json();
            })
            // Bei einem Fehler antwortet der Server mit einem Objekt statt einer
            // Liste - ohne diese Pruefung wuerde die Auswahlliste die Seite abschiessen.
            .then(data => { if (!abgebrochen) setWerkstoffe(Array.isArray(data) ? data : []); })
            .catch(err => console.error("Fehler beim Laden der Werkstoffe", err));
        // Nur eine Vorschau - reserviert wird nichts. Klappt sie nicht, vergibt
        // der Server die Nummer trotzdem beim Anlegen.
        fetch('/api/artikel/naechste-artikelnummer')
            .then(res => (res.ok ? res.json() : Promise.reject(new Error("Keine Vorschau"))))
            .then((data: { artikelnummer?: unknown }) => {
                if (abgebrochen) return;
                const nummer = typeof data?.artikelnummer === "string" ? data.artikelnummer.trim() : "";
                setVorschau(nummer ? { status: "da", nummer } : { status: "fehler" });
            })
            .catch(() => { if (!abgebrochen) setVorschau({ status: "fehler" }); });
        return () => { abgebrochen = true; };
    }, []);

    const mitLieferant = lieferant !== null;
    const anzahlDateien = (bild ? 1 : 0) + unterlagen.length;
    const mehrAusgefuellt = produktlinie.trim() !== "" || produkttext.trim() !== ""
        || verpackungseinheit.trim() !== "1" || preiseinheit.trim() !== "1";

    const reiterListe: ReiterEintrag<AnlegenReiterId>[] = [
        {
            id: "wichtig", label: "Das Wichtigste",
            kennzeichen: produktname.trim() ? { art: "haken", text: "ausgefüllt" } : undefined,
        },
        {
            id: "preis", label: "Preis & Lieferant", zusatz: "optional",
            kennzeichen: mitLieferant ? { art: "haken", text: "ausgefüllt" } : undefined,
        },
        {
            id: "bilder", label: "Bilder & Unterlagen", zusatz: "optional",
            kennzeichen: anzahlDateien > 0
                ? { art: "haken", text: anzahlDateien === 1 ? "1 Datei gewählt" : `${anzahlDateien} Dateien gewählt` }
                : undefined,
        },
        {
            id: "mehr", label: "Mehr Angaben", zusatz: "optional",
            kennzeichen: mehrAusgefuellt ? { art: "haken", text: "ausgefüllt" } : undefined,
        },
    ];
    const istLetzterReiter = reiter === REIHENFOLGE[REIHENFOLGE.length - 1];

    const eigeneNummerVergeben = () => {
        setEigeneNummer(vorschau.nummer ?? "");
        setNummerModus("eigen");
        requestAnimationFrame(() => eigeneNummerRef.current?.select());
    };

    /**
     * Was als Artikelnummer geschickt wird: `null` heisst "Server vergibt die
     * naechste freie". Das gilt auch, wenn im eigenen Feld nur die unveraenderte
     * Vorschau oder nichts steht - sonst wuerde eine inzwischen vergebene
     * Vorschau-Nummer als "schon vergeben" abgelehnt.
     */
    const zuSendendeNummer = (): string | null => {
        if (nummerModus === "automatisch") return null;
        const nummer = eigeneNummer.trim();
        return nummer === "" || nummer === vorschau.nummer ? null : nummer;
    };

    const zumNamen = () => {
        setReiter("wichtig");
        setNameFehlt(true);
        requestAnimationFrame(() => nameRef.current?.focus());
    };

    const bildWaehlen = (e: React.ChangeEvent<HTMLInputElement>) => {
        const datei = e.target.files?.[0];
        e.target.value = "";
        if (datei) setBild(datei);
    };

    const unterlagenWaehlen = (e: React.ChangeEvent<HTMLInputElement>) => {
        const dateien = Array.from(e.target.files ?? []);
        e.target.value = "";
        if (dateien.length === 0) return;
        setUnterlagen((alt) => [
            ...alt,
            ...dateien.map((datei) => ({ schluessel: naechsterSchluessel.current++, datei, typ: unterlagenArt })),
        ]);
    };

    /** Laedt eine Datei zum frisch angelegten Artikel hoch; `false`, wenn es nicht geklappt hat. */
    const hochladen = async (artikelId: number, datei: File, typ: ArtikelDokumentTyp): Promise<boolean> => {
        try {
            const formData = new FormData();
            formData.append("datei", datei);
            formData.append("typ", typ);
            const res = await fetch(`/api/artikel/${encodeURIComponent(artikelId)}/dokumente`, { method: "POST", body: formData });
            return res.ok;
        } catch (err) {
            console.error(err);
            return false;
        }
    };

    const handleSubmit = async () => {
        if (sendet.current) return;
        if (!produktname.trim()) {
            toast.warning("Bitte zuerst einen Produktnamen eingeben.");
            zumNamen();
            return;
        }
        const preisText = mitLieferant ? preis.trim() : "";
        const preisWert = preisText === "" ? null : parseDecimal(preisText);
        if (preisText !== "" && (preisWert === null || !Number.isFinite(preisWert) || preisWert < 0)) {
            setReiter("preis");
            toast.error("Bitte einen gültigen Preis eingeben, z. B. 12,50.");
            return;
        }
        const vpeText = verpackungseinheit.trim();
        if (!/^\d+$/.test(vpeText) || Number(vpeText) < 1) {
            setReiter("mehr");
            toast.error("Bitte bei „VPE (Menge)“ eine ganze Zahl ab 1 eingeben.");
            return;
        }

        sendet.current = true;
        setLoading(true);
        let artikel: Artikel;
        try {
            const payload = {
                produktname: produktname.trim(),
                produktlinie: produktlinie.trim(),
                produkttext: produkttext.trim(),
                // Ohne Lieferant gibt es weder Preis noch Lieferanten-Nummer.
                externeArtikelnummer: mitLieferant ? lieferantenNummer.trim() || null : null,
                artikelnummer: zuSendendeNummer(),
                verpackungseinheit: Number(vpeText),
                preiseinheit: preiseinheit.trim() || "1",
                verrechnungseinheit: einheit,
                kategorieId: kategorie?.id ?? null,
                werkstoffId: werkstoffId ? Number(werkstoffId) : null,
                preis: preisWert,
                lieferantId: lieferant?.id ?? null,
            };

            const res = await fetch('/api/artikel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, "Artikel konnte nicht angelegt werden."));
            artikel = await res.json();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Artikel konnte nicht angelegt werden.");
            console.error(err);
            sendet.current = false;
            setLoading(false);
            return;
        }

        // Der Artikel steht. Dateien nacheinander hochladen - schlaegt eine fehl,
        // bleibt der Artikel trotzdem angelegt und die Datei laesst sich nachreichen.
        const uploads: { datei: File; typ: ArtikelDokumentTyp }[] = [
            ...(bild ? [{ datei: bild, typ: "VORSCHAUBILD" as ArtikelDokumentTyp }] : []),
            ...unterlagen.map((u) => ({ datei: u.datei, typ: u.typ })),
        ];
        const fehlgeschlagen: string[] = [];
        for (const u of uploads) {
            if (!(await hochladen(artikel.id, u.datei, u.typ))) fehlgeschlagen.push(u.datei.name);
        }
        if (fehlgeschlagen.length === 1) {
            toast.warning(`„${fehlgeschlagen[0]}“ konnte nicht hochgeladen werden – du kannst sie auf der Artikelseite nachreichen.`);
        } else if (fehlgeschlagen.length > 1) {
            toast.warning(`${fehlgeschlagen.map((n) => `„${n}“`).join(", ")} konnten nicht hochgeladen werden – du kannst sie auf der Artikelseite nachreichen.`);
        }
        toast.success(artikel.artikelnummer
            ? `Artikel ${artikel.artikelnummer} ist angelegt.`
            : "Der Artikel ist angelegt.");
        onSave(artikel);
    };

    const werkstoffOptionen = [
        { value: "", label: "– kein Werkstoff –" },
        ...werkstoffe.map(w => ({ value: String(w.id), label: w.name })),
    ];

    const vorschauText = vorschau.status === "laedt"
        ? "wird ermittelt …"
        : vorschau.status === "da"
            ? `${vorschau.nummer} · wird automatisch vergeben`
            : "wird beim Anlegen automatisch vergeben";

    return (
        <Dialog
            open
            onOpenChange={(offen) => { if (!offen && !loading) onClose(); }}
            className="w-full max-w-4xl h-[min(42rem,calc(100vh-2rem))]"
            aria-labelledby="artikel-anlegen-titel"
        >
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id="artikel-anlegen-titel" className="text-xl text-slate-900">Neuen Artikel anlegen</DialogTitle>
                    <DialogDescription>
                        Nur der Name ist Pflicht. Alles andere kannst du gleich oder später auf der Artikelseite ergänzen.
                    </DialogDescription>
                </DialogHeader>

                <Reiter reiter={reiterListe} aktiv={reiter} onWechsel={setReiter}
                    ariaLabel="Bereiche des neuen Artikels" idPrefix="artikel-anlegen" />

                {/* Feste Hoehe: Der Dialog springt beim Reiterwechsel nicht; laengere Reiter scrollen innen. */}
                <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                    {reiter === "wichtig" && (
                        <ReiterPanel idPrefix="artikel-anlegen" id="wichtig" className="space-y-4">
                            <div className="space-y-1.5">
                                <Label htmlFor="artikel-produktname">Produktname *</Label>
                                <Input
                                    id="artikel-produktname"
                                    ref={nameRef}
                                    value={produktname}
                                    maxLength={255}
                                    aria-invalid={nameFehlt && !produktname.trim() ? true : undefined}
                                    aria-describedby={nameFehlt && !produktname.trim() ? "artikel-produktname-fehlt" : undefined}
                                    onChange={e => { setProduktname(e.target.value); setNameFehlt(false); }}
                                    onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); void handleSubmit(); } }}
                                    placeholder="z. B. Flachstahl 20 x 10"
                                />
                                {nameFehlt && !produktname.trim() && (
                                    <p id="artikel-produktname-fehlt" className="text-sm text-rose-700">Bitte einen Produktnamen eingeben.</p>
                                )}
                            </div>

                            <div className="space-y-1.5">
                                <div className="flex items-center gap-1">
                                    <Label htmlFor={nummerModus === "eigen" ? "artikel-eigene-nr" : undefined} className="mb-0">Artikel-Nr.</Label>
                                    <InfoButton titel="Artikel-Nr.">
                                        <p className="font-semibold text-slate-900">Was passiert im Hintergrund?</p>
                                        <p>
                                            Lässt du die Nummer auf automatisch, bekommt der Artikel beim Anlegen die nächste freie
                                            Nummer (A- und eine fortlaufende Zahl). Die angezeigte Nummer ist eine Vorschau: Legt
                                            gleichzeitig jemand anderes einen Artikel an, kann es eine Nummer weiter sein.
                                        </p>
                                        <p className="mt-2 font-semibold text-slate-900">Was bewirkt das?</p>
                                        <p>
                                            Die Nummer steht in der Artikelliste in der Spalte „Artikel-Nr.“ und gibt es nur einmal.
                                            Mit „Eigene Nummer“ vergibst du sie selbst. Die Nummern deiner Lieferanten bleiben davon unberührt.
                                        </p>
                                    </InfoButton>
                                </div>
                                {nummerModus === "automatisch" ? (
                                    <div className="flex min-h-10 items-center justify-between gap-2 rounded-md border border-dashed border-slate-300 bg-slate-50 pl-3 pr-1 md:max-w-md">
                                        <span className="min-w-0 text-sm text-slate-700" data-testid="artikelnummer-vorschau">
                                            {vorschauText}
                                        </span>
                                        <Button variant="ghost" size="sm" className="shrink-0" onClick={eigeneNummerVergeben}>
                                            Eigene Nummer
                                        </Button>
                                    </div>
                                ) : (
                                    <div className="flex gap-2 md:max-w-md">
                                        <Input
                                            id="artikel-eigene-nr"
                                            ref={eigeneNummerRef}
                                            value={eigeneNummer}
                                            maxLength={64}
                                            onChange={e => setEigeneNummer(e.target.value)}
                                            placeholder="z. B. FL-20-10"
                                        />
                                        <Button variant="ghost" size="sm" className="shrink-0 min-h-10"
                                            onClick={() => setNummerModus("automatisch")}>
                                            Automatisch
                                        </Button>
                                    </div>
                                )}
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-einheit">Einheit</Label>
                                    <Select id="artikel-einheit" aria-label="Einheit" options={VERRECHNUNGSEINHEITEN} value={einheit} onChange={setEinheit} />
                                    <p className="text-xs text-slate-500">In dieser Einheit wird eingekauft, verkauft und gerechnet.</p>
                                </div>
                                <div className="space-y-1.5">
                                    <Label>Kategorie</Label>
                                    <KategorieAuswahlFeld name={kategorie?.name ?? ""} gewaehlt={kategorie !== null}
                                        onOeffnen={() => setShowCategoryModal(true)} onEntfernen={() => setKategorie(null)} />
                                </div>
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-werkstoff">Werkstoff</Label>
                                    <Select id="artikel-werkstoff" aria-label="Werkstoff" options={werkstoffOptionen} value={werkstoffId}
                                        onChange={setWerkstoffId} placeholder="Werkstoff wählen" />
                                </div>
                            </div>
                        </ReiterPanel>
                    )}

                    {reiter === "preis" && (
                        <ReiterPanel idPrefix="artikel-anlegen" id="preis" className="space-y-4">
                            <p className="text-sm text-slate-500">
                                Wo kaufst du den Artikel ein und was kostet er? Das kannst du auch später auf der Artikelseite nachtragen.
                            </p>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-lieferant">Lieferant</Label>
                                    <div className="flex gap-1">
                                        <button
                                            id="artikel-lieferant"
                                            type="button"
                                            className="flex h-10 min-w-0 flex-1 items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                            onClick={() => setShowSupplierModal(true)}
                                        >
                                            <span className="truncate">{lieferant?.name || "Lieferant wählen"}</span>
                                            <Search className="w-4 h-4 shrink-0 text-slate-400" aria-hidden="true" />
                                        </button>
                                        {mitLieferant && (
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="sm"
                                                aria-label="Lieferant entfernen"
                                                title="Lieferant entfernen"
                                                className="h-10 w-10 shrink-0 px-0 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                                                onClick={() => { setLieferant(null); setPreis(""); setLieferantenNummer(""); }}
                                            >
                                                <X className="w-4 h-4" aria-hidden="true" />
                                            </Button>
                                        )}
                                    </div>
                                </div>
                                <div className="space-y-1.5" title={mitLieferant ? undefined : ERST_LIEFERANT_HINWEIS}>
                                    <Label htmlFor="artikel-lieferanten-nr">Artikelnummer beim Lieferanten</Label>
                                    <Input
                                        id="artikel-lieferanten-nr"
                                        value={lieferantenNummer}
                                        disabled={!mitLieferant}
                                        aria-describedby={mitLieferant ? undefined : "artikel-erst-lieferant"}
                                        onChange={e => setLieferantenNummer(e.target.value)}
                                        placeholder="z. B. 100234"
                                    />
                                </div>
                                <div className="space-y-1.5" title={mitLieferant ? undefined : ERST_LIEFERANT_HINWEIS}>
                                    <Label htmlFor="artikel-preis">Preis in Euro</Label>
                                    <DecimalInput
                                        id="artikel-preis"
                                        min={0}
                                        nullBehalten
                                        value={preis}
                                        disabled={!mitLieferant}
                                        aria-describedby={mitLieferant ? undefined : "artikel-erst-lieferant"}
                                        onChange={setPreis}
                                        placeholder="leer = kein Preis"
                                    />
                                </div>
                                {!mitLieferant && (
                                    <p id="artikel-erst-lieferant" className="md:col-span-3 -mt-2 text-xs text-slate-500">
                                        {ERST_LIEFERANT_HINWEIS}
                                    </p>
                                )}
                            </div>
                        </ReiterPanel>
                    )}

                    {reiter === "bilder" && (
                        <ReiterPanel idPrefix="artikel-anlegen" id="bilder" className="space-y-5">
                            <p className="text-sm text-slate-500">
                                Ein Foto zeigt, welches Teil gemeint ist. Datenblatt, Zulassung oder Zeichnung braucht der Monteur beim Einbau.
                                Die Dateien werden beim Anlegen mit hochgeladen.
                            </p>
                            <div className="grid grid-cols-1 md:grid-cols-[180px_1fr] gap-6">
                                {/* ---- Vorschaubild ---- */}
                                <div className="space-y-2">
                                    <p className="text-sm font-medium text-slate-800">Vorschaubild</p>
                                    {bild ? (
                                        <div className="relative">
                                            <BildVorschau datei={bild} label={`Gewähltes Bild: ${bild.name}`} />
                                            <Button type="button" variant="ghost" size="sm" onClick={() => setBild(null)}
                                                aria-label="Bild entfernen" title="Bild entfernen"
                                                className="absolute top-1.5 right-1.5 p-1.5 bg-white/90 text-slate-500 hover:text-rose-600 hover:bg-white rounded-full shadow-sm border border-slate-200">
                                                <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                                            </Button>
                                            <p className="mt-1 truncate text-xs text-slate-500" title={bild.name}>{bild.name}</p>
                                        </div>
                                    ) : (
                                        <button type="button" onClick={() => bildInputRef.current?.click()}
                                            className="flex flex-col items-center justify-center gap-2 w-full aspect-square rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 text-center px-3 text-slate-500 hover:border-rose-300 hover:bg-rose-50 hover:text-rose-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                            <ImagePlus className="w-8 h-8" aria-hidden="true" />
                                            <span className="text-sm font-medium">Bild auswählen</span>
                                        </button>
                                    )}
                                    <input ref={bildInputRef} type="file" className="hidden" aria-label="Bild auswählen"
                                        accept={BILD_TYPEN} onChange={bildWaehlen} />
                                </div>

                                {/* ---- Unterlagen ---- */}
                                <div className="space-y-3 min-w-0">
                                    <p className="text-sm font-medium text-slate-800">Unterlagen</p>
                                    {unterlagen.length === 0 ? (
                                        <div className="flex flex-col items-center justify-center gap-2 py-6 text-center border border-dashed border-slate-200 rounded-lg bg-slate-50">
                                            <Paperclip className="w-5 h-5 text-slate-300" aria-hidden="true" />
                                            <p className="text-sm text-slate-500">Noch keine Unterlagen gewählt.</p>
                                        </div>
                                    ) : (
                                        <ul className="divide-y divide-slate-100 border border-slate-200 rounded-lg" aria-label="Gewählte Unterlagen">
                                            {unterlagen.map((u) => (
                                                <li key={u.schluessel} className="flex items-center gap-3 px-3 py-2">
                                                    <FileText className="w-4 h-4 text-slate-400 shrink-0" aria-hidden="true" />
                                                    <span className="shrink-0 text-xs font-medium text-slate-500 bg-slate-100 rounded px-1.5 py-0.5">
                                                        {typLabel(u.typ)}
                                                    </span>
                                                    <span className="truncate text-sm text-slate-900 flex-1" title={u.datei.name}>{u.datei.name}</span>
                                                    <Button type="button" variant="ghost" size="sm"
                                                        onClick={() => setUnterlagen((alt) => alt.filter((x) => x.schluessel !== u.schluessel))}
                                                        aria-label={`${u.datei.name} entfernen`} title="Entfernen"
                                                        className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 shrink-0">
                                                        <X className="w-4 h-4" aria-hidden="true" />
                                                    </Button>
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                    <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-end">
                                        <div className="w-full sm:w-48">
                                            <Label htmlFor="artikel-unterlagen-art">Art der Unterlage</Label>
                                            <Select id="artikel-unterlagen-art" aria-label="Art der Unterlage" value={unterlagenArt}
                                                onChange={(v) => setUnterlagenArt(v as ArtikelDokumentTyp)} options={UNTERLAGEN_ARTEN} />
                                        </div>
                                        <Button type="button" variant="outline" size="sm"
                                            className="min-h-10 border-rose-300 text-rose-700 hover:bg-rose-50"
                                            onClick={() => unterlagenInputRef.current?.click()}>
                                            <Plus className="w-4 h-4" aria-hidden="true" />
                                            Datei hinzufügen
                                        </Button>
                                        <input ref={unterlagenInputRef} type="file" multiple className="hidden"
                                            aria-label="Unterlage auswählen" accept={UNTERLAGEN_TYPEN} onChange={unterlagenWaehlen} />
                                    </div>
                                </div>
                            </div>
                        </ReiterPanel>
                    )}

                    {reiter === "mehr" && (
                        <ReiterPanel idPrefix="artikel-anlegen" id="mehr" className="space-y-4">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-produktlinie">Produktlinie</Label>
                                    <Input id="artikel-produktlinie" value={produktlinie} maxLength={255}
                                        onChange={e => setProduktlinie(e.target.value)} placeholder="z. B. Stahlprofile" />
                                    <p className="text-xs text-slate-500">Hilft beim Filtern in der Artikelliste.</p>
                                </div>
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-produkttext">Beschreibung (intern)</Label>
                                    <Input id="artikel-produkttext" value={produkttext} maxLength={255}
                                        onChange={e => setProdukttext(e.target.value)} placeholder="Zusätzliche Infos …" />
                                    <p className="text-xs text-slate-500">Nur für dich. Den Text fürs Angebot pflegst du auf der Artikelseite.</p>
                                </div>
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-vpe">VPE (Menge)</Label>
                                    <DecimalInput id="artikel-vpe" value={verpackungseinheit} integer min={1}
                                        onChange={setVerpackungseinheit} />
                                    <p className="text-xs text-slate-500">Wie viel in einer Verpackung steckt, z. B. 100 Stück je Karton.</p>
                                </div>
                                <div className="space-y-1.5">
                                    <Label htmlFor="artikel-preiseinheit">Preiseinheit (Text)</Label>
                                    <Input id="artikel-preiseinheit" value={preiseinheit} maxLength={255}
                                        onChange={e => setPreiseinheit(e.target.value)} placeholder="z. B. 100 Stk" />
                                    <p className="text-xs text-slate-500">Nur ein Anzeigetext. Gerechnet wird mit der Einheit.</p>
                                </div>
                            </div>
                        </ReiterPanel>
                    )}
                </div>

                <DialogFooter className="gap-2 sm:space-x-0 border-t border-slate-100 pt-4">
                    <Button variant="ghost" onClick={onClose} disabled={loading}>Abbrechen</Button>
                    {!istLetzterReiter && (
                        <Button variant="outline" className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={() => setReiter(REIHENFOLGE[REIHENFOLGE.indexOf(reiter) + 1])}>
                            Weiter
                            <ArrowRight className="w-4 h-4" aria-hidden="true" />
                        </Button>
                    )}
                    <Button
                        onClick={handleSubmit}
                        disabled={loading}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {loading && <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />}
                        {loading ? "Wird angelegt …" : "Artikel anlegen"}
                    </Button>
                </DialogFooter>
            </DialogContent>

            {showCategoryModal && (
                <CategoryTreeModal
                    onSelect={(id, name) => {
                        setKategorie({ id, name });
                        setShowCategoryModal(false);
                    }}
                    onClose={() => setShowCategoryModal(false)}
                />
            )}

            {showSupplierModal && (
                <SupplierSelectModal
                    kategorieId={kategorie?.id}
                    kategorieName={kategorie?.name}
                    onSelect={(s) => {
                        setLieferant({ id: s.id, name: s.name });
                        setShowSupplierModal(false);
                    }}
                    onClose={() => setShowSupplierModal(false)}
                />
            )}
        </Dialog>
    );
}

/**
 * Vorschau des gewaehlten Bildes, gezeichnet auf eine Zeichenflaeche statt ueber eine
 * Bild-Adresse. So fliesst nichts aus der Dateiauswahl in ein src-Attribut
 * (CodeQL js/xss-through-dom), und es bleibt keine Objekt-Adresse zum Freigeben liegen.
 * Kann der Browser das Bild nicht lesen, steht dort ein ruhiger Platzhalter.
 */
function BildVorschau({ datei, label }: { datei: File; label: string }) {
    const flaecheRef = useRef<HTMLCanvasElement>(null);
    // Bei welcher Datei das Lesen gescheitert ist - so gilt ein Fehler nie fuer das naechste Bild.
    const [unlesbar, setUnlesbar] = useState<File | null>(null);
    const kannLesen = typeof createImageBitmap === "function";
    const lesbar = kannLesen && unlesbar !== datei;

    useEffect(() => {
        if (!kannLesen) return;
        let abgebrochen = false;
        createImageBitmap(datei)
            .then((bitmap) => {
                const flaeche = flaecheRef.current;
                if (abgebrochen || !flaeche) {
                    bitmap.close();
                    return;
                }
                // Fuer die kleine Kachel reicht eine Seitenlaenge von hoechstens 640 Pixeln.
                const massstab = Math.min(1, 640 / Math.max(bitmap.width, bitmap.height));
                flaeche.width = Math.max(1, Math.round(bitmap.width * massstab));
                flaeche.height = Math.max(1, Math.round(bitmap.height * massstab));
                flaeche.getContext("2d")?.drawImage(bitmap, 0, 0, flaeche.width, flaeche.height);
                bitmap.close();
            })
            .catch(() => { if (!abgebrochen) setUnlesbar(datei); });
        return () => { abgebrochen = true; };
    }, [datei, kannLesen]);

    if (!lesbar) {
        return (
            <div role="img" aria-label={label}
                className="flex flex-col items-center justify-center gap-2 w-full aspect-square rounded-lg border border-slate-200 bg-slate-50 text-slate-400">
                <ImageOff className="w-8 h-8" aria-hidden="true" />
                <span className="text-xs">Keine Vorschau</span>
            </div>
        );
    }
    return (
        <canvas ref={flaecheRef} role="img" aria-label={label}
            className="w-full aspect-square rounded-lg border border-slate-200 bg-white object-contain" />
    );
}
