import { useId, useRef, useState, type ComponentType } from 'react';
import { ArrowLeftRight, CheckCircle2, CircleHelp, ListChecks, PowerOff, SlidersHorizontal } from 'lucide-react';
import { Button } from '../../../ui/button';
import { cn } from '../../../../lib/utils';
import { SettingsCard } from '../../settingsUi';
import { anbieterName, faehigkeitText } from '../../../../features/ki/kiEinstellungenApi';
import type { KiEinstellungen, KiFunktion, KiFunktionStatus } from '../../../../features/ki/kiEinstellungenApi';
import type { KiModellListeZustand } from '../../../../features/ki/useKiModellListe';
import { KiFunktionWahl } from './KiFunktionWahl';
import { KiFunktionenListeInfo } from './kiInfos';

interface StatusAnzeige {
    text: string;
    icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean | 'true' }>;
    klassen: string;
}

/**
 * Status-Plakette: Farbe, Symbol UND Wort – die Aussage hängt nie allein an
 * der Farbe. „Aus“ ist ruhig grau statt alarmrot: Es ist meist eine bewusste
 * Entscheidung (kein Schlüssel, kein Ersatz), kein Fehler.
 */
const STATUS: Record<string, StatusAnzeige> = {
    AKTIV: { text: 'Läuft', icon: CheckCircle2, klassen: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
    ERSATZ: { text: 'Ersatz', icon: ArrowLeftRight, klassen: 'border-amber-200 bg-amber-50 text-amber-800' },
    AUS: { text: 'Aus', icon: PowerOff, klassen: 'border-slate-300 bg-slate-100 text-slate-700' },
};

function statusAnzeige(status: KiFunktionStatus): StatusAnzeige {
    return STATUS[status] ?? { text: status, icon: CircleHelp, klassen: 'border-slate-200 bg-white text-slate-700' };
}

export function KiStatusPlakette({ status }: { status: KiFunktionStatus }) {
    const { text, icon: Icon, klassen } = statusAnzeige(status);
    return (
        <span className={cn('inline-flex shrink-0 items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold', klassen)}>
            <Icon aria-hidden="true" className="h-3.5 w-3.5" />
            {text}
        </span>
    );
}

/** Kurze Zählung über der Liste, damit man den Stand ohne Durchlesen erfasst. */
function Zusammenfassung({ funktionen }: { funktionen: KiFunktion[] }) {
    const anzahl = (status: string) => funktionen.filter((f) => f.status === status).length;
    const teile = [
        { status: 'AKTIV', zahl: anzahl('AKTIV') },
        { status: 'ERSATZ', zahl: anzahl('ERSATZ') },
        { status: 'AUS', zahl: anzahl('AUS') },
    ].filter((t) => t.zahl > 0);
    return (
        <p className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-600" data-testid="ki-funktionen-zusammenfassung">
            {teile.map((t) => (
                <span key={t.status} className="inline-flex items-center gap-1.5">
                    <KiStatusPlakette status={t.status} />
                    <span className="tabular-nums">{t.zahl} {t.zahl === 1 ? 'Funktion' : 'Funktionen'}</span>
                </span>
            ))}
        </p>
    );
}

interface KiFunktionenListeProps {
    einstellungen: KiEinstellungen;
    modellListe: KiModellListeZustand;
    onAenderung: (stand: KiEinstellungen) => void;
}

/**
 * „Welche Funktion nutzt was?“ – die Hauptstelle der KI-Einstellungen: für
 * jede KI-Funktion, ob sie läuft, über welchen Anbieter und welches Modell,
 * und wenn nicht, warum. Über „Ändern“ lässt sich je Funktion ein eigener
 * Anbieter und ein eigenes Modell festlegen.
 *
 * <p>Die Liste rechnet nichts selbst aus, sie zeigt den Stand, den der Server
 * nach jeder Änderung mitschickt. Damit ist sie keine Blackbox: Was hier
 * steht, ist das, was beim nächsten Aufruf wirklich passiert.</p>
 */
export function KiFunktionenListe({ einstellungen, modellListe, onAenderung }: KiFunktionenListeProps) {
    const { funktionen } = einstellungen;
    // Höchstens eine Funktion gleichzeitig im Bearbeiten – so bleibt die Liste übersichtlich.
    const [offen, setOffen] = useState<string | null>(null);
    return (
        <SettingsCard
            icon={<ListChecks className="w-5 h-5 text-rose-600" />}
            title="Welche Funktion nutzt was?"
            info={<KiFunktionenListeInfo />}
            description={
                <p>
                    So laufen die KI-Funktionen gerade – mit Anbieter und Modell. Mit „Ändern“ legen Sie für eine
                    Funktion einen eigenen Anbieter oder ein eigenes Modell fest.
                </p>
            }
        >
            {funktionen.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-200 p-4 text-sm text-slate-500">
                    Der Server hat keine KI-Funktionen gemeldet.
                </p>
            ) : (
                <>
                    <Zusammenfassung funktionen={funktionen} />
                    <ul aria-label="KI-Funktionen" className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                        {funktionen.map((f) => (
                            <FunktionZeile
                                key={f.funktion}
                                funktion={f}
                                einstellungen={einstellungen}
                                modellListe={modellListe}
                                onAenderung={onAenderung}
                                offen={offen === f.funktion}
                                onOffen={(auf) => setOffen(auf ? f.funktion : null)}
                            />
                        ))}
                    </ul>
                </>
            )}
        </SettingsCard>
    );
}

