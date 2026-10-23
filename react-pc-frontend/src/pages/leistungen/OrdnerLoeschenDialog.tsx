import { useId, useState, type ReactNode } from 'react';
import { AlertTriangle, Briefcase, Clock, Folder, List, Loader2, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { InfoAllesLoeschen, InfoUmziehen } from './InfoTexte';
import { OrdnerZielAuswahl } from './OrdnerZielAuswahl';
import { anzahlText, weitereAnzahl } from './ordnerLogik';
import type { Loeschpruefung, LoeschModus, Ordner, ZielEintrag } from './typen';

export interface OrdnerLoeschenDialogProps {
    pruefung: Loeschpruefung;
    /** Der Ordner selbst – für die Einheit. */
    ordner: Ordner | undefined;
    ziele: ZielEintrag[];
    /** Ordner nach ID – für den Einheiten-Hinweis beim Ziel. */
    ordnerVon: (id: number) => Ordner | undefined;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog. */
    onLoeschen: (modus: LoeschModus, zielId: number | null) => Promise<void>;
}

/**
 * Rückfrage, wenn ein Ordner noch verwendet wird: zeigt, WAS drinsteckt
 * (Projekte, Leistungen, Unterordner, Arbeitszeiten), und bietet zwei Wege an –
 * empfohlen das Umziehen in einen anderen Ordner, sonst das endgültige Löschen.
 */
export function OrdnerLoeschenDialog({ pruefung, ordner, ziele, ordnerVon, onAbbrechen, onLoeschen }: OrdnerLoeschenDialogProps) {
    const toast = useToast();
    const titelId = useId();
    const [modus, setModus] = useState<LoeschModus>('UMZIEHEN');
    const [zielId, setZielId] = useState<number | null | undefined>(undefined);
    const [sendet, setSendet] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);

    const name = pruefung.bezeichnung;
    const ziel = typeof zielId === 'number' ? ordnerVon(zielId) : undefined;
    const andereEinheit = modus === 'UMZIEHEN' && ziel && ordner?.einheit && ziel.einheit && ziel.einheit !== ordner.einheit;
    const keinZiel = ziele.every((z) => z.gesperrt !== null);
    const bereit = modus === 'ALLES' || typeof zielId === 'number';

    const loeschen = async () => {
        if (!bereit) return;
        setSendet(true);
        setFehler(null);
        try {
            await onLoeschen(modus, modus === 'UMZIEHEN' ? (zielId as number) : null);
        } catch (err) {
            const meldung = err instanceof Error ? err.message : 'Der Ordner konnte nicht gelöscht werden.';
            setFehler(meldung);
            toast.error(meldung);
            setSendet(false);
        }
    };

    const folgen: string[] = [];
    if (pruefung.unterordnerAnzahl > 0) folgen.push(anzahlText(pruefung.unterordnerAnzahl, 'Unterordner', 'Unterordner'));
    if (pruefung.leistungAnzahl > 0) folgen.push(anzahlText(pruefung.leistungAnzahl, 'Leistung', 'Leistungen'));

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-4xl" aria-labelledby={titelId}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id={titelId}>„{name}“ wird noch verwendet</DialogTitle>
                    <DialogDescription>
                        In diesem Ordner steckt noch etwas. Damit nichts verloren geht, entscheiden Sie, was damit passieren soll.
                    </DialogDescription>
                </DialogHeader>

                <div className="-mx-1 min-h-0 flex-1 overflow-y-auto px-1">
                    <div className="grid gap-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
                        <section aria-label="Was steckt im Ordner?" className="space-y-4">
                            <div className="flex flex-wrap gap-2 text-xs">
                                {pruefung.projektAnzahl > 0 && <Zaehlchip icon={<Briefcase className="h-3.5 w-3.5" />} text={anzahlText(pruefung.projektAnzahl, 'Projekt', 'Projekte')} />}
                                {pruefung.leistungAnzahl > 0 && <Zaehlchip icon={<List className="h-3.5 w-3.5" />} text={anzahlText(pruefung.leistungAnzahl, 'Leistung', 'Leistungen')} />}
                                {pruefung.unterordnerAnzahl > 0 && <Zaehlchip icon={<Folder className="h-3.5 w-3.5" />} text={anzahlText(pruefung.unterordnerAnzahl, 'Unterordner', 'Unterordner')} />}
                                {pruefung.zeitbuchungAnzahl > 0 && <Zaehlchip icon={<Clock className="h-3.5 w-3.5" />} text={anzahlText(pruefung.zeitbuchungAnzahl, 'Arbeitszeit', 'Arbeitszeiten')} />}
                            </div>
                            <Liste
                                titel="Projekte"
                                gesamt={pruefung.projektAnzahl}
                                eintraege={pruefung.projekte.map((p) => ({
                                    id: p.id,
                                    haupt: p.bauvorhaben || 'Ohne Bauvorhaben',
                                    neben: [p.auftragsnummer, p.kunde].filter(Boolean).join(' · '),
                                }))}
                            />
                            <Liste titel="Leistungen" gesamt={pruefung.leistungAnzahl}
                                eintraege={pruefung.leistungen.map((l) => ({ id: l.id, haupt: l.name, neben: '' }))} />
                            <Liste titel="Unterordner" gesamt={pruefung.unterordnerAnzahl}
                                eintraege={pruefung.unterordner.map((u) => ({ id: u.id, haupt: u.bezeichnung, neben: u.pfad ?? '' }))} />
                        </section>

                        <section aria-label="Was soll passieren?" className="space-y-3">
                            <p className="text-sm font-semibold text-slate-900">Was soll passieren?</p>
                            <Wegkarte gewaehlt={modus === 'UMZIEHEN'}>
                                <div className="flex items-start justify-between gap-2">
                                    <label className="flex cursor-pointer items-start gap-3">
                                        <input type="radio" name={`${titelId}-modus`} checked={modus === 'UMZIEHEN'} onChange={() => setModus('UMZIEHEN')}
                                            className="mt-1 h-4 w-4 shrink-0 accent-rose-600" />
                                        <span>
                                            <span className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
                                                In anderen Ordner umziehen
                                                <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">Empfohlen</span>
                                            </span>
                                            <span className="mt-0.5 block text-sm text-slate-600">
                                                Projekte, Leistungen und Unterordner wandern in den gewählten Ordner. Nichts geht verloren.
                                            </span>
                                        </span>
                                    </label>
                                    <InfoUmziehen />
                                </div>
                                {modus === 'UMZIEHEN' && (
                                    <div className="mt-3 space-y-2">
                                        {keinZiel ? (
                                            <p className="text-sm text-slate-600">Es gibt keinen passenden Ziel-Ordner. Legen Sie zuerst einen an oder löschen Sie den Ordner mit Inhalt.</p>
                                        ) : (
                                            <OrdnerZielAuswahl ziele={ziele} wert={zielId} onWahl={setZielId} beschriftung="Ziel-Ordner" />
                                        )}
                                        {andereEinheit && (
                                            <p role="status" className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                                                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                                                „{ziel?.name}“ zählt in {ziel?.einheitAnzeige}, „{name}“ in {ordner?.einheitAnzeige}. Die Mengen passen in der Auswertung dann nicht mehr zusammen.
                                            </p>
                                        )}
                                    </div>
                                )}
                            </Wegkarte>

                            <Wegkarte gewaehlt={modus === 'ALLES'} gefahr>
                                <div className="flex items-start justify-between gap-2">
                                    <label className="flex cursor-pointer items-start gap-3">
                                        <input type="radio" name={`${titelId}-modus`} checked={modus === 'ALLES'} onChange={() => setModus('ALLES')}
                                            className="mt-1 h-4 w-4 shrink-0 accent-red-600" />
                                        <span>
                                            <span className="block font-medium text-slate-900">Ordner und Inhalt löschen</span>
                                            <span className="mt-0.5 block text-sm text-slate-600">
                                                {folgen.length > 0 ? `Löscht auch ${folgen.join(' und ')}. ` : ''}
                                                {pruefung.projektAnzahl > 0
                                                    ? `${anzahlText(pruefung.projektAnzahl, 'Projekt verliert', 'Projekte verlieren')} diese Kategorie; ihre Arbeitszeiten zählen in keiner Ordner-Auswertung mehr. `
                                                    : ''}
                                                Lässt sich nicht rückgängig machen.
                                            </span>
                                        </span>
                                    </label>
                                    <InfoAllesLoeschen />
                                </div>
                            </Wegkarte>
                        </section>
                    </div>
                </div>

                {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}
                <DialogFooter className="items-center gap-2">
                    {!bereit && <p className="mr-auto text-xs text-slate-500">Bitte zuerst einen Ziel-Ordner wählen.</p>}
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    {modus === 'UMZIEHEN' ? (
                        <Button type="button" onClick={() => void loeschen()} disabled={!bereit || sendet}>
                            {sendet && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />}
                            Umziehen und löschen
                        </Button>
                    ) : (
                        <Button type="button" onClick={() => void loeschen()} disabled={sendet}
                            className="border-red-600 bg-red-600 hover:bg-red-700 focus-visible:ring-red-500">
                            {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Trash2 className="h-4 w-4" aria-hidden="true" />}
                            Endgültig löschen
                        </Button>
                    )}
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function Zaehlchip({ icon, text }: { icon: ReactNode; text: string }) {
    return (
        <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-slate-700">
            <span aria-hidden="true" className="text-slate-500">{icon}</span>{text}
        </span>
    );
}

function Wegkarte({ gewaehlt, gefahr, children }: { gewaehlt: boolean; gefahr?: boolean; children: ReactNode }) {
    return (
        <div className={cn(
            'rounded-lg border p-3 transition-colors',
            gewaehlt ? (gefahr ? 'border-red-300 bg-red-50/40' : 'border-rose-300 bg-rose-50/40') : 'border-slate-200 bg-white',
        )}>
            {children}
        </div>
    );
}

/** Liste mit höchstens 20 Einträgen plus „und N weitere“. Ohne Einträge unsichtbar. */
export function Liste({ titel, gesamt, eintraege }: { titel: string; gesamt: number; eintraege: { id: number; haupt: string; neben: string }[] }) {
    if (gesamt === 0) return null;
    const rest = weitereAnzahl(gesamt, eintraege.length);
    return (
        <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{titel} ({gesamt})</p>
            <ul className="mt-1 divide-y divide-slate-100 rounded-lg border border-slate-200">
                {eintraege.map((e) => (
                    <li key={e.id} className="px-3 py-1.5 text-sm">
                        <span className="text-slate-800">{e.haupt}</span>
                        {e.neben && <span className="block text-xs text-slate-500">{e.neben}</span>}
                    </li>
                ))}
                {rest > 0 && <li className="px-3 py-1.5 text-sm text-slate-500">und {rest} weitere</li>}
            </ul>
        </div>
    );
}
