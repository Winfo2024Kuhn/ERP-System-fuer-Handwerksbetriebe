import { useEffect, useId, useState } from 'react';
import { HandCoins, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { InfoButton } from './ui/info-button';
import { useToast } from './ui/toast';
import { cn } from '../lib/utils';
import { formatEur, parseDecimal } from './verrechnungslohnFormat';
import { GUTSCHRIFT_GRUND_MAX, baueTeilGutschriftPositionen, heuteIso } from './gutschriftPositionen';
import type { AusgangsGeschaeftsDokument, GutschriftRahmen } from '../types';

type Umfang = 'GANZ' | 'TEIL';

interface GutschriftErstellenDialogProps {
    /** Die gebuchte Rechnung, zu der die Gutschrift entsteht. `null` = Dialog zu. */
    rechnung: AusgangsGeschaeftsDokument | null;
    onClose: () => void;
    /** Nach dem Anlegen – z. B. um die Dokumentliste neu zu laden. */
    onErstellt?: (gutschrift: AusgangsGeschaeftsDokument) => void;
    /** Ersatz, falls die Rechnung ihr Projekt / ihre Anfrage nicht mitliefert. */
    projektId?: number;
    anfrageId?: number;
}

/**
 * Fehlermeldung des Servers: Die Gutschrift-Regeln antworten mit JSON
 * `{ status, message }`, ältere Fehler desselben Controllers als reiner Text.
 * Beides lesen; HTML-Fehlerseiten oder leere Antworten ergeben den Ersatztext.
 */
async function leseMeldung(res: Response, ersatz: string): Promise<string> {
    let text = '';
    try {
        text = (await res.text()).trim();
    } catch {
        return ersatz;
    }
    if (!text) return ersatz;
    try {
        const daten: unknown = JSON.parse(text);
        if (daten && typeof daten === 'object') {
            const { message } = daten as { message?: unknown };
            return typeof message === 'string' && message.trim() ? message : ersatz;
        }
    } catch {
        // kein JSON – reiner Text
    }
    return text.startsWith('<') || text.length > 300 ? ersatz : text;
}

/** Cent-genau vergleichen, damit 119,00 nicht an 118,99999 scheitert. */
const inCent = (betrag: number) => Math.round(betrag * 100);

/** MwSt-Satz kommt als Anteil (0,19); zur Sicherheit auch Prozent (19) annehmen. */
const alsAnteil = (satz: number) => (satz > 1 ? satz / 100 : satz);

/**
 * Legt zu einer gebuchten Rechnung eine Gutschrift an – entweder über die
 * ganze Rechnung (der Server übernimmt die Positionen) oder über einen
 * Teilbetrag mit Begründung (eine einzige Position). Danach öffnet sich die
 * Gutschrift im Dokument-Editor, wo man sie prüfen und buchen kann.
 */
export function GutschriftErstellenDialog({ rechnung, onClose, onErstellt, projektId, anfrageId }: GutschriftErstellenDialogProps) {
    const toast = useToast();
    const titelId = useId();
    const betragId = useId();
    const grundId = useId();
    const [rahmen, setRahmen] = useState<GutschriftRahmen | null>(null);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [umfang, setUmfang] = useState<Umfang>('GANZ');
    const [betrag, setBetrag] = useState('');
    const [grund, setGrund] = useState('');
    const [fehler, setFehler] = useState<{ betrag?: string; grund?: string }>({});
    const [speichert, setSpeichert] = useState(false);

    const rechnungId = rechnung?.id;

    useEffect(() => {
        if (rechnungId == null) return;
        let abgebrochen = false;
        setRahmen(null);
        setLadeFehler(null);
        setUmfang('GANZ');
        setBetrag('');
        setGrund('');
        setFehler({});
        (async () => {
            try {
                const res = await fetch(`/api/ausgangs-dokumente/${encodeURIComponent(rechnungId)}/gutschrift-rahmen`);
                if (!res.ok) throw new Error(await leseMeldung(res, 'Die Rechnungsdaten für die Gutschrift konnten nicht geladen werden.'));
                const daten: GutschriftRahmen = await res.json();
                if (abgebrochen) return;
                setRahmen(daten);
                // Ist schon etwas gutgeschrieben, geht nur noch ein Teilbetrag.
                if (daten.bereitsGutgeschriebenNetto > 0) setUmfang('TEIL');
            } catch (err) {
                if (abgebrochen) return;
                const meldung = err instanceof Error ? err.message : 'Die Rechnungsdaten für die Gutschrift konnten nicht geladen werden.';
                setLadeFehler(meldung);
                toast.error(meldung);
            }
        })();
        return () => { abgebrochen = true; };
        // toast ist stabil genug; nur bei einer anderen Rechnung neu laden.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rechnungId]);

    if (!rechnung) return null;

    const nichtsMehrMoeglich = rahmen !== null && inCent(rahmen.hoechstensNetto) <= 0;
    const ganzGesperrt = rahmen !== null && rahmen.bereitsGutgeschriebenNetto > 0;
    const betragWert = parseDecimal(betrag);
    const bruttoVorschau = rahmen && betragWert !== null && betragWert > 0
        ? betragWert * (1 + alsAnteil(rahmen.mwstSatz))
        : null;

    // Projekt bzw. Anfrage wie bei der Rechnung, damit die Gutschrift dort erscheint.
    const projekt = rechnung.projektId ?? projektId;
    const anfrage = projekt == null ? (rechnung.anfrageId ?? anfrageId) : undefined;

    const pruefen = (): { betrag: number; grund: string } | null => {
        if (!rahmen) return null;
        const neu: { betrag?: string; grund?: string } = {};
        const wert = parseDecimal(betrag);
        if (wert === null) {
            neu.betrag = 'Bitte den Betrag eingeben, z. B. 100,00.';
        } else if (wert <= 0) {
            neu.betrag = 'Der Betrag muss größer als 0 sein.';
        } else if (Math.abs(wert * 100 - inCent(wert)) > 1e-6) {
            neu.betrag = 'Bitte höchstens zwei Nachkommastellen eingeben.';
        } else if (inCent(wert) > inCent(rahmen.hoechstensNetto)) {
            neu.betrag = `Höchstens ${formatEur(rahmen.hoechstensNetto)} netto sind noch möglich.`;
        }
        const grundText = grund.trim();
        if (!grundText) neu.grund = 'Bitte einen Grund eingeben, z. B. „Preisnachlass wegen Kratzer am Geländer“.';
        else if (grundText.length > GUTSCHRIFT_GRUND_MAX) neu.grund = `Der Grund darf höchstens ${GUTSCHRIFT_GRUND_MAX} Zeichen lang sein.`;
        setFehler(neu);
        if (neu.betrag || neu.grund || wert === null) {
            toast.error(neu.betrag ?? neu.grund ?? 'Bitte die Eingaben prüfen.');
            return null;
        }
        return { betrag: wert, grund: grundText };
    };

    const erstellen = async () => {
        if (!rahmen || speichert) return;
        let positionenJson: string | undefined;
        if (umfang === 'TEIL') {
            const geprueft = pruefen();
            if (!geprueft) return;
            positionenJson = baueTeilGutschriftPositionen(rahmen.rechnungNummer, geprueft.grund, geprueft.betrag);
        }
        setSpeichert(true);
        try {
            const res = await fetch('/api/ausgangs-dokumente', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    typ: 'GUTSCHRIFT',
                    vorgaengerId: rechnung.id,
                    datum: heuteIso(),
                    ...(projekt != null ? { projektId: projekt } : {}),
                    ...(anfrage != null ? { anfrageId: anfrage } : {}),
                    ...(positionenJson ? { positionenJson } : {}),
                }),
            });
            if (!res.ok) throw new Error(await leseMeldung(res, 'Die Gutschrift konnte nicht angelegt werden.'));
            const gutschrift: AusgangsGeschaeftsDokument = await res.json();
            toast.success(`${gutschrift.dokumentNummer ? `Gutschrift ${gutschrift.dokumentNummer}` : 'Gutschrift'} angelegt – sie öffnet sich im Editor.`);
            const kontext = projekt != null
                ? `projektId=${encodeURIComponent(projekt)}&`
                : anfrage != null ? `anfrageId=${encodeURIComponent(anfrage)}&` : '';
            window.open(`/dokument-editor?${kontext}dokumentId=${encodeURIComponent(gutschrift.id)}`, '_blank');
            onErstellt?.(gutschrift);
            onClose();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Gutschrift konnte nicht angelegt werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const optionKlasse = (aktiv: boolean, gesperrt = false) => cn(
        'flex items-start gap-3 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
        gesperrt
            ? 'cursor-not-allowed border-slate-200 bg-slate-50 opacity-70'
            : aktiv ? 'cursor-pointer border-rose-300 bg-rose-50/60' : 'cursor-pointer border-slate-200 bg-white hover:bg-slate-50',
    );

    return (
        <Dialog
            open
            onOpenChange={offen => { if (!offen && !speichert) onClose(); }}
            className="w-full max-w-lg"
            aria-labelledby={titelId}
        >
            <DialogContent>
                <DialogHeader>
                    <div className="flex items-center gap-1">
                        <DialogTitle id={titelId}>Gutschrift erstellen</DialogTitle>
                        <InfoButton titel="Gutschrift">
                            <p className="font-semibold text-slate-900">Was passiert im Hintergrund?</p>
                            <p>
                                Die Gutschrift wird als eigenes Dokument mit Nummer GU-… angelegt und mit der Rechnung
                                verknüpft. Die Beträge werden als Minus gespeichert. Sobald du sie buchst, ist sie wie
                                eine Rechnung gesperrt und lässt sich nur noch stornieren.
                            </p>
                            <p className="mt-2 font-semibold text-slate-900">Was bewirkt das?</p>
                            <p>
                                Der offene Betrag der Rechnung sinkt um die Gutschrift; Mahnungen fordern nur noch den
                                Rest. Eine Gutschrift über die ganze Rechnung erledigt den Offenen Posten. Die
                                E-Rechnung wird als Gutschrift mit Bezug auf die Rechnung erzeugt. Der Auftragswert des
                                Projekts ändert sich nicht.
                            </p>
                        </InfoButton>
                    </div>
                    <DialogDescription>
                        Zu Rechnung {rahmen?.rechnungNummer ?? rechnung.dokumentNummer}. Danach öffnet sich die
                        Gutschrift im Editor – dort kannst du sie noch anpassen, bevor du sie buchst.
                    </DialogDescription>
                </DialogHeader>

                {ladeFehler ? (
                    <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                        {ladeFehler}
                    </p>
                ) : !rahmen ? (
                    <div className="space-y-2" aria-busy="true" aria-label="Rechnungsdaten werden geladen">
                        <div className="h-20 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                        <div className="h-28 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                    </div>
                ) : (
                    <>
                        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
                            <dt className="text-slate-500">Rechnungsbetrag</dt>
                            <dd className="text-right font-medium tabular-nums text-slate-900">
                                {formatEur(rahmen.rechnungNetto)} netto
                                <span className="block text-xs font-normal text-slate-500">{formatEur(rahmen.rechnungBrutto)} brutto</span>
                            </dd>
                            <dt className="text-slate-500">Bereits gutgeschrieben</dt>
                            <dd className="text-right tabular-nums text-slate-700">
                                {formatEur(rahmen.bereitsGutgeschriebenNetto)} netto
                            </dd>
                            <dt className="font-medium text-slate-700">Höchstens noch möglich</dt>
                            <dd className="text-right font-semibold tabular-nums text-slate-900" data-testid="gutschrift-hoechstens">
                                {formatEur(rahmen.hoechstensNetto)} netto
                                <span className="block text-xs font-normal text-slate-500">{formatEur(rahmen.hoechstensBrutto)} brutto</span>
                            </dd>
                        </dl>

                        {nichtsMehrMoeglich ? (
                            <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                                Diese Rechnung ist schon vollständig gutgeschrieben. Eine weitere Gutschrift ist nicht möglich.
                            </p>
                        ) : (
                            <fieldset className="space-y-2">
                                <legend className="mb-1 text-sm font-medium text-slate-700">Was soll gutgeschrieben werden?</legend>
                                <label
                                    className={optionKlasse(umfang === 'GANZ', ganzGesperrt)}
                                    title={ganzGesperrt ? 'Zu dieser Rechnung gibt es schon eine Gutschrift – jetzt geht nur noch ein Teilbetrag.' : undefined}
                                >
                                    <input
                                        type="radio"
                                        name={`${titelId}-umfang`}
                                        checked={umfang === 'GANZ'}
                                        disabled={ganzGesperrt}
                                        onChange={() => setUmfang('GANZ')}
                                        className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none"
                                    />
                                    <span>
                                        <span className="block text-sm font-medium text-slate-900">Ganze Rechnung gutschreiben</span>
                                        <span className="block text-xs text-slate-500">
                                            {ganzGesperrt
                                                ? 'Geht nicht mehr – zu dieser Rechnung gibt es schon eine Gutschrift.'
                                                : 'Alle Positionen der Rechnung werden übernommen. Im Editor kannst du noch Positionen löschen oder ändern.'}
                                        </span>
                                    </span>
                                </label>
                                <label className={optionKlasse(umfang === 'TEIL')}>
                                    <input
                                        type="radio"
                                        name={`${titelId}-umfang`}
                                        checked={umfang === 'TEIL'}
                                        onChange={() => setUmfang('TEIL')}
                                        className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none"
                                    />
                                    <span>
                                        <span className="block text-sm font-medium text-slate-900">
                                            Teilbetrag gutschreiben (z. B. Preisnachlass nach Reklamation)
                                        </span>
                                        <span className="block text-xs text-slate-500">
                                            Eine Position mit Betrag und Grund.
                                        </span>
                                    </span>
                                </label>
                            </fieldset>
                        )}

                        {!nichtsMehrMoeglich && umfang === 'TEIL' && (
                            <div className="space-y-3">
                                <div className="space-y-1.5">
                                    <Label htmlFor={betragId}>Betrag netto in Euro *</Label>
                                    <Input
                                        id={betragId}
                                        inputMode="decimal"
                                        value={betrag}
                                        placeholder="z. B. 100,00"
                                        aria-invalid={fehler.betrag ? true : undefined}
                                        aria-describedby={`${betragId}-hinweis`}
                                        onChange={e => { setBetrag(e.target.value); setFehler(f => ({ ...f, betrag: undefined })); }}
                                    />
                                    <p id={`${betragId}-hinweis`} className={cn('text-xs', fehler.betrag ? 'text-red-600' : 'text-slate-500')}>
                                        {fehler.betrag
                                            ?? (bruttoVorschau !== null
                                                ? `Das sind ${formatEur(bruttoVorschau)} brutto. Höchstens ${formatEur(rahmen.hoechstensNetto)} netto.`
                                                : `Höchstens ${formatEur(rahmen.hoechstensNetto)} netto.`)}
                                    </p>
                                </div>
                                <div className="space-y-1.5">
                                    <Label htmlFor={grundId}>Grund *</Label>
                                    <Input
                                        id={grundId}
                                        value={grund}
                                        maxLength={GUTSCHRIFT_GRUND_MAX}
                                        placeholder="z. B. Preisnachlass wegen Kratzer am Geländer"
                                        aria-invalid={fehler.grund ? true : undefined}
                                        aria-describedby={`${grundId}-hinweis`}
                                        onChange={e => { setGrund(e.target.value); setFehler(f => ({ ...f, grund: undefined })); }}
                                    />
                                    <p id={`${grundId}-hinweis`} className={cn('text-xs', fehler.grund ? 'text-red-600' : 'text-slate-500')}>
                                        {fehler.grund ?? 'Steht so auf der Gutschrift.'}
                                    </p>
                                </div>
                            </div>
                        )}
                    </>
                )}

                <DialogFooter className="gap-2">
                    <Button variant="outline" size="sm" onClick={onClose} disabled={speichert}>
                        Abbrechen
                    </Button>
                    <Button
                        size="sm"
                        onClick={erstellen}
                        disabled={!rahmen || nichtsMehrMoeglich || speichert}
                        title={nichtsMehrMoeglich ? 'Die Rechnung ist schon vollständig gutgeschrieben.' : undefined}
                    >
                        {speichert
                            ? <Loader2 className="mr-2 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <HandCoins className="mr-2 h-4 w-4" aria-hidden="true" />}
                        Gutschrift anlegen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
