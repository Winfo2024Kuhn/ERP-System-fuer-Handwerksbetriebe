import { useId } from 'react';
import { FileText, MinusCircle, PlusCircle, Receipt } from 'lucide-react';
import { InfoButton } from '../../ui/info-button';
import { formatCurrency } from '../helpers';
import { abschlagUnterzeile, mitSteuer, type AbschlagInfo, type Abrechnungsstand as Stand } from './abrechnungsstandRechnung';
import { AbschlussZeile } from './AbschlussZeile';
import type { AbschlussZeileAnpassung, AbschlussZeilen, AbschlussZeileSchluessel } from './abschlussZeilen';

export interface AbrechnungsPosition {
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
    ABSCHLAGSRECHNUNG: 'Abschlagsrechnung',
    TEILRECHNUNG: 'Teilrechnung',
    SCHLUSSRECHNUNG: 'Schlussrechnung',
    RECHNUNG: 'Rechnung',
};

const ZEILE = 'flex items-center justify-between px-3 py-2 rounded-lg border';
const SYMBOL = 'w-7 h-7 rounded-md flex items-center justify-center flex-shrink-0';

interface AbrechnungsstandProps {
    stand: Stand;
    abrechnungsPositionen?: AbrechnungsPosition[];
    firmenfarbe?: string | null;
    differenzHinweis?: string | null;
    /** Wie der Abschlag eingegeben wurde (Prozent/Brutto), für die Unterzeile wie im PDF. */
    abschlagInfo?: AbschlagInfo | null;
    balkenAnzeigen: boolean;
    onBalkenAnzeigenChange?: (anzeigen: boolean) => void;
    abschlussZeilen?: AbschlussZeilen;
    /** Nur bei bearbeitbarer Rechnung gesetzt. */
    onAbschlussZeileAendern?: (schluessel: AbschlussZeileSchluessel, neu: AbschlussZeileAnpassung | undefined) => void;
}

/** Brutto groß, netto klein in Firmenfarbe darunter — wie im PDF. */
function Betrag({ brutto, netto, farbe, className = 'text-sm font-bold text-slate-900' }: { brutto: string; netto: string; farbe: string; className?: string }) {
    return (
        <span className={`${className} text-right tabular-nums block`}>
            {brutto}
            <span className="block text-[10px] font-medium" style={{ color: farbe }}>{netto}</span>
        </span>
    );
}

