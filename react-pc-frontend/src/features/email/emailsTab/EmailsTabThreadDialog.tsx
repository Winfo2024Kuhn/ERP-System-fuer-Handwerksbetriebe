import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import { ImageViewer } from '../../../components/ui/image-viewer';
import DocumentPreviewModal from '../../../components/DocumentPreviewModal';
import { EmailComposeForm } from '../../../components/EmailComposeForm';
import { EmailThreadView } from '../../../components/EmailThreadView';
import type { EmailThread, EmailThreadEntry } from '../../../components/EmailThreadView';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import type { EmailComposeKopfDaten } from '../compose/emailComposeAnzeige';
import { AntwortKopf, VerlaufKopf } from './EmailsTabThreadKopf';
import { useEmailsTabEntwurfLoeschen } from './useEmailsTabEntwurfLoeschen';
import type { EmailsTabReply } from './useEmailsTabReply';

export interface EmailsTabThreadDialogProps {
    subject: string;
    thread: EmailThread | null;
    threadLoading: boolean;
    showReplyButton: boolean;
    reply: EmailsTabReply;
    projektId?: number;
    anfrageId?: number;
    onClose: () => void;
    onReplySuccess: () => void;
    onReloadThread: () => void;
    onOpenDraft: (entry: EmailThreadEntry) => void;
}

interface Anhang { url: string; type: 'image' | 'pdf'; name: string }

/**
 * Verlauf im E-Mail-Reiter als gemeinsamer Dialog (Fokusfalle, Escape, Titel = Betreff)
 * mit Antwort-Formular und Anhang-Vorschau.
 *
 * Die Vorschau hängt im Dialog, damit Fokusfalle und Escape sie einschließen: Escape
 * schließt dann nur die Vorschau. Beim Schließen während einer Antwort wird der Entwurf
 * erst gespeichert; scheitert das, bleibt der Verlauf offen.
 *
 * Während einer Antwort stellt der Dialog den einzigen Kopf: Pfeil „Zurück zum Verlauf“,
 * Titel „Antwort“, Betreff darunter, rechts nur das X des Dialogs. Das Formular läuft
 * ohne eigenen Kopf (`kopf="ohne"`), damit nie zwei X übereinander stehen. Pfeil und X
 * sichern den Entwurf über dieselbe vom Formular registrierte Funktion. Das Formular meldet
 * den aktuellen Betreff und ob es gerade sendet oder speichert (dann ist der Pfeil gesperrt).
 *
 * Fokus beim Wechsel: Antwort beginnt → Pfeil; zurück im Verlauf → „Antworten“, sonst der Titel.
 */
export function EmailsTabThreadDialog(props: EmailsTabThreadDialogProps) {
    const { thread, reply, onClose, onReloadThread } = props;
    const titelId = useId();
    const [anhang, setAnhang] = useState<Anhang | null>(null);
    const speichernVorDemSchliessen = useRef<(() => Promise<boolean>) | null>(null);
    const verlassenLaeuft = useRef(false);
    const [kopfDaten, setKopfDaten] = useState<EmailComposeKopfDaten | null>(null);
    const pfeilRef = useRef<HTMLButtonElement>(null);
    const antwortenRef = useRef<HTMLButtonElement>(null);
    const titelRef = useRef<HTMLHeadingElement>(null);
    const merkeSpeichern = useCallback((speichern: (() => Promise<boolean>) | null) => {
        speichernVorDemSchliessen.current = speichern;
    }, []);
    const { sichtbarerVerlauf, entwurfLoeschen } = useEmailsTabEntwurfLoeschen(thread, onReloadThread);
    const toast = useToast();

    /** Nur sichere Adressen öffnen; sonst Meldung statt einer Vorschau, die nichts zeigt. */
    const vorschauOeffnen = (url: string, type: Anhang['type'], name: string) => {
        const sichereUrl = toSafeResourceUrl(url);
        if (!sichereUrl) {
            toast.error('Der Anhang kann nicht angezeigt werden. Bitte herunterladen.');
            return;
        }
        setAnhang({ url: sichereUrl, type, name });
    };

    /** Während einer Antwort erst den Entwurf sichern; scheitert das (oder ist es gesperrt), passiert nichts. */
    const nachDemSpeichern = (weiter: () => void) => {
        const speichern = reply.isReplying ? speichernVorDemSchliessen.current : null;
        if (!speichern) { weiter(); return; }
        if (verlassenLaeuft.current) return;
        verlassenLaeuft.current = true;
        void speichern()
            .then(gespeichert => { if (gespeichert) weiter(); })
            .finally(() => { verlassenLaeuft.current = false; });
    };

    const schliessenWollen = () => {
        if (anhang) { setAnhang(null); return; }
        nachDemSpeichern(onClose);
    };
    const zurueckZumVerlauf = () => nachDemSpeichern(reply.cancelReply);
    const antwortet = reply.isReplying && !!reply.replyToEmail;
    const beschreibungId = antwortet ? `${titelId}-betreff` : sichtbarerVerlauf ? `${titelId}-anzahl` : undefined;

    // Der geklickte Knopf verschwindet beim Wechsel; ohne das hier stünde der Fokus auf <body>.
    const antwortetVorher = useRef(antwortet);
    useEffect(() => {
        if (antwortetVorher.current === antwortet) return;
        antwortetVorher.current = antwortet;
        if (antwortet) pfeilRef.current?.focus();
        else (antwortenRef.current ?? titelRef.current)?.focus();
    }, [antwortet]);

    return (
        <Dialog
            open
            onOpenChange={offen => { if (!offen) schliessenWollen(); }}
            aria-labelledby={titelId}
            aria-describedby={beschreibungId}
            className="p-0 overflow-hidden w-[75vw] max-w-5xl h-[90vh]"
        >
            <DialogContent className="gap-0">
                {antwortet ? (
                    <AntwortKopf
                        titelId={titelId}
                        betreff={kopfDaten?.betreff ?? reply.replyInitialSubject}
                        gesperrt={kopfDaten?.gesperrt ?? null}
                        pfeilRef={pfeilRef}
                        onZurueck={zurueckZumVerlauf}
                    />
                ) : (
                    <VerlaufKopf
                        {...props}
                        titelId={titelId}
                        thread={sichtbarerVerlauf}
                        antwortenRef={antwortenRef}
                        titelRef={titelRef}
                    />
                )}

                <EmailsTabThreadContent
                    {...props}
                    thread={sichtbarerVerlauf}
                    onBeforeLeave={merkeSpeichern}
                    onKopfDaten={setKopfDaten}
                    onPreview={vorschauOeffnen}
                    onDeleteDraft={entwurfLoeschen}
                />
                <EmailsTabAnhangVorschau anhang={anhang} onClose={() => setAnhang(null)} />
            </DialogContent>
        </Dialog>
    );
}

