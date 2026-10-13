import { useEffect, useId, useRef, useState } from 'react';
import { AlertCircle, FileText, Loader2, RotateCcw, Save } from 'lucide-react';
import { Button } from '../../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../ui/dialog';
import { useConfirm } from '../../ui/confirm-dialog';
import { useToast } from '../../ui/toast';
import DocumentPreviewModal from '../../DocumentPreviewModal';
import { htmlToText, toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { cn } from '../../../lib/utils';
import { ErklaerInfo } from './ErklaerInfo';
import { RechtstextFassungAnsicht } from './RechtstextFassungAnsicht';
import { RechtstextRichtext, type RechtstextRichtextHandle } from './RechtstextRichtext';
import { RechtstextSeitenspalte } from './RechtstextSeitenspalte';
import {
    beschreibeStand, bereinigeRechtstextHtml, formatiereDatum, ladeFassung, ladeFassungen, ladeRechtstext,
    ladeVorschau, MAX_ZEICHEN, ohneLeereAbsaetzeAmEnde, setzeAufStandard, speichereRechtstext,
    type RechtstextDetail, type RechtstextFassung, type RechtstextFassungKurz,
} from './rechtstextApi';
import { texteFuer } from './rechtstextTexte';

export interface RechtstextDialogProps {
    art: string;
    /** Bezeichnung aus der Liste – steht schon im Kopf, bevor der Text geladen ist. */
    bezeichnung: string;
    onClose: () => void;
    /** Neuer Stand nach Speichern oder Zurücksetzen (für die Karte). */
    onAktualisiert: (detail: RechtstextDetail) => void;
}

type Aktion = 'speichern' | 'vorschau' | 'standard';

/** Eine frühere Fassung, die statt des Editors angezeigt wird (null = lädt). */
interface Ansicht {
    nummer: number;
    fassung: RechtstextFassung | null;
}

function meldung(fehler: unknown, ersatz: string): string {
    return fehler instanceof Error && fehler.message ? fehler.message : ersatz;
}

/** Prüft den Text vor Speichern und Vorschau so, wie es auch das Backend tut. */
function pruefeText(html: string): string | null {
    if (!htmlToText(html).trim()) return 'Der Text ist leer. Bitte geben Sie einen Text ein.';
    if (html.length > MAX_ZEICHEN) {
        return `Der Text ist zu lang (höchstens ${MAX_ZEICHEN.toLocaleString('de-DE')} Zeichen). Bitte kürzen Sie ihn.`;
    }
    return null;
}

/**
 * Großer Bearbeiten-Dialog für einen festen Text (z. B. die Widerrufsbelehrung):
 * Editor links, Platzhalter/Vorschläge/frühere Fassungen rechts, Aktionen unten.
 */
export function RechtstextDialog({ art, bezeichnung, onClose, onAktualisiert }: RechtstextDialogProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const titelId = useId();
    const texte = texteFuer(art);
    const editorRef = useRef<RechtstextRichtextHandle>(null);

    const [detail, setDetail] = useState<RechtstextDetail | null>(null);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [entwurf, setEntwurf] = useState('');
    const [geaendert, setGeaendert] = useState(false);
    const [fassungen, setFassungen] = useState<RechtstextFassungKurz[] | null>(null);
    const [fassungenFehler, setFassungenFehler] = useState<string | null>(null);
    const [ansicht, setAnsicht] = useState<Ansicht | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [aktion, setAktion] = useState<Aktion | null>(null);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);

    // Zähler für „Erneut versuchen“ – jede Erhöhung lädt den Teil neu.
    const [textVersuch, setTextVersuch] = useState(0);
    const [listenVersuch, setListenVersuch] = useState(0);

    useEffect(() => {
        let aktiv = true;
        ladeRechtstext(art)
            .then(geladen => {
                if (!aktiv) return;
                setDetail(geladen);
                setEntwurf(bereinigeRechtstextHtml(geladen.html));
                setGeaendert(false);
            })
            .catch((e: unknown) => {
                if (!aktiv) return;
                const text = meldung(e, 'Der Text konnte nicht geladen werden.');
                setLadeFehler(text);
                toast.error(text);
            });
        return () => { aktiv = false; };
    }, [art, textVersuch, toast]);

    useEffect(() => {
        let aktiv = true;
        ladeFassungen(art)
            .then(liste => { if (aktiv) setFassungen(liste); })
            .catch((e: unknown) => {
                if (!aktiv) return;
                const text = meldung(e, 'Die früheren Fassungen konnten nicht geladen werden.');
                setFassungenFehler(text);
                toast.error(text);
            });
        return () => { aktiv = false; };
    }, [art, listenVersuch, toast]);

    const textNeuLaden = () => {
        setLadeFehler(null);
        setTextVersuch(v => v + 1);
    };

    const fassungenNeuLaden = () => {
        setFassungenFehler(null);
        setFassungen(null);
        setListenVersuch(v => v + 1);
    };

    // Blob-URL der Vorschau freigeben, sobald sie nicht mehr gebraucht wird.
    useEffect(() => () => { if (pdfUrl) URL.revokeObjectURL(pdfUrl); }, [pdfUrl]);

    const zeigeFehler = (text: string) => {
        setFehler(text);
        toast.error(text);
    };

    const schliessenWollen = async () => {
        if (aktion) return;
        if (geaendert) {
            const verwerfen = await confirm({
                title: 'Änderungen verwerfen?',
                message: 'Sie haben den Text geändert, aber noch nicht gespeichert. Beim Schließen gehen die Änderungen verloren.',
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter bearbeiten',
                variant: 'warning',
            });
            if (!verwerfen) return;
        }
        onClose();
    };

    const speichern = async () => {
        if (!detail) return;
        const html = ohneLeereAbsaetzeAmEnde(entwurf);
        const problem = pruefeText(html);
        if (problem) { zeigeFehler(problem); return; }
        setFehler(null);
        setAktion('speichern');
        try {
            const neu = await speichereRechtstext(art, html);
            if (neu.fassung === detail.fassung) {
                toast.info('Keine Änderung – nichts Neues gespeichert.');
            } else {
                toast.success(`Gespeichert – gilt ab sofort als Fassung ${neu.fassung}, Stand ${formatiereDatum(neu.stand)}.`);
            }
            onAktualisiert(neu);
            onClose();
        } catch (e) {
            zeigeFehler(meldung(e, 'Der Text konnte nicht gespeichert werden.'));
        } finally {
            setAktion(null);
        }
    };

    const zuruecksetzen = async () => {
        if (!detail) return;
        const ja = await confirm({
            title: 'Auf Standardtext zurücksetzen?',
            message: 'Der mitgelieferte Text nach dem gesetzlichen Muster gilt dann ab sofort als neue Fassung mit heutigem Stand. '
                + 'Ihre bisherigen Fassungen bleiben erhalten.'
                + (geaendert ? ' Ihre ungespeicherten Änderungen gehen dabei verloren.' : ''),
            confirmLabel: 'Zurücksetzen',
            cancelLabel: 'Abbrechen',
            variant: 'warning',
        });
        if (!ja) return;
        setFehler(null);
        setAktion('standard');
        try {
            const neu = await setzeAufStandard(art);
            const sauber = bereinigeRechtstextHtml(neu.html);
            editorRef.current?.setzeInhalt(sauber, false);
            setEntwurf(sauber);
            setGeaendert(false);
            setAnsicht(null);
            if (neu.fassung === detail.fassung) {
                toast.info('Der Standardtext gilt bereits – nichts Neues gespeichert.');
            } else {
                toast.success(`Standardtext gilt ab sofort als Fassung ${neu.fassung}, Stand ${formatiereDatum(neu.stand)}.`);
            }
            setDetail(neu);
            onAktualisiert(neu);
            fassungenNeuLaden();
        } catch (e) {
            zeigeFehler(meldung(e, 'Der Standardtext konnte nicht gesetzt werden.'));
        } finally {
            setAktion(null);
        }
    };

    const vorschau = async () => {
        const html = ohneLeereAbsaetzeAmEnde(entwurf);
        const problem = pruefeText(html);
        if (problem) { zeigeFehler(problem); return; }
        setFehler(null);
        setAktion('vorschau');
        try {
            const pdf = await ladeVorschau(art, html);
            setPdfUrl(URL.createObjectURL(pdf));
            toast.info('Die Vorschau zeigt den Text aus dem Editor – gespeichert ist damit noch nichts.');
        } catch (e) {
            zeigeFehler(meldung(e, 'Die Vorschau konnte nicht erstellt werden.'));
        } finally {
            setAktion(null);
        }
    };

    const fassungZeigen = async (nummer: number) => {
        setAnsicht({ nummer, fassung: null });
        try {
            const fassung = await ladeFassung(art, nummer);
            // Hat der Nutzer inzwischen eine andere Fassung gewählt, diese Antwort verwerfen.
            setAnsicht(aktuell => (aktuell?.nummer === nummer ? { nummer, fassung } : aktuell));
        } catch (e) {
            setAnsicht(null);
            zeigeFehler(meldung(e, 'Diese Fassung konnte nicht geladen werden.'));
        }
    };

    const fassungUebernehmen = (fassung: RechtstextFassung) => {
        // setzeInhalt meldet die Änderung über onChange → Entwurf + „geändert“.
        editorRef.current?.setzeInhalt(bereinigeRechtstextHtml(fassung.html), true);
        setAnsicht(null);
        toast.info(`Fassung ${fassung.fassung} steht jetzt im Editor – noch nicht gespeichert.`);
    };

    const sichereVorschauUrl = toSafeResourceUrl(pdfUrl);
    const vorschauSchliessen = () => setPdfUrl(null);

    const beschaeftigt = aktion !== null;
    const gesperrtHinweis = ansicht ? 'Erst „Zurück zum Editor“ oder „In den Editor übernehmen“ wählen.' : undefined;

    return (
        <Dialog
            open
            // Escape landet zuerst hier (der Dialog hält den Fokus): Ist die PDF-Vorschau
            // offen, schließt es nur die Vorschau, nicht den Bearbeiten-Dialog.
            onOpenChange={offen => {
                if (offen) return;
                if (pdfUrl) setPdfUrl(null);
                else void schliessenWollen();
            }}
            aria-labelledby={titelId}
            className="h-[calc(100vh-2rem)] max-h-[960px] w-full max-w-6xl"
        >
            <DialogContent>
                <DialogHeader className="pr-10">
                    <div className="flex items-center gap-1.5">
                        <DialogTitle id={titelId} className="text-slate-900">{detail?.bezeichnung ?? bezeichnung}</DialogTitle>
                        <ErklaerInfo titel={detail?.bezeichnung ?? bezeichnung} info={texte.info} />
                    </div>
                    <DialogDescription>{detail ? beschreibeStand(detail) : 'Wird geladen …'}</DialogDescription>
                </DialogHeader>

                {ladeFehler ? (
                    <div role="alert" className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
                        <AlertCircle aria-hidden="true" className="h-8 w-8 text-rose-500" />
                        <p className="text-sm text-slate-700">{ladeFehler}</p>
                        <Button type="button" variant="outline" size="sm" onClick={textNeuLaden}>Erneut versuchen</Button>
                    </div>
                ) : !detail ? (
                    <div className="flex min-h-0 flex-1 gap-5" aria-label="Text wird geladen">
                        <div className="flex-1 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                        <div className="w-72 shrink-0 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                    </div>
                ) : (
                    <div className="flex min-h-0 flex-1 gap-5">
                        <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-2">
                            {ansicht && (
                                <RechtstextFassungAnsicht
                                    fassung={ansicht.fassung}
                                    onUebernehmen={fassungUebernehmen}
                                    onZurueck={() => setAnsicht(null)}
                                />
                            )}
                            {/* Editor bleibt eingehängt, damit Rückgängig/Wiederholen erhalten bleiben. */}
                            <div className={cn('flex min-h-0 flex-1 flex-col', ansicht && 'hidden')}>
                                <RechtstextRichtext
                                    ref={editorRef}
                                    anfangsHtml={entwurf}
                                    label={`Text der ${detail.bezeichnung}`}
                                    onChange={html => { setEntwurf(html); setGeaendert(true); setFehler(null); }}
                                />
                            </div>
                            {texte.hinweis && <p className="text-xs text-slate-500">{texte.hinweis}</p>}
                        </div>
                        <RechtstextSeitenspalte
                            platzhalter={detail.platzhalter ?? []}
                            vorschlaege={texte.vorschlaege}
                            fassungen={fassungen}
                            fassungenFehler={fassungenFehler}
                            gewaehlteFassung={ansicht?.nummer ?? null}
                            aktuelleFassung={detail.fassung}
                            onPlatzhalter={token => { setAnsicht(null); editorRef.current?.fuegeTextEin(token); }}
                            onVorschlag={html => { setAnsicht(null); editorRef.current?.haengeAbsatzAn(html); }}
                            onFassung={nummer => void fassungZeigen(nummer)}
                            onFassungenNeuLaden={fassungenNeuLaden}
                        />
                    </div>
                )}

                {fehler && (
                    <div role="alert" className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                        <AlertCircle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                        <p>{fehler}</p>
                    </div>
                )}

                <DialogFooter className="items-center gap-2 sm:justify-between sm:space-x-0">
                    <div className="flex items-center gap-1">
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={!detail || beschaeftigt}
                            onClick={() => void zuruecksetzen()}
                        >
                            {aktion === 'standard'
                                ? <Loader2 aria-hidden="true" className="mr-1 inline h-4 w-4 motion-safe:animate-spin" />
                                : <RotateCcw aria-hidden="true" className="mr-1 inline h-4 w-4" />}
                            Auf Standardtext zurücksetzen
                        </Button>
                        <ErklaerInfo titel="Auf Standardtext zurücksetzen" info={texte.zuruecksetzenInfo} />
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={!detail || beschaeftigt || !!ansicht}
                            title={gesperrtHinweis}
                            onClick={() => void vorschau()}
                        >
                            {aktion === 'vorschau'
                                ? <Loader2 aria-hidden="true" className="mr-1 inline h-4 w-4 motion-safe:animate-spin" />
                                : <FileText aria-hidden="true" className="mr-1 inline h-4 w-4" />}
                            Vorschau (PDF)
                        </Button>
                        <Button type="button" variant="secondary" size="sm" disabled={beschaeftigt} onClick={() => void schliessenWollen()}>
                            Abbrechen
                        </Button>
                        <Button
                            type="button"
                            size="sm"
                            disabled={!detail || beschaeftigt || !!ansicht}
                            title={gesperrtHinweis}
                            onClick={() => void speichern()}
                        >
                            {aktion === 'speichern'
                                ? <Loader2 aria-hidden="true" className="mr-1 inline h-4 w-4 motion-safe:animate-spin" />
                                : <Save aria-hidden="true" className="mr-1 inline h-4 w-4" />}
                            Speichern
                        </Button>
                    </div>
                </DialogFooter>

                {/* Im Dialog eingehängt, damit Fokusfalle und Escape des Dialogs die Vorschau einschließen. */}
                {sichereVorschauUrl && (
                    <DocumentPreviewModal
                        doc={{ url: sichereVorschauUrl, title: `Vorschau: ${detail?.bezeichnung ?? bezeichnung}` }}
                        isPdf
                        onClose={vorschauSchliessen}
                    />
                )}
            </DialogContent>
        </Dialog>
    );
}
