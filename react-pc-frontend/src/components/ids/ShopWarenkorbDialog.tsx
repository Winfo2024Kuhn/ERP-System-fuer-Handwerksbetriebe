import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Check, ExternalLink, Loader2, PackagePlus, PackageOpen, RotateCcw, ShoppingCart, Store, Trash2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { DecimalInput } from '../ui/decimal-input';
import { SegmentAuswahl } from '../ui/segment-auswahl';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { formatCurrency } from '../artikel/formatCurrency';
import { cn } from '../../lib/utils';
import { useOptionalAuth } from '../../auth/AuthContext';
import { CreateArticleModal } from '../CreateArticleModal';
import type { DocBlock } from '../document-editor/types';
import { uebernimmIdsArtikelstamm, type IdsArtikelstammErgebnis, type IdsLieferant, type IdsPosition, type IdsVorgang } from './api';
import { useIdsShopBesuch } from './useIdsShopBesuch';
import {
    addiereStammErgebnisse,
    artikelVorbelegungAus,
    artikelstammAuftraegeJeBesuch,
    artikelstammErgebnisText,
    aufschlagQuelleText,
    baueEinzelBloecke,
    baueSammelBlock,
    berechneSummen,
    entferneZeile,
    gueltigeZeilen,
    haengeZeilenAn,
    pruefeZeilen,
    sammelListeText,
    stammAktionFuer,
    stelleZeileWiederHer,
    verkaufspreis,
    weitereArtikelText,
    zahlAusEntwurf,
    zeilenEntwurfAus,
    zeilenSchluessel,
    zuEntwurf,
    type StammAktion,
    type WarenkorbZeile,
    type ZeilenEntwurf,
} from './warenkorbPositionen';

/**
 * Warenkorb aus dem Lieferanten-Shop (IDS) ins Dokument holen – in vier Schritten:
 * Shop wählen → im Shop auswählen → Preise prüfen → Artikelstamm.
 *
 * <p>Jeder Schritt sagt, was gerade passiert und was als Nächstes zu tun ist.
 * Ins Dokument kommt erst etwas, wenn der Nutzer im dritten Schritt auf
 * „In Dokument einfügen“ klickt. Im dritten Schritt kann er Artikel aus der
 * Liste nehmen (mit „Rückgängig“) und mit „Weitere Artikel aus dem Shop holen“
 * einen neuen Shop-Besuch starten, dessen Artikel unten angehängt werden.
 * Jede Zeile kennt deshalb ihren Ursprung (Shop-Besuch + Index darin). Der
 * vierte Schritt fragt danach, ob die Preise im Artikelstamm nachgezogen
 * werden sollen – je Shop-Besuch ein Aufruf; das ändert das Dokument nicht mehr.
 * Unbekannte Artikel legt der Nutzer dort einzeln über das gewohnte
 * Anlegen-Fenster an (vorausgefüllt), damit Name und Kategorie stimmen.</p>
 */

type Schritt = 'shop' | 'warten' | 'preise' | 'stamm';
type Modus = 'einzeln' | 'zusammen';

const SCHRITTE: { id: Schritt; text: string }[] = [
    { id: 'shop', text: 'Shop wählen' },
    { id: 'warten', text: 'Im Shop auswählen' },
    { id: 'preise', text: 'Preise prüfen' },
    { id: 'stamm', text: 'Artikelstamm' },
];

/** Schlüssel der „Rückgängig“-Meldungen: Beim Schließen/Einfügen verschwinden sie, damit nichts in eine spätere Liste zurückspringt. */
const ENTFERNT_MELDUNG = 'ids-zeile-entfernt';

interface Zeile extends ZeilenEntwurf {
    /** Kleiner Hinweis unter dem Aufschlag: woher er kommt bzw. dass er von Hand gesetzt ist. */
    aufschlagHinweis: string;
    /** Die Zeile, wie der Shop sie geschickt hat (Hinweis, fehlender Preis, Artikelstamm). */
    position: IdsPosition;
}

interface StammZeile {
    zeile: WarenkorbZeile;
    aktion: StammAktion;
    alterPreis: number | null;
    /** Preis, wie der Shop ihn geschickt hat – entscheidet, ob der Preis als „aus dem Shop“ gilt. */
    shopPreis: number | null;
    /** Nur bei „aktualisieren“: Haken für „Preis aktualisieren“. */
    gewaehlt: boolean;
    /** Nach „Als Artikel anlegen…“: die vergebene Artikel-Nr. (leer, wenn der Server keine meldet). */
    angelegtAls?: string;
}

/** Eine Meldung, die nach einem Wechsel des Shop-Besuchs angezeigt werden soll (die Nummer macht jede einzigartig). */
interface Meldung {
    nr: number;
    art: 'erfolg' | 'fehler';
    text: string;
}

export interface ShopWarenkorbDialogProps {
    offen: boolean;
    /** Die Shops, die gerade zur Verfügung stehen (aus `/api/ids/verfuegbar`). */
    lieferanten: IdsLieferant[];
    onSchliessen: () => void;
    /** Fügt die fertigen Positionen ins Dokument ein (ein Schritt im Verlauf). */
    onEinfuegen: (bloecke: DocBlock[]) => void;
}

function startZeilen(vorgang: IdsVorgang): Zeile[] {
    return vorgang.positionen.map((p) => ({
        ...zeilenEntwurfAus(p, vorgang.vorgangId),
        aufschlagHinweis: aufschlagQuelleText(p.aufschlagQuelle),
        position: p,
    }));
}

