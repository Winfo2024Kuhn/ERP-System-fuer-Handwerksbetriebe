import { forwardRef, useState } from 'react';
import { Download, MailOpen, Voicemail } from 'lucide-react';
import { useToast } from '../../components/ui/toast';
import { refreshNotifications } from '../../lib/notificationRefresh';
import { cn } from '../../lib/utils';
import { audioPfad, setzeAbgehoert } from './api';
import { AudioPlayer } from './AudioPlayer';
import { formatSekunden, formatWann, formatWannImSatz } from './format';
import type { KontaktKurz, Sprachnachricht, ZuordnenZiel } from './types';
import { WerAnzeige, ZuordnungsMenue } from './WerAnzeige';

/**
 * Eine Nachricht auf dem Anrufbeantworter: wer, welcher AB, wann, wie lang,
 * Abspieler und Download.
 *
 * <p>Neue Nachrichten stehen fett mit rotem Punkt. Beim ersten Abspielen
 * wird die Nachricht als abgehört vermerkt („Abgehört von Max Mustermann,
 * heute 12:04"); über das Drei-Punkte-Menü lässt sie sich wieder als neu
 * markieren.</p>
 */

export interface ZuordnenAktionen {
    oeffneDialog: (ziel: ZuordnenZiel, nummer: string) => void;
    kandidatWaehlen: (ziel: ZuordnenZiel, kontakt: KontaktKurz) => void;
    aufheben: (ziel: ZuordnenZiel) => void;
    istBeschaeftigt: (ziel: ZuordnenZiel) => boolean;
}

interface SprachnachrichtEintragProps {
    nachricht: Sprachnachricht;
    anrufbeantworterName: string;
    hervorgehoben?: boolean;
    onGeaendert: (nachricht: Sprachnachricht) => void;
    zuordnen: ZuordnenAktionen;
    /** In der Akte ist der Kontakt klar – dort reicht ein schlichter Kopf. */
    kompakt?: boolean;
    /** Unter einem Anruf eingerückt: die Zuordnung ändert man dort am Anruf. */
    ohneZuordnung?: boolean;
}

export const SprachnachrichtEintrag = forwardRef<HTMLLIElement, SprachnachrichtEintragProps>(function SprachnachrichtEintrag(
    { nachricht, anrufbeantworterName, hervorgehoben = false, onGeaendert, zuordnen, kompakt = false, ohneZuordnung = false },
    ref,
) {
    const toast = useToast();
    const [aendert, setAendert] = useState(false);
    const ziel: ZuordnenZiel = { art: 'sprachnachricht', id: nachricht.id };
    const wer = nachricht.kontakt?.name || nachricht.nameFritzbox || nachricht.nummer || 'Unbekannt';

    const markiere = async (abgehoert: boolean) => {
        setAendert(true);
        try {
            const neu = await setzeAbgehoert(nachricht.id, abgehoert);
            onGeaendert(neu);
            refreshNotifications();
            if (!abgehoert) toast.success('Nachricht wieder als neu markiert.');
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Die Nachricht konnte nicht geändert werden.');
        } finally {
            setAendert(false);
        }
    };

    const beiWiedergabe = () => {
        if (nachricht.neu && !aendert) void markiere(true);
    };

    return (
        <li
            ref={ref}
            id={`nachricht-${nachricht.id}`}
            data-hervorgehoben={hervorgehoben || undefined}
            className={cn(
                'rounded-lg border bg-white p-4 shadow-sm transition-shadow scroll-mt-24',
                nachricht.neu ? 'border-rose-200' : 'border-slate-200',
                hervorgehoben && 'ring-2 ring-rose-400 ring-offset-2',
            )}
        >
            <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
                <div className="flex min-w-[16rem] flex-1 items-start gap-3">
                    <span className="mt-1.5 flex h-2.5 w-2.5 shrink-0 items-center justify-center" aria-hidden={!nachricht.neu}>
                        {nachricht.neu && (
                            <span className="h-2.5 w-2.5 rounded-full bg-rose-600" role="img" aria-label="Neue Nachricht" />
                        )}
                    </span>
                    <div className="min-w-0 flex-1">
                        {kompakt ? (
                            <p className={cn('flex items-center gap-2 text-slate-900', nachricht.neu ? 'font-bold' : 'font-medium')}>
                                <Voicemail aria-hidden="true" className="h-4 w-4 shrink-0 text-rose-600" />
                                Nachricht auf dem Anrufbeantworter
                            </p>
                        ) : (
                            <WerAnzeige
                                eintrag={nachricht}
                                fett={nachricht.neu}
                                beschaeftigt={zuordnen.istBeschaeftigt(ziel)}
                                onZuordnen={() => zuordnen.oeffneDialog(ziel, nachricht.nummer)}
                                onKandidatWaehlen={(k) => zuordnen.kandidatWaehlen(ziel, k)}
                            />
                        )}
                        <p className={cn('mt-1 text-sm', nachricht.neu ? 'font-semibold text-slate-700' : 'text-slate-500')}>
                            <span>{anrufbeantworterName}</span>
                            <span aria-hidden="true"> · </span>
                            <span>{formatWann(nachricht.zeitpunkt)}</span>
                            <span aria-hidden="true"> · </span>
                            <span className="tabular-nums">{formatSekunden(nachricht.dauerSekunden)}</span>
                            {!kompakt && nachricht.nummer && (
                                <>
                                    <span aria-hidden="true"> · </span>
                                    <span className="tabular-nums">{nachricht.nummer}</span>
                                </>
                            )}
                        </p>
                        {!nachricht.neu && nachricht.abgehoertAm && (
                            <p className="mt-0.5 text-xs text-slate-500">
                                Abgehört{nachricht.abgehoertVon ? ` von ${nachricht.abgehoertVon}` : ''}, {formatWannImSatz(nachricht.abgehoertAm)}
                            </p>
                        )}
                    </div>
                </div>
                <div className="flex w-full items-center gap-2 lg:w-auto lg:min-w-[22rem] lg:max-w-lg lg:flex-1">
                    <AudioPlayer
                        className="flex-1"
                        src={audioPfad(nachricht.id)}
                        dauerSekunden={nachricht.dauerSekunden}
                        beschriftung={`Nachricht von ${wer}`}
                        onWiedergabeStart={beiWiedergabe}
                    />
                    <a
                        href={audioPfad(nachricht.id)}
                        download={`nachricht-${nachricht.id}.wav`}
                        aria-label={`Nachricht von ${wer} herunterladen`}
                        title="Herunterladen"
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-500 transition-colors hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <Download aria-hidden="true" className="h-4 w-4" />
                    </a>
                    <ZuordnungsMenue
                        beschriftung={`Nachricht von ${wer}`}
                        zugeordnet={!ohneZuordnung && !!nachricht.kontakt}
                        beschaeftigt={aendert || zuordnen.istBeschaeftigt(ziel)}
                        onAndererKontakt={() => zuordnen.oeffneDialog(ziel, nachricht.nummer)}
                        onAufheben={() => zuordnen.aufheben(ziel)}
                        zusaetzlich={nachricht.neu ? [] : [{ text: 'Wieder als neu markieren', symbol: MailOpen, onSelect: () => void markiere(false) }]}
                    />
                </div>
            </div>
        </li>
    );
});
