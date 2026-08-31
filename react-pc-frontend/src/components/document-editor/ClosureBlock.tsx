import { FolderOpen, Layers, Receipt, FileText, Percent } from 'lucide-react';
import { formatCurrency, normalisiereRabattProzent, rabattBetrag as berechneRabattBetrag } from './helpers';
import type { ClosureSummary } from './helpers';

interface AbrechnungsPosition {
    dokumentNummer: string;
    typ: string;
    datum: string;
    betragNetto: number;
    abschlagsNummer?: number;
}

/** Firmenfarbe, wenn in den Firmeninformationen keine hinterlegt ist. */
const FIRMENFARBE_STANDARD = '#500010';

/** Mischt eine Hex-Farbe mit Weiss — 0 bleibt die Farbe, 1 ist reines Weiss. */
function aufhellen(hex: string, anteil: number): string {
    const wert = parseInt(hex.slice(1), 16);
    const misch = (kanal: number) => Math.round(kanal + (255 - kanal) * anteil);
    const r = misch((wert >> 16) & 0xff);
    const g = misch((wert >> 8) & 0xff);
    const b = misch(wert & 0xff);
    return `#${((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1)}`;
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
    /** Nettobetrag des Basisdokuments (AB/Anfrage) */
    basisdokumentBetragNetto?: number | null;
    /** Pauschalrabatt auf das gesamte Dokument in Prozent */
    globalRabatt?: number | null;
    /** Hausfarbe aus den Firmeninformationen als Hex. Leer = Standardfarbe. */
    firmenfarbe?: string | null;
}

