import type { ReactNode } from 'react';
import { Square, SquareCheck } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { cn } from '../../lib/utils';
import {
    arbeitsbeginn,
    datumFormat,
    formatiereBetrag,
    formatiereStand,
    haekchenListe,
    type FreigabeRechtsnachweisDaten,
    type Haekchen,
} from './rechtsnachweisLogik';

/** Eine beschriftete Zeile im Annahme-Nachweis; `mono` für technische Werte (IP, Browser). */
export function NachweisZeile({ label, children, info, mono, small }: {
    label: string;
    children: ReactNode;
    info?: ReactNode;
    mono?: boolean;
    small?: boolean;
}) {
    return (
        <div>
            <div className="flex items-center gap-1">
                <p className="text-xs text-slate-500">{label}</p>
                {info}
            </div>
            <div className={cn(
                'text-slate-900',
                mono ? 'font-mono break-all' : 'break-words',
                small ? 'text-xs' : 'text-sm',
            )}>
                {children}
            </div>
        </div>
    );
}

/** Abschnitt mit kleiner Überschrift, durch eine feine Linie vom vorherigen getrennt. */
export function NachweisAbschnitt({ titel, children }: { titel: string; children: ReactNode }) {
    return (
        <section className="pt-3 mt-1 border-t border-slate-100 space-y-3">
            <h4 className="text-xs font-medium text-slate-500 uppercase tracking-wider">{titel}</h4>
            {children}
        </section>
    );
}

/** „30.10.2026, 12:26 an max@example.com“ – wann und wohin der Bestätigungs-Code ging. */
export function CodeBestaetigtZeile({ daten }: { daten: Pick<FreigabeRechtsnachweisDaten, 'codeBestaetigtAm' | 'codeBestaetigtEmail'> }) {
    if (!daten.codeBestaetigtAm) return null;
    const codeAm = new Date(daten.codeBestaetigtAm);
    const zeit = Number.isNaN(codeAm.getTime())
        ? '—'
        : codeAm.toLocaleString('de-DE', { ...datumFormat, hour: '2-digit', minute: '2-digit' });
    return (
        <NachweisZeile label="Bestätigt per E-Mail-Code">
            {zeit}
            {daten.codeBestaetigtEmail ? ` an ${daten.codeBestaetigtEmail}` : ''}
        </NachweisZeile>
    );
}

function HaekchenZeile({ haekchen }: { haekchen: Haekchen }) {
    const Symbol = haekchen.angehakt ? SquareCheck : Square;
    const art = haekchen.art === 'pflicht' ? 'Pflicht-Häkchen' : 'Freiwilliges Häkchen';
    return (
        <li className="flex items-start gap-2">
            <Symbol
                aria-hidden="true"
                className={cn('w-5 h-5 shrink-0', haekchen.angehakt ? 'text-emerald-600' : 'text-slate-400')}
            />
            <div className="min-w-0">
                <p className="text-xs text-slate-500">
                    <span>{art}</span>
                    {' · '}
                    <span className={haekchen.angehakt ? 'text-emerald-700' : 'text-slate-600'}>
                        {haekchen.angehakt ? 'angehakt' : 'nicht angehakt'}
                    </span>
                </p>
                {haekchen.wortlaut ? (
                    <p className="text-sm text-slate-900 break-words">„{haekchen.wortlaut}“</p>
                ) : (
                    <p className="text-xs text-slate-500 italic">Wortlaut bei dieser Annahme nicht gespeichert</p>
                )}
            </div>
        </li>
    );
}

/**
 * Was der Kunde auf der Annahmeseite bestätigt hat: jedes Häkchen mit Wortlaut und Zustand,
 * der gedrückte Knopf, die geltenden Fassungen der Rechtstexte sowie mitbeauftragte
 * Wahlpositionen samt verbindlichem Betrag. Rendert nichts bei Altbestand ohne diese Angaben.
 */
