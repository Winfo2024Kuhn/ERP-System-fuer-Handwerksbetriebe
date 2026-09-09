import { useEffect, useMemo, useState } from 'react';
import {
    AlertCircle, BookOpen, CheckCircle2, ChevronDown, ChevronRight, FileText,
    Loader2, Lock, Receipt, Save, Trash2, Truck, Undo2, X,
} from 'lucide-react';
import { Button } from '../ui/button';
import { Select } from '../ui/select-custom';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../LieferantSearchModal';
import { KostenstellenSplitsEditor, type KostenstellenSplit } from './KostenstellenSplitsEditor';
import { StornoDialog } from './StornoDialog';
import { VerwerfenDialog } from './VerwerfenDialog';
import { nettoAusBrutto, schluesseleAuf, zuZahl } from '../../lib/mwst';
import type { Beleg, BelegKategorie, Sachkonto, Zahlungsart } from '../../types';
import {
    KATEGORIE_LABELS, buildSachkontoOptions, buildZahlungsartOptions,
    formatDateTime, formatEuro, gesperrtCls, inputCls,
} from './belegFormat';
import { VorschlagsChip } from './VorschlagsChip';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): heutiger
// BelegDetailModal aus BelegeKasseEditor.tsx (Zeilen 1160-2137) samt
// AufteilungsSektion, Field, BelegPreview. Props unveraendert.

// ===================== Detail / Validierungs-Modal =====================

