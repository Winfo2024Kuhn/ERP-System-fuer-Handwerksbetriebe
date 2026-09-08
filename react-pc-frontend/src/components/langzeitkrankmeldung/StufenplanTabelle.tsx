import { useState } from 'react';
import { Loader2, Plus, Trash2 } from 'lucide-react';
import { DatePicker } from '../ui/datepicker';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Button } from '../ui/button';
import { useConfirm } from '../ui/confirm-dialog';
import { useToast } from '../ui/toast';
import { cn } from '../../lib/utils';
import { formatDatum, type Phase } from './phasen';

interface StufenplanTabelleProps {
    phasen: Phase[];
    onHinzufuegen: (p: { vonDatum: string; bisDatum: string | null; stundenProTag: number }) => Promise<void>;
    onLoeschen: (phasenId: number) => Promise<void>;
    maxStundenProTag: number;
    disabled?: boolean;
}

/**
 * Deutsches Zahlenformat (Komma statt Punkt), z.B. 5.5 -> "5,5" -- dieselbe
 * Regel wie in der Handy-App (toLocaleString('de-DE')). Nachbesserung
 * Abschnitt 5, Befund 1 (BLOCKER): englische Dezimalpunkte in einer
 * deutschen Oberfläche.
 */
const formatStunden = (n: number): string => n.toLocaleString('de-DE');

/**
 * Wiedereingliederungs-Stufenplan als kleine Tabelle ("ab 01.04. — 2 Stunden
 * pro Tag"), Zeilen hinzufügbar. Reine Präsentationskomponente — die Liste
 * kommt per Props, Änderungen laufen über onHinzufuegen/onLoeschen zum
 * Aufrufer zurück (kontrolliert, kein eigener Datenabruf).
 */
