import type { ReactNode } from 'react';
import { Plus, Trash2 } from 'lucide-react';

import { Button } from '../../../components/ui/button';
import { Card } from '../../../components/ui/card';
import { Input } from '../../../components/ui/input';
import { formatCurrency } from '../../../components/artikel/formatCurrency';
import { cn } from '../../../lib/utils';

import { Schalter, Zahlenfeld } from '../bausteine';
import { berechneGesamt, type Gesamtergebnis } from '../berechnung';
import { neueId } from '../beispieldaten';
import { formatKg, formatQm } from '../format';
import type { VorkalkulationDaten } from '../types';

interface GesamtSchrittProps {
    daten: VorkalkulationDaten;
    aendern: (patch: Partial<VorkalkulationDaten>) => void;
    readOnly: boolean;
    onPreisUebernehmen: () => void;
}

/** Eine Zeile der Ergebnis-Kette. */
function Kettenzeile({
    text,
    zusatz,
    betrag,
    summe,
    ergebnis,
    eingabe,
}: {
    text: string;
    zusatz?: string;
    betrag: number;
    /** Zwischensumme: oben eine Linie, fetter gesetzt. */
    summe?: boolean;
    /** Endergebnis: rose hervorgehoben. */
    ergebnis?: boolean;
    eingabe?: ReactNode;
}) {
    return (
        <div
            className={cn(
                'flex items-center gap-3 py-1.5',
                summe && 'mt-1 border-t border-slate-300 pt-2',
                ergebnis && 'mt-1 border-t-2 border-rose-300 pt-2',
            )}
        >
            <div className="min-w-0 flex-1">
                <p className={cn(
                    'text-sm',
                    ergebnis ? 'font-bold text-slate-900' : summe ? 'font-semibold text-slate-900' : 'text-slate-600',
                )}>
                    {text}
                </p>
                {zusatz && <p className="text-[11px] text-slate-400">{zusatz}</p>}
            </div>
            {eingabe && <div className="flex-shrink-0">{eingabe}</div>}
            <p className={cn(
                'w-32 flex-shrink-0 text-right tabular-nums',
                ergebnis ? 'text-lg font-bold text-rose-700' : summe ? 'text-sm font-bold text-slate-900' : 'text-sm text-slate-700',
            )}>
                {formatCurrency(betrag)}
            </p>
        </div>
    );
}

