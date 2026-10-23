import { useCallback, useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { Check, X, Calendar, User, FileText, Plane, Stethoscope, Clock, AlertTriangle, Ban, Loader2 } from 'lucide-react';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useToast } from '../components/ui/toast';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { leseFehlermeldung } from '../lib/apiFehler';
import { formatUrlaubstage, formatUrlaubstageMitEinheit, vorjahresHinweis } from '../lib/urlaubstage';
import { resturlaubSchluessel, useResturlaubJeAntrag } from '../hooks/useResturlaubJeAntrag';
import { stornoRueckfrage, type AntragTyp } from '../lib/antragStorno';
import { scrolleMittigInDenBlick } from '../lib/scrollInDenBlick';

interface Urlaubsantrag {
    id: number;
    mitarbeiterName: string;
    mitarbeiter: {
        id: number;
        vorname: string;
        nachname: string;
        jahresUrlaub: number;
    };
    vonDatum: string;
    bisDatum: string;
    bemerkung: string | null;
    status: AntragStatus;
    erstellDatum: string;
    typ: AntragTyp;
    /** Wann das Büro entschieden hat (null, solange der Antrag offen ist). */
    entschiedenAm?: string | null;
    /** Hat der Mitarbeiter die Entscheidung am Handy schon gesehen? */
    entscheidungGesehen?: boolean;
}

type AntragStatus = 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT';

/** Klartext je Stand – nie der Rohwert („STORNIERT") auf dem Bildschirm. */
const STATUS_TEXTE: Record<AntragStatus, { label: string; leer: string; klasse: string }> = {
    OFFEN: { label: 'Offen', leer: 'Keine offenen Anträge.', klasse: 'bg-yellow-100 text-yellow-700' },
    GENEHMIGT: { label: 'Genehmigt', leer: 'Keine genehmigten Anträge.', klasse: 'bg-green-100 text-green-700' },
    ABGELEHNT: { label: 'Abgelehnt', leer: 'Keine abgelehnten Anträge.', klasse: 'bg-red-100 text-red-700' },
    STORNIERT: { label: 'Storniert', leer: 'Keine stornierten Anträge.', klasse: 'bg-slate-100 text-slate-700' },
};

function istAntragStatus(wert: string): wert is AntragStatus {
    return wert in STATUS_TEXTE;
}

