import { useEffect, useMemo, useState } from "react";
import { Loader2, Save, X } from "lucide-react";
import { Button } from "../ui/button";
import { Input } from "../ui/input";
import { InfoButton } from "../ui/info-button";
import { Label } from "../ui/label";
import { Select } from "../ui/select-custom";
import { useToast } from "../ui/toast";
import { CategoryTreeModal } from "../CategoryTreeModal";
import { KategorieAuswahlFeld } from "./KategorieAuswahlFeld";
import { formatDecimalInput } from "../../lib/numberInput";
import { validateNumberDrafts } from "../../lib/numberDrafts";
import type { Artikel } from "../../types";

interface Option { wert: string; name: string; erklaerung?: string }
interface Filteroptionen { herstellverfahren: Option[]; fertigungszustand: Option[]; profilform: Option[] }

const VERRECHNUNGSEINHEITEN = [
    { value: "LAUFENDE_METER", label: "Laufende Meter" },
    { value: "QUADRATMETER", label: "Quadratmeter" },
    { value: "KILOGRAMM", label: "Kilogramm" },
    { value: "STUECK", label: "Stück" },
    { value: "LITER", label: "Liter" },
];

const KEINE = "";

/** Eignung für ein Verfahren: vom Werkstoff übernehmen (Standard) oder für diesen Artikel festlegen. */
type Eignungswahl = "UEBERNEHMEN" | "JA" | "NEIN";
const wahlAus = (eigen?: boolean | null): Eignungswahl => (eigen == null ? "UEBERNEHMEN" : eigen ? "JA" : "NEIN");
const wertAus = (wahl: Eignungswahl): boolean | null => (wahl === "UEBERNEHMEN" ? null : wahl === "JA");
const eignungsOptionen = (werkstoffWert?: boolean) => [
    { value: "UEBERNEHMEN", label: werkstoffWert == null ? "Vom Werkstoff übernehmen" : `Vom Werkstoff übernehmen (${werkstoffWert ? "geeignet" : "nicht geeignet"})` },
    { value: "JA", label: "Geeignet" },
    { value: "NEIN", label: "Nicht geeignet" },
];

const zahlText = (w?: number | null) => (w == null ? "" : formatDecimalInput(w));
const einheitName = (e: Artikel["verrechnungseinheit"]) => (typeof e === "string" ? e : e?.name ?? "");

/** Die Maße, die nur Halbzeug (Rohre, Profile, Bleche) hat. */
const MASSE = [
    { schluessel: "durchmesser", label: "Durchmesser (mm)" },
    { schluessel: "hoehe", label: "Höhe (mm)" },
    { schluessel: "breite", label: "Breite (mm)" },
    { schluessel: "wandstaerke", label: "Wandstärke (mm)" },
    { schluessel: "stegdicke", label: "Stegdicke (mm)" },
    { schluessel: "flanschdicke", label: "Flanschdicke (mm)" },
] as const;
type MassSchluessel = typeof MASSE[number]["schluessel"];

interface Props {
    artikel: Artikel;
    onGespeichert: () => void;
    onAbbrechen: () => void;
}

/**
 * Alle Stammdaten eines Artikels ändern: Name, Nummern, Einheit, Kategorie, Werkstoff, technische Merkmale und Maße.
 * Texte für Angebote, Lieferanten und Preise haben weiter ihre eigenen Bereiche. Speichern ersetzt die Stammdaten vollständig,
 * deshalb sind hier alle Felder vorbelegt, auch die, die man nicht ändert.
 */
