import { useMemo } from 'react';
import { Select } from '../../components/ui/select-custom';
import { InfoJahr } from './InfoTexte';
import { jahresOptionen } from './ordnerLogik';

/** „Alle Jahre“ + letzte 10 Jahre, mit Erklärung, welches Jahr zählt. */
export function JahrAuswahl({ jahr, onJahr }: { jahr: string; onJahr: (jahr: string) => void }) {
    const optionen = useMemo(() => jahresOptionen(), []);
    return (
        <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-slate-700">Abschlussjahr</span>
            <div className="w-36">
                <Select value={jahr} onChange={onJahr} options={optionen} aria-label="Abschlussjahr" />
            </div>
            <InfoJahr />
        </div>
    );
}
