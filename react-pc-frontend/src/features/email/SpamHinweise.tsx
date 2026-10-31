import { ShieldCheck, ShieldQuestion, ShieldX, TriangleAlert } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import type { EmailItem, FolderType } from './emailCenterModel';
import { spamGruendeAlsText, warnhinweisFuer } from './spamHinweisTexte';

type SpamFelder = Pick<EmailItem, 'spamVerdacht' | 'spamGruende' | 'warnhinweis'>;

/**
 * Dezente Schilder in der Mailliste: „Spam-Verdacht“ und ein Warnzeichen bei einer
 * erkannten Betrugsmasche. Ohne beides wird nichts gezeichnet.
 */
export function SpamListenSchilder({ email }: { email: SpamFelder }) {
    const warnung = warnhinweisFuer(email.warnhinweis);
    return <>
        {email.spamVerdacht && (
            <span data-testid="spam-verdacht-schild" title="Vielleicht Spam – bleibt im Posteingang, bis du entscheidest"
                className="flex items-center gap-1 rounded border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs text-amber-800">
                <ShieldQuestion className="h-3 w-3" aria-hidden="true" />
                Spam-Verdacht
            </span>
        )}
        {warnung && (
            <span data-testid="warnhinweis-schild" role="img" aria-label={warnung.titel} title={warnung.titel}
                className="flex items-center rounded border border-amber-300 bg-amber-50 px-1 py-0.5 text-amber-700">
                <TriangleAlert className="h-3 w-3" aria-hidden="true" />
            </span>
        )}
    </>;
}

interface SpamHinweiseProps {
    email: SpamFelder;
    folder: FolderType;
    /** Sperrt die Knöpfe, solange eine andere Aktion an der Mail läuft. */
    busy?: boolean;
    onSpam: () => void;
    onNotSpam: () => void;
}

/**
 * Schmale Hinweiszeilen im Kopf der Mail-Ansicht: Warnung vor einer Betrugsmasche (nur Hinweis,
 * verschiebt nichts, nicht wegklickbar) und „Vielleicht Spam“ mit den Knöpfen „Ist Spam“ und
 * „Kein Spam“. Gründe kommen nur als bekannte Klartexte aus `spamHinweisTexte.ts`, nie roh.
 */
export function SpamHinweise({ email, folder, busy = false, onSpam, onNotSpam }: SpamHinweiseProps) {
    const warnung = warnhinweisFuer(email.warnhinweis);
    // Im Papierkorb gibt es nichts mehr zu entscheiden.
    const verdacht = Boolean(email.spamVerdacht) && folder !== 'trash';
    if (!warnung && !verdacht) return null;
    const gruende = spamGruendeAlsText(email.spamGruende);

    return <div className="mt-2 space-y-2" data-testid="spam-hinweise">
        {warnung && (
            <div role="note" aria-label={warnung.titel} data-testid="warnhinweis"
                className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                <TriangleAlert className="mt-px h-4 w-4 shrink-0 text-amber-600" aria-hidden="true" />
                <p className="min-w-0 flex-1 leading-5">
                    <span className="font-semibold">{warnung.titel}</span>{' '}
                    <span>{warnung.satz}</span>
                </p>
                {/* -my-0.5: Das (i) ist höher als die Textzeile und soll die Zeile nicht aufblähen. */}
                <InfoButton titel="Woran erkennt das Programm das?" className="-my-0.5">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>{warnung.erkennung}</p>
                        <p>Das Programm prüft dafür jede eingehende Mail – auch von bekannten Kunden und Lieferanten.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Der Hinweis verschiebt nichts und bleibt an der Mail stehen. Er kann auch bei einer echten Mail erscheinen – dann in Ruhe prüfen, bevor du zahlst oder antwortest.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
        )}
        {verdacht && (
            <div data-testid="spam-verdacht-hinweis"
                className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2 text-xs text-amber-900">
                <div className="flex min-w-0 flex-1 items-start gap-2">
                    <ShieldQuestion className="mt-px h-4 w-4 shrink-0 text-amber-600" aria-hidden="true" />
                    <p className="min-w-0 leading-5">
                        <span className="font-semibold">Vielleicht Spam</span>{' – '}
                        Diese E-Mail sieht verdächtig aus. Sie bleibt im Posteingang, bis du entscheidest.
                    </p>
                    <InfoButton titel="Warum?" className="-my-0.5">
                        <InfoAbschnitt titel="Was ist aufgefallen?">
                            {gruende.length > 0
                                ? <ul className="list-disc space-y-0.5 pl-4">{gruende.map(text => <li key={text}>{text}</li>)}</ul>
                                : <p>Der Lernfilter kennt ähnliche Mails, die Spam waren.</p>}
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was passiert beim Klick?">
                            <p><span className="font-medium text-slate-700">Ist Spam:</span> Die Mail wandert in den Spam-Ordner – auch auf dem Mail-Server – und das Programm lernt dazu.</p>
                            <p><span className="font-medium text-slate-700">Kein Spam:</span> Der Hinweis verschwindet, das Programm merkt es sich.</p>
                        </InfoAbschnitt>
                    </InfoButton>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                    <Button variant="outline" size="sm" onClick={onSpam} disabled={busy}
                        className="h-7 gap-1.5 rounded-lg px-2.5 text-xs">
                        <ShieldX className="h-3.5 w-3.5" aria-hidden="true" />Ist Spam
                    </Button>
                    <Button variant="outline" size="sm" onClick={onNotSpam} disabled={busy}
                        className="h-7 gap-1.5 rounded-lg px-2.5 text-xs">
                        <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />Kein Spam
                    </Button>
                </div>
            </div>
        )}
    </div>;
}
