import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, Clock, Loader2, Phone, RefreshCw, Settings, ShieldOff, XCircle } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useToast } from '../components/ui/toast';
import { useAuth } from '../auth/AuthContext';
import { refreshNotifications } from '../lib/notificationRefresh';
import { cn } from '../lib/utils';
import { holeJetztAb, ladeStatus } from '../features/telefon/api';
import { AnrufbeantworterListe } from '../features/telefon/AnrufbeantworterListe';
import { AnrufListe } from '../features/telefon/AnrufListe';
import { formatVorZeit } from '../features/telefon/format';
import type { AbholErgebnis, TelefonStatus } from '../features/telefon/types';
import { useTelefonBerechtigung } from '../features/telefon/useTelefonBerechtigung';

/**
 * Seiten „Anrufe" (`/telefon/anrufe`) und „Anrufbeantworter"
 * (`/telefon/anrufbeantworter`). Gewechselt wird über die Menüleiste oben
 * (Kommunikation › Telefon) – eigene Reiter auf der Seite gibt es bewusst
 * nicht mehr, der Titel nennt die aktuelle Ansicht.
 *
 * <p>Die Daten holt der Server regelmäßig selbst von der FRITZ!Box.
 * „Jetzt abholen" stößt das sofort an. Ohne das Recht „Anrufe &
 * Anrufbeantworter" zeigt die Seite nur einen freundlichen Hinweis.</p>
 */

type Reiter = 'anrufe' | 'anrufbeantworter';
const ANSICHT: Record<Reiter, { titel: string; untertitel: string }> = {
    anrufe: { titel: 'Anrufe', untertitel: 'Alle Anrufe der Geschäftsnummer aus der FRITZ!Box.' },
    anrufbeantworter: { titel: 'Anrufbeantworter', untertitel: 'Nachrichten auf dem Anrufbeantworter der FRITZ!Box.' },
};

function istReiter(wert: string | undefined): wert is Reiter {
    return wert === 'anrufe' || wert === 'anrufbeantworter';
}

function ergebnisText(ergebnis: AbholErgebnis): string {
    if (!ergebnis.erfolgreich) return ergebnis.meldung || 'Abholen hat nicht geklappt.';
    const teile: string[] = [];
    teile.push(ergebnis.neueAnrufe === 1 ? '1 neuer Anruf' : `${ergebnis.neueAnrufe} neue Anrufe`);
    teile.push(ergebnis.neueSprachnachrichten === 1 ? '1 neue Nachricht' : `${ergebnis.neueSprachnachrichten} neue Nachrichten`);
    if (ergebnis.nachtraeglichZugeordnet > 0) teile.push(`${ergebnis.nachtraeglichZugeordnet} nachträglich zugeordnet`);
    return `Abgeholt: ${teile.join(', ')}.`;
}

function useMinutenTakt(): Date {
    const [jetzt, setJetzt] = useState(() => new Date());
    useEffect(() => {
        const intervall = window.setInterval(() => setJetzt(new Date()), 30_000);
        return () => window.clearInterval(intervall);
    }, []);
    return jetzt;
}

export default function TelefonPage() {
    const { reiter } = useParams();
    const darf = useTelefonBerechtigung();

    if (!istReiter(reiter)) return <Navigate to="/telefon/anrufe" replace />;
    if (darf === null) {
        return (
            <PageLayout ribbonCategory="Kommunikation" title={ANSICHT[reiter].titel}>
                <p role="status" className="rounded-lg bg-slate-100 p-6 text-slate-600 motion-safe:animate-pulse">Recht wird geprüft …</p>
            </PageLayout>
        );
    }
    if (!darf) {
        return (
            <PageLayout ribbonCategory="Kommunikation" title={ANSICHT[reiter].titel}>
                <Card className="mx-auto flex max-w-xl flex-col items-center gap-3 p-10 text-center">
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-500">
                        <ShieldOff aria-hidden="true" className="h-6 w-6" />
                    </div>
                    <h2 className="text-lg font-semibold text-slate-900">Anrufe sind für Sie nicht freigeschaltet</h2>
                    <p className="text-slate-500">
                        Anrufliste und Anrufbeantworter sieht nur, wer das Recht „Anrufe &amp; Anrufbeantworter“ hat.
                        Bitte wenden Sie sich an den Administrator.
                    </p>
                </Card>
            </PageLayout>
        );
    }
    return <TelefonInhalt reiter={reiter} />;
}

