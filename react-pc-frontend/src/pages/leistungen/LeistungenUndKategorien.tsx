import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useSearchParams } from 'react-router-dom';
import { BarChart3, FolderCog, List } from 'lucide-react';
import { PageLayout } from '../../components/layout/PageLayout';
import { Card } from '../../components/ui/card';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { LadeAnzeige } from '../../components/ui/lade-anzeige';
import { Reiter, ReiterPanel } from '../../components/ui/reiter';
import { useToast } from '../../components/ui/toast';
import {
    aenderungAus, ladeLeistungen, ladeLoeschpruefung, ladeOrdner, ladeVerwendung, legeOrdnerAn, leistungLoeschUrl,
    loescheOrdnerMitModus, loescheSofort, ordnerLoeschUrl, speichereLeistung, speichereOrdner, verschiebeOrdner,
    type OrdnerAenderung,
} from './api';
import { AuswertungReiter, Fehlerkarte } from './AuswertungReiter';
import { InfoSeite, InfoVerschieben } from './InfoTexte';
import { LeistungenReiter } from './LeistungenReiter';
import { LeistungLoeschenDialog } from './LeistungLoeschenDialog';
import { OrdnerBaum } from './OrdnerBaum';
import { OrdnerKopf } from './OrdnerKopf';
import { OrdnerLoeschenDialog } from './OrdnerLoeschenDialog';
import { OrdnerReiter } from './OrdnerReiter';
import {
    baueBaum, hatUnterordner, leistungZielSperre, leistungsZaehler, ordnerKette, ordnerZielSperre, pfadText,
    pruefeBilddatei, zieleFuerLeistung, zieleFuerOrdnerVerschieben, zieleFuerUmzug,
} from './ordnerLogik';
import { UebersichtAuswertung } from './UebersichtAuswertung';
import { VerschiebenDialog } from './VerschiebenDialog';
import { useVerzoegertesLoeschen } from './useVerzoegertesLoeschen';
import type { Leistung, LeistungVerwendung, Loeschpruefung, LoeschModus, Ordner, ReiterId, ZiehObjekt } from './typen';

const REITER: ReiterId[] = ['leistungen', 'ordner', 'auswertung'];

type DialogZustand =
    | { art: 'ordnerVerschieben'; id: number }
    | { art: 'leistungVerschieben'; id: number }
    | { art: 'ordnerLoeschen'; pruefung: Loeschpruefung }
    | { art: 'leistungLoeschen'; leistung: Leistung; verwendung: LeistungVerwendung }
    | null;

/** Ordner-ID aus der Adresse (`?ordner=12`), sonst `null` (= Übersicht). */
function ordnerAusSuche(suche: string): number | null {
    const wert = new URLSearchParams(suche).get('ordner');
    const zahl = wert ? Number(wert) : NaN;
    return Number.isInteger(zahl) && zahl > 0 ? zahl : null;
}

/**
 * Seite „Leistungen & Kategorien“ (`/leistungen`).
 *
 * Eine Seite für zwei Sichten auf dieselben Daten: Die Ordner der Leistungen
 * sind zugleich die Projekt-Kategorien. Links der Ordnerbaum, rechts der
 * gewählte Ordner mit den Reitern Leistungen, Ordner und Auswertung – oder ohne
 * Auswahl die Übersicht aller Hauptordner. Gewählter Ordner und Reiter stehen
 * in der Adresse (`?ordner=12&reiter=auswertung`), damit Zurück und Links gehen.
 */
