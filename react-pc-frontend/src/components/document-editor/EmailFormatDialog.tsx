import { FileText, Shield, X, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';

export type PdfFormat = 'pdf' | 'zugferd';

interface EmailFormatDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (format: PdfFormat) => void;
    loading: boolean;
    title?: string;
    description?: string;
    pdfLabel?: string;
    zugferdLabel?: string;
    loadingText?: string;
}

export function EmailFormatDialog({ isOpen, onClose, onSelect, loading, title, description, pdfLabel, zugferdLabel, loadingText }: EmailFormatDialogProps) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 overflow-hidden" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
                    <h3 className="text-base font-semibold text-slate-900">{title || 'Dokument per E-Mail senden'}</h3>
                    <button onClick={onClose} disabled={loading} className="text-slate-400 hover:text-slate-600 transition-colors">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                {/* Content */}
                <div className="p-5 space-y-3">
                    <p className="text-sm text-slate-500 mb-4">
                        {description || 'Wählen Sie das Format für den PDF-Anhang:'}
                    </p>

                    {/* Normal PDF */}
                    <button
                        onClick={() => onSelect('pdf')}
                        disabled={loading}
                        className="w-full flex items-start gap-4 p-4 rounded-lg border border-slate-200 hover:border-rose-300 hover:bg-rose-50/50 transition-all text-left group disabled:opacity-50 disabled:pointer-events-none"
                    >
                        <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center transition-colors">
                            <FileText className="w-5 h-5 text-slate-500 group-hover:text-rose-600 transition-colors" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="font-medium text-slate-900 text-sm">Standard PDF</div>
                            <div className="text-xs text-slate-500 mt-0.5">
                                {pdfLabel || 'PDF-Datei als E-Mail-Anhang versenden'}
                            </div>
                        </div>
                    </button>

                    {/* ZUGFeRD PDF */}
                    <button
                        onClick={() => onSelect('zugferd')}
                        disabled={loading}
                        className="w-full flex items-start gap-4 p-4 rounded-lg border border-slate-200 hover:border-rose-300 hover:bg-rose-50/50 transition-all text-left group disabled:opacity-50 disabled:pointer-events-none"
                    >
                        <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center transition-colors">
                            <Shield className="w-5 h-5 text-slate-500 group-hover:text-rose-600 transition-colors" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="font-medium text-slate-900 text-sm">ZUGFeRD PDF</div>
                            <div className="text-xs text-slate-500 mt-0.5">
                                {zugferdLabel || 'PDF mit maschinenlesbaren Rechnungsdaten (EN 16931)'}
                            </div>
                        </div>
                    </button>
                </div>

                {/* Footer */}
                <div className="flex justify-between items-center px-5 py-3 border-t border-slate-100 bg-slate-50/50">
                    {loading ? (
                        <div className="flex items-center gap-2 text-sm text-rose-600">
                            <Loader2 className="w-4 h-4 animate-spin" />
                            <span>{loadingText || 'PDF wird generiert…'}</span>
                        </div>
                    ) : (
                        <div />
                    )}
                    <Button variant="outline" size="sm" onClick={onClose} disabled={loading}>
                        Abbrechen
                    </Button>
                </div>
            </div>
        </div>
    );
}