export function GesamtSchritt({ daten, aendern, readOnly, onPreisUebernehmen }: GesamtSchrittProps) {
    const ergebnis: Gesamtergebnis = berechneGesamt(daten);
    const { material, lohn, beschichtung } = ergebnis;

    const setzeZusatz = (id: string, patch: { bezeichnung?: string; betrag?: string }) => {
        aendern({ zusatzkosten: daten.zusatzkosten.map((z) => (z.id === id ? { ...z, ...patch } : z)) });
    };

    return (
        <div className="space-y-4">
            {/* ── Oberfläche & Beschichtung ─────────────────────────────── */}
            <Card className="p-4">
                <div className="mb-3">
                    <h3 className="text-base font-bold text-slate-900">Oberfläche &amp; Beschichtung</h3>
                    <p className="text-sm text-slate-500">
                        Rechnet mit dem Gewicht und der Fläche aus dem Material-Reiter.
                    </p>
                </div>

                <div className="space-y-1">
                    <Kettenzeile
                        text="Feuerverzinken Schlosserware"
                        zusatz={formatKg(material.kilogrammSchlosserware)}
                        betrag={beschichtung.verzinkenSchlosserware}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.verzinkenSchlosserwareJeKg}
                                onChange={(entwurf) => aendern({ verzinkenSchlosserwareJeKg: entwurf })}
                                ariaLabel="Verzinkungspreis Schlosserware je Kilogramm"
                                einheit="€/kg"
                                breite="w-28"
                                disabled={readOnly}
                                platzhalter="0,00"
                            />
                        }
                    />
                    <Kettenzeile
                        text="Feuerverzinken Trägerware"
                        zusatz={formatKg(material.kilogrammTraegerware)}
                        betrag={beschichtung.verzinkenTraegerware}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.verzinkenTraegerwareJeKg}
                                onChange={(entwurf) => aendern({ verzinkenTraegerwareJeKg: entwurf })}
                                ariaLabel="Verzinkungspreis Trägerware je Kilogramm"
                                einheit="€/kg"
                                breite="w-28"
                                disabled={readOnly}
                                platzhalter="0,00"
                            />
                        }
                    />
                    <Kettenzeile
                        text="Feinverputzen"
                        zusatz={daten.feinverputzen
                            ? 'Aufschlag auf die Verzinkung — ohne Fracht'
                            : 'Ausgeschaltet'}
                        betrag={beschichtung.feinverputzen}
                        eingabe={
                            <div className="flex items-center gap-2">
                                <Schalter
                                    an={daten.feinverputzen}
                                    disabled={readOnly}
                                    onChange={(an) => aendern({ feinverputzen: an })}
                                >
                                    {daten.feinverputzen ? 'an' : 'aus'}
                                </Schalter>
                                <Zahlenfeld
                                    wert={daten.feinverputzenAufschlagProzent}
                                    onChange={(entwurf) => aendern({ feinverputzenAufschlagProzent: entwurf })}
                                    ariaLabel="Aufschlag fürs Feinverputzen in Prozent"
                                    einheit="%"
                                    breite="w-20"
                                    disabled={readOnly || !daten.feinverputzen}
                                    platzhalter="0"
                                />
                            </div>
                        }
                    />
                    <Kettenzeile
                        text="Fracht zur Verzinkerei"
                        zusatz={material.kilogrammVerzinken > 0
                            ? undefined
                            : 'Fällt nicht an — es wird nichts verzinkt'}
                        betrag={beschichtung.fracht}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.verzinkenFracht}
                                onChange={(entwurf) => aendern({ verzinkenFracht: entwurf })}
                                ariaLabel="Fracht zur Verzinkerei"
                                einheit="€"
                                breite="w-24"
                                disabled={readOnly}
                                platzhalter="0,00"
                            />
                        }
                    />
                    <Kettenzeile
                        text="Pulverbeschichten"
                        zusatz={formatQm(material.quadratmeterPulver)}
                        betrag={beschichtung.pulverbeschichten}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.pulverbeschichtenJeQm}
                                onChange={(entwurf) => aendern({ pulverbeschichtenJeQm: entwurf })}
                                ariaLabel="Preis fürs Pulverbeschichten je Quadratmeter"
                                einheit="€/m²"
                                breite="w-28"
                                disabled={readOnly}
                                platzhalter="0,00"
                            />
                        }
                    />
                    <Kettenzeile summe text="Oberfläche gesamt" betrag={beschichtung.gesamt} />
                </div>
            </Card>

            {/* ── Weitere Kosten ────────────────────────────────────────── */}
            <Card className="p-4">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <h3 className="text-base font-bold text-slate-900">Weitere Kosten</h3>
                        <p className="text-sm text-slate-500">
                            Alles, was weder Material noch Arbeitszeit ist — Fremdleistungen, Zeugnisse, Fahrtkosten.
                        </p>
                    </div>
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={readOnly}
                        onClick={() => aendern({
                            zusatzkosten: [...daten.zusatzkosten, { id: neueId('zus'), bezeichnung: '', betrag: '' }],
                        })}
                    >
                        <Plus className="h-4 w-4" aria-hidden="true" />
                        Zeile hinzufügen
                    </Button>
                </div>

                {daten.zusatzkosten.length === 0 ? (
                    <p className="py-4 text-center text-sm text-slate-500">Keine weiteren Kosten eingetragen.</p>
                ) : (
                    <div className="space-y-2">
                        {daten.zusatzkosten.map((zeile, index) => (
                            <div key={zeile.id} className="flex items-center gap-2">
                                <Input
                                    value={zeile.bezeichnung}
                                    disabled={readOnly}
                                    aria-label={`Bezeichnung der Kostenzeile ${index + 1}`}
                                    placeholder="z. B. Werkstoffzeugnisse"
                                    onChange={(e) => setzeZusatz(zeile.id, { bezeichnung: e.target.value })}
                                    className="flex-1 py-1.5"
                                />
                                <Zahlenfeld
                                    wert={zeile.betrag}
                                    onChange={(entwurf) => setzeZusatz(zeile.id, { betrag: entwurf })}
                                    ariaLabel={`Betrag der Kostenzeile ${index + 1}`}
                                    einheit="€"
                                    breite="w-28"
                                    disabled={readOnly}
                                    platzhalter="0,00"
                                />
                                <button
                                    type="button"
                                    disabled={readOnly}
                                    aria-label={`Kostenzeile ${index + 1} entfernen`}
                                    onClick={() => aendern({ zusatzkosten: daten.zusatzkosten.filter((z) => z.id !== zeile.id) })}
                                    className="rounded-md p-1.5 text-slate-300 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
                                >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </Card>

            {/* ── Ergebnis ──────────────────────────────────────────────── */}
            <Card className="p-4">
                <div className="mb-3">
                    <h3 className="text-base font-bold text-slate-900">Was es kostet, was wir nehmen</h3>
                    <p className="text-sm text-slate-500">
                        Die Prozentsätze tragen Sie selbst ein. Später sollen sie aus der Nachkalkulation kommen.
                    </p>
                </div>

                <div className="space-y-0.5">
                    <Kettenzeile
                        text="Material inklusive Zuschlag"
                        betrag={material.materialkostenGesamt}
                        zusatz={`${formatCurrency(material.materialkosten)} + ${formatCurrency(material.gkzBetrag)} Zuschlag`}
                    />
                    <Kettenzeile
                        text="Arbeitszeit inklusive Zuschlag"
                        betrag={lohn.lohnkostenGesamt}
                        zusatz={`${lohn.stundenGesamt.toLocaleString('de-DE', { maximumFractionDigits: 2 })} Stunden`}
                    />
                    <Kettenzeile
                        text="Oberfläche und weitere Kosten"
                        betrag={ergebnis.variableKosten}
                        zusatz={`Beschichtung ${formatCurrency(beschichtung.gesamt)} · Sonstiges ${formatCurrency(ergebnis.zusatzkosten)}`}
                    />
                    <Kettenzeile
                        summe
                        text="Was uns der Auftrag kostet"
                        zusatz="Herstellkosten"
                        betrag={ergebnis.herstellkosten}
                    />

                    <Kettenzeile
                        text="Verwaltung und Vertrieb"
                        zusatz="Noch von Hand — später aus der Nachkalkulation"
                        betrag={ergebnis.verwaltungVertriebBetrag}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.verwaltungVertriebProzent}
                                onChange={(entwurf) => aendern({ verwaltungVertriebProzent: entwurf })}
                                ariaLabel="Aufschlag für Verwaltung und Vertrieb in Prozent"
                                einheit="%"
                                breite="w-20"
                                disabled={readOnly}
                                platzhalter="0"
                            />
                        }
                    />
                    <Kettenzeile summe text="Selbstkosten" betrag={ergebnis.selbstkosten} />

                    <Kettenzeile
                        text="Wagnis und Gewinn"
                        zusatz="Noch von Hand — später aus der Nachkalkulation"
                        betrag={ergebnis.wagnisGewinnBetrag}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.wagnisGewinnProzent}
                                onChange={(entwurf) => aendern({ wagnisGewinnProzent: entwurf })}
                                ariaLabel="Aufschlag für Wagnis und Gewinn in Prozent"
                                einheit="%"
                                breite="w-20"
                                disabled={readOnly}
                                platzhalter="0"
                            />
                        }
                    />
                    <Kettenzeile
                        text="Skonto laut Kundenliste"
                        zusatz="Wird aufgeschlagen, damit nach dem Abzug der kalkulierte Preis bleibt"
                        betrag={ergebnis.skontoBetrag}
                        eingabe={
                            <Zahlenfeld
                                wert={daten.skontoProzent}
                                onChange={(entwurf) => aendern({ skontoProzent: entwurf })}
                                ariaLabel="Skonto in Prozent"
                                einheit="%"
                                breite="w-20"
                                disabled={readOnly}
                                platzhalter="0"
                            />
                        }
                    />

                    <Kettenzeile
                        ergebnis
                        text="Was wir dafür nehmen"
                        zusatz="Verkaufspreis netto"
                        betrag={ergebnis.verkaufspreisNetto}
                    />
                </div>

                <div className="mt-4 flex justify-end">
                    <Button onClick={onPreisUebernehmen} disabled={readOnly}>
                        Preis in die Position übernehmen
                    </Button>
                </div>
            </Card>
        </div>
    );
}