export function ClosureBlock({ summary, dokumentTyp, abschlagBetragNetto, bereitsAbgerechnetDurchAndere, abrechnungsPositionen, basisdokumentBetragNetto, globalRabatt, firmenfarbe }: ClosureBlockProps) {
    const FIRMENFARBE = /^#[0-9a-fA-F]{6}$/.test(firmenfarbe || '') ? firmenfarbe as string : FIRMENFARBE_STANDARD;
    const FIRMENFARBE_HELL = aufhellen(FIRMENFARBE, 0.88);
    // Pauschalrabatt: `summary.gesamtNetto` enthaelt nur die Positions-Rabatte.
    // Der Abschluss muss den Betrag zeigen, der auch berechnet und versendet wird.
    const rabattProzent = normalisiereRabattProzent(globalRabatt);
    const hasRabatt = rabattProzent > 0 && summary.gesamtNetto > 0;
    // Zentrale Rundung: der Abschluss muss denselben Betrag zeigen, der gespeichert
    // und im PDF ausgewiesen wird — sonst weicht er um einen Cent ab.
    const rabattBetrag = hasRabatt ? berechneRabattBetrag(summary.gesamtNetto, rabattProzent) : 0;
    const gesamtNettoEffektiv = summary.gesamtNetto - rabattBetrag;
    const hasSections = summary.sections.length > 0;
    const showBreakdown = hasSections;
    const isAbschlag = dokumentTyp === 'ABSCHLAGSRECHNUNG' && abschlagBetragNetto != null;
    const isTeilrechnung = dokumentTyp === 'TEILRECHNUNG';
    const isSchlussrechnung = dokumentTyp === 'SCHLUSSRECHNUNG';
    const showAbschlagInfo = isAbschlag || isTeilrechnung || isSchlussrechnung;

    // --- Abrechnungsstand ---
    // Der Kunde rechnet in Bruttobetraegen, deshalb steht brutto gross und netto klein
    // darunter. Der Steuersatz ist wie im PDF fest 19 %.
    // In Cent rechnen, nicht mit 0,19 multiplizieren: 42,50 * 0,19 ergibt in
    // Gleitkomma 8,074999... und rundet auf 8,07, waehrend das PDF mit BigDecimal
    // HALF_UP auf 8,08 kommt. Bei 200.000 Betraegen weichen so 76 voneinander ab.
    // Math.round rundet negative Halbe zur Null hin, BigDecimal HALF_UP von ihr weg —
    // deshalb ueber den Betrag runden und das Vorzeichen wieder dranhaengen.
    const steuerBetrag = (netto: number) => {
        const steuerCent = Math.round(netto * 100) * 19 / 100;
        return (Math.sign(steuerCent) * Math.round(Math.abs(steuerCent))) / 100;
    };
    const mitSteuer = (netto: number) => netto + steuerBetrag(netto);

    const auftragNetto = basisdokumentBetragNetto ?? gesamtNettoEffektiv;
    const auftragBrutto = mitSteuer(auftragNetto);
    const bereitsAbgerechnet = bereitsAbgerechnetDurchAndere ?? 0;

    // Der eingegebene Abschlagsbetrag ist bereits der Endbetrag — deshalb schickt der
    // Editor bei einer Abschlagsrechnung gar keinen globalRabattProzent ans PDF
    // (s. buildPdfPayload). Hier also ebenfalls kein Rabatt, sonst zeigt die Vorschau
    // weniger an als das fertige Dokument. Bei der Schlussrechnung ergibt sich der
    // Betrag aus dem offenen Rest, dort bleibt der Rabatt ebenfalls aussen vor.
    // Nicht auf 0 begrenzen: ist ein Auftrag ueberzahlt, soll die Vorschau dasselbe
    // Minus zeigen wie das fertige Dokument.
    const dieseNetto = isAbschlag
        ? (abschlagBetragNetto ?? 0)
        : isSchlussrechnung
            ? auftragNetto - bereitsAbgerechnet
            : gesamtNettoEffektiv;
    const dieseSteuer = steuerBetrag(dieseNetto);
    const dieseBrutto = dieseNetto + dieseSteuer;

    const dieseRechnungLabel = isAbschlag
        ? 'Diese Abschlagsrechnung'
        : isSchlussrechnung
            ? 'Diese Schlussrechnung'
            : 'Diese Teilrechnung';

    // Brutto aus dem Nettorest, nicht als Differenz der Bruttozeilen: sonst bleiben bei
    // krummen Betraegen Rundungsreste stehen und eine restlos abgerechnete
    // Schlussrechnung meldet "Noch offen 0,01 €".
    const restNetto = auftragNetto - bereitsAbgerechnet - dieseNetto;
    const restBrutto = mitSteuer(restNetto);

    const abgerechnetProzent = auftragNetto > 0
        ? Math.min(100, Math.max(0, ((bereitsAbgerechnet + dieseNetto) / auftragNetto) * 100))
        : 0;

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
                        {hasRabatt && (
                            <>
                                <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                                    <span className="text-xs font-medium text-slate-600">Zwischensumme Netto</span>
                                    <span className="text-sm font-semibold text-slate-700">
                                        {formatCurrency(summary.gesamtNetto)} €
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
                    </div>
                </div>
            )}

            {/* No sections: show only grand total if there are services */}
            {!showBreakdown && summary.gesamtNetto > 0 && (
                <div className="px-4 pb-4 space-y-1.5">
                        {hasRabatt && (
                            <>
                                <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                                    <span className="text-xs font-medium text-slate-600">Zwischensumme Netto</span>
                                    <span className="text-sm font-semibold text-slate-700">
                                        {formatCurrency(summary.gesamtNetto)} €
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
                </div>
            )}

            {/* No services at all */}
            {!showBreakdown && summary.gesamtNetto <= 0 && (
                <div className="px-4 pb-4">
                    <p className="text-xs text-slate-400 italic">Keine Leistungen vorhanden</p>
                </div>
            )}

            {/* Abrechnungsstand: Auftragssumme, bereits gestellte Rechnungen, offener Rest */}
            {showAbschlagInfo && summary.gesamtNetto > 0 && (
                <div className="px-4 pb-4">
                    <div className="border-t border-slate-200 pt-3 space-y-1.5">
                        {/* Fortschrittsbalken */}
                        <div className="px-1 pb-1">
                            <div className="h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: FIRMENFARBE_HELL }}>
                                <div
                                    className="h-full rounded-full"
                                    style={{ width: `${abgerechnetProzent}%`, backgroundColor: FIRMENFARBE }}
                                />
                            </div>
                            <p className="text-[10px] text-slate-500 mt-1.5">
                                Mit dieser Rechnung sind {abgerechnetProzent.toFixed(0)} % des Auftrags abgerechnet
                            </p>
                        </div>

                        {/* Auftragssumme */}
                        <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                            <div className="flex items-center gap-2.5">
                                <div className="w-7 h-7 bg-slate-200 rounded-md flex items-center justify-center flex-shrink-0">
                                    <FileText className="w-3.5 h-3.5 text-slate-500" />
                                </div>
                                <span className="text-xs font-medium text-slate-600">Auftragssumme</span>
                            </div>
                            <span className="text-sm font-bold text-slate-900 text-right">
                                {formatCurrency(auftragBrutto)} €
                                <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                    netto {formatCurrency(auftragNetto)} €
                                </span>
                            </span>
                        </div>

                        {/* Bereits gestellte Rechnungen, einzeln */}
                        {abrechnungsPositionen && abrechnungsPositionen.length > 0 ? (
                            abrechnungsPositionen.map((pos, idx) => (
                                <div key={idx} className="flex items-center justify-between px-3 py-2 bg-amber-50 rounded-lg border border-amber-100">
                                    <div className="flex items-center gap-2.5 min-w-0">
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
                                    <span className="text-sm font-bold text-amber-700 flex-shrink-0 ml-3 text-right">
                                        − {formatCurrency(mitSteuer(pos.betragNetto))} €
                                        <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                            netto {formatCurrency(pos.betragNetto)} €
                                        </span>
                                    </span>
                                </div>
                            ))
                        ) : bereitsAbgerechnet > 0 && (
                            // Fallback, wenn die Einzelpositionen (noch) nicht geladen sind
                            <div className="flex items-center justify-between px-3 py-2 bg-amber-50 rounded-lg border border-amber-100">
                                <div className="flex items-center gap-2.5">
                                    <div className="w-7 h-7 bg-amber-100 rounded-md flex items-center justify-center flex-shrink-0">
                                        <Receipt className="w-3.5 h-3.5 text-amber-600" />
                                    </div>
                                    <span className="text-xs font-medium text-slate-600">Bereits abgerechnet</span>
                                </div>
                                <span className="text-sm font-bold text-amber-700 text-right">
                                    − {formatCurrency(mitSteuer(bereitsAbgerechnet))} €
                                    <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                        netto {formatCurrency(bereitsAbgerechnet)} €
                                    </span>
                                </span>
                            </div>
                        )}

                        {/* Diese Rechnung */}
                        <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-100">
                            <div className="flex items-center gap-2.5">
                                <div className="w-7 h-7 bg-slate-200 rounded-md flex items-center justify-center flex-shrink-0">
                                    <Receipt className="w-3.5 h-3.5 text-slate-500" />
                                </div>
                                <div>
                                    <span className="text-xs font-medium text-slate-600">{dieseRechnungLabel}</span>
                                    {isAbschlag && auftragNetto > 0 && (
                                        <p className="text-[10px] text-slate-400">
                                            ca. {((dieseNetto / auftragNetto) * 100).toFixed(1)} % der Auftragssumme
                                        </p>
                                    )}
                                </div>
                            </div>
                            <span className="text-sm font-bold text-slate-900 text-right">
                                − {formatCurrency(dieseBrutto)} €
                                <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                    netto {formatCurrency(dieseNetto)} €
                                </span>
                            </span>
                        </div>

                        {/* Noch offen */}
                        <div className="flex items-center justify-between px-3 py-2 bg-white rounded-lg border border-slate-200">
                            <span className="text-xs font-semibold text-slate-700">Noch offen nach dieser Rechnung</span>
                            <span className="text-sm font-bold text-slate-900 text-right">
                                {formatCurrency(restBrutto)} €
                                <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                    netto {formatCurrency(restNetto)} €
                                </span>
                            </span>
                        </div>

                        {/* Zahlbetrag */}
                        <div className="flex items-center justify-between px-3 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                            <div className="flex items-center gap-2.5">
                                <div className="w-7 h-7 bg-rose-200 rounded-md flex items-center justify-center flex-shrink-0">
                                    <Receipt className="w-3.5 h-3.5 text-rose-700" />
                                </div>
                                <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">Zahlbetrag</span>
                            </div>
                            <span className="text-base font-bold text-slate-900 text-right">
                                {formatCurrency(dieseBrutto)} €
                                <span className="block text-[10px] font-medium" style={{ color: FIRMENFARBE }}>
                                    netto {formatCurrency(dieseNetto)} € + {formatCurrency(dieseSteuer)} € USt
                                </span>
                            </span>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
