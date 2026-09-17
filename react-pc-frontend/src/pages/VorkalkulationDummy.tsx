import { useState } from 'react';
import { ChevronRight, Info, Scale, Trash2 } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useToast } from '../components/ui/toast';
import { formatCurrency } from '../components/artikel/formatCurrency';
import { cn } from '../lib/utils';

import { VorkalkulationDialog } from '../features/vorkalkulation/VorkalkulationDialog';
import { VorkalkulationListe } from '../features/vorkalkulation/VorkalkulationListe';
import { entwurfAlsZahl } from '../features/vorkalkulation/berechnung';
import {
    BEISPIEL_EINTRAEGE,
    beispielKalkulation,
    leereKalkulation,
} from '../features/vorkalkulation/beispieldaten';
import type { VorkalkulationDaten } from '../features/vorkalkulation/types';

/**
 * Click-Dummy der Vor-Kalkulation.
 *
 * Zweck: UI und UX gemeinsam durchgehen, bevor der Dokumenteditor und die
 * Editor-Seiten angefasst werden. Deshalb sind die Leistungs-Karten hier
 * **Nachbauten** der echten `ServiceBlock`-Karte und kein Produktivcode —
 * nichts wird gespeichert, nichts an den Server geschickt.
 *
 * Die Kalkulation selbst (`features/vorkalkulation/`) ist dagegen schon die
 * echte Komponente und wandert spaeter unveraendert in den Dokumenteditor.
 */

type Buehne = 'anfrage' | 'projekt' | 'liste';

interface DummyLeistung {
    id: string;
    positionsnummer: string;
    titel: string;
    menge: string;
    einheit: string;
    einzelpreis: number;
    kalkulation: VorkalkulationDaten | null;
}

const ANFRAGE_LEISTUNGEN: DummyLeistung[] = [
    {
        id: 'l1',
        positionsnummer: '1',
        titel: 'Stahltreppe außen, feuerverzinkt',
        menge: '1',
        einheit: 'Stk',
        einzelpreis: 0,
        kalkulation: null,
    },
    {
        id: 'l2',
        positionsnummer: '2',
        titel: 'Geländer Podest, 4,20 m',
        menge: '1',
        einheit: 'Stk',
        einzelpreis: 0,
        kalkulation: null,
    },
];

const PROJEKT_LEISTUNGEN: DummyLeistung[] = [
    {
        id: 'p1',
        positionsnummer: '1',
        titel: 'Vordach Eingang, pulverbeschichtet',
        menge: '1',
        einheit: 'Stk',
        einzelpreis: 6455.7,
        kalkulation: beispielKalkulation(),
    },
    {
        id: 'p2',
        positionsnummer: '2',
        titel: 'Zusatzstütze Vordach',
        menge: '2',
        einheit: 'Stk',
        einzelpreis: 340,
        kalkulation: null,
    },
];

