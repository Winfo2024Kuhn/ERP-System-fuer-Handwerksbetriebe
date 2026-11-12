import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { EmailZuordnungSearchModal, type EmailZuordnung } from './EmailZuordnungSearchModal';
import { useToast } from './ui/toast';
import { parseRecipientList } from '../lib/emailAddress';
import { useEmailDraft } from '../features/email/useEmailDraft';
import type { EmailDraftSnapshot } from '../features/email/emailDraftPersistence';
import { einzelneEmpfaenger } from '../features/email/einzelversand';
import { EinzelversandErgebnisAnzeige } from '../features/email/EinzelversandSchalter';
import { EmailComposeKopf } from '../features/email/compose/EmailComposeKopf';
import { EmailComposeFusszeile } from '../features/email/compose/EmailComposeFusszeile';
import { EmailComposeDetails } from '../features/email/compose/EmailComposeDetails';
import { EmailComposeNachricht } from '../features/email/compose/EmailComposeNachricht';
import { AutomatischAngehaengt, EmailComposeAnhaenge } from '../features/email/compose/EmailComposeAnhaenge';
import { AnhangVorschau } from '../features/email/compose/AnhangVorschau';
import { AdresseSpeichernDialog, ZuordnungWarnungDialog } from '../features/email/compose/EmailComposeRueckfragen';
import { EinzelversandTeilfehler, EntwurfLadenAnzeige } from '../features/email/compose/EmailComposeZustaende';
import { emailComposeTitel, emailComposeUntertitel, sendenSperre, verlassenSperrGrund } from '../features/email/compose/emailComposeAnzeige';
import type { EmailComposeFormProps } from '../features/email/compose/emailComposeTypen';
import { neuerAnhang, type UploadedFile } from '../features/email/compose/anhangGrenze';
import { useVorgangDaten } from '../features/email/compose/useVorgangDaten';
import { useVorgangDateien } from '../features/email/compose/useVorgangDateien';
import { useEmpfaenger } from '../features/email/compose/useEmpfaenger';
import { useAbsender } from '../features/email/compose/useAbsender';
import { useOriginalMail } from '../features/email/compose/useOriginalMail';
import { useNachricht } from '../features/email/compose/useNachricht';
import { useEntwurfLaden } from '../features/email/compose/useEntwurfLaden';
import { useEmailAnhaenge } from '../features/email/compose/useEmailAnhaenge';
import { useAnhangVorschau } from '../features/email/compose/useAnhangVorschau';
import { useAdresseSpeichern } from '../features/email/compose/useAdresseSpeichern';
import { useEmailVersand } from '../features/email/compose/useEmailVersand';

/**
 * E-Mail schreiben, antworten, weiterleiten. Setzt nur zusammen: Teile und
 * Logik liegen in `features/email/compose/` (Empfänger, Nachricht, Anhänge,
 * Versand, Entwurf), das Nachrichtenfeld in `features/email/editor/`.
 */