interface FunktionZeileProps extends KiFunktionenListeProps {
    funktion: KiFunktion;
    offen: boolean;
    onOffen: (offen: boolean) => void;
}

function eigeneWahlText(funktion: KiFunktion, einstellungen: KiEinstellungen): string {
    const wahl = funktion.eigeneWahl;
    if (!wahl) return 'Folgt dem Standard-Anbieter';
    const name = anbieterName(einstellungen, wahl.anbieter);
    return `Eigene Wahl: ${name}${wahl.modell ? ` · ${wahl.modell}` : ' mit Standard-Modell'}`;
}

function FunktionZeile({ funktion, einstellungen, modellListe, onAenderung, offen, onOffen }: FunktionZeileProps) {
    const panelId = useId();
    const knopfRef = useRef<HTMLButtonElement>(null);
    const { name, braucht, status, anbieterName: wirksam, modell, hinweis } = funktion;
    const schliessen = () => {
        onOffen(false);
        knopfRef.current?.focus();
    };
    return (
        <li className="grid gap-x-4 gap-y-1.5 px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto]" aria-label={name}>
            <div className="min-w-0">
                <p className="font-medium text-slate-900 break-words">{name}</p>
                <p className="text-xs text-slate-500">
                    {braucht.length > 0 && <>Braucht: {braucht.map(faehigkeitText).join(', ')} · </>}
                    <span className={cn(funktion.eigeneWahl && 'font-medium text-rose-700')}>{eigeneWahlText(funktion, einstellungen)}</span>
                </p>
            </div>
            <div className="flex min-w-0 flex-wrap items-center gap-2 sm:justify-end">
                <KiStatusPlakette status={status} />
                {wirksam ? (
                    <span className="min-w-0 text-sm text-slate-700 break-words">
                        {wirksam}
                        {modell && (
                            <>
                                <span aria-hidden="true" className="mx-1 text-slate-400">·</span>
                                <span className="sr-only">, Modell </span>
                                <span className="font-mono text-xs text-slate-600 break-all">{modell}</span>
                            </>
                        )}
                    </span>
                ) : (
                    <span className="text-sm text-slate-500">Kein Anbieter</span>
                )}
                <Button
                    ref={knopfRef}
                    variant="outline"
                    size="sm"
                    aria-expanded={offen}
                    aria-controls={offen ? panelId : undefined}
                    aria-label={`Ändern: ${name}`}
                    onClick={() => onOffen(!offen)}
                >
                    <SlidersHorizontal aria-hidden="true" className="h-4 w-4" />
                    Ändern
                </Button>
            </div>
            {hinweis && (
                <p className={cn('text-sm sm:col-span-2', status === 'AUS' ? 'text-slate-700' : 'text-amber-800')}>
                    {hinweis}
                </p>
            )}
            {offen && (
                <KiFunktionWahl
                    id={panelId}
                    funktion={funktion}
                    einstellungen={einstellungen}
                    modellListe={modellListe}
                    onAenderung={onAenderung}
                    onSchliessen={schliessen}
                />
            )}
        </li>
    );
}
