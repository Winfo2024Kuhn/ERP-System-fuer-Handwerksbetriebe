import { useEffect, useMemo, useRef, useState } from "react";
import {
    AlertCircle,
    AlignLeft,
    CheckCircle,
    Euro,
    FileSpreadsheet,
    FileText,
    Folder,
    Hash,
    Layers,
    Package,
    RefreshCw,
    Search,
    Upload,
    X
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select } from "./ui/select-custom";
import { useToast } from "./ui/toast";
import { CategoryTreeModal } from "./CategoryTreeModal";

interface ArtikelImportModalProps {
    onClose: () => void;
    onImportSuccess: () => void;
}

interface ImportAnalysisResult {
    existingCount: number;
    newCount: number;
    newArticleExamples: string[];
}

type MappingKey =
    | "externeArtikelnummer"
    | "preis"
    | "produktlinie"
    | "produktname"
    | "werkstoff"
    | "produkttext"
    | "verpackungseinheit"
    | "preiseinheit"
    | "waehrung";

type FieldDefinition = {
    key: MappingKey;
    label: string;
    required?: boolean;
    hint: string;
    icon: LucideIcon;
};

const FIELD_DEFINITIONS: FieldDefinition[] = [
    {
        key: "externeArtikelnummer",
        label: "Externe Artikelnummer",
        required: true,
        hint: "Lieferanten-Referenznummer für den Artikel",
        icon: Hash
    },
    {
        key: "preis",
        label: "Preis",
        required: true,
        hint: "Nettopreis des Artikels",
        icon: Euro
    },
    {
        key: "produktlinie",
        label: "Produktlinie",
        hint: "Linie oder Serie des Artikels",
        icon: Layers
    },
    {
        key: "produktname",
        label: "Produktname",
        hint: "Bezeichnung des Artikels",
        icon: Package
    },
    {
        key: "werkstoff",
        label: "Werkstoff",
        hint: "Materialangabe des Artikels",
        icon: Package
    },
    {
        key: "produkttext",
        label: "Produkttext",
        hint: "Zusätzliche Beschreibung",
        icon: FileText
    },
    {
        key: "verpackungseinheit",
        label: "Verpackungseinheit",
        hint: "Menge pro Verpackung",
        icon: Hash
    },
    {
        key: "preiseinheit",
        label: "Preiseinheit",
        hint: "Bezugsmenge des Preises",
        icon: AlignLeft
    },
    {
        key: "waehrung",
        label: "Währung",
        hint: "Preiswährung, z. B. EUR",
        icon: Euro
    }
];

const DEFAULT_HEADER_CANDIDATES: Record<MappingKey, string[]> = {
    externeArtikelnummer: ["externeArtikelnummer", "materialnummer"],
    preis: ["preis", "nettopreis"],
    produktlinie: ["produktlinie"],
    produktname: ["produktname"],
    werkstoff: ["werkstoff", "material", "materialbezeichnung"],
    produkttext: ["produkttext"],
    verpackungseinheit: ["verpackungseinheit", "packgroesse"],
    preiseinheit: ["preiseinheit"],
    waehrung: ["waehrung"]
};

const IMPORT_FEEDBACK_STEPS = [
    {
        label: "Import vorbereiten",
        description: "Datei und Zuordnungen werden geprüft."
    },
    {
        label: "Datei hochladen",
        description: "CSV wird an den Server übertragen."
    },
    {
        label: "Daten verarbeiten",
        description: "Artikel werden validiert und verarbeitet."
    },
    {
        label: "Import abschließen",
        description: "Änderungen werden final gespeichert."
    }
];

const createInitialMappings = (): Record<MappingKey, string> => ({
    externeArtikelnummer: "",
    preis: "",
    produktlinie: "",
    produktname: "",
    werkstoff: "",
    produkttext: "",
    verpackungseinheit: "",
    preiseinheit: "",
    waehrung: ""
});

const normalizeHeader = (value: string): string =>
    value
        .trim()
        .toLowerCase()
        .replaceAll("ä", "ae")
        .replaceAll("ö", "oe")
        .replaceAll("ü", "ue")
        .replaceAll("ß", "ss")
        .replace(/[^a-z0-9]/g, "");

