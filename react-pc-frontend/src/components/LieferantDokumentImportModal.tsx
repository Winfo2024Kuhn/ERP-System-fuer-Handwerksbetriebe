import React, { useState, useRef, useEffect } from 'react';
import { Button } from './ui/button';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from './ui/dialog';
import { FileText, Upload, AlertCircle, RefreshCw, Hash, Euro, CheckCircle, Percent, Eye, EyeOff } from 'lucide-react';
import { Input } from './ui/input';
import { Select } from './ui/select-custom';
import { DatePicker } from './ui/datepicker';
import type { LieferantDokument } from '../types';

interface LieferantDokumentImportModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: (createdDokumente: LieferantDokument[]) => void;
    lieferantId: number | string;
}

type Step = 'upload' | 'edit' | 'importing';

interface MultiInvoiceAnalyzeResponse {
    pageRange: string;
    analyzeResponse: AnalyzeResponse;
    splitPdfBase64: string | null;
}

interface AnalyzeResponse {
    dokumentTyp: string;
    dokumentNummer: string | null;
    dokumentDatum: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    mwstSatz: number | null;
    liefertermin: string | null;
    zahlungsziel: string | null;
    bestellnummer: string | null;
    referenzNummer: string | null;
    skontoTage: number | null;
    skontoProzent: number | null;
    nettoTage: number | null;
    bereitsGezahlt: boolean | null;
    zahlungsart: string | null;
    aiConfidence: number | null;
    analyseQuelle: string | null;
}

const DOKUMENT_TYP_OPTIONS = [
    { value: 'RECHNUNG', label: 'Rechnung' },
    { value: 'ANGEBOT', label: 'Angebot' },
    { value: 'AUFTRAGSBESTAETIGUNG', label: 'Auftragsbestätigung' },
    { value: 'LIEFERSCHEIN', label: 'Lieferschein' },
    { value: 'GUTSCHRIFT', label: 'Gutschrift' },
    { value: 'SONSTIG', label: 'Sonstiges' },
];

const ZAHLUNGSART_OPTIONS = [
    { value: '', label: 'Nicht erkannt' },
    { value: 'VORAUSKASSE', label: 'Vorauskasse' },
    { value: 'SEPA_LASTSCHRIFT', label: 'SEPA-Lastschrift' },
    { value: 'KREDITKARTE', label: 'Kreditkarte' },
    { value: 'PAYPAL', label: 'PayPal' },
    { value: 'AMAZON_PAY', label: 'Amazon Pay' },
    { value: 'UEBERWEISUNG', label: 'Überweisung' },
    { value: 'BAR', label: 'Bar' },
    { value: 'SONSTIGE', label: 'Sonstige' },
];

