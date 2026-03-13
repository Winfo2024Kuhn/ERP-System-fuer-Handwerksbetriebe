import React, { useState, useRef, useEffect } from 'react';
import { Button } from './ui/button';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from './ui/dialog';
import { FileText, Upload, AlertCircle, RefreshCw, Hash, Euro, Eye, EyeOff, Search, X, CheckCircle } from 'lucide-react';
import { Input } from './ui/input';
import { DatePicker } from './ui/datepicker';

interface AusgangsrechnungUploadModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
}

type Step = 'upload' | 'edit' | 'importing';

interface AnalyzeResponse {
    dokumentTyp?: string;
    dokumentNummer?: string | null;
    dokumentDatum?: string | null;
    betragNetto?: number | null;
    betragBrutto?: number | null;
    mwstSatz?: number | null;
    zahlungsziel?: string | null;
    bestellnummer?: string | null;
    referenzNummer?: string | null;
    aiConfidence?: number | null;
    analyseQuelle?: string | null;
    error?: string;
}

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string;
    kunde: string;
}

const GESCHAEFTSDOKUMENTART_OPTIONS = [
    { value: 'Rechnung', label: 'Rechnung' },
    { value: 'Teilrechnung', label: 'Teilrechnung' },
    { value: 'Abschlagsrechnung', label: 'Abschlagsrechnung' },
    { value: 'Schlussrechnung', label: 'Schlussrechnung' },
    { value: 'Gutschrift', label: 'Gutschrift' },
];

