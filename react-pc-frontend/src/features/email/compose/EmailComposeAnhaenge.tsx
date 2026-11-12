import { useRef, useState, type DragEvent } from 'react';
import { Eye, FolderOpen, Loader2, Paperclip, Upload } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import type { ProjektDokument } from '../../../types';
import { EigeneAnhaenge, WeiterleitungsAnhaenge } from './AnhangListe';
import { MAX_ATTACHMENT_BYTES, formatFileSize, type UploadedFile } from './anhangGrenze';
import { VorgangDateienDialog } from './VorgangDateienDialog';
import type { EmailAnhaenge } from './useEmailAnhaenge';
import type { AnhangVorschauStand } from './useAnhangVorschau';
import type { OriginalMail } from './useOriginalMail';

interface AutomatischAngehaengtProps {
    beschriftung: string;
    vorschauGesperrt?: string;
    erstesPdf?: UploadedFile;
    onPdfVorschau: (datei: File) => void;
}

/** Hinweis „Automatisch angehängt“ (z. B. Rechnung aus dem Dokument-Editor) mit PDF-Vorschau. */
export function AutomatischAngehaengt({ beschriftung, vorschauGesperrt, erstesPdf, onPdfVorschau }: AutomatischAngehaengtProps) {
    return (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 shadow-sm">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <p className="text-sm font-semibold text-rose-700 flex items-center gap-2">
                        <Paperclip className="w-4 h-4" />
                        Automatisch angehängt
                    </p>
                    <p className="mt-1 text-sm text-rose-700/90 break-words">{beschriftung}</p>
                </div>
                {vorschauGesperrt ? (
                    <p className="max-w-sm text-xs text-rose-700/90" data-testid="anhang-vorschau-gesperrt">{vorschauGesperrt}</p>
                ) : erstesPdf && (
                    <Button type="button" variant="outline" size="sm" onClick={() => onPdfVorschau(erstesPdf.file)}
                        className="border-rose-300 text-rose-700 hover:bg-white">
                        <Eye className="w-4 h-4 mr-2" />
                        PDF-Vorschau öffnen
                    </Button>
                )}
            </div>
        </div>
    );
}

interface EmailComposeAnhaengeProps {
    inline: boolean;
    anhaenge: EmailAnhaenge;
    dateien: UploadedFile[];
    vorgang?: { anfrage: boolean; dokumente: ProjektDokument[]; bautagebuch: ProjektDokument[]; laedt: boolean };
    original?: OriginalMail;
    pflichtanhangHinweis?: string;
    ohneVorschau: (datei: File) => boolean;
    vorschau: AnhangVorschauStand;
}

/** Karte „Anhänge“: Dateien aus dem Vorgang, Ablegen/Hochladen, Größengrenze, Listen. */
export function EmailComposeAnhaenge({ inline, anhaenge, dateien, vorgang, original, pflichtanhangHinweis, ohneVorschau, vorschau }: EmailComposeAnhaengeProps) {
    const dateiAuswahl = useRef<HTMLInputElement>(null);
    const [vorgangDateienOffen, setVorgangDateienOffen] = useState(false);
    const ziehen = (event: DragEvent) => { event.preventDefault(); event.stopPropagation(); };
    const ablegen = (event: DragEvent) => { ziehen(event); void anhaenge.hochladen(event.dataTransfer.files); };

    return (
        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className={inline ? "border-b border-slate-100 bg-slate-50/80 px-3 py-2" : "border-b border-slate-100 bg-slate-50/80 px-5 py-3"}>
                <p className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                    <Upload className="w-4 h-4 text-rose-600" />
                    Anhänge
                </p>
                <p className={inline ? "hidden" : "text-xs text-slate-500 mt-1"}>Zusätzliche Dateien per Klick oder Drag & Drop anhängen.</p>
            </div>
            <div className={inline ? "p-3 space-y-2" : "p-5 space-y-3"}>
                {vorgang && (
                    <Button type="button" variant="outline" onClick={() => setVorgangDateienOffen(true)}
                        className="min-h-11 border-rose-200 text-rose-700 hover:bg-rose-50">
                        <FolderOpen className="mr-2 h-4 w-4" />
                        Dateien aus {vorgang.anfrage ? 'Anfrage' : 'Projekt'} hinzufügen
                    </Button>
                )}
                {vorgang && vorgangDateienOffen && (
                    <VorgangDateienDialog
                        anfrage={vorgang.anfrage} dokumente={vorgang.dokumente} bautagebuch={vorgang.bautagebuch} laedt={vorgang.laedt}
                        gewaehlteIds={anhaenge.gewaehlteDokumentIds} ladendeIds={anhaenge.ladendeDokumentIds}
                        onUmschalten={(dokument) => { void anhaenge.dokumentUmschalten(dokument); }}
                        onSchliessen={() => setVorgangDateienOffen(false)}
                    />
                )}
                <div
                    onDrop={ablegen}
                    onDragOver={ziehen}
                    className="border-2 border-dashed border-slate-300 rounded-xl p-5 text-center hover:border-rose-400 hover:bg-rose-50/50 transition-colors cursor-pointer"
                    onClick={() => dateiAuswahl.current?.click()}
                >
                    {anhaenge.komprimiere ? (
                        <>
                            <Loader2 className="w-8 h-8 mx-auto text-rose-500 mb-2 animate-spin" />
                            <p className="text-sm font-medium text-slate-700">Fotos werden verkleinert …</p>
                            <p className="text-xs text-slate-500 mt-1">Große Bilder passen sonst nicht in die E-Mail.</p>
                        </>
                    ) : (
                        <>
                            <Upload className={inline ? "hidden" : "w-8 h-8 mx-auto text-slate-400 mb-2"} />
                            <p className="text-sm font-medium text-slate-700">Dateien hier ablegen oder klicken</p>
                            <p className={inline ? "hidden" : "text-xs text-slate-500 mt-1"}>PDFs, Bilder und weitere Dokumente werden direkt als Anhang hinzugefügt. Große Fotos verkleinern wir automatisch.</p>
                        </>
                    )}
                </div>
                <input ref={dateiAuswahl} type="file" multiple className="hidden" onChange={(event) => void anhaenge.hochladen(event.target.files)} />

                <div className="space-y-1">
                    <div className="flex flex-wrap items-center justify-between gap-1 text-xs">
                        <span className={anhaenge.limitUeberschritten ? 'font-medium text-red-600' : 'text-slate-500'}>
                            {formatFileSize(anhaenge.bytes)} von {formatFileSize(MAX_ATTACHMENT_BYTES)}
                        </span>
                        <span className="text-slate-400">T-Online-kompatibles Sicherheitslimit</span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                        <div
                            className={`h-full rounded-full ${anhaenge.limitUeberschritten ? 'bg-red-500' : 'bg-rose-500'}`}
                            style={{ width: `${Math.min(100, (anhaenge.bytes / MAX_ATTACHMENT_BYTES) * 100)}%` }}
                        />
                    </div>
                </div>

                {pflichtanhangHinweis && (
                    <p role="alert" data-testid="pflichtanhang-fehlt" className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                        {pflichtanhangHinweis}
                    </p>
                )}
                {original && (original.laedt || original.anhaenge.length > 0) && <WeiterleitungsAnhaenge original={original} />}
                <EigeneAnhaenge dateien={dateien} ohneVorschau={ohneVorschau} onVorschau={vorschau.zeigeDatei} onEntfernen={anhaenge.entfernen} />
            </div>
        </div>
    );
}