export default function Urlaubsantraege() {
    const confirmDialog = useConfirm();
    const toast = useToast();
    const [aktionLaeuftId, setAktionLaeuftId] = useState<number | null>(null);
    const [searchParams, setSearchParams] = useSearchParams();
    const [antraege, setAntraege] = useState<Urlaubsantrag[]>([]);
    const [statusFilter, setStatusFilter] = useState<string>(() => {
        return searchParams.get('status') || 'OFFEN';
    });
    const [loading, setLoading] = useState(true);
    const [fokusId, setFokusId] = useState<number | null>(() => {
        const v = searchParams.get('antragId');
        return v ? Number(v) : null;
    });
    // Krankmeldungs-Hinweis je Antrag-ID (Task 17, Abschnitt 5). Reine
    // Warnung fürs Büro, keine Sperre -- siehe der Effect weiter unten,
    // der diesen State über /api/langzeitkrankmeldungen/urlaubs-hinweise füllt.
    const [hinweise, setHinweise] = useState<Record<number, string[]>>({});
    // Resturlaub (mit Vorjahresrest) je Mitarbeiter und Jahr – nur für offene Urlaubsanträge.
    const resturlaub = useResturlaubJeAntrag(antraege);

    // Deep-link: read status + optionalen fokusId aus URL.
    // fokusId wird nach dem Scrollen wieder aus der URL entfernt, damit die Hervorhebung
    // nicht beim Reload wieder triggert.
    // Den Zustand übernehmen wir beim Rendern, das Aufräumen der Adresszeile im Effekt.
    const [verarbeiteteParams, setVerarbeiteteParams] = useState(searchParams);
    if (verarbeiteteParams !== searchParams) {
        setVerarbeiteteParams(searchParams);
        const statusParam = searchParams.get('status');
        const antragParam = searchParams.get('antragId');
        if (statusParam) setStatusFilter(statusParam);
        if (antragParam) setFokusId(Number(antragParam));
    }
    useEffect(() => {
        if (searchParams.get('status') || searchParams.get('antragId')) {
            setSearchParams({}, { replace: true });
        }
    }, [searchParams, setSearchParams]);

    // Scroll + Highlight, sobald die Liste die fokussierte ID enthält.
    useEffect(() => {
        if (fokusId == null) return;
        if (!antraege.some(a => a.id === fokusId)) return;
        const t = window.setTimeout(() => {
            const el = document.querySelector(`[data-antrag-id="${fokusId}"]`) as HTMLElement | null;
            if (el) {
                scrolleMittigInDenBlick(el);
                el.classList.add('ring-2', 'ring-rose-400', 'ring-offset-2');
                window.setTimeout(() => {
                    el.classList.remove('ring-2', 'ring-rose-400', 'ring-offset-2');
                    setFokusId(null);
                }, 2400);
            } else {
                setFokusId(null);
            }
        }, 80);
        return () => window.clearTimeout(t);
    }, [fokusId, antraege]);

    // Anderer Status-Filter: Ladeanzeige an, bevor der Effekt neu lädt.
    const [geladenerFilter, setGeladenerFilter] = useState(statusFilter);
    if (geladenerFilter !== statusFilter) {
        setGeladenerFilter(statusFilter);
        setLoading(true);
    }

    // Promise-Kette: Zustand wird erst gesetzt, wenn die Antwort da ist.
    const loadAntraege = useCallback(() => fetch(`/api/urlaub/antraege?status=${statusFilter}`)
        .then(async (res) => {
            if (res.ok) {
                const data = await res.json();
                setAntraege(data);
            }
        })
        .catch((error) => {
            console.error("Error loading requests", error);
        })
        .finally(() => {
            setLoading(false);
        }), [statusFilter]);

    useEffect(() => {
        void loadAntraege();
    }, [loadAntraege]);

    // Hinweis auf eine laufende Krankmeldung, dort abgefragt, wo die
    // Entscheidung fällt: bei jedem offenen Antrag, direkt beim Laden, nicht
    // erst nach dem Klick auf "Genehmigen". Genau EIN Promise.all für alle
    // offenen Anträge -- kein Fetch-Wasserfall in einer Schleife
    // (kriterien.md, Performance). Der Hinweis ist Beiwerk: ein Fehler hier
    // darf die Antragsliste nicht kaputt machen, deshalb kein Toast/Error-UI,
    // nur ein leerer Kasten (= gar keiner).
    // Ohne offene Anträge gibt es keine Hinweise: gleich beim Rendern leeren.
    const [hinweiseFuer, setHinweiseFuer] = useState(antraege);
    if (hinweiseFuer !== antraege) {
        setHinweiseFuer(antraege);
        if (!antraege.some(a => a.status === 'OFFEN')) setHinweise({});
    }
    useEffect(() => {
        const offeneAntraege = antraege.filter(a => a.status === 'OFFEN');
        if (offeneAntraege.length === 0) return;
        let abgebrochen = false;
        Promise.all(
            offeneAntraege.map(async (antrag): Promise<[number, string[]]> => {
                try {
                    const params = new URLSearchParams({
                        mitarbeiterId: String(antrag.mitarbeiter.id),
                        von: antrag.vonDatum,
                        bis: antrag.bisDatum,
                    });
                    const res = await fetch(`/api/langzeitkrankmeldungen/urlaubs-hinweise?${params.toString()}`);
                    if (!res.ok) return [antrag.id, []];
                    const data = await res.json();
                    return [antrag.id, Array.isArray(data.warnungen) ? data.warnungen : []];
                } catch (error) {
                    console.error('Error loading Krankmeldungs-Hinweis', error);
                    return [antrag.id, []];
                }
            }),
        ).then((ergebnisse) => {
            if (abgebrochen) return;
            setHinweise(Object.fromEntries(ergebnisse));
        });
        return () => { abgebrochen = true; };
    }, [antraege]);

    /** Genehmigen, Ablehnen, Stornieren – ein Ablauf, damit jede Antwort des Servers sichtbar wird. */
    const entscheide = async (id: number, aktion: 'approve' | 'reject' | 'storno', erfolg: string, ersatzFehler: string) => {
        setAktionLaeuftId(id);
        try {
            const res = await fetch(`/api/urlaub/antraege/${id}/${aktion}`, { method: 'PUT' });
            if (!res.ok) {
                toast.error(await leseFehlermeldung(res, ersatzFehler));
                return;
            }
            toast.success(erfolg);
            setLoading(true);
            await loadAntraege();
        } catch (error) {
            console.error(`Error (${aktion}) request`, error);
            toast.error('Server nicht erreichbar. Bitte später erneut versuchen.');
        } finally {
            setAktionLaeuftId(null);
        }
    };

    const handleApprove = async (id: number) => {
        if (!await confirmDialog({ title: 'Genehmigen', message: 'Urlaubsantrag genehmigen?', variant: 'info', confirmLabel: 'Genehmigen' })) return;
        await entscheide(id, 'approve', 'Antrag genehmigt.', 'Der Antrag konnte nicht genehmigt werden.');
    };

    const handleReject = async (id: number) => {
        if (!await confirmDialog({ title: 'Ablehnen', message: 'Urlaubsantrag ablehnen?', variant: 'danger', confirmLabel: 'Ablehnen' })) return;
        await entscheide(id, 'reject', 'Antrag abgelehnt.', 'Der Antrag konnte nicht abgelehnt werden.');
    };

    const handleStorno = async (antrag: Urlaubsantrag) => {
        if (!await confirmDialog({ ...stornoRueckfrage(antrag.typ), variant: 'danger', confirmLabel: 'Stornieren' })) return;
        await entscheide(antrag.id, 'storno', 'Antrag storniert.', 'Der Antrag konnte nicht storniert werden.');
    };

    const getStatusBadge = (status: string) => {
        const text = istAntragStatus(status) ? STATUS_TEXTE[status] : { label: 'Unbekannt', klasse: 'bg-slate-100 text-slate-700' };
        return <span className={`px-2 py-1 rounded text-xs font-medium ${text.klasse}`}>{text.label}</span>;
    };

    const leerText = istAntragStatus(statusFilter) ? STATUS_TEXTE[statusFilter].leer : 'Keine Anträge gefunden.';

    return (
        <PageLayout>
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Urlaubsverwaltung
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900 uppercase">
                        URLAUBSANTRÄGE
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Verwaltung und Genehmigung von Urlaubsanträgen
                    </p>
                </div>
                <div className="w-full md:w-64">
                    <Select
                        value={statusFilter}
                        onChange={(val) => setStatusFilter(val)}
                        options={[
                            { value: 'OFFEN', label: 'Offene Anträge' },
                            { value: 'GENEHMIGT', label: 'Genehmigte Anträge' },
                            { value: 'ABGELEHNT', label: 'Abgelehnte Anträge' },
                            { value: 'STORNIERT', label: 'Stornierte Anträge' }
                        ]}
                    />
                </div>
            </div>

            <div className="space-y-4">
                {antraege.length === 0 && !loading && (
                    <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                        <p>{leerText}</p>
                    </div>
                )}

                {antraege.map((antrag) => (
                    <Card
                        key={antrag.id}
                        data-antrag-id={antrag.id}
                        className="p-4 border-slate-200 transition-shadow"
                    >
                        <div className="flex flex-col md:flex-row justify-between gap-4">
                            <div className="flex items-start gap-4">
                                <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-600">
                                    <User className="w-5 h-5" />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        {antrag.typ === 'KRANKHEIT' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-red-100 text-red-800">
                                                <Stethoscope className="w-3 h-3" />
                                                Krankheit
                                            </span>
                                        )}
                                        {antrag.typ === 'URLAUB' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800">
                                                <Plane className="w-3 h-3" />
                                                Urlaub
                                            </span>
                                        )}
                                        {antrag.typ === 'FORTBILDUNG' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-purple-100 text-purple-800">
                                                <FileText className="w-3 h-3" />
                                                Fortbildung
                                            </span>
                                        )}
                                        {antrag.typ === 'ZEITAUSGLEICH' && (
                                            <span className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium bg-teal-100 text-teal-800">
                                                <Clock className="w-3 h-3" />
                                                Zeitausgleich
                                            </span>
                                        )}
                                    </div>
                                    <h3 className="text-lg font-bold text-slate-900">
                                        {antrag.mitarbeiter.nachname}, {antrag.mitarbeiter.vorname}
                                    </h3>
                                    <div className="flex items-center gap-4 mt-1 text-sm text-slate-500">
                                        <span className="flex items-center gap-1">
                                            <Calendar className="w-4 h-4" />
                                            {new Date(antrag.vonDatum).toLocaleDateString()} - {new Date(antrag.bisDatum).toLocaleDateString()}
                                        </span>
                                        {antrag.mitarbeiter.jahresUrlaub && (
                                            <span className="text-rose-600 font-medium">
                                                (Jahresurlaub: {formatUrlaubstage(antrag.mitarbeiter.jahresUrlaub)} Tage)
                                            </span>
                                        )}
                                    </div>
                                    {antrag.status === 'OFFEN' && antrag.typ === 'URLAUB' && resturlaub[resturlaubSchluessel(antrag)] && (() => {
                                        const rest = resturlaub[resturlaubSchluessel(antrag)];
                                        const vorjahr = vorjahresHinweis(rest);
                                        return (
                                            <p className="mt-1 text-sm text-slate-600">
                                                Resturlaub zum Urlaubsbeginn: <span className="font-medium text-slate-900">{formatUrlaubstageMitEinheit(rest.verbleibend)}</span>
                                                {vorjahr && <span className="text-slate-500"> ({vorjahr})</span>}
                                            </p>
                                        );
                                    })()}
                                    {antrag.bemerkung && (
                                        <div className="mt-2 p-2 bg-slate-50 rounded text-sm text-slate-600 flex gap-2">
                                            <FileText className="w-4 h-4 mt-0.5 text-slate-400" />
                                            {antrag.bemerkung}
                                        </div>
                                    )}
                                </div>
                            </div>

                            <div className="flex items-center gap-4">
                                <div>
                                    {getStatusBadge(antrag.status)}
                                </div>
                                {(antrag.status === 'OFFEN' || antrag.status === 'GENEHMIGT') && (
                                    <div className="flex flex-col items-end gap-2">
                                        {antrag.status === 'OFFEN' && hinweise[antrag.id]?.length > 0 && (
                                            <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 flex items-start gap-2 max-w-sm">
                                                <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                                                <div>
                                                    {hinweise[antrag.id].map((warnung, i) => (
                                                        <p key={i}>{warnung}</p>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                        <div className="flex items-center gap-2">
                                            <Button
                                                onClick={() => handleStorno(antrag)}
                                                variant="outline"
                                                className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                                size="sm"
                                                disabled={aktionLaeuftId === antrag.id}
                                            >
                                                {aktionLaeuftId === antrag.id
                                                    ? <Loader2 className="w-4 h-4 mr-1 animate-spin" aria-hidden="true" />
                                                    : <Ban className="w-4 h-4 mr-1" aria-hidden="true" />}
                                                Stornieren
                                            </Button>
                                            <InfoButton titel="Stornieren">
                                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                                    <p>Der Antrag bekommt den Stand „Storniert". Alle Tage, die dieser Antrag im Kalender eingetragen hat, werden gelöscht. Bei Urlaub steigt der Resturlaub des Mitarbeiters entsprechend.</p>
                                                </InfoAbschnitt>
                                                <InfoAbschnitt titel="Was bewirkt das?">
                                                    <p>Der Mitarbeiter bekommt eine Nachricht aufs Handy und findet sie in der Glocke der App. Den Antrag sieht er unter „Storniert" und kann neu beantragen. Liegt ein Tag in einem bereits abgeschlossenen Monat, geht das Storno nicht – erst den Monatsabschluss zurücksetzen.</p>
                                                </InfoAbschnitt>
                                            </InfoButton>
                                            {antrag.status === 'OFFEN' && (
                                                <>
                                                    <span aria-hidden="true" className="mx-1 h-6 w-px bg-slate-200" />
                                                    <Button
                                                        onClick={() => handleReject(antrag.id)}
                                                        variant="outline"
                                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                                        size="sm"
                                                        disabled={aktionLaeuftId === antrag.id}
                                                    >
                                                        <X className="w-4 h-4 mr-1" aria-hidden="true" /> Ablehnen
                                                    </Button>
                                                    <Button
                                                        onClick={() => handleApprove(antrag.id)}
                                                        className="bg-rose-600 text-white hover:bg-rose-700"
                                                        size="sm"
                                                        disabled={aktionLaeuftId === antrag.id}
                                                    >
                                                        <Check className="w-4 h-4 mr-1" aria-hidden="true" /> Genehmigen
                                                    </Button>
                                                </>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    </Card>
                ))}
            </div>
        </PageLayout>
    );
}
