import { useEffect, useState } from 'react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';

const RECHTE = [
    { key: 'LESEN', label: 'Einkauf lesen' },
    { key: 'BEARBEITEN', label: 'Einkauf bearbeiten' },
    { key: 'ANFRAGE_SENDEN', label: 'Anfragen senden' },
    { key: 'BESTELLUNG_FREIGEBEN', label: 'Bestellungen freigeben' },
    { key: 'ZEUGNIS_PRUEFEN', label: 'Zeugnisse prüfen' },
] as const;
type Recht = typeof RECHTE[number]['key'];

export function EinkaufBerechtigungen({ profileId }: { profileId: number }) {
    const toast = useToast();
    const [rechte, setRechte] = useState<Recht[]>([]);
    const [laden, setLaden] = useState(true);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState(false);

    useEffect(() => {
        let aktiv = true;
        setLaden(true);
        setFehler(false);
        fetch(`/api/settings/einkauf-berechtigungen/${profileId}`)
            .then(async response => {
                if (!response.ok) throw new Error('Einkaufsrechte konnten nicht geladen werden. Nur Admins dürfen Rechte verwalten.');
                const values = await response.json() as string[];
                if (aktiv) setRechte(values.filter((value): value is Recht => RECHTE.some(item => item.key === value)));
            })
            .catch(error => {
                if (!aktiv) return;
                setFehler(true);
                toast.error(error instanceof Error ? error.message : 'Einkaufsrechte konnten nicht geladen werden.');
            })
            .finally(() => { if (aktiv) setLaden(false); });
        return () => { aktiv = false; };
    }, [profileId, toast]);

    const speichern = async () => {
        setSpeichert(true);
        try {
            const response = await fetch(`/api/settings/einkauf-berechtigungen/${profileId}`, {
                method: 'PUT', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ rechte }),
            });
            if (!response.ok) throw new Error('Einkaufsrechte konnten nicht gespeichert werden.');
            const updated = await response.json() as string[];
            setRechte(updated.filter((value): value is Recht => RECHTE.some(item => item.key === value)));
            toast.success('Einkaufsrechte gespeichert.');
        } catch (error) {
            toast.error(error instanceof Error ? error.message : 'Einkaufsrechte konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return <section aria-labelledby="einkauf-rechte-title" className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
        <div className="mb-3">
            <h4 id="einkauf-rechte-title" className="font-semibold text-slate-900">Einkaufsrechte</h4>
            <p className="text-sm text-slate-600">Admins verwalten hier die Rechte für dieses Profil.</p>
        </div>
        {laden ? <p role="status" className="text-sm text-slate-500">Einkaufsrechte werden geladen …</p> : fehler ? (
            <p role="alert" className="text-sm text-rose-700">Rechte können mit diesem Zugang nicht bearbeitet werden.</p>
        ) : <>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {RECHTE.map(item => <label key={item.key} className="flex items-center gap-2 text-sm text-slate-700">
                    <input type="checkbox" aria-label={item.label} checked={rechte.includes(item.key)} disabled={speichert}
                        onChange={event => setRechte(current => event.target.checked
                            ? [...current, item.key]
                            : current.filter(value => value !== item.key))}
                        className="rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                    {item.label}
                </label>)}
            </div>
            <Button className="mt-4 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" size="sm"
                onClick={speichern} disabled={speichert}>
                {speichert ? 'Speichert …' : 'Rechte speichern'}
            </Button>
        </>}
    </section>;
}
