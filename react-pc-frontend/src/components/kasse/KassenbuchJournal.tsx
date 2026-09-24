import { AlertTriangle, Coins, Loader2, Search } from 'lucide-react';
import { Button } from '../ui/button';
import { DatePicker } from '../ui/datepicker';
import { Card } from '../ui/card';
import { Input } from '../ui/input';
import type { Kassenbuch } from '../../types';
import type { SaldoInfo } from './NeueBuchungDialog';
import { KassenbuchAbschlussLeiste } from './KassenbuchAbschlussLeiste';
import { formatDate, formatEuro, isoDatum } from './belegFormat';
import { baueJournal, journalKategorieLabel, type JournalZeile } from './journalSaldo';

export function KassenbuchJournal({ kassenbuch, saldo, loading, von, bis, onVonChange, onBisChange, search, onSearchChange, onSelectBeleg, onGeaendert }: {
    kassenbuch: Kassenbuch | null;
    saldo: SaldoInfo | null;
    loading: boolean;
    von: string;
    bis: string;
    onVonChange: (v: string) => void;
    onBisChange: (v: string) => void;
    search: string;
    onSearchChange: (v: string) => void;
    onSelectBeleg: (id: number) => void;
    onGeaendert: () => void;
}) {
    const unterMindestbestand = saldo != null && saldo.saldo < saldo.mindestbestand;
    const filterLeiste = <KassenbuchFilter von={von} bis={bis} onVonChange={onVonChange}
        onBisChange={onBisChange} search={search} onSearchChange={onSearchChange} />;
    if (loading) return <div className="space-y-4">{filterLeiste}
        <div className="flex items-center justify-center gap-2 py-16 text-slate-600" role="status">
            <Loader2 className="h-6 w-6 motion-safe:animate-spin text-rose-500" aria-hidden /> Kassenbuch wird geladen…
        </div>
    </div>;
    if (!kassenbuch) return <div className="space-y-4">{filterLeiste}
        <Card className="p-12 text-center text-slate-500"><Coins className="w-12 h-12 mx-auto mb-3 opacity-30" aria-hidden />
            <p>Kassenbuch konnte nicht geladen werden.</p></Card>
    </div>;

    const zeilen = baueJournal(kassenbuch.bewegungen, search);
    const summeEinnahmen = kassenbuch.summeEinnahmen + kassenbuch.summePrivateinlagen;
    const summeAusgaben = kassenbuch.summeAusgaben + kassenbuch.summePrivatentnahmen;
    return (
        <div className="min-w-0 space-y-4">
            <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-[minmax(12rem,1fr)_minmax(0,3fr)] xl:items-center">
                <div className="min-w-0 px-1">
                    <p className="text-sm font-medium text-slate-500">Kasse jetzt</p>
                    <p className={`text-3xl font-bold tabular-nums ${unterMindestbestand ? 'text-red-700' : 'text-slate-900'}`} data-testid="kasse-jetzt">
                        {saldo == null ? '–' : `${formatEuro(saldo.saldo)} €`}
                    </p>
                    {saldo && <p className="mt-1 text-xs text-slate-500">Mindestbestand: {formatEuro(saldo.mindestbestand)} €</p>}
                    {unterMindestbestand && <p className="mt-2 inline-flex items-center gap-1 rounded border border-red-200 bg-red-50 px-2 py-1 text-xs font-medium text-red-700">
                        <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden /> unter Mindestbestand
                    </p>}
                </div>
                <div className="min-w-0"><KassenbuchAbschlussLeiste
                    letzterAbschluss={kassenbuch.letzterAbschluss ?? null}
                    offeneBewegungen={kassenbuch.offeneBewegungen ?? 0} onGeaendert={onGeaendert} /></div>
            </div>
            {filterLeiste}
            {search.trim() && <div className="flex min-w-0 items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <Search className="h-4 w-4 shrink-0 mt-0.5" aria-hidden />
                <p className="min-w-0 break-words">Suche aktiv – es werden <strong>{zeilen.length} von {kassenbuch.bewegungen.length}</strong> Zeilen gezeigt.
                    {' '}Summen und Bestände gelten weiterhin für den ganzen Zeitraum.</p>
            </div>}
            <Card className="min-w-0">
                <div className="border-b border-slate-200 px-4 py-3">
                    <h2 className="text-base font-semibold text-slate-900">Barbewegungen</h2>
                    <p className="text-xs text-slate-500">{formatDate(von)} – {formatDate(bis)} · {kassenbuch.bewegungen.length} Buchungen</p>
                </div>
                <div className="min-w-0 overflow-auto max-h-[65vh] rounded-t-lg">
                    <table aria-label="Kassenbuch" className="w-full table-fixed text-sm">
                        <colgroup><col className="w-[5%]" /><col className="w-[10%]" /><col className="w-[32%]" />
                            <col className="w-[11%]" /><col className="w-[13%]" /><col className="w-[13%]" /><col className="w-[16%]" /></colgroup>
                        <thead className="sticky top-0 z-10 bg-slate-50 text-slate-600">
                            <tr>{['Nr.', 'Datum', 'Was', 'Beleg', 'Einnahme', 'Ausgabe', 'Bestand danach'].map((titel, i) =>
                                <th key={titel} scope="col" className={`px-3 py-3 font-semibold ${i >= 4 ? 'text-right' : 'text-left'}`}>{titel}</th>)}</tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {zeilen.map(zeile => <JournalBuchung key={zeile.belegId} zeile={zeile} onOeffnen={() => onSelectBeleg(zeile.belegId)} />)}
                            {zeilen.length === 0 && <tr><td colSpan={7} className="px-4 py-12 text-center text-slate-500">
                                {search.trim() ? 'Keine passenden Buchungen gefunden.' : 'Keine Barbewegungen in diesem Zeitraum.'}
                            </td></tr>}
                        </tbody>
                    </table>
                </div>
                <dl className="grid grid-cols-2 gap-4 border-t border-slate-200 bg-slate-50 p-4 lg:grid-cols-4 rounded-b-lg">
                    <div className="min-w-0"><dt className="text-xs font-medium text-slate-500">Summe Einnahmen</dt>
                        <dd className="mt-1 text-lg font-semibold tabular-nums text-emerald-700">{formatEuro(summeEinnahmen)} €</dd></div>
                    <div className="min-w-0"><dt className="text-xs font-medium text-slate-500">Summe Ausgaben</dt>
                        <dd className="mt-1 text-lg font-semibold tabular-nums text-amber-700">{formatEuro(summeAusgaben)} €</dd></div>
                    <div className="min-w-0"><dt className="text-xs font-medium text-slate-500">Anfangsbestand</dt>
                        <dd className="mt-1 text-lg font-semibold tabular-nums text-slate-700">{formatEuro(kassenbuch.saldoStart)} €</dd></div>
                    <div className="min-w-0"><dt className="text-xs font-medium text-slate-500">Bestand am Ende</dt>
                        <dd data-testid="bestand-am-ende" className="mt-1 text-lg font-bold tabular-nums text-slate-900">{formatEuro(kassenbuch.saldoEnde)} €</dd></div>
                </dl>
            </Card>
        </div>
    );
}

