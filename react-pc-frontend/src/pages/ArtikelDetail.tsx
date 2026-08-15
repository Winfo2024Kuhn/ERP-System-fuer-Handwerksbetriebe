import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft, Minus, Package, RefreshCw, Save, ShieldCheck, ShieldX,
    TrendingDown, TrendingUp
} from "lucide-react";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { PageLayout } from "../components/layout/PageLayout";
import { SupplierSelectModal } from "../components/SupplierSelectModal";
import { useToast } from "../components/ui/toast";
import { TiptapEditor } from "../components/TiptapEditor";
import { preisHinweisText, type PreisHinweis } from "../components/artikel/preisHinweis";
import { ArtikelDokumente } from "../components/artikel/ArtikelDokumente";
import type { Artikel, ArtikelDetail as ArtikelDetailData, ArtikelPreisstand } from "../types";

const formatCurrency = (val?: number) =>
    val === undefined || val === null
        ? "—"
        : new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(val);

const formatDate = (dateStr?: string) =>
    dateStr ? new Date(dateStr).toLocaleDateString("de-DE") : "—";

const formatZahl = (val?: number, nachkomma = 3) =>
    val === undefined || val === null
        ? "—"
        : new Intl.NumberFormat("de-DE", { maximumFractionDigits: nachkomma }).format(val);

/**
 * Ein Rich-Text ohne sichtbaren Inhalt gilt als leer und wird als `null`
 * gesendet - sonst bleibt ein geleertes Beschreibungsfeld "gepflegt", weil
 * Tiptap beim Leeren typischerweise "<p></p>" liefert statt eines leeren
 * Strings. Zaehlt nicht als Inhalt: Tags, "&nbsp;" (als Entity oder echtes
 * Zeichen) und gewoehnliche Leerzeichen.
 */
function richtextOderNull(html: string): string | null {
    const sichtbarerText = html
        .replace(/<[^>]*>/g, "")
        .replace(/&nbsp;/gi, " ")
        .replace(/\u00a0/g, " ")
        .trim();
    return sichtbarerText === "" ? null : html;
}

/**
 * Detailseite eines Artikels.
 *
 * Zeigt technische Daten, alle Lieferanten mit ihrer eigenen Artikelnummer und
 * den Preisverlauf. Preise koennen hier von Hand nachgetragen werden - etwa nach
 * einem Telefonat, wenn kein Angebot per Mail vorliegt.
 */
