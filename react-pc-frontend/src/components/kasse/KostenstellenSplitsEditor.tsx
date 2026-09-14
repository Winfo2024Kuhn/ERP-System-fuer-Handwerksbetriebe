import { useEffect, useState } from 'react';
import { Plus, Trash2, ChevronDown, ChevronRight, Split } from 'lucide-react';
import { Button } from '../ui/button';
import { Select } from '../ui/select-custom';
import { DecimalInput } from '../ui/decimal-input';
import { useToast } from '../ui/toast';
import { draft, type KostenstellenSplit, type SplitZahl } from '../../features/finanzen/kostenstellenDrafts';
export type { KostenstellenSplit } from '../../features/finanzen/kostenstellenDrafts';
import { validateNumberDrafts } from '../../lib/numberDrafts';

// Kostenstellen-Splits-Editor (Issue #60).
//
// Erlaubt mehrere Kostenstellen pro Beleg mit Prozent ODER absolutem Betrag
// und optionaler Streckung über mehrere Jahre. Beim Speichern landet das
// Ergebnis im PUT /api/buchhaltung/belege/{id} im Feld `kostenstellenSplits`.

interface Kostenstelle {
    id: number;
    bezeichnung: string;
    nummer?: string | null;
    istFixkosten?: boolean;
}

interface Props {
    splits: KostenstellenSplit[];
    onChange: (splits: KostenstellenSplit[]) => void;
    defaultStartJahr: number;
}

const inputCls = 'w-full rounded-md border border-slate-300 px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500';

