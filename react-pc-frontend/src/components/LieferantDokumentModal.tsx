import { useState, useEffect } from "react";
import { Button } from "./ui/button";
import { X, Eye, FileText, Hash, Euro, Link2, FolderPlus, AlertCircle, CheckCircle, RefreshCw, Truck, Percent, CreditCard } from "lucide-react";

import { Input } from "./ui/input";
import { Select } from "./ui/select-custom";
import { DatePicker } from "./ui/datepicker";
import { cn } from "../lib/utils";
import type { LieferantDokument, LieferantDokumentTyp } from "../types";

interface LieferantDokumentModalProps {
    isOpen: boolean;
    onClose: () => void;
    dokument: LieferantDokument | null;
    lieferantId: number | string;
    onSave?: (updated: LieferantDokument) => void;
}

const DOK_TYP_OPTIONS = [
    { value: "ANGEBOT", label: "Angebot" },
    { value: "AUFTRAGSBESTAETIGUNG", label: "Auftragsbestätigung" },
    { value: "LIEFERSCHEIN", label: "Lieferschein" },
    { value: "RECHNUNG", label: "Rechnung" },
    { value: "EINGANGSRECHNUNG", label: "Eingangsrechnung" },
    { value: "GUTSCHRIFT", label: "Gutschrift" },
];

