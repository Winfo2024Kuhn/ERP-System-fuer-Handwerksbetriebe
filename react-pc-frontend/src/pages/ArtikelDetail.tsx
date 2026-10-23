import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Euro, FileText, Images, LayoutDashboard, Package, RefreshCw, Ruler } from "lucide-react";
import { Button } from "../components/ui/button";
import { PageLayout } from "../components/layout/PageLayout";
import { Reiter, ReiterPanel, type ReiterEintrag } from "../components/ui/reiter";
import { useToast } from "../components/ui/toast";
import { ArtikelDokumente } from "../components/artikel/ArtikelDokumente";
import { UebersichtReiter, type ArtikelReiterId } from "../components/artikel/detail/UebersichtReiter";
import { PreiseReiter } from "../components/artikel/detail/PreiseReiter";
import { AngabenReiter } from "../components/artikel/detail/AngabenReiter";
import { AngeboteReiter, type Angebotsfelder } from "../components/artikel/detail/AngeboteReiter";
import { zahlAlsEingabe } from "../components/artikel/detail/format";
import { stripHtmlTags } from "../lib/htmlSanitizer";
import { parseDecimal } from "../components/verrechnungslohnFormat";
import { cn } from "../lib/utils";
import type { Artikel, ArtikelDetail as ArtikelDetailData } from "../types";

/**
 * Ein Rich-Text ohne sichtbaren Inhalt gilt als leer und wird als `null`
 * gesendet - sonst bleibt ein geleertes Beschreibungsfeld "gepflegt", weil
 * Tiptap beim Leeren typischerweise "<p></p>" liefert statt eines leeren
 * Strings. Zaehlt nicht als Inhalt: Tags, "&nbsp;" (als Entity oder echtes
 * Zeichen) und gewoehnliche Leerzeichen.
 */
function richtextOderNull(html: string): string | null {
    return sichtbarerText(html) === "" ? null : html;
}

function sichtbarerText(html: string): string {
    return stripHtmlTags(html)
        .replace(/&nbsp;/gi, " ")
        .replace(/\u00a0/g, " ")
        .trim();
}

const REITER_IDS: ArtikelReiterId[] = ["uebersicht", "preise", "angaben", "angebote", "bilder"];