function JournalBuchung({ zeile, onOeffnen }: { zeile: JournalZeile; onOeffnen: () => void }) {
    const storniert = zeile.storniertDurchBelegId != null;
    return <tr onClick={onOeffnen} className="cursor-pointer hover:bg-rose-50/50 focus-within:bg-rose-50/50">
        <td className="px-3 py-3 align-top tabular-nums text-xs text-slate-600" title={zeile.anzeigeNummer != null
            ? `Feste Belegnummer ${zeile.anzeigeNummer}` : 'Vorläufige Position – die feste Nummer kommt mit dem Monatsabschluss'}>
            {zeile.anzeigeNummer ?? `(${zeile.vorlaeufigeNummer})`}</td>
        <td className="px-3 py-3 align-top tabular-nums text-xs text-slate-500">{formatDate(zeile.datum)}</td>
        <td className="min-w-0 px-3 py-3 align-top">
            <button type="button" onClick={event => { event.stopPropagation(); onOeffnen(); }}
                className={`block w-full min-w-0 break-words rounded text-left font-medium hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 ${storniert ? 'text-slate-500 line-through' : 'text-slate-800'}`}>
                {zeile.beschreibung || 'Buchung öffnen'}
            </button>
            {zeile.lieferantName && <p className="min-w-0 break-words text-xs text-slate-500 mt-1">{zeile.lieferantName}</p>}
            <div className="min-w-0 flex flex-wrap gap-1 mt-2">
                <span className="min-w-0 break-words rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">{journalKategorieLabel(zeile.kategorie)}</span>
                {zeile.sachkontoNummer && <span className="min-w-0 break-words rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">Konto {zeile.sachkontoNummer}</span>}
                {storniert && <span className="rounded bg-slate-200 px-1.5 py-0.5 text-xs font-medium text-slate-700">Storniert</span>}
                {zeile.stornoFuerBelegId != null && <span className="rounded bg-slate-200 px-1.5 py-0.5 text-xs font-medium text-slate-700">Gegenbuchung</span>}
            </div>
        </td>
        <td className="px-3 py-3 align-top text-xs text-rose-700">Beleg #{zeile.belegId}</td>
        <td className="px-3 py-3 align-top text-right tabular-nums text-emerald-700">{zeile.einnahme != null ? `${formatEuro(zeile.einnahme)} €` : '–'}</td>
        <td className="px-3 py-3 align-top text-right tabular-nums text-amber-700">{zeile.ausgabe != null ? `${formatEuro(zeile.ausgabe)} €` : '–'}</td>
        <td className="px-3 py-3 align-top text-right tabular-nums text-slate-700">{formatEuro(zeile.saldoNachher)} €</td>
    </tr>;
}

