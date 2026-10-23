import { useEffect, useState } from 'react';
import { Briefcase, Loader2, Save, Undo2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { TiptapEditor } from '../../components/TiptapEditor';
import { useToast } from '../../components/ui/toast';
import { VERRECHNUNGSEINHEITEN } from '../../types';
import { BildFeld } from './BildFeld';
import { InfoBeschreibung, InfoEinheit } from './InfoTexte';
import { projekteSatz } from './ordnerLogik';
import type { OrdnerAenderung } from './api';
import type { Ordner } from './typen';

export interface OrdnerReiterProps {
    ordner: Ordner;
    /** Wirft bei einem Fehler – die Meldung erscheint am Formular. */
    onSpeichern: (aenderung: OrdnerAenderung) => Promise<void>;
    onUngespeichert: (ungespeichert: boolean) => void;
}

interface Entwurf {
    name: string;
    einheit: string;
    beschreibung: string;
}

const EINHEITEN = VERRECHNUNGSEINHEITEN.map((e) => ({ value: e.name, label: e.anzeigename }));
const entwurfAus = (o: Ordner): Entwurf => ({ name: o.name, einheit: o.einheit ?? '', beschreibung: o.beschreibung });

/**
 * Reiter „Ordner“: Name, Einheit, Bild und Beschreibung des Ordners mit einem
 * gemeinsamen „Speichern“. Zeigt, an wie vielen Projekten die Kategorie hängt.
 */
export function OrdnerReiter({ ordner, onSpeichern, onUngespeichert }: OrdnerReiterProps) {
    const toast = useToast();
    const [entwurf, setEntwurf] = useState<Entwurf>(() => entwurfAus(ordner));
    const [ausgang, setAusgang] = useState<Entwurf>(() => entwurfAus(ordner));
    const [bild, setBild] = useState<File | null>(null);
    const [bildEntfernen, setBildEntfernen] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);

    const ungespeichert = JSON.stringify(entwurf) !== JSON.stringify(ausgang) || bild !== null || bildEntfernen;

    // Ändert sich der Ordner von außen (z. B. Umbenennen im Baum, Bild abgelegt) und hier ist nichts offen: übernehmen.
    useEffect(() => {
        if (ungespeichert) return;
        const neu = entwurfAus(ordner);
        setEntwurf(neu);
        setAusgang(neu);
        // Nur auf Änderungen am Ordner reagieren.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ordner]);

    useEffect(() => { onUngespeichert(ungespeichert); }, [ungespeichert, onUngespeichert]);
    useEffect(() => () => onUngespeichert(false), [onUngespeichert]);

    const verwerfen = () => {
        const neu = entwurfAus(ordner);
        setEntwurf(neu);
        setAusgang(neu);
        setBild(null);
        setBildEntfernen(false);
        setFehler(null);
    };

    const speichern = async () => {
        if (!entwurf.name.trim()) {
            const meldung = 'Bitte geben Sie dem Ordner einen Namen.';
            setFehler(meldung);
            toast.error(meldung);
            return;
        }
        setSendet(true);
        setFehler(null);
        try {
            await onSpeichern({
                name: entwurf.name.trim(),
                einheit: entwurf.einheit || null,
                beschreibung: entwurf.beschreibung,
                bild,
                bildEntfernen: bild === null && bildEntfernen,
            });
            setBild(null);
            setBildEntfernen(false);
            const neu = { ...entwurf, name: entwurf.name.trim() };
            setEntwurf(neu);
            setAusgang(neu);
            toast.success(`Ordner „${neu.name}“ gespeichert.`);
        } catch (err) {
            const meldung = err instanceof Error ? err.message : 'Der Ordner konnte nicht gespeichert werden.';
            setFehler(meldung);
            toast.error(meldung);
        } finally {
            setSendet(false);
        }
    };

    return (
        <div className="max-w-3xl space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="flex items-center gap-2 text-sm text-slate-700">
                    <Briefcase className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                    {projekteSatz(ordner.projektAnzahl)}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs text-slate-500" role="status">
                        {ungespeichert ? 'Nicht gespeichert' : 'Alles gespeichert'}
                    </span>
                    <Button size="sm" variant="outline" onClick={verwerfen} disabled={sendet || !ungespeichert}>
                        <Undo2 className="h-4 w-4" aria-hidden="true" /> Abbrechen
                    </Button>
                    <Button size="sm" onClick={() => void speichern()} disabled={sendet || !ungespeichert}>
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
                        Speichern
                    </Button>
                </div>
            </div>

            {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}

            <div className="grid gap-4 md:grid-cols-2">
                <div>
                    <Label htmlFor="ordner-name">Name *</Label>
                    <Input id="ordner-name" value={entwurf.name} maxLength={255}
                        onChange={(e) => setEntwurf({ ...entwurf, name: e.target.value })} placeholder="z. B. Geländer" />
                </div>
                <div>
                    <div className="flex items-center gap-1">
                        <Label htmlFor="ordner-einheit">Einheit</Label>
                        <InfoEinheit />
                    </div>
                    <Select id="ordner-einheit" value={entwurf.einheit} onChange={(v) => setEntwurf({ ...entwurf, einheit: v })}
                        options={EINHEITEN} placeholder="Einheit wählen" aria-label="Einheit" />
                </div>
            </div>

            <div className="space-y-1.5">
                <p className="text-sm font-medium text-slate-700">Bild</p>
                <BildFeld gespeichertUrl={ordner.bildUrl} datei={bild} entfernen={bildEntfernen} onDatei={setBild} onEntfernen={setBildEntfernen} />
            </div>

            <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                    <Label>Beschreibung</Label>
                    <InfoBeschreibung />
                </div>
                <TiptapEditor value={entwurf.beschreibung} onChange={(v) => setEntwurf((alt) => ({ ...alt, beschreibung: v }))} />
            </div>

        </div>
    );
}
