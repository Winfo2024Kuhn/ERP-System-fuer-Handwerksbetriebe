import { useRef, useState } from 'react';
import { ImagePlus, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { cn } from '../../lib/utils';
import { ERLAUBTE_BILDTYPEN, MAX_DATEIGROESSE_MB, pruefeBilddateien } from './bildUpload';

export interface BildUploadFeldProps {
    /** Bekommt nur geprüfte Bilddateien. */
    onDateien: (dateien: File[]) => void;
    /** Sperrt das Feld, z.B. während eines laufenden Uploads. */
    laedt?: boolean;
    /** Text unter der Überschrift, falls der Kontext mehr erklären muss. */
    hinweis?: string;
    /** Kompakte einzeilige Darstellung, z.B. neben vorhandenen Bildern. */
    kompakt?: boolean;
}

/**
 * Ablagefläche für Bilder von der Festplatte: Dateien hineinziehen oder per
 * Knopf aus dem Dateidialog wählen. Die Dateiauswahl bleibt der echte
 * Systemdialog, alles andere ist eigene Oberfläche.
 */
export function BildUploadFeld({ onDateien, laedt = false, hinweis, kompakt = false }: BildUploadFeldProps) {
    const toast = useToast();
    const eingabe = useRef<HTMLInputElement>(null);
    const [darueber, setDarueber] = useState(false);

    const verarbeite = (liste: FileList | null) => {
        if (!liste || liste.length === 0) return;
        const { gueltig, abgelehnt } = pruefeBilddateien(Array.from(liste));
        if (abgelehnt.length > 0) toast.error(abgelehnt.join(' '));
        if (gueltig.length > 0) onDateien(gueltig);
    };

    return (
        <div
            data-testid="bild-upload-feld"
            onDragOver={e => { e.preventDefault(); if (!laedt) setDarueber(true); }}
            onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDarueber(false); }}
            onDrop={e => {
                e.preventDefault();
                setDarueber(false);
                if (!laedt) verarbeite(e.dataTransfer.files);
            }}
            className={cn(
                'rounded-xl border-2 border-dashed transition-colors',
                kompakt ? 'flex items-center gap-4 px-4 py-3' : 'flex flex-col items-center gap-3 px-6 py-8 text-center',
                darueber ? 'border-rose-500 bg-rose-50' : 'border-slate-300 bg-slate-50',
                laedt && 'opacity-60',
            )}
        >
            <span className={cn(
                'flex items-center justify-center rounded-full bg-white text-rose-600 border border-slate-200 flex-shrink-0',
                kompakt ? 'w-10 h-10' : 'w-14 h-14',
            )}>
                {laedt
                    ? <Loader2 className="w-5 h-5 animate-spin" />
                    : <ImagePlus className={kompakt ? 'w-5 h-5' : 'w-7 h-7'} />}
            </span>
            <div className={kompakt ? 'flex-1 min-w-0' : undefined}>
                <p className="font-semibold text-slate-900">Bilder vom Computer hochladen</p>
                <p className="text-sm text-slate-500 mt-0.5">
                    {hinweis ?? `Hierher ziehen oder auswählen. JPG, PNG oder WebP, bis ${MAX_DATEIGROESSE_MB} MB pro Bild.`}
                </p>
            </div>
            <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={laedt}
                onClick={() => eingabe.current?.click()}
            >
                <ImagePlus className="w-4 h-4" />
                Bilder auswählen
            </Button>
            <input
                ref={eingabe}
                type="file"
                multiple
                accept={ERLAUBTE_BILDTYPEN.join(',')}
                aria-label="Bilddateien auswählen"
                className="hidden"
                onChange={e => {
                    verarbeite(e.target.files);
                    // Gleiche Datei darf danach erneut gewählt werden.
                    e.target.value = '';
                }}
            />
        </div>
    );
}
