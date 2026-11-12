import { File, FileImage, FileText } from 'lucide-react';
import type { Dateiart } from './typen';

/** Lucide-Symbol je Dateiart – in Tabelle und Kacheln gleich. Immer nur Schmuck (`aria-hidden`). */
export function DateiSymbol({ dateiart, className, strokeWidth }: { dateiart: Dateiart; className?: string; strokeWidth?: number }) {
    if (dateiart === 'PDF') return <FileText className={className} strokeWidth={strokeWidth} aria-hidden="true" />;
    if (dateiart === 'BILD') return <FileImage className={className} strokeWidth={strokeWidth} aria-hidden="true" />;
    return <File className={className} strokeWidth={strokeWidth} aria-hidden="true" />;
}
