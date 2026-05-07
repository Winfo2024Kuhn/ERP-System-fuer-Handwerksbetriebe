import { useState, useEffect } from 'react';
import { Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Button } from '../ui/button';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';

const STANDARD_GRUENDE = [
    { value: 'TIPPFEHLER', label: 'Tippfehler / Erfassungsfehler' },
    { value: 'DOPPELT', label: 'Versehentlich doppelt erstellt' },
    { value: 'FALSCHER_KUNDE', label: 'Falscher Kunde / Empfänger' },
    { value: 'FALSCHER_BETRAG', label: 'Falscher Betrag' },
    { value: 'FALSCHE_POSITIONEN', label: 'Falsche Leistungspositionen' },
    { value: 'AUFTRAG_STORNIERT', label: 'Auftrag wurde storniert (Entwurf)' },
    { value: 'TESTDATEN', label: 'Testdaten / Demo-Eintrag' },
    { value: 'SONSTIGES', label: 'Sonstiges (bitte erläutern)' },
];

interface DokumentLoeschenDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    dokumentNummer: string | undefined;
    dokumentId: number | undefined;
    onDeleted: () => void;
    userId?: number;
}

/**
 * GoBD-konformer Lösch-Dialog für Ausgangs-Geschäftsdokumente.
 * Dropdown mit Standard-Gründen + Pflicht-Freitext (mind. 5 Zeichen).
 * Schickt Begründung + UserId an /api/ausgangs-dokumente/{id}.
 */
export function DokumentLoeschenDialog({
    open,
    onOpenChange,
    dokumentNummer,
    dokumentId,
    onDeleted,
    userId,
}: DokumentLoeschenDialogProps) {
    const toast = useToast();
    const [grundCode, setGrundCode] = useState<string>('TIPPFEHLER');
    const [freitext, setFreitext] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (open) {
            setGrundCode('TIPPFEHLER');
            setFreitext('');
        }
    }, [open]);

    const grundLabel = STANDARD_GRUENDE.find(g => g.value === grundCode)?.label || '';
    const begruendungZusammen = freitext.trim()
        ? `${grundLabel} – ${freitext.trim()}`
        : grundLabel;
    const erfordertFreitext = grundCode === 'SONSTIGES';
    const istValide = !erfordertFreitext || freitext.trim().length >= 5;

    const handleDelete = async () => {
        if (!dokumentId || !istValide) return;
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set('begruendung', begruendungZusammen);
            if (userId) params.set('userId', userId.toString());

            const res = await fetch(
                `/api/ausgangs-dokumente/${dokumentId}?${params.toString()}`,
                { method: 'DELETE' }
            );
            if (res.ok) {
                toast.success(`Dokument ${dokumentNummer} gelöscht`);
                onOpenChange(false);
                onDeleted();
            } else {
                const error = await res.text();
                toast.error(error || 'Löschen fehlgeschlagen');
            }
        } catch (e) {
            console.error(e);
            toast.error('Fehler beim Löschen');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-red-600">Dokument löschen</DialogTitle>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Möchten Sie <span className="font-semibold">{dokumentNummer}</span> wirklich löschen?
                    </p>
                    <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-800">
                        <strong>Steuerprüfungs-Hinweis:</strong> Die Löschung wird mit Begründung,
                        Bearbeiter und Zeitstempel revisionssicher protokolliert. Gemäß GoBD dürfen
                        nur Entwürfe gelöscht werden – gebuchte oder versandte Dokumente müssen storniert werden.
                    </div>
                    <div className="space-y-2">
                        <Label className="text-sm font-medium text-slate-700">Grund auswählen *</Label>
                        <Select
                            value={grundCode}
                            onChange={setGrundCode}
                            options={STANDARD_GRUENDE}
                        />
                    </div>
                    <div className="space-y-2">
                        <Label className="text-sm font-medium text-slate-700">
                            {erfordertFreitext ? 'Freitext-Begründung *' : 'Zusätzliche Erläuterung (optional)'}
                        </Label>
                        <textarea
                            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 min-h-[80px] resize-none"
                            placeholder={erfordertFreitext
                                ? 'Bitte mindestens 5 Zeichen Begründung angeben...'
                                : 'Optionale Detail-Begründung...'}
                            value={freitext}
                            onChange={(e) => setFreitext(e.target.value)}
                        />
                        {erfordertFreitext && freitext.trim().length > 0 && freitext.trim().length < 5 && (
                            <p className="text-xs text-red-500">Mindestens 5 Zeichen erforderlich.</p>
                        )}
                    </div>
                    <div className="text-xs text-slate-500">
                        Wird protokolliert als: <em className="text-slate-700">{begruendungZusammen || '(leer)'}</em>
                    </div>
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={() => onOpenChange(false)}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-red-600 text-white hover:bg-red-700"
                        disabled={loading || !istValide}
                        onClick={handleDelete}
                    >
                        <Trash2 className="w-4 h-4 mr-2" />
                        Löschen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