export default function ArtikelDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const toast = useToast();

    /**
     * Zurueck zur Artikelliste.
     *
     * Kam man aus der Liste, geht es einen Schritt in der History zurueck: Die
     * Liste haelt ihre Suche in der URL, also stehen Filter und Treffer wieder
     * genau so da, wie man sie verlassen hat. Ein festes navigate("/artikel")
     * wuerde die muehsam eingegrenzte Suche stattdessen wegwerfen.
     * Wer die Seite direkt geoeffnet hat (Link, Lesezeichen), hat keinen solchen
     * Schritt in der History - fuer den fuehrt der Weg auf die volle Liste.
     */
    const vonListe = Boolean((location.state as { vonListe?: boolean } | null)?.vonListe);
    const zurueckZurListe = useCallback(() => {
        if (vonListe) navigate(-1);
        else navigate("/artikel");
    }, [vonListe, navigate]);

    const [daten, setDaten] = useState<ArtikelDetailData | null>(null);
    const [loading, setLoading] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);

    // Preis-Eingabe
    const [zeigeLieferantenWahl, setZeigeLieferantenWahl] = useState(false);
    const [neuerPreis, setNeuerPreis] = useState({
        lieferantId: 0,
        lieferantName: "",
        externeArtikelnummer: "",
        preis: "",
        notiz: "",
    });
    const [speichert, setSpeichert] = useState(false);

    // Angebotsfelder
    const [kurzbeschreibung, setKurzbeschreibung] = useState("");
    const [beschreibung, setBeschreibung] = useState("");
    const [aufschlag, setAufschlag] = useState<string>("");
    const [speichern, setSpeichern] = useState(false);

    const laden = useCallback(async () => {
        if (!id) return;
        setLoading(true);
        setFehler(null);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(id)}/detail`);
            if (res.status === 404) {
                setFehler("Dieser Artikel wurde nicht gefunden.");
                setDaten(null);
                return;
            }
            if (!res.ok) throw new Error("Laden fehlgeschlagen");
            setDaten(await res.json());
        } catch (err) {
            console.error(err);
            setFehler("Die Artikeldaten konnten nicht geladen werden.");
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => { laden(); }, [laden]);

    /**
     * Merkt sich, fuer welche Artikel-ID die Angebotsfelder zuletzt aus den
     * Serverdaten vorbelegt wurden. `daten` wechselt nicht nur beim
     * Erstladen - auch "Aktualisieren" und ein gespeicherter Lieferantenpreis
     * loesen `laden()` erneut aus. Ohne diese Bremse wuerde jedes Neuladen
     * eine noch ungespeicherte Eingabe kommentarlos verwerfen.
     */
    const vorbelegteArtikelId = useRef<number | null>(null);

    // Beim ersten Laden eines Artikels die Angebotsfelder vorbelegen -
    // danach nicht mehr, damit ungespeicherte Eingaben ein Neuladen ueberleben.
    useEffect(() => {
        if (!daten?.artikel) return;
        if (vorbelegteArtikelId.current === daten.artikel.id) return;
        vorbelegteArtikelId.current = daten.artikel.id;
        setKurzbeschreibung(daten.artikel.kurzbeschreibung ?? "");
        setBeschreibung(daten.artikel.beschreibung ?? "");
        setAufschlag(
            daten.artikel.verkaufsaufschlagProzent == null
                ? ""
                : String(daten.artikel.verkaufsaufschlagProzent),
        );
    }, [daten]);

    const artikel = daten?.artikel;
    const hinweis = (daten?.artikel.preisHinweis ?? "KEIN_PREIS") as PreisHinweis;

    const preisSpeichern = async () => {
        if (!id || !neuerPreis.lieferantId) {
            toast.error("Bitte zuerst einen Lieferanten auswählen.");
            return;
        }
        const betrag = Number(neuerPreis.preis.replace(",", "."));
        if (!Number.isFinite(betrag) || betrag < 0) {
            toast.error("Bitte einen gültigen Preis eingeben.");
            return;
        }
        setSpeichert(true);
        try {
            const res = await fetch(
                `/api/lieferanten/${encodeURIComponent(neuerPreis.lieferantId)}/artikelpreise`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        artikelId: Number(id),
                        preis: betrag,
                        externeArtikelnummer: neuerPreis.externeArtikelnummer || null,
                    }),
                },
            );
            if (!res.ok) throw new Error("Speichern fehlgeschlagen");
            toast.success("Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.");
            setNeuerPreis({ lieferantId: 0, lieferantName: "", externeArtikelnummer: "", preis: "", notiz: "" });
            await laden();
        } catch (err) {
            console.error(err);
            toast.error("Der Preis konnte nicht gespeichert werden.");
        } finally {
            setSpeichert(false);
        }
    };

    const speichereAngebotsfelder = async () => {
        if (!id) return;
        setSpeichern(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(id)}/dokumenttexte`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    kurzbeschreibung: kurzbeschreibung || null,
                    beschreibung: richtextOderNull(beschreibung),
                    verkaufsaufschlagProzent: aufschlag === "" ? null : Number(aufschlag),
                }),
            });
            if (!res.ok) throw new Error("Speichern fehlgeschlagen");
            // Die Antwort ist der frisch gespeicherte Artikel - das ist der
            // Server-Stand, mit dem die Felder jetzt uebereinstimmen sollen.
            // Ein Ueberschreiben ist hier richtig: Der Bediener hat genau das
            // ausgeloest. Die ID bleibt gleich, darum wuerde der Vorbelege-
            // Effekt oben sonst nicht erneut greifen.
            const aktualisiert: Artikel = await res.json();
            vorbelegteArtikelId.current = aktualisiert.id;
            setKurzbeschreibung(aktualisiert.kurzbeschreibung ?? "");
            setBeschreibung(aktualisiert.beschreibung ?? "");
            setAufschlag(
                aktualisiert.verkaufsaufschlagProzent == null
                    ? ""
                    : String(aktualisiert.verkaufsaufschlagProzent),
            );
            toast.success("Die Angebotsfelder sind aktualisiert.");
            await laden();
        } catch (err) {
            console.error(err);
            toast.error("Die Angebotsfelder konnten nicht gespeichert werden.");
        } finally {
            setSpeichern(false);
        }
    };

    if (loading) {
        return (
            <PageLayout ribbonCategory="Stammdaten" title="Artikel">
                <div className="animate-pulse space-y-4" aria-busy="true" aria-label="Artikel wird geladen">
                    <div className="h-28 bg-slate-100 rounded-lg" />
                    <div className="h-56 bg-slate-100 rounded-lg" />
                </div>
            </PageLayout>
        );
    }

    if (fehler || !artikel) {
        return (
            <PageLayout ribbonCategory="Stammdaten" title="Artikel">
                <div className="text-center py-16">
                    <Package className="w-12 h-12 mx-auto text-slate-300 mb-3" aria-hidden="true" />
                    <p className="text-slate-600">{fehler ?? "Artikel nicht gefunden."}</p>
                    <Button className="mt-4 border-rose-300 text-rose-700 hover:bg-rose-50"
                        variant="outline" size="sm" onClick={zurueckZurListe}>
                        <ArrowLeft className="w-4 h-4 mr-2" aria-hidden="true" />
                        Zurück zur Artikelliste
                    </Button>
                </div>
            </PageLayout>
        );
    }

    return (
        <PageLayout
            ribbonCategory={artikel.kategoriePfad || "Stammdaten"}
            title={artikel.produktname}
            subtitle={[artikel.artikelnummer, artikel.werkstoffName].filter(Boolean).join(" · ")}
            actions={
                <div className="flex gap-2">
                    <Button variant="outline" size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={zurueckZurListe}>
                        <ArrowLeft className="w-4 h-4 mr-2" aria-hidden="true" />
                        Zurück
                    </Button>
                    <Button variant="outline" size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={laden}>
                        <RefreshCw className="w-4 h-4 mr-2" aria-hidden="true" />
                        Aktualisieren
                    </Button>
                </div>
            }
        >
            {/* ============ Was ist das für ein Teil? ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-4">Was ist das für ein Teil?</h2>

                <div className="flex flex-wrap gap-2 mb-5">
                    {artikel.profilform && <Merkmalchip text={artikel.profilform.anzeigename} titel={artikel.profilform.erklaerung} />}
                    {artikel.herstellverfahren && <Merkmalchip text={artikel.herstellverfahren.anzeigename} titel={artikel.herstellverfahren.erklaerung} />}
                    {artikel.fertigungszustand && <Merkmalchip text={artikel.fertigungszustand.anzeigename} titel={artikel.fertigungszustand.erklaerung} />}
                    {artikel.werkstoffName && <Merkmalchip text={artikel.werkstoffName} />}
                </div>

                <dl className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 text-sm">
                    <Datenfeld label="Abmessung" wert={artikel.abmessung} />
                    <Datenfeld label="Durchmesser" wert={artikel.durchmesser ? `${formatZahl(artikel.durchmesser, 1)} mm` : undefined} />
                    <Datenfeld label="Wandstärke" wert={artikel.wandstaerke ? `${formatZahl(artikel.wandstaerke, 1)} mm` : undefined} />
                    <Datenfeld label="Höhe × Breite" wert={artikel.hoehe && artikel.breite
                        ? `${formatZahl(artikel.hoehe, 1)} × ${formatZahl(artikel.breite, 1)} mm` : undefined} />
                    <Datenfeld label="Gewicht je Meter" wert={artikel.kgProMeter ? `${formatZahl(artikel.kgProMeter)} kg/m` : undefined} />
                    <Datenfeld label="Gewicht je m²" wert={artikel.kgProQm ? `${formatZahl(artikel.kgProQm)} kg/m²` : undefined} />
                    <Datenfeld label="Oberfläche je Meter" wert={artikel.mantelflaeche ? `${formatZahl(artikel.mantelflaeche, 4)} m²/m` : undefined}
                        hinweis="Wird für die Kosten von Pulverbeschichtung und Anstrich gebraucht." />
                    <Datenfeld label="Übliche Länge" wert={artikel.standardlaenge ? `${formatZahl(artikel.standardlaenge, 0)} mm` : undefined} />
                    <Datenfeld label="Maßnorm" wert={artikel.massnorm} />
                    <Datenfeld label="Werkstoffnorm" wert={artikel.werkstoffnorm} />
                </dl>
            </section>

            {/* ============ Bilder & Unterlagen ============ */}
            <ArtikelDokumente artikelId={artikel.id} />

            {/* ============ Oberflächenbehandlung ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-1">Oberfläche</h2>
                <p className="text-sm text-slate-500 mb-4">
                    Was mit diesem Werkstoff möglich ist — wichtig für die Kalkulation.
                </p>
                <div className="flex flex-col sm:flex-row gap-3">
                    <Eignung ok={!!artikel.verzinkungsgeeignet} text="Feuerverzinken" />
                    <Eignung ok={!!artikel.pulverbeschichtungsgeeignet} text="Pulverbeschichten" />
                </div>
                {artikel.beschichtungshinweis && (
                    <p className="text-sm text-slate-600 mt-3">{artikel.beschichtungshinweis}</p>
                )}
            </section>

            {/* ============ Für Angebote und Rechnungen ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-1">Für Angebote und Rechnungen</h2>
                <p className="text-sm text-slate-500 mb-4">
                    Was der Kunde sieht, wenn dieses Material in einem Angebot steht.
                </p>

                <div className="space-y-4">
                    <div className="space-y-1.5">
                        <Label htmlFor="kurzbeschreibung">Kurzbeschreibung</Label>
                        <Input
                            id="kurzbeschreibung"
                            value={kurzbeschreibung}
                            onChange={(e) => setKurzbeschreibung(e.target.value)}
                            maxLength={255}
                            placeholder="z.B. T-Stahl 40x40 Lager"
                        />
                        <p className="text-xs text-slate-400">Nur für dich — steht nicht auf dem Angebot.</p>
                    </div>

                    <div className="space-y-1.5">
                        <Label>Beschreibung</Label>
                        <TiptapEditor value={beschreibung} onChange={setBeschreibung} />
                        <p className="text-xs text-slate-400">
                            Das liest der Kunde auf Angebot, Rechnung und Freigabe-Seite.
                        </p>
                    </div>

                    <div className="space-y-1.5 max-w-xs">
                        <Label htmlFor="aufschlag">Aufschlag auf den Einkaufspreis</Label>
                        <div className="relative">
                            <Input
                                id="aufschlag"
                                type="number"
                                min={0}
                                max={999.99}
                                step={0.01}
                                value={aufschlag}
                                onChange={(e) => setAufschlag(e.target.value)}
                                className="pr-8"
                            />
                            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">%</span>
                        </div>
                    </div>

                    {hinweis === "OK" ? (
                        <p className="text-sm text-slate-600">
                            So steht es im Angebot:{" "}
                            <span className="font-semibold text-slate-900">
                                1 {daten.artikel.positionsEinheit} à {formatCurrency(daten.artikel.positionsEinzelpreis)}
                            </span>
                        </p>
                    ) : (
                        <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                            {preisHinweisText[hinweis]}
                        </p>
                    )}

                    <Button
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        size="sm"
                        onClick={speichereAngebotsfelder}
                        disabled={speichern}
                    >
                        <Save className="w-4 h-4" aria-hidden="true" /> Angebotsfelder speichern
                    </Button>
                </div>
            </section>

            {/* ============ Wer liefert das? ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <div className="flex flex-wrap items-end justify-between gap-3 mb-4">
                    <div>
                        <h2 className="text-lg font-semibold text-slate-900">Wer liefert das?</h2>
                        <p className="text-sm text-slate-500">
                            Jeder Lieferant führt den Artikel unter seiner eigenen Nummer.
                        </p>
                    </div>
                </div>

                {daten.lieferanten.length === 0 ? (
                    <p className="text-sm text-slate-500 py-6 text-center">
                        Für diesen Artikel ist noch kein Lieferantenpreis hinterlegt.
                        Unten können Sie einen eintragen.
                    </p>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <caption className="sr-only">
                                Lieferanten mit Artikelnummer und aktuellem Preis, günstigster zuerst
                            </caption>
                            <thead>
                                <tr className="text-left text-slate-500 border-b border-slate-200">
                                    <th scope="col" className="py-2 pr-4 font-medium">Lieferant</th>
                                    <th scope="col" className="py-2 pr-4 font-medium">Dessen Artikelnummer</th>
                                    <th scope="col" className="py-2 pr-4 font-medium text-right">Preis</th>
                                    <th scope="col" className="py-2 pr-4 font-medium text-right">Unterschied</th>
                                    <th scope="col" className="py-2 pr-4 font-medium">Stand</th>
                                    <th scope="col" className="py-2 font-medium">Herkunft</th>
                                </tr>
                            </thead>
                            <tbody>
                                {daten.lieferanten.map((l) => (
                                    <tr key={l.preisId} className="border-b border-slate-100 last:border-0">
                                        <td className="py-3 pr-4">
                                            <span className="font-medium text-slate-900">{l.lieferantName}</span>
                                            {l.guenstigster && (
                                                // Nicht nur Farbe: Text plus Symbol kennzeichnen den guenstigsten.
                                                <span className="ml-2 inline-flex items-center gap-1 text-xs font-semibold
                                                    text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-1.5 py-0.5">
                                                    <TrendingDown className="w-3 h-3" aria-hidden="true" />
                                                    günstigster
                                                </span>
                                            )}
                                        </td>
                                        <td className="py-3 pr-4 text-slate-600">{l.externeArtikelnummer || "—"}</td>
                                        <td className="py-3 pr-4 text-right tabular-nums font-medium text-slate-900">
                                            {formatCurrency(l.preis)}
                                        </td>
                                        <td className="py-3 pr-4 text-right tabular-nums text-slate-600">
                                            {l.guenstigster
                                                ? <span className="text-slate-400">—</span>
                                                : l.aufschlagProzent !== undefined
                                                    ? <span className="text-amber-700">+{formatZahl(l.aufschlagProzent, 1)} %</span>
                                                    : "—"}
                                        </td>
                                        <td className="py-3 pr-4 text-slate-600">{formatDate(l.preisDatum)}</td>
                                        <td className="py-3 text-slate-500">{l.quelle?.anzeigename ?? "—"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* ---- Preis von Hand nachtragen ---- */}
                <div className="mt-6 pt-5 border-t border-slate-200">
                    <h3 className="text-sm font-semibold text-slate-900 mb-3">Preis nachtragen</h3>
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
                        <div>
                            <Label htmlFor="lieferant-wahl">Lieferant</Label>
                            <Button
                                id="lieferant-wahl"
                                type="button"
                                variant="outline"
                                className="w-full justify-start min-h-[44px] border-slate-300 text-slate-700 hover:bg-slate-50"
                                onClick={() => setZeigeLieferantenWahl(true)}
                            >
                                {neuerPreis.lieferantName || "Lieferant auswählen"}
                            </Button>
                        </div>
                        <div>
                            <Label htmlFor="externe-nr">Dessen Artikelnummer</Label>
                            <Input id="externe-nr" className="min-h-[44px]"
                                value={neuerPreis.externeArtikelnummer}
                                onChange={(e) => setNeuerPreis((p) => ({ ...p, externeArtikelnummer: e.target.value }))}
                                placeholder="z. B. 100234" />
                        </div>
                        <div>
                            <Label htmlFor="preis-eingabe">Preis in Euro</Label>
                            <Input id="preis-eingabe" inputMode="decimal" className="min-h-[44px]"
                                value={neuerPreis.preis}
                                onChange={(e) => setNeuerPreis((p) => ({ ...p, preis: e.target.value }))}
                                placeholder="z. B. 12,50" />
                            <p className="text-xs text-slate-500 mt-1">
                                Der bisherige Preis bleibt im Verlauf erhalten.
                            </p>
                        </div>
                        <Button
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 min-h-[44px]"
                            size="sm"
                            disabled={speichert}
                            onClick={preisSpeichern}
                        >
                            {speichert
                                ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" aria-hidden="true" />
                                : <Save className="w-4 h-4 mr-2" aria-hidden="true" />}
                            Speichern
                        </Button>
                    </div>
                </div>
            </section>

            {/* ============ Preisverlauf ============ */}
            <section className="bg-white border border-slate-200 rounded-lg p-5">
                <h2 className="text-lg font-semibold text-slate-900 mb-1">Preisverlauf</h2>
                <p className="text-sm text-slate-500 mb-4">
                    Wie sich der Preis über die Zeit entwickelt hat, je Lieferant.
                </p>
                <Preisverlauf staende={daten.preisverlauf} />
            </section>

            {zeigeLieferantenWahl && (
                <SupplierSelectModal
                    onClose={() => setZeigeLieferantenWahl(false)}
                    onSelect={(lieferant: { id: number; name: string }) => {
                        setNeuerPreis((p) => ({ ...p, lieferantId: lieferant.id, lieferantName: lieferant.name }));
                        setZeigeLieferantenWahl(false);
                    }}
                />
            )}
        </PageLayout>
    );
}