export function AusgangsrechnungUploadModal({
    isOpen,
    onClose,
    onSuccess,
}: AusgangsrechnungUploadModalProps) {
    const [step, setStep] = useState<Step>('upload');
    const [file, setFile] = useState<File | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Metadata
    const [metadata, setMetadata] = useState({
        rechnungsnummer: '',
        rechnungsdatum: new Date().toISOString().split('T')[0],
        faelligkeitsdatum: '',
        betragBrutto: '',
        geschaeftsdokumentart: 'Rechnung',
    });

    const [aiConfidence, setAiConfidence] = useState<number>(0);
    const [analyseQuelle, setAnalyseQuelle] = useState<string>('');

    // PDF Preview
    const [showPdfPreview, setShowPdfPreview] = useState(false);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);

    // Project search
    const [selectedProjekt, setSelectedProjekt] = useState<Projekt | null>(null);
    const [projektSearch, setProjektSearch] = useState('');
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [projektLoading, setProjektLoading] = useState(false);
    const [showProjektDropdown, setShowProjektDropdown] = useState(false);
    const projektSearchRef = useRef<HTMLDivElement>(null);

    // Reset on open
    useEffect(() => {
        if (isOpen) {
            setStep('upload');
            setFile(null);
            setError(null);
            setLoading(false);
            setAiConfidence(0);
            setAnalyseQuelle('');
            setShowPdfPreview(false);
            setPdfUrl(null);
            setSelectedProjekt(null);
            setProjektSearch('');
            setMetadata({
                rechnungsnummer: '',
                rechnungsdatum: new Date().toISOString().split('T')[0],
                faelligkeitsdatum: '',
                betragBrutto: '',
                geschaeftsdokumentart: 'Rechnung',
            });
        }
    }, [isOpen]);

    // Cleanup PDF URL
    useEffect(() => {
        return () => {
            if (pdfUrl) URL.revokeObjectURL(pdfUrl);
        };
    }, [pdfUrl]);

    // Search projects with debounce
    useEffect(() => {
        const timeout = setTimeout(async () => {
            setProjektLoading(true);
            try {
                const res = await fetch(`/api/projekte/simple?q=${encodeURIComponent(projektSearch)}&size=20`);
                if (res.ok) {
                    const data = await res.json();
                    setProjekte(Array.isArray(data) ? data : []);
                }
            } catch {
                setProjekte([]);
            } finally {
                setProjektLoading(false);
            }
        }, 300);
        return () => clearTimeout(timeout);
    }, [projektSearch]);

    // Close dropdown on outside click
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (projektSearchRef.current && !projektSearchRef.current.contains(e.target as Node)) {
                setShowProjektDropdown(false);
            }
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, []);

    const handleFileSelect = (selectedFile: File) => {
        setFile(selectedFile);
        setError(null);

        // Create preview URL
        if (pdfUrl) URL.revokeObjectURL(pdfUrl);
        const url = URL.createObjectURL(selectedFile);
        setPdfUrl(url);

        // Start AI analysis
        analyzeFile(selectedFile);
    };

    const analyzeFile = async (fileToAnalyze: File) => {
        setLoading(true);
        setError(null);
        setStep('upload');

        try {
            const formData = new FormData();
            formData.append('datei', fileToAnalyze);

            const res = await fetch('/api/offene-posten/ausgang/analyze', {
                method: 'POST',
                body: formData,
            });

            const data: AnalyzeResponse = await res.json();

            if (data.error) {
                // Analysis failed but we can still let user fill in manually
                setError(data.error);
                setStep('edit');
                return;
            }

            // Fill in extracted data
            setMetadata({
                rechnungsnummer: data.dokumentNummer || '',
                rechnungsdatum: data.dokumentDatum || new Date().toISOString().split('T')[0],
                faelligkeitsdatum: data.zahlungsziel || '',
                betragBrutto: data.betragBrutto != null ? String(data.betragBrutto) : '',
                geschaeftsdokumentart: mapDokumentTypToArt(data.dokumentTyp),
            });
            setAiConfidence(data.aiConfidence ?? 0);
            setAnalyseQuelle(data.analyseQuelle || 'KI');
            setStep('edit');
        } catch (err) {
            console.error('Analyse fehlgeschlagen:', err);
            setError('Analyse fehlgeschlagen – bitte manuell ausfüllen');
            setStep('edit');
        } finally {
            setLoading(false);
        }
    };

    const mapDokumentTypToArt = (typ?: string): string => {
        if (!typ) return 'Rechnung';
        const upper = typ.toUpperCase();
        if (upper.includes('SCHLUSS')) return 'Schlussrechnung';
        if (upper.includes('ABSCHLAG')) return 'Abschlagsrechnung';
        if (upper.includes('TEIL')) return 'Teilrechnung';
        if (upper.includes('GUTSCHRIFT')) return 'Gutschrift';
        return 'Rechnung';
    };

    const handleImport = async () => {
        if (!file || !selectedProjekt) return;
        if (!metadata.rechnungsnummer.trim()) {
            setError('Bitte Rechnungsnummer eingeben');
            return;
        }

        setStep('importing');
        setError(null);

        try {
            const formData = new FormData();
            formData.append('datei', file);
            formData.append('projektId', String(selectedProjekt.id));
            formData.append('rechnungsnummer', metadata.rechnungsnummer);
            formData.append('geschaeftsdokumentart', metadata.geschaeftsdokumentart);
            if (metadata.rechnungsdatum) formData.append('rechnungsdatum', metadata.rechnungsdatum);
            if (metadata.faelligkeitsdatum) formData.append('faelligkeitsdatum', metadata.faelligkeitsdatum);
            if (metadata.betragBrutto) formData.append('betragBrutto', metadata.betragBrutto);

            const res = await fetch('/api/offene-posten/ausgang/import', {
                method: 'POST',
                body: formData,
            });

            const result = await res.json();

            if (!res.ok) {
                setError(result.error || 'Import fehlgeschlagen');
                setStep('edit');
                return;
            }

            onSuccess();
            onClose();
        } catch (err) {
            console.error('Import fehlgeschlagen:', err);
            setError('Import fehlgeschlagen');
            setStep('edit');
        }
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        const droppedFile = e.dataTransfer.files[0];
        if (droppedFile) handleFileSelect(droppedFile);
    };

    const confidenceColor = aiConfidence >= 0.8 ? 'text-green-600' : aiConfidence >= 0.5 ? 'text-amber-600' : 'text-red-600';

    return (
        <Dialog open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
            <DialogContent className={`${showPdfPreview && pdfUrl ? 'sm:max-w-6xl' : 'sm:max-w-lg'} max-h-[90vh] overflow-hidden flex flex-col`}>
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <FileText className="w-5 h-5 text-rose-600" />
                        Ausgangsrechnung manuell erfassen
                    </DialogTitle>
                </DialogHeader>

                <div className={`flex-1 overflow-auto ${showPdfPreview && pdfUrl ? 'flex gap-4' : ''}`}>
                    {/* PDF Preview Panel */}
                    {showPdfPreview && pdfUrl && (
                        <div className="w-1/2 border rounded-lg overflow-hidden bg-slate-100 min-h-[400px]">
                            <iframe src={pdfUrl} className="w-full h-full min-h-[500px]" title="PDF Vorschau" />
                        </div>
                    )}

                    <div className={showPdfPreview && pdfUrl ? 'w-1/2' : 'w-full'}>
                        {/* Upload Stage */}
                        {step === 'upload' && !file && (
                            <div
                                className="border-2 border-dashed border-slate-300 rounded-xl p-8 text-center hover:border-rose-400 transition-colors cursor-pointer"
                                onClick={() => fileInputRef.current?.click()}
                                onDrop={handleDrop}
                                onDragOver={(e) => e.preventDefault()}
                            >
                                <Upload className="w-12 h-12 mx-auto mb-3 text-slate-400" />
                                <p className="text-sm font-medium text-slate-700">
                                    Rechnung hier ablegen oder klicken
                                </p>
                                <p className="text-xs text-slate-400 mt-1">PDF, JPG oder PNG</p>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".pdf,.jpg,.jpeg,.png"
                                    className="hidden"
                                    onChange={(e) => {
                                        const f = e.target.files?.[0];
                                        if (f) handleFileSelect(f);
                                    }}
                                />
                            </div>
                        )}

                        {/* Loading */}
                        {step === 'upload' && file && loading && (
                            <div className="flex flex-col items-center justify-center py-12">
                                <RefreshCw className="w-8 h-8 text-rose-500 animate-spin mb-3" />
                                <p className="text-sm font-medium text-slate-700">KI analysiert Dokument...</p>
                                <p className="text-xs text-slate-400 mt-1">{file.name}</p>
                            </div>
                        )}

                        {/* Edit Stage */}
                        {step === 'edit' && (
                            <div className="space-y-4">
                                {/* AI Confidence Badge */}
                                {analyseQuelle && (
                                    <div className="flex items-center gap-2 text-xs">
                                        <span className="px-2 py-0.5 bg-slate-100 rounded-full text-slate-600">
                                            Quelle: {analyseQuelle}
                                        </span>
                                        <span className={`px-2 py-0.5 rounded-full ${confidenceColor} bg-opacity-10`}>
                                            Konfidenz: {Math.round(aiConfidence * 100)}%
                                        </span>
                                        {pdfUrl && (
                                            <button
                                                onClick={() => setShowPdfPreview(!showPdfPreview)}
                                                className="ml-auto flex items-center gap-1 text-slate-500 hover:text-rose-600 transition-colors"
                                            >
                                                {showPdfPreview ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                                <span>{showPdfPreview ? 'Vorschau ausblenden' : 'PDF Vorschau'}</span>
                                            </button>
                                        )}
                                    </div>
                                )}

                                {error && (
                                    <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
                                        <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                        <span>{error}</span>
                                    </div>
                                )}

                                {/* Projekt Selection */}
                                <div ref={projektSearchRef} className="relative">
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                        Projekt *
                                    </label>
                                    {selectedProjekt ? (
                                        <div className="flex items-center gap-2 p-2.5 bg-green-50 border border-green-200 rounded-lg">
                                            <CheckCircle className="w-4 h-4 text-green-600 flex-shrink-0" />
                                            <div className="flex-1 min-w-0">
                                                <span className="text-sm font-medium text-slate-900 block truncate">
                                                    {selectedProjekt.bauvorhaben}
                                                </span>
                                                <span className="text-xs text-slate-500">
                                                    {selectedProjekt.auftragsnummer && `Nr. ${selectedProjekt.auftragsnummer}`}
                                                    {selectedProjekt.kunde && ` · ${selectedProjekt.kunde}`}
                                                </span>
                                            </div>
                                            <button
                                                onClick={() => { setSelectedProjekt(null); setProjektSearch(''); }}
                                                className="text-slate-400 hover:text-slate-600"
                                            >
                                                <X className="w-4 h-4" />
                                            </button>
                                        </div>
                                    ) : (
                                        <>
                                            <div className="relative">
                                                <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                <Input
                                                    placeholder="Projekt suchen..."
                                                    value={projektSearch}
                                                    onChange={(e) => {
                                                        setProjektSearch(e.target.value);
                                                        setShowProjektDropdown(true);
                                                    }}
                                                    onFocus={() => setShowProjektDropdown(true)}
                                                    className="pl-9"
                                                />
                                            </div>
                                            {showProjektDropdown && (
                                                <div className="absolute z-50 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
                                                    {projektLoading ? (
                                                        <div className="p-3 text-center text-sm text-slate-500">Lade...</div>
                                                    ) : projekte.length === 0 ? (
                                                        <div className="p-3 text-center text-sm text-slate-500">Keine Projekte gefunden</div>
                                                    ) : (
                                                        projekte.map((p) => (
                                                            <button
                                                                key={p.id}
                                                                onClick={() => {
                                                                    setSelectedProjekt(p);
                                                                    setShowProjektDropdown(false);
                                                                }}
                                                                className="w-full text-left px-3 py-2 hover:bg-rose-50 transition-colors border-b border-slate-100 last:border-0"
                                                            >
                                                                <span className="text-sm font-medium text-slate-900 block truncate">
                                                                    {p.bauvorhaben}
                                                                </span>
                                                                <span className="text-xs text-slate-500">
                                                                    {p.auftragsnummer && `Nr. ${p.auftragsnummer}`}
                                                                    {p.kunde && ` · ${p.kunde}`}
                                                                </span>
                                                            </button>
                                                        ))
                                                    )}
                                                </div>
                                            )}
                                        </>
                                    )}
                                </div>

                                {/* Dokumentart */}
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                        Dokumentart
                                    </label>
                                    <select
                                        value={metadata.geschaeftsdokumentart}
                                        onChange={(e) => setMetadata({ ...metadata, geschaeftsdokumentart: e.target.value })}
                                        className="w-full h-10 rounded-md border border-slate-200 bg-white px-3 text-sm"
                                    >
                                        {GESCHAEFTSDOKUMENTART_OPTIONS.map(opt => (
                                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                                        ))}
                                    </select>
                                </div>

                                {/* Rechnungsnummer */}
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                        Rechnungsnummer *
                                    </label>
                                    <div className="relative">
                                        <Hash className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                        <Input
                                            value={metadata.rechnungsnummer}
                                            onChange={(e) => setMetadata({ ...metadata, rechnungsnummer: e.target.value })}
                                            className="pl-9"
                                            placeholder="z.B. 2026/03/00012"
                                        />
                                    </div>
                                </div>

                                {/* Dates Row */}
                                <div className="grid grid-cols-2 gap-3">
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                            Rechnungsdatum
                                        </label>
                                        <DatePicker
                                            value={metadata.rechnungsdatum}
                                            onChange={(val) => setMetadata({ ...metadata, rechnungsdatum: val })}
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                            Fälligkeitsdatum
                                        </label>
                                        <DatePicker
                                            value={metadata.faelligkeitsdatum}
                                            onChange={(val) => setMetadata({ ...metadata, faelligkeitsdatum: val })}
                                        />
                                    </div>
                                </div>

                                {/* Betrag */}
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                        Betrag Brutto (€)
                                    </label>
                                    <div className="relative">
                                        <Euro className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                        <Input
                                            type="number"
                                            step="0.01"
                                            value={metadata.betragBrutto}
                                            onChange={(e) => setMetadata({ ...metadata, betragBrutto: e.target.value })}
                                            className="pl-9"
                                            placeholder="0.00"
                                        />
                                    </div>
                                </div>

                                {/* File info */}
                                {file && (
                                    <div className="flex items-center gap-2 p-2 bg-slate-50 rounded-lg text-xs text-slate-600">
                                        <FileText className="w-4 h-4 text-slate-400" />
                                        <span className="truncate">{file.name}</span>
                                        <span className="text-slate-400">({(file.size / 1024).toFixed(0)} KB)</span>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Importing */}
                        {step === 'importing' && (
                            <div className="flex flex-col items-center justify-center py-12">
                                <RefreshCw className="w-8 h-8 text-rose-500 animate-spin mb-3" />
                                <p className="text-sm font-medium text-slate-700">Wird importiert...</p>
                            </div>
                        )}
                    </div>
                </div>

                {step === 'edit' && (
                    <DialogFooter className="flex gap-2 pt-4 border-t">
                        <Button variant="outline" onClick={onClose}>
                            Abbrechen
                        </Button>
                        <Button
                            onClick={handleImport}
                            disabled={!file || !selectedProjekt || !metadata.rechnungsnummer.trim()}
                            className="bg-rose-600 hover:bg-rose-700 text-white"
                        >
                            <Upload className="w-4 h-4 mr-1" />
                            Importieren
                        </Button>
                    </DialogFooter>
                )}
            </DialogContent>
        </Dialog>
    );
}