const autoMapHeaders = (headers: string[]): Record<MappingKey, string> => {
    const mapped = createInitialMappings();

    FIELD_DEFINITIONS.forEach((field) => {
        const candidates = DEFAULT_HEADER_CANDIDATES[field.key];
        const match = headers.find((header) =>
            candidates.some((candidate) => normalizeHeader(candidate) === normalizeHeader(header))
        );

        if (match) {
            mapped[field.key] = match;
        }
    });

    return mapped;
};

export function ArtikelImportModal({ onClose, onImportSuccess }: ArtikelImportModalProps) {
    const toast = useToast();
    const supplierSearchRef = useRef<HTMLDivElement>(null);
    const importStepTimeoutsRef = useRef<number[]>([]);
    const importAbortControllerRef = useRef<AbortController | null>(null);

    const [file, setFile] = useState<File | null>(null);
    const [csvHeaders, setCsvHeaders] = useState<string[]>([]);
    const [lieferant, setLieferant] = useState("");
    const [lieferantenVorschlaege, setLieferantenVorschlaege] = useState<string[]>([]);
    const [filteredLieferanten, setFilteredLieferanten] = useState<string[]>([]);
    const [supplierDropdownOpen, setSupplierDropdownOpen] = useState(false);
    const [supplierFiltering, setSupplierFiltering] = useState(false);
    const [kategorieId, setKategorieId] = useState<number>(0);
    const [kategorieName, setKategorieName] = useState("");
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [mappings, setMappings] = useState<Record<MappingKey, string>>(createInitialMappings());
    const [analysis, setAnalysis] = useState<ImportAnalysisResult | null>(null);

    const [loadingHeaders, setLoadingHeaders] = useState(false);
    const [analyzing, setAnalyzing] = useState(false);
    const [importing, setImporting] = useState(false);
    const [activeImportStep, setActiveImportStep] = useState(0);
    const [importElapsedSeconds, setImportElapsedSeconds] = useState(0);

    useEffect(() => {
        fetch("/api/artikel/lieferanten")
            .then((res) => res.json())
            .then((data) => {
                const lieferanten = Array.isArray(data) ? data : [];
                setLieferantenVorschlaege(lieferanten);
                setFilteredLieferanten(lieferanten.slice(0, 12));
            })
            .catch(() => {
                setLieferantenVorschlaege([]);
                setFilteredLieferanten([]);
            });
    }, []);

    useEffect(() => {
        setSupplierFiltering(true);

        const timer = window.setTimeout(() => {
            const suchText = lieferant.trim().toLowerCase();
            const treffer = suchText.length === 0
                ? lieferantenVorschlaege
                : lieferantenVorschlaege.filter((name) => name.toLowerCase().includes(suchText));

            setFilteredLieferanten(treffer.slice(0, 20));
            setSupplierFiltering(false);
        }, 180);

        return () => window.clearTimeout(timer);
    }, [lieferant, lieferantenVorschlaege]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Node;
            if (supplierSearchRef.current && !supplierSearchRef.current.contains(target)) {
                setSupplierDropdownOpen(false);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    useEffect(() => {
        if (!importing) {
            setImportElapsedSeconds(0);
            return;
        }

        const interval = window.setInterval(() => {
            setImportElapsedSeconds((prev) => prev + 1);
        }, 1000);

        return () => window.clearInterval(interval);
    }, [importing]);

    useEffect(() => {
        return () => {
            importStepTimeoutsRef.current.forEach((timeout) => window.clearTimeout(timeout));
            importAbortControllerRef.current?.abort();
        };
    }, []);

    const requiredMissing = useMemo(
        () => FIELD_DEFINITIONS.filter((field) => field.required && !mappings[field.key]),
        [mappings]
    );

    const handleSupplierPick = (name: string) => {
        setLieferant(name);
        setSupplierDropdownOpen(false);
    };

    const clearImportFeedbackTimers = () => {
        importStepTimeoutsRef.current.forEach((timeout) => window.clearTimeout(timeout));
        importStepTimeoutsRef.current = [];
    };

    const startImportFeedback = () => {
        clearImportFeedbackTimers();
        setActiveImportStep(0);

        importStepTimeoutsRef.current = [
            window.setTimeout(() => setActiveImportStep(1), 450),
            window.setTimeout(() => setActiveImportStep(2), 1400),
            window.setTimeout(() => setActiveImportStep(3), 3300)
        ];
    };

    const handleModalClose = () => {
        if (importing) {
            toast.info("Import läuft noch. Bitte warten oder zuerst 'Import abbrechen' wählen.");
            return;
        }
        onClose();
    };

    const handleAbortImport = () => {
        if (!importing) {
            return;
        }
        importAbortControllerRef.current?.abort();
    };

    const handleFileSelected = async (selectedFile: File | null) => {
        setFile(selectedFile);
        setCsvHeaders([]);
        setMappings(createInitialMappings());
        setAnalysis(null);

        if (!selectedFile) {
            return;
        }

        setLoadingHeaders(true);
        try {
            const formData = new FormData();
            formData.append("file", selectedFile);

            const res = await fetch("/api/artikel/import/headers", {
                method: "POST",
                body: formData
            });

            if (!res.ok) {
                throw new Error("Header konnten nicht gelesen werden.");
            }

            const headers = await res.json();
            const parsedHeaders = Array.isArray(headers) ? headers : [];
            setCsvHeaders(parsedHeaders);
            setMappings(autoMapHeaders(parsedHeaders));
        } catch (error) {
            toast.error("CSV-Header konnten nicht geladen werden.");
            console.error(error);
        } finally {
            setLoadingHeaders(false);
        }
    };

    const buildFormData = () => {
        const formData = new FormData();

        if (file) {
            formData.append("file", file);
        }
        formData.append("lieferant", lieferant.trim());

        if (kategorieId > 0) {
            formData.append("kategorieId", String(kategorieId));
        }

        FIELD_DEFINITIONS.forEach((field) => {
            formData.append(field.key, mappings[field.key] || "");
        });

        return formData;
    };

    const validateBeforeSubmit = () => {
        if (!file) {
            toast.warning("Bitte wählen Sie eine CSV-Datei aus.");
            return false;
        }
        if (!lieferant.trim()) {
            toast.warning("Bitte geben Sie einen Lieferanten an.");
            return false;
        }
        if (requiredMissing.length > 0) {
            toast.warning("Bitte ordnen Sie alle Pflichtfelder zu.");
            return false;
        }
        return true;
    };

    const handleAnalyze = async () => {
        if (!validateBeforeSubmit()) {
            return;
        }

        setAnalyzing(true);
        setAnalysis(null);
        try {
            const res = await fetch("/api/artikel/import/analyze", {
                method: "POST",
                body: buildFormData()
            });

            if (!res.ok) {
                throw new Error("Analyse fehlgeschlagen");
            }

            const data = await res.json();
            setAnalysis(data);
        } catch (error) {
            toast.error("Import-Analyse fehlgeschlagen.");
            console.error(error);
        } finally {
            setAnalyzing(false);
        }
    };

    const handleImport = async () => {
        if (!validateBeforeSubmit()) {
            return;
        }

        setImporting(true);
        setImportElapsedSeconds(0);
        startImportFeedback();

        const abortController = new AbortController();
        importAbortControllerRef.current = abortController;

        try {
            const res = await fetch("/api/artikel/import", {
                method: "POST",
                body: buildFormData(),
                signal: abortController.signal
            });

            if (!res.ok) {
                throw new Error("Import fehlgeschlagen");
            }

            setActiveImportStep(IMPORT_FEEDBACK_STEPS.length - 1);
            toast.success("Artikel erfolgreich importiert.");
            onImportSuccess();
            onClose();
        } catch (error) {
            const isAbortError = (error as { name?: string })?.name === "AbortError";
            if (isAbortError) {
                toast.info("Import wurde abgebrochen.");
                return;
            }
            toast.error("Artikel-Import fehlgeschlagen.");
            console.error(error);
        } finally {
            clearImportFeedbackTimers();
            importAbortControllerRef.current = null;
            setImporting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[92vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
                <div className="p-6 border-b border-slate-100 flex items-center justify-between">
                    <div>
                        <h3 className="text-xl font-semibold text-slate-900 flex items-center gap-2">
                            <FileSpreadsheet className="w-5 h-5 text-rose-600" />
                            Artikel aus CSV importieren
                        </h3>
                        <p className="text-sm text-slate-500 mt-1">Spalten manuell zuordnen: Datenbankfeld → CSV-Spalte</p>
                    </div>
                    <button title="Schließen" aria-label="Schließen" onClick={handleModalClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="p-6 space-y-6">
                    <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
                        <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3 xl:col-span-2">
                            <div className="flex items-center gap-2">
                                <Upload className="w-4 h-4 text-rose-600" />
                                <h4 className="font-medium text-slate-800">CSV-Datei</h4>
                            </div>

                            <input
                                id="artikel-import-file"
                                type="file"
                                className="hidden"
                                accept=".csv,text/csv,.txt"
                                disabled={importing}
                                onChange={(e) => handleFileSelected(e.target.files?.[0] || null)}
                            />

                            <label htmlFor="artikel-import-file" className={`block ${importing ? "pointer-events-none opacity-60" : "cursor-pointer"}`}>
                                <div className="border-2 border-dashed border-slate-300 rounded-xl p-6 text-center hover:border-rose-400 transition-colors">
                                    <FileSpreadsheet className="w-10 h-10 mx-auto mb-2 text-slate-400" />
                                    <p className="text-sm font-medium text-slate-700">{file ? "Datei wechseln" : "CSV-Datei auswählen"}</p>
                                    <p className="text-xs text-slate-500 mt-1">Semikolon- oder Komma-separierte Datei</p>
                                </div>
                            </label>

                            <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                                {file ? (
                                    <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-slate-700">
                                        <FileSpreadsheet className="w-3.5 h-3.5" />
                                        {file.name}
                                    </span>
                                ) : (
                                    <span className="text-slate-500">Noch keine Datei ausgewählt</span>
                                )}

                                {loadingHeaders && <span className="text-rose-600">CSV-Header werden geladen...</span>}
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-4">
                            <div className="space-y-1.5" ref={supplierSearchRef}>
                                <Label htmlFor="artikel-import-lieferant" className="text-sm font-medium text-slate-700 flex items-center gap-2">
                                    <Search className="w-4 h-4 text-rose-600" />
                                    Lieferant *
                                </Label>
                                <div className="relative">
                                    <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                    <Input
                                        id="artikel-import-lieferant"
                                        placeholder="Lieferant suchen oder neu eingeben"
                                        value={lieferant}
                                        disabled={importing}
                                        onChange={(e) => {
                                            setLieferant(e.target.value);
                                            setSupplierDropdownOpen(true);
                                        }}
                                        onFocus={() => setSupplierDropdownOpen(true)}
                                        onClick={() => setSupplierDropdownOpen(true)}
                                        className="pl-9 pr-9"
                                        autoComplete="off"
                                    />

                                    {lieferant && (
                                        <button
                                            type="button"
                                            title="Lieferantenfeld leeren"
                                            aria-label="Lieferantenfeld leeren"
                                            className="absolute right-2 top-2 text-slate-400 hover:text-rose-600"
                                            onClick={() => {
                                                setLieferant("");
                                                setSupplierDropdownOpen(true);
                                            }}
                                        >
                                            <X className="w-4 h-4" />
                                        </button>
                                    )}

                                    {supplierDropdownOpen && !importing && (
                                        <div className="absolute z-50 mt-1 w-full rounded-lg border border-slate-200 bg-white shadow-lg max-h-56 overflow-y-auto">
                                            {supplierFiltering ? (
                                                <div className="px-3 py-2 text-sm text-slate-500">Suche läuft...</div>
                                            ) : filteredLieferanten.length === 0 ? (
                                                <div className="px-3 py-2 text-sm text-slate-500">Kein Lieferant gefunden.</div>
                                            ) : (
                                                filteredLieferanten.map((name) => (
                                                    <button
                                                        key={name}
                                                        type="button"
                                                        className="w-full text-left px-3 py-2 text-sm text-slate-700 hover:bg-rose-50 hover:text-rose-700 transition-colors"
                                                        onMouseDown={(e) => e.preventDefault()}
                                                        onClick={() => handleSupplierPick(name)}
                                                    >
                                                        {name}
                                                    </button>
                                                ))
                                            )}
                                        </div>
                                    )}
                                </div>
                                <p className="text-xs text-slate-500">
                                    Tippen zum asynchronen Filtern. Ein neuer Name kann direkt eingegeben werden.
                                </p>
                            </div>

                            <div className="space-y-1.5">
                                <Label className="text-sm font-medium text-slate-700 flex items-center gap-2">
                                    <Folder className="w-4 h-4 text-rose-600" />
                                    Standard-Kategorie (optional)
                                </Label>
                                <div className="relative">
                                    <div
                                        className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm cursor-pointer items-center justify-between hover:bg-slate-50"
                                        onClick={() => !importing && setShowCategoryModal(true)}
                                    >
                                        <span className={kategorieName ? "truncate" : "text-slate-500"}>{kategorieName || "Keine Standard-Kategorie"}</span>
                                        <Folder className="w-4 h-4 text-slate-400" />
                                    </div>
                                    {kategorieId > 0 && (
                                        <button
                                            type="button"
                                            title="Standard-Kategorie entfernen"
                                            aria-label="Standard-Kategorie entfernen"
                                            className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600"
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setKategorieId(0);
                                                setKategorieName("");
                                            }}
                                        >
                                            <X className="w-4 h-4" />
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="border border-slate-200 rounded-xl bg-white overflow-hidden">
                        <div className="px-4 py-3 border-b bg-slate-50 flex items-center justify-between gap-3">
                            <div className="flex items-center gap-2">
                                <Layers className="w-4 h-4 text-rose-600" />
                                <h4 className="font-medium text-slate-800">Spaltenzuordnung</h4>
                            </div>
                            {csvHeaders.length > 0 && (
                                <span className="text-xs text-slate-500">
                                    {csvHeaders.length} CSV-Spalten erkannt
                                </span>
                            )}
                        </div>

                        {csvHeaders.length === 0 ? (
                            <div className="p-6 text-sm text-slate-500 flex items-center gap-2">
                                <AlertCircle className="w-4 h-4 text-slate-400" />
                                Bitte zuerst eine CSV-Datei auswählen, damit die Header geladen werden.
                            </div>
                        ) : (
                            <div>
                                {FIELD_DEFINITIONS.map((field) => {
                                    const FieldIcon = field.icon;
                                    const options = [
                                        {
                                            value: "",
                                            label: field.required ? "Bitte auswählen" : "Nicht zuordnen"
                                        },
                                        ...csvHeaders.map((header) => ({ value: header, label: header }))
                                    ];

                                    return (
                                        <div key={field.key} className="grid grid-cols-1 lg:grid-cols-2 gap-3 px-4 py-3 border-b border-slate-100 last:border-b-0">
                                            <div className="flex items-start gap-3">
                                                <div className="h-8 w-8 rounded-lg bg-rose-50 flex items-center justify-center shrink-0">
                                                    <FieldIcon className="w-4 h-4 text-rose-600" />
                                                </div>
                                                <div>
                                                    <Label className="text-sm text-slate-800 font-medium">
                                                        {field.label}
                                                        {field.required && <span className="text-rose-600"> *</span>}
                                                    </Label>
                                                    <p className="text-xs text-slate-500 mt-0.5">{field.hint}</p>
                                                </div>
                                            </div>

                                            <Select
                                                options={options}
                                                value={mappings[field.key]}
                                                onChange={(value) => setMappings((prev) => ({ ...prev, [field.key]: value }))}
                                                placeholder="Bitte auswählen"
                                            />
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>

                    {csvHeaders.length > 0 && requiredMissing.length > 0 && (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 flex items-start gap-2">
                            <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                            <span>
                                Pflichtfelder ohne Zuordnung: {requiredMissing.map((field) => field.label).join(", ")}
                            </span>
                        </div>
                    )}

                    {csvHeaders.length > 0 && requiredMissing.length === 0 && (
                        <div className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800 flex items-center gap-2">
                            <CheckCircle className="w-4 h-4 shrink-0" />
                            Pflichtfelder sind vollständig zugeordnet.
                        </div>
                    )}

                    {analysis && (
                        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
                            <p className="text-slate-700 flex items-center gap-2">
                                <CheckCircle className="w-4 h-4 text-green-600" />
                                Analyse abgeschlossen
                            </p>
                            <p className="text-slate-700 mt-1">
                                <strong>{analysis.newCount}</strong> neue und <strong>{analysis.existingCount}</strong> bestehende Artikel erkannt.
                            </p>
                            {analysis.newArticleExamples?.length > 0 && (
                                <p className="text-slate-600 mt-1">
                                    Beispiele neue Artikel: {analysis.newArticleExamples.join(", ")}
                                </p>
                            )}
                        </div>
                    )}

                    {importing && (
                        <div className="rounded-xl border border-rose-200 bg-rose-50/60 p-4 space-y-3">
                            <div className="flex items-center justify-between gap-3">
                                <p className="text-sm font-medium text-rose-800 flex items-center gap-2">
                                    <RefreshCw className="w-4 h-4 animate-spin" />
                                    Import läuft
                                </p>
                                <span className="text-xs text-rose-700">seit {importElapsedSeconds}s</span>
                            </div>
                            <p className="text-xs text-rose-700">
                                Bitte Seite geöffnet lassen. Je nach Dateigröße kann der Vorgang einige Sekunden dauern.
                            </p>

                            <div className="space-y-2">
                                {IMPORT_FEEDBACK_STEPS.map((step, index) => {
                                    const done = index < activeImportStep;
                                    const current = index === activeImportStep;

                                    return (
                                        <div
                                            key={step.label}
                                            className={`flex items-start gap-2 rounded-md px-2 py-1.5 ${current ? "bg-white border border-rose-200" : ""}`}
                                        >
                                            {done ? (
                                                <CheckCircle className="w-4 h-4 text-green-600 mt-0.5 shrink-0" />
                                            ) : current ? (
                                                <RefreshCw className="w-4 h-4 text-rose-600 mt-0.5 shrink-0 animate-spin" />
                                            ) : (
                                                <div className="w-4 h-4 mt-0.5 rounded-full border border-slate-300 bg-white shrink-0" />
                                            )}
                                            <div>
                                                <p className={`text-sm ${current ? "text-rose-900 font-medium" : done ? "text-slate-800" : "text-slate-600"}`}>
                                                    {step.label}
                                                </p>
                                                <p className="text-xs text-slate-500">{step.description}</p>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>

                <div className="p-6 border-t border-slate-100 bg-slate-50 flex justify-end gap-3 rounded-b-xl">
                    <Button variant="ghost" onClick={handleModalClose} disabled={importing}>
                        Abbrechen
                    </Button>
                    {importing && (
                        <Button variant="outline" onClick={handleAbortImport}>
                            <X className="w-4 h-4 mr-2" />
                            Import abbrechen
                        </Button>
                    )}
                    <Button
                        variant="outline"
                        onClick={handleAnalyze}
                        disabled={analyzing || importing || !file || loadingHeaders}
                    >
                        <Search className="w-4 h-4 mr-2" />
                        {analyzing ? "Analysiere..." : "Analysieren"}
                    </Button>
                    <Button
                        className="bg-rose-600 text-white hover:bg-rose-700"
                        onClick={handleImport}
                        disabled={importing || analyzing || !file || loadingHeaders}
                    >
                        {importing ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Upload className="w-4 h-4 mr-2" />}
                        {importing ? "Import läuft..." : "Import starten"}
                    </Button>
                </div>
            </div>

            {showCategoryModal && (
                <CategoryTreeModal
                    onSelect={(id, name) => {
                        setKategorieId(id);
                        setKategorieName(name);
                        setShowCategoryModal(false);
                    }}
                    onClose={() => setShowCategoryModal(false)}
                />
            )}
        </div>
    );
}
