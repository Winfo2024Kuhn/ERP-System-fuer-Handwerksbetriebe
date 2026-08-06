import { useMemo, useState } from 'react';
import { Check, Diamond, Unlink, X } from 'lucide-react';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import { buildPositionMap, formatCurrency, serviceLineTotal } from './helpers';
import { sammleGruppenKandidaten } from './blockOps';
import type { DocBlock } from './types';

interface AlternativGruppeDialogProps {
    /** Alle Bloecke des Dokuments — daraus werden Kandidaten und Nummern abgeleitet. */
    blocks: DocBlock[];
    /** Die Leistung, von der aus der Dialog geoeffnet wurde. Immer Teil der Gruppe. */
    ankerId: string;
    onSpeichern: (blockIds: string[], name: string) => void;
    onAufloesen: (name: string) => void;
    onClose: () => void;
}

const DEFAULT_NAME = 'Auswahl';

/**
 * Stellt eine Entweder-Oder-Gruppe zusammen: welche Leistungen gehoeren dazu und
 * unter welcher Ueberschrift sieht der Kunde sie.
 *
 * Angeboten werden nur die Leistungen aus dem Container des Ankers (Root-Ebene
 * ODER derselbe Bauabschnitt) — siehe `sammleGruppenKandidaten`. Nach dem
 * Speichern ruecken die Varianten im Editor automatisch zusammen.
 */
