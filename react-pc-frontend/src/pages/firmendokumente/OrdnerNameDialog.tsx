import { useId, useState, type FormEvent } from 'react';
import { Check, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { useToast } from '../../components/ui/toast';
import { MAX_NAME_LAENGE, pruefeName } from './firmendokumenteLogik';

export interface OrdnerNameDialogProps {
    titel: string;
    beschreibung: string;
    startName?: string;
    knopfText: string;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung des Servers erscheint am Feld und als Toast. */
    onSpeichern: (name: string) => Promise<void>;
}

/** Name für einen neuen oder umbenannten Ordner. */
export function OrdnerNameDialog({ titel, beschreibung, startName = '', knopfText, onAbbrechen, onSpeichern }: OrdnerNameDialogProps) {
    const toast = useToast();
    const id = useId();
    const [name, setName] = useState(startName);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);

    const absenden = (event: FormEvent) => {
        event.preventDefault();
        const meldung = pruefeName(name, 'Ordner');
        setFehler(meldung);
        if (meldung) return;
        setSendet(true);
        onSpeichern(name.trim()).catch((err: unknown) => {
            const text = err instanceof Error ? err.message : 'Der Ordner konnte nicht gespeichert werden.';
            setFehler(text);
            toast.error(text);
            setSendet(false);
        });
    };

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-md" aria-labelledby={`${id}-titel`}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id={`${id}-titel`}>{titel}</DialogTitle>
                    <DialogDescription className="break-words">{beschreibung}</DialogDescription>
                </DialogHeader>
                <form id={`${id}-form`} onSubmit={absenden} noValidate>
                    <Label htmlFor={`${id}-name`}>Name des Ordners *</Label>
                    <Input
                        id={`${id}-name`}
                        value={name}
                        maxLength={MAX_NAME_LAENGE}
                        autoFocus
                        placeholder="z. B. Tore"
                        aria-invalid={fehler ? true : undefined}
                        aria-describedby={fehler ? `${id}-fehler` : undefined}
                        onChange={(e) => { setName(e.target.value); if (fehler) setFehler(null); }}
                    />
                    {fehler && <p id={`${id}-fehler`} role="alert" className="mt-1 text-sm text-red-700">{fehler}</p>}
                </form>
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    <Button type="submit" form={`${id}-form`} disabled={sendet}>
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Check className="h-4 w-4" aria-hidden="true" />}
                        {knopfText}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