export function BelegDetailModal({ beleg, sachkonten, zahlungsarten, onClose, onSaved, onDeleted }: {
    beleg: Beleg;
    sachkonten: Sachkonto[];
    zahlungsarten: Zahlungsart[];
    onClose: () => void;
    onSaved: (b: Beleg) => void;
    onDeleted: (id: number) => void;
}) {
    // Detail-Beleg nachladen. Zwei Gruende:
    //  (a) Issue #58: Die Listen-Query liefert positionen[] aus Performance-
    //      Gruenden nicht — der Aufteilungs-Bereich braucht sie aber.
    //  (b) Solange die KI noch analysiert, pollen wir alle 4s nach. Der
    //      Auto-Refresh der Liste pausiert naemlich, sobald ein Beleg offen ist
    //      (`if (!hatOffene || editing) return`) — der Hinweis "Werte erscheinen
    //      automatisch" war im offenen Dialog schlicht gelogen.
    //
    // Wichtig: Das Nachladen fuellt NUR `detailBeleg` (Anzeige), niemals `form`.
    // Sonst wuerde eine spaet eintreffende KI-Antwort die Eingaben ueberschreiben,
    // die der Buchhalter waehrenddessen getippt hat.
    const [detailBeleg, setDetailBeleg] = useState<Beleg>(beleg);
    useEffect(() => {
        const kiOffen = (b: Beleg) => b.kiAnalyseStatus === 'PENDING' || b.kiAnalyseStatus === 'LAEUFT';
        if (beleg.aufteilungsModus !== 'TEILWEISE' && !kiOffen(beleg)) return;

        let cancelled = false;
        let timer: ReturnType<typeof setTimeout> | undefined;

        const lade = async () => {
            try {
                const res = await fetch(`/api/buchhaltung/belege/${beleg.id}`);
                if (!res.ok) return;
                const data: Beleg = await res.json();
                if (cancelled) return;
                setDetailBeleg(data);
                if (kiOffen(data)) timer = setTimeout(lade, 4000);
            } catch (e) {
                console.error('Beleg-Detail laden fehlgeschlagen', e);
            }
        };
        lade();

        return () => { cancelled = true; if (timer) clearTimeout(timer); };
    }, [beleg.id, beleg.aufteilungsModus, beleg.kiAnalyseStatus]);

    const [form, setForm] = useState({
        belegKategorie: beleg.belegKategorie,
        belegDatum: beleg.belegDatum ?? '',
        belegNummer: beleg.belegNummer ?? '',
        beschreibung: beleg.beschreibung ?? '',
        betragNetto: beleg.betragNetto ?? '',
        betragBrutto: beleg.betragBrutto ?? '',
        mwstSatz: beleg.mwstSatz ?? '',
        zahlungsart: beleg.zahlungsart ?? '',
        lieferantId: beleg.lieferantId ?? null as number | null,
        lieferantName: beleg.lieferantName ?? '',
        sachkontoId: beleg.sachkontoId ?? null as number | null,
        notiz: beleg.notiz ?? '',
    });
    const [zeigeStorno, setZeigeStorno] = useState(false);
    const [zeigeVerwerfen, setZeigeVerwerfen] = useState(false);
    const [splits, setSplits] = useState<KostenstellenSplit[]>(beleg.kostenstellenSplits ?? []);
    // Wenn das Detail nachgeladen wird (TEILWEISE), beziehen wir die Splits
    // aus dem frischen DTO — die Listen-Query liefert sie ggf. nicht mit.
    useEffect(() => {
        if (detailBeleg.kostenstellenSplits) setSplits(detailBeleg.kostenstellenSplits);
    }, [detailBeleg]);
    const [saving, setSaving] = useState(false);
    const [lieferantPicker, setLieferantPicker] = useState(false);
    // Seltene Felder (Beleg-Nr., Netto, MwSt-Satz, Zahlungsart, Lieferant, Notiz)
    // sind eingeklappt. Sie standen bisher gleichberechtigt neben Betrag und
    // Datum — dadurch sahen alle 12 Felder gleich wichtig aus und der Dialog
    // erschlug den Nutzer.
    //
    // Bewusst IMMER zu, auch wenn Werte drinstehen: die KI fuellt Beleg-Nummer
    // und Zahlungsart bei fast jedem Scan, ein "auf, sobald gefuellt" waere
    // also praktisch immer auf. Damit trotzdem nichts unsichtbar verschwindet,
    // zeigt der zugeklappte Knopf, wie viele Felder belegt sind.
    const [mehrDetails, setMehrDetails] = useState(false);
    const [saldoInfo, setSaldoInfo] = useState<{ saldo: number; mindestbestand: number } | null>(null);
    const [konflikt, setKonflikt] = useState<{ projizierterSaldo: number; mindestbestand: number; message: string } | null>(null);
    // Inline-Validierungs-Hinweis statt blocking alert(), gemäß Toast-Pattern
    // im restlichen Modul (siehe KasseShortcuts.tsx). Nach 4s ausblenden.
    const [validationHint, setValidationHint] = useState<string | null>(null);
    useEffect(() => {
        if (!validationHint) return;
        const t = setTimeout(() => setValidationHint(null), 4000);
        return () => clearTimeout(t);
    }, [validationHint]);

    // Live-Saldo laden, sobald Modal offen — nur fuer Bar-Belege relevant.
    useEffect(() => {
        const barKategorien: BelegKategorie[] = ['KASSE_EINNAHME', 'KASSE_AUSGABE', 'PRIVATENTNAHME', 'PRIVATEINLAGE'];
        if (!barKategorien.includes(form.belegKategorie)) {
            setSaldoInfo(null);
            return;
        }
        fetch('/api/buchhaltung/kasse/saldo')
            .then(r => r.ok ? r.json() : null)
            .then((s) => s && setSaldoInfo(s))
            .catch(err => console.error('Saldo laden fehlgeschlagen', err));
    }, [form.belegKategorie]);

    // Live-Projektion: wie sieht der Saldo nach Validierung dieses Belegs aus?
    const projektion = useMemo(() => {
        if (!saldoInfo) return null;
        const brutto = Number(form.betragBrutto);
        if (!Number.isFinite(brutto) || brutto <= 0) return null;
        const alt = beleg.status === 'VALIDIERT' && beleg.betragBrutto != null
            ? (beleg.belegKategorie === 'KASSE_AUSGABE' || beleg.belegKategorie === 'PRIVATENTNAHME'
                ? -beleg.betragBrutto : beleg.betragBrutto) : 0;
        const neu = form.belegKategorie === 'KASSE_AUSGABE' || form.belegKategorie === 'PRIVATENTNAHME'
            ? -brutto : brutto;
        return saldoInfo.saldo - alt + neu;
    }, [saldoInfo, form.belegKategorie, form.betragBrutto, beleg.status, beleg.belegKategorie, beleg.betragBrutto]);

    const update = <K extends keyof typeof form>(k: K, v: typeof form[K]) =>
        setForm(f => ({ ...f, [k]: v }));

    // Wie viele der eingeklappten Felder sind belegt? Steht als Hinweis am
    // zugeklappten "Mehr Details"-Knopf, damit gefuellte Werte nicht unsichtbar
    // werden (§8 progressive-disclosure darf nichts verstecken, nur ordnen).
    const offeneDetails = [
        form.belegNummer, form.zahlungsart, form.notiz,
        form.betragNetto === '' ? null : form.betragNetto,
        form.mwstSatz === '' ? null : form.mwstSatz,
        form.lieferantId,
    ].filter(v => v != null && v !== '').length;

    // Brutto → Netto/MwSt aufschluesseln. Rein zur Anzeige; geschrieben wird
    // erst beim Klick auf einen MwSt-Knopf. Die Rechnung selbst liegt in
    // src/utils/mwst.ts — dort ist sie einzeln testbar, was bei Geldbetraegen
    // die Stelle ist, an der sich ein Rundungsfehler am teuersten raecht.
    const aufschluesselung = (() => {
        const brutto = zuZahl(form.betragBrutto as string | number);
        const satz = zuZahl(form.mwstSatz as string | number);
        if (brutto === null || satz === null) return null;
        return schluesseleAuf(brutto, satz);
    })();
    const berechnetesNetto = aufschluesselung?.netto ?? null;
    const berechneteMwst = aufschluesselung?.mwst ?? null;

    // Setzt MwSt-Satz und rechnet das Netto passend aus. Ohne gueltiges Brutto
    // wird nur der Satz gesetzt — sonst schrieben wir NaN ins Netto-Feld.
    const setzeMwstSatz = (satz: number) => {
        setForm(f => {
            const brutto = zuZahl(f.betragBrutto as string | number);
            const netto = brutto === null ? null : nettoAusBrutto(brutto, satz);
            if (netto === null) {
                return { ...f, mwstSatz: satz as never };
            }
            return { ...f, mwstSatz: satz as never, betragNetto: netto as never };
        });
    };

    const save = async (alsValidiert: boolean) => {
        // Splits-Vorab-Validierung: Summe Prozent <= 100 + jeder Eintrag hat
        // genau eines von Prozent/Absolut. Verhindert HTTP 400 Round-trip.
        const prozentSumme = splits.reduce((acc, s) => acc + (s.prozent ?? 0), 0);
        if (prozentSumme > 100) {
            setValidationHint(`Summe der Kostenstellen-Prozente ist ${prozentSumme}% — darf nicht über 100% liegen.`);
            return;
        }
        for (const s of splits) {
            if (!s.kostenstelleId) {
                setValidationHint('Jeder Split braucht eine Kostenstelle.');
                return;
            }
            const hatProzent = s.prozent != null;
            const hatAbsolut = s.absoluterBetrag != null;
            if (hatProzent === hatAbsolut) {
                setValidationHint('Pro Split-Eintrag genau EINES von Prozent ODER absolutem Betrag setzen.');
                return;
            }
        }
        setValidationHint(null);

        setSaving(true);
        setKonflikt(null);
        try {
            const body = {
                belegKategorie: form.belegKategorie,
                status: alsValidiert ? 'VALIDIERT' : undefined,
                belegDatum: form.belegDatum || null,
                belegNummer: form.belegNummer || null,
                beschreibung: form.beschreibung || null,
                betragNetto: form.betragNetto === '' ? null : Number(form.betragNetto),
                betragBrutto: form.betragBrutto === '' ? null : Number(form.betragBrutto),
                mwstSatz: form.mwstSatz === '' ? null : Number(form.mwstSatz),
                zahlungsart: form.zahlungsart || null,
                lieferantId: form.lieferantId,
                sachkontoId: form.sachkontoId,
                notiz: form.notiz || null,
                kostenstellenSplits: splits.map(s => ({
                    kostenstelleId: s.kostenstelleId,
                    prozent: s.prozent,
                    absoluterBetrag: s.absoluterBetrag,
                    beschreibung: s.beschreibung || null,
                    streckungJahre: s.streckungJahre,
                    streckungStartJahr: s.streckungStartJahr,
                })),
            };
            const res = await fetch(`/api/buchhaltung/belege/${beleg.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (res.ok) {
                const updated: Beleg = await res.json();
                onSaved(updated);
                return;
            }
            if (res.status === 409) {
                const body409 = await res.json();
                setKonflikt({
                    projizierterSaldo: Number(body409.projizierterSaldo),
                    mindestbestand: Number(body409.mindestbestand),
                    message: body409.message ?? 'Kasse würde unter Mindestbestand fallen',
                });
                return;
            }
            const body400 = await res.json().catch(() => null);
            alert(body400?.message ?? 'Speichern fehlgeschlagen');
        } catch (e) {
            console.error(e);
            alert('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // 1-Klick-Loesung bei 409: vorab eine Privateinlage in der benoetigten
    // Hoehe buchen und dann nochmal speichern.
    const loeseUnterdeckung = async () => {
        if (!konflikt) return;
        const benoetigt = Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo);
        if (benoetigt <= 0) {
            setKonflikt(null);
            return;
        }
        setSaving(true);
        try {
            const heute = new Date().toISOString().slice(0, 10);
            const res = await fetch('/api/buchhaltung/kasse/privateinlage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    betrag: benoetigt,
                    datum: heute,
                    beschreibung: 'Vorab-Einlage für validierten Beleg',
                }),
            });
            if (!res.ok) {
                alert('Vorab-Einlage fehlgeschlagen');
                return;
            }
            setKonflikt(null);
            await save(true);
        } finally {
            setSaving(false);
        }
    };

    // Festgeschrieben = der Monat ist abgeschlossen. Ab hier sperren wir die
    // kassenwirksamen Felder schon in der Oberflaeche, statt den Nutzer erst
    // beim Speichern in eine Fehlermeldung laufen zu lassen. Der Server
    // prueft dasselbe noch einmal — die Sperre hier ist Bequemlichkeit,
    // nicht die Absicherung.
    const istFestgeschrieben = detailBeleg.festgeschrieben === true || beleg.festgeschrieben === true;
    const wurdeStorniert = (detailBeleg.storniertDurchBelegId ?? beleg.storniertDurchBelegId) != null;
    const istGegenbuchung = (detailBeleg.stornoFuerBelegId ?? beleg.stornoFuerBelegId) != null;
    const laufendeNummer = detailBeleg.laufendeNummer ?? beleg.laufendeNummer ?? null;

    // detailBeleg statt beleg: waehrend die KI noch laeuft, pollen wir nach —
    // der Lieferant-Vorschlag soll im offenen Dialog nachtraeglich erscheinen.
    const kiVorschlag = detailBeleg.kiVorgeschlagenerLieferant && !form.lieferantName
        ? detailBeleg.kiVorgeschlagenerLieferant : null;

    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-[98vw] max-h-[95vh] flex flex-col overflow-hidden">
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <Receipt className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 className="font-bold text-slate-900">
                                {istFestgeschrieben ? 'Beleg ansehen' : 'Beleg prüfen & validieren'}
                                {laufendeNummer != null && (
                                    <span className="ml-2 text-sm font-semibold text-slate-500 tabular-nums">Nr. {laufendeNummer}</span>
                                )}
                            </h2>
                            <p className="text-xs text-slate-500">Hochgeladen {formatDateTime(beleg.uploadDatum)} {beleg.uploadedByName ? `von ${beleg.uploadedByName}` : ''}</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full"><X className="w-5 h-5 text-slate-500" /></button>
                </div>

                <div className="flex-1 overflow-hidden grid grid-cols-1 lg:grid-cols-3 gap-0 min-h-0">
                    {/* Vorschau */}
                    <div className="lg:col-span-2 bg-slate-100 flex flex-col items-stretch p-4 border-r border-slate-200 overflow-auto">
                        <BelegPreview belegId={beleg.id} mimeType={beleg.mimeType} originalDateiname={beleg.originalDateiname} />
                    </div>

                    {/* Form */}
                    <div className="lg:col-span-1 overflow-auto p-6 space-y-5">
                        {/* Festschreibungs-Hinweis ganz oben: er erklaert, warum
                            gleich mehrere Felder nicht mehr bedienbar sind. Ohne
                            ihn wirken die gesperrten Felder wie ein Fehler. */}
                        {istFestgeschrieben && (
                            <div className="bg-slate-100 border border-slate-300 rounded-lg p-3 text-sm text-slate-700 flex gap-2">
                                <Lock className="w-4 h-4 shrink-0 mt-0.5 text-slate-500" aria-hidden />
                                <div>
                                    <p className="font-semibold text-slate-900">Dieser Beleg ist fest gebucht.</p>
                                    <p className="mt-0.5">
                                        Datum, Betrag, MwSt, Art der Buchung, Zahlungsart und Verwendungszweck
                                        lassen sich nicht mehr ändern. Sachkonto, Kostenstelle und Notiz schon –
                                        jede Änderung daran wird protokolliert.
                                    </p>
                                    {wurdeStorniert && (
                                        <p className="mt-1 font-medium">
                                            Diese Buchung wurde bereits durch eine Gegenbuchung aufgehoben.
                                            {detailBeleg.stornoGrund ? ` Grund: ${detailBeleg.stornoGrund}` : ''}
                                        </p>
                                    )}
                                    {istGegenbuchung && (
                                        <p className="mt-1 font-medium">
                                            Das ist selbst eine Gegenbuchung – sie hebt eine frühere Buchung auf.
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}
                        {detailBeleg.kiAnalyseStatus === 'FAILED' && (
                            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                                <strong>KI-Analyse fehlgeschlagen:</strong> {detailBeleg.kiFehlerText}
                            </div>
                        )}
                        {detailBeleg.kiAnalyseStatus === 'PENDING' || detailBeleg.kiAnalyseStatus === 'LAEUFT' ? (
                            <div className="bg-sky-50 border border-sky-200 rounded-lg p-3 text-sm text-sky-700 inline-flex items-center gap-2">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                KI-Analyse läuft – der Vorschlag erscheint gleich hier.
                            </div>
                        ) : null}

                        <VorschlagsChip
                            beleg={detailBeleg}
                            sachkonten={sachkonten}
                            aktuellesSachkontoId={form.sachkontoId}
                            onUebernehmen={id => update('sachkontoId', id)}
                        />

                        {/* ---------- Das Wichtigste: was, wie viel, wann, wofür ---------- */}

                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Betrag (€)">
                                <input type="number" step="0.01" value={form.betragBrutto}
                                    onChange={e => update('betragBrutto', e.target.value as never)}
                                    disabled={istFestgeschrieben}
                                    className={`${inputCls} ${gesperrtCls} text-lg font-semibold tabular-nums`} />
                            </Field>
                            <Field label="Beleg-Datum">
                                <input type="date" value={form.belegDatum}
                                    onChange={e => update('belegDatum', e.target.value)}
                                    disabled={istFestgeschrieben}
                                    className={`${inputCls} ${gesperrtCls}`} />
                            </Field>
                        </div>

                        {/* MwSt per Klick statt Kopfrechnen. Der Klick setzt Satz UND Netto —
                            bewusst als ausdrueckliche Nutzer-Aktion, damit sich Betraege auf
                            einem Steuerbeleg nie von selbst aendern. */}
                        <div className="flex flex-wrap items-center gap-2">
                            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">MwSt</span>
                            {[19, 7, 0].map(satz => {
                                const aktiv = Number(form.mwstSatz) === satz && form.mwstSatz !== '';
                                return (
                                    <button key={satz} type="button" onClick={() => setzeMwstSatz(satz)}
                                        aria-pressed={aktiv}
                                        disabled={istFestgeschrieben}
                                        className={`px-3 py-1 rounded-full text-sm border transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
                                            aktiv
                                                ? 'bg-rose-600 text-white border-rose-600'
                                                : 'bg-white text-slate-600 border-slate-200 enabled:hover:border-rose-300 enabled:hover:text-rose-700'
                                        }`}>
                                        {satz} %
                                    </button>
                                );
                            })}
                            {berechneteMwst != null && berechnetesNetto != null && (
                                <span className="text-xs text-slate-500 tabular-nums">
                                    = {formatEuro(berechneteMwst)} € MwSt · {formatEuro(berechnetesNetto)} € netto
                                </span>
                            )}
                        </div>

                        <Field label="Beschreibung">
                            <input type="text" value={form.beschreibung}
                                onChange={e => update('beschreibung', e.target.value)}
                                placeholder="z.B. Tankquittung, Büromaterial…"
                                disabled={istFestgeschrieben}
                                className={`${inputCls} ${gesperrtCls}`} />
                        </Field>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            <div>
                                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Wo gezahlt</label>
                                <Select
                                    value={form.belegKategorie}
                                    onChange={v => update('belegKategorie', v as BelegKategorie)}
                                    disabled={istFestgeschrieben}
                                    options={(Object.entries(KATEGORIE_LABELS) as [BelegKategorie, string][])
                                        .map(([k, label]) => ({ value: k, label }))}
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1 inline-flex items-center gap-1">
                                    <BookOpen className="w-3 h-3" /> Konto / Wofür?
                                </label>
                                <Select
                                    value={form.sachkontoId != null ? String(form.sachkontoId) : ''}
                                    onChange={v => update('sachkontoId', v ? Number(v) : null)}
                                    placeholder="– kein Konto zugewiesen –"
                                    options={buildSachkontoOptions(sachkonten)}
                                />
                            </div>
                        </div>

                        {/* ---------- Alles Seltene eingeklappt (Progressive Disclosure) ---------- */}

                        <div className="border-t border-slate-200 pt-3">
                            <button type="button"
                                onClick={() => setMehrDetails(v => !v)}
                                aria-expanded={mehrDetails}
                                className="inline-flex items-center gap-1.5 text-sm font-medium text-rose-700 hover:text-rose-800">
                                {mehrDetails
                                    ? <ChevronDown className="w-4 h-4" aria-hidden />
                                    : <ChevronRight className="w-4 h-4" aria-hidden />}
                                Mehr Details
                                {!mehrDetails && offeneDetails > 0 && (
                                    <span className="ml-1 text-xs font-normal text-slate-500">
                                        ({offeneDetails} ausgefüllt)
                                    </span>
                                )}
                            </button>

                            {mehrDetails && (
                                <div className="mt-3 space-y-4">
                                    <div className="grid grid-cols-2 gap-3">
                                        <Field label="Beleg-Nummer">
                                            <input type="text" value={form.belegNummer}
                                                onChange={e => update('belegNummer', e.target.value)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                        <Field label="Zahlungsart">
                                            <Select
                                                value={form.zahlungsart}
                                                onChange={v => update('zahlungsart', v)}
                                                placeholder="– bitte wählen –"
                                                disabled={istFestgeschrieben}
                                                options={buildZahlungsartOptions(zahlungsarten, form.zahlungsart)}
                                            />
                                        </Field>
                                        <Field label="Netto (€)">
                                            <input type="number" step="0.01" value={form.betragNetto}
                                                onChange={e => update('betragNetto', e.target.value as never)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                        <Field label="MwSt-Satz (%)">
                                            <input type="number" step="0.1" value={form.mwstSatz}
                                                onChange={e => update('mwstSatz', e.target.value as never)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                    </div>

                                    <Field label="Lieferant (optional)">
                                        <div className="flex items-center gap-2">
                                            <input type="text" readOnly
                                                value={form.lieferantName || (kiVorschlag ? `KI-Vorschlag: ${kiVorschlag}` : '')}
                                                placeholder="Kein Lieferant – z.B. bei Kassen-Einnahme"
                                                className={`${inputCls} bg-slate-50`} />
                                            <Button variant="outline" type="button" onClick={() => setLieferantPicker(true)}>
                                                <Truck className="w-4 h-4 mr-2" />
                                                Wählen
                                            </Button>
                                            {form.lieferantId && (
                                                <Button variant="ghost" type="button"
                                                    onClick={() => { update('lieferantId', null); update('lieferantName', ''); }}>
                                                    <X className="w-4 h-4" />
                                                </Button>
                                            )}
                                        </div>
                                    </Field>

                                    <Field label="Notiz">
                                        <textarea rows={2} value={form.notiz}
                                            onChange={e => update('notiz', e.target.value)}
                                            className={inputCls} />
                                    </Field>
                                </div>
                            )}
                        </div>

                        {detailBeleg.aufteilungsModus === 'TEILWEISE' && (
                            <AufteilungsSektion beleg={detailBeleg} />
                        )}

                        {/* Issue #60: Kostenstellen-Splits — mehrere Kostenstellen pro Beleg */}
                        <KostenstellenSplitsEditor
                            splits={splits}
                            onChange={setSplits}
                            defaultStartJahr={form.belegDatum
                                ? new Date(form.belegDatum).getFullYear()
                                : new Date().getFullYear()}
                        />

                        {/* Live-Saldo-Vorschau + 409-Konflikt-Dialog */}
                        {saldoInfo && (
                            <div className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-2 space-y-0.5">
                                <div>Kassenstand jetzt: <strong>{formatEuro(saldoInfo.saldo)} €</strong></div>
                                {projektion != null && (
                                    <div>
                                        Nach Validierung: <strong className={projektion < saldoInfo.mindestbestand ? 'text-red-700' : 'text-slate-700'}>
                                            {formatEuro(projektion)} €
                                        </strong>
                                        {saldoInfo.mindestbestand > 0 && (
                                            <span className="text-slate-500"> (Mindestbestand: {formatEuro(saldoInfo.mindestbestand)} €)</span>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}
                        {konflikt && (
                            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-900">
                                <div className="flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                    <div className="flex-1">
                                        <p className="font-medium">Kasse würde auf {formatEuro(konflikt.projizierterSaldo)} € rutschen.</p>
                                        <p className="text-xs mt-1">Mindestbestand: {formatEuro(konflikt.mindestbestand)} €</p>
                                        <Button size="sm" className="mt-2 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                            onClick={loeseUnterdeckung} disabled={saving}>
                                            Privateinlage in Höhe {formatEuro(Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo))} € vorab buchen?
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                <div className="border-t border-slate-200 p-4 flex flex-col gap-2 bg-slate-50">
                    {validationHint && (
                        <div className="text-sm px-3 py-2 rounded-lg border bg-amber-50 border-amber-200 text-amber-900">
                            {validationHint}
                        </div>
                    )}
                    <div className="flex items-center justify-between gap-3">
                        {/* Fest gebuchte Belege lassen sich nicht mehr verwerfen —
                            an ihre Stelle tritt die Gegenbuchung. Bereits
                            stornierte Belege bieten gar nichts mehr an. */}
                        {istFestgeschrieben ? (
                            wurdeStorniert || istGegenbuchung ? <span /> : (
                                <Button variant="ghost" onClick={() => setZeigeStorno(true)} disabled={saving}
                                    className="text-red-600 hover:bg-red-50">
                                    <Undo2 className="w-4 h-4 mr-2" /> Stornieren
                                </Button>
                            )
                        ) : (
                            <Button variant="ghost" onClick={() => setZeigeVerwerfen(true)} disabled={saving}
                                className="text-red-600 hover:bg-red-50">
                                <Trash2 className="w-4 h-4 mr-2" /> Verwerfen
                            </Button>
                        )}
                        <div className="flex items-center gap-2">
                            <Button variant="outline" onClick={() => save(false)} disabled={saving}>
                                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                                {istFestgeschrieben ? 'Kontierung speichern' : 'Zwischenspeichern'}
                            </Button>
                            {!istFestgeschrieben && (
                                <Button onClick={() => save(true)} disabled={saving}>
                                    {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <CheckCircle2 className="w-4 h-4 mr-2" />}
                                    Prüfen & Übernehmen
                                </Button>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {lieferantPicker && (
                <LieferantSearchModal
                    isOpen={lieferantPicker}
                    onClose={() => setLieferantPicker(false)}
                    currentLieferantId={form.lieferantId ?? undefined}
                    onSelect={(l: LieferantSuchErgebnis) => {
                        update('lieferantId', l.id);
                        update('lieferantName', l.lieferantenname);
                    }}
                />
            )}

            {zeigeStorno && (
                <StornoDialog
                    belegId={beleg.id}
                    laufendeNummer={laufendeNummer}
                    beschreibung={detailBeleg.beschreibung ?? beleg.beschreibung}
                    betragBrutto={detailBeleg.betragBrutto ?? beleg.betragBrutto}
                    onClose={() => setZeigeStorno(false)}
                    onStorniert={() => {
                        setZeigeStorno(false);
                        // Der Dialog schliesst sich komplett: nach dem Storno ist
                        // die Gegenbuchung die interessante Zeile, nicht mehr das
                        // Original. Der Aufrufer laedt Liste und Kassenbuch neu
                        // und hat damit den echten neuen Stand -- deshalb hier
                        // bewusst kein zusammengebasteltes Beleg-Objekt.
                        onDeleted(beleg.id);
                    }}
                />
            )}

            {zeigeVerwerfen && (
                <VerwerfenDialog
                    belegId={beleg.id}
                    beschreibung={detailBeleg.beschreibung ?? beleg.beschreibung}
                    onClose={() => setZeigeVerwerfen(false)}
                    onVerworfen={() => {
                        setZeigeVerwerfen(false);
                        onDeleted(beleg.id);
                    }}
                />
            )}
        </div>
    );
}

/**
 * Issue #58: Read-only Anzeige der Beleg-Positionen mit Hervorhebung der am
 * Handy markierten Firma-Positionen. Korrektur am PC ist NICHT vorgesehen —
 * die Mobile-Auswahl ist die Quelle der Wahrheit, der Buchhalter sieht hier
 * nur, was der Scanner gewaehlt hat (und kann es ggf. ueber die Mobile-PWA
 * korrigieren).
 */
function AufteilungsSektion({ beleg }: { beleg: Beleg }) {
    const positionen = beleg.positionen ?? [];
    if (positionen.length === 0) {
        return (
            <div className="bg-rose-50/60 border border-rose-100 rounded-lg p-3 text-sm text-slate-600">
                <strong className="text-rose-700">Teil-Beleg.</strong>{' '}
                Positionen werden geladen oder wurden vom Scanner noch nicht erfasst.
            </div>
        );
    }
    const firmaCount = positionen.filter(p => p.istFuerFirma).length;
    return (
        <div className="border border-rose-200 rounded-lg overflow-hidden">
            <div className="bg-rose-50 px-3 py-2 flex items-center gap-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-rose-700">
                    Aufteilung – nur ein Teil ist betrieblich
                </span>
                <span className="text-xs text-slate-600">
                    {firmaCount} von {positionen.length} Positionen für die Firma
                </span>
            </div>
            <table className="w-full text-xs">
                <thead className="bg-slate-50 text-slate-600">
                    <tr>
                        <th className="text-center px-2 py-1.5 font-medium w-8">✓</th>
                        <th className="text-left px-2 py-1.5 font-medium">Beschreibung</th>
                        <th className="text-right px-2 py-1.5 font-medium">Menge</th>
                        <th className="text-right px-2 py-1.5 font-medium">Einzel</th>
                        <th className="text-right px-2 py-1.5 font-medium">Brutto</th>
                        <th className="text-right px-2 py-1.5 font-medium">MwSt</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {positionen.map(p => (
                        <tr key={p.id} className={p.istFuerFirma ? 'bg-rose-50/60' : ''}>
                            <td className="px-2 py-1.5 text-center">
                                {p.istFuerFirma
                                    ? <CheckCircle2 className="w-4 h-4 text-rose-600 inline" />
                                    : <span className="text-slate-300">–</span>}
                            </td>
                            <td className="px-2 py-1.5 text-slate-800">{p.beschreibung || `Pos ${p.sortierung}`}</td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.menge != null
                                    ? `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 }).format(p.menge)}${p.einheit ? ' ' + p.einheit : ''}`
                                    : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.einzelpreis != null ? `${formatEuro(p.einzelpreis)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums font-medium">
                                {p.betragBrutto != null ? `${formatEuro(p.betragBrutto)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-500">
                                {p.mwstSatz != null ? `${p.mwstSatz}%` : '–'}
                            </td>
                        </tr>
                    ))}
                </tbody>
                <tfoot className="bg-rose-50/40 border-t border-rose-200">
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs font-semibold text-rose-700">
                            Summe für Firma
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums font-bold text-rose-700">
                            {formatEuro(beleg.betragFirmaBrutto)} €
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-rose-700">
                            {formatEuro(beleg.betragFirmaMwst)} €
                        </td>
                    </tr>
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs text-slate-500">
                            Netto / MwSt davon
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                            {formatEuro(beleg.betragFirmaNetto)} € netto
                        </td>
                        <td className="px-2 py-1.5"></td>
                    </tr>
                </tfoot>
            </table>
            <div className="px-3 py-2 text-xs text-slate-500 bg-white border-t border-slate-100">
                Auswahl wurde am Handy getroffen. Zum Korrigieren in der Mobile-App neu auswählen.
            </div>
        </div>
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

function BelegPreview({ belegId, mimeType, originalDateiname }: {
    belegId: number; mimeType?: string | null; originalDateiname?: string | null;
}) {
    const src = `/api/buchhaltung/belege/${belegId}/datei`;
    const istPdf = mimeType?.includes('pdf') || originalDateiname?.toLowerCase().endsWith('.pdf');
    const istBild = mimeType?.startsWith('image/');

    if (istPdf) {
        return <iframe src={src} title={originalDateiname ?? 'Beleg-PDF'} className="w-full h-full min-h-[400px] bg-white rounded border border-slate-200" />;
    }
    if (istBild) {
        return <img src={src} alt={originalDateiname ?? 'Beleg'} className="max-w-full max-h-[600px] rounded shadow" />;
    }
    return (
        <div className="text-center text-slate-500 p-8">
            <FileText className="w-12 h-12 mx-auto mb-2 opacity-30" />
            <p className="text-sm">{originalDateiname}</p>
            <a href={src} target="_blank" rel="noopener noreferrer" className="text-rose-600 text-sm underline mt-2 inline-block">
                Datei öffnen
            </a>
        </div>
    );
}