export function ArtikelStammdatenForm({ artikel, onGespeichert, onAbbrechen }: Props) {
    const toast = useToast();
    const hatMasse = !!artikel.profilform || MASSE.some(m => artikel[m.schluessel] != null) || artikel.standardlaenge != null;

    const [produktname, setProduktname] = useState(artikel.produktname ?? "");
    const [produktlinie, setProduktlinie] = useState(artikel.produktlinie ?? "");
    const [produkttext, setProdukttext] = useState(artikel.produkttext ?? "");
    const [artikelnummer, setArtikelnummer] = useState(artikel.artikelnummer ?? "");
    const [ean, setEan] = useState(artikel.ean ?? "");
    const [einheit, setEinheit] = useState(einheitName(artikel.verrechnungseinheit));
    const [verpackung, setVerpackung] = useState(zahlText(artikel.verpackungseinheit));
    const [preiseinheit, setPreiseinheit] = useState(artikel.preiseinheit ?? "");
    const [kategorieId, setKategorieId] = useState<number | null>(artikel.kategorieId ?? null);
    const [kategorieName, setKategorieName] = useState(artikel.kategoriePfad ?? "");
    const [werkstoffId, setWerkstoffId] = useState(artikel.werkstoffId != null ? String(artikel.werkstoffId) : KEINE);
    // Eignung für die Oberflächenbehandlung: vom Werkstoff übernehmen (Standard) oder für diesen Artikel festlegen
    const [verzinken, setVerzinken] = useState<Eignungswahl>(wahlAus(artikel.verzinkungsgeeignetEigen));
    const [pulvern, setPulvern] = useState<Eignungswahl>(wahlAus(artikel.pulverbeschichtungsgeeignetEigen));
    const [profilform, setProfilform] = useState(artikel.profilform?.name ?? KEINE);
    const [herstellverfahren, setHerstellverfahren] = useState(artikel.herstellverfahren?.name ?? KEINE);
    const [fertigungszustand, setFertigungszustand] = useState(artikel.fertigungszustand?.name ?? KEINE);
    const [massnorm, setMassnorm] = useState(artikel.massnorm ?? "");
    const [werkstoffnorm, setWerkstoffnorm] = useState(artikel.werkstoffnorm ?? "");
    const [masse, setMasse] = useState<Record<MassSchluessel, string>>(() => ({
        durchmesser: zahlText(artikel.durchmesser), hoehe: zahlText(artikel.hoehe), breite: zahlText(artikel.breite),
        wandstaerke: zahlText(artikel.wandstaerke), stegdicke: zahlText(artikel.stegdicke), flanschdicke: zahlText(artikel.flanschdicke),
    }));
    const [laenge, setLaenge] = useState(zahlText(artikel.standardlaenge));

    const [optionen, setOptionen] = useState<Filteroptionen | null>(null);
    const [werkstoffe, setWerkstoffe] = useState<{ id: number; name: string; verzinkungsgeeignet?: boolean; pulverbeschichtungsgeeignet?: boolean }[]>([]);
    const [zeigeKategorien, setZeigeKategorien] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState("");

    useEffect(() => {
        let abgebrochen = false;
        fetch("/api/artikel/filteroptionen")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Auswahllisten konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setOptionen(d as Filteroptionen); })
            .catch(() => { if (!abgebrochen) toast.error("Die Auswahllisten für Form, Verfahren und Zustand konnten nicht geladen werden."); });
        fetch("/api/artikel/werkstoffe/details")
            .then(r => (r.ok ? r.json() : Promise.reject(new Error("Werkstoffe konnten nicht geladen werden."))))
            .then(d => { if (!abgebrochen) setWerkstoffe(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) toast.error("Die Liste der Werkstoffe konnte nicht geladen werden. Der bisherige Werkstoff bleibt gewählt."); });
        return () => { abgebrochen = true; };
    }, [toast]);

    const alsOptionen = (liste?: Option[]) => [{ value: KEINE, label: "– keine Angabe –" }, ...(liste ?? []).map(o => ({ value: o.wert, label: o.name }))];
    const werkstoffOptionen = useMemo(() => {
        const liste = werkstoffe.map(w => ({ value: String(w.id), label: w.name }));
        // Auch ohne geladene Liste soll der bisherige Werkstoff sichtbar bleiben
        if (werkstoffId && !liste.some(w => w.value === werkstoffId)) liste.push({ value: werkstoffId, label: artikel.werkstoffName ?? `Werkstoff ${werkstoffId}` });
        return [{ value: KEINE, label: "– kein Werkstoff –" }, ...liste];
    }, [werkstoffe, werkstoffId, artikel.werkstoffName]);

    const werkstoffEignung = useMemo(() => werkstoffe.find(w => String(w.id) === werkstoffId), [werkstoffe, werkstoffId]);

    const speichern = async () => {
        setFehler("");
        if (!produktname.trim()) { setFehler("Bitte einen Produktnamen eingeben."); return; }
        const entwuerfe: Record<string, string> = { verpackung, laenge, ...masse };
        const regeln: Record<string, { label: string; min?: number; max?: number; integer?: boolean; maxDecimalPlaces?: number }> = {
            verpackung: { label: "die Verpackungseinheit", min: 0, integer: true },
            laenge: { label: "die übliche Länge", min: 1, max: 30000, integer: true },
        };
        for (const m of MASSE) regeln[m.schluessel] = { label: m.label.replace(" (mm)", ""), min: 0, max: 100000, maxDecimalPlaces: 2 };
        const geprueft = validateNumberDrafts(entwuerfe, regeln);
        if (!geprueft.valid) { setFehler(geprueft.message); return; }
        const v = geprueft.values;

        setSpeichert(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(String(artikel.id))}/stammdaten`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    version: artikel.version ?? null,
                    produktname: produktname.trim(),
                    produktlinie: produktlinie.trim() || null,
                    produkttext: produkttext.trim() || null,
                    artikelnummer: artikelnummer.trim() || null,
                    ean: ean.replace(/\s+/g, "") || null,
                    verrechnungseinheit: einheit || null,
                    verpackungseinheit: v.verpackung,
                    preiseinheit: preiseinheit.trim() || null,
                    kategorieId,
                    werkstoffId: werkstoffId ? Number(werkstoffId) : null,
                    profilform: profilform || null,
                    herstellverfahren: herstellverfahren || null,
                    fertigungszustand: fertigungszustand || null,
                    massnorm: massnorm.trim() || null,
                    werkstoffnorm: werkstoffnorm.trim() || null,
                    verzinkungsgeeignet: wertAus(verzinken),
                    pulverbeschichtungsgeeignet: wertAus(pulvern),
                    durchmesser: v.durchmesser, hoehe: v.hoehe, breite: v.breite,
                    wandstaerke: v.wandstaerke, stegdicke: v.stegdicke, flanschdicke: v.flanschdicke,
                    standardlaenge: v.laenge,
                }),
            });
            if (!res.ok) {
                let text = "Die Stammdaten konnten nicht gespeichert werden.";
                try {
                    const body = await res.json() as { message?: string };
                    if (body?.message) text = body.message;
                } catch { /* kein JSON: Standardtext */ }
                throw new Error(text);
            }
            toast.success("Die Stammdaten sind gespeichert.");
            onGespeichert();
        } catch (e) {
            const text = e instanceof Error ? e.message : "Die Stammdaten konnten nicht gespeichert werden.";
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <section className="bg-white border border-rose-200 rounded-lg p-5" aria-label="Stammdaten bearbeiten" data-testid="artikel-stammdaten-form">
            <div className="flex items-start justify-between gap-3 mb-4">
                <div>
                    <h2 className="text-lg font-semibold text-slate-900">Stammdaten bearbeiten</h2>
                    <p className="text-sm text-slate-500">Bestehende Angebote und Rechnungen ändern sich dadurch nicht, sie haben Text und Preis kopiert.</p>
                </div>
                <Button variant="ghost" size="sm" onClick={onAbbrechen} aria-label="Bearbeiten abbrechen" className="text-slate-500 hover:bg-slate-100">
                    <X className="w-4 h-4" aria-hidden="true" />
                </Button>
            </div>

            <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-name">Produktname *</Label>
                        <Input id="sd-name" value={produktname} maxLength={255} onChange={e => setProduktname(e.target.value)} placeholder="z. B. Quadratrohr 40x40x2" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-linie">Produktlinie</Label>
                        <Input id="sd-linie" value={produktlinie} maxLength={255} onChange={e => setProduktlinie(e.target.value)} placeholder="z. B. Stahlprofile" />
                    </div>
                </div>

                <div className="space-y-1.5">
                    <Label htmlFor="sd-text">Beschreibung (intern)</Label>
                    <Input id="sd-text" value={produkttext} maxLength={255} onChange={e => setProdukttext(e.target.value)} placeholder="Zusätzliche Infos …" />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-nummer">Artikelnummer</Label>
                        <Input id="sd-nummer" value={artikelnummer} maxLength={64} onChange={e => setArtikelnummer(e.target.value)} placeholder="eigene Nummer" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-ean">EAN / Strichcode</Label>
                        <Input id="sd-ean" value={ean} inputMode="numeric" maxLength={20} onChange={e => setEan(e.target.value)} placeholder="nur wenn vorhanden" className="font-mono" />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Kategorie</Label>
                        <KategorieAuswahlFeld name={kategorieName} gewaehlt={kategorieId != null} onOeffnen={() => setZeigeKategorien(true)}
                            onEntfernen={() => { setKategorieId(null); setKategorieName(""); }} />
                        <p className="text-xs text-slate-500">Im Fenster lassen sich neue Kategorien anlegen („Kategorie hinzufügen“).</p>
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 bg-slate-50 p-4 rounded-lg border border-slate-100">
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1">
                            <Label>Verrechnungseinheit</Label>
                            <InfoButton titel="Verrechnungseinheit">
                                <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                <p>Mit dieser Einheit wird der Artikel eingekauft, verkauft und gerechnet. Das Programm rechnet bereits eingetragene Mengen <strong>nicht</strong> um.</p>
                                <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                <p>Neue Angebote und Rechnungen nutzen die neue Einheit. Bestehende Dokumente bleiben, wie sie sind.</p>
                            </InfoButton>
                        </div>
                        <Select options={VERRECHNUNGSEINHEITEN} value={einheit} onChange={setEinheit} />
                        <p className="text-xs text-slate-500">Die Einheit, in der eingekauft, verkauft und gerechnet wird.</p>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-vpe">Verpackungseinheit (Menge)</Label>
                        <Input id="sd-vpe" value={verpackung} inputMode="numeric" onChange={e => setVerpackung(e.target.value)} />
                        <p className="text-xs text-slate-500">Wie viel in einer Verpackung steckt, z. B. 100 Stück je Karton.</p>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-pe">Preiseinheit (Text)</Label>
                        <Input id="sd-pe" value={preiseinheit} maxLength={255} onChange={e => setPreiseinheit(e.target.value)} placeholder="z. B. 100 Stk" />
                        <p className="text-xs text-slate-500">Nur ein Anzeigetext. Gerechnet wird mit der Verrechnungseinheit.</p>
                    </div>
                    <div className="space-y-1.5">
                        {/* Gleicher Aufbau wie bei der Einheit (Flex-Zeile), damit beide Auswahlfelder auf einer Höhe mit den Eingabefeldern stehen */}
                        <div className="flex items-center gap-1"><Label>Werkstoff</Label></div>
                        <Select options={werkstoffOptionen} value={werkstoffId} onChange={setWerkstoffId} />
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 rounded-lg border border-slate-100 bg-slate-50 p-4">
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1">
                            <Label>Feuerverzinken</Label>
                            <InfoButton titel="Eignung zur Oberflächenbehandlung">
                                <p className="font-medium text-slate-800">Was passiert im Hintergrund?</p>
                                <p>Standardmäßig gilt, was beim Werkstoff steht. Hier lässt sich das für <strong>diesen einen Artikel</strong> überstimmen.</p>
                                <p className="mt-2 font-medium text-slate-800">Was bewirkt das?</p>
                                <p>Es legt nur fest, ob das Teil verzinkt oder pulverbeschichtet werden <strong>kann</strong>. Die Eignung erscheint auf der Artikelseite und in der Artikelsuche als Filter. Preise ändern sich dadurch nicht.</p>
                            </InfoButton>
                        </div>
                        <Select aria-label="Feuerverzinken" options={eignungsOptionen(werkstoffEignung?.verzinkungsgeeignet)} value={verzinken} onChange={v => setVerzinken(v as Eignungswahl)} />
                    </div>
                    <div className="space-y-1.5">
                        <div className="flex items-center gap-1"><Label>Pulverbeschichten</Label></div>
                        <Select aria-label="Pulverbeschichten" options={eignungsOptionen(werkstoffEignung?.pulverbeschichtungsgeeignet)} value={pulvern} onChange={v => setPulvern(v as Eignungswahl)} />
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="space-y-1.5">
                        <Label>Form</Label>
                        <Select options={alsOptionen(optionen?.profilform)} value={profilform} onChange={setProfilform} />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Herstellverfahren</Label>
                        <Select options={alsOptionen(optionen?.herstellverfahren)} value={herstellverfahren} onChange={setHerstellverfahren} />
                    </div>
                    <div className="space-y-1.5">
                        <Label>Fertigungszustand</Label>
                        <Select options={alsOptionen(optionen?.fertigungszustand)} value={fertigungszustand} onChange={setFertigungszustand} />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-massnorm">Maßnorm</Label>
                        <Input id="sd-massnorm" value={massnorm} maxLength={64} onChange={e => setMassnorm(e.target.value)} placeholder="z. B. EN 10219-2" />
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="sd-wnorm">Werkstoffnorm</Label>
                        <Input id="sd-wnorm" value={werkstoffnorm} maxLength={64} onChange={e => setWerkstoffnorm(e.target.value)} />
                    </div>
                </div>

                {hatMasse && (
                    <div>
                        <h3 className="text-sm font-semibold text-slate-800 mb-2">Maße</h3>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            {MASSE.map(m => (
                                <div key={m.schluessel} className="space-y-1.5">
                                    <Label htmlFor={`sd-${m.schluessel}`}>{m.label}</Label>
                                    <Input id={`sd-${m.schluessel}`} value={masse[m.schluessel]} inputMode="decimal"
                                        onChange={e => setMasse(alt => ({ ...alt, [m.schluessel]: e.target.value }))} />
                                </div>
                            ))}
                            <div className="space-y-1.5">
                                <Label htmlFor="sd-laenge">Übliche Länge (mm)</Label>
                                <Input id="sd-laenge" value={laenge} inputMode="numeric" onChange={e => setLaenge(e.target.value)} placeholder="z. B. 6000" />
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {fehler && <div role="alert" className="mt-4 p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

            <div className="mt-5 flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={onAbbrechen} disabled={speichert}>Abbrechen</Button>
                <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={speichert}>
                    {speichert ? <Loader2 className="w-4 h-4 mr-2 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Save className="w-4 h-4 mr-2" aria-hidden="true" />}
                    Speichern
                </Button>
            </div>

            {zeigeKategorien && (
                <CategoryTreeModal
                    onSelect={(id, name) => { setKategorieId(Number(id)); setKategorieName(name); setZeigeKategorien(false); }}
                    onClose={() => setZeigeKategorien(false)}
                />
            )}
        </section>
    );
}
