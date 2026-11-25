import { ArrowLeft, Edit2, MapPin, Wallet } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { LIEFERANT_ROLLEN, type LieferantDetail, type LieferantStatistik } from '../../types';

/** Platzhalter für eine Kennzahl, solange sie noch lädt. */
const KennzahlLaedt = () => (
    <span className="mt-1 block h-6 w-16 rounded bg-slate-200/80 motion-safe:animate-pulse" aria-label="Wird geladen" />
);

const formatCurrency = (val?: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

interface Props {
    lieferant: LieferantDetail;
    statistik: LieferantStatistik | null | undefined;
    statistikLaedt: boolean;
    onBack: () => void;
    onEdit: () => void;
}

/** Kopf-Karte der Lieferanten-Detailansicht: Name, Rollen, Adresse, Kennzahlen, Bearbeiten. */
export function LieferantDetailKopf({ lieferant, statistik, statistikLaedt, onBack, onEdit }: Props) {
    const initials = lieferant.lieferantenname?.slice(0, 2).toUpperCase() || "??";
    return (
        <Card className="p-6">
            {/* Gemeinsame Rezeptur fuer Kopfzeile/Reiterleiste (Abschnitt 4 im Plan,
                Ergebnis aus dem Abschnitt-3-Review): aeusseres Flex-Element umbricht
                (flex-wrap), Titelblock ist flex-1 mit Mindestbreite 18rem (der innere
                Textblock zusaetzlich min-w-0, sonst schrumpft er trotz break-words
                nicht unter seine Mindestinhaltsbreite), Kennzahlen ohne flex-1/max-w
                mit min-w-[7rem] je Kasten, Knopfblock shrink-0 ml-auto -- das ml-auto
                haelt "Bearbeiten" beim Umbruch rechts statt links (Design-Review
                Abschnitt 3, Projekt-Editor: ohne ml-auto x=89 statt x=961). Vorher
                liefen "Gesamtkosten" (34px) und "Bestellungen" (29px) aus ihren
                Kaesten, weil die Kennzahlen-Reihe "flex-1 max-w-4xl" hatte und sich
                den Platz nahm, den Titelblock und Knopfblock ebenfalls brauchten
                (Spec-Befund 2, docs/superpowers/specs/2026-09-04-layout-14-zoll.md). */}
            <div className="flex flex-wrap items-start gap-4">
                <div className="flex items-start gap-4 flex-1 min-w-[18rem]">
                    <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2 h-auto py-1 self-start">
                        <ArrowLeft className="w-5 h-5" />
                    </Button>
                    <div className="w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-xl font-bold shrink-0">
                        {initials}
                    </div>
                    <div className="min-w-0">
                        <div className="flex items-center gap-3 flex-wrap">
                            {/* min-w-0 zusaetzlich zu break-words: die <h1> ist selbst ein
                                Flex-Item und behaelt ohne min-w-0 ihre volle Mindestinhaltsbreite
                                -- bei einem einzigen langen Wort quetscht sie sich sonst quer
                                ueber die Kennzahlen (Befund 1 aus dem Abschnitt-4-Design-Review). */}
                            <h1 className="text-2xl font-bold text-slate-900 break-words min-w-0">{lieferant.lieferantenname}</h1>
                            {lieferant.rollen && lieferant.rollen.length > 0 ? (
                                lieferant.rollen.map(rolle => (
                                    <span key={rolle} className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium border border-slate-200">
                                        {LIEFERANT_ROLLEN.find(r => r.value === rolle)?.label || rolle}
                                    </span>
                                ))
                            ) : (
                                <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium border border-slate-200">
                                    {lieferant.lieferantenTyp || "Lieferant"}
                                </span>
                            )}
                            {lieferant.vorauskasse && (
                                <span
                                    className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-800 text-xs font-medium border border-amber-200"
                                    title="Rechnungen dieser Firma gelten sofort als bezahlt und stehen nicht unter Offene Posten."
                                >
                                    <Wallet className="w-3 h-3" />
                                    Zahlung im Voraus
                                </span>
                            )}
                        </div>
                        {/* aliasName ist ein Block-<p> und braucht nur break-words. Die Adresse
                            ist ein Textknoten in einem Flex-<p>; erst der umschliessende
                            <span min-w-0 break-words> macht ihn umbrechbar (Nacharbeit Abschnitt 9). */}
                        <div className="mt-1 text-slate-500 space-y-0.5">
                            {lieferant.aliasName && <p className="text-slate-600 break-words">auch: {lieferant.aliasName}</p>}
                            <p className="flex items-center gap-2"><MapPin className="w-4 h-4 shrink-0" /> <span className="min-w-0 break-words">{lieferant.strasse}, {lieferant.plz} {lieferant.ort}</span></p>
                        </div>
                    </div>
                </div>

                {/* Kennzahlen ohne "flex-1 max-w-4xl" (das zwang die Kaesten auf 34px/29px);
                    min-w-[7rem] je Kasten haelt die Beschriftung lesbar. Alle Kaesten Slate mit Rose-Beschriftung
                    (Firmenfarbe; Fremdfarben nur mit Statusbedeutung). */}
                <div className="flex flex-wrap gap-4 shrink-0" data-testid="lieferant-kennzahlen">
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 min-w-[7rem]">
                        <p className="text-xs text-rose-600 uppercase tracking-wide">Gesamtkosten</p>
                        {statistikLaedt ? <KennzahlLaedt /> : <p className="text-lg font-semibold text-slate-900">{formatCurrency(statistik?.gesamtKosten)}</p>}
                    </div>
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 min-w-[7rem]">
                        <p className="text-xs text-rose-600 uppercase tracking-wide">Bestellungen</p>
                        {statistikLaedt ? <KennzahlLaedt /> : <p className="text-lg font-semibold text-slate-900">{statistik?.bestellungAnzahl || 0}</p>}
                    </div>
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 min-w-[7rem]">
                        <p className="text-xs text-rose-600 uppercase tracking-wide">Artikel</p>
                        {statistikLaedt ? <KennzahlLaedt /> : <p className="text-lg font-semibold text-slate-900">{statistik?.artikelAnzahl || 0}</p>}
                    </div>
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 min-w-[7rem]">
                        <p className="text-xs text-rose-600 uppercase tracking-wide">Lieferzeit Ø</p>
                        {statistikLaedt ? <KennzahlLaedt /> : <p className="text-lg font-semibold text-slate-900">{statistik?.lieferzeit || lieferant.lieferzeit || 0} Tage</p>}
                    </div>
                </div>

                <div className="shrink-0 ml-auto flex flex-wrap items-start gap-2">
                    <Button variant="outline" onClick={onEdit}>
                        <Edit2 className="w-4 h-4 mr-2" /> Bearbeiten
                    </Button>
                </div>
            </div>
        </Card>
    );
}
