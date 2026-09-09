import { ArrowRight, Coins, Loader2, Search } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import type { KassenBewegung, Kassenbuch } from '../../types';
import { KassenbuchAbschlussLeiste } from './KassenbuchAbschlussLeiste';
import { KATEGORIE_FARBE, KATEGORIE_LABELS, formatDate, formatEuro, inputCls, isoDatum } from './belegFormat';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): heutiger
// KassenbuchView + TKontoZeile + KassenbuchFilter + KpiTile aus
// BelegeKasseEditor.tsx. Das T-Konto bleibt inhaltlich unveraendert stehen
// und wird erst in Task 11 ersetzt.

export function KassenbuchJournal({ kassenbuch, loading, von, bis, onVonChange, onBisChange, search, onSearchChange, onSelectBeleg, onGeaendert }: {
    kassenbuch: Kassenbuch | null;
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
    const filterLeiste = (
        <KassenbuchFilter
            von={von} bis={bis} onVonChange={onVonChange} onBisChange={onBisChange}
            search={search} onSearchChange={onSearchChange}
        />
    );

    if (loading) {
        return (
            <div className="space-y-4">
                {filterLeiste}
                <div className="flex justify-center py-16"><Loader2 className="w-8 h-8 animate-spin text-rose-500" /></div>
            </div>
        );
    }
    if (!kassenbuch) {
        return (
            <div className="space-y-4">
                {filterLeiste}
                <Card className="p-12 text-center text-slate-500"><Coins className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>Kassenbuch konnte nicht geladen werden.</p></Card>
            </div>
        );
    }

    // Die Nummer kommt jetzt vom Server und haengt dauerhaft am Beleg: sie
    // wird beim Monatsabschluss vergeben und aendert sich nie wieder. Solange
    // der Monat offen ist, gibt es noch keine — dann zeigen wir eine
    // vorlaeufige Position im Zeitraum, deutlich als solche markiert.
    //
    // Vorher wurde hier durchgezaehlt. Das sah aus wie eine Belegnummer, sprang
    // aber bei jedem Zeitraumwechsel auf voellig andere Werte.
    const nummeriert = kassenbuch.bewegungen.map((b, i) => ({
        ...b,
        anzeigeNummer: b.laufendeNummer ?? null,
        vorlaeufigeNummer: i + 1,
    }));

    const term = search.trim().toLowerCase();
    const sichtbar = term
        ? nummeriert.filter(b =>
            b.beschreibung?.toLowerCase().includes(term)
            || b.lieferantName?.toLowerCase().includes(term)
            || KATEGORIE_LABELS[b.kategorie].toLowerCase().includes(term))
        : nummeriert;

    // T-Konto: Soll-Seite = Geld rein (Einnahmen + Privateinlagen),
    // Haben-Seite = Geld raus (Ausgaben + Privatentnahmen). Sortierung folgt
    // der Server-Reihenfolge (chronologisch). 0,00-€-Bewegungen (pathologisch
    // aber moeglich) landen auf der Eingang-Seite, damit nichts stillschweigend
    // verschwindet — Summenfuss-Konsistenz bleibt.
    const eingaenge = sichtbar.filter(b => b.betrag >= 0);
    const ausgaenge = sichtbar.filter(b => b.betrag < 0);
    const summeEingang = kassenbuch.summeEinnahmen + kassenbuch.summePrivateinlagen;
    const summeAusgang = kassenbuch.summeAusgaben + kassenbuch.summePrivatentnahmen;

    return (
        <div className="space-y-4">
            <KassenbuchAbschlussLeiste
                letzterAbschluss={kassenbuch.letzterAbschluss ?? null}
                offeneBewegungen={kassenbuch.offeneBewegungen ?? 0}
                onGeaendert={onGeaendert}
            />

            {filterLeiste}

            {term && (
                // Die Summen unten kommen vom Server und gelten fuer den ganzen
                // Zeitraum. Ohne diesen Hinweis wirkte es, als passten Zeilen und
                // Summen nicht zusammen.
                <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-900 flex items-start gap-2">
                    <Search className="w-4 h-4 mt-0.5 shrink-0" aria-hidden />
                    <span>
                        Suche aktiv – es werden <strong>{sichtbar.length} von {nummeriert.length}</strong> Zeilen gezeigt.
                        Summen und Saldo unten gelten weiterhin für den ganzen Zeitraum.
                    </span>
                </div>
            )}
            <Card className="overflow-hidden">
                {/* Konto-Kopf */}
                <div className="border-b-2 border-slate-800 bg-slate-50/60 px-6 py-3 text-center">
                    <div className="text-[10px] uppercase tracking-[0.25em] text-slate-500 font-semibold">Kasse · Bargeldkonto</div>
                    <div className="text-lg font-bold text-slate-900 mt-0.5">Bar-Bewegungen</div>
                    <div className="text-xs text-slate-500 mt-0.5">
                        {formatDate(von)} – {formatDate(bis)} · {nummeriert.length} Buchungen
                    </div>
                </div>

                {/* Spaltenköpfe */}
                <div className="grid grid-cols-2 border-b border-slate-300">
                    <div className="px-6 py-3 border-r-2 border-slate-800 bg-emerald-50/40">
                        <div className="flex items-baseline gap-2">
                            <span className="text-sm font-bold text-emerald-800 uppercase tracking-wider">Eingang</span>
                            <span className="text-[10px] text-emerald-600 font-medium uppercase tracking-wider">Soll</span>
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5">Einnahmen + Privateinlagen</div>
                    </div>
                    <div className="px-6 py-3 bg-amber-50/40">
                        <div className="flex items-baseline gap-2 justify-end">
                            <span className="text-[10px] text-amber-700 font-medium uppercase tracking-wider">Haben</span>
                            <span className="text-sm font-bold text-amber-800 uppercase tracking-wider">Ausgang</span>
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5 text-right">Ausgaben + Privatentnahmen</div>
                    </div>
                </div>

                {/* Buchungsspalten */}
                <div className="grid grid-cols-2 min-h-[420px]">
                    {/* Eingang / Soll */}
                    <div className="border-r-2 border-slate-800 divide-y divide-slate-100">
                        {eingaenge.length === 0 ? (
                            <div className="px-6 py-10 text-center text-slate-400 text-sm">
                                {term ? 'Keine Treffer.' : 'Keine Eingänge in diesem Zeitraum.'}
                            </div>
                        ) : eingaenge.map(bew => (
                            <TKontoZeile key={`in-${bew.belegId}`} bew={bew} side="eingang" onClick={() => onSelectBeleg(bew.belegId)} />
                        ))}
                    </div>
                    {/* Ausgang / Haben */}
                    <div className="divide-y divide-slate-100">
                        {ausgaenge.length === 0 ? (
                            <div className="px-6 py-10 text-center text-slate-400 text-sm">
                                {term ? 'Keine Treffer.' : 'Keine Ausgänge in diesem Zeitraum.'}
                            </div>
                        ) : ausgaenge.map(bew => (
                            <TKontoZeile key={`out-${bew.belegId}`} bew={bew} side="ausgang" onClick={() => onSelectBeleg(bew.belegId)} />
                        ))}
                    </div>
                </div>

                {/* Doppelstrich nach Buchhalter-Tradition */}
                <div className="border-t-2 border-slate-800" />
                <div className="border-t border-slate-800 mt-[3px]" />

                {/* Summenfuß */}
                <div className="grid grid-cols-2 bg-slate-50">
                    <div className="px-6 py-3 border-r-2 border-slate-800 flex items-baseline justify-between">
                        <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Summe Eingang</span>
                        <span className="text-xl font-bold text-emerald-700 tabular-nums">{formatEuro(summeEingang)} €</span>
                    </div>
                    <div className="px-6 py-3 flex items-baseline justify-between">
                        <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Summe Ausgang</span>
                        <span className="text-xl font-bold text-amber-700 tabular-nums">{formatEuro(summeAusgang)} €</span>
                    </div>
                </div>

                {/* Saldo-Zeile */}
                <div className="bg-rose-50/60 border-t border-rose-200 px-6 py-3">
                    <div className="flex items-baseline justify-between max-w-2xl mx-auto gap-4">
                        <div>
                            <div className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">Anfangsbestand</div>
                            <div className="text-sm text-slate-600 tabular-nums">{formatEuro(kassenbuch.saldoStart)} €</div>
                        </div>
                        <ArrowRight className="w-5 h-5 text-slate-300 shrink-0" aria-hidden />
                        <div className="text-right">
                            <div className="text-[10px] uppercase tracking-wider text-rose-600 font-semibold">Neuer Saldo</div>
                            <div className="text-2xl font-bold text-rose-700 tabular-nums">{formatEuro(kassenbuch.saldoEnde)} €</div>
                        </div>
                    </div>
                </div>
            </Card>
        </div>
    );
}

function TKontoZeile({ bew, side, onClick }: {
    bew: KassenBewegung & { anzeigeNummer: number | null; vorlaeufigeNummer: number };
    side: 'eingang' | 'ausgang';
    onClick: () => void;
}) {
    const istPrivat = bew.kategorie === 'PRIVATENTNAHME' || bew.kategorie === 'PRIVATEINLAGE';
    const betragColor = side === 'eingang'
        ? (istPrivat ? 'text-lime-700' : 'text-emerald-700')
        : (istPrivat ? 'text-fuchsia-700' : 'text-amber-700');
    const hoverBg = side === 'eingang' ? 'hover:bg-emerald-50/40' : 'hover:bg-amber-50/40';
    const istStorno = bew.stornoFuerBelegId != null;
    const wurdeStorniert = bew.storniertDurchBelegId != null;
    return (
        <button type="button" onClick={onClick}
            className={`w-full text-left px-6 py-2.5 cursor-pointer ${hoverBg} focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 focus-visible:ring-inset`}>
            <div className="flex items-baseline justify-between gap-3">
                <div className="flex items-baseline gap-3 min-w-0 flex-1">
                    {/* Feste Belegnummer, sobald der Monat abgeschlossen ist.
                        Vorher nur eine vorlaeufige Position, in Klammern, damit
                        sie niemand fuer die endgueltige Nummer haelt. */}
                    <span
                        className={`text-xs tabular-nums w-8 shrink-0 text-right ${
                            bew.anzeigeNummer != null ? 'text-slate-600 font-semibold' : 'text-slate-300'
                        }`}
                        title={bew.anzeigeNummer != null
                            ? `Feste Belegnummer ${bew.anzeigeNummer}`
                            : 'Vorläufige Position – die feste Nummer kommt mit dem Monatsabschluss'}
                    >
                        {bew.anzeigeNummer ?? `(${bew.vorlaeufigeNummer})`}
                    </span>
                    <span className="text-xs text-slate-500 tabular-nums w-20 shrink-0">{formatDate(bew.datum)}</span>
                    <div className="min-w-0 flex-1">
                        <div className={`text-sm font-medium truncate ${wurdeStorniert ? 'text-slate-400 line-through' : 'text-slate-800'}`}>
                            {bew.beschreibung || '–'}
                            {bew.lieferantName && <span className="ml-2 text-slate-400 font-normal">({bew.lieferantName})</span>}
                        </div>
                        <div className="flex flex-wrap items-center gap-1 mt-1">
                            <span className={`text-[10px] uppercase tracking-wider font-semibold px-1.5 py-0.5 rounded ${KATEGORIE_FARBE[bew.kategorie]}`}>
                                {KATEGORIE_LABELS[bew.kategorie]}
                            </span>
                            {/* Gegenkonto gehoert auf den Kassenbuch-Ausdruck und
                                damit auch auf den Bildschirm — fehlt es, sieht der
                                Buchhalter sofort, wo noch Arbeit liegt. */}
                            {bew.sachkontoNummer && (
                                <span className="text-[10px] text-slate-500 px-1.5 py-0.5 rounded bg-slate-100">
                                    Konto {bew.sachkontoNummer}
                                </span>
                            )}
                            {/* Storno-Markierung mit Text, nicht nur Durchstreichung:
                                Farbe und Linie allein tragen die Aussage nicht. */}
                            {wurdeStorniert && (
                                <span className="text-[10px] uppercase tracking-wider font-semibold px-1.5 py-0.5 rounded bg-slate-200 text-slate-600">
                                    Storniert
                                </span>
                            )}
                            {istStorno && (
                                <span className="text-[10px] uppercase tracking-wider font-semibold px-1.5 py-0.5 rounded bg-slate-200 text-slate-600">
                                    Gegenbuchung
                                </span>
                            )}
                        </div>
                    </div>
                </div>
                <div className="shrink-0 text-right">
                    <div className={`text-base font-semibold tabular-nums ${betragColor}`}>
                        {formatEuro(Math.abs(bew.betrag))} €
                    </div>
                    {/* Laufender Kassenstand nach dieser Buchung. Der Server rechnet
                        ihn laengst mit (saldoNachher) — angezeigt wurde er nie.
                        Genau das braucht man beim Nachzaehlen der Kasse. */}
                    <div className="text-[11px] text-slate-400 tabular-nums">
                        Stand {formatEuro(bew.saldoNachher)} €
                    </div>
                </div>
            </div>
        </button>
    );
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
                <input type="date" value={von} onChange={e => onVonChange(e.target.value)} className={inputCls} />
            </Field>
            <Field label="Bis">
                <input type="date" value={bis} onChange={e => onBisChange(e.target.value)} className={inputCls} />
            </Field>
            <div className="flex items-center gap-2 pb-0.5">
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
                <input
                    type="text"
                    value={search}
                    onChange={e => onSearchChange(e.target.value)}
                    placeholder="Beschreibung, Lieferant, Art…"
                    className={`${inputCls} pl-10`}
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