/**
 * Zeitraum + Suche fuer das Kassenbuch. Vorher gab es beides nicht: die Seite
 * zeigte immer alle Bewegungen seit Beginn.
 */
function KassenbuchFilter({ von, bis, onVonChange, onBisChange, search, onSearchChange }: {
    von: string;
    bis: string;
    onVonChange: (v: string) => void;
    onBisChange: (v: string) => void;
    search: string;
    onSearchChange: (v: string) => void;
}) {
    const heute = new Date();
    const setzeZeitraum = (start: Date, ende: Date) => {
        onVonChange(isoDatum(start));
        onBisChange(isoDatum(ende));
    };
    const dieserMonat = () => setzeZeitraum(
        new Date(heute.getFullYear(), heute.getMonth(), 1), heute);
    const letzterMonat = () => setzeZeitraum(
        new Date(heute.getFullYear(), heute.getMonth() - 1, 1),
        new Date(heute.getFullYear(), heute.getMonth(), 0));
    const diesesJahr = () => setzeZeitraum(
        new Date(heute.getFullYear(), 0, 1), heute);

    return (
        <Card className="p-4 flex flex-wrap items-end gap-3">
            <Field label="Von">
                <DatePicker aria-label="Von" value={von} onChange={onVonChange} />
            </Field>
            <Field label="Bis">
                <DatePicker aria-label="Bis" value={bis} onChange={onBisChange} />
            </Field>
            <div className="flex flex-wrap items-center gap-2 pb-0.5">
                <Button variant="outline" size="sm" onClick={dieserMonat}
                    className="border-rose-300 text-rose-700 hover:bg-rose-50">Dieser Monat</Button>
                <Button variant="outline" size="sm" onClick={letzterMonat}
                    className="border-rose-300 text-rose-700 hover:bg-rose-50">Letzter Monat</Button>
                <Button variant="outline" size="sm" onClick={diesesJahr}
                    className="border-rose-300 text-rose-700 hover:bg-rose-50">Dieses Jahr</Button>
            </div>
            <div className="relative flex-1 min-w-[14rem]">
                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Suche</label>
                <Search className="absolute left-3 top-[2.15rem] w-4 h-4 text-slate-400" aria-hidden />
                <Input
                    type="text"
                    value={search}
                    onChange={e => onSearchChange(e.target.value)}
                    placeholder="Beschreibung, Lieferant, Art…"
                    className="pl-10"
                />
            </div>
        </Card>
    );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div>
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">{label}</label>
            {children}
        </div>
    );
}

export function KpiTile({ label, value, icon, highlight }: { label: string; value: string; icon: React.ReactNode; highlight?: boolean }) {
    return (
        <Card className={`p-4 ${highlight ? 'border-rose-200 bg-rose-50/50' : ''}`}>
            <div className="flex items-center gap-2 text-slate-500 text-xs uppercase font-semibold tracking-wide">
                {icon}
                {label}
            </div>
            <div className="mt-2 text-2xl font-bold text-slate-900 tabular-nums">{value}</div>
        </Card>
    );
}
