import { forwardRef, useId, useState } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import { AlertTriangle, ChevronDown, Globe, KeyRound, Loader2, Lock, PlugZap, RotateCcw, Save, ShieldCheck, Trash2 } from 'lucide-react';
import { Button } from '../../../ui/button';
import { Label } from '../../../ui/label';
import { useToast } from '../../../ui/toast';
import { useConfirm } from '../../../ui/confirm-dialog';
import { cn } from '../../../../lib/utils';
import { PasswordField, TestResultBanner } from '../../settingsUi';
import type { TestResult } from '../../settingsApi';
import {
    datenstandortText,
    entferneKiSchluessel,
    faehigkeitText,
    hatEigeneModelle,
    modellFeldwert,
    pruefeModellname,
    speichereKiModelle,
    speichereKiSchluessel,
    stufeText,
    testeKiAnbieter,
} from '../../../../features/ki/kiEinstellungenApi';
import type { KiAnbieter, KiEinstellungen, KiFaehigkeit, KiModellAuswahl } from '../../../../features/ki/kiEinstellungenApi';
import { KiModelleInfo, KiSchluesselInfo } from './kiInfos';
import { KiModellSuche } from './KiModellSuche';
import type { KiModellListeZustand } from '../../../../features/ki/useKiModellListe';

interface KiAnbieterKarteProps {
    anbieter: KiAnbieter;
    gewaehlt: boolean;
    /** Wird gerade auf diesen Anbieter umgestellt? */
    waehltGerade: boolean;
    /** Ist irgendeine Umstellung unterwegs? Dann nimmt die Karte keine Wahl an. */
    wahlGesperrt: boolean;
    /** Was der Anbieter in diesem Programm (noch) nicht kann. */
    fehlend: KiFaehigkeit[];
    verschluesselungBereit: boolean;
    /** Roving Tabindex der Auswahlgruppe: nur eine Karte ist per Tab erreichbar. */
    tabIndex: number;
    onWaehlen: () => void;
    onPfeilTaste: (event: KeyboardEvent<HTMLButtonElement>) => void;
    /** Neuer Stand vom Server nach Schlüssel- oder Modelländerung. */
    onAenderung: (stand: KiEinstellungen) => void;
    /** Live-Modellliste der Anbieter, von allen Modellsuchen der Seite geteilt. */
    modellListe: KiModellListeZustand;
}

/**
 * Eine Karte je KI-Anbieter: oben die Wahl (Radio), darunter Schlüssel und
 * Modelle dieses Anbieters.
 *
 * <p>Die Wahl ist ein eigener Knopf mit `role="radio"`, nicht die ganze
 * Karte – sonst lägen Eingabefelder und Knöpfe in einem Radio, was weder
 * Tastatur noch Screenreader verstehen. Schlüssel und Modelle lassen sich
 * auch für einen nicht gewählten Anbieter pflegen, damit man ihn vor dem
 * Umschalten (oder als Ersatz) einrichten kann.</p>
 */