/** Abrechnungsstand: Auftragssumme, gestellte Rechnungen, diese Rechnung, Differenz, Rest, Zahlbetrag. */
export function Abrechnungsstand({ stand, abrechnungsPositionen, firmenfarbe, differenzHinweis, abschlagInfo, balkenAnzeigen, onBalkenAnzeigenChange, abschlussZeilen, onAbschlussZeileAendern }: AbrechnungsstandProps) {
    // Eigene ID pro Instanz: zwei Abschlüsse dürfen sich label/for nicht kapern.
    const balkenCheckboxId = useId();
    const farbe = /^#[0-9a-fA-F]{6}$/.test(firmenfarbe || '') ? firmenfarbe as string : FIRMENFARBE_STANDARD;
    const aendern = (schluessel: AbschlussZeileSchluessel) => onAbschlussZeileAendern
        ? (neu: AbschlussZeileAnpassung | undefined) => onAbschlussZeileAendern(schluessel, neu)
        : undefined;

    return (
        <div className="px-4 pb-4">
            <div className="border-t border-slate-200 pt-3 space-y-1.5">
                <div className="flex items-center gap-1 px-1">
                    <h5 className="text-xs font-semibold text-slate-700">Abrechnungsstand</h5>
                    <InfoButton titel="Abrechnungsstand">
                        <p><strong>Was passiert im Hintergrund?</strong> Das Programm vergleicht diese Rechnung mit dem Angebot bzw. der Auftragsbestätigung und rechnet die Beträge selbst aus. Weicht die Rechnung ab, erklärt die Zeile „Zusätzliche Leistungen“ oder „Nicht angefallen“ dem Kunden den Unterschied.</p>
                        <p className="mt-2"><strong>Was bewirkt das?</strong> Mit dem Stift ändern Sie nur die Beschriftung auf der Rechnung. Mit dem durchgestrichenen Auge verschwindet die Zeile von der Rechnung. Zahlbetrag, Steuer und offene Posten ändern sich dabei nicht. Mit „Wieder einblenden“ oder Strg+Z holen Sie die Zeile zurück.</p>
                    </InfoButton>
                </div>

                {/* Fortschrittsbalken — pro Rechnung abschaltbar */}
                <div className="px-1 pb-1">
                    {balkenAnzeigen && (
                        <>
                            <div className="h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: aufhellen(farbe, 0.88) }}>
                                <div className="h-full rounded-full" style={{ width: `${stand.prozent}%`, backgroundColor: farbe }} />
                            </div>
                            <p className="text-[10px] text-slate-500 mt-1.5">
                                Mit dieser Rechnung sind {stand.prozent.toFixed(0)} % des Auftrags abgerechnet
                            </p>
                        </>
                    )}
                    {onBalkenAnzeigenChange && (
                        <label
                            htmlFor={balkenCheckboxId}
                            className="mt-1 flex min-h-[44px] cursor-pointer items-center gap-2.5 rounded py-2 text-xs text-slate-500 transition-colors hover:text-slate-700"
                        >
                            <input
                                id={balkenCheckboxId}
                                type="checkbox"
                                checked={balkenAnzeigen}
                                onChange={(e) => onBalkenAnzeigenChange(e.target.checked)}
                                className="h-4 w-4 cursor-pointer rounded border-slate-300 accent-rose-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                            />
                            Fortschrittsbalken auf die Rechnung drucken
                        </label>
                    )}
                </div>

                <div className={`${ZEILE} bg-white border-slate-100`}>
                    <div className="flex items-center gap-2.5">
                        <div className={`${SYMBOL} bg-slate-200`}><FileText className="w-3.5 h-3.5 text-slate-500" /></div>
                        <span className="text-xs font-medium text-slate-600">Auftragssumme</span>
                    </div>
                    <Betrag brutto={`${formatCurrency(stand.auftragBrutto)} €`} netto={`netto ${formatCurrency(stand.auftragNetto)} €`} farbe={farbe} />
                </div>

                <Vorrechnungen positionen={abrechnungsPositionen} bereitsAbgerechnet={stand.bereitsAbgerechnet} farbe={farbe} />

                <AbschlussZeile
                    symbol={<div className={`${SYMBOL} bg-slate-200`}><Receipt className="w-3.5 h-3.5 text-slate-500" /></div>}
                    automatischeBezeichnung={stand.dieseLabel}
                    automatischeUnterzeile={abschlagUnterzeile(stand, abschlagInfo)}
                    anpassung={abschlussZeilen?.dieseRechnung}
                    onAendern={aendern('dieseRechnung')}
                    betrag={<Betrag brutto={`− ${formatCurrency(stand.dieseBrutto)} €`} netto={`netto ${formatCurrency(stand.dieseNetto)} €`} farbe={farbe} />}
                />

                {stand.zeigeDifferenz && (
                    <AbschlussZeile
                        symbol={(
                            <div className={`${SYMBOL} ${stand.differenzEntfallen ? 'bg-slate-200' : 'bg-rose-100'}`}>
                                {stand.differenzEntfallen
                                    ? <MinusCircle className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />
                                    : <PlusCircle className="w-3.5 h-3.5 text-rose-600" aria-hidden="true" />}
                            </div>
                        )}
                        automatischeBezeichnung={stand.differenzEntfallen ? 'Nicht angefallen' : 'Zusätzliche Leistungen'}
                        automatischeUnterzeile={differenzHinweis || 'Unterschied zur Auftragsbestätigung'}
                        anpassung={abschlussZeilen?.differenz}
                        onAendern={aendern('differenz')}
                        betrag={(
                            <Betrag
                                brutto={`${stand.differenzEntfallen ? '−' : '+'} ${formatCurrency(stand.differenzBrutto)} €`}
                                netto={`netto ${formatCurrency(Math.abs(stand.differenzNetto))} €`}
                                farbe={farbe}
                                className={`text-sm font-bold ${stand.differenzEntfallen ? 'text-slate-600' : 'text-rose-700'}`}
                            />
                        )}
                    />
                )}

                {stand.zeigeRest && (
                    <div className={`${ZEILE} bg-white border-slate-200`}>
                        <span className="text-xs font-semibold text-slate-700">Noch offen nach dieser Rechnung</span>
                        <Betrag brutto={`${formatCurrency(stand.restBrutto)} €`} netto={`netto ${formatCurrency(stand.restNetto)} €`} farbe={farbe} />
                    </div>
                )}

                <div className={`${ZEILE} py-2.5 bg-rose-50 border-rose-200`}>
                    <div className="flex items-center gap-2.5">
                        <div className={`${SYMBOL} bg-rose-200`}><Receipt className="w-3.5 h-3.5 text-rose-700" /></div>
                        <span className="text-xs font-bold text-rose-700 uppercase tracking-wide">Zahlbetrag</span>
                    </div>
                    <Betrag
                        brutto={`${formatCurrency(stand.dieseBrutto)} €`}
                        netto={`netto ${formatCurrency(stand.dieseNetto)} € + ${formatCurrency(stand.dieseSteuer)} € USt`}
                        farbe={farbe}
                        className="text-base font-bold text-slate-900"
                    />
                </div>
            </div>
        </div>
    );
}