export function AlternativGruppeDialog({
    blocks, ankerId, onSpeichern, onAufloesen, onClose,
}: AlternativGruppeDialogProps) {
    const kandidaten = useMemo(() => sammleGruppenKandidaten(blocks, ankerId), [blocks, ankerId]);
    const posMap = useMemo(() => buildPositionMap(blocks), [blocks]);

    const anker = kandidaten.find(b => b.id === ankerId);
    const bestehendeGruppe = anker?.alternativGruppe ?? null;

    const [name, setName] = useState(bestehendeGruppe ?? DEFAULT_NAME);
    const [gewaehlt, setGewaehlt] = useState<Set<string>>(() => new Set(
        bestehendeGruppe
            ? kandidaten.filter(b => b.alternativGruppe === bestehendeGruppe).map(b => b.id)
            : [ankerId]
    ));

    const umschalten = (id: string) => {
        if (id === ankerId) return; // Der Anker bleibt immer Teil der Gruppe.
        setGewaehlt(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    };

    const nameGetrimmt = name.trim();
    const genugVarianten = gewaehlt.size >= 2;
    const speicherbar = genugVarianten && nameGetrimmt.length > 0;

    const speichern = () => {
        if (!speicherbar) return;
        onSpeichern([...gewaehlt], nameGetrimmt);
        onClose();
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center">
            {/* Backdrop */}
            <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />

            {/* Dialog */}
            <div
                role="dialog"
                aria-modal="true"
                aria-label="Alternative Leistungen zusammenstellen"
                onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}
                className="relative bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-lg mx-4 max-h-[85vh] flex flex-col animate-in fade-in zoom-in-95 duration-200"
            >
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
                    <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 bg-amber-50 border border-amber-100 rounded-xl flex items-center justify-center">
                            <Diamond className="w-4 h-4 text-amber-600" />
                        </div>
                        <div>
                            <h2 className="text-base font-bold text-slate-900">Alternative Leistungen</h2>
                            <p className="text-xs text-slate-400">Der Kunde wählt genau eine davon aus</p>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        aria-label="Schließen"
                        className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40"
                    >
                        <X className="w-4 h-4 text-slate-400" />
                    </button>
                </div>

                {/* Ueberschrift fuer den Kunden */}
                <div className="px-5 pt-4">
                    <label htmlFor="alternativ-gruppe-name" className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
                        Überschrift
                    </label>
                    <input
                        id="alternativ-gruppe-name"
                        type="text"
                        value={name}
                        autoFocus
                        onChange={(e) => setName(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter') speichern(); }}
                        placeholder="z.B. Bodenbelag"
                        className="w-full text-sm font-medium text-slate-900 bg-white border border-slate-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-300 transition-all"
                    />
                    <p className="text-[11px] text-slate-400 mt-1">
                        Diesen Text sieht der Kunde über den Varianten.
                    </p>
                </div>

                {/* Kandidaten */}
                <div className="px-5 pt-4 pb-1 flex items-center justify-between">
                    <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                        Welche Leistungen gehören dazu?
                    </span>
                    <span className={cn(
                        "text-[11px] font-medium",
                        genugVarianten ? "text-slate-400" : "text-amber-600"
                    )}>
                        {gewaehlt.size} ausgewählt
                    </span>
                </div>
                <div className="flex-1 overflow-y-auto px-5 pb-3 min-h-0">
                    {kandidaten.length < 2 ? (
                        <p className="text-xs text-slate-400 bg-slate-50 border border-slate-100 rounded-lg px-3 py-4 text-center">
                            Hier gibt es nur diese eine Leistung. Lege zuerst eine zweite an —
                            eine Auswahl braucht mindestens zwei Varianten.
                        </p>
                    ) : (
                        <div className="space-y-1">
                            {kandidaten.map(kandidat => {
                                const aktiv = gewaehlt.has(kandidat.id);
                                const istAnker = kandidat.id === ankerId;
                                const fremdeGruppe = kandidat.alternativGruppe
                                    && kandidat.alternativGruppe !== bestehendeGruppe;
                                return (
                                    <label
                                        key={kandidat.id}
                                        className={cn(
                                            "flex items-center gap-3 px-3 py-2.5 rounded-lg border transition-colors",
                                            istAnker
                                                ? "border-amber-200 bg-amber-50/70 cursor-default"
                                                : aktiv
                                                    ? "border-amber-200 bg-amber-50/70 cursor-pointer hover:bg-amber-50"
                                                    : "border-slate-100 hover:bg-slate-50 cursor-pointer"
                                        )}
                                    >
                                        <input
                                            type="checkbox"
                                            checked={aktiv}
                                            disabled={istAnker}
                                            onChange={() => umschalten(kandidat.id)}
                                            className="w-4 h-4 rounded border-slate-300 text-amber-600 focus:ring-2 focus:ring-amber-500/30 disabled:opacity-60"
                                        />
                                        <span className="w-9 flex-shrink-0 text-[11px] font-bold text-slate-400 tabular-nums">
                                            {posMap.get(kandidat.id) ?? '–'}
                                        </span>
                                        <span className="flex-1 min-w-0">
                                            <span className="block text-sm font-medium text-slate-800 truncate">
                                                {kandidat.title || 'Ohne Titel'}
                                            </span>
                                            {(istAnker || fremdeGruppe) && (
                                                <span className="block text-[10px] text-amber-600">
                                                    {istAnker
                                                        ? 'Ausgangsposition — immer dabei'
                                                        : `Bisher in „${kandidat.alternativGruppe}"`}
                                                </span>
                                            )}
                                        </span>
                                        <span className="text-xs font-semibold text-slate-500 tabular-nums flex-shrink-0">
                                            {formatCurrency(serviceLineTotal(kandidat))} €
                                        </span>
                                    </label>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex items-center gap-2 px-5 py-4 border-t border-slate-100">
                    {bestehendeGruppe && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => { onAufloesen(bestehendeGruppe); onClose(); }}
                            className="text-slate-500 hover:text-rose-600 hover:bg-rose-50 gap-1.5"
                        >
                            <Unlink className="w-3.5 h-3.5" />
                            Auswahl auflösen
                        </Button>
                    )}
                    <div className="ml-auto flex items-center gap-2">
                        {!genugVarianten && kandidaten.length >= 2 && (
                            <span className="text-[11px] text-amber-600">Mindestens zwei auswählen</span>
                        )}
                        <Button variant="ghost" size="sm" onClick={onClose} className="text-slate-500 hover:bg-slate-100">
                            Abbrechen
                        </Button>
                        <Button
                            size="sm"
                            onClick={speichern}
                            disabled={!speicherbar}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 gap-1.5 disabled:opacity-50"
                        >
                            <Check className="w-3.5 h-3.5" />
                            Übernehmen
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