/** Nachbau der Leistungs-Karte aus dem Dokumenteditor. */
function LeistungsKarte({
    leistung,
    imProjekt,
    onKalkulationOeffnen,
}: {
    leistung: DummyLeistung;
    /** Im Projekt darf nur geoeffnet werden, was in der Anfrage angelegt wurde. */
    imProjekt: boolean;
    onKalkulationOeffnen: () => void;
}) {
    const hatKalkulation = leistung.kalkulation !== null;
    const gesamt = leistung.einzelpreis * entwurfAlsZahl(leistung.menge);

    return (
        <div className="rounded-xl border border-slate-200 bg-white transition-all hover:border-slate-300 hover:shadow-sm">
            <div className="flex items-start gap-3 p-4">
                <div className="mt-0.5 flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg border border-rose-100 bg-rose-50 text-sm font-bold text-rose-600">
                    {leistung.positionsnummer}
                </div>

                <div className="min-w-0 flex-1 pt-1">
                    <p className="truncate text-sm font-semibold text-slate-900">{leistung.titel}</p>
                    <p className="mt-0.5 text-xs text-slate-500">
                        {leistung.menge} {leistung.einheit} × {formatCurrency(leistung.einzelpreis)}
                        {hatKalkulation && (
                            <span className="ml-2 rounded border border-rose-200 bg-rose-50 px-1.5 py-0.5 text-[10px] font-medium text-rose-700">
                                kalkuliert
                            </span>
                        )}
                    </p>
                </div>

                <div className="flex-shrink-0 pt-1 text-sm font-bold tabular-nums text-slate-900">
                    {formatCurrency(gesamt)}
                </div>

                <div className="flex flex-shrink-0 items-center gap-1">
                    {/* Das Anfrage-Gate: Anlegen nur in der Anfrage. Im Projekt
                        bleibt eine vorhandene Kalkulation bearbeitbar — sie haengt
                        an der Leistung, nicht am Dokument. */}
                    {(!imProjekt || hatKalkulation) && (
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={onKalkulationOeffnen}
                            title={imProjekt
                                ? 'Die in der Anfrage angelegte Kalkulation bearbeiten'
                                : 'Vor-Kalkulation für diese Leistung anlegen'}
                        >
                            <Scale className="h-4 w-4" aria-hidden="true" />
                            {hatKalkulation ? 'Vor-Kalkulation' : 'Vor-Kalkulation anlegen'}
                        </Button>
                    )}
                    <button
                        type="button"
                        aria-label={`Position ${leistung.positionsnummer} löschen`}
                        className="rounded-md p-1.5 text-slate-300 transition-colors hover:bg-rose-50 hover:text-rose-600"
                    >
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </button>
                </div>
            </div>
        </div>
    );
}