interface ContentProps extends EmailsTabThreadDialogProps {
    onBeforeLeave: (speichern: (() => Promise<boolean>) | null) => void;
    onKopfDaten: (daten: EmailComposeKopfDaten | null) => void;
    onPreview: (url: string, type: 'image' | 'pdf', name: string) => void;
    onDeleteDraft: (draftId: number) => void;
}

function EmailsTabThreadContent({
    thread, threadLoading, showReplyButton, reply, projektId, anfrageId,
    onReplySuccess, onReloadThread, onBeforeLeave, onKopfDaten, onPreview, onOpenDraft, onDeleteDraft,
}: ContentProps) {
    if (reply.isReplying && reply.replyToEmail) {
        return (
            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <EmailComposeForm
                    onClose={reply.cancelReply}
                    onBeforeLeave={onBeforeLeave}
                    onKopfDaten={onKopfDaten}
                    onSuccess={onReplySuccess}
                    kopf="ohne"
                    schliessenText="Zurück zum Verlauf"
                    initialRecipient={reply.replyInitialRecipient}
                    initialSubject={reply.replyInitialSubject}
                    replyQuote={reply.replyQuote}
                    replyEmailId={reply.replyToEmailId}
                    projektId={projektId}
                    anfrageId={anfrageId}
                    variant="modal"
                />
            </div>
        );
    }
    if (threadLoading && !thread) return <EmailsTabThreadSkeleton />;
    if (thread) {
        return (
            <EmailThreadView
                thread={thread}
                onPreview={onPreview}
                onReply={showReplyButton ? (entry) => { void reply.startReply(entry); } : undefined}
                onOpenDraft={onOpenDraft}
                onDeleteDraft={onDeleteDraft}
            />
        );
    }
    return (
        <div className="flex-1 flex flex-col items-center justify-center gap-3 p-6">
            <p className="text-sm text-slate-500">Der Verlauf konnte nicht geladen werden.</p>
            <Button variant="outline" size="sm" onClick={onReloadThread}>
                <RefreshCw aria-hidden="true" className="w-4 h-4 mr-2" /> Erneut laden
            </Button>
        </div>
    );
}

/** Bilder im gemeinsamen ImageViewer, PDFs in der gemeinsamen Dokumentvorschau (URL schon geprüft). */
function EmailsTabAnhangVorschau({ anhang, onClose }: { anhang: Anhang | null; onClose: () => void }) {
    if (!anhang) return null;
    if (anhang.type === 'image') return <ImageViewer src={anhang.url} alt={anhang.name} onClose={onClose} />;
    return <DocumentPreviewModal doc={{ url: anhang.url, title: anhang.name }} isPdf onClose={onClose} />;
}

function EmailsTabThreadSkeleton() {
    return (
        <div aria-hidden="true" className="flex-1 overflow-auto bg-slate-50 px-6 py-6 space-y-2">
            {[false, true, false].map((right, i) => (
                <div key={i} className={`flex items-end gap-2 mb-2 ${right ? 'flex-row-reverse' : ''}`}>
                    <div className="w-8 h-8 rounded-full bg-slate-200 motion-safe:animate-pulse shrink-0" />
                    <div className={`motion-safe:animate-pulse bg-slate-200 rounded-2xl h-16 ${right ? 'rounded-br-sm w-56' : 'rounded-bl-sm w-64'}`} />
                </div>
            ))}
        </div>
    );
}
