import { DecimalInput } from '../../components/ui/decimal-input';
import { TimeInput } from '../../components/ui/time-input';
import { WOCHENTAGE, ZEITFENSTER_LABELS, type ArbeitszeitEntwurf, type ZeitfensterBeschriftung } from './arbeitszeitInput';

interface Props {
    value: ArbeitszeitEntwurf;
    onChange: (next: ArbeitszeitEntwurf) => void;
    zeitfenster?: ZeitfensterBeschriftung;
    optionalHinweis?: boolean;
    kompakt?: boolean;
}
export function ArbeitszeitFelder({ value, onChange, zeitfenster = ZEITFENSTER_LABELS, optionalHinweis = false, kompakt = false }: Props) {
    const labelClass = kompakt ? 'text-xs font-medium text-slate-700' : 'text-sm font-medium text-slate-700';
    const zeitfelder = [
        { key: 'buchungStartZeit', label: zeitfenster.start },
        { key: 'buchungEndeZeit', label: zeitfenster.ende },
    ] as const;
    return <>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {WOCHENTAGE.map(tag => <label key={tag.key} className={labelClass}>
                {tag.label}
                <div className="relative mt-1">
                    <DecimalInput aria-label={`${tag.label} Stunden`} required min={0} max={24}
                        value={value[tag.key]} onChange={draft => onChange({ ...value, [tag.key]: draft })}
                        className={kompakt ? undefined : 'h-10 w-full rounded-lg border border-slate-200 px-3 pr-8 text-right focus:border-rose-300 focus:outline-none focus:ring-2 focus:ring-rose-200'} />
                    {!kompakt && <span className="absolute right-3 top-2.5 text-slate-400">h</span>}
                </div>
            </label>)}
        </div>
        <div className={kompakt ? 'grid grid-cols-1 gap-3 sm:grid-cols-2' : 'grid grid-cols-2 gap-3 border-t border-slate-100 pt-4'}>
            {zeitfelder.map(feld => <label key={feld.key} className={labelClass}>
                {feld.label}{optionalHinweis ? ' – optional' : ''}
                <TimeInput aria-label={`${feld.label}${optionalHinweis ? ' – optional' : ''}`}
                    value={value[feld.key]} onChange={draft => onChange({ ...value, [feld.key]: draft })}
                    className={kompakt ? 'mt-1' : 'mt-1 h-10 w-full rounded-lg border border-slate-200 px-3'} />
            </label>)}
        </div>
    </>;
}