function Schrittanzeige({ aktiv }: { aktiv: Schritt }) {
    const aktivIndex = SCHRITTE.findIndex((s) => s.id === aktiv);
    return (
        <ol aria-label="Schritte" className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            {SCHRITTE.map((s, i) => {
                const erledigt = i < aktivIndex;
                const jetzt = i === aktivIndex;
                return (
                    <li key={s.id} className="flex items-center gap-2" aria-current={jetzt ? 'step' : undefined}>
                        {i > 0 && <span aria-hidden="true" className="text-slate-300">·</span>}
                        <span className={cn(
                            'flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold',
                            jetzt ? 'bg-rose-600 text-white' : erledigt ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-500',
                        )}>
                            {erledigt ? <Check aria-hidden="true" className="h-3.5 w-3.5" /> : i + 1}
                        </span>
                        <span className={cn(jetzt ? 'font-semibold text-slate-900' : 'text-slate-500')}>{s.text}</span>
                    </li>
                );
            })}
        </ol>
    );
}

export function ShopWarenkorbDialog({ offen, lieferanten, onSchliessen, onEinfuegen }: ShopWarenkorbDialogProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const besuch = useIdsShopBesuch();

    const [schritt, setSchritt] = useState<Schritt>('shop');
    const [lieferantId, setLieferantId] = useState<number | null>(null);
    const [zeilen, setZeilen] = useState<Zeile[]>([]);
    /** Alle Shop-Besuche, deren Artikel in der Liste gelandet sind (für die Bestell-Warnung). */
    const [vorgaenge, setVorgaenge] = useState<IdsVorgang[]>([]);
    /** Läuft gerade ein weiterer Shop-Besuch, dessen Artikel unten angehängt werden sollen? */
    const [nachladen, setNachladen] = useState(false);
    const [meldung, setMeldung] = useState<Meldung | null>(null);
    const [alleAufschlag, setAlleAufschlag] = useState('');
    const [modus, setModus] = useState<Modus>('einzeln');
    const [ueberschrift, setUeberschrift] = useState('Material');
    const [stammZeilen, setStammZeilen] = useState<StammZeile[]>([]);
    const [stammSpeichert, setStammSpeichert] = useState(false);
    /** Shop-Besuche, deren Artikelstamm-Teil schon durch ist – wird nach einem Fehler nicht erneut gesendet. */
    const [stammErledigt, setStammErledigt] = useState<Map<number, IdsArtikelstammErgebnis>>(new Map());
    /** Für welche Zeile ist gerade das Anlegen-Fenster offen? */
    const [anlegenFuer, setAnlegenFuer] = useState<string | null>(null);

    // Wird der Dialog von außen zugemacht (z. B. weil das Dokument gesperrt wird),
    // nicht im Hintergrund weiter nach dem Warenkorb fragen.
    const { abbrechen: besuchAbbrechen } = besuch;
    useEffect(() => {
        if (!offen) besuchAbbrechen();
    }, [offen, besuchAbbrechen]);

    // Ergebnis eines weiteren Shop-Besuchs als Meldung zeigen (Toasts gehören nicht in den Render-Durchlauf).
    useEffect(() => {
        if (!meldung) return;
        if (meldung.art === 'erfolg') toast.success(meldung.text);
        else toast.error(meldung.text);
    }, [meldung, toast]);

    // Beim Öffnen frisch anfangen; bei genau einem Shop ist er schon gewählt.
    const [warOffen, setWarOffen] = useState(false);
    if (offen !== warOffen) {
        setWarOffen(offen);
        if (offen) {
            setSchritt('shop');
            setLieferantId(lieferanten.length === 1 ? lieferanten[0].lieferantId : null);
            setZeilen([]);
            setVorgaenge([]);
            setNachladen(false);
            setModus('einzeln');
            setUeberschrift('Material');
            setStammZeilen([]);
            setStammErledigt(new Map());
            setAnlegenFuer(null);
        }
    }

    // Warenkorb angekommen: beim ersten Besuch → „Preise prüfen“ mit den Werten des Servers;
    // bei „Weitere Artikel holen“ → unten anhängen, Bisheriges samt Änderungen bleibt.
    const vorgang = besuch.vorgang;
    const [geladenerVorgang, setGeladenerVorgang] = useState<number | null>(null);
    if (offen && besuch.phase === 'empfangen' && vorgang && vorgang.vorgangId !== geladenerVorgang) {
        setGeladenerVorgang(vorgang.vorgangId);
        const neue = startZeilen(vorgang);
        if (nachladen) {
            setZeilen((alt) => haengeZeilenAn(alt, neue));
            setVorgaenge((alt) => [...alt, vorgang]);
            setNachladen(false);
            setMeldung((alt) => ({ nr: (alt?.nr ?? 0) + 1, art: 'erfolg', text: weitereArtikelText(neue.length) }));
        } else if (schritt === 'warten') {
            setZeilen(neue);
            setVorgaenge([vorgang]);
            setAlleAufschlag(zuEntwurf(vorgang.standardAufschlagProzent));
            setSchritt('preise');
        }
    }
    // Weiterer Besuch fehlgeschlagen oder abgelaufen: erklären, die Liste bleibt unverändert.
    if (nachladen && (besuch.phase === 'fehler' || besuch.phase === 'abgelaufen')) {
        setNachladen(false);
        const grund = besuch.fehlertext || 'Der Shop konnte nicht geöffnet werden.';
        setMeldung((alt) => ({ nr: (alt?.nr ?? 0) + 1, art: 'fehler', text: `Keine weiteren Artikel geholt: ${grund} Deine Liste bleibt, wie sie ist.` }));
    }

    const lieferantName = lieferanten.find((l) => l.lieferantId === lieferantId)?.lieferantName
        ?? vorgaenge[0]?.lieferantName ?? vorgang?.lieferantName ?? 'Lieferant';
    const shopLieferantId = lieferantId ?? vorgaenge[0]?.lieferantId ?? null;
    const mitBestellung = vorgaenge.some((v) => v.mitBestellung);

    const live = useMemo(() => gueltigeZeilen(zeilen), [zeilen]);
    const summen = useMemo(() => berechneSummen(live), [live]);

    const schliessen = async () => {
        if (schritt === 'preise' && zeilen.length > 0) {
            const ok = await confirm({
                title: 'Warenkorb verwerfen?',
                message: 'Die Artikel aus dem Shop sind noch nicht im Dokument. Wenn du jetzt schließt, musst du den Shop erneut öffnen.',
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter prüfen',
                variant: 'warning',
            });
            if (!ok) return;
        }
        toast.schliessen(ENTFERNT_MELDUNG);
        besuch.abbrechen();
        onSchliessen();
    };

    const shopOeffnen = () => {
        if (lieferantId == null) return;
        // Synchron im Klick: öffnet den Tab, bevor der Browser ihn als Pop-up blockt.
        besuch.starte(lieferantId);
        setSchritt('warten');
    };

    const weitereHolen = () => {
        if (shopLieferantId == null) return;
        // Wie beim ersten Mal synchron im Klick – sonst blockt der Browser den Tab.
        besuch.starte(shopLieferantId);
        setNachladen(true);
    };

    const nachladenAbbrechen = () => {
        besuch.abbrechen();
        setNachladen(false);
    };

    const zeileAendern = (schluessel: string, aenderung: Partial<Zeile>) => {
        setZeilen((alt) => alt.map((z) => (zeilenSchluessel(z) === schluessel ? { ...z, ...aenderung } : z)));
    };

    const zeileEntfernen = (schluessel: string) => {
        const { zeilen: rest, entfernt } = entferneZeile(zeilen, schluessel);
        if (!entfernt) return;
        setZeilen(rest);
        const name = entfernt.zeile.bezeichnung.trim() || entfernt.zeile.artikelnummer;
        toast.info(`„${name}“ aus der Liste entfernt.`, {
            schluessel: ENTFERNT_MELDUNG,
            dauerMs: 8000,
            aktion: { label: 'Rückgängig', onClick: () => setZeilen((alt) => stelleZeileWiederHer(alt, entfernt)) },
        });
    };

    const aufAlleAnwenden = () => {
        const wert = zahlAusEntwurf(alleAufschlag);
        if (wert === null) {
            toast.error('Bitte den Aufschlag für alle als Zahl eintragen (z. B. 25 oder 12,5).');
            return;
        }
        setZeilen((alt) => alt.map((z) => ({ ...z, aufschlag: alleAufschlag.trim(), aufschlagHinweis: 'für alle gesetzt' })));
    };

    const einfuegen = () => {
        const pruefung = pruefeZeilen(zeilen);
        if (!pruefung.gueltig) {
            toast.error(pruefung.meldung);
            return;
        }
        const bloecke = modus === 'einzeln'
            ? baueEinzelBloecke(pruefung.zeilen)
            : [baueSammelBlock(pruefung.zeilen, ueberschrift)];
        onEinfuegen(bloecke);
        toast.schliessen(ENTFERNT_MELDUNG);
        toast.success(bloecke.length === 1 ? '1 Position eingefügt.' : `${bloecke.length} Positionen eingefügt.`);
        const nachSchluessel = new Map(zeilen.map((z) => [zeilenSchluessel(z), z]));
        setStammZeilen(pruefung.zeilen.map((zeile) => {
            const stamm = nachSchluessel.get(zeilenSchluessel(zeile))?.position.stamm ?? null;
            const aktion = stammAktionFuer(stamm, zeile.einkaufspreis);
            const shopPreis = nachSchluessel.get(zeilenSchluessel(zeile))?.position.einkaufspreis ?? null;
            return { zeile, aktion, alterPreis: stamm?.aktuellerPreis ?? null, shopPreis, gewaehlt: aktion === 'aktualisieren' };
        }));
        setStammErledigt(new Map());
        setSchritt('stamm');
    };

    const stammUebernehmen = async () => {
        // Je Shop-Besuch ein Aufruf, nacheinander; schon erledigte Teile nicht noch einmal senden.
        const auftraege = artikelstammAuftraegeJeBesuch(stammZeilen.filter((s) => s.gewaehlt));
        const erledigt = new Map(stammErledigt);
        setStammSpeichert(true);
        try {
            for (const { vorgangId, auftrag } of auftraege) {
                if (erledigt.has(vorgangId)) continue;
                erledigt.set(vorgangId, await uebernimmIdsArtikelstamm(vorgangId, auftrag));
            }
            toast.success(artikelstammErgebnisText(addiereStammErgebnisse(erledigt.values())));
            besuch.abbrechen();
            onSchliessen();
        } catch (e) {
            setStammErledigt(erledigt);
            const grund = e instanceof Error && e.message ? e.message : 'Der Artikelstamm konnte nicht aktualisiert werden.';
            const schon = addiereStammErgebnisse(erledigt.values());
            const teil = schon.aktualisiert + schon.angelegt > 0 ? ` Schon erledigt: ${artikelstammErgebnisText(schon)}` : '';
            toast.error(`${grund}${teil}`);
        } finally {
            setStammSpeichert(false);
        }
    };

    const anlegenZeile = anlegenFuer ? stammZeilen.find((s) => zeilenSchluessel(s.zeile) === anlegenFuer) ?? null : null;
    const anlegenVorbelegung = useMemo(() => {
        if (!anlegenZeile) return undefined;
        const v = vorgaenge.find((x) => x.vorgangId === anlegenZeile.zeile.vorgangId);
        const lieferant = { id: v?.lieferantId ?? shopLieferantId ?? 0, name: v?.lieferantName ?? lieferantName };
        return artikelVorbelegungAus(anlegenZeile.zeile, anlegenZeile.shopPreis, lieferant);
    }, [anlegenZeile, vorgaenge, shopLieferantId, lieferantName]);

    const alsArtikelAngelegt = (schluessel: string, artikelnummer: string | null | undefined) => {
        setStammZeilen((alt) => alt.map((s) => (zeilenSchluessel(s.zeile) === schluessel ? { ...s, angelegtAls: artikelnummer?.trim() ?? '' } : s)));
        setAnlegenFuer(null);
    };

    const breit = schritt === 'preise' || schritt === 'stamm';
    const listeLeer = zeilen.length === 0;
    const einfuegenGesperrt = listeLeer
        ? 'Keine Artikel in der Liste – erst weitere aus dem Shop holen'
        : nachladen ? 'Erst auf die Artikel aus dem Shop warten – oder oben „Abbrechen“' : undefined;

    return (
        <Dialog
            open={offen}
            onOpenChange={(o) => { if (!o) void schliessen(); }}
            aria-labelledby="ids-dialog-titel"
            className={cn('w-full', breit ? 'max-w-6xl' : 'max-w-2xl')}
        >
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id="ids-dialog-titel" className="flex items-center gap-2 text-slate-900">
                        <ShoppingCart aria-hidden="true" className="h-5 w-5 text-rose-600" />
                        Aus Lieferanten-Shop übernehmen
                    </DialogTitle>
                    <Schrittanzeige aktiv={schritt} />
                </DialogHeader>

                {schritt === 'shop' && (
                    <SchrittShop
                        lieferanten={lieferanten}
                        lieferantId={lieferantId}
                        onWaehlen={setLieferantId}
                        onAbbrechen={() => void schliessen()}
                        onOeffnen={shopOeffnen}
                    />
                )}

                {schritt === 'warten' && (
                    <SchrittWarten
                        lieferantName={lieferantName}
                        phase={besuch.phase}
                        fehlertext={besuch.fehlertext}
                        onErneut={shopOeffnen}
                        onAbbrechen={() => { besuch.abbrechen(); setSchritt('shop'); }}
                    />
                )}

                {schritt === 'preise' && (
                    <>
                        <div className="-m-1 min-h-0 flex-1 space-y-4 overflow-auto p-1">
                            {mitBestellung && (
                                <div role="alert" className="flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
                                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
                                    <p><span className="font-semibold">Achtung: Dieser Warenkorb wurde im Shop bereits bestellt.</span> Nicht noch einmal bestellen.</p>
                                </div>
                            )}
                            {nachladen && (
                                <ShopOffenHinweis lieferantName={lieferantName} startet={besuch.phase === 'startet'} onAbbrechen={nachladenAbbrechen} />
                            )}

                            {listeLeer ? (
                                <LeereListe wartet={nachladen} onHolen={weitereHolen} />
                            ) : (
                                <>
                                    <p className="text-sm text-slate-600">
                                        {zeilen.length === 1 ? '1 Artikel' : `${zeilen.length} Artikel`} von {lieferantName} in der Liste.
                                        Prüf die Preise und Mengen, dann „In Dokument einfügen“. Was nicht ins Dokument soll, nimmst du mit dem Papierkorb heraus.
                                    </p>

                                    <div className="flex flex-wrap items-end justify-between gap-3">
                                        <div className="flex flex-wrap items-end gap-3">
                                            <div className="w-52">
                                                <div className="mb-1 flex items-center gap-1">
                                                    <Label htmlFor="ids-aufschlag-alle" className="mb-0">Aufschlag für alle (%)</Label>
                                                    <AufschlagInfo />
                                                </div>
                                                <DecimalInput id="ids-aufschlag-alle" value={alleAufschlag} onChange={setAlleAufschlag} placeholder="z. B. 25" />
                                            </div>
                                            <Button variant="outline" size="sm" onClick={aufAlleAnwenden} className="mb-0.5">Auf alle anwenden</Button>
                                        </div>
                                        <WeitereArtikelHolen wartet={nachladen} onHolen={weitereHolen} className="mb-0.5" />
                                    </div>

                                    <PreisTabelle zeilen={zeilen} onAendern={zeileAendern} onEntfernen={zeileEntfernen} />

                                    <div className="flex flex-wrap items-start gap-4">
                                        <SegmentAuswahl
                                            beschriftung="Wie ins Dokument?"
                                            wert={modus}
                                            onChange={(w) => setModus(w as Modus)}
                                            optionen={[
                                                { wert: 'einzeln', text: 'Jeder Artikel eine Position' },
                                                { wert: 'zusammen', text: 'Alles in einer Position' },
                                            ]}
                                        />
                                        {modus === 'zusammen' && (
                                            <div className="min-w-[16rem] flex-1 space-y-2">
                                                <div>
                                                    <Label htmlFor="ids-ueberschrift">Überschrift</Label>
                                                    <Input id="ids-ueberschrift" value={ueberschrift} onChange={(e) => setUeberschrift(e.target.value)} placeholder="Material" />
                                                </div>
                                                <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm" aria-label="Vorschau der Position">
                                                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vorschau</p>
                                                    <p className="mt-1 font-medium text-slate-900">{ueberschrift.trim() || 'Material'} · 1 pauschal · {formatCurrency(summen.verkauf)}</p>
                                                    <p className="mt-1 text-slate-600">{sammelListeText(live) || 'Noch keine vollständigen Artikel in der Liste.'}</p>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </>
                            )}
                        </div>

                        <DialogFooter className="flex-wrap items-center gap-3 border-t border-slate-200 pt-4 sm:justify-between">
                            <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm" aria-label="Summen">
                                <div className="flex gap-1.5"><dt className="text-slate-500">Summe Einkauf</dt><dd className="font-medium tabular-nums text-slate-900">{formatCurrency(summen.einkauf)}</dd></div>
                                <div className="flex gap-1.5"><dt className="text-slate-500">Summe Verkauf</dt><dd className="font-semibold tabular-nums text-slate-900">{formatCurrency(summen.verkauf)}</dd></div>
                                <div className="flex gap-1.5"><dt className="text-slate-500">Gewinn</dt><dd className={cn('font-medium tabular-nums', summen.gewinn < 0 ? 'text-red-700' : 'text-emerald-700')}>{formatCurrency(summen.gewinn)}</dd></div>
                            </dl>
                            <div className="flex gap-2">
                                <Button variant="outline" onClick={() => void schliessen()}>Abbrechen</Button>
                                <Button onClick={einfuegen} disabled={einfuegenGesperrt !== undefined} title={einfuegenGesperrt}>
                                    <Check aria-hidden="true" className="h-4 w-4" />
                                    In Dokument einfügen
                                </Button>
                            </div>
                        </DialogFooter>
                    </>
                )}

                {schritt === 'stamm' && (
                    <SchrittStamm
                        zeilen={stammZeilen}
                        speichert={stammSpeichert}
                        onUmschalten={(schluessel, gewaehlt) => setStammZeilen((alt) => alt.map((s) => (zeilenSchluessel(s.zeile) === schluessel ? { ...s, gewaehlt } : s)))}
                        onAnlegen={setAnlegenFuer}
                        onUeberspringen={() => { besuch.abbrechen(); onSchliessen(); }}
                        onUebernehmen={() => void stammUebernehmen()}
                    />
                )}
            </DialogContent>

            {anlegenFuer && anlegenVorbelegung && (
                <CreateArticleModal
                    key={anlegenFuer}
                    vorbelegung={anlegenVorbelegung}
                    onClose={() => setAnlegenFuer(null)}
                    onSave={(artikel) => alsArtikelAngelegt(anlegenFuer, artikel.artikelnummer)}
                />
            )}
        </Dialog>
    );
}

// ---------- Schritt 1: Shop wählen ----------

interface SchrittShopProps {
    lieferanten: IdsLieferant[];
    lieferantId: number | null;
    onWaehlen: (id: number) => void;
    onAbbrechen: () => void;
    onOeffnen: () => void;
}

function SchrittShop({ lieferanten, lieferantId, onWaehlen, onAbbrechen, onOeffnen }: SchrittShopProps) {
    const gewaehlt = lieferanten.find((l) => l.lieferantId === lieferantId) ?? null;
    return (
        <>
            <div className="space-y-3">
                <div className="flex items-center gap-1">
                    <DialogDescription className="text-slate-600">Bei welchem Lieferanten willst du einkaufen?</DialogDescription>
                    <InfoButton titel="Aus Lieferanten-Shop">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>Der Shop öffnet sich in einem neuen Tab, du bist dort schon angemeldet. Wenn du im Shop „Warenkorb übernehmen“ klickst, schickt der Shop die Artikel mit deinen Einkaufspreisen zurück ins Programm. Der Shop-Besuch gilt 60 Minuten.</p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>Im Shop wird nichts bestellt. Die Artikel landen erst im Dokument, wenn du sie im nächsten Schritt geprüft und auf „In Dokument einfügen“ geklickt hast.</p>
                        </InfoAbschnitt>
                    </InfoButton>
                </div>
                <div role="radiogroup" aria-label="Lieferant" className="grid gap-2 sm:grid-cols-2">
                    {lieferanten.map((l) => {
                        const gewaehlt = l.lieferantId === lieferantId;
                        return (
                            <button
                                key={l.lieferantId}
                                type="button"
                                role="radio"
                                aria-checked={gewaehlt}
                                onClick={() => onWaehlen(l.lieferantId)}
                                className={cn(
                                    'flex items-center gap-3 rounded-lg border px-3 py-3 text-left text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                                    gewaehlt ? 'border-rose-300 bg-rose-50 font-semibold text-rose-800' : 'border-slate-200 text-slate-800 hover:bg-slate-50',
                                )}
                            >
                                <Store aria-hidden="true" className={cn('h-5 w-5 shrink-0', gewaehlt ? 'text-rose-600' : 'text-slate-400')} />
                                {l.lieferantName}
                            </button>
                        );
                    })}
                </div>
                {gewaehlt && gewaehlt.passwortGesetzt === false && <OhnePasswortHinweis lieferantName={gewaehlt.lieferantName} />}
            </div>
            <DialogFooter className="gap-2">
                <Button variant="outline" onClick={onAbbrechen}>Abbrechen</Button>
                <Button
                    onClick={onOeffnen}
                    disabled={lieferantId == null}
                    title={lieferantId == null ? 'Bitte zuerst einen Lieferanten wählen' : undefined}
                >
                    <ExternalLink aria-hidden="true" className="h-4 w-4" />
                    Shop öffnen
                </Button>
            </DialogFooter>
        </>
    );
}

/**
 * Ohne gespeichertes Passwort lehnt der Shop die Anmeldung meist ab. Vorher
 * sagen statt hinterher rätseln lassen; öffnen bleibt trotzdem möglich
 * (manche Shops brauchen keins). Admins kommen direkt zur Einrichtung –
 * in einem neuen Tab, damit das offene Dokument bleibt.
 */
function OhnePasswortHinweis({ lieferantName }: { lieferantName: string }) {
    const istAdmin = useOptionalAuth()?.isAdmin ?? false;
    return (
        <div role="note" className="flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <div className="space-y-1">
                <p>Für {lieferantName} ist kein Passwort gespeichert – der Shop wird die Anmeldung vermutlich ablehnen („Login failed“).</p>
                {istAdmin && (
                    <a
                        href="/einstellungen#ids"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 rounded font-medium text-rose-700 underline underline-offset-2 hover:text-rose-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                        Zugang in den Einstellungen ergänzen
                        <ExternalLink aria-hidden="true" className="h-3.5 w-3.5" />
                        <span className="sr-only">(öffnet in neuem Tab)</span>
                    </a>
                )}
            </div>
        </div>
    );
}

// ---------- Schritt 2: Warten auf den Warenkorb ----------

interface SchrittWartenProps {
    lieferantName: string;
    phase: ReturnType<typeof useIdsShopBesuch>['phase'];
    fehlertext: string | null;
    onErneut: () => void;
    onAbbrechen: () => void;
}

function SchrittWarten({ lieferantName, phase, fehlertext, onErneut, onAbbrechen }: SchrittWartenProps) {
    const problem = phase === 'abgelaufen' || phase === 'fehler';
    return (
        <>
            {problem ? (
                <div role="alert" className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                    <div>
                        <p className="font-semibold">{phase === 'abgelaufen' ? 'Der Shop-Besuch ist abgelaufen.' : 'Das hat nicht geklappt.'}</p>
                        <p className="mt-1">{fehlertext}</p>
                    </div>
                </div>
            ) : (
                <div role="status" aria-live="polite" className="space-y-4 py-2">
                    <div className="flex items-center gap-3 text-slate-700">
                        <Loader2 aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600 motion-safe:animate-spin" />
                        <span className="font-medium">
                            {phase === 'startet' ? `Der Shop von ${lieferantName} wird geöffnet …` : 'Warte auf den Warenkorb …'}
                        </span>
                    </div>
                    {phase !== 'startet' && (
                        <p className="text-sm text-slate-600">
                            Der Shop von <strong className="text-slate-900">{lieferantName}</strong> ist in einem neuen Tab geöffnet.
                            Stell dort den Warenkorb zusammen und klick auf „Warenkorb übernehmen“ (heißt je nach Shop auch „An Software übergeben“).
                            Danach geht es hier von selbst weiter.
                        </p>
                    )}
                </div>
            )}
            <DialogFooter className="gap-2">
                <Button variant="outline" onClick={onAbbrechen}>{problem ? 'Zurück' : 'Abbrechen'}</Button>
                <Button variant={problem ? 'default' : 'outline'} onClick={onErneut} disabled={phase === 'startet'}>
                    <RotateCcw aria-hidden="true" className="h-4 w-4" />
                    {problem ? 'Nochmal versuchen' : 'Shop erneut öffnen'}
                </Button>
            </DialogFooter>
        </>
    );
}

// ---------- Schritt 3: Preis-Tabelle ----------

function AufschlagInfo() {
    return (
        <InfoButton titel="Aufschlag">
            <p>Vorbelegt wird der Aufschlag, der im Programm für den Artikel gilt: am Artikel selbst, sonst an seiner Kategorie, sonst dein allgemeiner Material-Aufschlag (Einstellungen → Aufschläge). Änderungen hier gelten nur für dieses Dokument.</p>
        </InfoButton>
    );
}

interface WeitereArtikelHolenProps {
    /** Läuft schon ein weiterer Shop-Besuch? Dann ist der Knopf gesperrt (mit Begründung). */
    wartet: boolean;
    onHolen: () => void;
    className?: string;
}

/** „Weitere Artikel aus dem Shop holen“ samt Erklärung – in der Werkzeugleiste und im Leerzustand. */
function WeitereArtikelHolen({ wartet, onHolen, className }: WeitereArtikelHolenProps) {
    return (
        <div className={cn('flex items-center gap-1', className)}>
            <Button
                variant="outline"
                size="sm"
                onClick={onHolen}
                disabled={wartet}
                title={wartet ? 'Der Shop ist schon offen – dort „Warenkorb übernehmen“ klicken oder oben abbrechen' : undefined}
            >
                <ShoppingCart aria-hidden="true" className="h-4 w-4" />
                Weitere Artikel aus dem Shop holen
            </Button>
            <InfoButton titel="Weitere Artikel holen">
                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                    <p>Das Programm öffnet den Shop erneut (neuer Besuch, wieder 60 Minuten gültig).</p>
                </InfoAbschnitt>
                <InfoAbschnitt titel="Was bewirkt das?">
                    <p>Die neuen Artikel kommen unten an die Liste dazu. Was du oben schon geändert hast, bleibt erhalten. Im Shop wird nichts bestellt.</p>
                </InfoAbschnitt>
            </InfoButton>
        </div>
    );
}

interface ShopOffenHinweisProps {
    lieferantName: string;
    /** Wird der Shop gerade erst geöffnet? */
    startet: boolean;
    onAbbrechen: () => void;
}

/** Gut sichtbarer Hinweis oben in „Preise prüfen“, solange ein weiterer Shop-Besuch läuft. */
function ShopOffenHinweis({ lieferantName, startet, onAbbrechen }: ShopOffenHinweisProps) {
    return (
        <div role="group" aria-label="Weiterer Shop-Besuch"
            className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900">
            <Loader2 aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600 motion-safe:animate-spin" />
            <p role="status" aria-live="polite" className="min-w-[16rem] flex-1">
                {startet
                    ? <>Der Shop von <strong>{lieferantName}</strong> wird geöffnet …</>
                    : <><strong>Der Shop von {lieferantName} ist offen</strong> – weitere Artikel dort zusammenstellen und „Warenkorb übernehmen“ klicken. Sie kommen dann unten an die Liste dazu.</>}
            </p>
            <Button variant="outline" size="sm" onClick={onAbbrechen}>Abbrechen</Button>
        </div>
    );
}

/** Alle Artikel herausgenommen: klar sagen, was jetzt geht. */
function LeereListe({ wartet, onHolen }: { wartet: boolean; onHolen: () => void }) {
    return (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center">
            <PackageOpen aria-hidden="true" className="h-8 w-8 text-slate-400" />
            <div>
                <p className="font-semibold text-slate-900">Keine Artikel mehr in der Liste</p>
                <p className="mt-1 text-sm text-slate-600">Hol weitere Artikel aus dem Shop – oder nimm einen gerade entfernten über „Rückgängig“ in der Meldung zurück.</p>
            </div>
            <WeitereArtikelHolen wartet={wartet} onHolen={onHolen} />
        </div>
    );
}

interface PreisTabelleProps {
    zeilen: Zeile[];
    onAendern: (schluessel: string, aenderung: Partial<Zeile>) => void;
    onEntfernen: (schluessel: string) => void;
}

function PreisTabelle({ zeilen, onAendern, onEntfernen }: PreisTabelleProps) {
    return (
        <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <tr>
                        <th scope="col" className="px-2 py-2 pl-3">Art.-Nr.</th>
                        <th scope="col" className="px-2 py-2">
                            Bezeichnung
                            <span className="block text-[11px] font-normal normal-case tracking-normal text-slate-400">Wird so ins Angebot übernommen – hier verständlich umformulieren.</span>
                        </th>
                        <th scope="col" className="w-24 px-2 py-2">Menge</th>
                        <th scope="col" className="px-2 py-2">Einheit</th>
                        <th scope="col" className="w-28 px-2 py-2">EK je Einheit</th>
                        <th scope="col" className="w-28 px-2 py-2">Aufschlag %</th>
                        <th scope="col" className="px-2 py-2 text-right">VK je Einheit</th>
                        <th scope="col" className="px-2 py-2 text-right">Summe VK</th>
                        <th scope="col" className="w-12 px-2 py-2"><span className="sr-only">Entfernen</span></th>
                    </tr>
                </thead>
                <tbody>
                    {zeilen.map((z) => {
                        const schluessel = zeilenSchluessel(z);
                        const ek = zahlAusEntwurf(z.einkaufspreis);
                        const aufschlag = zahlAusEntwurf(z.aufschlag);
                        const menge = zahlAusEntwurf(z.menge);
                        const vk = ek !== null && aufschlag !== null ? verkaufspreis(ek, aufschlag) : null;
                        const ohnePreis = z.position.einkaufspreis == null;
                        const name = z.bezeichnung || z.artikelnummer;
                        return (
                            <tr key={schluessel} className="border-t border-slate-100 align-top">
                                <td className="px-2 py-2 pl-3 pt-3.5 font-mono text-xs text-slate-600">{z.artikelnummer}</td>
                                <td className="min-w-[14rem] px-2 py-2">
                                    <Input value={z.bezeichnung} onChange={(e) => onAendern(schluessel, { bezeichnung: e.target.value })}
                                        aria-label={`Bezeichnung von Artikel ${z.artikelnummer}`}
                                        placeholder="Verständliche Bezeichnung für den Kunden" />
                                    {z.position.hinweis && (
                                        <p className="mt-1 flex items-start gap-1 text-xs text-amber-800">
                                            <AlertTriangle aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600" />
                                            <span><span className="font-semibold">Hinweis vom Shop:</span> {z.position.hinweis}</span>
                                        </p>
                                    )}
                                    {ohnePreis && (
                                        <p className="mt-1 flex items-start gap-1 text-xs font-medium text-amber-800">
                                            <AlertTriangle aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-600" />
                                            Kein Preis vom Shop – bitte Einkaufspreis eintragen
                                        </p>
                                    )}
                                </td>
                                <td className="px-2 py-2">
                                    <DecimalInput value={z.menge} onChange={(v) => onAendern(schluessel, { menge: v })}
                                        aria-label={`Menge von „${name}“`} className="text-right tabular-nums" />
                                </td>
                                <td className="px-2 py-2 pt-3.5 text-slate-600">{z.einheit}</td>
                                <td className="px-2 py-2">
                                    <DecimalInput value={z.einkaufspreis} onChange={(v) => onAendern(schluessel, { einkaufspreis: v })}
                                        aria-label={`Einkaufspreis von „${name}“`} placeholder="0,00"
                                        className={cn('text-right tabular-nums', ohnePreis && !z.einkaufspreis.trim() && 'border-amber-400 bg-amber-50')} />
                                </td>
                                <td className="px-2 py-2">
                                    <DecimalInput value={z.aufschlag} onChange={(v) => onAendern(schluessel, { aufschlag: v, aufschlagHinweis: 'von Hand' })}
                                        aria-label={`Aufschlag von „${name}“`} className="text-right tabular-nums" />
                                    <p className="mt-0.5 text-xs text-slate-500">{z.aufschlagHinweis}</p>
                                </td>
                                <td className="px-2 py-2 pt-3.5 text-right tabular-nums text-slate-700">{vk !== null ? formatCurrency(vk) : '–'}</td>
                                <td className="px-2 py-2 pt-3.5 text-right font-medium tabular-nums text-slate-900">
                                    {vk !== null && menge !== null ? formatCurrency(vk * menge) : '–'}
                                </td>
                                <td className="px-2 py-2 text-right">
                                    <Button variant="ghost" size="sm" onClick={() => onEntfernen(schluessel)}
                                        aria-label="Artikel aus der Liste entfernen" title={`„${name}“ aus der Liste entfernen`}
                                        className="h-9 w-9 px-0 text-slate-500 hover:bg-red-50 hover:text-red-600">
                                        <Trash2 aria-hidden="true" className="h-4 w-4" />
                                    </Button>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

// ---------- Schritt 4: Artikelstamm ----------

interface SchrittStammProps {
    zeilen: StammZeile[];
    speichert: boolean;
    onUmschalten: (schluessel: string, gewaehlt: boolean) => void;
    /** Öffnet das Anlegen-Fenster für diese Zeile. */
    onAnlegen: (schluessel: string) => void;
    onUeberspringen: () => void;
    onUebernehmen: () => void;
}

function SchrittStamm({ zeilen, speichert, onUmschalten, onAnlegen, onUeberspringen, onUebernehmen }: SchrittStammProps) {
    const etwasZuAktualisieren = zeilen.some((s) => s.aktion === 'aktualisieren');
    const etwasGewaehlt = zeilen.some((s) => s.aktion === 'aktualisieren' && s.gewaehlt);
    return (
        <>
            <div className="-m-1 min-h-0 flex-1 space-y-3 overflow-auto p-1">
                <div className="flex items-center gap-1">
                    <DialogDescription className="text-slate-600">
                        Die Positionen sind im Dokument. Sollen die Einkaufspreise auch im Artikelstamm landen?
                    </DialogDescription>
                    <InfoButton titel="Artikelstamm aktualisieren">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>„Preis aktualisieren“ legt beim Artikel einen neuen Einkaufspreis für diesen Lieferanten an; der alte bleibt im Preisverlauf. „Als Artikel anlegen…“ öffnet das gewohnte Anlegen-Fenster, vorausgefüllt mit Bezeichnung, Lieferanten-Nummer, Preis und Einheit – Kategorie und Beschreibung ergänzt du dort.</p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>Das Dokument ändert sich dadurch nicht mehr.</p>
                        </InfoAbschnitt>
                    </InfoButton>
                </div>
                <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-sm">
                        <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <tr>
                                <th scope="col" className="px-3 py-2">Art.-Nr.</th>
                                <th scope="col" className="px-3 py-2">Bezeichnung</th>
                                <th scope="col" className="px-3 py-2">Im Artikelstamm</th>
                                <th scope="col" className="px-3 py-2">Was tun?</th>
                            </tr>
                        </thead>
                        <tbody>
                            {zeilen.map((s) => {
                                const schluessel = zeilenSchluessel(s.zeile);
                                return (
                                    <tr key={schluessel} className="border-t border-slate-100">
                                        <td className="px-3 py-2 font-mono text-xs text-slate-600">{s.zeile.artikelnummer}</td>
                                        <td className="px-3 py-2 text-slate-900">{s.zeile.bezeichnung}</td>
                                        <td className="px-3 py-2 tabular-nums text-slate-700">
                                            {s.aktion === 'neu' && (s.angelegtAls === undefined
                                                ? <span className="text-slate-500">Noch nicht im Artikelstamm</span>
                                                : <span className="inline-flex items-center gap-1 font-medium text-emerald-700">
                                                    <Check aria-hidden="true" className="h-4 w-4" />
                                                    {s.angelegtAls ? `Angelegt als ${s.angelegtAls}` : 'Als Artikel angelegt'}
                                                </span>)}
                                            {s.aktion === 'unveraendert' && <span className="text-slate-500">Preis unverändert ({formatCurrency(s.zeile.einkaufspreis)})</span>}
                                            {s.aktion === 'aktualisieren' && (
                                                <span>Preis {s.alterPreis != null ? formatCurrency(s.alterPreis) : 'keiner'} → <strong className="text-slate-900">{formatCurrency(s.zeile.einkaufspreis)}</strong></span>
                                            )}
                                        </td>
                                        <td className="px-3 py-2">
                                            {s.aktion === 'aktualisieren' && (
                                                <label className="inline-flex cursor-pointer items-center gap-2 text-slate-700">
                                                    <input
                                                        type="checkbox"
                                                        checked={s.gewaehlt}
                                                        onChange={(e) => onUmschalten(schluessel, e.target.checked)}
                                                        className="h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                                    />
                                                    Preis aktualisieren
                                                </label>
                                            )}
                                            {s.aktion === 'neu' && s.angelegtAls === undefined && (
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => onAnlegen(schluessel)}
                                                    disabled={speichert}
                                                    aria-label={`Als Artikel anlegen… („${s.zeile.bezeichnung}“)`}
                                                >
                                                    <PackagePlus aria-hidden="true" className="h-4 w-4" />
                                                    Als Artikel anlegen…
                                                </Button>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            </div>
            <DialogFooter className="gap-2 border-t border-slate-200 pt-4">
                <Button variant={etwasZuAktualisieren ? 'outline' : 'default'} onClick={onUeberspringen} disabled={speichert}>
                    {etwasZuAktualisieren ? 'Überspringen' : 'Fertig'}
                </Button>
                {etwasZuAktualisieren && (
                    <Button
                        onClick={onUebernehmen}
                        disabled={speichert || !etwasGewaehlt}
                        title={!etwasGewaehlt ? 'Bitte mindestens einen Haken bei „Preis aktualisieren“ setzen – oder „Überspringen“' : undefined}
                    >
                        {speichert ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <Check aria-hidden="true" className="h-4 w-4" />}
                        {speichert ? 'Wird übernommen …' : 'Übernehmen'}
                    </Button>
                )}
            </DialogFooter>
        </>
    );
}