export default function LeistungenUndKategorien() {
    const toast = useToast();
    const bestaetigen = useConfirm();
    const [params, setParams] = useSearchParams();
    const gewaehlt = ordnerAusSuche(params.toString());
    const reiterWert = params.get('reiter') as ReiterId | null;
    const reiter: ReiterId = reiterWert && REITER.includes(reiterWert) ? reiterWert : 'leistungen';

    const [ordnerRoh, setOrdnerRoh] = useState<Ordner[]>([]);
    const [leistungenRoh, setLeistungenRoh] = useState<Leistung[]>([]);
    const [ladeStatus, setLadeStatus] = useState<'laedt' | 'fertig' | 'fehler'>('laedt');
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);

    const [aufgeklappt, setAufgeklappt] = useState<Set<number>>(() => new Set());
    const [suchtext, setSuchtext] = useState('');
    const [jahr, setJahr] = useState('');
    const [neuerOrdner, setNeuerOrdner] = useState<{ parentId: number | null } | null>(null);
    const [umbenennenId, setUmbenennenId] = useState<number | null>(null);
    const [ziehen, setZiehen] = useState<ZiehObjekt | null>(null);
    const [dialog, setDialog] = useState<DialogZustand>(null);
    const [oeffneLeistung, setOeffneLeistung] = useState<{ id: number; zaehler: number } | null>(null);
    const [ungespeichertLeistung, setUngespeichertLeistung] = useState(false);
    const [ungespeichertOrdner, setUngespeichertOrdner] = useState(false);
    const ungespeichert = ungespeichertLeistung || ungespeichertOrdner;

    const { ausgeblendet, planen } = useVerzoegertesLoeschen();
    const ordner = useMemo(() => ordnerRoh.filter((o) => !ausgeblendet.has(`ordner:${o.id}`)), [ordnerRoh, ausgeblendet]);
    const leistungen = useMemo(() => leistungenRoh.filter((l) => !ausgeblendet.has(`leistung:${l.id}`)), [leistungenRoh, ausgeblendet]);
    const baum = useMemo(() => baueBaum(ordner), [ordner]);
    const zaehler = useMemo(() => leistungsZaehler(baum, leistungen), [baum, leistungen]);
    const gewaehlterOrdner = gewaehlt !== null ? baum.byId.get(gewaehlt) ?? null : null;

    // ------------------------------------------------------------------
    // Laden
    // ------------------------------------------------------------------

    const [ladeVersuch, setLadeVersuch] = useState(0);
    useEffect(() => {
        let gueltig = true;
        Promise.all([ladeOrdner(), ladeLeistungen()])
            .then(([neueOrdner, neueLeistungen]) => {
                if (!gueltig) return;
                setOrdnerRoh(neueOrdner);
                setLeistungenRoh(neueLeistungen);
                setLadeStatus('fertig');
            })
            .catch((err: unknown) => {
                if (!gueltig) return;
                setLadeFehler(err instanceof Error ? err.message : 'Die Daten konnten nicht geladen werden.');
                setLadeStatus('fehler');
            });
        return () => { gueltig = false; };
    }, [ladeVersuch]);

    const erneutLaden = () => {
        setLadeStatus('laedt');
        setLadeFehler(null);
        setLadeVersuch((v) => v + 1);
    };

    /** Nach Änderungen im Hintergrund neu laden – ohne Ladeanzeige; ein Fehler kommt als Meldung. */
    const neuLaden = async () => {
        try {
            const [neueOrdner, neueLeistungen] = await Promise.all([ladeOrdner(), ladeLeistungen()]);
            setOrdnerRoh(neueOrdner);
            setLeistungenRoh(neueLeistungen);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Daten konnten nicht neu geladen werden.');
        }
    };

    // ------------------------------------------------------------------
    // Auswahl & Adresse
    // ------------------------------------------------------------------

    const waehle = useCallback((id: number | null, neuerReiter?: ReiterId) => {
        setParams((alt) => {
            const neu = new URLSearchParams(alt);
            if (id === null) { neu.delete('ordner'); neu.delete('reiter'); } else {
                neu.set('ordner', String(id));
                const r = neuerReiter ?? (alt.get('reiter') as ReiterId | null) ?? 'leistungen';
                if (r === 'leistungen') neu.delete('reiter'); else neu.set('reiter', r);
            }
            return neu;
        });
    }, [setParams]);

    // Gewählten Ordner im Baum sichtbar machen und aufklappen – einmal je Auswahl, danach darf man ihn zuklappen.
    const zuletztAufgeklappt = useRef<number | null>(null);
    useEffect(() => {
        if (gewaehlt === null || gewaehlt === zuletztAufgeklappt.current || !baum.byId.has(gewaehlt)) return;
        zuletztAufgeklappt.current = gewaehlt;
        const kette = ordnerKette(baum, gewaehlt).map((o) => o.id);
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setAufgeklappt((alt) => new Set([...alt, ...kette]));
    }, [gewaehlt, baum]);

    // Unbekannter Ordner in der Adresse (z. B. inzwischen gelöscht) → zurück zur Übersicht.
    useEffect(() => {
        if (ladeStatus !== 'fertig' || gewaehlt === null || gewaehlterOrdner) return;
        if (ordnerRoh.some((o) => o.id === gewaehlt)) return; // nur ausgeblendet (Löschen läuft)
        setParams((alt) => { const neu = new URLSearchParams(alt); neu.delete('ordner'); neu.delete('reiter'); return neu; }, { replace: true });
    }, [ladeStatus, gewaehlt, gewaehlterOrdner, ordnerRoh, setParams]);

    // Ungespeichertes nicht stillschweigend verlieren: beim Ordnerwechsel und beim Verlassen der Seite nachfragen.
    const blocker = useBlocker(({ currentLocation, nextLocation }) => ungespeichert && (
        currentLocation.pathname !== nextLocation.pathname
        || ordnerAusSuche(currentLocation.search) !== ordnerAusSuche(nextLocation.search)
    ));
    useEffect(() => {
        if (blocker.state !== 'blocked') return;
        let gueltig = true;
        void bestaetigen({
            title: 'Änderungen verwerfen?',
            message: 'Sie haben Änderungen, die noch nicht gespeichert sind. Wenn Sie jetzt wechseln, gehen sie verloren.',
            confirmLabel: 'Verwerfen und wechseln',
            cancelLabel: 'Weiter bearbeiten',
            variant: 'warning',
        }).then((ja) => {
            if (!gueltig) return;
            if (ja) blocker.proceed(); else blocker.reset();
        });
        return () => { gueltig = false; };
    }, [blocker, bestaetigen]);

    useEffect(() => {
        if (!ungespeichert) return;
        const warnen = (e: BeforeUnloadEvent) => { e.preventDefault(); };
        window.addEventListener('beforeunload', warnen);
        return () => window.removeEventListener('beforeunload', warnen);
    }, [ungespeichert]);

    const aufklappen = (id: number, offen: boolean) => {
        setAufgeklappt((alt) => {
            const neu = new Set(alt);
            if (offen) neu.add(id); else neu.delete(id);
            return neu;
        });
    };

    // ------------------------------------------------------------------
    // Ordner anlegen, umbenennen, speichern, verschieben
    // ------------------------------------------------------------------

    const neuenOrdnerStarten = (parentId: number | null) => {
        setUmbenennenId(null);
        setNeuerOrdner({ parentId });
        if (parentId !== null) aufklappen(parentId, true);
    };

    const ordnerAnlegen = async (parentId: number | null, name: string) => {
        const wandern = parentId !== null ? zaehler.get(parentId)?.direkt ?? 0 : 0;
        const neu = await legeOrdnerAn({ name, parentId, leistungenMitnehmen: wandern > 0 });
        setNeuerOrdner(null);
        await neuLaden();
        if (parentId !== null) aufklappen(parentId, true);
        waehle(neu.id);
        toast.success(wandern > 0
            ? `Ordner „${neu.name}“ angelegt – ${wandern === 1 ? 'die Leistung ist' : `${wandern} Leistungen sind`} mitgewandert.`
            : `Ordner „${neu.name}“ angelegt.`);
    };

    const ersetzeOrdner = (neu: Ordner) => setOrdnerRoh((alt) => alt.map((o) => (o.id === neu.id ? { ...neu, projektAnzahl: neu.projektAnzahl || o.projektAnzahl } : o)));

    const ordnerUmbenennen = async (id: number, name: string) => {
        const alt = baum.byId.get(id);
        if (!alt) return;
        const neu = await speichereOrdner(id, { ...aenderungAus(alt), name });
        ersetzeOrdner(neu);
        setUmbenennenId(null);
        toast.success(`Umbenannt in „${neu.name}“.`);
    };

    const ordnerSpeichern = async (id: number, aenderung: OrdnerAenderung) => {
        const neu = await speichereOrdner(id, aenderung);
        ersetzeOrdner(neu);
    };

    const ordnerVerschieben = async (id: number, zielId: number | null, mitRueckgaengig = true) => {
        const vorher = baum.byId.get(id);
        if (!vorher) return;
        const alterParent = vorher.parentId;
        await verschiebeOrdner(id, zielId);
        await neuLaden();
        if (zielId !== null) aufklappen(zielId, true);
        const ziel = zielId === null ? 'der obersten Ebene' : `„${baum.byId.get(zielId)?.name ?? 'Ziel'}“`;
        toast.success(`„${vorher.name}“ liegt jetzt in ${ziel}.`, mitRueckgaengig ? {
            aktion: {
                label: 'Rückgängig',
                onClick: () => {
                    void ordnerVerschieben(id, alterParent, false).catch((err: unknown) =>
                        toast.error(err instanceof Error ? err.message : 'Das Zurückschieben hat nicht geklappt.'));
                },
            },
            dauerMs: 6000,
        } : undefined);
    };

    const leistungVerschieben = async (leistung: Leistung, zielId: number, mitRueckgaengig = true) => {
        const vorher = leistung.ordnerId;
        const neu = await speichereLeistung({ ...leistung, ordnerId: zielId });
        setLeistungenRoh((alt) => alt.map((l) => (l.id === neu.id ? neu : l)));
        toast.success(`„${leistung.name}“ liegt jetzt in „${baum.byId.get(zielId)?.name ?? 'Ziel'}“.`, mitRueckgaengig && vorher !== null ? {
            aktion: {
                label: 'Rückgängig',
                onClick: () => {
                    void leistungVerschieben(neu, vorher, false).catch((err: unknown) =>
                        toast.error(err instanceof Error ? err.message : 'Das Zurückschieben hat nicht geklappt.'));
                },
            },
            dauerMs: 6000,
        } : undefined);
    };

    const bildAblegen = async (id: number, datei: File) => {
        const fehler = pruefeBilddatei(datei);
        if (fehler) { toast.error(fehler); return; }
        const o = baum.byId.get(id);
        if (!o) return;
        try {
            const neu = await speichereOrdner(id, { ...aenderungAus(o), bild: datei });
            ersetzeOrdner(neu);
            toast.success(`Bild für „${o.name}“ übernommen.`);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Bild konnte nicht übernommen werden.');
        }
    };

    // ------------------------------------------------------------------
    // Ziehen & Ablegen
    // ------------------------------------------------------------------

    const ablageErlaubt = useCallback((zielId: number | null) => {
        if (!ziehen) return false;
        if (ziehen.art === 'ordner') return ordnerZielSperre(baum, zaehler, ziehen.id, zielId) === null;
        const leistung = leistungen.find((l) => l.id === ziehen.id);
        return zielId !== null && !!leistung && leistungZielSperre(baum, leistung, zielId) === null;
    }, [ziehen, baum, zaehler, leistungen]);

    const ablegen = (zielId: number | null) => {
        const objekt = ziehen;
        setZiehen(null);
        if (!objekt) return;
        const fehlerMelden = (err: unknown) => toast.error(err instanceof Error ? err.message : 'Das Verschieben hat nicht geklappt.');
        if (objekt.art === 'ordner') { void ordnerVerschieben(objekt.id, zielId).catch(fehlerMelden); return; }
        const leistung = leistungen.find((l) => l.id === objekt.id);
        if (leistung && zielId !== null) void leistungVerschieben(leistung, zielId).catch(fehlerMelden);
    };

    // ------------------------------------------------------------------
    // Löschen
    // ------------------------------------------------------------------

    const ordnerLoeschenStarten = async (id: number) => {
        const o = baum.byId.get(id);
        if (!o) return;
        try {
            const pruefung = await ladeLoeschpruefung(id);
            if (pruefung.hatBezuege) { setDialog({ art: 'ordnerLoeschen', pruefung }); return; }
            if (gewaehlt === id) waehle(o.parentId);
            planen({
                schluessel: `ordner:${id}`,
                url: ordnerLoeschUrl(id),
                name: o.name,
                fehlerText: `„${o.name}“ konnte nicht gelöscht werden.`,
                onGeloescht: () => setOrdnerRoh((alt) => alt.filter((x) => x.id !== id)),
            });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Ordner konnte nicht gelöscht werden.');
        }
    };

    const ordnerLoeschenMitModus = async (pruefung: Loeschpruefung, modus: LoeschModus, zielId: number | null) => {
        const id = pruefung.kategorieId;
        const parent = baum.byId.get(id)?.parentId ?? null;
        await loescheOrdnerMitModus(id, modus, zielId);
        setDialog(null);
        // Erst die Auswahl umstellen, dann neu laden – sonst zeigte die Adresse kurz auf den gelöschten Ordner.
        const zielName = zielId !== null ? baum.byId.get(zielId)?.name ?? 'Ziel-Ordner' : '';
        if (modus === 'UMZIEHEN' && zielId !== null) waehle(zielId);
        else if (gewaehlt !== null && ordnerKette(baum, gewaehlt).some((o) => o.id === id)) waehle(parent);
        await neuLaden();
        toast.success(modus === 'UMZIEHEN'
            ? `„${pruefung.bezeichnung}“ gelöscht – der Inhalt liegt jetzt in „${zielName}“.`
            : `„${pruefung.bezeichnung}“ und sein Inhalt wurden gelöscht.`);
    };

    const leistungLoeschenStarten = async (leistung: Leistung) => {
        try {
            const verwendung = await ladeVerwendung(leistung.id);
            if (verwendung.dokumentAnzahl > 0) { setDialog({ art: 'leistungLoeschen', leistung, verwendung }); return; }
            planen({
                schluessel: `leistung:${leistung.id}`,
                url: leistungLoeschUrl(leistung.id),
                name: leistung.name,
                fehlerText: `„${leistung.name}“ konnte nicht gelöscht werden.`,
                onGeloescht: () => setLeistungenRoh((alt) => alt.filter((x) => x.id !== leistung.id)),
            });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Leistung konnte nicht gelöscht werden.');
        }
    };

    const leistungTrotzdemLoeschen = async (leistung: Leistung) => {
        await loescheSofort(leistungLoeschUrl(leistung.id), `„${leistung.name}“ konnte nicht gelöscht werden.`);
        setLeistungenRoh((alt) => alt.filter((x) => x.id !== leistung.id));
        setDialog(null);
        toast.success(`„${leistung.name}“ gelöscht.`);
    };

    // ------------------------------------------------------------------
    // Suche → Leistung öffnen
    // ------------------------------------------------------------------

    const zaehlerRef = useRef(0);
    const leistungOeffnen = (leistung: Leistung) => {
        if (leistung.ordnerId === null) return;
        zaehlerRef.current += 1;
        setOeffneLeistung({ id: leistung.id, zaehler: zaehlerRef.current });
        waehle(leistung.ordnerId, 'leistungen');
    };

    // ------------------------------------------------------------------
    // Darstellung
    // ------------------------------------------------------------------

    const reiterListe = [
        { id: 'leistungen' as const, label: 'Leistungen', icon: <List className="h-4 w-4" aria-hidden="true" />, anzahl: gewaehlt !== null ? zaehler.get(gewaehlt)?.gesamt ?? 0 : 0 },
        { id: 'ordner' as const, label: 'Ordner', icon: <FolderCog className="h-4 w-4" aria-hidden="true" />,
            kennzeichen: ungespeichertOrdner ? { art: 'punkt' as const, text: 'Nicht gespeichert' } : undefined },
        { id: 'auswertung' as const, label: 'Auswertung', icon: <BarChart3 className="h-4 w-4" aria-hidden="true" /> },
    ];

    const verschiebeOrdnerDialog = dialog?.art === 'ordnerVerschieben' ? baum.byId.get(dialog.id) : undefined;
    const verschiebeLeistungDialog = dialog?.art === 'leistungVerschieben' ? leistungen.find((l) => l.id === dialog.id) : undefined;

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Leistungen & Kategorien"
            titelInfo={<InfoSeite />}
            subtitle="Ihre Leistungen in Ordnern – dieselben Ordner ordnen auch Ihre Projekte ein."
        >
            {ladeStatus === 'laedt' && <LadeAnzeige text="Ordner und Leistungen werden geladen …" onErneut={erneutLaden} />}
            {ladeStatus === 'fehler' && (
                <Fehlerkarte text={ladeFehler ?? 'Die Daten konnten nicht geladen werden.'} onErneut={erneutLaden} />
            )}
            {ladeStatus === 'fertig' && (
                <div className="grid items-start gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
                    <Card className="sticky top-4 flex h-[calc(100vh-15rem)] min-h-[26rem] flex-col overflow-hidden border-slate-200 p-0 shadow-sm">
                        <OrdnerBaum
                            baum={baum}
                            zaehler={zaehler}
                            leistungen={leistungen}
                            gewaehlt={gewaehlt}
                            aufgeklappt={aufgeklappt}
                            onAufklappen={aufklappen}
                            onWaehlen={(id) => waehle(id)}
                            suchtext={suchtext}
                            onSuchtext={setSuchtext}
                            neuerOrdner={neuerOrdner}
                            onNeuerOrdner={neuenOrdnerStarten}
                            onNeuerOrdnerAbbrechen={() => setNeuerOrdner(null)}
                            onNeuerOrdnerSpeichern={ordnerAnlegen}
                            umbenennenId={umbenennenId}
                            onUmbenennen={(id) => { setNeuerOrdner(null); setUmbenennenId(id); }}
                            onUmbenennenAbbrechen={() => setUmbenennenId(null)}
                            onUmbenennenSpeichern={ordnerUmbenennen}
                            onVerschieben={(id) => setDialog({ art: 'ordnerVerschieben', id })}
                            onLoeschen={(id) => void ordnerLoeschenStarten(id)}
                            ziehen={ziehen}
                            onZiehenStart={setZiehen}
                            onZiehenEnde={() => setZiehen(null)}
                            ablageErlaubt={ablageErlaubt}
                            onAblegen={ablegen}
                            onBildAblegen={(id, datei) => void bildAblegen(id, datei)}
                            onLeistungOeffnen={leistungOeffnen}
                        />
                    </Card>

                    <Card className="min-w-0 space-y-5 border-slate-200 p-5 shadow-sm">
                        {gewaehlterOrdner ? (
                            <>
                                <OrdnerKopf
                                    ordner={gewaehlterOrdner}
                                    baum={baum}
                                    onWaehlen={(id) => waehle(id)}
                                    onUnterordner={() => neuenOrdnerStarten(gewaehlterOrdner.id)}
                                    onUmbenennen={() => { setNeuerOrdner(null); setUmbenennenId(gewaehlterOrdner.id); }}
                                    onVerschieben={() => setDialog({ art: 'ordnerVerschieben', id: gewaehlterOrdner.id })}
                                    onLoeschen={() => void ordnerLoeschenStarten(gewaehlterOrdner.id)}
                                />
                                <div>
                                    <Reiter reiter={reiterListe} aktiv={reiter} onWechsel={(r) => waehle(gewaehlterOrdner.id, r)}
                                        ariaLabel={`Bereiche von „${gewaehlterOrdner.name}“`} idPrefix="leistungen-seite" />
                                    {/* Leistungen und Ordner bleiben beim Reiterwechsel aufgebaut – angefangene Eingaben gehen nicht verloren. */}
                                    <ReiterPanel idPrefix="leistungen-seite" id="leistungen" hidden={reiter !== 'leistungen'} className="pt-5">
                                        <LeistungenReiter
                                            key={gewaehlterOrdner.id}
                                            ordner={gewaehlterOrdner}
                                            baum={baum}
                                            leistungen={leistungen}
                                            suchtext={suchtext}
                                            onSucheLeeren={() => setSuchtext('')}
                                            oeffnen={oeffneLeistung}
                                            onSpeichern={async (l) => {
                                                const neu = await speichereLeistung(l);
                                                setLeistungenRoh((alt) => (alt.some((x) => x.id === neu.id) ? alt.map((x) => (x.id === neu.id ? neu : x)) : [...alt, neu]));
                                                return neu;
                                            }}
                                            onLoeschen={(l) => void leistungLoeschenStarten(l)}
                                            onVerschieben={(l) => setDialog({ art: 'leistungVerschieben', id: l.id })}
                                            onZiehenStart={setZiehen}
                                            onZiehenEnde={() => setZiehen(null)}
                                            onUngespeichert={setUngespeichertLeistung}
                                        />
                                    </ReiterPanel>
                                    <ReiterPanel idPrefix="leistungen-seite" id="ordner" hidden={reiter !== 'ordner'} className="pt-5">
                                        <OrdnerReiter
                                            key={gewaehlterOrdner.id}
                                            ordner={gewaehlterOrdner}
                                            onSpeichern={(a) => ordnerSpeichern(gewaehlterOrdner.id, a)}
                                            onUngespeichert={setUngespeichertOrdner}
                                        />
                                    </ReiterPanel>
                                    {reiter === 'auswertung' && (
                                        <ReiterPanel idPrefix="leistungen-seite" id="auswertung" className="pt-5">
                                            <AuswertungReiter
                                                key={gewaehlterOrdner.id}
                                                ordner={gewaehlterOrdner}
                                                hatUnterordner={hatUnterordner(baum, gewaehlterOrdner.id)}
                                                jahr={jahr}
                                                onJahr={setJahr}
                                                onWaehlen={(id) => waehle(id, 'auswertung')}
                                            />
                                        </ReiterPanel>
                                    )}
                                </div>
                            </>
                        ) : (
                            <>
                                <div>
                                    <h2 className="text-xl font-bold text-slate-900">Alle Ordner</h2>
                                    <p className="text-sm text-slate-500">Wie viel Zeit steckt in welchem Bereich? Wählen Sie einen Ordner für Details.</p>
                                </div>
                                <UebersichtAuswertung jahr={jahr} onJahr={setJahr} onWaehlen={(id) => waehle(id, 'auswertung')} keineOrdner={ordner.length === 0} />
                            </>
                        )}
                    </Card>
                </div>
            )}

            {verschiebeOrdnerDialog && (
                <VerschiebenDialogFuerOrdner
                    ordner={verschiebeOrdnerDialog}
                    ort={verschiebeOrdnerDialog.parentId !== null ? pfadText(baum, verschiebeOrdnerDialog.parentId) : 'oberste Ebene'}
                    ziele={zieleFuerOrdnerVerschieben(baum, zaehler, verschiebeOrdnerDialog.id)}
                    onAbbrechen={() => setDialog(null)}
                    onBestaetigen={async (zielId) => { await ordnerVerschieben(verschiebeOrdnerDialog.id, zielId); setDialog(null); }}
                />
            )}
            {verschiebeLeistungDialog && (
                <VerschiebenDialog
                    titel={`„${verschiebeLeistungDialog.name}“ verschieben`}
                    beschreibung="Wählen Sie den Ordner, in den die Leistung kommen soll. Leistungen liegen immer im untersten Ordner."
                    ziele={zieleFuerLeistung(baum, verschiebeLeistungDialog)}
                    onAbbrechen={() => setDialog(null)}
                    onBestaetigen={async (zielId) => {
                        if (zielId === null) return;
                        await leistungVerschieben(verschiebeLeistungDialog, zielId);
                        setDialog(null);
                    }}
                />
            )}
            {dialog?.art === 'ordnerLoeschen' && (
                <OrdnerLoeschenDialog
                    pruefung={dialog.pruefung}
                    ordner={baum.byId.get(dialog.pruefung.kategorieId)}
                    ziele={zieleFuerUmzug(baum, zaehler, dialog.pruefung.kategorieId)}
                    ordnerVon={(id) => baum.byId.get(id)}
                    onAbbrechen={() => setDialog(null)}
                    onLoeschen={(modus, zielId) => ordnerLoeschenMitModus(dialog.pruefung, modus, zielId)}
                />
            )}
            {dialog?.art === 'leistungLoeschen' && (
                <LeistungLoeschenDialog
                    name={dialog.leistung.name}
                    verwendung={dialog.verwendung}
                    onAbbrechen={() => setDialog(null)}
                    onLoeschen={() => leistungTrotzdemLoeschen(dialog.leistung)}
                />
            )}
        </PageLayout>
    );
}

function VerschiebenDialogFuerOrdner({ ordner, ort, ziele, onAbbrechen, onBestaetigen }: {
    ordner: Ordner;
    /** Wo der Ordner jetzt liegt. */
    ort: string;
    ziele: ReturnType<typeof zieleFuerOrdnerVerschieben>;
    onAbbrechen: () => void;
    onBestaetigen: (zielId: number | null) => Promise<void>;
}) {
    return (
        <VerschiebenDialog
            titel={`„${ordner.name}“ verschieben`}
            info={<InfoVerschieben />}
            beschreibung={<>Liegt jetzt in: {ort}. Wählen Sie, wohin der Ordner mit seinem Inhalt soll.</>}
            ziele={ziele}
            onAbbrechen={onAbbrechen}
            onBestaetigen={onBestaetigen}
        />
    );
}
