import { useEffect, useState } from 'react';
import { Clock, Plus, Trash2 } from 'lucide-react';

import { Button } from '../../../components/ui/button';
import { Card } from '../../../components/ui/card';
import { Input } from '../../../components/ui/input';
import { Select } from '../../../components/ui/select-custom';
import { formatCurrency } from '../../../components/artikel/formatCurrency';

import { Kennzahl, Umschalter, Zahlenfeld } from '../bausteine';
import { lohnSummen, zeitzeilenKosten } from '../berechnung';
import { BEISPIEL_ARBEITSGAENGE, neueZeitzeile } from '../beispieldaten';
import { formatStunden } from '../format';
import type { ArbeitszeitPosition, VorkalkulationDaten } from '../types';

interface ArbeitszeitSchrittProps {
    daten: VorkalkulationDaten;
    aendern: (patch: Partial<VorkalkulationDaten>) => void;
    readOnly: boolean;
}

interface ArbeitsgangOption {
    id: number;
    beschreibung: string;
    stundensatz: number;
}

/**
 * Laedt die Arbeitsgaenge aus den Stammdaten.
 *
 * Bewusst `Arbeitsgang` und nicht `Arbeitszeitart`: An `Arbeitsgang` haengen
 * die Zeitbuchungen der Zeiterfassung. Nur so laesst sich die Vor-Kalkulation
 * spaeter gegen die tatsaechlich gebuchte Zeit stellen.
 *
 * Faellt die API aus, kommen die Beispiel-Arbeitsgaenge zum Zug — der Dummy
 * soll auch ohne laufendes Backend bedienbar bleiben.
 */
function useArbeitsgaenge(): { optionen: ArbeitsgangOption[]; ausBeispiel: boolean } {
    const [optionen, setOptionen] = useState<ArbeitsgangOption[]>(BEISPIEL_ARBEITSGAENGE);
    const [ausBeispiel, setAusBeispiel] = useState(true);

    useEffect(() => {
        let abgebrochen = false;
        (async () => {
            try {
                const antwort = await fetch('/api/arbeitsgaenge');
                if (!antwort.ok) return;
                const roh: { id: number; beschreibung: string; stundensatz: number | null }[] = await antwort.json();
                if (abgebrochen || roh.length === 0) return;
                setOptionen(roh.map((a) => ({
                    id: a.id,
                    beschreibung: a.beschreibung,
                    stundensatz: a.stundensatz ?? 0,
                })));
                setAusBeispiel(false);
            } catch {
                // Kein Toast: Der Rueckfall auf Beispieldaten ist im Dummy der
                // erwartete Normalfall, kein Fehler, den der Bediener beheben kann.
                // Der Hinweis unter der Liste sagt, woher die Werte stammen.
            }
        })();
        return () => { abgebrochen = true; };
    }, []);

    return { optionen, ausBeispiel };
}

