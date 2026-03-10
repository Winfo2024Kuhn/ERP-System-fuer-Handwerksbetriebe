import { FolderOpen, Layers, Receipt, FileText } from 'lucide-react';
import { formatCurrency } from './helpers';
import type { ClosureSummary } from './helpers';

interface AbrechnungsPosition {
    dokumentNummer: string;
    typ: string;
    datum: string;
    betragNetto: number;
    abschlagsNummer?: number;
}

const TYP_LABELS: Record<string, string> = {
    'ABSCHLAGSRECHNUNG': 'Abschlagsrechnung',
    'TEILRECHNUNG': 'Teilrechnung',
    'SCHLUSSRECHNUNG': 'Schlussrechnung',
    'RECHNUNG': 'Rechnung',
};

interface ClosureBlockProps {
    summary: ClosureSummary;
    /** Dokumenttyp (ABSCHLAGSRECHNUNG, TEILRECHNUNG, SCHLUSSRECHNUNG etc.) */
    dokumentTyp?: string;
    /** Abschlagsbetrag netto (nur bei ABSCHLAGSRECHNUNG) */
    abschlagBetragNetto?: number | null;
    /** Bereits abgerechneter Betrag durch andere Rechnungen */
    bereitsAbgerechnetDurchAndere?: number | null;
    /** Detaillierte Positionen vorheriger Abrechnungen */
    abrechnungsPositionen?: AbrechnungsPosition[];
    /** Nettobetrag des Basisdokuments (AB/Angebot) */
    basisdokumentBetragNetto?: number | null;
}

