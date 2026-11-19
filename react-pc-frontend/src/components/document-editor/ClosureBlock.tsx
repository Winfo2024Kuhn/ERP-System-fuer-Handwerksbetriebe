import { FolderOpen, Layers, Percent } from 'lucide-react';
import { formatCurrency, normalisiereRabattProzent, rabattBetrag as berechneRabattBetrag } from './helpers';
import type { ClosureSummary } from './helpers';
import { berechneAbrechnungsstand, type AbschlagInfo } from './abschluss/abrechnungsstandRechnung';
import { Abrechnungsstand, type AbrechnungsPosition } from './abschluss/Abrechnungsstand';
import type { AbschlussZeileAnpassung, AbschlussZeilen, AbschlussZeileSchluessel } from './abschluss/abschlussZeilen';

interface ClosureBlockProps {
    summary: ClosureSummary;
    /** Dokumenttyp (ABSCHLAGSRECHNUNG, TEILRECHNUNG, SCHLUSSRECHNUNG etc.) */
    dokumentTyp?: string;
    /** Abschlagsbetrag netto (nur bei ABSCHLAGSRECHNUNG) */
    abschlagBetragNetto?: number | null;
    /** Wie der Abschlag eingegeben wurde (positionenJson.abschlagInfo), für die Unterzeile wie im PDF. */
    abschlagInfo?: AbschlagInfo | null;
    /** Bereits abgerechneter Betrag durch andere Rechnungen */
    bereitsAbgerechnetDurchAndere?: number | null;
    /** Detaillierte Positionen vorheriger Abrechnungen */
    abrechnungsPositionen?: AbrechnungsPosition[];
    /** Nettobetrag des Basisdokuments (AB/Anfrage) */
    basisdokumentBetragNetto?: number | null;
    /** Pauschalrabatt auf das gesamte Dokument in Prozent */
    globalRabatt?: number | null;
    /** Hausfarbe aus den Firmeninformationen als Hex. Leer = Standardfarbe. */
    firmenfarbe?: string | null;
    /**
     * Namen der entfallenen bzw. zusaetzlichen Leistungen als Unterzeile der
     * Differenzzeile. Leer = nur der Betrag.
     */
    differenzHinweis?: string | null;
    /**
     * Einfache Rechnung, die den Auftrag abschliesst (schliesstEinfacheRechnungAuftragAb):
     * zeigt den Abrechnungsstand wie eine Schlussrechnung, genau wie das PDF.
     */
    schliesstAuftragAb?: boolean;
    /** Fortschrittsbalken und Prozentangabe zeigen. Standard: ja. */
    balkenAnzeigen?: boolean;
    /**
     * Umschalter fuer den Fortschrittsbalken. Nur gesetzt, wenn das Dokument
     * bearbeitbar ist — bei gesperrten Rechnungen erscheint kein Bedienelement.
     */
    onBalkenAnzeigenChange?: (anzeigen: boolean) => void;
    /** Eigene Texte bzw. ausgeblendete Zeilen im Abrechnungsstand. */
    abschlussZeilen?: AbschlussZeilen;
    /** Nur bei bearbeitbarer Rechnung gesetzt, sonst keine Knoepfe. */
    onAbschlussZeileAendern?: (schluessel: AbschlussZeileSchluessel, neu: AbschlussZeileAnpassung | undefined) => void;
}