export const KiAnbieterKarte = forwardRef<HTMLButtonElement, KiAnbieterKarteProps>(function KiAnbieterKarte({
    anbieter,
    gewaehlt,
    waehltGerade,
    wahlGesperrt,
    fehlend,
    verschluesselungBereit,
    tabIndex,
    onWaehlen,
    onPfeilTaste,
    onAenderung,
    modellListe,
}, ref) {
    const id = useId();
    const nameId = `${id}-name`;
    const beschreibungId = `${id}-beschreibung`;
    const standort = datenstandortText(anbieter.datenstandort);

    return (
        <div
            className={cn(
                'rounded-lg border bg-white transition-colors',
                gewaehlt ? 'border-rose-300 ring-1 ring-rose-200' : 'border-slate-200',
            )}
            data-testid={`ki-anbieter-${anbieter.typ}`}
        >
            <button
                ref={ref}
                type="button"
                role="radio"
                aria-checked={gewaehlt}
                aria-labelledby={nameId}
                aria-describedby={beschreibungId}
                aria-busy={waehltGerade || undefined}
                tabIndex={tabIndex}
                onClick={() => {
                    if (!gewaehlt && !wahlGesperrt) onWaehlen();
                }}
                onKeyDown={onPfeilTaste}
                className={cn(
                    'flex w-full items-start gap-3 rounded-t-lg p-4 text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                    gewaehlt ? 'bg-rose-50/60 cursor-default' : 'hover:bg-slate-50 cursor-pointer',
                    wahlGesperrt && !gewaehlt && 'cursor-wait',
                )}
            >
                <span
                    aria-hidden="true"
                    className={cn(
                        'mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2',
                        gewaehlt ? 'border-rose-600' : 'border-slate-300',
                    )}
                >
                    {gewaehlt && <span className="h-2.5 w-2.5 rounded-full bg-rose-600" />}
                </span>
                <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                        <span id={nameId} className="font-semibold text-slate-900">{anbieter.name}</span>
                        {gewaehlt && <Plakette klassen="border-rose-200 bg-white text-rose-700">Gewählt</Plakette>}
                        {standort && (
                            <Plakette klassen={anbieter.datenstandort === 'EU'
                                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                                : 'border-amber-200 bg-amber-50 text-amber-800'}
                            >
                                {anbieter.datenstandort === 'EU'
                                    ? <ShieldCheck aria-hidden="true" className="h-3.5 w-3.5" />
                                    : <Globe aria-hidden="true" className="h-3.5 w-3.5" />}
                                {standort}
                            </Plakette>
                        )}
                        {anbieter.schluesselGesetzt ? (
                            <Plakette klassen="border-emerald-200 bg-emerald-50 text-emerald-800">
                                <KeyRound aria-hidden="true" className="h-3.5 w-3.5" />
                                Schlüssel hinterlegt
                            </Plakette>
                        ) : anbieter.schluesselNichtLesbar ? (
                            <Plakette klassen="border-red-200 bg-red-50 text-red-800">
                                <KeyRound aria-hidden="true" className="h-3.5 w-3.5" />
                                Schlüssel nicht lesbar
                            </Plakette>
                        ) : (
                            <Plakette klassen="border-slate-300 bg-slate-100 text-slate-700">
                                <KeyRound aria-hidden="true" className="h-3.5 w-3.5" />
                                Kein Schlüssel
                            </Plakette>
                        )}
                        {waehltGerade && (
                            <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                                <Loader2 aria-hidden="true" className="h-3.5 w-3.5 motion-safe:animate-spin" />
                                Wird umgestellt …
                            </span>
                        )}
                    </span>
                    <span id={beschreibungId} className="mt-1 block text-sm text-slate-500">
                        {anbieter.beschreibung}
                        <span className="mt-2 block text-xs text-slate-600">
                            <span className="font-medium text-slate-700">Kann: </span>
                            {anbieter.faehigkeiten.length > 0 ? anbieter.faehigkeiten.map(faehigkeitText).join(', ') : '–'}
                        </span>
                        {fehlend.length > 0 && (
                            <span className="block text-xs text-slate-600">
                                <span className="font-medium text-slate-700">Kann in diesem Programm noch nicht: </span>
                                {fehlend.map(faehigkeitText).join(', ')}
                            </span>
                        )}
                    </span>
                </span>
            </button>

            <div className="space-y-4 border-t border-slate-100 p-4">
                <SchluesselBereich anbieter={anbieter} verschluesselungBereit={verschluesselungBereit} onAenderung={onAenderung} />
                <ModelleBereich anbieter={anbieter} onAenderung={onAenderung} modellListe={modellListe} />
            </div>
        </div>
    );
});

function Plakette({ klassen, children }: { klassen: string; children: ReactNode }) {
    return (
        <span className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium', klassen)}>
            {children}
        </span>
    );
}

