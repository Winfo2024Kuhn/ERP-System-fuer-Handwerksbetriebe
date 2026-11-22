import { Check, Loader2, LockKeyhole, Save } from 'lucide-react';
import { Button } from '../../components/ui/button';

interface Props {
    readOnly: boolean;
    kopiert: boolean;
    speichert: boolean;
    gespeichert: boolean;
    speichernGesperrt: boolean;
    onSchliessen: () => void;
    onSpeichern: () => void;
}

const Taste = ({ taste, text, titel }: { taste: string; text: string; titel: string }) => (
    <span title={titel}><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">{taste}</kbd> {text}</span>
);

/** Fußleiste der Tagesansicht: Tastenkürzel bzw. Schreibschutz, Schließen und Speichern. */
export function TagEditorFuss({ readOnly, kopiert, speichert, gespeichert, speichernGesperrt, onSchliessen, onSpeichern }: Props) {
    return (
        <div className="bg-slate-50 p-4 border-t border-slate-200 flex items-center justify-between">
            {!readOnly ? (
                <div className="text-xs text-slate-500 flex items-center gap-4">
                    {kopiert && <span className="inline-flex items-center gap-1 bg-green-100 text-green-700 px-2 py-1 rounded"><Check className="h-3 w-3" aria-hidden="true" />Kopiert</span>}
                    <Taste taste="Strg+C" text="Kopieren" titel="Zeile kopieren" />
                    <Taste taste="Strg+V" text="Einfügen" titel="Einfügen" />
                    <Taste taste="Strg+D" text="Duplizieren" titel="Duplizieren mit anderer Tätigkeit" />
                </div>
            ) : (
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-amber-800 bg-amber-100 px-2.5 py-1 rounded-md">
                    <LockKeyhole className="w-3.5 h-3.5" />
                    Schreibgeschützt (abgeschlossener Monat)
                </span>
            )}
            <div className="flex gap-2">
                <Button variant="outline" onClick={onSchliessen} size="default">
                    Schließen
                </Button>
                {!readOnly && (
                    <Button onClick={onSpeichern} disabled={speichert || speichernGesperrt} className="bg-rose-600 hover:bg-rose-700 text-white">
                        {speichert ? (
                            <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> Speichern...</>
                        ) : gespeichert ? (
                            <><Save className="w-4 h-4 mr-2" /> Gespeichert!</>
                        ) : (
                            <><Save className="w-4 h-4 mr-2" /> Alle Speichern</>
                        )}
                    </Button>
                )}
            </div>
        </div>
    );
}