function TelefonInhalt({ reiter }: { reiter: Reiter }) {
    const toast = useToast();
    const { isAdmin } = useAuth();
    const jetzt = useMinutenTakt();
    const [status, setStatus] = useState<TelefonStatus | null>(null);
    const [statusFehler, setStatusFehler] = useState<string | null>(null);
    const [holtAb, setHoltAb] = useState(false);
    const [ergebnis, setErgebnis] = useState<AbholErgebnis | null>(null);
    const [aktualisierung, setAktualisierung] = useState(0);
    const ergebnisTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const ladeStand = useCallback(async () => {
        try {
            setStatus(await ladeStatus());
            setStatusFehler(null);
        } catch (e) {
            setStatusFehler(e instanceof Error ? e.message : 'Der Telefon-Stand konnte nicht geladen werden.');
        }
    }, []);

    useEffect(() => {
        void ladeStand();
    }, [ladeStand]);

    useEffect(() => () => {
        if (ergebnisTimer.current) clearTimeout(ergebnisTimer.current);
    }, []);

    const jetztAbholen = async () => {
        setHoltAb(true);
        try {
            const neu = await holeJetztAb();
            setErgebnis(neu);
            if (!neu.erfolgreich) toast.error(ergebnisText(neu));
            if (ergebnisTimer.current) clearTimeout(ergebnisTimer.current);
            ergebnisTimer.current = setTimeout(() => setErgebnis(null), 8000);
            setAktualisierung((n) => n + 1);
            refreshNotifications();
            await ladeStand();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Anrufe konnten nicht von der FRITZ!Box abgeholt werden.');
        } finally {
            setHoltAb(false);
        }
    };

    const nichtEingerichtet = status !== null && !status.eingerichtet;

    const abholKnopf = (
        <Button onClick={jetztAbholen} disabled={holtAb || !status || nichtEingerichtet}
            title={nichtEingerichtet ? 'Erst die FRITZ!Box in den Einstellungen verbinden' : undefined}>
            {holtAb ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <RefreshCw aria-hidden="true" className="h-4 w-4" />}
            {holtAb ? 'Wird abgeholt …' : 'Jetzt abholen'}
        </Button>
    );

    return (
        <PageLayout
            ribbonCategory="Kommunikation"
            title={ANSICHT[reiter].titel}
            subtitle={ANSICHT[reiter].untertitel}
            actions={abholKnopf}
        >
            {statusFehler && !status ? (
                <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800 sm:flex-row sm:items-center">
                    <span className="flex-1">{statusFehler}</span>
                    <Button variant="outline" size="sm" onClick={() => void ladeStand()}>
                        <RefreshCw aria-hidden="true" className="h-4 w-4" />
                        Erneut laden
                    </Button>
                </div>
            ) : !status ? (
                <p role="status" className="rounded-lg bg-slate-100 p-6 text-slate-600 motion-safe:animate-pulse">Telefon-Stand wird geladen …</p>
            ) : nichtEingerichtet ? (
                <Card className="mx-auto flex max-w-xl flex-col items-center gap-3 p-10 text-center">
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
                        <Phone aria-hidden="true" className="h-6 w-6" />
                    </div>
                    <h2 className="text-lg font-semibold text-slate-900">Telefon ist noch nicht eingerichtet</h2>
                    {isAdmin ? (
                        <>
                            <p className="text-slate-500">
                                Verbinden Sie die FRITZ!Box in den Einstellungen. Danach erscheinen hier Anrufe und
                                Nachrichten vom Anrufbeantworter.
                            </p>
                            <Link
                                to="/einstellungen#telefon"
                                className="inline-flex items-center gap-2 rounded-lg border border-rose-200 bg-white px-4 py-2 text-sm font-medium text-rose-700 transition-colors hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            >
                                <Settings aria-hidden="true" className="h-4 w-4" />
                                Zu den Telefon-Einstellungen
                            </Link>
                        </>
                    ) : (
                        <p className="text-slate-500">Noch nicht eingerichtet – bitte an den Administrator wenden.</p>
                    )}
                </Card>
            ) : (
                <>
                    {/* Stand der letzten Abholung */}
                    <div className="flex flex-col gap-2 text-sm">
                        <p className="flex items-center gap-2 text-slate-500">
                            <Clock aria-hidden="true" className="h-4 w-4 shrink-0" />
                            Zuletzt abgeholt: {status.letzteAbholung ? formatVorZeit(status.letzteAbholung, jetzt) : 'noch nie'}
                        </p>
                        {status.letzterFehler && (
                            <p role="alert" className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-amber-800">
                                <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                                <span>Letzter Abruf hat nicht geklappt: {status.letzterFehler}</span>
                            </p>
                        )}
                        {ergebnis && (
                            <p
                                role="status"
                                className={cn(
                                    'flex items-center gap-2 rounded-lg border px-3 py-2',
                                    ergebnis.erfolgreich ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800',
                                )}
                            >
                                {ergebnis.erfolgreich
                                    ? <CheckCircle2 aria-hidden="true" className="h-4 w-4 shrink-0" />
                                    : <XCircle aria-hidden="true" className="h-4 w-4 shrink-0" />}
                                {ergebnisText(ergebnis)}
                            </p>
                        )}
                    </div>

                    {reiter === 'anrufe'
                        ? <AnrufListe anrufbeantworter={status.anrufbeantworter} aktualisierung={aktualisierung} />
                        : <AnrufbeantworterListe anrufbeantworter={status.anrufbeantworter} aktualisierung={aktualisierung} />}
                </>
            )}
        </PageLayout>
    );
}
