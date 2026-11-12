import { useState, useEffect, useRef, useMemo } from 'react';
import { Mail, Loader2, Paperclip } from 'lucide-react';
import { Button } from './ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { useToast } from './ui/toast';
import { htmlFuerVersand, setzeEditorHtml } from '../features/email/mailEditorHtml';
import { MailNachrichtFeld } from '../features/email/editor/MailNachrichtFeld';
import { steuerberaterPdfUrl } from '../features/report-centre/api';
import { PdfAnhangChip } from '../features/report-centre/PdfAnhangChip';
import { SteuerberaterEmpfaengerFelder } from '../features/report-centre/SteuerberaterEmpfaengerFelder';
import { usePdfAnhang } from '../features/report-centre/usePdfAnhang';
import { useSteuerberaterEmpfaenger } from '../features/report-centre/useSteuerberaterEmpfaenger';
import { aktuellesFrontendProfil } from '../lib/frontendProfil';
import { stundenMailText } from '../features/report-centre/stundenMail';
import type { Stundenart } from '../features/report-centre/stundenart';
import { StundenartHinweis } from '../features/report-centre/StundenartHinweis';
import { monatsname } from '../features/report-centre/zeitraum';
import { escapeHtml } from '../lib/emailAddress';
import { briefanrede } from '../lib/briefanrede';

export interface SteuerberaterEmailModalProps {
    isOpen: boolean;
    onClose: () => void;
    jahr: number;
    monat: number;
    /** Mitarbeiter in der Stundenaufstellung (leer = alle, die der Server für relevant hält). */
    mitarbeiterIds: number[];
    mitResturlaub: boolean;
    mitStundenkonto: boolean;
    anzahlMitarbeiter: number;
    /** Soll- oder Iststunden aus der Vorschau; geht mit ins PDF, damit PDF und Vorschau übereinstimmen. */
    stundenart: Stundenart;
    onSuccess?: () => void;
}