export function KostenstellenSplitsEditor({ splits, onChange, defaultStartJahr }: Props) {
    const toast = useToast();
    const [open, setOpen] = useState<boolean>(splits.length > 0);
    const [kostenstellen, setKostenstellen] = useState<Kostenstelle[]>([]);

    useEffect(() => {
        fetch('/api/bestellungen-uebersicht/kostenstellen')
            .then(r => r.ok ? r.json() : [])
            .then((d: Kostenstelle[]) => setKostenstellen(Array.isArray(d) ? d : []))
            .catch(() => toast.error('Kostenstellen konnten nicht geladen werden.'));
    }, [toast]);

    const prozentSumme = splits.reduce((acc, s) => {
        const result = validateNumberDrafts({ prozent: draft(s, 'prozent') }, { prozent: { label: 'Anteil', min: 0, max: 100, integer: true } });
        return result.valid ? acc + (result.values.prozent ?? 0) : Number.NaN;
    }, 0);
    const ueberhang = prozentSumme > 100;

    const addSplit = () => {
        if (!Number.isFinite(prozentSumme)) { toast.error('Bitte vorhandene Anteile vollständig und zwischen 0 und 100 eingeben.'); return; }
        onChange([
            ...splits,
            {
                kostenstelleId: null,
                prozent: 100 - prozentSumme > 0 ? 100 - prozentSumme : null,
                absoluterBetrag: null,
                streckungJahre: 1,
                streckungStartJahr: defaultStartJahr,
                _clientKey: `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            },
        ]);
        setOpen(true);
    };

    const updateSplit = (index: number, patch: Partial<KostenstellenSplit>) => {
        onChange(splits.map((s, i) => (i === index ? { ...s, ...patch } : s)));
    };

    const updateDraft = (index: number, key: SplitZahl, value: string) => {
        const fields = { ...splits[index]._drafts, [key]: value };
        if (value !== '' && key === 'prozent') fields.absoluterBetrag = '';
        if (value !== '' && key === 'absoluterBetrag') fields.prozent = '';
        updateSplit(index, { _drafts: fields });
    };

    const removeSplit = (index: number) => {
        onChange(splits.filter((_, i) => i !== index));
    };

    const kostenstellenOptions = [
        { value: '', label: '– bitte wählen –' },
        ...kostenstellen.map(k => ({
            value: String(k.id),
            label: `${k.nummer ? k.nummer + ' ' : ''}${k.bezeichnung}${k.istFixkosten ? ' · Fixkosten' : ''}`,
        })),
    ];

    return (
        <div className="border border-slate-200 rounded-lg bg-white">
            <button type="button"
                onClick={() => setOpen(o => !o)}
                className="w-full px-3 py-2 flex items-center justify-between text-left hover:bg-slate-50">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                    <Split className="w-4 h-4 text-rose-600" />
                    Kosten aufteilen (Kostenstellen-Splits)
                    {splits.length > 0 && (
                        <span className="ml-2 px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs">
                            {splits.length}
                        </span>
                    )}
                </div>
                {open ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronRight className="w-4 h-4 text-slate-400" />}
            </button>

            {open && (
                <div className="border-t border-slate-200 p-3 space-y-2">
                    {splits.length === 0 && (
                        <p className="text-xs text-slate-500 italic">
                            Noch keine Splits — der Beleg wird vollständig der oben gewählten Kostenstelle zugeordnet.
                        </p>
                    )}

                    {splits.map((s, i) => (
                        <div key={s.id ?? s._clientKey ?? `idx-${i}`}
                            className="border border-slate-200 rounded-md p-2 bg-slate-50">
                            <div className="grid grid-cols-12 gap-2 items-end">
                                <div className="col-span-5">
                                    <label className="block text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">Kostenstelle</label>
                                    <Select
                                        value={s.kostenstelleId != null ? String(s.kostenstelleId) : ''}
                                        onChange={(v: string) => updateSplit(i, { kostenstelleId: v ? Number(v) : null })}
                                        options={kostenstellenOptions}
                                    />
                                </div>
                                <div className="col-span-2">
                                    <label className="block text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">% Anteil</label>
                                    <DecimalInput aria-label={`Anteil Split ${i + 1} (%)`} value={draft(s, 'prozent')} onChange={value => updateDraft(i, 'prozent', value)} className={inputCls} />
                                </div>
                                <div className="col-span-2">
                                    <label className="block text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">€ absolut</label>
                                    <DecimalInput aria-label={`Betrag Split ${i + 1} (€)`} value={draft(s, 'absoluterBetrag')} onChange={value => updateDraft(i, 'absoluterBetrag', value)} className={inputCls} />
                                </div>
                                <div className="col-span-2">
                                    <label className="block text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">Jahre</label>
                                    <DecimalInput aria-label={`Streckung Split ${i + 1}`} value={draft(s, 'streckungJahre')} onChange={value => updateDraft(i, 'streckungJahre', value)} className={inputCls} />
                                </div>
                                <div className="col-span-1 flex justify-end pb-0.5">
                                    <button type="button"
                                        onClick={() => removeSplit(i)}
                                        className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded"
                                        title="Split entfernen">
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                </div>
                                {draft(s, 'streckungJahre') !== '1' && (
                                    <>
                                        <div className="col-span-3">
                                            <label className="block text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">Startjahr Streckung</label>
                                            <DecimalInput aria-label={`Startjahr Split ${i + 1}`} value={draft(s, 'streckungStartJahr')} onChange={value => updateDraft(i, 'streckungStartJahr', value)} className={inputCls} />
                                        </div>
                                        <div className="col-span-9 text-[11px] text-slate-500 italic self-end pb-1">
                                            Über {draft(s, 'streckungJahre')} Jahre verteilt — ab {draft(s, 'streckungStartJahr') || '–'}.
                                        </div>
                                    </>
                                )}
                                <div className="col-span-12">
                                    <input type="text"
                                        value={s.beschreibung ?? ''}
                                        onChange={e => updateSplit(i, { beschreibung: e.target.value })}
                                        placeholder="Beschreibung (optional)"
                                        maxLength={255}
                                        className={`${inputCls} mt-1`} />
                                </div>
                            </div>
                        </div>
                    ))}

                    <div className="flex items-center justify-between pt-1">
                        <Button type="button" variant="outline" size="sm"
                            onClick={addSplit}
                            className="border-rose-300 text-rose-700 hover:bg-rose-50">
                            <Plus className="w-3 h-3 mr-1" /> Split hinzufügen
                        </Button>
                        <div className={`text-xs ${ueberhang ? 'text-red-700 font-semibold' : 'text-slate-500'}`}>
                            Summe Prozent: {Number.isFinite(prozentSumme) ? prozentSumme.toLocaleString('de-DE') : '–'}%{ueberhang ? ' — über 100%!' : ''}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