export function WasBestaetigtAbschnitt({ daten }: { daten: FreigabeRechtsnachweisDaten }) {
    const haekchen = haekchenListe(daten);
    const widerruf = formatiereStand(daten.widerrufStand);
    const datenschutz = formatiereStand(daten.datenschutzStand);
    const wahlpositionen = daten.mitbeauftragteAlternativen ?? [];
    const betrag = daten.akzeptierterBetrag;

    if (haekchen.length === 0 && !daten.knopfText && !widerruf && !datenschutz
        && wahlpositionen.length === 0 && betrag == null) {
        return null;
    }

    return (
        <NachweisAbschnitt titel="Was der Kunde bestätigt hat">
            {haekchen.length > 0 && (
                <ul className="space-y-2" aria-label="Häkchen bei der Annahme">
                    {haekchen.map((h) => <HaekchenZeile key={h.art} haekchen={h} />)}
                </ul>
            )}
            {daten.knopfText && (
                <NachweisZeile label="Knopf gedrückt">
                    <span className="font-medium">„{daten.knopfText}“</span>
                </NachweisZeile>
            )}
            {(widerruf || datenschutz) && (
                <NachweisZeile label="Geltende Fassungen">
                    {[widerruf && `Widerrufsbelehrung vom ${widerruf}`, datenschutz && `Datenschutzerklärung vom ${datenschutz}`]
                        .filter(Boolean)
                        .join(' · ')}
                </NachweisZeile>
            )}
            {wahlpositionen.length > 0 && (
                <NachweisZeile label="Mitbeauftragte Wahlpositionen">
                    <ul className="list-disc pl-5">
                        {wahlpositionen.map((position, index) => <li key={`${index}-${position}`}>{position}</li>)}
                    </ul>
                </NachweisZeile>
            )}
            {betrag != null && (
                <NachweisZeile label="Verbindlicher Betrag (brutto)">
                    <span className="font-medium tabular-nums">{formatiereBetrag(betrag)}</span>
                </NachweisZeile>
            )}
        </NachweisAbschnitt>
    );
}

/**
 * Darf der Betrieb sofort beginnen? Farbige Zeile mit Erklärung. Ohne gespeicherte Angabe
 * zeigt `ohneAngabeHinweis` ausdrücklich, dass nichts abgefragt wurde; sonst rendert er nichts.
 */
export function ArbeitsbeginnAbschnitt({ daten, ohneAngabeHinweis = false }: {
    daten: Pick<FreigabeRechtsnachweisDaten, 'vorzeitigerBeginn' | 'akzeptiertAm'>;
    ohneAngabeHinweis?: boolean;
}) {
    const beginn = arbeitsbeginn(daten);
    if (!beginn) {
        if (!ohneAngabeHinweis) return null;
        return (
            <NachweisAbschnitt titel="Arbeitsbeginn">
                <p className="text-sm text-slate-700">
                    Keine Angabe gespeichert – bei dieser Annahme wurde noch nicht abgefragt, ob der Kunde
                    einen Beginn vor Ablauf der Widerrufsfrist verlangt.
                </p>
            </NachweisAbschnitt>
        );
    }
    return (
        <NachweisAbschnitt titel="Arbeitsbeginn">
            <NachweisZeile
                label="Darf ich anfangen?"
                info={
                    <InfoButton titel="Arbeitsbeginn und Widerrufsfrist">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>
                                Der Kunde entscheidet bei der Online-Freigabe selbst per Häkchen, ob wir vor Ablauf
                                der 14-tägigen Widerrufsfrist anfangen dürfen. Die Entscheidung ist mit der Freigabe
                                gespeichert und steht in der Auftragsbestätigung und der Widerrufsbelehrung, die der
                                Kunde per E-Mail bekommen hat.
                            </p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>
                                Mit Zustimmung dürfen Sie sofort Material bestellen und beginnen. Ohne Zustimmung
                                erst nach Ablauf der Frist: Fangen Sie früher an und der Kunde widerruft, bekommen
                                Sie für die geleistete Arbeit kein Geld. Der Auftrag selbst ist in beiden Fällen
                                erteilt. Bei Firmenkunden gibt es kein Widerrufsrecht – dann spielt die Angabe keine
                                Rolle.
                            </p>
                        </InfoAbschnitt>
                    </InfoButton>
                }
            >
                <span
                    className={cn(
                        'inline-block rounded-md px-2 py-0.5 text-sm',
                        beginn.ton === 'erlaubt' ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-800',
                    )}
                >
                    {beginn.text}
                </span>
            </NachweisZeile>
        </NachweisAbschnitt>
    );
}