interface BereichProps {
    anbieter: KiAnbieter;
    onAenderung: (stand: KiEinstellungen) => void;
}

interface ModelleProps extends BereichProps {
    modellListe: KiModellListeZustand;
}

/** Schlüssel eintragen, testen, speichern und entfernen. */
function SchluesselBereich({ anbieter, verschluesselungBereit, onAenderung }: BereichProps & { verschluesselungBereit: boolean }) {
    const toast = useToast();
    const confirm = useConfirm();
    const id = useId();
    const [apiKey, setApiKey] = useState('');
    const [saving, setSaving] = useState(false);
    const [removing, setRemoving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);

    // Der Server kann diesen Schlüssel nur verschlüsselt speichern, ist dafür aber nicht eingerichtet.
    const gesperrt = anbieter.schluesselVerschluesselt && !verschluesselungBereit;
    const sperrHinweisId = `${id}-sperre`;
    const beschaeftigt = saving || removing;

    const speichern = () => {
        const wert = apiKey.trim();
        if (!wert) {
            toast.error('Bitte einen Schlüssel eintragen.');
            return;
        }
        setSaving(true);
        speichereKiSchluessel(anbieter.typ, wert)
            .then((stand) => {
                toast.success(`Schlüssel für ${anbieter.name} gespeichert.`);
                setApiKey('');
                setTestResult(null);
                onAenderung(stand);
            })
            .catch((e: unknown) => toast.error(e instanceof Error ? e.message : 'Der Schlüssel konnte nicht gespeichert werden.'))
            .finally(() => setSaving(false));
    };

    const testen = () => {
        setTesting(true);
        setTestResult(null);
        testeKiAnbieter(anbieter.typ, apiKey.trim() || null)
            .then((ergebnis) => {
                setTestResult(ergebnis);
                if (ergebnis.success) toast.success(ergebnis.message);
                else toast.error(ergebnis.message);
            })
            .finally(() => setTesting(false));
    };

    const entfernen = async () => {
        const ok = await confirm({
            title: `Schlüssel für ${anbieter.name} entfernen?`,
            message: `Danach kann ${anbieter.name} nicht mehr genutzt werden, bis Sie wieder einen Schlüssel speichern. `
                + 'Die Funktionen springen auf einen Ersatz um oder sind aus. Schon ausgelesene Daten bleiben erhalten.',
            confirmLabel: 'Schlüssel entfernen',
            variant: 'danger',
        });
        if (!ok) return;
        setRemoving(true);
        try {
            const stand = await entferneKiSchluessel(anbieter.typ);
            toast.success(`Schlüssel für ${anbieter.name} entfernt.`);
            setApiKey('');
            setTestResult(null);
            onAenderung(stand);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Schlüssel konnte nicht entfernt werden.');
        } finally {
            setRemoving(false);
        }
    };

    const testGrund = !apiKey.trim() && !anbieter.schluesselGesetzt
        ? 'Bitte zuerst einen Schlüssel eintragen.'
        : undefined;
    const speichernGrund = gesperrt
        ? 'Der Server kann diesen Schlüssel noch nicht verschlüsselt speichern.'
        : (!apiKey.trim() ? 'Bitte zuerst einen Schlüssel eintragen.' : undefined);

    return (
        <div>
            {gesperrt && (
                <p id={sperrHinweisId} className="mb-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                    <Lock aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                    Der Server ist noch nicht für verschlüsselte Zugangsdaten eingerichtet. Bitte den Betreuer des Programms fragen.
                </p>
            )}
            <div className="flex flex-col gap-2 lg:flex-row lg:items-end">
                <PasswordField
                    id={`${id}-schluessel`}
                    label={`Schlüssel für ${anbieter.name}`}
                    value={apiKey}
                    onChange={setApiKey}
                    isSet={anbieter.schluesselGesetzt}
                    placeholder="Schlüssel hier einfügen"
                    disabled={gesperrt}
                    beschreibungId={gesperrt ? sperrHinweisId : undefined}
                    info={anbieter.schluesselVerschluesselt ? <KiSchluesselInfo anbieterName={anbieter.name} /> : undefined}
                    className="flex-1 lg:max-w-md"
                />
                <div className="flex flex-wrap gap-2">
                    <span title={testGrund}>
                        <Button
                            variant="outline"
                            onClick={testen}
                            disabled={testing || !!testGrund}
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        >
                            {testing
                                ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                                : <PlugZap aria-hidden="true" className="w-4 h-4" />}
                            {testing ? 'Wird geprüft …' : 'Verbindung testen'}
                        </Button>
                    </span>
                    <span title={speichernGrund}>
                        <Button onClick={speichern} disabled={beschaeftigt || !!speichernGrund}>
                            {saving
                                ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                                : <Save aria-hidden="true" className="w-4 h-4" />}
                            {saving ? 'Wird gespeichert …' : 'Schlüssel speichern'}
                        </Button>
                    </span>
                </div>
            </div>

            <TestResultBanner result={testResult} className="mt-3" />

            {anbieter.schluesselNichtLesbar && !anbieter.schluesselGesetzt && (
                <p role="alert" className="mt-3 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>
                        Der gespeicherte Schlüssel lässt sich auf diesem Server nicht entschlüsseln – meist, weil der
                        Server neu aufgesetzt oder umgezogen wurde. Bitte den Schlüssel oben neu eintragen. Bis dahin
                        ist {anbieter.name} aus, und kein anderer Anbieter springt dafür ein.
                    </span>
                </p>
            )}

            {anbieter.schluesselGesetzt && (
                <div className="mt-2">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => void entfernen()}
                        disabled={beschaeftigt}
                        aria-label={`Schlüssel für ${anbieter.name} entfernen`}
                        className="text-red-600 hover:bg-red-50 hover:text-red-700"
                    >
                        {removing
                            ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                            : <Trash2 aria-hidden="true" className="w-4 h-4" />}
                        {removing ? 'Wird entfernt …' : 'Schlüssel entfernen'}
                    </Button>
                </div>
            )}
        </div>
    );
}