/** Dialog „Stundenübermittlung“: kurze Mail an den Steuerberater, die Stundenaufstellung hängt als PDF an. */
export function SteuerberaterEmailModal({
    isOpen, onClose, jahr, monat, mitarbeiterIds, mitResturlaub, mitStundenkonto, anzahlMitarbeiter, stundenart, onSuccess,
}: SteuerberaterEmailModalProps) {
    const toast = useToast();
    const [sending, setSending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const editorRef = useRef<HTMLDivElement>(null);
    const lastRenderedKeyRef = useRef<string>('');
    const e = useSteuerberaterEmpfaenger(isOpen, monat, jahr);

    // Das PDF wird beim Öffnen erstellt und als Anhang gezeigt; zu = nichts laden.
    const idsText = mitarbeiterIds.join(',');
    const pdfUrl = useMemo(() => (isOpen
        ? steuerberaterPdfUrl({ jahr, monat, mitarbeiterIds: idsText ? idsText.split(',').map(Number) : [], mitResturlaub, mitStundenkonto, stundenart })
        : null), [isOpen, jahr, monat, idsText, mitResturlaub, mitStundenkonto, stundenart]);
    const anhang = usePdfAnhang(pdfUrl, `Stundenaufstellung_${jahr}-${String(monat).padStart(2, '0')}.pdf`);

    // Geschlossen: Fehler vergessen, Editor beim nächsten Öffnen neu befüllen.
    const [warOffen, setWarOffen] = useState(isOpen);
    if (warOffen !== isOpen) {
        setWarOffen(isOpen);
        if (!isOpen) setError(null);
    }

    // Editor-Inhalt nur neu setzen, wenn sich Anrede/Ansprechpartner/Monat ändert –
    // sonst würde jedes Tippen im contentEditable überschrieben.
    const { laedt, steuerberaterId, ansprechpartnerId, ansprechpartner, signatur } = e;
    useEffect(() => {
        if (!isOpen) { lastRenderedKeyRef.current = ''; return; }
        if (laedt) return;
        const key = `${steuerberaterId}|${ansprechpartnerId}|${monat}|${jahr}|${stundenart}`;
        if (lastRenderedKeyRef.current === key) return;
        // Kein Ansprechpartner: briefanrede liefert „Sehr geehrte Damen und Herren“.
        const anredeZeile = `${escapeHtml(briefanrede(ansprechpartner?.anrede, ansprechpartner?.nachname))},`;
        setzeEditorHtml(editorRef.current, stundenMailText(anredeZeile, monat, jahr, stundenart, signatur));
        lastRenderedKeyRef.current = key;
    }, [isOpen, laedt, steuerberaterId, ansprechpartnerId, ansprechpartner, monat, jahr, stundenart, signatur]);

    const pruefe = (): string | null => {
        if (anhang.art !== 'fertig') return 'Das PDF ist noch nicht fertig – ohne Anhang wird nichts gesendet.';
        if (!e.empfaenger.trim()) return 'Bitte Empfänger angeben.';
        if (!e.betreff.trim()) return 'Bitte Betreff angeben.';
        return null;
    };

    const handleSend = async () => {
        const problem = pruefe();
        if (problem || anhang.art !== 'fertig') { setError(problem); return; }
        setSending(true);
        setError(null);
        try {
            const profil = aktuellesFrontendProfil();
            const formData = new FormData();
            const dto = {
                // Leerer sender = Backend löst aus frontendUserId auf (im FirmaEditor konfigurierte Adresse).
                sender: null,
                recipients: [e.empfaenger.trim()],
                cc: [],
                subject: e.betreff.trim(),
                body: htmlFuerVersand(editorRef.current?.innerHTML),
                direction: 'OUT',
                benutzer: profil?.displayName || '',
                frontendUserId: profil?.id || null,
            };
            formData.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
            formData.append('attachments', anhang.pdf.blob, anhang.pdf.dateiname);
            const res = await fetch('/api/emails/send', { method: 'POST', body: formData });
            if (!res.ok) throw new Error('E-Mail senden fehlgeschlagen');
            toast.success(`Stundenaufstellung an ${e.empfaenger.trim()} gesendet.`);
            onSuccess?.();
            onClose();
        } catch (err) {
            console.error('E-Mail senden fehlgeschlagen:', err);
            const meldung = 'E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.';
            setError(meldung);
            toast.error(meldung);
        } finally {
            setSending(false);
        }
    };

    const sperrGrund = anhang.art === 'laedt' ? 'Das PDF wird noch erstellt.'
        : anhang.art === 'fehler' ? 'Ohne PDF kann die Stundenaufstellung nicht gesendet werden.' : undefined;

    return (
        <Dialog open={isOpen} onOpenChange={o => { if (!o && !sending) onClose(); }} aria-labelledby="stundenuebermittlung-titel"
            className="w-full max-w-4xl">
            <DialogContent>
                <DialogHeader className="flex-row items-center gap-3 space-y-0 pr-10">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100">
                        <Mail aria-hidden="true" className="h-5 w-5 text-rose-600" />
                    </div>
                    <div>
                        <DialogTitle id="stundenuebermittlung-titel" className="text-slate-900">Stundenübermittlung</DialogTitle>
                        <p className="mt-1 text-sm text-slate-500">{anzahlMitarbeiter} Mitarbeiter für {monatsname(monat)} {jahr}</p>
                    </div>
                </DialogHeader>

                <div className="-m-1 min-h-0 flex-1 space-y-4 overflow-y-auto p-1">
                    <StundenartHinweis art={stundenart} />
                    {error && <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
                    <SteuerberaterEmpfaengerFelder empfaenger={e} />
                    <div className="space-y-2">
                        <Label htmlFor="stundenuebermittlung-betreff">Betreff</Label>
                        <Input id="stundenuebermittlung-betreff" value={e.betreff} onChange={ev => e.setBetreff(ev.target.value)}
                            placeholder="Betreff eingeben..." className="font-medium" />
                    </div>
                    <div className="space-y-2">
                        <p className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                            <Paperclip aria-hidden="true" className="h-4 w-4 text-slate-500" /> Anhang
                        </p>
                        <PdfAnhangChip stand={anhang} />
                    </div>
                    <div className="space-y-2">
                        <Label>Nachricht (direkt bearbeitbar)</Label>
                        <MailNachrichtFeld editorRef={editorRef} ariaLabel="Nachricht" className="max-h-[360px]" />
                    </div>
                </div>

                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onClose} disabled={sending}>Abbrechen</Button>
                    <Button onClick={handleSend} disabled={sending || anhang.art !== 'fertig'} title={sperrGrund}>
                        {sending && <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />}
                        Senden
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
