import { useEffect, useState } from 'react';
import { Stethoscope, Plus, ChevronDown, ChevronUp, Loader2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { DatePicker } from '../components/ui/datepicker';
import { Label } from '../components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '../components/ui/dialog';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useKonfliktMeldung } from '../components/lock/useKonfliktMeldung';
import { PhasenZeitleiste } from '../components/langzeitkrankmeldung/PhasenZeitleiste';
import { StufenplanTabelle } from '../components/langzeitkrankmeldung/StufenplanTabelle';
import { PHASEN_BADGE, formatDatum, type Phase, type PhasenTyp } from '../components/langzeitkrankmeldung/phasen';

type Status = 'LAUFEND' | 'BEENDET' | 'ABGEBROCHEN';

interface StufenplanTag {
    datum: string;
    geplanteStunden: number;
    gestempelteStunden: number;
    ueberPlan: boolean;
}

interface Meldung {
    id: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
    beginn: string;
    ende: string | null;
    status: Status;
    statusLabel: string;
    lohnfortzahlungBis: string;
    notiz: string | null;
    version: number;
    // Befund 4 (Nachbesserung Abschnitt 5): das Backend liefert hier null,
    // wenn heute keine Phase greift (z.B. bei einer abgeschlossenen Meldung
    // ohne aktuelle Phase) -- vorher nicht-nullable typisiert, was
    // PHASEN_BADGE[null] zu einem leeren/kaputten Badge machte.
    aktuellePhaseTyp: PhasenTyp | null;
    aktuellePhaseLabel: string;
    restTageLohnfortzahlung: number | null;
    heuteGeplanteStunden: number | null;
    geplanteRueckkehr: string | null;
    phasen: Phase[];
    stufenplanTage?: StufenplanTag[];
}

interface MitarbeiterOption {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
}

interface ZeitkontoEintrag {
    mitarbeiterId: number;
    montagStunden: number;
    dienstagStunden: number;
    mittwochStunden: number;
    donnerstagStunden: number;
    freitagStunden: number;
}

const STANDARD_MAX_STUNDEN_PRO_TAG = 8;

/** ISO-Datum (YYYY-MM-DD) + Tage, ohne Zeitzonen-Ueberraschungen (feste Uhrzeit 00:00). */
function plusTage(iso: string, tage: number): string {
    const datum = new Date(`${iso}T00:00:00`);
    datum.setDate(datum.getDate() + tage);
    const jahr = datum.getFullYear();
    const monat = String(datum.getMonth() + 1).padStart(2, '0');
    const tag = String(datum.getDate()).padStart(2, '0');
    return `${jahr}-${monat}-${tag}`;
}

function heuteIso(): string {
    const jetzt = new Date();
    const jahr = jetzt.getFullYear();
    const monat = String(jetzt.getMonth() + 1).padStart(2, '0');
    const tag = String(jetzt.getDate()).padStart(2, '0');
    return `${jahr}-${monat}-${tag}`;
}

async function fehlertextAus(res: Response, standard: string): Promise<string> {
    const body = await res.json().catch(() => null) as { error?: string; message?: string } | null;
    return body?.error ?? body?.message ?? standard;
}

export default function Langzeitkrankmeldungen() {
    const toast = useToast();
    const confirm = useConfirm();
    const { pruefeAntwort } = useKonfliktMeldung('Krankmeldung');

    const [statusFilter, setStatusFilter] = useState<Status>('LAUFEND');
    const [meldungen, setMeldungen] = useState<Meldung[]>([]);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'ok' | 'fehler'>('laedt');
    const [expandedId, setExpandedId] = useState<number | null>(null);
    const [detailLaedtId, setDetailLaedtId] = useState<number | null>(null);
    const [aktionLaeuftId, setAktionLaeuftId] = useState<number | null>(null);

    const [mitarbeiterListe, setMitarbeiterListe] = useState<MitarbeiterOption[]>([]);
    const [maxStundenLookup, setMaxStundenLookup] = useState<Record<number, number>>({});

    const [anlegenOffen, setAnlegenOffen] = useState(false);
    const [anlegenSpeichert, setAnlegenSpeichert] = useState(false);
    const [neuMitarbeiterId, setNeuMitarbeiterId] = useState('');
    const [neuBeginn, setNeuBeginn] = useState('');
    const [neuLohnfortzahlungBis, setNeuLohnfortzahlungBis] = useState('');
    const [lohnfortzahlungBisBeruehrt, setLohnfortzahlungBisBeruehrt] = useState(false);
    const [neuNotiz, setNeuNotiz] = useState('');

    const loadMeldungen = async () => {
        setLadeStatus('laedt');
        try {
            const res = await fetch(`/api/langzeitkrankmeldungen?status=${statusFilter}`);
            if (!res.ok) throw new Error();
            const data = await res.json();
            setMeldungen(Array.isArray(data) ? data : []);
            setExpandedId(null);
            setLadeStatus('ok');
        } catch {
            setLadeStatus('fehler');
            toast.error('Krankmeldungen konnten nicht geladen werden.');
        }
    };

    useEffect(() => {
        loadMeldungen();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [statusFilter]);

    // Stammdaten fuer den Anlegen-Dialog und das Tageslimit im Stufenplan --
    // zwei unabhaengige Fetches, kein Wasserfall (kriterien.md, Performance).
    useEffect(() => {
        fetch('/api/mitarbeiter')
            .then((res) => (res.ok ? res.json() : []))
            .then((data) => setMitarbeiterListe(Array.isArray(data) ? data : []))
            .catch(() => setMitarbeiterListe([]));

        fetch('/api/zeitverwaltung/zeitkonten')
            .then((res) => (res.ok ? res.json() : []))
            .then((data: ZeitkontoEintrag[]) => {
                const lookup: Record<number, number> = {};
                (Array.isArray(data) ? data : []).forEach((k) => {
                    lookup[k.mitarbeiterId] = Math.max(
                        k.montagStunden ?? 0, k.dienstagStunden ?? 0, k.mittwochStunden ?? 0,
                        k.donnerstagStunden ?? 0, k.freitagStunden ?? 0,
                    );
                });
                setMaxStundenLookup(lookup);
            })
            .catch(() => setMaxStundenLookup({}));
    }, []);

    const ladeDetail = async (id: number) => {
        const res = await fetch(`/api/langzeitkrankmeldungen/${id}`);
        if (!res.ok) return;
        const detail = await res.json();
        setMeldungen((prev) => prev.map((m) => (m.id === id ? detail : m)));
    };

    const toggleDetail = async (meldung: Meldung) => {
        if (expandedId === meldung.id) {
            setExpandedId(null);
            return;
        }
        setExpandedId(meldung.id);
        if (meldung.stufenplanTage) return;
        setDetailLaedtId(meldung.id);
        try {
            await ladeDetail(meldung.id);
        } catch {
            toast.error('Details konnten nicht geladen werden.');
            setExpandedId(null);
        } finally {
            setDetailLaedtId(null);
        }
    };

    const handleUmstellenAufKrankengeld = async (meldung: Meldung) => {
        setAktionLaeuftId(meldung.id);
        try {
            const res = await fetch(`/api/langzeitkrankmeldungen/${meldung.id}/phasen?version=${meldung.version}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ typ: 'KRANKENGELD', vonDatum: heuteIso(), bisDatum: null, stundenProTag: null }),
            });
            if (await pruefeAntwort(res)) return;
            if (!res.ok) throw new Error(await fehlertextAus(res, 'Die Phase konnte nicht angelegt werden.'));
            toast.success('Auf Krankengeld umgestellt.');
            await ladeDetail(meldung.id);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Phase konnte nicht angelegt werden.');
        } finally {
            setAktionLaeuftId(null);
        }
    };

    const handleBeenden = async (meldung: Meldung) => {
        const heute = heuteIso();
        const bestaetigt = await confirm({
            title: 'Wieder voll im Einsatz',
            message: `${meldung.mitarbeiterName} ab ${formatDatum(heute)} als "Wieder voll im Einsatz" markieren? Die Krankmeldung wird damit abgeschlossen.`,
            confirmLabel: 'Bestätigen',
            cancelLabel: 'Abbrechen',
            variant: 'warning',
        });
        if (!bestaetigt) return;

        setAktionLaeuftId(meldung.id);
        try {
            const res = await fetch(`/api/langzeitkrankmeldungen/${meldung.id}/beenden?ende=${heute}&version=${meldung.version}`, { method: 'PUT' });
            if (await pruefeAntwort(res)) return;
            if (!res.ok) throw new Error(await fehlertextAus(res, 'Konnte nicht abgeschlossen werden.'));
            toast.success('Krankmeldung abgeschlossen.');
            await loadMeldungen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Konnte nicht abgeschlossen werden.');
        } finally {
            setAktionLaeuftId(null);
        }
    };

    const handleAbbrechen = async (meldung: Meldung) => {
        const bestaetigt = await confirm({
            title: 'Krankmeldung zurücknehmen',
            message: `Krankmeldung von ${meldung.mitarbeiterName} wirklich zurücknehmen? Das kann nicht rückgängig gemacht werden.`,
            confirmLabel: 'Ja, zurücknehmen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!bestaetigt) return;

        setAktionLaeuftId(meldung.id);
        try {
            const res = await fetch(`/api/langzeitkrankmeldungen/${meldung.id}/abbrechen?version=${meldung.version}`, { method: 'PUT' });
            if (await pruefeAntwort(res)) return;
            if (!res.ok) throw new Error(await fehlertextAus(res, 'Konnte nicht zurückgenommen werden.'));
            toast.success('Krankmeldung zurückgenommen.');
            await loadMeldungen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Konnte nicht zurückgenommen werden.');
        } finally {
            setAktionLaeuftId(null);
        }
    };

    const handlePhaseHinzufuegen = async (
        meldung: Meldung,
        p: { vonDatum: string; bisDatum: string | null; stundenProTag: number },
    ) => {
        const res = await fetch(`/api/langzeitkrankmeldungen/${meldung.id}/phasen?version=${meldung.version}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ typ: 'WIEDEREINGLIEDERUNG', vonDatum: p.vonDatum, bisDatum: p.bisDatum, stundenProTag: p.stundenProTag }),
        });
        if (await pruefeAntwort(res)) return;
        if (!res.ok) throw new Error(await fehlertextAus(res, 'Die Zeile konnte nicht gespeichert werden.'));
        await ladeDetail(meldung.id);
    };

    const handlePhaseLoeschen = async (meldung: Meldung, phasenId: number) => {
        const res = await fetch(`/api/langzeitkrankmeldungen/${meldung.id}/phasen/${phasenId}?version=${meldung.version}`, {
            method: 'DELETE',
        });
        if (await pruefeAntwort(res)) return;
        if (!res.ok) throw new Error(await fehlertextAus(res, 'Der Eintrag konnte nicht gelöscht werden.'));
        await ladeDetail(meldung.id);
    };

    const resetAnlegenForm = () => {
        setNeuMitarbeiterId('');
        setNeuBeginn('');
        setNeuLohnfortzahlungBis('');
        setLohnfortzahlungBisBeruehrt(false);
        setNeuNotiz('');
    };

    const handleBeginnAendern = (wert: string) => {
        setNeuBeginn(wert);
        if (!lohnfortzahlungBisBeruehrt) {
            setNeuLohnfortzahlungBis(wert ? plusTage(wert, 41) : '');
        }
    };

    const handleAnlegen = async () => {
        if (!neuMitarbeiterId || !neuBeginn) {
            toast.error('Bitte Mitarbeiter und Beginn angeben.');
            return;
        }
        setAnlegenSpeichert(true);
        try {
            const res = await fetch('/api/langzeitkrankmeldungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mitarbeiterId: Number(neuMitarbeiterId),
                    beginn: neuBeginn,
                    lohnfortzahlungBis: neuLohnfortzahlungBis || null,
                    notiz: neuNotiz || null,
                }),
            });
            if (!res.ok) throw new Error(await fehlertextAus(res, 'Die Krankmeldung konnte nicht angelegt werden.'));
            toast.success('Krankmeldung angelegt.');
            setAnlegenOffen(false);
            resetAnlegenForm();
            await loadMeldungen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Krankmeldung konnte nicht angelegt werden.');
        } finally {
            setAnlegenSpeichert(false);
        }
    };

    const mitarbeiterOptionen = mitarbeiterListe
        .filter((m) => m.aktiv !== false)
        .map((m) => ({ value: String(m.id), label: `${m.nachname}, ${m.vorname}` }));

    return (
        <PageLayout>
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Abwesenheiten</p>
                    <h1 className="text-3xl font-bold text-slate-900 uppercase">Lange Krankheit</h1>
                    <p className="text-slate-500 mt-1">Lohnfortzahlung, Krankengeld und Wiedereingliederung im Blick</p>
                </div>
                <div className="flex flex-col sm:flex-row gap-2 sm:items-center">
                    <div className="w-full sm:w-56">
                        <Select
                            value={statusFilter}
                            onChange={(val) => setStatusFilter(val as Status)}
                            options={[
                                { value: 'LAUFEND', label: 'Läuft noch' },
                                { value: 'BEENDET', label: 'Abgeschlossen' },
                                { value: 'ABGEBROCHEN', label: 'Zurückgenommen' },
                            ]}
                        />
                    </div>
                    <Button
                        onClick={() => setAnlegenOffen(true)}
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        size="sm"
                    >
                        <Plus className="w-4 h-4" aria-hidden="true" /> Krankmeldung anlegen
                    </Button>
                </div>
            </div>

            {ladeStatus === 'laedt' && (
                <div className="flex flex-col gap-4" aria-busy="true" aria-label="Krankmeldungen werden geladen">
                    {[0, 1, 2].map((i) => (
                        <div key={i} className="motion-safe:animate-pulse bg-white border border-slate-200 rounded-lg shadow-sm h-28" />
                    ))}
                </div>
            )}

            {ladeStatus === 'fehler' && (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <p>Die Krankmeldungen konnten nicht geladen werden.</p>
                    <Button variant="outline" size="sm" className="mt-3" onClick={loadMeldungen}>
                        Erneut versuchen
                    </Button>
                </div>
            )}

            {ladeStatus === 'ok' && meldungen.length === 0 && (
                <div className="text-center py-12 text-slate-500 bg-slate-50 rounded-lg">
                    <p>Aktuell ist niemand langzeitkrank gemeldet.</p>
                </div>
            )}

            {ladeStatus === 'ok' && meldungen.length > 0 && (
                <div className="flex flex-col gap-4">
                    {meldungen.map((m) => {
                        const istOffen = expandedId === m.id;
                        const restTage = m.restTageLohnfortzahlung ?? 0;
                        const laeuftGerade = m.status === 'LAUFEND';

                        return (
                            <Card key={m.id} data-meldung-id={m.id} className="p-4 border-slate-200">
                                <div className="flex flex-col md:flex-row justify-between gap-4">
                                    <div className="flex items-start gap-4 min-w-0 flex-1">
                                        <div
                                            className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 shrink-0"
                                            aria-hidden="true"
                                        >
                                            <Stethoscope className="w-5 h-5" />
                                        </div>
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2 mb-1 flex-wrap">
                                                <span
                                                    className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${
                                                        m.aktuellePhaseTyp ? PHASEN_BADGE[m.aktuellePhaseTyp] : 'bg-slate-100 text-slate-700'
                                                    }`}
                                                >
                                                    {m.aktuellePhaseLabel}
                                                </span>
                                                {!laeuftGerade && (
                                                    <span className="inline-flex items-center rounded px-2 py-0.5 text-xs font-medium bg-slate-100 text-slate-700">
                                                        {m.statusLabel}
                                                    </span>
                                                )}
                                            </div>
                                            <h3 className="text-lg font-bold text-slate-900 min-w-0 truncate">{m.mitarbeiterName}</h3>
                                            <p className="text-sm text-slate-500 mt-1 min-w-0">krank seit {formatDatum(m.beginn)}</p>
                                            <p className="text-sm text-slate-500 min-w-0">
                                                {m.geplanteRueckkehr
                                                    ? `geplante Rückkehr: ${formatDatum(m.geplanteRueckkehr)}`
                                                    : 'geplante Rückkehr: noch offen'}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="flex flex-col items-start md:items-end gap-2 shrink-0">
                                        {laeuftGerade && (
                                            restTage > 0 ? (
                                                <p className="text-sm font-medium text-slate-700 tabular-nums">
                                                    Noch {restTage.toLocaleString('de-DE')} Tage Lohnfortzahlung
                                                </p>
                                            ) : (
                                                <div className="flex flex-col items-start md:items-end gap-1.5">
                                                    <p className="text-sm font-medium text-rose-700">
                                                        Lohnfortzahlung endete am {formatDatum(m.lohnfortzahlungBis)}
                                                    </p>
                                                    {m.aktuellePhaseTyp === 'LOHNFORTZAHLUNG' && (
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                                            onClick={() => handleUmstellenAufKrankengeld(m)}
                                                            disabled={aktionLaeuftId === m.id}
                                                        >
                                                            {aktionLaeuftId === m.id && (
                                                                <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                                            )}
                                                            Auf Krankengeld umstellen
                                                        </Button>
                                                    )}
                                                </div>
                                            )
                                        )}
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => toggleDetail(m)}
                                            aria-expanded={istOffen}
                                            aria-label={istOffen ? 'Details schließen' : 'Details anzeigen'}
                                        >
                                            {istOffen ? <ChevronUp className="w-4 h-4" aria-hidden="true" /> : <ChevronDown className="w-4 h-4" aria-hidden="true" />}
                                            Details
                                        </Button>
                                    </div>
                                </div>

                                {istOffen && (
                                    <div className="mt-4 pt-4 border-t border-slate-100">
                                        {detailLaedtId === m.id ? (
                                            <div className="motion-safe:animate-pulse bg-slate-100 rounded-lg h-24" aria-busy="true" aria-label="Details werden geladen" />
                                        ) : (
                                            <div className="flex flex-col md:flex-row gap-6">
                                                <div className="min-w-0 flex-1">
                                                    <h4 className="text-sm font-semibold text-slate-700 mb-2">Verlauf</h4>
                                                    <PhasenZeitleiste phasen={m.phasen} />
                                                </div>
                                                <div className="min-w-0 flex-1">
                                                    <h4 className="text-sm font-semibold text-slate-700 mb-2">Stufenplan</h4>
                                                    <StufenplanTabelle
                                                        phasen={m.phasen}
                                                        onHinzufuegen={(p) => handlePhaseHinzufuegen(m, p)}
                                                        onLoeschen={(phasenId) => handlePhaseLoeschen(m, phasenId)}
                                                        maxStundenProTag={maxStundenLookup[m.mitarbeiterId] ?? STANDARD_MAX_STUNDEN_PRO_TAG}
                                                        disabled={!laeuftGerade}
                                                    />

                                                    {m.stufenplanTage && m.stufenplanTage.length > 0 && (
                                                        <div className="mt-3">
                                                            <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-400 mb-1">
                                                                Gestempelt gegen geplant
                                                            </h5>
                                                            <ul className="flex flex-col gap-1 text-sm text-slate-600">
                                                                {m.stufenplanTage.map((tag) => (
                                                                    <li key={tag.datum} className="flex justify-between gap-2 min-w-0">
                                                                        <span className="min-w-0">{formatDatum(tag.datum)}</span>
                                                                        <span className={`min-w-0 ${tag.ueberPlan ? 'text-amber-700' : ''}`}>
                                                                            {tag.gestempelteStunden.toLocaleString('de-DE')} h gestempelt, {tag.geplanteStunden.toLocaleString('de-DE')} h geplant
                                                                        </span>
                                                                    </li>
                                                                ))}
                                                            </ul>
                                                        </div>
                                                    )}

                                                    {m.notiz && (
                                                        <div className="mt-3 p-2 bg-slate-50 rounded text-sm text-slate-600">
                                                            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1">
                                                                Interne Notiz
                                                            </p>
                                                            {m.notiz}
                                                        </div>
                                                    )}

                                                    {laeuftGerade && (
                                                        <div className="mt-4 flex gap-2 flex-wrap">
                                                            <Button
                                                                variant="outline"
                                                                size="sm"
                                                                onClick={() => handleAbbrechen(m)}
                                                                disabled={aktionLaeuftId === m.id}
                                                            >
                                                                Zurücknehmen
                                                            </Button>
                                                            <Button
                                                                size="sm"
                                                                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                                                onClick={() => handleBeenden(m)}
                                                                disabled={aktionLaeuftId === m.id}
                                                            >
                                                                {aktionLaeuftId === m.id && (
                                                                    <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                                                )}
                                                                Wieder voll im Einsatz
                                                            </Button>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </Card>
                        );
                    })}
                </div>
            )}

            <Dialog
                open={anlegenOffen}
                onOpenChange={(open) => {
                    setAnlegenOffen(open);
                    if (!open) resetAnlegenForm();
                }}
            >
                <DialogContent className="sm:max-w-[480px]">
                    <DialogHeader>
                        <DialogTitle>Krankmeldung anlegen</DialogTitle>
                        <DialogDescription>Erfasst den Beginn der Lohnfortzahlung — die weiteren Phasen kommen später dazu.</DialogDescription>
                    </DialogHeader>

                    <div className="flex flex-col gap-4 py-2">
                        <div>
                            <Label htmlFor="neu-mitarbeiter">Mitarbeiter</Label>
                            <Select
                                value={neuMitarbeiterId}
                                onChange={setNeuMitarbeiterId}
                                options={mitarbeiterOptionen}
                                placeholder="Bitte wählen..."
                            />
                        </div>
                        <div>
                            <Label id="neu-beginn-label">Beginn</Label>
                            <div role="group" aria-labelledby="neu-beginn-label">
                                <DatePicker value={neuBeginn} onChange={handleBeginnAendern} placeholder="Beginn wählen" />
                            </div>
                        </div>
                        <div>
                            <Label id="neu-lfz-label">Lohnfortzahlung bis</Label>
                            <div role="group" aria-labelledby="neu-lfz-label">
                                <DatePicker
                                    value={neuLohnfortzahlungBis}
                                    onChange={(wert) => {
                                        setLohnfortzahlungBisBeruehrt(true);
                                        setNeuLohnfortzahlungBis(wert);
                                    }}
                                    placeholder="wird automatisch vorgeschlagen"
                                />
                            </div>
                            <p className="text-xs text-slate-500 mt-1">
                                42 Tage ab Beginn — bei einer Fortsetzungserkrankung früher setzen
                            </p>
                        </div>
                        <div>
                            <Label htmlFor="neu-notiz">Interne Notiz — bitte keine Diagnosen eintragen</Label>
                            <textarea
                                id="neu-notiz"
                                className="w-full min-h-[80px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                placeholder="z.B. Rückmeldetermin, Ansprechpartner..."
                                value={neuNotiz}
                                onChange={(e) => setNeuNotiz(e.target.value)}
                            />
                        </div>
                    </div>

                    <DialogFooter>
                        <Button variant="outline" size="sm" onClick={() => setAnlegenOffen(false)} disabled={anlegenSpeichert}>
                            Abbrechen
                        </Button>
                        <Button
                            size="sm"
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            onClick={handleAnlegen}
                            disabled={anlegenSpeichert}
                        >
                            {anlegenSpeichert && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                            {anlegenSpeichert ? 'Speichert...' : 'Speichern'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </PageLayout>
    );
}