/**
 * Aufklappbar unter „Erweitert“: die drei Standard-Modelle (Schnell/Standard/Gründlich)
 * des Anbieters – sie gelten für alle Funktionen, die ihm ohne eigenes Modell folgen.
 */
function ModelleBereich({ anbieter, onAenderung, modellListe }: ModelleProps) {
    const id = useId();
    const panelId = `${id}-modelle`;
    const [offen, setOffen] = useState(false);

    if (anbieter.modelle.length === 0) return null;

    return (
        <div>
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <button
                    type="button"
                    aria-expanded={offen}
                    aria-controls={panelId}
                    onClick={() => setOffen((o) => !o)}
                    className="-ml-1 inline-flex items-center gap-1 rounded px-1 py-0.5 text-sm font-medium text-slate-800 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    <ChevronDown aria-hidden="true" className={cn('h-4 w-4 transition-transform', !offen && '-rotate-90')} />
                    Erweitert: Standard-Modelle
                </button>
                <KiModelleInfo anbieterName={anbieter.name} />
                <span className="text-xs text-slate-500">
                    {hatEigeneModelle(anbieter) ? 'Eigene Modelle eingetragen' : 'Standard-Modelle des Programms'}
                </span>
            </div>
            {offen && (
                <ModelleFormular
                    // Neu aufbauen, sobald der Server andere Modelle meldet – so stehen nach dem
                    // Speichern immer die tatsächlich gültigen Namen im Formular.
                    key={anbieter.modelle.map((m) => `${m.stufe}=${m.modell}`).join('|')}
                    id={panelId}
                    anbieter={anbieter}
                    onAenderung={onAenderung}
                    modellListe={modellListe}
                />
            )}
        </div>
    );
}

