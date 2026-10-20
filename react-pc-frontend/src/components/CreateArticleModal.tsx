import { useState, useEffect } from "react";
import { X, Search } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select } from "./ui/select-custom";
import { CategoryTreeModal } from "./CategoryTreeModal";
import { SupplierSelectModal } from "./SupplierSelectModal";
import { useToast } from './ui/toast';
import { InfoButton } from './ui/info-button';
import { parseDecimal } from './verrechnungslohnFormat';
import { leseFehlermeldung } from '../lib/apiFehler';

interface CreateArticleModalProps {
    onClose: () => void;
    onSave: () => void;
}

const VERRECHNUNGSEINHEITEN = [
    { value: "LAUFENDE_METER", label: "Laufende Meter" },
    { value: "QUADRATMETER", label: "Quadratmeter" },
    { value: "KILOGRAMM", label: "Kilogramm" },
    { value: "STUECK", label: "Stück" }
];

/** Warum Preis und Lieferanten-Nummer gesperrt sind, solange kein Lieferant gewählt ist. */
const ERST_LIEFERANT_HINWEIS =
    "Erst den Lieferanten wählen – Preis und Lieferanten-Nummer gehören immer zu einem Lieferanten.";

export function CreateArticleModal({ onClose, onSave }: CreateArticleModalProps) {
    const toast = useToast();
    const [formData, setFormData] = useState({
        produktname: "",
        produktlinie: "",
        produkttext: "",
        externeArtikelnummer: "",
        artikelnummer: "",
        verpackungseinheit: 1,
        preiseinheit: "1",
        verrechnungseinheit: "STUECK",
        kategorieId: 0,
        kategorieName: "",
        werkstoffId: 0 as number | null,
        werkstoffName: "",
        // Als Text: leer heisst "kein Preis", nicht 0 Euro.
        preis: "",
        lieferantId: 0,
        lieferantName: ""
    });

    // Werkstoffe mit ID, weil der Artikel die ID braucht - nicht nur den Namen.
    const [werkstoffe, setWerkstoffe] = useState<{ id: number, name: string }[]>([]);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [loading, setLoading] = useState(false);
    const mitLieferant = formData.lieferantId > 0;

    useEffect(() => {
        fetch('/api/artikel/werkstoffe/details')
            .then(res => {
                if (!res.ok) throw new Error("Werkstoffe konnten nicht geladen werden");
                return res.json();
            })
            // Bei einem Fehler antwortet der Server mit einem Objekt statt einer
            // Liste - ohne diese Pruefung wuerde die Auswahlliste die Seite abschiessen.
            .then(data => setWerkstoffe(Array.isArray(data) ? data : []))
            .catch(err => console.error("Fehler beim Laden der Werkstoffe", err));
    }, []);

    const handleChange = (key: string, value: string | number | boolean | null) => {
        setFormData(prev => ({ ...prev, [key]: value }));
    };

    const handleSubmit = async () => {
        if (!formData.produktname) {
            toast.warning("Produktname ist erforderlich.");
            return;
        }
        const preisText = mitLieferant ? formData.preis.trim() : "";
        const preis = preisText === "" ? null : parseDecimal(preisText);
        if (preisText !== "" && (preis === null || preis < 0)) {
            toast.error("Bitte einen gültigen Preis eingeben, z. B. 12,50.");
            return;
        }

        setLoading(true);
        try {
            const payload = {
                produktname: formData.produktname,
                produktlinie: formData.produktlinie,
                produkttext: formData.produkttext,
                // Ohne Lieferant gibt es weder Preis noch Lieferanten-Nummer.
                externeArtikelnummer: mitLieferant ? formData.externeArtikelnummer.trim() || null : null,
                artikelnummer: formData.artikelnummer.trim() || null,
                verpackungseinheit: formData.verpackungseinheit,
                preiseinheit: formData.preiseinheit,
                verrechnungseinheit: formData.verrechnungseinheit,
                kategorieId: formData.kategorieId || null,
                werkstoffId: formData.werkstoffId || null,
                preis,
                lieferantId: mitLieferant ? formData.lieferantId : null
            };

            const res = await fetch('/api/artikel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error(await leseFehlermeldung(res, "Artikel konnte nicht angelegt werden."));
            onSave();
            onClose();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Artikel konnte nicht angelegt werden.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const werkstoffSelectOptions = werkstoffe.map(w => ({ value: String(w.id), label: w.name }));

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto flex flex-col" onClick={e => e.stopPropagation()}>
                <div className="p-6 border-b border-slate-100 flex justify-between items-center">
                    <h3 className="text-xl font-semibold text-slate-900">Neuen Artikel anlegen</h3>
                    <button type="button" onClick={onClose} aria-label="Schließen" className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" aria-hidden="true" /></button>
                </div>
                
                <div className="p-6 space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <Label htmlFor="artikel-produktname">Produktname *</Label>
                            <Input
                                id="artikel-produktname"
                                value={formData.produktname} 
                                onChange={e => handleChange('produktname', e.target.value)} 
                                placeholder="z.B. Quadratrohr 40x40x2" 
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Produktlinie</Label>
                            <Input 
                                value={formData.produktlinie} 
                                onChange={e => handleChange('produktlinie', e.target.value)} 
                                placeholder="z.B. Stahlprofile" 
                            />
                        </div>
                    </div>

                    <div className="space-y-1.5">
                        <Label>Beschreibung / Text</Label>
                        <Input 
                            value={formData.produkttext} 
                            onChange={e => handleChange('produkttext', e.target.value)} 
                            placeholder="Zusätzliche Infos..." 
                        />
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="space-y-1.5">
                            <div className="flex items-center gap-1">
                                <Label htmlFor="artikel-eigene-nr">Eigene Artikel-Nr.</Label>
                                <InfoButton titel="Eigene Artikel-Nr.">
                                    <p className="font-semibold text-slate-900">Was passiert im Hintergrund?</p>
                                    <p>Das ist eure eigene Nummer für den Artikel, unabhängig vom Lieferanten.</p>
                                    <p className="mt-2 font-semibold text-slate-900">Was bewirkt das?</p>
                                    <p>
                                        Sie steht in der Artikelliste in der Spalte „Artikel-Nr.“ und muss eindeutig sein.
                                        Die Nummern der Lieferanten bleiben davon unberührt.
                                    </p>
                                </InfoButton>
                            </div>
                            <Input
                                id="artikel-eigene-nr"
                                value={formData.artikelnummer}
                                maxLength={64}
                                onChange={e => handleChange('artikelnummer', e.target.value)}
                                placeholder="optional, z. B. QR-40-2"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Kategorie</Label>
                            <div 
                                className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 cursor-pointer hover:bg-slate-50"
                                onClick={() => setShowCategoryModal(true)}
                            >
                                <span className="truncate">{formData.kategorieName || "Kategorie wählen"}</span>
                                <Search className="w-4 h-4 text-slate-400" />
                            </div>
                        </div>
                        <div className="space-y-1.5">
                            <Label>Werkstoff</Label>
                            <Select 
                                options={werkstoffSelectOptions} 
                                value={String(formData.werkstoffId || "")} 
                                onChange={v => {
                                    const w = werkstoffe.find(x => String(x.id) === v);
                                    handleChange('werkstoffId', Number(v));
                                    handleChange('werkstoffName', w?.name || "");
                                }} 
                                placeholder="Werkstoff wählen" 
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-slate-50 p-4 rounded-lg border border-slate-100">
                        <div className="space-y-1.5">
                            <Label>Verrechnungseinheit</Label>
                            <Select 
                                options={VERRECHNUNGSEINHEITEN} 
                                value={formData.verrechnungseinheit} 
                                onChange={v => handleChange('verrechnungseinheit', v)} 
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>VPE (Menge)</Label>
                            <Input 
                                type="number" 
                                min="1"
                                value={formData.verpackungseinheit} 
                                onChange={e => handleChange('verpackungseinheit', parseInt(e.target.value) || 1)} 
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Preiseinheit (Text)</Label>
                            <Input 
                                value={formData.preiseinheit} 
                                onChange={e => handleChange('preiseinheit', e.target.value)} 
                                placeholder="z.B. 100 Stk" 
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="space-y-1.5">
                            <Label htmlFor="artikel-lieferant">Lieferant (optional)</Label>
                            <div className="flex gap-1">
                                <button
                                    id="artikel-lieferant"
                                    type="button"
                                    className="flex h-10 min-w-0 flex-1 items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                    onClick={() => setShowSupplierModal(true)}
                                >
                                    <span className="truncate">{formData.lieferantName || "Lieferant wählen"}</span>
                                    <Search className="w-4 h-4 shrink-0 text-slate-400" aria-hidden="true" />
                                </button>
                                {mitLieferant && (
                                    <button
                                        type="button"
                                        aria-label="Lieferant entfernen"
                                        title="Lieferant entfernen"
                                        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                        onClick={() => setFormData(prev => ({
                                            ...prev, lieferantId: 0, lieferantName: "", preis: "", externeArtikelnummer: "",
                                        }))}
                                    >
                                        <X className="w-4 h-4" aria-hidden="true" />
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="space-y-1.5" title={mitLieferant ? undefined : ERST_LIEFERANT_HINWEIS}>
                            <Label htmlFor="artikel-lieferanten-nr">Artikelnummer beim Lieferanten</Label>
                            <Input
                                id="artikel-lieferanten-nr"
                                value={formData.externeArtikelnummer}
                                disabled={!mitLieferant}
                                aria-describedby={mitLieferant ? undefined : "artikel-erst-lieferant"}
                                onChange={e => handleChange('externeArtikelnummer', e.target.value)}
                                placeholder="z. B. 100234"
                            />
                        </div>
                        <div className="space-y-1.5" title={mitLieferant ? undefined : ERST_LIEFERANT_HINWEIS}>
                            <Label htmlFor="artikel-preis">Preis in Euro</Label>
                            <Input
                                id="artikel-preis"
                                inputMode="decimal"
                                value={formData.preis}
                                disabled={!mitLieferant}
                                aria-describedby={mitLieferant ? undefined : "artikel-erst-lieferant"}
                                onChange={e => handleChange('preis', e.target.value)}
                                placeholder="leer = kein Preis"
                            />
                        </div>
                        {!mitLieferant && (
                            <p id="artikel-erst-lieferant" className="md:col-span-3 -mt-2 text-xs text-slate-500">
                                {ERST_LIEFERANT_HINWEIS}
                            </p>
                        )}
                    </div>
                </div>

                <div className="p-6 border-t border-slate-100 bg-slate-50 flex justify-end gap-3 rounded-b-xl">
                    <Button variant="ghost" onClick={onClose}>Abbrechen</Button>
                    <Button 
                        onClick={handleSubmit} 
                        disabled={loading || !formData.produktname}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {loading ? "Speichert..." : "Artikel anlegen"}
                    </Button>
                </div>
            </div>

            {showCategoryModal && (
                <CategoryTreeModal 
                    onSelect={(id, name) => { 
                        handleChange('kategorieId', id); 
                        handleChange('kategorieName', name); 
                        setShowCategoryModal(false); 
                    }} 
                    onClose={() => setShowCategoryModal(false)} 
                />
            )}

            {showSupplierModal && (
                <SupplierSelectModal
                    kategorieId={formData.kategorieId || undefined}
                    kategorieName={formData.kategorieName || undefined}
                    onSelect={(s) => { 
                        handleChange('lieferantId', s.id); 
                        handleChange('lieferantName', s.name); 
                        setShowSupplierModal(false); 
                    }} 
                    onClose={() => setShowSupplierModal(false)} 
                />
            )}
        </div>
    );
}
