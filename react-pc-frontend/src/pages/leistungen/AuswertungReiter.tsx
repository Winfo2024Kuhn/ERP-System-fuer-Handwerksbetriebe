import type { ReactNode } from 'react';
import { BarChart3, Briefcase, Clock, List, RefreshCw, Ruler } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { KategorieAnalyseInhalt } from '../../components/KategorieAnalyseInhalt';
import { AuswertungTabelle } from './AuswertungTabelle';
import { InfoStundenJeEinheit } from './InfoTexte';
import { JahrAuswahl } from './JahrAuswahl';
import { einheitJe, formatZahl } from './ordnerLogik';
import { useAuswertung } from './useAuswertung';
import type { Ordner } from './typen';

export interface AuswertungReiterProps {
    ordner: Ordner;
    hatUnterordner: boolean;
    jahr: string;
    onJahr: (jahr: string) => void;
    onWaehlen: (id: number) => void;
}

/**
 * Reiter „Auswertung“: Kennzahlen des Ordners (inkl. Unterordner), bei
 * Unterordnern deren Aufteilung als Balken, darunter die Zeit-/Mengenanalyse.
 * Grundlage sind nur abgeschlossene Projekte.
 */
export function AuswertungReiter({ ordner, hatUnterordner, jahr, onJahr, onWaehlen }: AuswertungReiterProps) {
    // Die eigene Zeile steht in der Auswertung des übergeordneten Ordners (bzw. der Hauptordner).
    const eigene = useAuswertung(jahr, ordner.parentId);
    const unter = useAuswertung(jahr, ordner.id, hatUnterordner);
    const zeile = eigene.zeilen?.find((z) => z.kategorieId === ordner.id) ?? null;
    const je = einheitJe(zeile?.verrechnungseinheit ?? ordner.einheitAnzeige);
    const leer = zeile !== null && zeile.projektAnzahl === 0;

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <JahrAuswahl jahr={jahr} onJahr={onJahr} />
                <p className="text-xs text-slate-500">Nur abgeschlossene Projekte · inkl. Unterordner</p>
            </div>

            {eigene.laedt && (
                <div role="status" aria-label="Kennzahlen werden geladen" className="grid grid-cols-2 gap-4 xl:grid-cols-4">
                    {[0, 1, 2, 3].map((i) => <div key={i} className="h-24 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
                </div>
            )}
            {!eigene.laedt && eigene.fehler && <Fehlerkarte text={eigene.fehler} onErneut={eigene.erneut} />}
            {!eigene.laedt && zeile && (
                <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
                    <Karte icon={<Briefcase className="h-4 w-4" />} titel="Abgeschlossene Projekte" wert={formatZahl(zeile.projektAnzahl, 0)} />
                    <Karte icon={<Clock className="h-4 w-4" />} titel="Stunden" wert={`${formatZahl(zeile.stunden, 1)} Std.`} />
                    <Karte
                        icon={<Ruler className="h-4 w-4" />}
                        titel={<span className="inline-flex items-center gap-0.5">Stunden je {je} <InfoStundenJeEinheit /></span>}
                        wert={zeile.stundenProEinheit !== null ? `${formatZahl(zeile.stundenProEinheit, 2)} Std.` : '—'}
                        zusatz={zeile.stundenProEinheit === null ? 'Noch keine Mengen erfasst' : `bei ${formatZahl(zeile.menge, 1)} ${je} gesamt`}
                    />
                    <Karte icon={<List className="h-4 w-4" />} titel="Leistungen im Ordner" wert={formatZahl(zeile.leistungAnzahl, 0)} />
                </div>
            )}

            {leer && (
                <div className="rounded-lg border border-dashed border-slate-200 p-8 text-center">
                    <BarChart3 className="mx-auto mb-2 h-10 w-10 text-rose-200" aria-hidden="true" />
                    <p className="font-medium text-slate-700">
                        Noch keine abgeschlossenen Projekte mit Zeiten in diesem Ordner{jahr ? ` (${jahr})` : ''}.
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                        Sobald ein Projekt mit Leistungen aus diesem Ordner abgeschlossen ist, erscheinen hier Stunden und Richtwerte.
                    </p>
                </div>
            )}

            {hatUnterordner && (
                <section className="space-y-2" aria-label="Aufteilung nach Unterordnern">
                    <h3 className="text-sm font-semibold text-slate-900">Aufteilung nach Unterordnern</h3>
                    {unter.laedt && <div role="status" aria-label="Aufteilung wird geladen" className="h-32 rounded-lg bg-slate-100 motion-safe:animate-pulse" />}
                    {!unter.laedt && unter.fehler && <Fehlerkarte text={unter.fehler} onErneut={unter.erneut} />}
                    {!unter.laedt && unter.zeilen && unter.zeilen.length > 0 && (
                        <AuswertungTabelle zeilen={unter.zeilen} beschriftung={`Unterordner von „${ordner.name}“`} onWaehlen={onWaehlen} />
                    )}
                </section>
            )}

            {!leer && !eigene.laedt && !eigene.fehler && (
                <section className="space-y-2" aria-label="Zeit- und Mengenanalyse">
                    <h3 className="text-sm font-semibold text-slate-900">Zeit nach Menge</h3>
                    <KategorieAnalyseInhalt kategorieId={ordner.id} jahr={jahr} />
                </section>
            )}
        </div>
    );
}

function Karte({ icon, titel, wert, zusatz }: { icon: ReactNode; titel: ReactNode; wert: string; zusatz?: string }) {
    return (
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex items-center gap-2 text-xs text-slate-500">
                <span className="flex h-7 w-7 items-center justify-center rounded-md bg-rose-50 text-rose-600" aria-hidden="true">{icon}</span>
                {titel}
            </div>
            <p className="mt-2 text-2xl font-bold tabular-nums text-slate-900">{wert}</p>
            {zusatz && <p className="text-xs text-slate-500">{zusatz}</p>}
        </div>
    );
}

export function Fehlerkarte({ text, onErneut }: { text: string; onErneut: () => void }) {
    return (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4">
            <p className="text-sm text-rose-800">{text}</p>
            <Button size="sm" variant="outline" onClick={onErneut}>
                <RefreshCw className="h-4 w-4" aria-hidden="true" /> Erneut versuchen
            </Button>
        </div>
    );
}