export function EmailComposeForm(props: EmailComposeFormProps) {
    const {
        onClose, onBeforeLeave, projektId, projekt, anfrageId, anfrage, kundeId, initialRecipient = '', initialCc,
        initialSubject = '', initialBody = '', replyQuote, replyEmailId: initialReplyEmailId,
        weitergeleitetVonEmailId: initialWeitergeleitetVonEmailId, weiterleitungsAnhaenge, antwortPostfach,
        initialAttachments, geschaeftsdokument: initialGeschaeftsdokument = false, onSuccess, draftId: initialDraftId,
        zuordnungWaehlbar = false, variant = 'default', kopf = 'eigen', schliessenText = 'Schließen', onKopfDaten,
        vorDemSenden, anhangVorschauGesperrt, entwurfSpeichern = true, freigabenAbloesenFuerDokumentId,
        pflichtanhangHinweis, erfolgsMeldung = 'E-Mail wurde gesendet.',
    } = props;
    const inline = variant !== 'modal';
    const toast = useToast();
    const [replyEmailId, setReplyEmailId] = useState(initialReplyEmailId);
    /** Weiterleitung – auch aus einem gespeicherten Entwurf, damit der Absender fest bleibt. */
    const [weitergeleitetVonEmailId, setWeitergeleitetVonEmailId] = useState(initialWeitergeleitetVonEmailId);
    const [geschaeftsdokument, setGeschaeftsdokument] = useState(initialGeschaeftsdokument);
    const [closing, setClosing] = useState(false);
    // Startwert aus dem Kontext (Projekt-/Anfrage-Seite), im Formular jederzeit änderbar.
    const [zuordnung, setZuordnung] = useState<EmailZuordnung | null>(() => {
        if (projektId) return { typ: 'PROJEKT', id: projektId, titel: projekt?.bauvorhaben || '' };
        if (anfrageId) return { typ: 'ANFRAGE', id: anfrageId, titel: anfrage?.bauvorhaben || '' };
        return null;
    });
    const [showZuordnungSuche, setShowZuordnungSuche] = useState(false);
    const [subject, setSubject] = useState<string>(initialSubject || '');
    const [error, setError] = useState<string | null>(null);
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>(() => (initialAttachments ?? []).map(file => neuerAnhang(file)));
    const editorRef = useRef<HTMLDivElement>(null);
    // Der Entwurf braucht alle Felder, die Felder brauchen „geändert“ – darum über eine Ref verbunden.
    const markDirtyRef = useRef<() => void>(() => undefined);
    const markDirty = useCallback(() => markDirtyRef.current(), []);

    const vorgang = useVorgangDaten(zuordnung, { projektId, projekt, anfrageId, anfrage });
    const vorgangDateien = useVorgangDateien(vorgang.entityId, vorgang.isAnfrageContext, setError);
    const empfaenger = useEmpfaenger({
        initialRecipient, initialCc, replyEmailId, istAntwortBeimOeffnen: !!initialReplyEmailId,
        kundenEmails: vorgang.kundenEmails, markDirty,
    });
    const absenderFest = !!replyEmailId || !!weitergeleitetVonEmailId;
    const absender = useAbsender({ geschaeftsdokument, absenderFest });
    const original = useOriginalMail({
        originalEmailId: replyEmailId ?? weitergeleitetVonEmailId, weitergeleitetVonEmailId,
        bekanntesAntwortPostfach: antwortPostfach, bekannteAnhaenge: weiterleitungsAnhaenge,
    });
    const nachricht = useNachricht({
        editorRef, initialBody, replyQuote, initialDraftId, vorgangName: vorgang.entityName, markDirty, setError,
    });
    const anhaenge = useEmailAnhaenge({ uploadedFiles, setUploadedFiles, weiterleitungsBytes: original.bytes, markDirty, setError });
    const vorschau = useAnhangVorschau();

    const reportDraftError = useCallback((message: string) => {
        setError(message);
        toast.error(message);
    }, [toast]);
    const entwurfAnwenden = ({ content, files }: EmailDraftSnapshot) => {
        empfaenger.setRecipient(content.recipient ?? initialRecipient);
        setSubject(content.subject ?? initialSubject);
        nachricht.entwurfEinsetzen(content.body ?? initialBody);
        const gespeicherterEinzelversand = !!content.einzelversand;
        // Einzelversand und CC schließen sich aus (Backend lehnt sonst mit 400 ab).
        const cc = gespeicherterEinzelversand ? [] : parseRecipientList(content.cc).map(r => r.raw);
        empfaenger.setCcRecipients(cc);
        empfaenger.setShowCc(cc.length > 0);
        // Aus bei mehreren Empfängern ohne CC kann nur von Hand ausgeschaltet worden sein – diese Wahl bleibt.
        const handAusgeschaltet = !gespeicherterEinzelversand && cc.length === 0
            && einzelneEmpfaenger(content.recipient ?? initialRecipient).length >= 2;
        empfaenger.setEinzelversandWahl(gespeicherterEinzelversand ? true : handAusgeschaltet ? false : null);
        if (content.postfachId != null) absender.setPostfachId(content.postfachId);
        setReplyEmailId(content.replyEmailId ?? initialReplyEmailId);
        setWeitergeleitetVonEmailId(content.weitergeleitetVonEmailId ?? initialWeitergeleitetVonEmailId ?? undefined);
        setGeschaeftsdokument(content.geschaeftsdokument ?? initialGeschaeftsdokument);
        if (content.projektId) setZuordnung({ typ: 'PROJEKT', id: content.projektId, titel: '' });
        else if (content.anfrageId) setZuordnung({ typ: 'ANFRAGE', id: content.anfrageId, titel: '' });
        else if ('projektId' in content) setZuordnung(null);
        setUploadedFiles(files.map(file => neuerAnhang(file)));
        empfaenger.setEmpfaengerAutomatisch(false);
    };
    const entwurf = useEntwurfLaden(initialDraftId, entwurfAnwenden, reportDraftError);

    const isAnfrageContext = vorgang.isAnfrageContext;
    const entityId = vorgang.entityId;
    const draftSnapshot = useMemo(() => ({
        content: {
            recipient: empfaenger.recipient.trim(), cc: empfaenger.ccRecipients.filter(c => c.trim()).join(', '),
            subject: subject.trim(), body: nachricht.body,
            fromAddress: absender.modus === 'auswahl' ? absender.gewaehltes?.emailAdresse ?? null : null,
            postfachId: absender.modus === 'auswahl' ? absender.postfachId : null,
            einzelversand: empfaenger.einzelversandAktiv,
            replyEmailId: replyEmailId || null,
            weitergeleitetVonEmailId: weitergeleitetVonEmailId || null,
            projektId: !isAnfrageContext && entityId ? entityId : null,
            anfrageId: isAnfrageContext && entityId ? entityId : null,
            geschaeftsdokument,
        },
        files: uploadedFiles.map(entry => entry.file),
    }), [empfaenger.recipient, empfaenger.ccRecipients, subject, nachricht.body, absender.modus, absender.gewaehltes,
        absender.postfachId, empfaenger.einzelversandAktiv, replyEmailId, weitergeleitetVonEmailId, isAnfrageContext,
        entityId, geschaeftsdokument, uploadedFiles]);
    const draft = useEmailDraft(draftSnapshot, initialDraftId,
        entwurfSpeichern && !entwurf.laedt && !entwurf.fehler && !anhaenge.komprimiere, reportDraftError);
    useEffect(() => { markDirtyRef.current = draft.markDirty; }, [draft.markDirty]);

    const fertig = () => { onSuccess?.(); onClose(); };
    const adresse = useAdresseSpeichern({ kundeId, zuordnung, fertig });
    const pflichtanhangFehlt = !!pflichtanhangHinweis && !!initialAttachments
        && initialAttachments.some(datei => !uploadedFiles.some(uf => uf.file === datei));
    const versand = useEmailVersand({
        empfaenger, subject, editorHtml: nachricht.aktuellesHtml, uploadedFiles, anhaengeZuGross: anhaenge.limitUeberschritten,
        pflichtanhangFehlt, pflichtanhangHinweis, zuordnungWaehlbar, zuordnung, replyEmailId, weitergeleitetVonEmailId,
        weiterleitungsAnhangIds: original.mitgesendet.map(anhang => anhang.id), geschaeftsdokument,
        postfachId: absender.modus === 'auswahl' ? absender.postfachId : null, bekannteAdressen: vorgang.availableEmails,
        draft, erfolgsMeldung, setError, adresseAnbieten: adresse.anbieten, kundeId, vorDemSenden, fertig,
        freigabenAbloesenFuerDokumentId,
    });

    /** Senden, Speichern, Bildverkleinern oder Dokument-Laden läuft: weder schließen noch zurück. */
    const verlassenGesperrt = versand.sending || closing || anhaenge.komprimiere || anhaenge.ladendeDokumentIds.size > 0;
    const verlassenGrund = verlassenSperrGrund({
        sendet: versand.sending, speichert: closing, verkleinert: anhaenge.komprimiere,
        laedtDokumente: anhaenge.ladendeDokumentIds.size > 0, laedtEntwurf: entwurf.laedt,
    });
    useEffect(() => { onKopfDaten?.({ betreff: subject, gesperrt: verlassenGrund }); }, [onKopfDaten, subject, verlassenGrund]);
    useEffect(() => () => onKopfDaten?.(null), [onKopfDaten]);
    const flushDraft = draft.flush;
    const saveBeforeLeave = useCallback(async () => {
        if (verlassenGrund) return false;
        setClosing(true);
        try { await flushDraft(); return true; }
        catch { return false; }
        finally { setClosing(false); }
    }, [verlassenGrund, flushDraft]);
    const saveBeforeLeaveRef = useRef(saveBeforeLeave);
    useEffect(() => { saveBeforeLeaveRef.current = saveBeforeLeave; }, [saveBeforeLeave]);
    useEffect(() => {
        onBeforeLeave?.(() => saveBeforeLeaveRef.current());
        return () => onBeforeLeave?.(null);
    }, [onBeforeLeave]);

    const handleClose = async () => {
        if (verlassenGesperrt) return;
        setClosing(true);
        try {
            await draft.flush();
            await draft.pause();
            onClose();
        } catch {
            // flush already reports the failure; retain the complete local draft for retry.
        } finally { setClosing(false); }
    };

    const senden = sendenSperre({
        beschaeftigt: verlassenGesperrt, originalAnhaengeLaden: original.laedt,
        empfaengerFehlt: !empfaenger.recipient.trim() && !empfaenger.anEntwurfVorhanden, betreffFehlt: !subject.trim(),
        anhaengeZuGross: anhaenge.limitUeberschritten, zuVieleEmpfaenger: empfaenger.zuVieleEmpfaenger, pflichtanhangFehlt,
    }, pflichtanhangHinweis);

    if (entwurf.laedt || entwurf.fehler) {
        return <EntwurfLadenAnzeige laedt={entwurf.laedt} fehler={entwurf.fehler} onErneut={entwurf.erneutLaden} onSchliessen={onClose} />;
    }
    const ergebnis = empfaenger.einzelversandErgebnis;
    if (ergebnis && ergebnis.verschickt > 0) return <EinzelversandTeilfehler ergebnis={ergebnis} onFertig={fertig} />;

    const automatisch = initialAttachments ?? [];
    return (
        <div className="flex min-w-0 flex-col h-full bg-slate-50">
            {kopf === 'eigen' && (
                <EmailComposeKopf
                    titel={emailComposeTitel({ antwort: !!replyEmailId, weiterleitung: !!weitergeleitetVonEmailId, betreff: initialSubject || subject })}
                    untertitel={emailComposeUntertitel({
                        vorgang: vorgang.entityName ? { typ: isAnfrageContext ? 'Anfrage' : 'Projekt', name: vorgang.entityName } : undefined,
                        betreff: initialSubject || subject,
                    })}
                    onSchliessen={() => { void handleClose(); }}
                    schliessenGesperrt={verlassenGesperrt}
                />
            )}

            <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-4" inert={versand.sending || closing}>
                {error && <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">{error}</div>}
                {ergebnis && <EinzelversandErgebnisAnzeige ergebnis={ergebnis} className="mx-auto w-full max-w-5xl" />}

                <div className={inline ? "mx-auto w-full max-w-5xl space-y-3" : "mx-auto w-full max-w-5xl space-y-5"}>
                    {automatisch.length > 0 && (
                        <AutomatischAngehaengt
                            beschriftung={automatisch.length === 1 ? automatisch[0].name : `${automatisch.length} Dateien automatisch angehängt`}
                            vorschauGesperrt={anhangVorschauGesperrt}
                            erstesPdf={uploadedFiles.find(eintrag => eintrag.file.type === 'application/pdf')}
                            onPdfVorschau={vorschau.zeigePdf}
                        />
                    )}
                    <EmailComposeDetails
                        inline={inline} empfaenger={empfaenger} vorschlaege={vorgang.availableEmails}
                        empfaengerFest={!!replyEmailId && !!initialRecipient.trim()}
                        absender={absender} absenderFest={absenderFest} weiterleitung={!!weitergeleitetVonEmailId}
                        festesPostfach={original.festesPostfach} geschaeftsdokument={geschaeftsdokument}
                        subject={subject} onSubject={setSubject} markDirty={markDirty}
                        zuordnung={zuordnungWaehlbar ? {
                            wert: zuordnung, name: vorgang.entityName, antwort: !!replyEmailId,
                            onSuchen: () => setShowZuordnungSuche(true), onEntfernen: () => setZuordnung(null),
                        } : undefined}
                    />
                    <EmailComposeNachricht
                        inline={inline} editorRef={editorRef} beautifying={nachricht.beautifying}
                        aktuellesHtml={nachricht.aktuellesHtml} onFormulieren={(stil) => void nachricht.formuliere(stil)}
                        onInput={nachricht.beimTippen}
                    />
                    <EmailComposeAnhaenge
                        inline={inline} anhaenge={anhaenge} dateien={uploadedFiles} vorschau={vorschau}
                        vorgang={entityId ? { anfrage: isAnfrageContext, ...vorgangDateien } : undefined}
                        original={weitergeleitetVonEmailId ? original : undefined}
                        pflichtanhangHinweis={pflichtanhangFehlt ? pflichtanhangHinweis : undefined}
                        ohneVorschau={(datei) => !!anhangVorschauGesperrt && automatisch.includes(datei)}
                    />
                </div>
            </div>
            <AnhangVorschau vorschau={vorschau} />

            <EmailComposeFusszeile
                entwurfHinweis={entwurfSpeichern ? draft.label : 'Wird nicht als Entwurf gespeichert'}
                entwurfFehler={draft.status === 'error'}
                onErneutSpeichern={() => { void draft.flush().catch(() => undefined); }}
                zurueckText={schliessenText}
                speichert={closing}
                zurueckGesperrt={verlassenGesperrt}
                onZurueck={() => { void handleClose(); }}
                sendet={versand.sending}
                sendenGesperrt={senden.gesperrt}
                sendenHinweis={senden.hinweis}
                onSenden={versand.handleSend}
            />

            <EmailZuordnungSearchModal
                isOpen={showZuordnungSuche}
                onClose={() => setShowZuordnungSuche(false)}
                onSelect={(auswahl) => { markDirty(); setZuordnung(auswahl); }}
                current={zuordnung}
            />
            {versand.zuordnungWarnung && (
                <ZuordnungWarnungDialog
                    onAuswaehlen={() => { versand.setZuordnungWarnung(false); setShowZuordnungSuche(true); }}
                    onTrotzdemSenden={() => { versand.setZuordnungWarnung(false); void versand.sendeEmail(); }}
                    onAbbrechen={() => versand.setZuordnungWarnung(false)}
                />
            )}
            {adresse.offen && <AdresseSpeichernDialog stand={adresse} kundeId={kundeId} zuordnung={zuordnung} />}
        </div>
    );
}