export function ClosureBlock({ summary, dokumentTyp, abschlagBetragNetto, abschlagInfo, bereitsAbgerechnetDurchAndere, abrechnungsPositionen, basisdokumentBetragNetto, globalRabatt, firmenfarbe, differenzHinweis, schliesstAuftragAb, balkenAnzeigen = true, onBalkenAnzeigenChange, abschlussZeilen, onAbschlussZeileAendern }: ClosureBlockProps) {
    // Pauschalrabatt: `summary.gesamtNetto` enthaelt nur die Positions-Rabatte.
    // Der Abschluss muss den Betrag zeigen, der auch berechnet und versendet wird.
    const rabattProzent = normalisiereRabattProzent(globalRabatt);
    const hasRabatt = rabattProzent > 0 && summary.gesamtNetto > 0;
    // Zentrale Rundung: der Abschluss muss denselben Betrag zeigen, der gespeichert
    // und im PDF ausgewiesen wird — sonst weicht er um einen Cent ab.
    const rabattBetrag = hasRabatt ? berechneRabattBetrag(summary.gesamtNetto, rabattProzent) : 0;
    const gesamtNettoEffektiv = summary.gesamtNetto - rabattBetrag;
    const showBreakdown = summary.sections.length > 0;
    const stand = berechneAbrechnungsstand({
        dokumentTyp,
        positionenNetto: summary.gesamtNetto,
        gesamtNettoEffektiv,
        abschlagBetragNetto,
        bereitsAbgerechnetDurchAndere,
        basisdokumentBetragNetto,
        schliesstAuftragAb,
    });
    const summenZeilen = (
        <Gesamtsumme
            hasRabatt={hasRabatt}
            gesamtNetto={summary.gesamtNetto}
            rabattProzent={rabattProzent}
            rabattBetrag={rabattBetrag}
            gesamtNettoEffektiv={gesamtNettoEffektiv}
        />
    );

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
                    <div className="pt-1.5 space-y-1.5">
                        {summenZeilen}
                    </div>
                </div>
            )}

            {/* No sections: show only grand total if there are services */}
            {!showBreakdown && summary.gesamtNetto > 0 && (
                <div className="px-4 pb-4 space-y-1.5">
                    {summenZeilen}
                </div>
            )}

            {/* No services at all */}
            {!showBreakdown && summary.gesamtNetto <= 0 && (
                <div className="px-4 pb-4">
                    <p className="text-xs text-slate-400 italic">Keine Leistungen vorhanden</p>
                </div>
            )}

            {stand.anzeigen && (
                <Abrechnungsstand
                    stand={stand}
                    abrechnungsPositionen={abrechnungsPositionen}
                    firmenfarbe={firmenfarbe}
                    differenzHinweis={differenzHinweis}
                    abschlagInfo={abschlagInfo}
                    balkenAnzeigen={balkenAnzeigen}
                    onBalkenAnzeigenChange={onBalkenAnzeigenChange}
                    abschlussZeilen={abschlussZeilen}
                    onAbschlussZeileAendern={onAbschlussZeileAendern}
                />
            )}
        </div>
    );
}

interface GesamtsummeProps {
    hasRabatt: boolean;
    gesamtNetto: number;
    rabattProzent: number;
    rabattBetrag: number;
    gesamtNettoEffektiv: number;
}

/** Zwischensumme und Rabatt (falls vorhanden) und die Gesamtsumme netto. */
function Gesamtsumme({ hasRabatt, gesamtNetto, rabattProzent, rabattBetrag, gesamtNettoEffektiv }: GesamtsummeProps) {
    return (
        <>
            {hasRabatt && (
                <>
                    <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                        <span className="text-xs font-medium text-slate-600">Zwischensumme Netto</span>
                        <span className="text-sm font-semibold text-slate-700">
                            {formatCurrency(gesamtNetto)} €
                        </span>
                    </div>
                    <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-rose-100">
                        <div className="flex items-center gap-2.5">
                            <div className="w-7 h-7 bg-rose-100 rounded-md flex items-center justify-center flex-shrink-0">
                                <Percent className="w-3.5 h-3.5 text-rose-600" />
                            </div>
                            <span className="text-xs font-medium text-slate-600">
                                Rabatt {formatCurrency(rabattProzent)} %
                            </span>
                        </div>
                        <span className="text-sm font-bold text-rose-600">
                            − {formatCurrency(rabattBetrag)} €
                        </span>
                    </div>
                </>
            )}
            <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">
                    {hasRabatt ? 'Gesamtsumme Netto nach Rabatt' : 'Gesamtsumme Netto'}
                </span>
                <span className="text-base font-bold text-slate-900">
                    {formatCurrency(gesamtNettoEffektiv)} €
                </span>
            </div>
        </>
    );
}
