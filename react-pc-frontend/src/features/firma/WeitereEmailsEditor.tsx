import { Plus, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { weitereEmailAendern, weitereEmailEntfernen, weitereEmailHinzufuegen } from './steuerberaterEntwurf';
import type { SteuerberaterEntwurf } from './firmaTypen';

type Aktualisieren = (aenderung: (prev: SteuerberaterEntwurf | null) => SteuerberaterEntwurf) => void;

interface Props {
    weitereEmails: string[];
    aktualisieren: Aktualisieren;
}

/** Weitere E-Mail-Adressen des Steuerberaters als Liste mit + und ×. */
export function WeitereEmailsEditor({ weitereEmails, aktualisieren }: Props) {
    return (
        <div className="space-y-2">
            <div className="flex items-center justify-between">
                <Label>Weitere E-Mail-Adressen</Label>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => aktualisieren(weitereEmailHinzufuegen)}
                >
                    <Plus className="w-3 h-3 mr-1" />E-Mail hinzufügen
                </Button>
            </div>
            {weitereEmails.length === 0 && (
                <p className="text-xs text-slate-500">Keine weiteren Adressen.</p>
            )}
            {weitereEmails.map((mail, idx) => (
                <div key={idx} className="flex items-center gap-2">
                    <Input
                        value={mail}
                        onChange={e => {
                            const wert = e.target.value;
                            aktualisieren(prev => weitereEmailAendern(prev, idx, wert));
                        }}
                        placeholder="weitere@kanzlei.de"
                    />
                    <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => aktualisieren(prev => weitereEmailEntfernen(prev, idx))}
                        aria-label="E-Mail entfernen"
                    >
                        <X className="w-4 h-4" />
                    </Button>
                </div>
            ))}
        </div>
    );
}
