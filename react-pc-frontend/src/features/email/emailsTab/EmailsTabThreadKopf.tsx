import { useEffect, useRef, type RefObject } from 'react';
import { ArrowLeft, Loader2, Reply } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { DialogDescription, DialogTitle } from '../../../components/ui/dialog';
import { emailComposeTitel } from '../compose/emailComposeAnzeige';
import { verlaufAnzahl } from './emailsTabFormat';
import type { EmailsTabThreadDialogProps } from './EmailsTabThreadDialog';

interface AntwortKopfProps {
    titelId: string;
    betreff: string;
    /** Grund, warum das Formular gerade nicht verlassen werden kann (Senden, Speichern …). */
    gesperrt: string | null;
    pfeilRef: RefObject<HTMLButtonElement | null>;
    onZurueck: () => void;
}

/** Kopf während einer Antwort; rechts bleibt Platz für das X des Dialogs. */
export function AntwortKopf({ titelId, betreff, gesperrt, pfeilRef, onZurueck }: AntwortKopfProps) {
    // Ein gesperrter Knopf verliert den Fokus; ging er dabei an <body>, kommt er danach zurück.
    const warGesperrt = useRef(false);
    useEffect(() => {
        const fokusVerloren = !document.activeElement || document.activeElement === document.body;
        if (warGesperrt.current && !gesperrt && fokusVerloren) pfeilRef.current?.focus();
        warGesperrt.current = !!gesperrt;
    }, [gesperrt, pfeilRef]);

    return (
        <div className="flex items-center gap-2 pl-2 pr-14 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
            <Button
                ref={pfeilRef}
                variant="ghost"
                size="sm"
                onClick={onZurueck}
                disabled={!!gesperrt}
                aria-busy={gesperrt ? true : undefined}
                aria-label="Zurück zum Verlauf"
                title={gesperrt ?? 'Zurück zum Verlauf'}
                className="shrink-0 px-2 text-slate-600 hover:text-rose-700"
            >
                <ArrowLeft aria-hidden="true" className="w-4 h-4" />
            </Button>
            <div className="min-w-0">
                <DialogTitle id={titelId} className="text-sm leading-normal text-slate-900">
                    {emailComposeTitel({ antwort: true, weiterleitung: false, betreff })}
                </DialogTitle>
                <DialogDescription id={`${titelId}-betreff`} className="text-xs truncate">{betreff}</DialogDescription>
            </div>
        </div>
    );
}

/** Kopf des Verlaufs: Betreff, Anzahl und „Antworten“ auf die letzte Nachricht. */
interface VerlaufKopfProps extends Pick<EmailsTabThreadDialogProps, 'subject' | 'thread' | 'showReplyButton' | 'reply'> {
    titelId: string;
    antwortenRef: RefObject<HTMLButtonElement | null>;
    titelRef: RefObject<HTMLHeadingElement | null>;
}

export function VerlaufKopf({ titelId, subject, thread, showReplyButton, reply, antwortenRef, titelRef }: VerlaufKopfProps) {
    return (
        <div className="flex items-center justify-between gap-3 pl-5 pr-14 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
            <div className="min-w-0">
                {/* tabIndex -1: Fokusziel nach einer Antwort, wenn es keinen „Antworten“-Knopf gibt */}
                <DialogTitle ref={titelRef} id={titelId} tabIndex={-1} className="text-sm leading-normal text-slate-900 truncate focus:outline-none">
                    {subject}
                </DialogTitle>
                {thread && (
                    <DialogDescription id={`${titelId}-anzahl`} className="text-xs">
                        {verlaufAnzahl(thread.emails)}
                    </DialogDescription>
                )}
            </div>
            {showReplyButton && !reply.isReplying && thread && thread.emails.length > 0 && (
                <Button
                    ref={antwortenRef}
                    variant="outline"
                    size="sm"
                    disabled={reply.replyLaedt}
                    aria-busy={reply.replyLaedt || undefined}
                    title={reply.replyLaedt ? 'Mail wird geladen…' : undefined}
                    onClick={() => { void reply.startReply(thread.emails[thread.emails.length - 1]); }}
                    className="gap-1.5 shrink-0 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                >
                    {reply.replyLaedt
                        ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                        : <Reply aria-hidden="true" className="w-4 h-4" />}
                    Antworten
                </Button>
            )}
        </div>
    );
}
