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
    // Nur noch das Ende: „Frühester Start“ wurde entfernt (Issue #270). Der Beginn
    // bleibt im Entwurf und wird beim Speichern unverändert durchgereicht.
    const endeLabel = `${zeitfenster.ende}${optionalHinweis ? ' – optional' : ''}`;
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
            <label className={labelClass}>
                {endeLabel}
                <TimeInput aria-label={endeLabel}
                    value={value.buchungEndeZeit} onChange={draft => onChange({ ...value, buchungEndeZeit: draft })}
                    className={kompakt ? 'mt-1' : 'mt-1 h-10 w-full rounded-lg border border-slate-200 px-3'} />
            </label>
        </div>
    </>;
}
