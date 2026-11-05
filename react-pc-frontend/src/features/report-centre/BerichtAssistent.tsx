import { useState } from 'react';
import { ArrowLeft, ArrowRight, Check } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import type { GeladenesPdf } from './api';
import { BERICHT_DEFINITIONEN } from './berichtDefinitionen';
import { BerichtFilter } from './BerichtFilter';
import type { BerichtDefinition, FilterWerte } from './berichtTypen';
import { gewaehlteIds, gewaehlteMitarbeiter, mitarbeiterFeld, pruefeWerte } from './berichtWerte';
import { useBerichtMitarbeiter } from './useBerichtMitarbeiter';

export interface BerichtAssistentProps {
    offen: boolean;
    onClose: () => void;
    onPdfErstellt: (pdf: GeladenesPdf, titel: string) => string;
    /** Nur für Tests: „heute“ für die Vorauswahl (Vormonat). */
    heute?: Date;
    definitionen?: BerichtDefinition[];
}

const SCHRITTE = ['Bericht wählen', 'Auswahl', 'Vorschau & Abschluss'] as const;
const FRAGEN = ['Was möchtest du machen?', 'Wofür soll der Bericht sein?', 'Vorschau & Abschluss'] as const;

function Schrittanzeige({ schritt }: { schritt: number }) {
    return (
        <ol className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs" aria-label="Schritte">
            {SCHRITTE.map((name, index) => (
                <li key={name} aria-current={index === schritt ? 'step' : undefined}
                    className={cn('flex items-center gap-1.5', index === schritt ? 'font-semibold text-rose-700' : 'text-slate-500')}>
                    <span className={cn('flex h-5 w-5 items-center justify-center rounded-full text-[11px]',
                        index < schritt ? 'bg-rose-600 text-white' : index === schritt ? 'bg-rose-100 text-rose-700 ring-1 ring-rose-300' : 'bg-slate-100 text-slate-500')}>
                        {index < schritt ? <Check aria-hidden="true" className="h-3 w-3" /> : index + 1}
                    </span>
                    {name}
                    {index < SCHRITTE.length - 1 && <span aria-hidden="true" className="ml-1 text-slate-300">›</span>}
                </li>
            ))}
        </ol>
    );
}

function Auswahlkarten({ definitionen, gewaehlt, onWaehle }: { definitionen: BerichtDefinition[]; gewaehlt: string | null; onWaehle: (id: string) => void }) {
    return (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {definitionen.map(d => {
                const aktiv = d.id === gewaehlt;
                const Icon = d.icon;
                return (
                    <button key={d.id} type="button" aria-pressed={aktiv} onClick={() => onWaehle(d.id)}
                        className={cn('flex flex-col items-start gap-3 rounded-xl border p-5 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                            aktiv ? 'border-rose-400 bg-rose-50 ring-1 ring-rose-300' : 'border-slate-200 bg-white hover:border-rose-200 hover:bg-rose-50/40')}>
                        <span className={cn('flex h-11 w-11 items-center justify-center rounded-lg', aktiv ? 'bg-rose-600 text-white' : 'bg-rose-50 text-rose-600')}>
                            <Icon aria-hidden="true" className="h-5 w-5" />
                        </span>
                        <span className="text-base font-semibold text-slate-900">{d.titel}</span>
                        <span className="text-sm text-slate-600">{d.beschreibung}</span>
                    </button>
                );
            })}
        </div>
    );
}

/**
 * Geführter Dialog „Neuer Bericht“: Bericht wählen → Filter → Vorschau & PDF.
 * Alles Berichtsspezifische kommt aus der {@link BerichtDefinition}.
 */
export function BerichtAssistent({ offen, onClose, onPdfErstellt, heute, definitionen = BERICHT_DEFINITIONEN }: BerichtAssistentProps) {
    const toast = useToast();
    const [schritt, setSchritt] = useState(0);
    const [definitionId, setDefinitionId] = useState<string | null>(null);
    const [werte, setWerte] = useState<FilterWerte | null>(null);
    const [meldung, setMeldung] = useState<string | null>(null);
    const [warOffen, setWarOffen] = useState(offen);
    if (warOffen !== offen) {
        setWarOffen(offen);
        if (offen) { setSchritt(0); setDefinitionId(null); setWerte(null); setMeldung(null); }
    }
    const { mitarbeiter, laedt, fehler } = useBerichtMitarbeiter(offen);
    const definition = definitionen.find(d => d.id === definitionId) ?? null;
    const heuteJetzt = heute ?? new Date();

    const waehle = (id: string) => {
        if (id === definitionId) return;
        setDefinitionId(id);
        setWerte(definitionen.find(d => d.id === id)!.standardwerte(heuteJetzt));
        setMeldung(null);
    };

    const ids = definition && werte ? gewaehlteIds(mitarbeiterFeld(definition.filterFelder), werte, mitarbeiter) : [];
    const alleGewaehlt = mitarbeiter.length > 0 && ids.length === mitarbeiter.length;

    const weiter = () => {
        if (schritt === 1 && definition && werte) {
            const problem = laedt ? 'Die Mitarbeiter werden noch geladen.' : pruefeWerte(definition.filterFelder, werte, ids.length, mitarbeiter.length);
            setMeldung(problem);
            if (problem) { toast.error(problem); return; }
        }
        setSchritt(s => s + 1);
    };
    const zurueck = () => { setMeldung(null); setSchritt(s => s - 1); };
    const zurueckKnopf = <Button variant="outline" onClick={zurueck}><ArrowLeft aria-hidden="true" className="h-4 w-4" />Zurück</Button>;

    return (
        <Dialog open={offen} onOpenChange={o => { if (!o) onClose(); }} aria-labelledby="bericht-assistent-titel" className="w-full max-w-6xl">
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id="bericht-assistent-titel" className="text-slate-900">Neuer Bericht</DialogTitle>
                    <Schrittanzeige schritt={schritt} />
                    <DialogDescription className="pt-2 text-base font-medium text-slate-800">
                        {schritt === 1 && definition ? definition.titel : FRAGEN[schritt]}
                    </DialogDescription>
                </DialogHeader>

                {schritt === 0 && <Auswahlkarten definitionen={definitionen} gewaehlt={definitionId} onWaehle={waehle} />}

                {schritt === 1 && definition && werte && (
                    <div className="min-h-0 flex-1 overflow-y-auto">
                        <BerichtFilter felder={definition.filterFelder} werte={werte} onChange={w => { setWerte(w); setMeldung(null); }}
                            mitarbeiter={mitarbeiter} mitarbeiterLaden={laedt} mitarbeiterFehler={fehler} aktuellesJahr={heuteJetzt.getFullYear()} />
                        {meldung && <p role="alert" className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{meldung}</p>}
                    </div>
                )}

                {schritt === 2 && definition && werte && (
                    <definition.Abschluss definition={definition} werte={werte} mitarbeiter={gewaehlteMitarbeiter(ids, mitarbeiter)}
                        alleGewaehlt={alleGewaehlt} onPdfErstellt={onPdfErstellt} zurueck={zurueckKnopf} />
                )}

                {schritt < 2 && (
                    <DialogFooter className="gap-2 sm:justify-between">
                        {schritt === 0 ? <Button variant="outline" onClick={onClose}>Abbrechen</Button> : zurueckKnopf}
                        <Button onClick={weiter} disabled={!definition} title={definition ? undefined : 'Bitte zuerst einen Bericht wählen.'}>
                            Weiter<ArrowRight aria-hidden="true" className="h-4 w-4" />
                        </Button>
                    </DialogFooter>
                )}
            </DialogContent>
        </Dialog>
    );
}
