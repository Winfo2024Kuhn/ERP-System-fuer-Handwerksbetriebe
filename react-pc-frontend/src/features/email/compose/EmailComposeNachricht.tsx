import type { RefObject } from 'react';
import { Label } from '../../../components/ui/label';
import { KiStilMenue } from '../KiStilMenue';
import { MailFormatLeiste } from '../editor/MailFormatLeiste';
import { MailTextFeld } from '../editor/MailTextFeld';
import { useMailEditor } from '../editor/useMailEditor';
import { hatZitiertenVerlauf, type KiStil } from '../mailEditorHtml';

interface EmailComposeNachrichtProps {
    inline: boolean;
    editorRef: RefObject<HTMLDivElement | null>;
    beautifying: boolean;
    aktuellesHtml: () => string;
    onFormulieren: (stil: KiStil) => void;
    onInput: () => void;
}

/**
 * Karte „Nachricht“ des E-Mail-Formulars. Formatierungsleiste und KI-Menü
 * teilen sich die Kopfzeile, damit das Feld keinen Platz nach unten verliert.
 */
export function EmailComposeNachricht({ inline, editorRef, beautifying, aktuellesHtml, onFormulieren, onInput }: EmailComposeNachrichtProps) {
    const editor = useMailEditor({ editorRef });
    return (
        <div className={inline ? "rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden flex flex-col" : "rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden flex flex-col min-h-[320px]"}>
            <div className={inline ? "border-b border-slate-200 bg-slate-50 px-3 py-1.5 flex flex-wrap items-center justify-between gap-2" : "border-b border-slate-200 bg-slate-50 px-5 py-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"}>
                <div>
                    <Label className="text-slate-900">Nachricht</Label>
                    <p className={inline ? "hidden" : "text-xs text-slate-500 mt-1"}>Formulieren, überarbeiten und anschließend direkt versenden.</p>
                </div>
                <div className="flex min-w-0 flex-nowrap items-center gap-1">
                    <MailFormatLeiste editor={editor} />
                    <span aria-hidden="true" className="mx-0.5 h-5 w-px shrink-0 bg-slate-200" />
                    <KiStilMenue
                        isLoading={beautifying}
                        hatVerlauf={() => hatZitiertenVerlauf(aktuellesHtml())}
                        onWaehlen={onFormulieren}
                    />
                </div>
            </div>
            <div className={inline ? "flex-1 p-2 bg-white" : "flex-1 p-4 bg-white"}>
                <MailTextFeld
                    editor={editor}
                    ariaLabel="Nachricht"
                    className={`h-full ${inline ? 'min-h-[200px] p-3' : 'min-h-[240px] p-4'} rounded-xl border border-slate-200 overflow-auto focus-within:border-rose-300 [&_p]:min-h-[1.25em] [&_p]:my-0 [&>p+p]:mt-2 [&>div+p]:mt-2 [&>p+div]:mt-2`}
                    onInput={onInput}
                />
            </div>
        </div>
    );
}
