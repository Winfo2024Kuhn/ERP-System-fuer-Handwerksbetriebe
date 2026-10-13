import type { ReactNode } from 'react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { cn } from '../../lib/utils';
import { arbeitsbeginn, datumFormat, formatiereStand, type FreigabeRechtsnachweisDaten } from './rechtsnachweisLogik';

function Zeile({ label, children, info }: { label: string; children: ReactNode; info?: ReactNode }) {
    return (
        <div>
            <div className="flex items-center gap-1">
                <p className="text-xs text-slate-500">{label}</p>
                {info}
            </div>
            <div className="text-sm text-slate-900 break-words">{children}</div>
        </div>
    );
}

/**
 * Zeigt im Annahme-Nachweis, wann der Code bestätigt wurde, ob der Betrieb sofort
 * beginnen darf und welche Fassungen der Rechtstexte galten. Rendert nichts, wenn
 * die Annahme keine dieser Angaben trägt (Altbestand).
 */
export function FreigabeRechtsnachweis({ daten }: { daten: FreigabeRechtsnachweisDaten }) {
    const beginn = arbeitsbeginn(daten);
    const widerruf = formatiereStand(daten.widerrufStand);
    const datenschutz = formatiereStand(daten.datenschutzStand);
    const codeAm = daten.codeBestaetigtAm ? new Date(daten.codeBestaetigtAm) : null;
    const codeAmGueltig = codeAm && !Number.isNaN(codeAm.getTime());

    if (!beginn && !widerruf && !datenschutz && !daten.knopfText && !daten.bestaetigungText && !daten.codeBestaetigtAm) {
        return null;
    }

    return (
        <div className="pt-2 mt-2 border-t border-slate-100 space-y-3">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Rechtsnachweis</p>
            {daten.codeBestaetigtAm && (
                <Zeile label="Bestätigt per E-Mail-Code">
                    {codeAmGueltig
                        ? codeAm.toLocaleString('de-DE', { ...datumFormat, hour: '2-digit', minute: '2-digit' })
                        : '—'}
                    {daten.codeBestaetigtEmail ? ` an ${daten.codeBestaetigtEmail}` : ''}
                </Zeile>
            )}
            {beginn && (
                <Zeile
                    label="Arbeitsbeginn"
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
                </Zeile>
            )}
            {(widerruf || datenschutz) && (
                <Zeile label="Geltende Fassungen">
                    {[widerruf && `Widerrufsbelehrung vom ${widerruf}`, datenschutz && `Datenschutzerklärung vom ${datenschutz}`]
                        .filter(Boolean)
                        .join(' · ')}
                </Zeile>
            )}
            {(daten.knopfText || daten.bestaetigungText) && (
                <Zeile label="Ausgelöst mit">
                    {daten.knopfText && <span className="font-medium">„{daten.knopfText}“</span>}
                    {daten.bestaetigungText && (
                        <span className="block text-xs text-slate-600 mt-0.5">Häkchen: {daten.bestaetigungText}</span>
                    )}
                </Zeile>
            )}
        </div>
    );
}