// ==================== Bausteine ====================

function Merkmalchip({ text, titel }: { text: string; titel?: string }) {
    return (
        <span title={titel}
            className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50
                       px-3 py-1 text-sm text-slate-700">
            {text}
        </span>
    );
}

function Datenfeld({ label, wert, hinweis }: { label: string; wert?: string | null; hinweis?: string }) {
    if (!wert) return null;
    return (
        <div>
            <dt className="text-slate-500" title={hinweis}>{label}</dt>
            <dd className="font-medium text-slate-900 tabular-nums">{wert}</dd>
        </div>
    );
}

/** Eignung mit Symbol UND Text - Farbe allein darf die Aussage nicht tragen. */
function Eignung({ ok, text }: { ok: boolean; text: string }) {
    return (
        <div className={`flex items-center gap-2 rounded-lg border px-4 py-3 ${
            ok ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50"
        }`}>
            {ok
                ? <ShieldCheck className="w-5 h-5 text-emerald-700 shrink-0" aria-hidden="true" />
                : <ShieldX className="w-5 h-5 text-slate-500 shrink-0" aria-hidden="true" />}
            <div>
                <p className={`text-sm font-semibold ${ok ? "text-emerald-800" : "text-slate-700"}`}>{text}</p>
                <p className={`text-xs ${ok ? "text-emerald-700" : "text-slate-500"}`}>
                    {ok ? "möglich" : "nicht möglich"}
                </p>
            </div>
        </div>
    );
}

