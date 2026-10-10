import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, Globe } from 'lucide-react';
import { useToast } from '../../../ui/toast';
import { cn } from '../../../../lib/utils';
import { parseErrorMessage } from '../../settingsApi';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';

/**
 * Wie kommen die Handys der Mitarbeiter von unterwegs an die Zeiterfassung?
 *
 * <p>Entweder mit der Tailscale-App (privates Firmennetz, Standard) oder über
 * Tailscale Funnel ohne App aus dem Internet. Den Funnel schaltet man am Server
 * ein; dieser Schalter entscheidet, ob das ERP solche Anfragen annimmt. Ist er
 * aus, weist das ERP jede Funnel-Anfrage ab.</p>
 */

interface ZugangStand {
    funnelFreigegeben: boolean;
    letzteFunnelAnfrage: string | null;
    letzteFunnelAnfrageAbgewiesen: boolean;
}

const API = '/api/settings/zeiterfassung-zugang';

const WEGE = [
    {
        funnel: false,
        titel: 'Mit der Tailscale-App (empfohlen)',
        text: 'Jedes Handy bekommt die Tailscale-App und ist damit im privaten Firmennetz. Das ERP bleibt komplett privat.',
    },
    {
        funnel: true,
        titel: 'Über Tailscale Funnel freigeben',
        text: 'Die Zeiterfassung ist ohne App aus dem Internet erreichbar. Nur die Zeiterfassung – der Rest des ERP bleibt gesperrt.',
    },
] as const;

function wann(iso: string): string {
    return new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' });
}

function Befehl({ children }: { children: string }) {
    return <code className="bg-slate-100 px-1.5 py-0.5 rounded font-mono text-slate-800">{children}</code>;
}

export function ZugangVonUnterwegsKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [stand, setStand] = useState<ZugangStand | null>(null);
    const [ladefehler, setLadefehler] = useState<string | null>(null);
    const [funnel, setFunnel] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const adresse = `${window.location.origin}/zeiterfassung`;

    const uebernimm = useCallback((daten: ZugangStand) => {
        setStand(daten);
        setFunnel(daten.funnelFreigegeben);
    }, []);

    useEffect(() => {
        let aktiv = true;
        (async () => {
            try {
                const res = await fetch(API);
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht geladen werden.'));
                const daten = (await res.json()) as ZugangStand;
                if (aktiv) uebernimm(daten);
            } catch (e) {
                if (aktiv) setLadefehler(e instanceof Error ? e.message : 'Die Einstellung konnte nicht geladen werden.');
            }
        })();
        return () => {
            aktiv = false;
        };
    }, [uebernimm]);

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(API, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ funnelFreigegeben: funnel }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Speichern fehlgeschlagen.'));
            uebernimm((await res.json()) as ZugangStand);
            toast.success(funnel
                ? 'Zeiterfassung über Tailscale Funnel freigegeben.'
                : 'Gespeichert: Die Mitarbeiter nutzen die Tailscale-App.');
            onSaved?.();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Speichern fehlgeschlagen.');
        } finally {
            setSpeichert(false);
        }
    };

    const karte = (inhalt: ReactNode) => (
        <SettingsCard
            icon={<Globe className="w-5 h-5 text-rose-600" />}
            title="Zugang von unterwegs"
            description={<p>Wie kommen die Handys der Mitarbeiter auf der Baustelle an die Zeiterfassung?</p>}
        >
            {inhalt}
        </SettingsCard>
    );

    if (ladefehler) return karte(<p className="text-sm text-red-600">{ladefehler}</p>);
    if (!stand) return karte(<SectionLoading />);

    // Am Server läuft ein Funnel, den hier keiner freigegeben hat.
    const fremderFunnel = !stand.funnelFreigegeben && stand.letzteFunnelAnfrage && stand.letzteFunnelAnfrageAbgewiesen;

    return karte(
        <>
            <fieldset className="m-0 min-w-0 border-0 p-0">
                <legend className="sr-only">Zugang von unterwegs</legend>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {WEGE.map((weg) => {
                        const an = funnel === weg.funnel;
                        return (
                            <label
                                key={weg.titel}
                                className={cn(
                                    'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors',
                                    an ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                                )}
                            >
                                <input
                                    type="radio"
                                    name="zeiterfassung-zugang"
                                    checked={an}
                                    onChange={() => setFunnel(weg.funnel)}
                                    className="mt-0.5 h-4 w-4 shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                                />
                                <span>
                                    <span className="block text-sm font-medium text-slate-900">{weg.titel}</span>
                                    <span className="block text-xs text-slate-500">{weg.text}</span>
                                </span>
                            </label>
                        );
                    })}
                </div>
            </fieldset>

            <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
                {funnel ? (
                    <ol className="list-decimal list-inside space-y-1">
                        <li>
                            Am Server einmal in der Eingabeaufforderung: <Befehl>tailscale funnel --bg 8080</Befehl>
                            {' '}– beim ersten Mal führt Tailscale durch das Einschalten von Funnel.
                        </li>
                        <li>Hier „Über Tailscale Funnel freigeben“ wählen und speichern.</li>
                        <li>
                            Testen: Handy <strong>ohne</strong> Tailscale im Mobilfunk, Adresse{' '}
                            <Befehl>{adresse}</Befehl> öffnen und den QR-Code scannen.
                        </li>
                        <li>
                            Zurück zur App-Lösung: am Server <Befehl>tailscale serve --bg 8080</Befehl>, dann hier umstellen.
                        </li>
                    </ol>
                ) : (
                    <ol className="list-decimal list-inside space-y-1">
                        <li>Tailscale-App auf dem Handy installieren (App Store bzw. Google Play).</li>
                        <li>Mit dem Firmen-Konto anmelden oder in der Tailscale-Verwaltung einladen lassen.</li>
                        <li>
                            Zeiterfassung öffnen: <Befehl>{adresse}</Befehl> und den QR-Code scannen.
                        </li>
                    </ol>
                )}
            </div>

            <p className="mt-3 text-xs text-slate-500">
                Letzte Anfrage über Funnel:{' '}
                {stand.letzteFunnelAnfrage
                    ? `${wann(stand.letzteFunnelAnfrage)} – ${stand.letzteFunnelAnfrageAbgewiesen ? 'abgewiesen' : 'angenommen'}`
                    : 'keine seit dem Start des ERP'}
            </p>

            {fremderFunnel && (
                <p className="mt-2 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>
                        Am Server läuft ein Funnel, der hier nicht freigegeben ist. Das ERP weist ihn ab. Ausschalten am
                        Server: <Befehl>tailscale serve --bg 8080</Befehl>
                    </span>
                </p>
            )}

            <SaveButton
                onClick={speichern}
                saving={speichert}
                disabled={funnel === stand.funnelFreigegeben}
                ariaLabel="Zugang von unterwegs speichern"
            >
                Speichern
            </SaveButton>
        </>,
    );
}
