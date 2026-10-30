import { useId, useState, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import {
    ArbeitsbeginnAbschnitt,
    CodeBestaetigtZeile,
    NachweisAbschnitt,
    NachweisZeile,
    WasBestaetigtAbschnitt,
} from './FreigabeRechtsnachweis';
import type { FreigabeAuditDaten } from './freigabeAudit';

/** Eine beschriftete Zeile mit Textwert im Annahme-Nachweis. */
export function AuditRow({ label, value, mono, small }: { label: string; value: string; mono?: boolean; small?: boolean }) {
    return <NachweisZeile label={label} mono={mono} small={small}>{value}</NachweisZeile>;
}

export interface HashRowProps {
    label: string;
    value: string | null;
    /** Kleiner Zusatz hinter der Beschriftung, z. B. wo der Hash sonst noch steht. */
    hinweis?: string;
    /** Vollen Wert zeigen (umgebrochen) statt „12345678…12345678“. */
    voll?: boolean;
    /** Info-Knopf neben der Beschriftung. */
    info?: ReactNode;
}

/** Fingerabdruck (Hash) mit Knopf, der den vollen Wert kopiert; gekürzt oder voll. */
export function HashRow({ label, value, hinweis, voll = false, info }: HashRowProps) {
    const toast = useToast();
    const beschriftung = (
        <div className="flex items-center gap-1">
            <p className="text-xs text-slate-500">
                {label}
                {hinweis && <span className="text-slate-400"> ({hinweis})</span>}
            </p>
            {info}
        </div>
    );
    if (!value) {
        return (
            <div>
                {beschriftung}
                <p className="text-sm text-slate-900">—</p>
            </div>
        );
    }
    const kurz = value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value;
    const handleCopy = () => {
        navigator.clipboard.writeText(value)
            .then(() => toast.success('Hash in Zwischenablage kopiert'))
            .catch(() => toast.error('Kopieren fehlgeschlagen'));
    };
    return (
        <div>
            {beschriftung}
            <div className={cn('flex gap-2', voll ? 'items-start' : 'items-center')}>
                <code
                    className={cn(
                        'text-xs font-mono text-slate-700 bg-slate-50 border border-slate-200 rounded px-2 py-1 flex-1 min-w-0',
                        voll ? 'break-all select-all leading-relaxed' : 'truncate',
                    )}
                    title={voll ? undefined : value}
                    // Gekürzt mit Absicht: voller Wert im Tooltip und per „Kopieren“.
                    data-kuerzung-erlaubt={voll ? undefined : ''}
                >
                    {voll ? value : kurz}
                </code>
                <Button size="sm" variant="outline" onClick={handleCopy} className="shrink-0" aria-label={`${label} kopieren`}>
                    Kopieren
                </Button>
            </div>
        </div>
    );
}

function formatiereAnnahmeZeit(wert: string | null): string {
    if (!wert) return '—';
    const datum = new Date(wert);
    if (Number.isNaN(datum.getTime())) return wert;
    return datum.toLocaleString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
}

/** „Angebot AG-2026/10/00021“; fehlende Teile fallen weg. */
function angenommenesDokument(daten: FreigabeAuditDaten): string {
    return [daten.dokumentArt, daten.dokumentNummer].filter(Boolean).join(' ') || '—';
}

/**
 * Name so, wie der Kunde ihn eingetippt hat – Vor- und Nachname getrennt. Ältere Annahmen
 * kennen nur den zusammengesetzten Namen.
 */
function EingetragenerName({ daten }: { daten: FreigabeAuditDaten }) {
    // Bei Firmenkunden die vertretungsberechtigte Person, nicht zwangsläufig der Kundenstammname.
    if (daten.unterzeichnerVorname == null && daten.unterzeichnerNachname == null) {
        return <AuditRow label="Angenommen von" value={daten.unterzeichnerName || '—'} />;
    }
    return (
        <>
            <AuditRow label="Vorname (eingetragen)" value={daten.unterzeichnerVorname || '—'} />
            <AuditRow label="Nachname (eingetragen)" value={daten.unterzeichnerNachname || '—'} />
        </>
    );
}

/** Voller Annahme-Hash – derselbe Wert steht in der Bestätigungs-Mail an den Kunden. */
function AnnahmeHash({ daten }: { daten: FreigabeAuditDaten }) {
    return (
        <NachweisAbschnitt titel="Fingerabdruck">
            <HashRow
                label="Annahme-Hash"
                hinweis="steht so auch in der Mail an den Kunden"
                value={daten.hashAcceptance}
                voll
                info={
                    <InfoButton titel="Annahme-Hash">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>
                                Der Annahme-Hash ist eine Prüfsumme über alle Angaben der Annahme: Name, E-Mail,
                                Zeitpunkt, Häkchen, Knopf, Fassungen, Betrag. Ändert sich auch nur ein Zeichen, kommt
                                ein völlig anderer Hash heraus. Er wird mit einem geheimen Schlüssel des Programms
                                gebildet; der Kunde kann ihn deshalb nicht selbst nachrechnen, sondern nur vergleichen.
                            </p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>
                                Der Hash wurde bei der Annahme aus genau diesen Angaben gebildet. Derselbe Wert steht in
                                der Mail des Kunden und im Verlauf des Dokuments, dort zusammen mit allen Angaben der
                                Annahme, die sich nicht unbemerkt ändern lassen. Stimmen Mail, Verlauf und diese Anzeige
                                überein, gehören sie zur selben Annahme. Der Fingerabdruck des Dokuments im Verlauf ist
                                etwas anderes: Er gehört zum Inhalt der Auftragsbestätigung.
                            </p>
                        </InfoAbschnitt>
                    </InfoButton>
                }
            />
        </NachweisAbschnitt>
    );
}

/** Woher die Annahme kam (IP, Browser) und der Fingerabdruck des versendeten Dokuments. */
function TechnischeAngaben({ daten }: { daten: FreigabeAuditDaten }) {
    return (
        <>
            <AuditRow label="IP-Adresse" value={daten.akzeptiertIp || '—'} mono />
            {daten.akzeptiertUserAgent && (
                <AuditRow label="Browser" value={daten.akzeptiertUserAgent} mono small />
            )}
            <HashRow label="Original-Hash" hinweis="Stand beim Versand" value={daten.hashOriginal} />
        </>
    );
}

export interface AnnahmeNachweisDetailsProps {
    daten: FreigabeAuditDaten;
    /**
     * Kompakte Fassung für den Verlauf: Kerndaten zweispaltig, IP, Browser und Original-Hash
     * hinter „Technische Angaben anzeigen“ (der Annahme-Hash bleibt sichtbar), und ein Hinweis,
     * wenn zum Arbeitsbeginn nichts gespeichert ist.
     */
    kompakt?: boolean;
}

/**
 * Lückenloser Nachweis einer digitalen Annahme: welches Dokument, wann, wer (Name wie
 * eingetragen, E-Mail, Bestätigungs-Code), was der Kunde bestätigt hat (Häkchen mit Wortlaut,
 * Knopf, Fassungen, Wahlpositionen, Betrag), der Arbeitsbeginn und der volle Annahme-Hash.
 * Wird im Projekt („Annahme-Beweis“) und im Verlauf eines Dokuments gezeigt.
 */
export function AnnahmeNachweisDetails({ daten, kompakt = false }: AnnahmeNachweisDetailsProps) {
    const [technikOffen, setTechnikOffen] = useState(false);
    const technikId = useId();
    const raster = kompakt ? 'grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-3' : 'space-y-3';

    return (
        <div className="space-y-3">
            <div className={raster}>
                <AuditRow label={kompakt ? 'Angenommen wurde' : 'Dokument'} value={angenommenesDokument(daten)} />
                <AuditRow label="Angenommen am" value={formatiereAnnahmeZeit(daten.akzeptiertAm)} />
            </div>
            <NachweisAbschnitt titel="Wer hat angenommen">
                <div className={raster}>
                    <EingetragenerName daten={daten} />
                    <AuditRow label="E-Mail" value={daten.akzeptiertEmail || '—'} />
                    <CodeBestaetigtZeile daten={daten} />
                </div>
            </NachweisAbschnitt>
            <WasBestaetigtAbschnitt daten={daten} />
            <ArbeitsbeginnAbschnitt daten={daten} ohneAngabeHinweis={kompakt} />
            <AnnahmeHash daten={daten} />
            {kompakt ? (
                <div className="pt-1">
                    <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => setTechnikOffen((offen) => !offen)}
                        aria-expanded={technikOffen}
                        aria-controls={technikId}
                        className="-ml-2"
                    >
                        <ChevronDown className={cn('w-4 h-4 motion-safe:transition-transform', technikOffen && 'rotate-180')} />
                        {technikOffen ? 'Technische Angaben ausblenden' : 'Technische Angaben anzeigen'}
                    </Button>
                    {technikOffen && (
                        <div id={technikId} className="mt-2 space-y-3">
                            <TechnischeAngaben daten={daten} />
                        </div>
                    )}
                </div>
            ) : (
                <NachweisAbschnitt titel="Technische Angaben">
                    <TechnischeAngaben daten={daten} />
                </NachweisAbschnitt>
            )}
        </div>
    );
}