/**
 * Preisverlauf als Liniendiagramm je Lieferant, ergaenzt um eine Tabelle.
 *
 * Die Tabelle ist nicht nur Beiwerk: Ein Diagramm allein ist fuer Screenreader
 * nicht lesbar, und die genauen Werte sind ohnehin oft die eigentliche Frage.
 */
function Preisverlauf({ staende }: { staende: ArtikelPreisstand[] }) {
    const mitDatum = useMemo(
        () => staende.filter((s) => s.preisDatum && s.preis !== undefined && s.preis !== null),
        [staende],
    );

    const serien = useMemo(() => {
        const map = new Map<string, ArtikelPreisstand[]>();
        for (const s of mitDatum) {
            const key = s.lieferantName ?? "Ohne Lieferant";
            if (!map.has(key)) map.set(key, []);
            map.get(key)!.push(s);
        }
        for (const liste of map.values()) {
            liste.sort((a, b) => new Date(a.preisDatum!).getTime() - new Date(b.preisDatum!).getTime());
        }
        return [...map.entries()];
    }, [mitDatum]);

    if (mitDatum.length === 0) {
        return (
            <p className="text-sm text-slate-500 py-6 text-center">
                Noch keine Preisänderungen erfasst. Sobald ein Preis geändert wird,
                erscheint hier der Verlauf.
            </p>
        );
    }

    // Achsenbereiche
    const zeiten = mitDatum.map((s) => new Date(s.preisDatum!).getTime());
    const preise = mitDatum.map((s) => s.preis!);
    const tMin = Math.min(...zeiten);
    const tMax = Math.max(...zeiten);
    const pMin = Math.min(...preise);
    const pMax = Math.max(...preise);
    // Etwas Luft nach oben und unten, damit Punkte nicht am Rand kleben.
    const pSpanne = pMax - pMin || Math.max(pMax * 0.1, 1);
    const yMin = Math.max(0, pMin - pSpanne * 0.15);
    const yMax = pMax + pSpanne * 0.15;

    const B = 720, H = 240, padL = 64, padR = 16, padT = 16, padB = 36;
    const x = (t: number) => tMax === tMin
        ? padL + (B - padL - padR) / 2
        : padL + ((t - tMin) / (tMax - tMin)) * (B - padL - padR);
    const y = (p: number) => padT + (1 - (p - yMin) / (yMax - yMin || 1)) * (H - padT - padB);

    // Kontraststarke, gut unterscheidbare Farben (nicht rot/gruen als Paar).
    const farben = ["#dc2626", "#0f766e", "#7c3aed", "#b45309", "#1d4ed8", "#be185d"];

    return (
        <div>
            <div className="overflow-x-auto">
                <svg viewBox={`0 0 ${B} ${H}`} className="w-full min-w-[560px] h-auto"
                    role="img"
                    aria-label={`Preisverlauf von ${formatCurrency(pMin)} bis ${formatCurrency(pMax)} über ${serien.length} Lieferanten`}>
                    {/* Waagerechte Hilfslinien mit Preisbeschriftung */}
                    {[0, 0.25, 0.5, 0.75, 1].map((anteil) => {
                        const wert = yMin + (yMax - yMin) * (1 - anteil);
                        const py = padT + anteil * (H - padT - padB);
                        return (
                            <g key={anteil}>
                                <line x1={padL} y1={py} x2={B - padR} y2={py} stroke="#e2e8f0" strokeWidth="1" />
                                <text x={padL - 8} y={py + 4} textAnchor="end"
                                    className="fill-slate-500" style={{ fontSize: 11 }}>
                                    {new Intl.NumberFormat("de-DE", {
                                        style: "currency", currency: "EUR", maximumFractionDigits: 2,
                                    }).format(wert)}
                                </text>
                            </g>
                        );
                    })}

                    {/* Zeitachse */}
                    <line x1={padL} y1={H - padB} x2={B - padR} y2={H - padB} stroke="#94a3b8" strokeWidth="1" />
                    <text x={padL} y={H - padB + 18} className="fill-slate-500" style={{ fontSize: 11 }}>
                        {new Date(tMin).toLocaleDateString("de-DE")}
                    </text>
                    <text x={B - padR} y={H - padB + 18} textAnchor="end"
                        className="fill-slate-500" style={{ fontSize: 11 }}>
                        {new Date(tMax).toLocaleDateString("de-DE")}
                    </text>

                    {/* Eine Linie je Lieferant */}
                    {serien.map(([name, punkte], i) => {
                        const farbe = farben[i % farben.length];
                        const d = punkte
                            .map((p, idx) => `${idx === 0 ? "M" : "L"} ${x(new Date(p.preisDatum!).getTime())} ${y(p.preis!)}`)
                            .join(" ");
                        return (
                            <g key={name}>
                                {punkte.length > 1 && (
                                    <path d={d} fill="none" stroke={farbe} strokeWidth="2"
                                        strokeLinejoin="round" strokeLinecap="round" />
                                )}
                                {punkte.map((p) => (
                                    <circle key={p.preisId}
                                        cx={x(new Date(p.preisDatum!).getTime())}
                                        cy={y(p.preis!)}
                                        r={p.aktuell ? 5 : 3.5}
                                        fill={p.aktuell ? farbe : "#ffffff"}
                                        stroke={farbe} strokeWidth="2">
                                        <title>
                                            {name}: {formatCurrency(p.preis)} am {formatDate(p.preisDatum)}
                                            {p.aktuell ? " (gilt aktuell)" : ""}
                                        </title>
                                    </circle>
                                ))}
                            </g>
                        );
                    })}
                </svg>
            </div>

            {/* Legende */}
            <ul className="flex flex-wrap gap-x-5 gap-y-2 mt-3">
                {serien.map(([name], i) => (
                    <li key={name} className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="inline-block w-4 h-1 rounded-full"
                            style={{ backgroundColor: farben[i % farben.length] }} aria-hidden="true" />
                        {name}
                    </li>
                ))}
            </ul>

            {/* Werte-Tabelle als barrierefreie Entsprechung des Diagramms */}
            <div className="overflow-x-auto mt-5">
                <table className="w-full text-sm">
                    <caption className="sr-only">Alle Preisstände mit Datum, Lieferant und Herkunft</caption>
                    <thead>
                        <tr className="text-left text-slate-500 border-b border-slate-200">
                            <th scope="col" className="py-2 pr-4 font-medium">Datum</th>
                            <th scope="col" className="py-2 pr-4 font-medium">Lieferant</th>
                            <th scope="col" className="py-2 pr-4 font-medium text-right">Preis</th>
                            <th scope="col" className="py-2 pr-4 font-medium text-right">Änderung</th>
                            <th scope="col" className="py-2 pr-4 font-medium">Herkunft</th>
                            <th scope="col" className="py-2 font-medium">Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        {staende.map((s, idx) => {
                            // Vorheriger Stand desselben Lieferanten fuer die Veraenderung.
                            const vorher = staende
                                .slice(idx + 1)
                                .find((v) => v.lieferantId === s.lieferantId && v.preis !== undefined);
                            const diff = vorher?.preis !== undefined && s.preis !== undefined
                                ? s.preis - vorher.preis
                                : undefined;
                            return (
                                <tr key={s.preisId} className="border-b border-slate-100 last:border-0">
                                    <td className="py-2.5 pr-4 text-slate-600">{formatDate(s.preisDatum)}</td>
                                    <td className="py-2.5 pr-4 text-slate-900">{s.lieferantName ?? "—"}</td>
                                    <td className="py-2.5 pr-4 text-right tabular-nums font-medium text-slate-900">
                                        {formatCurrency(s.preis)}
                                    </td>
                                    <td className="py-2.5 pr-4 text-right tabular-nums">
                                        {diff === undefined ? (
                                            <span className="text-slate-400">—</span>
                                        ) : diff > 0 ? (
                                            <span className="inline-flex items-center gap-1 text-amber-700">
                                                <TrendingUp className="w-3.5 h-3.5" aria-hidden="true" />
                                                +{formatCurrency(diff)}
                                            </span>
                                        ) : diff < 0 ? (
                                            <span className="inline-flex items-center gap-1 text-emerald-700">
                                                <TrendingDown className="w-3.5 h-3.5" aria-hidden="true" />
                                                {formatCurrency(diff)}
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center gap-1 text-slate-500">
                                                <Minus className="w-3.5 h-3.5" aria-hidden="true" />
                                                unverändert
                                            </span>
                                        )}
                                    </td>
                                    <td className="py-2.5 pr-4 text-slate-500">{s.quelle?.anzeigename ?? "—"}</td>
                                    <td className="py-2.5 text-slate-600">
                                        {s.aktuell
                                            ? <span className="text-xs font-semibold text-slate-900">gilt aktuell</span>
                                            : <span className="text-xs text-slate-400">abgelöst</span>}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