export function ClosureBlock({ summary, dokumentTyp, abschlagBetragNetto, bereitsAbgerechnetDurchAndere, abrechnungsPositionen, basisdokumentBetragNetto }: ClosureBlockProps) {
    const hasSections = summary.sections.length > 0;
    const showBreakdown = hasSections;
    const isAbschlag = dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto != null;
    const isTeilrechnung = dokumentTyp === 'TEILRECHNUNG';
    const isSchlussrechnung = dokumentTyp === 'SCHLUSSRECHNUNG';
    const showAbschlagInfo = isAbschlag || isTeilrechnung || isSchlussrechnung;

    return (
        <div className="bg-slate-50 rounded-xl border border-slate-200 overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between p-4 pb-2">
                <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-slate-200 rounded-lg flex items-center justify-center">
                        <span className="text-lg font-bold text-slate-600">∑</span>
                    </div>
                    <div>
                        <h4 className="text-sm font-semibold text-slate-800">Abschluss</h4>
                        <p className="text-[11px] text-slate-400 mt-0.5">Übersicht aller Bauabschnitte und Leistungen</p>
                    </div>
                </div>
            </div>

            {/* Breakdown */}
            {showBreakdown && (
                <div className="px-4 pb-4 space-y-1.5">
                    {/* Section summaries */}
                    {summary.sections.map((sec, i) => (
                        <div
                            key={i}
                            className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100"
                        >
                            <div className="flex items-center gap-2.5 min-w-0">
                                <div className="w-7 h-7 bg-slate-800 rounded-md flex items-center justify-center flex-shrink-0">
                                    <FolderOpen className="w-3.5 h-3.5 text-white" />
                                </div>
                                <div className="min-w-0">
                                    <span className="text-[9px] font-semibold text-slate-400 uppercase tracking-wider">
                                        Bauabschnitt {sec.position}
                                    </span>
                                    <p className="text-xs font-semibold text-slate-700 truncate">
                                        {sec.label}
                                    </p>
                                </div>
                            </div>
                            <span className="text-sm font-bold text-slate-900 flex-shrink-0 ml-3">
                                {formatCurrency(sec.total)} €
                            </span>
                        </div>
                    ))}

                    {/* Sonstige Leistungen */}
                    {summary.hasSonstige && (
                        <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                            <div className="flex items-center gap-2.5 min-w-0">
                                <div className="w-7 h-7 bg-slate-500 rounded-md flex items-center justify-center flex-shrink-0">
                                    <Layers className="w-3.5 h-3.5 text-white" />
                                </div>
                                <div className="min-w-0">
                                    <span className="text-[9px] font-semibold text-slate-400 uppercase tracking-wider">
                                        Sonstige
                                    </span>
                                    <p className="text-xs font-semibold text-slate-700 truncate">
                                        Sonstige Leistungen
                                    </p>
                                </div>
                            </div>
                            <span className="text-sm font-bold text-slate-900 flex-shrink-0 ml-3">
                                {formatCurrency(summary.sonstigeTotal)} €
                            </span>
                        </div>
                    )}

                    {/* Divider + Grand total */}
                    <div className="pt-1.5">
                        <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                            <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                                Gesamtsumme Netto
                            </span>
                            <span className="text-base font-bold text-slate-900">
                                {formatCurrency(summary.gesamtNetto)} €
                            </span>
                        </div>
                    </div>
                </div>
            )}

            {/* No sections: show only grand total if there are services */}
            {!showBreakdown && summary.gesamtNetto > 0 && (
                <div className="px-4 pb-4">
                    <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                        <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                            Gesamtsumme Netto
                        </span>
                        <span className="text-base font-bold text-slate-900">
                            {formatCurrency(summary.gesamtNetto)} €
                        </span>
                    </div>
                </div>
            )}

            {/* No services at all */}
            {!showBreakdown && summary.gesamtNetto <= 0 && (
                <div className="px-4 pb-4">
                    <p className="text-xs text-slate-400 italic">Keine Leistungen vorhanden</p>
                </div>
            )}

            {/* Abschlag / Teilrechnung / Schlussrechnung Info */}
            {showAbschlagInfo && summary.gesamtNetto > 0 && (
                <div className="px-4 pb-4 space-y-1.5">
                    <div className="border-t border-slate-200 pt-3 space-y-1.5">
                        {/* Gesamtauftragssumme (Basisdokument) */}
                        <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                            <div className="flex items-center gap-2.5">
                                <div className="w-7 h-7 bg-slate-200 rounded-md flex items-center justify-center flex-shrink-0">
                                    <FileText className="w-3.5 h-3.5 text-slate-500" />
                                </div>
                                <span className="text-xs font-medium text-slate-600">Gesamtauftragssumme (Netto)</span>
                            </div>
                            <span className="text-sm font-bold text-slate-900">
                                {formatCurrency(basisdokumentBetragNetto != null ? basisdokumentBetragNetto : summary.gesamtNetto)} €
                            </span>
                        </div>

                        {/* Bereits abgerechnet (wenn vorhanden) – einzelne Positionen */}
                        {bereitsAbgerechnetDurchAndere != null && bereitsAbgerechnetDurchAndere > 0 && (
                            <>
                                {abrechnungsPositionen && abrechnungsPositionen.length > 0 ? (
                                    // Detaillierte Auflistung jeder vorherigen Rechnung
                                    abrechnungsPositionen.map((pos, idx) => (
                                        <div key={idx} className="flex items-center justify-between px-3 py-2 bg-amber-50 rounded-lg border border-amber-100">
                                            <div className="flex items-center gap-2.5">
                                                <div className="w-7 h-7 bg-amber-100 rounded-md flex items-center justify-center flex-shrink-0">
                                                    <Receipt className="w-3.5 h-3.5 text-amber-600" />
                                                </div>
                                                <div className="min-w-0">
                                                    <span className="text-[9px] font-semibold text-amber-500 uppercase tracking-wider">
                                                        {TYP_LABELS[pos.typ] || pos.typ}
                                                        {pos.abschlagsNummer ? ` #${pos.abschlagsNummer}` : ''}
                                                    </span>
                                                    <p className="text-xs font-medium text-slate-600 truncate">
                                                        {pos.dokumentNummer}
                                                        {pos.datum && (
                                                            <span className="text-slate-400 ml-1.5">
                                                                vom {new Date(pos.datum).toLocaleDateString('de-DE')}
                                                            </span>
                                                        )}
                                                    </p>
                                                </div>
                                            </div>
                                            <span className="text-sm font-bold text-amber-700 flex-shrink-0 ml-3">
                                                − {formatCurrency(pos.betragNetto)} €
                                            </span>
                                        </div>
                                    ))
                                ) : (
                                    // Fallback: nur Gesamtbetrag anzeigen
                                    <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                                        <div className="flex items-center gap-2.5">
                                            <div className="w-7 h-7 bg-amber-100 rounded-md flex items-center justify-center flex-shrink-0">
                                                <Receipt className="w-3.5 h-3.5 text-amber-600" />
                                            </div>
                                            <span className="text-xs font-medium text-slate-600">Bereits abgerechnet</span>
                                        </div>
                                        <span className="text-sm font-bold text-amber-700">
                                            − {formatCurrency(bereitsAbgerechnetDurchAndere)} €
                                        </span>
                                    </div>
                                )}
                            </>
                        )}

                        {/* Teilrechnung: Nettobetrag dieser Teilrechnung */}
                        {isTeilrechnung && (
                            <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                                <div className="flex items-center gap-2.5">
                                    <div className="w-7 h-7 bg-rose-200 rounded-md flex items-center justify-center flex-shrink-0">
                                        <Receipt className="w-3.5 h-3.5 text-rose-700" />
                                    </div>
                                    <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                                        Netto dieser Teilrechnung
                                    </span>
                                </div>
                                <span className="text-base font-bold text-slate-900">
                                    {formatCurrency(summary.gesamtNetto)} €
                                </span>
                            </div>
                        )}

                        {/* Abschlagsbetrag mit Prozent */}
                        {isAbschlag && abschlagBetragNetto != null && (
                            <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                                <div className="flex items-center gap-2.5">
                                    <div className="w-7 h-7 bg-rose-200 rounded-md flex items-center justify-center flex-shrink-0">
                                        <Receipt className="w-3.5 h-3.5 text-rose-700" />
                                    </div>
                                    <div>
                                        <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                                            Abschlagsbetrag (Netto)
                                        </span>
                                        {(basisdokumentBetragNetto ?? summary.gesamtNetto) > 0 && (
                                            <p className="text-[10px] text-rose-500">
                                                {((abschlagBetragNetto / (basisdokumentBetragNetto ?? summary.gesamtNetto)) * 100).toFixed(1)} % der Auftragssumme
                                            </p>
                                        )}
                                    </div>
                                </div>
                                <span className="text-base font-bold text-slate-900">
                                    {formatCurrency(abschlagBetragNetto)} €
                                </span>
                            </div>
                        )}

                        {/* Schlussrechnung: Restbetrag */}
                        {isSchlussrechnung && bereitsAbgerechnetDurchAndere != null && bereitsAbgerechnetDurchAndere > 0 && (
                            <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                                <div className="flex items-center gap-2.5">
                                    <div className="w-7 h-7 bg-rose-200 rounded-md flex items-center justify-center flex-shrink-0">
                                        <Receipt className="w-3.5 h-3.5 text-rose-700" />
                                    </div>
                                    <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                                        Restbetrag (Netto)
                                    </span>
                                </div>
                                <span className="text-base font-bold text-slate-900">
                                    {formatCurrency(Math.max(0, (basisdokumentBetragNetto ?? summary.gesamtNetto) - bereitsAbgerechnetDurchAndere))} €
                                </span>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
