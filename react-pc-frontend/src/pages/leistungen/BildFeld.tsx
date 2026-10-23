import { useEffect, useId, useMemo, useRef, useState, type ClipboardEvent, type DragEvent } from 'react';
import { ImagePlus, Trash2, Undo2, Upload } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import { BILD_TYPEN, einfuegenTaste, ersteDatei, pruefeBilddatei } from './ordnerLogik';

export interface BildFeldProps {
    /** Bild, das schon gespeichert ist. */
    gespeichertUrl: string | null;
    /** Neu gewählte, noch nicht gespeicherte Datei. */
    datei: File | null;
    /** Gespeichertes Bild soll beim Speichern entfernt werden. */
    entfernen: boolean;
    onDatei: (datei: File | null) => void;
    onEntfernen: (entfernen: boolean) => void;
}

const EINFUEGEN_TASTE = einfuegenTaste(typeof navigator === 'undefined' ? '' : navigator.platform);

/**
 * Bild eines Ordners: hineinziehen, einfügen (⌘V/Strg+V) oder per Klick
 * auswählen. Zeigt sofort eine Vorschau; gespeichert wird erst mit dem
 * „Speichern“ des Formulars. Nur Bilddateien bis 10 MB – sonst eine
 * verständliche Meldung direkt am Feld.
 */
export function BildFeld({ gespeichertUrl, datei, entfernen, onDatei, onEntfernen }: BildFeldProps) {
    const toast = useToast();
    const eingabeRef = useRef<HTMLInputElement>(null);
    const hinweisId = useId();
    const [fehler, setFehler] = useState<string | null>(null);
    const [drueber, setDrueber] = useState(false);
    const [dateiUrl, setDateiUrl] = useState<string | null>(null);

    useEffect(() => {
        // Vorschau-Adresse je Datei; beim Wechsel/Verlassen wieder freigeben.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        if (!datei) { setDateiUrl(null); return; }
        const url = URL.createObjectURL(datei);
        setDateiUrl(url);
        return () => URL.revokeObjectURL(url);
    }, [datei]);

    const vorschau = useMemo(() => {
        if (dateiUrl) return toSafeResourceUrl(dateiUrl);
        if (entfernen) return '';
        return toSafeResourceUrl(gespeichertUrl);
    }, [dateiUrl, entfernen, gespeichertUrl]);

    const uebernehmen = (kandidat: File | null) => {
        if (!kandidat) return;
        const meldung = pruefeBilddatei(kandidat);
        if (meldung) {
            setFehler(meldung);
            toast.error(meldung);
            return;
        }
        setFehler(null);
        onEntfernen(false);
        onDatei(kandidat);
    };

    const beiAblegen = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setDrueber(false);
        uebernehmen(ersteDatei(event.dataTransfer.files));
    };

    const beiEinfuegen = (event: ClipboardEvent<HTMLDivElement>) => {
        const kandidat = ersteDatei(event.clipboardData.files);
        if (!kandidat) return;
        event.preventDefault();
        uebernehmen(kandidat);
    };

    const oeffnen = () => eingabeRef.current?.click();

    return (
        <div className="flex flex-wrap items-start gap-4">
            <div
                role="button"
                tabIndex={0}
                aria-label={`Ordnerbild wählen – klicken, Bild hierher ziehen oder mit ${EINFUEGEN_TASTE} einfügen`}
                aria-describedby={hinweisId}
                onClick={oeffnen}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); oeffnen(); } }}
                onPaste={beiEinfuegen}
                onDragOver={(e) => {
                    if (!Array.from(e.dataTransfer.types).includes('Files')) return;
                    e.preventDefault();
                    e.dataTransfer.dropEffect = 'copy';
                    setDrueber(true);
                }}
                onDragLeave={() => setDrueber(false)}
                onDrop={beiAblegen}
                className={cn(
                    'relative flex h-36 w-36 shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-lg border-2 border-dashed transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2',
                    drueber ? 'border-rose-500 bg-rose-50' : 'border-slate-300 bg-slate-50 hover:border-rose-300 hover:bg-rose-50/50',
                )}
            >
                {vorschau ? (
                    <img src={vorschau} alt="Vorschau des Ordnerbilds" className="h-full w-full object-cover" />
                ) : (
                    <span className="flex flex-col items-center gap-1 px-2 text-center text-xs text-slate-500">
                        <ImagePlus className="h-7 w-7 text-slate-400" aria-hidden="true" />
                        Bild hierher ziehen
                    </span>
                )}
                {drueber && (
                    <span className="absolute inset-0 flex items-center justify-center bg-rose-50/90 text-sm font-medium text-rose-700">
                        Loslassen zum Übernehmen
                    </span>
                )}
            </div>

            <div className="min-w-0 flex-1 space-y-2 text-sm">
                <p id={hinweisId} className="text-slate-500">
                    Klicken, ein Foto hierher ziehen oder mit {EINFUEGEN_TASTE} einfügen. JPEG, PNG, GIF oder WebP, höchstens 10 MB.
                </p>
                <div className="flex flex-wrap gap-2">
                    <Button type="button" size="sm" variant="outline" onClick={oeffnen}>
                        <Upload className="h-4 w-4" aria-hidden="true" /> Bild wählen …
                    </Button>
                    {datei && (
                        <Button type="button" size="sm" variant="ghost" onClick={() => onDatei(null)}>
                            <Undo2 className="h-4 w-4" aria-hidden="true" /> Neues Bild verwerfen
                        </Button>
                    )}
                    {!datei && gespeichertUrl && !entfernen && (
                        <Button type="button" size="sm" variant="ghost" onClick={() => onEntfernen(true)}>
                            <Trash2 className="h-4 w-4" aria-hidden="true" /> Bild entfernen
                        </Button>
                    )}
                    {!datei && entfernen && (
                        <Button type="button" size="sm" variant="ghost" onClick={() => onEntfernen(false)}>
                            <Undo2 className="h-4 w-4" aria-hidden="true" /> Bild doch behalten
                        </Button>
                    )}
                </div>
                {datei && <p className="text-slate-600">Neues Bild „{datei.name}“ – wird beim Speichern übernommen.</p>}
                {!datei && entfernen && <p className="text-amber-700">Das Bild wird beim Speichern entfernt.</p>}
                {fehler && <p role="alert" className="text-red-600">{fehler}</p>}
            </div>

            <input
                ref={eingabeRef}
                type="file"
                accept={BILD_TYPEN.join(',')}
                className="hidden"
                tabIndex={-1}
                aria-hidden="true"
                onChange={(e) => { uebernehmen(ersteDatei(e.target.files)); e.target.value = ''; }}
            />
        </div>
    );
}
