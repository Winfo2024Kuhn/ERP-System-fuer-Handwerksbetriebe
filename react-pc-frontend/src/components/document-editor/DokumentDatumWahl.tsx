import { DatePicker } from '../ui/datepicker';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { SegmentAuswahl, type SegmentOption } from '../ui/segment-auswahl';
import { formatDatum } from '../../lib/datum';
import {
    ZUKUNFT_GESPERRT_TEXT,
    datumWahlFehler,
    gewaehltesDatum,
    zahlbarBis,
    type DatumWahl,
    type DatumWahlArt,
} from './datumWahlRegeln';

const UEBERSCHRIFT = 'Datum oben auf dem Dokument';

export interface DokumentDatumWahlProps {
    /** Datum, das gerade im Dokument gespeichert ist (`YYYY-MM-DD`). */
    gespeichertesDatum: string;
    /** Heutiges Datum in lokaler Zeit (`YYYY-MM-DD`). */
    heute: string;
    wert: DatumWahl;
    onChange: (wert: DatumWahl) => void;
    /** Rechnungen: kein Datum in der Zukunft, Fälligkeit wird angezeigt. */
    istRechnung: boolean;
    zahlungszielTage: number;
}

/**
 * Frage „Welches Datum steht oben auf dem Dokument?“ im ersten Dialog vor dem
 * endgültigen Drucken, Herunterladen oder Senden. Regeln in `datumWahlRegeln.ts`.
 */
export function DokumentDatumWahl({ gespeichertesDatum, heute, wert, onChange, istRechnung, zahlungszielTage }: DokumentDatumWahlProps) {
    const fehler = datumWahlFehler(wert, gespeichertesDatum, heute, istRechnung);
    const datum = gewaehltesDatum(wert, gespeichertesDatum, heute);
    const bisherInZukunft = istRechnung && gespeichertesDatum > heute;

    const optionen: SegmentOption[] = [
        { wert: 'heute', text: `Heute (${formatDatum(heute)})` },
        {
            wert: 'bisher',
            text: `Bisheriges Datum behalten (${formatDatum(gespeichertesDatum)})`,
            gesperrt: bisherInZukunft ? ZUKUNFT_GESPERRT_TEXT : null,
        },
        { wert: 'anders', text: 'Anderes Datum …' },
    ];

    return (
        <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
            <div className="flex items-center gap-1">
                <p className="text-sm font-medium text-slate-700">{UEBERSCHRIFT}</p>
                <InfoButton titel={UEBERSCHRIFT}>
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>
                            Das gewählte Datum wird beim Drucken, Herunterladen oder Senden im Dokument gespeichert
                            und steht oben im PDF. Bei Rechnungen zählt das Zahlungsziel ab diesem Datum.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Eine Rechnung wird dabei gebucht, danach lässt sich ihr Datum nicht mehr ändern. Bei
                            Angeboten und Auftragsbestätigungen können Sie das Datum später noch anpassen. Entwürfe
                            mit Wasserzeichen ändern das Datum nicht.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            <SegmentAuswahl
                beschriftung={UEBERSCHRIFT}
                optionen={optionen}
                wert={wert.art}
                onChange={art => onChange({ ...wert, art: art as DatumWahlArt })}
                className="flex w-full flex-col items-stretch gap-0.5"
            />
            {wert.art === 'anders' && (
                <DatePicker
                    value={wert.anderesDatum}
                    onChange={anderesDatum => onChange({ ...wert, anderesDatum })}
                    max={istRechnung ? heute : undefined}
                    aria-label="Anderes Datum"
                    error={fehler ?? undefined}
                />
            )}
            {fehler && wert.art !== 'anders' && (
                <p role="alert" className="text-sm text-rose-700">{fehler}</p>
            )}
            {istRechnung && !fehler && (
                <p className="text-sm text-slate-600 tabular-nums">
                    Zahlbar bis {zahlbarBis(datum, zahlungszielTage)}
                </p>
            )}
        </div>
    );
}
