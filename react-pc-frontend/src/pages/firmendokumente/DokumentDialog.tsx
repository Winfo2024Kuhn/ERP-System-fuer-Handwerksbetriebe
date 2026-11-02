import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { Loader2, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DatePicker } from '../../components/ui/datepicker';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import { useToast } from '../../components/ui/toast';
import { MAX_NAME_LAENGE, pruefeName } from './firmendokumenteLogik';
import { FaelligAmInfo } from './InfoTexte';
import type { DokumentAngaben } from './typen';

export interface DokumentDialogProps {
    titel: string;
    beschreibung: ReactNode;
    start: DokumentAngaben;
    ordnerOptionen: { value: string; label: string }[];
    knopfText: string;
    knopfSymbol: ReactNode;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog und als Toast. */
    onSpeichern: (angaben: DokumentAngaben) => Promise<void>;
}

/**
 * Name, Ordner und „Fällig am“ eines Firmendokuments – gemeinsam für
 * „PDF hochladen“ und „Bearbeiten“.
 */
export function DokumentDialog({ titel, beschreibung, start, ordnerOptionen, knopfText, knopfSymbol, onAbbrechen, onSpeichern }: DokumentDialogProps) {
    const toast = useToast();
    const id = useId();
    const [name, setName] = useState(start.name);
    const [ordnerId, setOrdnerId] = useState(String(start.ordnerId));
    const [faelligAm, setFaelligAm] = useState(start.faelligAm ?? '');
    const [nameFehler, setNameFehler] = useState<string | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);

    const absenden = (event: FormEvent) => {
        event.preventDefault();
        const meldung = pruefeName(name, 'Dokument');
        setNameFehler(meldung);
        if (meldung) return;
        setSendet(true);
        setFehler(null);
        onSpeichern({ name: name.trim(), ordnerId: Number(ordnerId), faelligAm: faelligAm || null })
            .catch((err: unknown) => {
                const text = err instanceof Error ? err.message : 'Das Dokument konnte nicht gespeichert werden.';
                setFehler(text);
                toast.error(text);
                setSendet(false);
            });
    };

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-lg" aria-labelledby={`${id}-titel`}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id={`${id}-titel`}>{titel}</DialogTitle>
                    <DialogDescription className="break-words">{beschreibung}</DialogDescription>
                </DialogHeader>
                <form id={`${id}-form`} onSubmit={absenden} noValidate className="space-y-4">
                    <div>
                        <Label htmlFor={`${id}-name`}>Name *</Label>
                        <Input
                            id={`${id}-name`}
                            value={name}
                            maxLength={MAX_NAME_LAENGE}
                            autoFocus
                            placeholder="z. B. Torprüfung Halle 2 – 2026"
                            aria-invalid={nameFehler ? true : undefined}
                            aria-describedby={nameFehler ? `${id}-name-fehler` : undefined}
                            onChange={(e) => { setName(e.target.value); if (nameFehler) setNameFehler(null); }}
                        />
                        {nameFehler && <p id={`${id}-name-fehler`} role="alert" className="mt-1 text-sm text-red-700">{nameFehler}</p>}
                    </div>
                    <div>
                        <Label htmlFor={`${id}-ordner`}>Ordner *</Label>
                        <Select id={`${id}-ordner`} aria-label="Ordner" options={ordnerOptionen} value={ordnerId} onChange={setOrdnerId} />
                    </div>
                    <div>
                        <div className="mb-1.5 flex items-center gap-1">
                            <Label htmlFor={`${id}-faellig`} className="mb-0">Fällig am (freiwillig)</Label>
                            <FaelligAmInfo />
                        </div>
                        <div className="flex items-center gap-2">
                            <DatePicker id={`${id}-faellig`} aria-label="Fällig am" value={faelligAm} onChange={setFaelligAm} placeholder="Kein Datum" className="flex-1" />
                            {faelligAm && (
                                <Button type="button" variant="ghost" size="sm" onClick={() => setFaelligAm('')} aria-label="Datum entfernen" title="Datum entfernen">
                                    <X className="h-4 w-4" aria-hidden="true" />
                                </Button>
                            )}
                        </div>
                        <p className="mt-1 text-xs text-slate-500">Zum Beispiel die nächste Prüfung. Ab 30 Tagen vorher wird das Dokument gelb markiert.</p>
                    </div>
                    {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}
                </form>
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    <Button type="submit" form={`${id}-form`} disabled={sendet}>
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : knopfSymbol}
                        {knopfText}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
