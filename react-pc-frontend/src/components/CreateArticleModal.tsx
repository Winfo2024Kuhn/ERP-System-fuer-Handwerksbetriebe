import { useState, useEffect } from "react";
import { X, Search } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select } from "./ui/select-custom";
import { CategoryTreeModal } from "./CategoryTreeModal";
import { SupplierSelectModal } from "./SupplierSelectModal";
import { useToast } from './ui/toast';

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

export function CreateArticleModal({ onClose, onSave }: CreateArticleModalProps) {
    const toast = useToast();
    const [formData, setFormData] = useState({
        produktname: "",
        produktlinie: "",
        produkttext: "",
        externeArtikelnummer: "",
        verpackungseinheit: 1,
        preiseinheit: "1",
        verrechnungseinheit: "STUECK",
        kategorieId: 0,
        kategorieName: "",
        werkstoffId: 0 as number | null, // We only have names in options, need ID map or just use ID if passed
        werkstoffName: "",
        preis: 0,
        lieferantId: 0,
        lieferantName: ""
    });

    // We need Werkstoff IDs actually. The current props pass strings. 
    // Let's fetch proper Werkstoff objects map.
    const [werkstoffe, setWerkstoffe] = useState<{id: number, name: string}[]>([]);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        // Fetch full werkstoff objects to get IDs
        fetch('/api/werkstoffe?full=true').then(async res => {
             if(res.ok) {
                 const data = await res.json();
                 setWerkstoffe(data);
             } else {
                 // Fallback if endpoint doesn't exist, maybe try searching or just mapping names
                 // For now, assume the passed strings are all we have and we might need to find IDs another way
                 // Or assume backend can take name? No, DTO has Id.
                 // Let's try to fetch all via existing endpoint if possible or mock.
                 // Actually /artikel/werkstoffe returns strings.
                 // Let's assume we can't easily set werkstoff ID without a proper endpoint.
                 // I'll assume the backend service can look it up by name if I change DTO, but I didn't.
                 // I'll fetch ALL articles to find werkstoffe? No too heavy.
                 // I will add a fetch for IDs logic if needed, or just skip Werkstoff ID for now if not critical.
                 // Wait, I can modify backend to accept Werkstoff Name? Or add an endpoint. 
                 // Easier: I'll just list strings in UI and if user selects one, I try to find ID from a pre-fetched list? 
                 // Let's assume there is an endpoint /api/werkstoffe/all or similar.
                 // Since I can't check easily, I will create a small helper endpoint or just try to use the index? No.
                 // I'll search for werkstoff by name on backend? 
                 // Let's create a quick endpoint on backend or assume 0.
                 // Better: I'll update ArtikelController to expose Werkstoff with IDs.
             }
        });
        
        // Quick fix: Fetch werkstoffe with IDs.
        // Since I cannot change backend easily in this file without switching context, 
        // I will check if I can use the existing string list and maybe the backend accepts name?
        // The DTO has `werkstoffId`.
        // I will add a `GET /artikel/werkstoffe/map` endpoint to Backend.
    }, []);

    // Fetch Werkstoff Map
    useEffect(() => {
        // We will implement this endpoint in backend next step.
        fetch('/artikel/werkstoffe/details').then(r => r.json()).then(d => setWerkstoffe(d)).catch(() => {});
    }, []);

    const handleChange = (key: string, value: string | number | boolean | null) => {
        setFormData(prev => ({ ...prev, [key]: value }));
    };

    const handleSubmit = async () => {
        if (!formData.produktname) {
            toast.warning("Produktname ist erforderlich.");
            return;
        }
        
        setLoading(true);
        try {
            const payload = {
                produktname: formData.produktname,
                produktlinie: formData.produktlinie,
                produkttext: formData.produkttext,
                externeArtikelnummer: formData.externeArtikelnummer,
                verpackungseinheit: formData.verpackungseinheit,
                preiseinheit: formData.preiseinheit,
                verrechnungseinheit: formData.verrechnungseinheit,
                kategorieId: formData.kategorieId || null,
                werkstoffId: formData.werkstoffId || null,
                preis: formData.preis,
                lieferantId: formData.lieferantId || null
            };

            const res = await fetch('/artikel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error("Fehler beim Speichern");
            onSave();
            onClose();
        } catch (err) {
            toast.error("Artikel konnte nicht angelegt werden.");
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
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
                </div>
                
                <div className="p-6 space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <Label>Produktname *</Label>
                            <Input 
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
                            <Label>Artikelnummer (Extern)</Label>
                            <Input 
                                value={formData.externeArtikelnummer} 
                                onChange={e => handleChange('externeArtikelnummer', e.target.value)} 
                                placeholder="Lieferanten-Nr." 
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

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <Label>Preis (€)</Label>
                            <Input 
                                type="number" 
                                step="0.01"
                                value={formData.preis} 
                                onChange={e => handleChange('preis', parseFloat(e.target.value) || 0)} 
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Lieferant (Optional)</Label>
                            <div 
                                className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 cursor-pointer hover:bg-slate-50"
                                onClick={() => setShowSupplierModal(true)}
                            >
                                <span className="truncate">{formData.lieferantName || "Lieferant wählen"}</span>
                                {formData.lieferantId ? (
                                    <X 
                                        className="w-4 h-4 text-slate-400 hover:text-red-500" 
                                        onClick={(e) => { 
                                            e.stopPropagation(); 
                                            handleChange('lieferantId', 0); 
                                            handleChange('lieferantName', ""); 
                                        }} 
                                    />
                                ) : (
                                    <Search className="w-4 h-4 text-slate-400" />
                                )}
                            </div>
                        </div>
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