export default function LieferantDokumentModal({
    isOpen,
    onClose,
    dokument,
    lieferantId,
    onSave
}: LieferantDokumentModalProps) {
    const [showPdf, setShowPdf] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null);

    // Form State
    const [formData, setFormData] = useState({
        typ: "RECHNUNG" as LieferantDokumentTyp,
        dokumentNummer: "",
        dokumentDatum: "",
        liefertermin: "",
        betragNetto: "",
        betragBrutto: "",
        mwstSatz: "19",
        referenzNummer: "",
        bestellnummer: "",
        // Rechnungsspezifische Felder
        zahlungsziel: "",
        skontoTage: "",
        skontoProzent: "",
        nettoTage: "",
    });

    // Reset form when document changes
    useEffect(() => {
        if (dokument) {
            setFormData({
                typ: dokument.typ,
                dokumentNummer: dokument.geschaeftsdaten?.dokumentNummer || "",
                dokumentDatum: dokument.geschaeftsdaten?.dokumentDatum || "",
                liefertermin: dokument.geschaeftsdaten?.liefertermin || "",
                betragNetto: dokument.geschaeftsdaten?.betragNetto?.toString() || "",
                betragBrutto: dokument.geschaeftsdaten?.betragBrutto?.toString() || "",
                mwstSatz: dokument.geschaeftsdaten?.mwstSatz?.toString() || "19",
                referenzNummer: dokument.geschaeftsdaten?.referenzNummer || "",
                bestellnummer: dokument.geschaeftsdaten?.bestellnummer || "",
                zahlungsziel: dokument.geschaeftsdaten?.zahlungsziel || "",
                skontoTage: dokument.geschaeftsdaten?.skontoTage?.toString() || "",
                skontoProzent: dokument.geschaeftsdaten?.skontoProzent?.toString() || "",
                nettoTage: dokument.geschaeftsdaten?.nettoTage?.toString() || "",
            });
            setError(null);
            setShowPdf(true);
        }
    }, [dokument]);

    // Lade PDF als Blob um X-Frame-Options externer URLs zu umgehen
    useEffect(() => {
        if (!isOpen || !dokument || !showPdf) {
            setPdfBlobUrl(null);
            return;
        }
        const controller = new AbortController();
        let currentUrl: string | null = null;

        fetch(`/api/lieferanten/${lieferantId}/dokumente/${dokument.id}/download`, { signal: controller.signal })
            .then(res => res.blob())
            .then(blob => {
                currentUrl = URL.createObjectURL(blob);
                setPdfBlobUrl(currentUrl);
            })
            .catch(() => { /* AbortError bei Cleanup ignorieren */ });

        return () => {
            controller.abort();
            if (currentUrl) URL.revokeObjectURL(currentUrl);
            setPdfBlobUrl(null);
        };
    }, [dokument?.id, dokument, lieferantId, isOpen, showPdf]);

    if (!isOpen || !dokument) return null;
    const confidence = dokument.geschaeftsdaten?.aiConfidence;

    // Typspezifische Feldanzeige
    const isRechnung = formData.typ === "RECHNUNG" || formData.typ === "EINGANGSRECHNUNG" || formData.typ === "GUTSCHRIFT";
    const isAB = formData.typ === "AUFTRAGSBESTAETIGUNG";
    const isLieferschein = formData.typ === "LIEFERSCHEIN";

    // Zeige Liefertermin für AB und Lieferschein
    const showLiefertermin = isAB || isLieferschein;
    // Zeige Beträge für alle außer Lieferschein
    const showBetraege = !isLieferschein;
    // Zeige Zahlungsbedingungen für Rechnungen und Gutschriften
    const showZahlungsbedingungen = isRechnung;

    // Auto-berechne Brutto aus Netto
    const handleNettoChange = (value: string) => {
        const newFormData = { ...formData, betragNetto: value };
        if (value && !formData.betragBrutto) {
            const netto = parseFloat(value.replace(",", "."));
            const mwst = parseFloat(formData.mwstSatz || "19") / 100;
            if (!isNaN(netto)) {
                newFormData.betragBrutto = (netto * (1 + mwst)).toFixed(2);
            }
        }
        setFormData(newFormData);
    };

    // Berechne Netto aus Brutto
    const handleBruttoChange = (value: string) => {
        const newFormData = { ...formData, betragBrutto: value };
        if (value && !formData.betragNetto) {
            const brutto = parseFloat(value.replace(",", "."));
            const mwst = parseFloat(formData.mwstSatz || "19") / 100;
            if (!isNaN(brutto)) {
                newFormData.betragNetto = (brutto / (1 + mwst)).toFixed(2);
            }
        }
        setFormData(newFormData);
    };

    const handleSave = async () => {
        setLoading(true);
        setError(null);
        try {
            let betragNettoVal = formData.betragNetto ? parseFloat(formData.betragNetto.replace(",", ".")) : null;
            let betragBruttoVal = formData.betragBrutto ? parseFloat(formData.betragBrutto.replace(",", ".")) : null;
            const mwst = parseFloat(formData.mwstSatz || "19") / 100;

            if (betragNettoVal && !betragBruttoVal) {
                betragBruttoVal = betragNettoVal * (1 + mwst);
            } else if (betragBruttoVal && !betragNettoVal) {
                betragNettoVal = betragBruttoVal / (1 + mwst);
            }

            const payload = {
                typ: formData.typ,
                geschaeftsdaten: {
                    dokumentNummer: formData.dokumentNummer || null,
                    dokumentDatum: formData.dokumentDatum || null,
                    liefertermin: formData.liefertermin || null,
                    betragNetto: betragNettoVal,
                    betragBrutto: betragBruttoVal,
                    mwstSatz: formData.mwstSatz ? parseFloat(formData.mwstSatz) / 100 : null,
                    referenzNummer: formData.referenzNummer || null,
                    bestellnummer: formData.bestellnummer || null,
                    // Rechnungsspezifische Felder
                    zahlungsziel: formData.zahlungsziel || null,
                    skontoTage: formData.skontoTage ? parseInt(formData.skontoTage) : null,
                    skontoProzent: formData.skontoProzent ? parseFloat(formData.skontoProzent) : null,
                    nettoTage: formData.nettoTage ? parseInt(formData.nettoTage) : null,
                }
            };

            const res = await fetch(`/api/lieferant-dokumente/${dokument.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                throw new Error("Speichern fehlgeschlagen");
            }

            const updated = await res.json();
            onSave?.(updated);
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Ein Fehler ist aufgetreten");
        } finally {
            setLoading(false);
        }
    };

    return (
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
                <div
                    className={cn(
                        "relative bg-white shadow-2xl rounded-2xl border border-slate-200 flex flex-col overflow-hidden",
                        showPdf
                            ? "w-[95vw] h-[90vh] max-w-[1600px]"
                            : "w-full max-w-xl"
                    )}
                    onClick={(e) => e.stopPropagation()}
                >
                    {/* Close Button */}
                    <button
                        className="absolute right-4 top-4 z-10 p-2 rounded-full bg-white/90 shadow-sm border border-slate-200 opacity-80 transition-all hover:opacity-100 hover:bg-rose-50 hover:text-rose-600 hover:border-rose-200"
                        onClick={onClose}
                    >
                        <X className="h-4 w-4" />
                    </button>

                    {/* Header */}
                    <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between shrink-0 bg-white">
                        <div className="flex items-center gap-4">
                            <h2 className="text-lg font-semibold text-slate-900">
                                Dokument bearbeiten
                            </h2>
                            {showPdf && (
                                <span className="text-sm text-slate-500">PDF-Vorschau</span>
                            )}
                        </div>
                        <button
                            onClick={() => setShowPdf(!showPdf)}
                            className={cn(
                                "flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium transition-all mr-10",
                                showPdf
                                    ? "bg-rose-100 text-rose-700 hover:bg-rose-200"
                                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                            )}
                        >
                            <Eye className="w-4 h-4" />
                            {showPdf ? "Vorschau aktiv" : "Vorschau anzeigen"}
                        </button>
                    </div>

                    {/* Content - Flex Row */}
                    <div className="flex flex-1 min-h-0 overflow-hidden">
                        {/* PDF Viewer - Links */}
                        {showPdf && (
                            <div className="flex-1 bg-slate-800 min-w-0 flex flex-col">
                                {pdfBlobUrl ? (
                                    <iframe
                                        src={pdfBlobUrl}
                                        className="w-full h-full border-0"
                                        title="PDF Vorschau"
                                    />
                                ) : (
                                    <div className="flex items-center justify-center h-full text-white text-sm">
                                        <div className="animate-spin w-5 h-5 border-2 border-white border-t-transparent rounded-full mr-3" />
                                        PDF wird geladen...
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Formular - Rechts */}
                        <div className={cn(
                            "flex flex-col bg-white",
                            showPdf ? "w-[420px] border-l border-slate-200 shrink-0" : "flex-1"
                        )}>
                            {/* Scrollbarer Bereich */}
                            <div className="flex-1 overflow-y-auto p-6 space-y-5">
                                {/* Datei-Info */}
                                <div className="flex items-start gap-3 p-4 bg-slate-50 rounded-xl border border-slate-100">
                                    <FileText className="w-10 h-10 text-rose-500 shrink-0" />
                                    <div className="flex-1 min-w-0">
                                        <p className="font-semibold text-slate-900 truncate">
                                            {dokument.originalDateiname}
                                        </p>
                                        <p className="text-sm text-slate-500">
                                            Hochgeladen: {new Date(dokument.uploadDatum).toLocaleDateString("de-DE")}
                                        </p>
                                    </div>
                                    {confidence !== undefined && (
                                        <span className={cn(
                                            "text-xs px-2.5 py-1 rounded-full font-semibold shrink-0",
                                            confidence >= 0.9 ? "bg-green-100 text-green-700" :
                                                confidence >= 0.7 ? "bg-yellow-100 text-yellow-700" :
                                                    "bg-red-100 text-red-700"
                                        )}>
                                            KI: {Math.round(confidence * 100)}%
                                        </span>
                                    )}
                                </div>

                                {/* Dokumenttyp + Nummer */}
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium text-slate-700">Dokumenttyp</label>
                                        <Select
                                            value={formData.typ}
                                            onChange={(val) => setFormData(prev => ({ ...prev, typ: val as LieferantDokumentTyp }))}
                                            options={DOK_TYP_OPTIONS}
                                        />
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium text-slate-700">Dokumentnummer</label>
                                        <div className="relative">
                                            <Hash className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                            <Input
                                                value={formData.dokumentNummer}
                                                onChange={(e) => setFormData(prev => ({ ...prev, dokumentNummer: e.target.value }))}
                                                className="pl-9"
                                                placeholder="RE-2024-001"
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* Dokumentdatum + Liefertermin */}
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium text-slate-700">Dokumentdatum</label>
                                        <DatePicker
                                            value={formData.dokumentDatum}
                                            onChange={(value) => setFormData(prev => ({ ...prev, dokumentDatum: value }))}
                                            placeholder="Datum wählen"
                                        />
                                    </div>
                                    {showLiefertermin && (
                                        <div className="space-y-2">
                                            <label className="text-sm font-medium text-slate-700 flex items-center gap-1">
                                                <Truck className="w-3.5 h-3.5" />
                                                Liefertermin
                                            </label>
                                            <DatePicker
                                                value={formData.liefertermin}
                                                onChange={(value) => setFormData(prev => ({ ...prev, liefertermin: value }))}
                                                placeholder="Lieferdatum"
                                            />
                                        </div>
                                    )}
                                </div>

                                {/* Beträge - Netto + Brutto + MwSt */}
                                {showBetraege && (
                                    <div className="space-y-3">
                                        <label className="text-sm font-medium text-slate-700">Beträge</label>
                                        <div className="grid grid-cols-3 gap-3">
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">Netto</label>
                                                <div className="relative">
                                                    <Euro className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                    <Input
                                                        type="number"
                                                        step="0.01"
                                                        value={formData.betragNetto}
                                                        onChange={(e) => handleNettoChange(e.target.value)}
                                                        className="pl-9"
                                                        placeholder="0.00"
                                                    />
                                                </div>
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">MwSt %</label>
                                                <div className="relative">
                                                    <Percent className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                    <Input
                                                        type="number"
                                                        step="0.1"
                                                        value={formData.mwstSatz}
                                                        onChange={(e) => setFormData(prev => ({ ...prev, mwstSatz: e.target.value }))}
                                                        className="pl-9"
                                                        placeholder="19"
                                                    />
                                                </div>
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-500">Brutto</label>
                                                <div className="relative">
                                                    <Euro className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                                    <Input
                                                        type="number"
                                                        step="0.01"
                                                        value={formData.betragBrutto}
                                                        onChange={(e) => handleBruttoChange(e.target.value)}
                                                        className="pl-9"
                                                        placeholder="0.00"
                                                    />
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* Referenz-/Bestellnummer */}
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium text-slate-700">Bestellnummer</label>
                                        <div className="relative">
                                            <Link2 className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                            <Input
                                                value={formData.bestellnummer}
                                                onChange={(e) => setFormData(prev => ({ ...prev, bestellnummer: e.target.value }))}
                                                className="pl-9"
                                                placeholder="z.B. 4500123456"
                                            />
                                        </div>
                                    </div>
                                    <div className="space-y-2">
                                        <label className="text-sm font-medium text-slate-700">Referenznummer</label>
                                        <div className="relative">
                                            <Link2 className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                                            <Input
                                                value={formData.referenzNummer}
                                                onChange={(e) => setFormData(prev => ({ ...prev, referenzNummer: e.target.value }))}
                                                className="pl-9"
                                                placeholder="z.B. AB-12345"
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* Zahlungsbedingungen (nur für Rechnungen) */}
                                {showZahlungsbedingungen && (
                                    <div className="space-y-4 p-4 bg-rose-50/50 rounded-xl border border-rose-100">
                                        <div className="flex items-center gap-2">
                                            <CreditCard className="w-4 h-4 text-rose-600" />
                                            <label className="text-sm font-semibold text-rose-700">Zahlungsbedingungen</label>
                                        </div>
                                        
                                        {/* Zahlungsziel */}
                                        <div className="space-y-2">
                                            <label className="text-xs text-slate-600">Zahlungsziel</label>
                                            <DatePicker
                                                value={formData.zahlungsziel}
                                                onChange={(value) => setFormData(prev => ({ ...prev, zahlungsziel: value }))}
                                                placeholder="Fälligkeitsdatum"
                                            />
                                        </div>

                                        {/* Skonto-Konditionen */}
                                        <div className="grid grid-cols-3 gap-3">
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-600">Skonto %</label>
                                                <Input
                                                    type="number"
                                                    step="0.1"
                                                    value={formData.skontoProzent}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, skontoProzent: e.target.value }))}
                                                    placeholder="z.B. 2"
                                                />
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-600">Skonto Tage</label>
                                                <Input
                                                    type="number"
                                                    value={formData.skontoTage}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, skontoTage: e.target.value }))}
                                                    placeholder="z.B. 14"
                                                />
                                            </div>
                                            <div className="space-y-1">
                                                <label className="text-xs text-slate-600">Netto Tage</label>
                                                <Input
                                                    type="number"
                                                    value={formData.nettoTage}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, nettoTage: e.target.value }))}
                                                    placeholder="z.B. 30"
                                                />
                                            </div>
                                        </div>
                                        <p className="text-xs text-slate-500">
                                            Beispiel: 2% Skonto bei Zahlung innerhalb 14 Tagen, netto 30 Tage
                                        </p>
                                    </div>
                                )}

                                {/* Warnung: Kein Projekt */}
                                {dokument.projektAnteile.length === 0 && (
                                    <div className="flex items-center justify-between p-3 bg-amber-50 rounded-lg border border-amber-200">
                                        <div className="flex items-center gap-2 text-sm text-amber-700">
                                            <AlertCircle className="w-4 h-4" />
                                            <span>Kein Projekt zugeordnet</span>
                                        </div>
                                        <Button variant="ghost" size="sm" className="text-rose-600 hover:text-rose-700 hover:bg-rose-50">
                                            <FolderPlus className="w-4 h-4 mr-1" />
                                            Zuordnen
                                        </Button>
                                    </div>
                                )}

                                {/* Verknüpfte Dokumente */}
                                {dokument.verknuepfteDokumente.length > 0 && (
                                    <div className="p-3 bg-slate-50 rounded-lg">
                                        <p className="text-sm font-medium text-slate-700 mb-1">Verknüpft mit:</p>
                                        <div className="flex flex-wrap gap-2">
                                            {dokument.verknuepfteDokumente.map(v => (
                                                <span key={v.id} className="text-sm px-2 py-0.5 bg-rose-100 text-rose-700 rounded">
                                                    {v.dokumentNummer || `#${v.id}`}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Error */}
                            {error && (
                                <div className="mx-6 mb-4 bg-red-50 text-red-600 p-3 rounded-lg flex items-center gap-2 text-sm border border-red-100">
                                    <AlertCircle className="h-4 w-4 shrink-0" />
                                    <span>{error}</span>
                                </div>
                            )}

                            {/* Footer */}
                            <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-3 shrink-0">
                                <Button variant="outline" onClick={onClose} disabled={loading}>
                                    Abbrechen
                                </Button>
                                <Button
                                    onClick={handleSave}
                                    disabled={loading}
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
                                            Speichern
                                        </>
                                    )}
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
