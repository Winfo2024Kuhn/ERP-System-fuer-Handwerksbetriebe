import type { ReactNode } from 'react';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';

interface Props {
    jahr: number;
    verfuegbareJahre: number[];
    onJahr: (jahr: number) => void;
    /** Rechts ausgerichtete Trefferzahl, z. B. „3 Dokumente gefunden“. */
    anzahlText: string;
    /** Weitere Filter zwischen Jahr und Trefferzahl. */
    children?: ReactNode;
}

/** Filterleiste über Lohnabrechnungen und BWA: Jahr, ggf. weitere Filter, Trefferzahl. */
export function UnterlagenFilter({ jahr, verfuegbareJahre, onJahr, anzahlText, children }: Props) {
    return (
        <div className="flex gap-4 items-end bg-slate-50 p-4 rounded-lg border border-slate-200">
            <div className="w-48">
                <Label>Jahr</Label>
                <Select
                    value={jahr.toString()}
                    onChange={v => onJahr(parseInt(v))}
                    options={verfuegbareJahre.map(j => ({ value: j.toString(), label: j.toString() }))}
                />
            </div>
            {children}
            <div className="ml-auto">
                <p className="text-sm text-slate-500 text-right">
                    {anzahlText}
                </p>
            </div>
        </div>
    );
}