/** Die drei Modell-Felder mit Prüfung, „Speichern“ und „Standard wiederherstellen“. */
function ModelleFormular({ id, anbieter, onAenderung, modellListe }: ModelleProps & { id: string }) {
    const toast = useToast();
    const eigene = hatEigeneModelle(anbieter);
    const [entwurf, setEntwurf] = useState<KiModellAuswahl>(
        () => Object.fromEntries(anbieter.modelle.map((m) => [m.stufe, modellFeldwert(m)])),
    );
    const [speichert, setSpeichert] = useState<'speichern' | 'zuruecksetzen' | null>(null);

    const geaendert = anbieter.modelle.some((m) => (entwurf[m.stufe] ?? '').trim() !== modellFeldwert(m));

    const senden = (auswahl: KiModellAuswahl, art: 'speichern' | 'zuruecksetzen') => {
        setSpeichert(art);
        speichereKiModelle(anbieter.typ, auswahl)
            .then((stand) => {
                toast.success(art === 'speichern'
                    ? `Modelle für ${anbieter.name} gespeichert. Prüfen Sie sie mit „Verbindung testen“.`
                    : `Standard-Modelle für ${anbieter.name} wiederhergestellt.`);
                onAenderung(stand);
            })
            .catch((e: unknown) => toast.error(e instanceof Error ? e.message : 'Die Modelle konnten nicht gespeichert werden.'))
            .finally(() => setSpeichert(null));
    };

    const speichern = () => {
        // Frei eingetragene Namen prüft schon die Suche; hier nur zur Sicherheit vor dem Senden.
        const fehler = anbieter.modelle.map((m) => pruefeModellname(entwurf[m.stufe] ?? '')).find(Boolean);
        if (fehler) {
            toast.error(fehler);
            return;
        }
        senden(Object.fromEntries(anbieter.modelle.map((m) => [m.stufe, (entwurf[m.stufe] ?? '').trim()])), 'speichern');
    };

    const zuruecksetzen = () => {
        senden(Object.fromEntries(anbieter.modelle.map((m) => [m.stufe, ''])), 'zuruecksetzen');
    };

    return (
        <div id={id} className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="grid gap-3 md:grid-cols-3">
                {anbieter.modelle.map((m) => {
                    const feldId = `${id}-${m.stufe}`;
                    return (
                        <div key={m.stufe} className="min-w-0">
                            <Label htmlFor={feldId}>{stufeText(m.stufe)}</Label>
                            <KiModellSuche
                                id={feldId}
                                anbieter={anbieter}
                                wert={entwurf[m.stufe] ?? ''}
                                onChange={(wert) => setEntwurf((alt) => ({ ...alt, [m.stufe]: wert }))}
                                standardText={`Standard (${m.standardModell})`}
                                modellListe={modellListe}
                            />
                        </div>
                    );
                })}
            </div>
            <p className="mt-3 text-xs text-slate-500">
                Ohne Auswahl gilt das Standard-Modell des Programms. Die Änderung gilt sofort für alle Funktionen,
                die diesem Anbieter ohne eigenes Modell folgen.
            </p>
            <div className="mt-3 flex flex-wrap justify-end gap-2">
                <span title={!eigene ? 'Es gelten schon die Standard-Modelle.' : undefined}>
                    <Button variant="outline" size="sm" onClick={zuruecksetzen} disabled={!eigene || speichert !== null}>
                        {speichert === 'zuruecksetzen'
                            ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                            : <RotateCcw aria-hidden="true" className="w-4 h-4" />}
                        Standard wiederherstellen
                    </Button>
                </span>
                <span title={!geaendert ? 'Es wurde nichts geändert.' : undefined}>
                    <Button size="sm" onClick={speichern} disabled={!geaendert || speichert !== null}>
                        {speichert === 'speichern'
                            ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                            : <Save aria-hidden="true" className="w-4 h-4" />}
                        Modelle speichern
                    </Button>
                </span>
            </div>
        </div>
    );
}
