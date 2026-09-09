import { CheckCircle2, Sparkles } from 'lucide-react';
import { Button } from '../ui/button';
import type { Beleg, Sachkonto } from '../../types';
import { sicherheitsText } from './belegFormat';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): heutige
// KiVorschlagKarte aus BelegeKasseEditor.tsx (Zeilen 1911-1997), Props
// unveraendert.

/**
 * Zeigt, was der KI-Kostenkonto-Agent vorgeschlagen hat: Konto, Begründung und
 * wie sicher er sich ist — mit einem Klick übernehmbar.
 *
 * Hintergrund: Der Server liefert diese Felder schon lange mit (siehe
 * BelegDto.Response), das UI hat sie bisher aber komplett verworfen. Der
 * Buchhalter sah nur den Status "KI fertig" neben einem leeren Konto-Feld und
 * musste jeden Beleg von Hand einordnen, obwohl die KI die Antwort inklusive
 * Begründung längst geliefert hatte.
 */
export function VorschlagsChip({ beleg, sachkonten, aktuellesSachkontoId, onUebernehmen }: {
    beleg: Beleg;
    sachkonten: Sachkonto[];
    aktuellesSachkontoId: number | null;
    onUebernehmen: (sachkontoId: number) => void;
}) {
    const vorschlagId = beleg.kiVorgeschlagenerSachkontoId;
    const begruendung = beleg.kiKostenkontoBegruendung;

    // Ohne Konto-Vorschlag gibt es nichts zu übernehmen. Eine reine Begründung
    // ohne Konto ("ich konnte es nicht zuordnen") zeigen wir trotzdem an — das
    // erklärt dem Buchhalter, warum das Feld leer geblieben ist.
    if (vorschlagId == null && !begruendung) return null;

    // Der Vorschlag ist nur wählbar, wenn das Konto auch wirklich in den
    // aktiven Stammdaten steht — sonst hätte der Select-Wert keinen Eintrag
    // und das Feld sähe nach dem Übernehmen wieder leer aus.
    const vorschlagKonto = vorschlagId != null
        ? sachkonten.find(s => s.id === vorschlagId) ?? null
        : null;
    const bereitsUebernommen = vorschlagId != null && aktuellesSachkontoId === vorschlagId;
    const sicherheit = sicherheitsText(beleg.kiKostenkontoConfidence);

    return (
        <div className="border border-rose-200 bg-rose-50/60 rounded-lg p-3 space-y-2">
            <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-rose-600 shrink-0" aria-hidden />
                <span className="text-xs font-semibold uppercase tracking-wide text-rose-700">
                    Das schlägt die KI vor
                </span>
            </div>

            {vorschlagKonto ? (
                <div className="flex items-start justify-between gap-3 flex-wrap">
                    <div className="min-w-0">
                        <div className="text-sm font-semibold text-slate-900">
                            {vorschlagKonto.nummer ? `${vorschlagKonto.nummer} ` : ''}{vorschlagKonto.bezeichnung}
                        </div>
                        <div className={`text-xs ${sicherheit.cls}`}>{sicherheit.text}</div>
                    </div>
                    {bereitsUebernommen ? (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 shrink-0">
                            <CheckCircle2 className="w-4 h-4" aria-hidden /> übernommen
                        </span>
                    ) : (
                        <Button size="sm" type="button" variant="outline"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50 shrink-0"
                            onClick={() => onUebernehmen(vorschlagKonto.id)}>
                            Konto übernehmen
                        </Button>
                    )}
                </div>
            ) : vorschlagId != null ? (
                // Vorschlag zeigt auf ein Konto, das nicht (mehr) aktiv ist.
                <p className="text-sm text-slate-700">
                    Vorgeschlagenes Konto ist nicht mehr aktiv – bitte von Hand wählen.
                </p>
            ) : (
                <p className="text-sm text-slate-700">
                    Die KI konnte kein Konto sicher zuordnen – bitte von Hand wählen.
                </p>
            )}

            {begruendung && (
                <p className="text-xs text-slate-600 leading-relaxed">
                    <span className="font-medium text-slate-700">Warum: </span>{begruendung}
                </p>
            )}

            {beleg.kiVorgeschlagenerKostenstelleBezeichnung && (
                <p className="text-xs text-slate-500">
                    Vorgeschlagener Kostenbereich: {beleg.kiVorgeschlagenerKostenstelleBezeichnung}
                </p>
            )}
        </div>
    );
}