export function StufenplanTabelle({ phasen, onHinzufuegen, onLoeschen, maxStundenProTag, disabled = false }: StufenplanTabelleProps) {
    const confirm = useConfirm();
    const toast = useToast();

    const [vonDatum, setVonDatum] = useState('');
    const [stunden, setStunden] = useState('');
    const [fehler, setFehler] = useState<string | null>(null);
    const [speichertGerade, setSpeichertGerade] = useState(false);
    const [loeschtId, setLoeschtId] = useState<number | null>(null);

    const stufen = phasen
        .filter((p) => p.typ === 'WIEDEREINGLIEDERUNG')
        .slice()
        .sort((a, b) => a.vonDatum.localeCompare(b.vonDatum));

    const deaktiviertGrund = 'Nur möglich, solange die Krankmeldung läuft.';

    const handleHinzufuegen = async () => {
        setFehler(null);

        if (!vonDatum) {
            const meldung = 'Bitte ein Startdatum wählen.';
            setFehler(meldung);
            toast.error(meldung);
            return;
        }

        // Bewusst KEINE Ganzzahl-Pflicht: halbe Stunden sind bei einer
        // Wiedereingliederung durchaus üblich (z.B. 2,5 Std. als Zwischenschritt
        // im Stufenplan). Die Prüfung erlaubt deshalb jeden Wert > 0 bis zum
        // Tageslimit -- der Fehlertext muss das widerspiegeln, nicht "zwischen
        // 1 und N" suggerieren (Nachbesserung Abschnitt 2, Befund 4).
        const stundenZahl = Number(stunden.replace(',', '.'));
        if (!Number.isFinite(stundenZahl) || stundenZahl <= 0 || stundenZahl > maxStundenProTag) {
            const meldung = `Stunden pro Tag müssen größer als 0 und höchstens ${formatStunden(maxStundenProTag)} sein.`;
            setFehler(meldung);
            toast.error(meldung);
            return;
        }

        setSpeichertGerade(true);
        try {
            await onHinzufuegen({ vonDatum, bisDatum: null, stundenProTag: stundenZahl });
            setVonDatum('');
            setStunden('');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Zeile konnte nicht gespeichert werden.');
        } finally {
            setSpeichertGerade(false);
        }
    };

    const handleLoeschen = async (phase: Phase) => {
        const bestaetigt = await confirm({
            title: 'Eintrag löschen?',
            message: `Den Stufenplan-Eintrag ab ${formatDatum(phase.vonDatum)} wirklich löschen? Das kann nicht rückgängig gemacht werden.`,
            confirmLabel: 'Ja, löschen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!bestaetigt) return;

        setLoeschtId(phase.id);
        try {
            await onLoeschen(phase.id);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Eintrag konnte nicht gelöscht werden.');
        } finally {
            setLoeschtId(null);
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                        <tr>
                            <th className="px-3 py-2">ab</th>
                            <th className="px-3 py-2">bis</th>
                            <th className="px-3 py-2">Stunden pro Tag</th>
                            <th className="px-3 py-2 text-right">Aktion</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                        {stufen.length === 0 && (
                            <tr>
                                <td colSpan={4} className="px-3 py-6 text-center text-slate-500">
                                    Noch kein Stufenplan hinterlegt.
                                </td>
                            </tr>
                        )}
                        {stufen.map((phase) => (
                            <tr key={phase.id}>
                                <td className="min-w-0 px-3 py-2">{formatDatum(phase.vonDatum)}</td>
                                <td className="min-w-0 px-3 py-2">
                                    {phase.bisDatum ? formatDatum(phase.bisDatum) : '—'}
                                </td>
                                <td className="min-w-0 px-3 py-2 tabular-nums">
                                    {`${phase.stundenProTag != null ? formatStunden(phase.stundenProTag) : '—'} Std.`}
                                </td>
                                <td className="px-3 py-2 text-right">
                                    <button
                                        type="button"
                                        onClick={() => handleLoeschen(phase)}
                                        disabled={disabled || loeschtId === phase.id}
                                        title={disabled ? deaktiviertGrund : 'Löschen'}
                                        aria-label={`Eintrag ab ${formatDatum(phase.vonDatum)} löschen`}
                                        className="rounded p-1.5 text-slate-400 shrink-0 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        {loeschtId === phase.id
                                            ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                                            : <Trash2 className="h-4 w-4" aria-hidden="true" />}
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="flex flex-col gap-3 rounded-lg border border-dashed border-slate-300 p-3 sm:flex-row sm:items-end">
                <div className="min-w-0 flex-1">
                    <Label id="stufenplan-von-label">ab</Label>
                    {/* DatePicker rendert kein natives Formularfeld (kein <input>,
                        keine id-Weiterreichung) -- ein klassisches htmlFor wie beim
                        Stundenfeld daneben greift hier ins Leere. role="group" +
                        aria-labelledby ist die dafuer vorgesehene ARIA-Alternative
                        fuer zusammengesetzte, nicht-native Eingabe-Widgets
                        (Nachbesserung Abschnitt 2, Befund 4). */}
                    <div role="group" aria-labelledby="stufenplan-von-label">
                        <DatePicker value={vonDatum} onChange={setVonDatum} placeholder="Startdatum" disabled={disabled} />
                    </div>
                </div>
                <div className="min-w-0 sm:w-40">
                    <Label htmlFor="stufenplan-stunden">Stunden pro Tag</Label>
                    <Input
                        id="stufenplan-stunden"
                        type="number"
                        min={0}
                        max={maxStundenProTag}
                        value={stunden}
                        onChange={(e) => setStunden(e.target.value)}
                        placeholder={`max. ${formatStunden(maxStundenProTag)}`}
                        disabled={disabled}
                    />
                </div>
                <Button
                    type="button"
                    onClick={handleHinzufuegen}
                    disabled={disabled || speichertGerade}
                    title={disabled ? deaktiviertGrund : undefined}
                    className={cn('bg-rose-600 text-white border border-rose-600 hover:bg-rose-700')}
                    size="sm"
                >
                    {speichertGerade
                        ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                        : <Plus className="h-4 w-4" aria-hidden="true" />}
                    Zeile hinzufügen
                </Button>
            </div>
            {fehler && (
                <p className="text-xs text-red-600" role="alert">
                    {fehler}
                </p>
            )}
        </div>
    );
}