export function ArbeitszeitSchritt({ daten, aendern, readOnly }: ArbeitszeitSchrittProps) {
    const { optionen, ausBeispiel } = useArbeitsgaenge();
    const summen = lohnSummen(daten.arbeitszeit, daten.gkzHandProzent, daten.gkzMaschineProzent);

    const setzeZeile = (id: string, patch: Partial<ArbeitszeitPosition>) => {
        aendern({ arbeitszeit: daten.arbeitszeit.map((z) => (z.id === id ? { ...z, ...patch } : z)) });
    };

    /** Arbeitsgang gewechselt: Stundensatz aus den Stammdaten vorbelegen. */
    const waehleArbeitsgang = (zeile: ArbeitszeitPosition, wert: string) => {
        const gewaehlt = optionen.find((o) => String(o.id) === wert);
        if (!gewaehlt) return;
        setzeZeile(zeile.id, {
            arbeitsgangId: gewaehlt.id,
            arbeitsgang: gewaehlt.beschreibung,
            // Nur vorbelegen, wenn noch nichts drinsteht — ein von Hand
            // korrigierter Satz darf nicht beim Umschalten verlorengehen.
            stundensatz: zeile.stundensatz.trim()
                ? zeile.stundensatz
                : gewaehlt.stundensatz.toLocaleString('de-DE', { useGrouping: false, maximumFractionDigits: 2 }),
        });
    };

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h3 className="text-base font-bold text-slate-900">Wie viel Zeit steckt drin?</h3>
                    <p className="text-sm text-slate-500">
                        Arbeitsgang wählen, Stunden eintragen. Halbe Stunden sind erlaubt — z. B. 0,5.
                    </p>
                </div>
                <Button
                    variant="outline"
                    size="sm"
                    disabled={readOnly}
                    onClick={() => aendern({ arbeitszeit: [...daten.arbeitszeit, neueZeitzeile()] })}
                >
                    <Plus className="h-4 w-4" aria-hidden="true" />
                    Arbeitsgang hinzufügen
                </Button>
            </div>

            {daten.arbeitszeit.length === 0 ? (
                <Card className="border-dashed p-10 text-center">
                    <Clock className="mx-auto mb-2 h-8 w-8 text-rose-200" aria-hidden="true" />
                    <p className="font-medium text-slate-700">Noch keine Arbeitszeit eingetragen</p>
                    <p className="mt-1 text-sm text-slate-500">
                        Die Arbeitsgänge und ihre Stundensätze pflegen Sie unter Stammdaten → Arbeitsgänge.
                    </p>
                </Card>
            ) : (
                <div className="space-y-2">
                    {daten.arbeitszeit.map((zeile, index) => (
                        <Card key={zeile.id} className="p-3">
                            <div className="flex flex-wrap items-end gap-3">
                                <div className="min-w-[180px]">
                                    <label className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                        Arbeitsgang
                                    </label>
                                    <Select
                                        value={zeile.arbeitsgangId === null ? '' : String(zeile.arbeitsgangId)}
                                        onChange={(wert) => waehleArbeitsgang(zeile, wert)}
                                        disabled={readOnly}
                                        placeholder="Arbeitsgang wählen…"
                                        aria-label={`Arbeitsgang der Zeile ${index + 1}`}
                                        options={optionen.map((o) => ({ value: String(o.id), label: o.beschreibung }))}
                                    />
                                </div>

                                <div className="min-w-[200px] flex-1">
                                    <label className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                        Beschreibung
                                    </label>
                                    <Input
                                        value={zeile.beschreibung}
                                        disabled={readOnly}
                                        aria-label={`Beschreibung der Zeile ${index + 1}`}
                                        placeholder="Was genau gemacht wird"
                                        onChange={(e) => setzeZeile(zeile.id, { beschreibung: e.target.value })}
                                        className="py-1.5"
                                    />
                                </div>

                                <Zahlenfeld
                                    label="Stunden"
                                    wert={zeile.stunden}
                                    onChange={(entwurf) => setzeZeile(zeile.id, { stunden: entwurf })}
                                    ariaLabel={`Stunden der Zeile ${index + 1}`}
                                    einheit="h"
                                    breite="w-24"
                                    disabled={readOnly}
                                    platzhalter="0,00"
                                />

                                <span className="pb-1.5 text-sm font-light text-slate-300" aria-hidden="true">×</span>

                                <Zahlenfeld
                                    label="Stundensatz"
                                    wert={zeile.stundensatz}
                                    onChange={(entwurf) => setzeZeile(zeile.id, { stundensatz: entwurf })}
                                    ariaLabel={`Stundensatz der Zeile ${index + 1}`}
                                    einheit="€"
                                    breite="w-24"
                                    disabled={readOnly}
                                    platzhalter="0,00"
                                />

                                <div>
                                    <span className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                        Art
                                    </span>
                                    <Umschalter
                                        klein
                                        wert={zeile.art}
                                        disabled={readOnly}
                                        ariaLabel={`Art der Zeile ${index + 1}`}
                                        optionen={[
                                            { wert: 'HAND', text: 'Hand' },
                                            { wert: 'MASCHINE', text: 'Maschine' },
                                        ]}
                                        onChange={(wert) => setzeZeile(zeile.id, { art: wert })}
                                    />
                                </div>

                                <div className="ml-auto w-28 text-right">
                                    <span className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                                        Zeile
                                    </span>
                                    <p className="tabular-nums text-base font-bold text-slate-900">
                                        {formatCurrency(zeitzeilenKosten(zeile))}
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    disabled={readOnly}
                                    aria-label={`Arbeitszeile ${index + 1} entfernen`}
                                    onClick={() => aendern({ arbeitszeit: daten.arbeitszeit.filter((z) => z.id !== zeile.id) })}
                                    className="mb-1 rounded-md p-1.5 text-slate-300 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40"
                                >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                </button>
                            </div>
                        </Card>
                    ))}
                </div>
            )}

            {ausBeispiel && (
                <p className="text-xs text-slate-500">
                    Arbeitsgänge aus Beispieldaten — mit laufendem Server kommen hier Ihre eigenen aus den
                    Stammdaten.
                </p>
            )}

            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <Kennzahl
                    titel="Stunden gesamt"
                    wert={formatStunden(summen.stundenGesamt)}
                    zusatz={`Hand ${formatStunden(summen.stundenHand)} · Maschine ${formatStunden(summen.stundenMaschine)}`}
                />
                <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
                    <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Handarbeit</p>
                    <p className="tabular-nums text-sm font-bold text-slate-900">{formatCurrency(summen.lohnHand)}</p>
                    <div className="mt-1 flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-500">Zuschlag</span>
                        <Zahlenfeld
                            wert={daten.gkzHandProzent}
                            onChange={(entwurf) => aendern({ gkzHandProzent: entwurf })}
                            ariaLabel="Zuschlag auf die Handarbeit in Prozent"
                            einheit="%"
                            breite="w-16"
                            disabled={readOnly}
                            platzhalter="0"
                        />
                    </div>
                </div>
                <div className="rounded-lg border border-slate-200 bg-white px-3 py-2">
                    <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Maschine</p>
                    <p className="tabular-nums text-sm font-bold text-slate-900">{formatCurrency(summen.lohnMaschine)}</p>
                    <div className="mt-1 flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-500">Zuschlag</span>
                        <Zahlenfeld
                            wert={daten.gkzMaschineProzent}
                            onChange={(entwurf) => aendern({ gkzMaschineProzent: entwurf })}
                            ariaLabel="Zuschlag auf die Maschinenarbeit in Prozent"
                            einheit="%"
                            breite="w-16"
                            disabled={readOnly}
                            platzhalter="0"
                        />
                    </div>
                </div>
                <Kennzahl
                    betont
                    titel="Lohnkosten gesamt"
                    wert={formatCurrency(summen.lohnkostenGesamt)}
                    zusatz={`inkl. Zuschläge ${formatCurrency(summen.gkzHandBetrag + summen.gkzMaschineBetrag)}`}
                />
            </div>
        </div>
    );
}
