import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { FreigabeRechtsnachweis } from './FreigabeRechtsnachweis';
import type { FreigabeAuditDaten } from './freigabeAudit';

/** Eine beschriftete Zeile im Annahme-Nachweis. */
export function AuditRow({ label, value, mono, small }: { label: string; value: string; mono?: boolean; small?: boolean }) {
    return (
        <div>
            <p className="text-xs text-slate-500">{label}</p>
            <p className={cn(
                'text-slate-900 break-all',
                mono && 'font-mono',
                small ? 'text-xs' : 'text-sm',
            )}>
                {value}
            </p>
        </div>
    );
}

/** Gekürzter Fingerabdruck (Hash) mit Knopf, der den vollen Wert kopiert. */
export function HashRow({ label, value }: { label: string; value: string | null }) {
    const toast = useToast();
    if (!value) {
        return <AuditRow label={label} value="—" />;
    }
    const kurz = value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value;
    const handleCopy = () => {
        navigator.clipboard.writeText(value)
            .then(() => toast.success('Hash in Zwischenablage kopiert'))
            .catch(() => toast.error('Kopieren fehlgeschlagen'));
    };
    return (
        <div>
            <p className="text-xs text-slate-500">{label}</p>
            <div className="flex items-center gap-2">
                <code className="text-xs font-mono text-slate-700 bg-slate-50 border border-slate-200 rounded px-2 py-1 flex-1 truncate" title={value}>
                    {kurz}
                </code>
                <Button size="sm" variant="outline" onClick={handleCopy} className="shrink-0">
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

/** Woher die Annahme kam: IP-Adresse und Browser. */
function Verbindung({ daten }: { daten: FreigabeAuditDaten }) {
    return (
        <>
            <AuditRow label="IP-Adresse" value={daten.akzeptiertIp || '—'} mono />
            {daten.akzeptiertUserAgent && (
                <AuditRow label="Browser" value={daten.akzeptiertUserAgent} mono small />
            )}
        </>
    );
}

/** Fingerabdrücke (Hashes) von Dokument und Annahme. */
function KryptographischerBeweis({ daten }: { daten: FreigabeAuditDaten }) {
    return (
        <div className="pt-2 mt-2 border-t border-slate-100 space-y-3">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">
                Kryptographischer Beweis
            </p>
            <HashRow label="Original-Hash" value={daten.hashOriginal} />
            <HashRow label="Annahme-Hash" value={daten.hashAcceptance} />
            <p className="text-xs text-slate-400 leading-relaxed">
                SHA-256 über Geschäftsdaten + Akzeptanzdaten. Im Streitfall reproduzierbar
                und damit unveränderbarer Beweis der digitalen Annahme.
            </p>
        </div>
    );
}

export interface AnnahmeNachweisDetailsProps {
    daten: FreigabeAuditDaten;
    /**
     * Kompakte Fassung für den Verlauf: Kerndaten zweispaltig, IP, Browser und
     * Fingerabdrücke hinter „Technische Angaben anzeigen“, und ein Hinweis, wenn zum
     * Arbeitsbeginn nichts gespeichert ist.
     */
    kompakt?: boolean;
}

/**
 * Beweisdaten einer digitalen Annahme: welches Dokument, wer, wann, mit welcher E-Mail,
 * die Entscheidung zum Arbeitsbeginn samt Rechtstexten und die Fingerabdrücke.
 * Wird im Projekt („Annahme-Beweis“) und im Verlauf eines Dokuments gezeigt.
 */
export function AnnahmeNachweisDetails({ daten, kompakt = false }: AnnahmeNachweisDetailsProps) {
    const [technikOffen, setTechnikOffen] = useState(false);
    const technikId = useId();

    const kerndaten = (
        <>
            <AuditRow label={kompakt ? 'Angenommen wurde' : 'Dokument'} value={angenommenesDokument(daten)} />
            <AuditRow label="Angenommen am" value={formatiereAnnahmeZeit(daten.akzeptiertAm)} />
            {/* Unterzeichner: konkret klickende Person — bei Firmenkunden die
                vertretungsberechtigte Person, nicht zwangsläufig der Kundenstammname. */}
            <AuditRow label="Angenommen von" value={daten.unterzeichnerName || '—'} />
            <AuditRow label="E-Mail" value={daten.akzeptiertEmail || '—'} />
        </>
    );

    if (!kompakt) {
        return (
            <div className="space-y-3">
                {kerndaten}
                <Verbindung daten={daten} />
                <FreigabeRechtsnachweis daten={daten} />
                <KryptographischerBeweis daten={daten} />
            </div>
        );
    }

    return (
        <div className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2">{kerndaten}</div>
            <FreigabeRechtsnachweis daten={daten} />
            {daten.vorzeitigerBeginn == null && (
                <div className="pt-2 mt-2 border-t border-slate-100">
                    <p className="text-xs text-slate-500">Arbeitsbeginn</p>
                    <p className="text-sm text-slate-700">
                        Keine Angabe gespeichert – bei dieser Annahme wurde noch nicht abgefragt, ob der Kunde
                        einen Beginn vor Ablauf der Widerrufsfrist verlangt.
                    </p>
                </div>
            )}
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
                        <Verbindung daten={daten} />
                        <KryptographischerBeweis daten={daten} />
                    </div>
                )}
            </div>
        </div>
    );
}
