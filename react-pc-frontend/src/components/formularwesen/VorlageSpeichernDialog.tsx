import { useState } from 'react';
import { Save } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';

interface VorlageSpeichernDialogProps {
    onSpeichern: (name: string) => void;
    onAbbrechen: () => void;
}

/** Name für eine neue Vorlage abfragen. */
export default function VorlageSpeichernDialog({ onSpeichern, onAbbrechen }: VorlageSpeichernDialogProps) {
    const [name, setName] = useState('');
    const fertig = name.trim();

    return (
        <Dialog open onOpenChange={offen => { if (!offen) onAbbrechen(); }} aria-labelledby="vorlage-speichern-titel" className="w-full max-w-md">
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id="vorlage-speichern-titel">Vorlage speichern</DialogTitle>
                    <DialogDescription>Unter diesem Namen finden Sie die Vorlage später wieder.</DialogDescription>
                </DialogHeader>
                <form
                    id="vorlage-speichern-formular"
                    onSubmit={e => { e.preventDefault(); if (fertig) onSpeichern(fertig); }}
                >
                    <Label htmlFor="vorlage-name">Name der Vorlage</Label>
                    <Input
                        id="vorlage-name"
                        autoFocus
                        value={name}
                        onChange={e => setName(e.target.value)}
                        placeholder="z. B. Briefpapier Standard"
                        className="mt-1"
                    />
                </form>
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={onAbbrechen}>Abbrechen</Button>
                    <Button type="submit" form="vorlage-speichern-formular" size="sm" disabled={!fertig} title={fertig ? undefined : 'Bitte zuerst einen Namen eingeben'}>
                        <Save aria-hidden="true" className="mr-1.5 h-4 w-4" />Speichern
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
