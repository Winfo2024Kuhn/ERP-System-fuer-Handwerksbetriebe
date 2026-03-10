import { useState, useEffect } from "react";
import { Button } from "./ui/button";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from "./ui/dialog";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Search, FileText, Upload, X, Loader2 } from "lucide-react";
import { useToast } from './ui/toast';

interface CreateReklamationModalProps {
    isOpen: boolean;
    onClose: () => void;
    lieferantId: number;
    onSuccess: () => void;
}

interface Lieferschein {
    id: number;
    dokumentNummer?: string; // Optional
    originalDateiname: string;
    datum: string;
}

export function CreateReklamationModal({ isOpen, onClose, lieferantId, onSuccess }: CreateReklamationModalProps) {
    const toast = useToast();
    const [beschreibung, setBeschreibung] = useState("");
    const [files, setFiles] = useState<File[]>([]);

    // Lieferschein Search State
    const [searchTerm, setSearchTerm] = useState("");
    const [searchResults, setSearchResults] = useState<Lieferschein[]>([]);
    const [selectedLieferschein, setSelectedLieferschein] = useState<Lieferschein | null>(null);

    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setBeschreibung("");
            setFiles([]);
            setSearchTerm("");
            setSearchResults([]);
            setSelectedLieferschein(null);
        }
    }, [isOpen]);

    // Search Lieferscheine Debounced
    useEffect(() => {
        const delayDebounceFn = setTimeout(async () => {
            if (searchTerm.length > 1) {
                try {
                    const res = await fetch(`/api/reklamationen/lieferscheine/search?lieferantId=${lieferantId}&query=${encodeURIComponent(searchTerm)}`);
                    if (res.ok) {
                        const data = await res.json();
                        setSearchResults(data);
                    }
                } catch (error) {
                    console.error("Search failed", error);
                }
            } else {
                setSearchResults([]);
            }
        }, 500);

        return () => clearTimeout(delayDebounceFn);
    }, [searchTerm, lieferantId]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files) {
            const newFiles = Array.from(e.target.files);
            setFiles(prev => [...prev, ...newFiles]);
        }
    };

    const removeFile = (index: number) => {
        setFiles(prev => prev.filter((_, i) => i !== index));
    };

    const handleSubmit = async () => {
        if (!beschreibung) return;

        setSubmitting(true);
        try {
            // 1. Create Reclamation
            const createRes = await fetch(`/api/reklamationen/lieferant/${lieferantId}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    beschreibung,
                    lieferscheinId: selectedLieferschein?.id
                })
            });

            if (!createRes.ok) throw new Error("Erstellen fehlgeschlagen");

            const newReklamation = await createRes.json();

            // 2. Upload Images
            if (files.length > 0) {
                for (const file of files) {
                    const formData = new FormData();
                    formData.append("datei", file);

                    await fetch(`/api/reklamationen/${newReklamation.id}/bilder`, {
                        method: "POST",
                        body: formData
                    });
                }
            }

            onSuccess();
            onClose();
        } catch (error) {
            console.error(error);
            toast.error("Fehler beim Erstellen der Reklamation.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="sm:max-w-[600px]">
                <DialogHeader>
                    <DialogTitle>Neue Reklamation erstellen</DialogTitle>
                </DialogHeader>

                <div className="space-y-6 py-4">
                    {/* Description */}
                    <div className="space-y-2">
                        <Label>Beschreibung / Grund der Reklamation</Label>
                        <textarea
                            className="flex min-h-[100px] w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                            placeholder="Beschreiben Sie das Problem..."
                            value={beschreibung}
                            onChange={(e) => setBeschreibung(e.target.value)}
                        />
                    </div>

                    {/* Lieferschein Selection */}
                    <div className="space-y-2">
                        <Label>Zugehöriger Lieferschein (Optional)</Label>
                        {selectedLieferschein ? (
                            <div className="flex items-center justify-between p-2 bg-slate-50 border border-slate-200 rounded-md">
                                <div className="flex items-center gap-2 overflow-hidden">
                                    <FileText className="h-4 w-4 text-slate-500" />
                                    <span className="text-sm font-medium truncate">
                                        {selectedLieferschein.dokumentNummer || selectedLieferschein.originalDateiname}
                                    </span>
                                    <span className="text-xs text-slate-400">
                                        {selectedLieferschein.datum}
                                    </span>
                                </div>
                                <Button variant="ghost" size="sm" onClick={() => setSelectedLieferschein(null)}>
                                    <X className="h-4 w-4" />
                                </Button>
                            </div>
                        ) : (
                            <div className="relative">
                                <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                <Input
                                    placeholder="Lieferschein suchen (Nr. oder Datei)..."
                                    value={searchTerm}
                                    onChange={(e) => setSearchTerm(e.target.value)}
                                    className="pl-9"
                                />
                                {searchResults.length > 0 && (
                                    <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-slate-200 rounded-md shadow-lg z-10 max-h-48 overflow-auto">
                                        {searchResults.map(ls => (
                                            <div
                                                key={ls.id}
                                                className="p-2 hover:bg-slate-50 cursor-pointer text-sm flex items-center justify-between"
                                                onClick={() => {
                                                    setSelectedLieferschein(ls);
                                                    setSearchTerm("");
                                                    setSearchResults([]);
                                                }}
                                            >
                                                <span className="font-medium text-slate-800">
                                                    {ls.dokumentNummer || ls.originalDateiname}
                                                </span>
                                                <span className="text-slate-500 text-xs">{ls.datum}</span>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>

                    {/* Image Upload */}
                    <div className="space-y-2">
                        <Label>Bilder hinzufügen</Label>
                        <div className="grid grid-cols-4 gap-2">
                            {files.map((file, i) => (
                                <div key={i} className="relative aspect-square bg-slate-100 rounded-md overflow-hidden border border-slate-200 group">
                                    <img
                                        src={URL.createObjectURL(file)}
                                        alt="Preview"
                                        className="h-full w-full object-cover"
                                    />
                                    <button
                                        onClick={() => removeFile(i)}
                                        className="absolute top-1 right-1 bg-black/50 text-white rounded-full p-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                                    >
                                        <X className="h-3 w-3" />
                                    </button>
                                </div>
                            ))}
                            <label className="border-2 border-dashed border-slate-200 rounded-md flex flex-col items-center justify-center cursor-pointer hover:bg-slate-50 transition-colors aspect-square">
                                <Upload className="h-5 w-5 text-slate-400 mb-1" />
                                <span className="text-[10px] text-slate-500 font-medium">Upload</span>
                                <input
                                    type="file"
                                    multiple
                                    accept="image/*"
                                    className="hidden"
                                    onChange={handleFileChange}
                                />
                            </label>
                        </div>
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose} disabled={submitting}>Abbrechen</Button>
                    <Button
                        className="bg-rose-600 hover:bg-rose-700"
                        onClick={handleSubmit}
                        disabled={!beschreibung || submitting}
                    >
                        {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                        Reklamation erstellen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