/** Bereits gestellte Rechnungen, einzeln; ohne Einzelpositionen die Summe. */
function Vorrechnungen({ positionen, bereitsAbgerechnet, farbe }: { positionen?: AbrechnungsPosition[]; bereitsAbgerechnet: number; farbe: string }) {
    const symbol = <div className={`${SYMBOL} bg-amber-100`}><Receipt className="w-3.5 h-3.5 text-amber-600" /></div>;
    const betrag = (netto: number) => (
        <Betrag brutto={`− ${formatCurrency(mitSteuer(netto))} €`} netto={`netto ${formatCurrency(netto)} €`} farbe={farbe} className="text-sm font-bold text-amber-700" />
    );
    if (positionen && positionen.length > 0) {
        return (
            <>
                {positionen.map((pos) => (
                    <div key={pos.dokumentNummer} className={`${ZEILE} bg-amber-50 border-amber-100`}>
                        <div className="flex items-center gap-2.5 min-w-0">
                            {symbol}
                            <div className="min-w-0">
                                <span className="text-[9px] font-semibold text-amber-500 uppercase tracking-wider">
                                    {TYP_LABELS[pos.typ] || pos.typ}
                                    {pos.abschlagsNummer ? ` #${pos.abschlagsNummer}` : ''}
                                </span>
                                <p className="text-xs font-medium text-slate-600 truncate">
                                    {pos.dokumentNummer}
                                    {pos.datum && (
                                        <span className="text-slate-400 ml-1.5">vom {new Date(pos.datum).toLocaleDateString('de-DE')}</span>
                                    )}
                                </p>
                            </div>
                        </div>
                        <div className="flex-shrink-0 ml-3">{betrag(pos.betragNetto)}</div>
                    </div>
                ))}
            </>
        );
    }
    if (bereitsAbgerechnet <= 0) return null;
    // Fallback, wenn die Einzelpositionen (noch) nicht geladen sind
    return (
        <div className={`${ZEILE} bg-amber-50 border-amber-100`}>
            <div className="flex items-center gap-2.5">
                {symbol}
                <span className="text-xs font-medium text-slate-600">Bereits abgerechnet</span>
            </div>
            {betrag(bereitsAbgerechnet)}
        </div>
    );
}