export function LieferantDokumentImportModal({
    isOpen,
    onClose,
    onSuccess,
    lieferantId
}: LieferantDokumentImportModalProps) {
    const [step, setStep] = useState<Step>('upload');
    const [file, setFile] = useState<File | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Metadata State
    const [metadata, setMetadata] = useState({
        dokumentTyp: 'RECHNUNG',
        dokumentNummer: '',
        dokumentDatum: new Date().toISOString().split('T')[0],
        betragNetto: '',
        betragBrutto: '',
        zahlungsziel: '',
        bestellnummer: '',
        referenzNummer: '',
        skontoTage: '',
        skontoProzent: '',
        nettoTage: '',
        bereitsGezahlt: false,
        zahlungsart: '',
    });

    const [aiConfidence, setAiConfidence] = useState<number>(0);
    const [analyseQuelle, setAnalyseQuelle] = useState<string>('');

    // PDF Preview State
    const [showPdfPreview, setShowPdfPreview] = useState(false);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);

    // Reset when opening
    useEffect(() => {
        if (isOpen) {
            setStep('upload');
            setFile(null);
            setError(null);
            setLoading(false);
            setAiConfidence(0);
            setAnalyseQuelle('');
            setShowPdfPreview(false);
            // Clean up old PDF URL
            if (pdfUrl) {
                URL.revokeObjectURL(pdfUrl);
                setPdfUrl(null);
            }
            setMetadata({
                dokumentTyp: 'RECHNUNG',
                dokumentNummer: '',
                dokumentDatum: new Date().toISOString().split('T')[0],
                betragNetto: '',
                betragBrutto: '',
                zahlungsziel: '',
                bestellnummer: '',
                referenzNummer: '',
                skontoTage: '',
                skontoProzent: '',
                nettoTage: '',
                bereitsGezahlt: false,
                zahlungsart: '',
            });
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen]);

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            const selectedFile = e.target.files[0];
            setFile(selectedFile);
            setError(null);

            // Create blob URL for PDF preview
            if (selectedFile.type === 'application/pdf' || selectedFile.name.endsWith('.pdf')) {
                if (pdfUrl) {
                    URL.revokeObjectURL(pdfUrl);
                }
                const url = URL.createObjectURL(selectedFile);
                setPdfUrl(url);
            } else {
                setPdfUrl(null);
            }

            // Auto-advance to extract
            await extractMetadata(selectedFile);
        }
    };

    const extractMetadata = async (uploadedFile: File) => {
        setLoading(true);
        setError(null);
        try {
            const formData = new FormData();
            formData.append('datei', uploadedFile);

            // Call analyze endpoint
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/analyze`, {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) {
                throw new Error('Metadaten konnten nicht extrahiert werden.');
            }

            const data: MultiInvoiceAnalyzeResponse[] = await res.json();

            if (data.length === 0) {
                throw new Error('Keine Daten erkannt.');
            }

            // Bei MEHREREN Rechnungen: Automatischer Batch-Import
            if (data.length > 1) {
                setStep('importing');
                await batchImportInvoices(data);
                return;
            }

            // Bei EINER Rechnung: Normal im Edit-Modus anzeigen
            const firstInvoice = data[0].analyzeResponse;

            // Merge extracted data with existing defaults
            setMetadata(prev => ({
                ...prev,
                dokumentTyp: firstInvoice.dokumentTyp || prev.dokumentTyp,
                dokumentNummer: firstInvoice.dokumentNummer || prev.dokumentNummer,
                dokumentDatum: firstInvoice.dokumentDatum || prev.dokumentDatum,
                betragNetto: firstInvoice.betragNetto ? firstInvoice.betragNetto.toString() : prev.betragNetto,
                betragBrutto: firstInvoice.betragBrutto ? firstInvoice.betragBrutto.toString() : prev.betragBrutto,
                zahlungsziel: firstInvoice.zahlungsziel || prev.zahlungsziel,
                bestellnummer: firstInvoice.bestellnummer || prev.bestellnummer,
                referenzNummer: firstInvoice.referenzNummer || prev.referenzNummer,
                skontoTage: firstInvoice.skontoTage ? firstInvoice.skontoTage.toString() : prev.skontoTage,
                skontoProzent: firstInvoice.skontoProzent ? firstInvoice.skontoProzent.toString() : prev.skontoProzent,
                nettoTage: firstInvoice.nettoTage ? firstInvoice.nettoTage.toString() : prev.nettoTage,
                bereitsGezahlt: firstInvoice.bereitsGezahlt ?? prev.bereitsGezahlt,
                zahlungsart: firstInvoice.zahlungsart ?? prev.zahlungsart,
            }));

            setAiConfidence(firstInvoice.aiConfidence || 0);
            setAnalyseQuelle(firstInvoice.analyseQuelle || '');

            // Move to edit step
            setStep('edit');
        } catch (err) {
            console.error(err);
            setError('Fehler beim Analysieren der Datei. Sie können die Daten manuell eingeben.');
            setStep('edit'); // Fallback to edit even if extraction failed
        } finally {
            setLoading(false);
        }
    };

    /**
     * Batch-Import für Multi-Invoice PDFs (z.B. Amazon).
     * Importiert alle erkannten Rechnungen automatisch ohne Benutzerinteraktion.
     */
    const batchImportInvoices = async (invoices: MultiInvoiceAnalyzeResponse[]) => {
        setLoading(true);
        setError(null);
        let successCount = 0;
        let errorCount = 0;
        const createdDokumente: LieferantDokument[] = [];

        for (const invoice of invoices) {
            try {
                // Base64 zu Blob konvertieren
                const pdfBytes = invoice.splitPdfBase64
                    ? Uint8Array.from(atob(invoice.splitPdfBase64), c => c.charCodeAt(0))
                    : null;

                if (!pdfBytes) {
                    errorCount++;
                    continue;
                }

                const pdfBlob = new Blob([pdfBytes], { type: 'application/pdf' });
                const fileName = `${invoice.analyzeResponse.dokumentNummer || 'rechnung'}_seiten_${invoice.pageRange}.pdf`;
                const pdfFile = new File([pdfBlob], fileName, { type: 'application/pdf' });

                const formData = new FormData();
                formData.append('datei', pdfFile);

                const importPayload = {
                    dokumentTyp: invoice.analyzeResponse.dokumentTyp || 'RECHNUNG',
                    dokumentNummer: invoice.analyzeResponse.dokumentNummer,
                    dokumentDatum: invoice.analyzeResponse.dokumentDatum,
                    betragNetto: invoice.analyzeResponse.betragNetto,
                    betragBrutto: invoice.analyzeResponse.betragBrutto,
                    zahlungsziel: invoice.analyzeResponse.zahlungsziel,
                    bestellnummer: invoice.analyzeResponse.bestellnummer,
                    referenzNummer: invoice.analyzeResponse.referenzNummer,
                    skontoTage: invoice.analyzeResponse.skontoTage,
                    skontoProzent: invoice.analyzeResponse.skontoProzent,
                    nettoTage: invoice.analyzeResponse.nettoTage,
                    bereitsGezahlt: invoice.analyzeResponse.bereitsGezahlt ?? true, // Amazon = meist Lastschrift
                    zahlungsart: invoice.analyzeResponse.zahlungsart ?? null,
                };

                formData.append('metadata', new Blob([JSON.stringify(importPayload)], {
                    type: 'application/json'
                }));

                const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/import`, {
                    method: 'POST',
                    body: formData,
                });

                if (res.ok) {
                    const created = await res.json() as LieferantDokument;
                    createdDokumente.push(created);
                    successCount++;
                } else if (res.status === 409) {
                    // Duplikat - überspringen aber nicht als Fehler zählen
                    console.warn('Duplikat übersprungen:', invoice.analyzeResponse.dokumentNummer);
                } else {
                    errorCount++;
                }
            } catch (err) {
                console.error('Batch-Import Fehler:', err);
                errorCount++;
            }
        }

        setLoading(false);

        if (successCount > 0) {
            onSuccess(createdDokumente);
            onClose();
        } else if (errorCount > 0) {
            setError(`${errorCount} von ${invoices.length} Rechnungen konnten nicht importiert werden.`);
            setStep('upload');
        }
    };

    const handleSave = async () => {
        if (!file) return;

        setLoading(true);
        setError(null);

        const formData = new FormData();
        formData.append('datei', file);

        // Prepare JSON payload
        const importPayload = {
            dokumentTyp: metadata.dokumentTyp,
            dokumentNummer: metadata.dokumentNummer || null,
            dokumentDatum: metadata.dokumentDatum || null,
            betragNetto: metadata.betragNetto ? parseFloat(metadata.betragNetto.replace(',', '.')) : null,
            betragBrutto: metadata.betragBrutto ? parseFloat(metadata.betragBrutto.replace(',', '.')) : null,
            zahlungsziel: metadata.zahlungsziel || null,
            bestellnummer: metadata.bestellnummer || null,
            referenzNummer: metadata.referenzNummer || null,
            skontoTage: metadata.skontoTage ? parseInt(metadata.skontoTage) : null,
            skontoProzent: metadata.skontoProzent ? parseFloat(metadata.skontoProzent.replace(',', '.')) : null,
            nettoTage: metadata.nettoTage ? parseInt(metadata.nettoTage) : null,
            bereitsGezahlt: metadata.bereitsGezahlt,
            zahlungsart: metadata.zahlungsart || null,
        };

        // Append as Blob/JSON for RequestPart
        formData.append('metadata', new Blob([JSON.stringify(importPayload)], {
            type: 'application/json'
        }));


        try {
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/import`, {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                if (res.status === 409) {
                    throw new Error(errData.message || 'Dokumentnummer existiert bereits für diesen Lieferanten.');
                }
                throw new Error(errData.message || `Speichern fehlgeschlagen: ${res.statusText}`);
            }

            const created = await res.json() as LieferantDokument;
            onSuccess([created]);
            onClose();
        } catch (err) {
            console.error(err);
            setError(err instanceof Error ? err.message : 'Ein unbekannter Fehler ist aufgetreten.');
        } finally {
            setLoading(false);
        }
    };

    const getConfidenceColor = (confidence: number) => {
        if (confidence >= 0.9) return 'bg-green-100 text-green-700';
        if (confidence >= 0.7) return 'bg-yellow-100 text-yellow-700';
        return 'bg-red-100 text-red-700';
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className={showPdfPreview ? "sm:max-w-[90vw] max-h-[90vh]" : "sm:max-w-2xl max-h-[90vh] overflow-y-auto"}>
                <DialogHeader>
                    <DialogTitle>
                        {step === 'upload' && 'Dokument importieren - Datei wählen'}
                        {step === 'edit' && 'Dokument importieren - Prüfen & Speichern'}
                        {step === 'importing' && 'Mehrere Rechnungen erkannt - Importiere...'}
                    </DialogTitle>
                </DialogHeader>

                <div className={showPdfPreview ? "flex gap-4" : ""}>
                    {/* PDF Preview Panel */}
                    {showPdfPreview && pdfUrl && (
                        <div className="w-1/2 h-[60vh] border border-slate-200 rounded-lg overflow-hidden">
                            <iframe
                                src={pdfUrl}
                                className="w-full h-full"
                                title="PDF-Vorschau"
                            />
                        </div>
                    )}

                    {/* Main Content */}
                    <div className={showPdfPreview ? "w-1/2 overflow-y-auto max-h-[60vh]" : ""}>

                        <div className="py-4">
                            {step === 'importing' && (
                                <div className="space-y-4 text-center py-10">
                                    <RefreshCw className="w-12 h-12 text-rose-500 mx-auto animate-spin" />
                                    <div>
                                        <p className="text-lg font-semibold text-slate-900">Mehrere Rechnungen erkannt!</p>
                                        <p className="text-sm text-slate-500 mt-1">Die erkannten Rechnungen werden automatisch importiert...</p>
                                    </div>
                                </div>
                            )}

                            {step === 'upload' && (
                                <div className="space-y-4">
                                    <p className="text-sm text-slate-500">
                                        Laden Sie ein PDF oder XML-Dokument hoch. Die Daten werden automatisch per KI analysiert.
                                    </p>
                                    <div
                                        className="border-2 border-dashed border-slate-200 rounded-xl p-10 flex flex-col items-center justify-center text-center hover:bg-slate-50 transition-colors cursor-pointer"
                                        onClick={() => !loading && fileInputRef.current?.click()}
                                    >
                                        <input
                                            type="file"
                                            ref={fileInputRef}
                                            className="hidden"
                                            accept=".pdf,.xml"
                                            onChange={handleFileChange}
                                            disabled={loading}
                                            aria-label="Dokumentdatei auswählen"
                                        />
                                        {loading ? (
                                            <>
                                                <RefreshCw className="w-10 h-10 text-rose-500 mb-4 animate-spin" />
                                                <p className="font-medium text-slate-900">Analysiere Datei mit KI...</p>
                                                <p className="text-xs text-slate-500 mt-1">Dies kann einige Sekunden dauern</p>
                                            </>
                                        ) : (
                                            <>
                                                <Upload className="w-10 h-10 text-slate-300 mb-4" />
                                                <p className="font-medium text-slate-900">PDF oder XML auswählen</p>
                                                <p className="text-xs text-slate-500 mt-1">.pdf und .xml Dateien werden unterstützt</p>
                                            </>
                                        )}
                                    </div>
                                </div>
                            )}

                            {step === 'edit' && (
                                <div className="space-y-4">
                                    {/* File Info */}
                                    <div className="flex items-center gap-3 p-3 bg-slate-50 rounded-lg border border-slate-100">
                                        <FileText className="w-8 h-8 text-rose-500" />
                                        <div className="flex-1 overflow-hidden">
                                            <p className="font-medium text-slate-900 truncate">{file?.name}</p>
                                            <div className="flex items-center gap-2 text-xs text-slate-500">
                                                <span>{(file!.size / 1024).toFixed(1)} KB</span>
                                                {analyseQuelle && (
                                                    <span className="bg-slate-200 px-1.5 py-0.5 rounded">
                                                        Quelle: {analyseQuelle}
                                                    </span>
                                                )}
                                                {aiConfidence > 0 && (
                                                    <span className={`px-1.5 py-0.5 rounded ${getConfidenceColor(aiConfidence)}`}>
                                                        {Math.round(aiConfidence * 100)}% Konfidenz
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                        <Button variant="ghost" size="sm" onClick={() => setStep('upload')}>
                                            Ändern
                                        </Button>
                                        {pdfUrl && (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => setShowPdfPreview(!showPdfPreview)}
                                                className="text-rose-600"
                                            >
                                                {showPdfPreview ? (
                                                    <><EyeOff className="w-4 h-4 mr-1" /> Vorschau ausblenden</>
                                                ) : (
                                                    <><Eye className="w-4 h-4 mr-1" /> Vorschau</>
                                                )}
                                            </Button>
                                        )}
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        {/* Dokumenttyp */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Dokumenttyp</label>
                                            <Select
                                                value={metadata.dokumentTyp}
                                                onChange={(val) => setMetadata(prev => ({ ...prev, dokumentTyp: val }))}
                                                options={DOKUMENT_TYP_OPTIONS}
                                            />
                                        </div>

                                        {/* Nummer */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Dokumentnummer</label>
                                            <div className="relative">
                                                <Hash className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                <Input
                                                    value={metadata.dokumentNummer}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, dokumentNummer: e.target.value }))}
                                                    className="pl-9"
                                                    placeholder="z.B. RE-2024-001"
                                                />
                                            </div>
                                        </div>

                                        {/* Datum */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Dokumentdatum</label>
                                            <DatePicker
                                                value={metadata.dokumentDatum}
                                                onChange={(value) => setMetadata(prev => ({ ...prev, dokumentDatum: value }))}
                                                placeholder="Datum wählen"
                                            />
                                        </div>

                                        {/* Betrag Brutto */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Betrag Brutto</label>
                                            <div className="relative">
                                                <Euro className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                <Input
                                                    type="number"
                                                    step="0.01"
                                                    value={metadata.betragBrutto}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, betragBrutto: e.target.value }))}
                                                    className="pl-9"
                                                    placeholder="0.00"
                                                />
                                            </div>
                                        </div>

                                        {/* Betrag Netto */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Betrag Netto</label>
                                            <div className="relative">
                                                <Euro className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                <Input
                                                    type="number"
                                                    step="0.01"
                                                    value={metadata.betragNetto}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, betragNetto: e.target.value }))}
                                                    className="pl-9"
                                                    placeholder="0.00"
                                                />
                                            </div>
                                        </div>

                                        {/* Zahlungsziel */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Zahlungsziel</label>
                                            <DatePicker
                                                value={metadata.zahlungsziel}
                                                onChange={(value) => setMetadata(prev => ({ ...prev, zahlungsziel: value }))}
                                                placeholder="Fälligkeitsdatum"
                                            />
                                        </div>

                                        {/* Bestellnummer */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Bestellnummer</label>
                                            <Input
                                                value={metadata.bestellnummer}
                                                onChange={(e) => setMetadata(prev => ({ ...prev, bestellnummer: e.target.value }))}
                                                placeholder="Eigene Bestellnummer"
                                            />
                                        </div>

                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Zahlungsart</label>
                                            <Select
                                                value={metadata.zahlungsart}
                                                onChange={(val) => setMetadata(prev => ({ ...prev, zahlungsart: val }))}
                                                options={ZAHLUNGSART_OPTIONS}
                                            />
                                        </div>

                                        {/* Referenznummer */}
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700">Referenznummer</label>
                                            <Input
                                                value={metadata.referenzNummer}
                                                onChange={(e) => setMetadata(prev => ({ ...prev, referenzNummer: e.target.value }))}
                                                placeholder="Anfrages-/AB-Nummer"
                                            />
                                        </div>
                                    </div>

                                    {/* Skonto Section */}
                                    <div className="border-t border-slate-200 pt-4 mt-4">
                                        <h4 className="text-sm font-medium text-slate-700 mb-3 flex items-center gap-2">
                                            <Percent className="w-4 h-4" />
                                            Skonto-Konditionen
                                        </h4>
                                        <div className="grid grid-cols-3 gap-3">
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">Skonto-Tage</label>
                                                <Input
                                                    type="number"
                                                    value={metadata.skontoTage}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, skontoTage: e.target.value }))}
                                                    placeholder="z.B. 8"
                                                />
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">Skonto %</label>
                                                <Input
                                                    type="number"
                                                    step="0.1"
                                                    value={metadata.skontoProzent}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, skontoProzent: e.target.value }))}
                                                    placeholder="z.B. 2"
                                                />
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">Netto-Tage</label>
                                                <Input
                                                    type="number"
                                                    value={metadata.nettoTage}
                                                    onChange={(e) => setMetadata(prev => ({ ...prev, nettoTage: e.target.value }))}
                                                    placeholder="z.B. 30"
                                                />
                                            </div>
                                        </div>
                                    </div>

                                    {/* Bereits Gezahlt Checkbox */}
                                    <div className="border-t border-slate-200 pt-4 mt-4">
                                        <label className="flex items-center gap-3 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={metadata.bereitsGezahlt}
                                                onChange={(e) => setMetadata(prev => ({ ...prev, bereitsGezahlt: e.target.checked }))}
                                                className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                            />
                                            <div>
                                                <span className="text-sm font-medium text-slate-700">Bereits bezahlt</span>
                                                <p className="text-xs text-slate-500">z.B. bei Amazon Pay, Kreditkarte oder SEPA-Lastschrift</p>
                                            </div>
                                        </label>
                                    </div>
                                </div>
                            )}

                            {error && (
                                <div className="mt-4 bg-red-50 text-red-600 p-3 rounded-md flex items-center gap-2 text-sm border border-red-100">
                                    <AlertCircle className="h-4 w-4 shrink-0" />
                                    <span>{error}</span>
                                </div>
                            )}
                        </div> {/* End py-4 */}
                    </div> {/* End of main content wrapper */}
                </div> {/* End flex container */}

                <DialogFooter>
                    <Button variant="outline" onClick={onClose} disabled={loading}>
                        Abbrechen
                    </Button>
                    {step === 'edit' && (
                        <Button
                            onClick={handleSave}
                            disabled={loading || !metadata.dokumentNummer}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {loading ? (
                                <>
                                    <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                                    Speichere...
                                </>
                            ) : (
                                <>
                                    <CheckCircle className="w-4 h-4 mr-2" />
                                    Importieren & Speichern
                                </>
                            )}
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