export default function VorkalkulationDummy() {
    const toast = useToast();
    const [buehne, setBuehne] = useState<Buehne>('anfrage');
    const [anfrage, setAnfrage] = useState<DummyLeistung[]>(ANFRAGE_LEISTUNGEN);
    const [projekt, setProjekt] = useState<DummyLeistung[]>(PROJEKT_LEISTUNGEN);
    const [offeneLeistung, setOffeneLeistung] = useState<{ quelle: Buehne; id: string } | null>(null);

    const aktuelleListe = offeneLeistung?.quelle === 'projekt' ? projekt : anfrage;
    const aktuelleLeistung = offeneLeistung
        ? aktuelleListe.find((l) => l.id === offeneLeistung.id) ?? null
        : null;

    const preisUebernehmen = (preis: number, daten: VorkalkulationDaten) => {
        if (!offeneLeistung) return;
        const aktualisiere = (liste: DummyLeistung[]) =>
            liste.map((l) => (l.id === offeneLeistung.id ? { ...l, einzelpreis: preis, kalkulation: daten } : l));

        if (offeneLeistung.quelle === 'projekt') setProjekt(aktualisiere);
        else setAnfrage(aktualisiere);

        setOffeneLeistung(null);
        toast.success(`Preis übernommen: ${formatCurrency(preis)}`);
    };

    const buehnen: { wert: Buehne; text: string }[] = [
        { wert: 'anfrage', text: 'Dokument aus einer Anfrage' },
        { wert: 'projekt', text: 'Dokument aus einem Projekt' },
        { wert: 'liste', text: 'Reiter „Vor-Kalkulation“' },
    ];

    return (
        <PageLayout
            ribbonCategory="Entwurf"
            title="VOR-KALKULATION (CLICK-DUMMY)"
            subtitle="Zum gemeinsamen Durchgehen von Aufbau und Bedienung. Nichts wird gespeichert."
        >
            <Card className="flex items-start gap-3 border-rose-200 bg-rose-50/50 p-4">
                <Info className="mt-0.5 h-5 w-5 flex-shrink-0 text-rose-600" aria-hidden="true" />
                <div className="text-sm text-slate-700">
                    <p className="font-semibold text-slate-900">Das ist ein Entwurf zum Anschauen</p>
                    <p className="mt-1">
                        Die Leistungs-Karten unten sind Nachbauten aus dem Dokumenteditor. Die Kalkulation
                        dahinter ist dagegen schon echt und rechnet wie die bisherige Excel-Mappe. Änderungen
                        verschwinden beim Neuladen der Seite.
                    </p>
                </div>
            </Card>

            {/* Umschalter zwischen den drei Einstiegen */}
            <div className="flex flex-wrap gap-2" role="group" aria-label="Ansicht wählen">
                {buehnen.map((b) => (
                    <button
                        key={b.wert}
                        type="button"
                        aria-pressed={buehne === b.wert}
                        onClick={() => setBuehne(b.wert)}
                        className={cn(
                            'rounded-lg border px-3 py-1.5 text-sm transition-colors',
                            buehne === b.wert
                                ? 'border-rose-600 bg-rose-600 text-white'
                                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
                        )}
                    >
                        {b.text}
                    </button>
                ))}
            </div>

            {buehne === 'anfrage' && (
                <div className="space-y-3">
                    <div>
                        <h2 className="text-base font-bold text-slate-900">Angebot 2026-0148 zur Anfrage</h2>
                        <p className="text-sm text-slate-500">
                            Hier wird die Vor-Kalkulation angelegt — vor dem Angebot, wenn der Preis noch offen ist.
                        </p>
                    </div>
                    {anfrage.map((leistung) => (
                        <LeistungsKarte
                            key={leistung.id}
                            leistung={leistung}
                            imProjekt={false}
                            onKalkulationOeffnen={() => setOffeneLeistung({ quelle: 'anfrage', id: leistung.id })}
                        />
                    ))}
                </div>
            )}

            {buehne === 'projekt' && (
                <div className="space-y-3">
                    <div>
                        <h2 className="text-base font-bold text-slate-900">Angebot 2026-0131 im Projekt</h2>
                        <p className="text-sm text-slate-500">
                            Angelegt wird hier nichts Neues. Was in der Anfrage kalkuliert wurde, bleibt
                            bearbeitbar — die Kalkulation hängt an der Leistung.
                        </p>
                    </div>
                    {projekt.map((leistung) => (
                        <LeistungsKarte
                            key={leistung.id}
                            leistung={leistung}
                            imProjekt
                            onKalkulationOeffnen={() => setOffeneLeistung({ quelle: 'projekt', id: leistung.id })}
                        />
                    ))}
                    <p className="flex items-center gap-1.5 text-xs text-slate-500">
                        <ChevronRight className="h-3 w-3" aria-hidden="true" />
                        Bei Position 2 fehlt der Knopf: Ohne Kalkulation aus der Anfrage lässt sich im Projekt
                        keine neue anlegen.
                    </p>
                </div>
            )}

            {buehne === 'liste' && (
                <div className="space-y-3">
                    <div>
                        <h2 className="text-base font-bold text-slate-900">Reiter „Vor-Kalkulation“</h2>
                        <p className="text-sm text-slate-500">
                            So sieht der zweite Einstieg im Anfrage- und Projekt-Editor aus. Zeile anklicken öffnet
                            dieselbe Kalkulation.
                        </p>
                    </div>
                    <VorkalkulationListe
                        eintraege={BEISPIEL_EINTRAEGE}
                        onOeffnen={() => setOffeneLeistung({ quelle: 'projekt', id: 'p1' })}
                    />
                </div>
            )}

            <VorkalkulationDialog
                offen={aktuelleLeistung !== null}
                leistungTitel={aktuelleLeistung?.titel ?? ''}
                positionsnummer={aktuelleLeistung?.positionsnummer ?? ''}
                startdaten={
                    aktuelleLeistung?.kalkulation
                        ?? (offeneLeistung?.quelle === 'anfrage' ? beispielKalkulation() : leereKalkulation())
                }
                onSchliessen={() => setOffeneLeistung(null)}
                onPreisUebernehmen={preisUebernehmen}
            />
        </PageLayout>
    );
}
