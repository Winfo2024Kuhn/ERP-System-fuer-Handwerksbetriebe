import { Eye, Loader2, X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../../../components/ui/info-button';
import { MAX_ATTACHMENT_BYTES, formatFileSize, isImageAttachment, isPdfAttachment, type UploadedFile } from './anhangGrenze';
import type { OriginalMail } from './useOriginalMail';

/** Anhänge der Original-Mail bei einer Weiterleitung: einzeln abwählbar. */
export function WeiterleitungsAnhaenge({ original }: { original: OriginalMail }) {
    return (
        <div className="mt-2 space-y-2" data-testid="weiterleitung-anhaenge">
            <div className="flex items-center gap-1">
                <h4 className="text-sm font-medium text-slate-700">Anhänge der Original-Mail</h4>
                <InfoButton titel="Anhänge der Original-Mail">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Die Dateien werden beim Senden direkt aus der ursprünglichen E-Mail angehängt – Sie müssen nichts herunter- oder hochladen.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Der Empfänger bekommt die Anhänge wie in der Original-Mail. Entfernen Sie hier nur,
                            was er nicht bekommen soll; die Original-Mail bleibt unverändert.
                        </p>
                        <p>
                            Diese Auswahl wird nicht im Entwurf gespeichert: Öffnen Sie den Entwurf später
                            wieder, sind alle Anhänge erneut ausgewählt.
                        </p>
                        <p>
                            Die Anhänge zählen zur Größengrenze unten: zusammen mit eigenen Anhängen
                            höchstens {formatFileSize(MAX_ATTACHMENT_BYTES)}.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            {original.laedt ? (
                <p role="status" className="flex items-center gap-2 text-xs text-slate-500">
                    <Loader2 className="w-3.5 h-3.5 motion-safe:animate-spin" />
                    Anhänge der Original-Mail werden geladen …
                </p>
            ) : (
                <>
                    {original.mitgesendet.map(anhang => {
                        const name = anhang.originalFilename || anhang.filename || 'Anhang';
                        return (
                            <div key={anhang.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between gap-3">
                                <div className="min-w-0">
                                    <span className="text-sm font-medium text-slate-700 block truncate">{name}</span>
                                    {anhang.fileSize != null && <span className="text-xs text-slate-500">{formatFileSize(anhang.fileSize)}</span>}
                                </div>
                                <Button type="button" variant="ghost" size="sm" onClick={() => original.entfernen(anhang.id)}
                                    aria-label={`${name} entfernen`} title="Nicht mitsenden">
                                    <X className="w-4 h-4" />
                                </Button>
                            </div>
                        );
                    })}
                    {original.entfernteIds.size > 0 && (
                        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                            <span>
                                {original.entfernteIds.size === 1
                                    ? '1 Anhang wird nicht mitgesendet.'
                                    : `${original.entfernteIds.size} Anhänge werden nicht mitgesendet.`}
                            </span>
                            <Button type="button" variant="ghost" size="sm" onClick={original.alleWiederAnhaengen}>Wieder anhängen</Button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}

interface EigeneAnhaengeProps {
    dateien: UploadedFile[];
    /** Für diese Dateien keine Vorschau (z. B. ungebuchte Rechnung). */
    ohneVorschau: (datei: File) => boolean;
    onVorschau: (datei: File) => void;
    onEntfernen: (id: string) => void;
}

/** Eigene Anhänge mit Größe, Vorschau (PDF, Bild) und Entfernen. */
export function EigeneAnhaenge({ dateien, ohneVorschau, onVorschau, onEntfernen }: EigeneAnhaengeProps) {
    if (dateien.length === 0) return null;
    return (
        <div className="space-y-2 mt-2">
            {dateien.map(eintrag => (
                <div key={eintrag.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                        <span className="text-sm font-medium text-slate-700 block truncate">{eintrag.file.name}</span>
                        <span className="text-xs text-slate-500">{formatFileSize(eintrag.file.size)}</span>
                    </div>
                    <div className="flex items-center gap-1">
                        {(isPdfAttachment(eintrag.file) || isImageAttachment(eintrag.file)) && !ohneVorschau(eintrag.file) && (
                            <Button type="button" variant="ghost" size="sm" onClick={() => onVorschau(eintrag.file)}
                                title="Vorschau" aria-label={`Vorschau für ${eintrag.file.name}`}>
                                <Eye className="w-4 h-4" />
                            </Button>
                        )}
                        <Button type="button" variant="ghost" size="sm" onClick={() => onEntfernen(eintrag.id)} aria-label={`${eintrag.file.name} entfernen`}>
                            <X className="w-4 h-4" />
                        </Button>
                    </div>
                </div>
            ))}
        </div>
    );
}