/** Der offene Reiter steht als Anker in der Adresse (#preise ...) - die Übersicht braucht keinen. */
function reiterAusAnker(anker: string): ArtikelReiterId {
    const roh = anker.replace(/^#/, "");
    return (REITER_IDS as string[]).includes(roh) ? (roh as ArtikelReiterId) : "uebersicht";
}

const angebotsfelderAus = (a: Artikel): Angebotsfelder => ({
    kurzbeschreibung: a.kurzbeschreibung ?? "",
    beschreibung: a.beschreibung ?? "",
    aufschlag: zahlAlsEingabe(a.verkaufsaufschlagProzent),
});

/**
 * Detailseite eines Artikels, aufgeteilt in Reiter wie die Einstellungen:
 * Übersicht, Preise & Lieferanten, Angaben zum Teil, Für Angebote sowie
 * Bilder & Unterlagen. Preise koennen in der Uebersicht und im Preis-Reiter
 * direkt in der Zeile geaendert werden - etwa nach einem Telefonat, wenn kein
 * Angebot per Mail vorliegt.
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

    /**
     * Reiterwechsel ersetzt nur den Anker der Adresse (kein neuer
     * History-Eintrag): "Zurueck" fuehrt weiterhin zur Liste, und der
     * mitgegebene Zustand (kam aus der Liste) bleibt erhalten.
     */
    const aktiverReiter = reiterAusAnker(location.hash);
    const wechsleReiter = useCallback((reiter: ArtikelReiterId) => {
        navigate(
            { pathname: location.pathname, search: location.search, hash: reiter === "uebersicht" ? "" : `#${reiter}` },
            { replace: true, state: location.state },
        );
    }, [navigate, location.pathname, location.search, location.state]);

    const [daten, setDaten] = useState<ArtikelDetailData | null>(null);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);
    /** Fuer welchen Artikel schon Daten da sind - dann ist ein weiteres Laden nur ein Auffrischen. */
    const geladeneId = useRef<string | null>(null);
    const [datenFuer, setDatenFuer] = useState<string | null>(null);

    // Angebotsfelder
    const [angebotsfelder, setAngebotsfelder] = useState<Angebotsfelder>({ kurzbeschreibung: "", beschreibung: "", aufschlag: "" });
    /** Zuletzt vom Server uebernommener Stand der Angebotsfelder - daran misst sich "nicht gespeichert". */
    const [angebotsStand, setAngebotsStand] = useState<Angebotsfelder | null>(null);
    const [speichern, setSpeichern] = useState(false);
    // Stammdaten bearbeiten (Name, Nummern, Einheit, Kategorie, Maße ...)
    const [stammdatenBearbeiten, setStammdatenBearbeiten] = useState(false);

    // Ergebnisse erst in den Promise-Callbacks übernehmen, nicht synchron im Effekt.
    // Den Ladezustand schalten die Auslöser an: `laden` (Knöpfe, nach dem
    // Speichern) bzw. die Render-Anpassung bei einem anderen Artikel.
    const ladeDaten = useCallback(() => {
        if (!id) return Promise.resolve();
        // Beim Auffrischen bleibt die Seite stehen (kein Skeleton, Reiter und
        // Eingaben bleiben); nur ein Fehler wird gemeldet.
        const auffrischen = geladeneId.current === id;
        return fetch(`/api/artikel/${encodeURIComponent(id)}/detail`)
            .then(async (res) => {
                if (res.status === 404) {
                    geladeneId.current = null;
                    setFehler("Dieser Artikel wurde nicht gefunden.");
                    setDaten(null);
                    return;
                }
                if (!res.ok) throw new Error("Laden fehlgeschlagen");
                setDaten(await res.json());
                setDatenFuer(id);
                setFehler(null);
                geladeneId.current = id;
            })
            .catch((err: unknown) => {
                console.error(err);
                if (auffrischen) toast.error("Die Artikeldaten konnten nicht neu geladen werden.");
                else setFehler("Die Artikeldaten konnten nicht geladen werden.");
            })
            .finally(() => {
                setLaedt(false);
            });
    }, [id, toast]);

    /** Neu laden (Aktualisieren, nach dem Speichern): Ladezustand an, beim Erstladen alten Fehler weg. */
    const laden = useCallback(() => {
        if (!id) return Promise.resolve();
        setLaedt(true);
        if (geladeneId.current !== id) setFehler(null);
        return ladeDaten();
    }, [id, ladeDaten]);

    // Anderer Artikel: sofort als „lädt“ zeigen und alten Fehler entfernen, bevor der Effekt neu lädt.
    const [vorherigeId, setVorherigeId] = useState(id);
    if (vorherigeId !== id) {
        setVorherigeId(id);
        if (id) {
            setLaedt(true);
            setFehler(null);
        }
    }

    useEffect(() => { void ladeDaten(); }, [ladeDaten]);

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
        const stand = angebotsfelderAus(daten.artikel);
        setAngebotsfelder(stand);
        setAngebotsStand(stand);
    }, [daten]);

    const artikel = daten?.artikel;

    /** Steht im Reiter „Für Angebote“ etwas, das noch nicht gespeichert ist? */
    const angeboteUngespeichert = useMemo(() => {
        if (!angebotsStand) return false;
        return angebotsfelder.kurzbeschreibung !== angebotsStand.kurzbeschreibung
            || angebotsfelder.aufschlag.trim() !== angebotsStand.aufschlag
            // Der Editor formt das HTML beim Laden mitunter leicht um - verglichen wird der sichtbare Text.
            || sichtbarerText(angebotsfelder.beschreibung) !== sichtbarerText(angebotsStand.beschreibung);
    }, [angebotsStand, angebotsfelder]);

    const speichereAngebotsfelder = async () => {
        if (!id) return;
        const aufschlagWert = parseDecimal(angebotsfelder.aufschlag);
        if (angebotsfelder.aufschlag.trim() !== "" && (aufschlagWert === null || aufschlagWert < 0 || aufschlagWert > 999.99)) {
            toast.error("Bitte einen Aufschlag zwischen 0 und 999,99 % eingeben.");
            return;
        }
        setSpeichern(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(id)}/dokumenttexte`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    kurzbeschreibung: angebotsfelder.kurzbeschreibung || null,
                    beschreibung: richtextOderNull(angebotsfelder.beschreibung),
                    verkaufsaufschlagProzent: aufschlagWert,
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
            const stand = angebotsfelderAus(aktualisiert);
            setAngebotsfelder(stand);
            setAngebotsStand(stand);
            toast.success("Die Angebotsfelder sind aktualisiert.");
            await laden();
        } catch (err) {
            console.error(err);
            toast.error("Die Angebotsfelder konnten nicht gespeichert werden.");
        } finally {
            setSpeichern(false);
        }
    };

    const stammdatenOeffnen = () => {
        setStammdatenBearbeiten(true);
        wechsleReiter("angaben");
    };

    // Daten eines anderen Artikels (beim Wechsel von Artikel zu Artikel) zaehlen nicht.
    const istAktuell = !!artikel && datenFuer === id;

    if (!istAktuell && (laedt || (!fehler && !daten))) {
        return (
            <PageLayout ribbonCategory="Stammdaten" title="Artikel">
                <div className="motion-safe:animate-pulse space-y-4" aria-busy="true" aria-label="Artikel wird geladen">
                    <div className="h-12 bg-slate-100 rounded-lg" />
                    <div className="h-56 bg-slate-100 rounded-lg" />
                </div>
            </PageLayout>
        );
    }

    if (!istAktuell || !daten || !artikel) {
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

    const reiter: ReiterEintrag<ArtikelReiterId>[] = [
        { id: "uebersicht", label: "Übersicht", icon: <LayoutDashboard className="w-4 h-4" aria-hidden="true" /> },
        {
            id: "preise", label: "Preise & Lieferanten", icon: <Euro className="w-4 h-4" aria-hidden="true" />,
            anzahl: daten.lieferanten.length,
        },
        {
            id: "angaben", label: "Angaben zum Teil", icon: <Ruler className="w-4 h-4" aria-hidden="true" />,
            kennzeichen: stammdatenBearbeiten ? { art: "punkt", text: "wird gerade bearbeitet" } : undefined,
        },
        {
            id: "angebote", label: "Für Angebote", icon: <FileText className="w-4 h-4" aria-hidden="true" />,
            kennzeichen: angeboteUngespeichert ? { art: "punkt", text: "nicht gespeichert" } : undefined,
        },
        { id: "bilder", label: "Bilder & Unterlagen", icon: <Images className="w-4 h-4" aria-hidden="true" /> },
    ];

    const inhalt = (reiterId: ArtikelReiterId) => {
        switch (reiterId) {
            case "uebersicht":
                return <UebersichtReiter daten={daten} onGespeichert={laden} onZuReiter={wechsleReiter} onBearbeiten={stammdatenOeffnen} />;
            case "preise":
                return <PreiseReiter daten={daten} onGespeichert={laden} />;
            case "angaben":
                return (
                    <AngabenReiter
                        artikel={artikel}
                        bearbeiten={stammdatenBearbeiten}
                        onBearbeiten={() => setStammdatenBearbeiten(true)}
                        onAbbrechen={() => setStammdatenBearbeiten(false)}
                        onGespeichert={() => { setStammdatenBearbeiten(false); void laden(); }}
                    />
                );
            case "angebote":
                return (
                    <AngeboteReiter
                        artikel={artikel}
                        felder={angebotsfelder}
                        onAendern={(aenderung) => setAngebotsfelder((alt) => ({ ...alt, ...aenderung }))}
                        speichert={speichern}
                        onSpeichern={speichereAngebotsfelder}
                    />
                );
            case "bilder":
                return <ArtikelDokumente artikelId={artikel.id} onGeaendert={() => void laden()} />;
        }
    };

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
                        disabled={laedt}
                        onClick={() => void laden()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", laedt && "motion-safe:animate-spin")} aria-hidden="true" />
                        Aktualisieren
                    </Button>
                </div>
            }
        >
            <Reiter reiter={reiter} aktiv={aktiverReiter} onWechsel={wechsleReiter}
                ariaLabel="Bereiche des Artikels" idPrefix="artikel" />

            {/*
                Nur der offene Reiter wird gebaut. Ausnahme: Ist die
                Bearbeiten-Maske offen, bleibt "Angaben zum Teil" versteckt
                stehen, damit angefangene Eingaben einen Reiterwechsel
                ueberstehen. Die Angebotsfelder haelt die Seite selbst.
            */}
            {REITER_IDS.map((reiterId) => {
                const offen = reiterId === aktiverReiter;
                const behalten = reiterId === "angaben" && stammdatenBearbeiten;
                if (!offen && !behalten) return null;
                return (
                    <ReiterPanel key={reiterId} idPrefix="artikel" id={reiterId} hidden={!offen}>
                        {inhalt(reiterId)}
                    </ReiterPanel>
                );
            })}
        </PageLayout>
    );
}
