import { useEffect, useState } from 'react';
import { History, Loader2, Pencil, Plus, Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { useToast } from '../../components/ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';

/** Ein Eintrag aus `GET /api/zeitverwaltung/buchungen/{id}/historie`. */
export interface BuchungVerlaufEintrag {
    id: number;
    version: number;
    aktion: string;
    startZeit: string | null;
    endeZeit: string | null;
    anzahlInStunden: number | null;
    notiz: string | null;
    geaendertVon: string | null;
    geaendertAm: string | null;
    geaendertVia: string | null;
    aenderungsgrund: string | null;
}

/** Klartext je Aktion (Backend-Enum `AuditAktion`). */
const AKTIONEN: Record<string, { label: string; Icon: typeof Plus; farbe: string }> = {
    ERSTELLT: { label: 'Angelegt', Icon: Plus, farbe: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    GEAENDERT: { label: 'Geändert', Icon: Pencil, farbe: 'bg-amber-50 text-amber-700 border-amber-200' },
    STORNIERT: { label: 'Gelöscht', Icon: Trash2, farbe: 'bg-red-50 text-red-700 border-red-200' },
    GELOESCHT: { label: 'Gelöscht', Icon: Trash2, farbe: 'bg-red-50 text-red-700 border-red-200' },
};

/** Klartext je Erfassungsweg (Backend-Enum `ErfassungsQuelle`). */
const WEGE: Record<string, string> = {
    MOBILE_APP: 'Handy-App',
    DESKTOP: 'Büro (PC)',
    ADMIN_KORREKTUR: 'Korrektur durch die Verwaltung',
    IMPORT: 'Import',
    SYSTEM: 'Automatisch vom Programm',
};

function wegKlartext(via: string | null): string | null {
    if (!via) return null;
    return WEGE[via] ?? via;
}

/** Uhrzeit (HH:mm) aus einem ISO-Zeitpunkt, ohne Zeitzonen-Umrechnung. */
function uhrzeit(iso: string | null): string {
    if (!iso) return '–';
    const treffer = /T(\d{2}:\d{2})/.exec(iso);
    return treffer ? treffer[1] : '–';
}

function zeitpunkt(iso: string | null): string {
    if (!iso) return '–';
    const datum = new Date(iso);
    return Number.isNaN(datum.getTime()) ? iso : datum.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

function stunden(wert: number | null): string {
    if (wert == null) return '–';
    return `${Number(wert).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} Std.`;
}

interface BuchungVerlaufDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    buchungId: number | null;
    /** Kurzbeschreibung der Buchung für die Unterzeile, z. B. „08:00–12:00“. */
    beschreibung?: string;
}

/**
 * Zeigt die Änderungshistorie einer Zeitbuchung: wer hat wann was geändert,
 * über welchen Weg und mit welchem Grund. Neueste Einträge stehen oben.
 */
export function BuchungVerlaufDialog({ open, onOpenChange, buchungId, beschreibung }: BuchungVerlaufDialogProps) {
    const toast = useToast();
    // Ergebnis je Buchung: so zeigt ein Wechsel zu einer anderen Buchung nie den alten Verlauf.
    const [stand, setStand] = useState<{ buchungId: number; eintraege?: BuchungVerlaufEintrag[]; fehler?: string } | null>(null);
    const aktuell = stand && stand.buchungId === buchungId ? stand : null;
    const eintraege = aktuell?.eintraege ?? null;
    const fehler = aktuell?.fehler ?? null;

    useEffect(() => {
        if (!open || !buchungId) return;
        const controller = new AbortController();
        fetch(`/api/zeitverwaltung/buchungen/${encodeURIComponent(String(buchungId))}/historie`, { signal: controller.signal })
            .then(async res => {
                if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Verlauf der Buchung konnte nicht geladen werden.'));
                return res.json();
            })
            .then((daten: unknown) => {
                if (controller.signal.aborted) return;
                const liste = Array.isArray(daten) ? daten as BuchungVerlaufEintrag[] : [];
                setStand({ buchungId, eintraege: [...liste].sort((a, b) => (b.version ?? 0) - (a.version ?? 0)) });
            })
            .catch((err: unknown) => {
                if (controller.signal.aborted) return;
                const meldung = err instanceof Error && err.message ? err.message : 'Verlauf der Buchung konnte nicht geladen werden.';
                setStand({ buchungId, fehler: meldung });
                toast.error(meldung);
            });
        return () => controller.abort();
    }, [open, buchungId, toast]);

    return (
        <Dialog open={open} onOpenChange={onOpenChange} aria-label="Verlauf der Buchung">
            <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <History className="h-5 w-5 text-rose-600" />
                        Verlauf der Buchung
                        <InfoButton titel="Verlauf">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>Jede Änderung an einer Buchung wird mit Zeitpunkt, Person und Grund festgehalten.</p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>Der Verlauf lässt sich nicht verändern oder löschen. So ist bei einer Prüfung nachvollziehbar, wer wann was geändert hat.</p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </DialogTitle>
                    {beschreibung && <DialogDescription>{beschreibung}</DialogDescription>}
                </DialogHeader>

                {fehler ? (
                    <p role="alert" className="py-8 text-center text-sm text-red-700">{fehler}</p>
                ) : !eintraege ? (
                    <div role="status" className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500">
                        <Loader2 className="h-5 w-5 motion-safe:animate-spin text-rose-600" />
                        Verlauf wird geladen …
                    </div>
                ) : eintraege.length === 0 ? (
                    <p className="py-12 text-center text-sm text-slate-500">
                        Für diese Buchung ist noch kein Verlauf vorhanden.
                    </p>
                ) : (
                    <ol className="mt-2 space-y-3" aria-label="Änderungen, neueste zuerst">
                        {eintraege.map(e => {
                            const meta = AKTIONEN[e.aktion] ?? { label: e.aktion, Icon: Pencil, farbe: 'bg-slate-100 text-slate-700 border-slate-200' };
                            const Icon = meta.Icon;
                            const weg = wegKlartext(e.geaendertVia);
                            return (
                                <li key={e.id} className="rounded-lg border border-slate-200 bg-white p-3">
                                    <div className="flex flex-wrap items-start justify-between gap-3">
                                        <div className="flex items-start gap-3">
                                            <span className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-xs font-medium ${meta.farbe}`}>
                                                <Icon className="h-3 w-3" />
                                                {meta.label}
                                            </span>
                                            <div className="text-sm">
                                                <p className="font-medium text-slate-900">{e.geaendertVon || 'Unbekannt'}</p>
                                                <p className="text-xs text-slate-500">
                                                    {zeitpunkt(e.geaendertAm)}{weg ? ` · über ${weg}` : ''}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="text-right text-sm tabular-nums text-slate-700">
                                            <p>{uhrzeit(e.startZeit)} – {uhrzeit(e.endeZeit)}</p>
                                            <p className="text-xs text-slate-500">{stunden(e.anzahlInStunden)}</p>
                                        </div>
                                    </div>
                                    {e.aenderungsgrund && (
                                        <p className="mt-2 rounded border border-slate-100 bg-slate-50 p-2 text-sm text-slate-700">
                                            <span className="font-medium">Grund: </span>{e.aenderungsgrund}
                                        </p>
                                    )}
                                    {e.notiz && (
                                        <p className="mt-1 text-xs text-slate-500">Bemerkung: {e.notiz}</p>
                                    )}
                                </li>
                            );
                        })}
                    </ol>
                )}
            </DialogContent>
        </Dialog>
    );
}
