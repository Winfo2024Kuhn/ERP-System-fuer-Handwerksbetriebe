import { useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Package, ChevronLeft, ChevronRight, X, Search, Folder } from "lucide-react";
import { Button } from "../ui/button";
import { Input } from "../ui/input";
import { Label } from "../ui/label";
import { Select } from "../ui/select-custom";
import { cn } from "../../lib/utils";
import type { Artikel } from "../../types";
import { SupplierSelectModal } from "../SupplierSelectModal";
import { CategoryTreeModal } from "../CategoryTreeModal";
// Waehrungsformatierung liegt nebenan, damit das Auswahlfenster sie mitbenutzen kann.
import { formatCurrency } from "./formatCurrency";

/** Seitengroesse der Artikelverwaltung - Standard, solange nichts anderes gefordert ist. */
const STANDARD_SEITENGROESSE = 12;
const SORT_STANDARD = "produktname";

/** Auswahlmoeglichkeit eines technischen Merkmals samt Klartext und Erklaerung. */
interface Merkmalsoption {
    wert: string;
    name: string;
    erklaerung?: string;
}

/** Ausgangslage der Suche - zugleich das Ziel von "Filter zuruecksetzen". */
const LEERE_FILTER = {
    q: "",
    lieferant: "",
    produktlinie: "",
    werkstoff: "",
    kategorieId: 0,
    kategorieName: "",
    // Technische Merkmale: fuer Anwender greifbarer als die Norm.
    herstellverfahren: "",
    fertigungszustand: "",
    verzinkbar: "",
    pulverbeschichtbar: "",
};
type Filterzustand = typeof LEERE_FILTER;

/**
 * Suche aus der Adresszeile lesen.
 *
 * Filter, Sortierung und Seite stehen in der URL, nicht nur im Komponenten-State:
 * Oeffnet man einen Artikel und kommt zurueck, wird die Liste neu aufgebaut - ohne
 * die URL waere die muehsam eingegrenzte Suche dann weg. Nebeneffekt: Eine
 * gefilterte Liste laesst sich als Link weitergeben.
 */
const filterAusUrl = (params: URLSearchParams): Filterzustand => ({
    q: params.get("q") ?? "",
    lieferant: params.get("lieferant") ?? "",
    produktlinie: params.get("produktlinie") ?? "",
    werkstoff: params.get("werkstoff") ?? "",
    kategorieId: Number(params.get("kategorieId")) || 0,
    kategorieName: params.get("kategorieName") ?? "",
    herstellverfahren: params.get("herstellverfahren") ?? "",
    fertigungszustand: params.get("fertigungszustand") ?? "",
    verzinkbar: params.get("verzinkbar") ?? "",
    pulverbeschichtbar: params.get("pulverbeschichtbar") ?? "",
});

/** Nur belegte Werte wandern in die URL - Standardwerte wuerden sie nur zumuellen. */
const urlAusFilter = (
    filter: Filterzustand,
    page: number,
    sortColumn: string,
    sortDirection: string,
): URLSearchParams => {
    const params = new URLSearchParams();
    for (const [schluessel, wert] of Object.entries(filter)) {
        if (wert !== "" && wert !== 0) params.set(schluessel, String(wert));
    }
    if (page > 0) params.set("page", String(page));
    if (sortColumn !== SORT_STANDARD) params.set("sort", sortColumn);
    if (sortDirection !== "asc") params.set("dir", sortDirection);
    return params;
};

// Helpers
const formatKg = (val?: number) => val ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(val) : '';

/**
 * Klartext-Bezeichnung fuer die Liste. Der Produktname enthaelt oft nur das Mass
 * ("0,75 mm") - erst die Form davor sagt dem Anwender, was er vor sich hat:
 * "Blech 0,75 mm". Steht die Form schon im Namen, wird sie nicht doppelt gesetzt.
 */
const artikelBezeichnung = (artikel: Artikel) => {
    const form = artikel.profilform?.anzeigename;
    const mass = artikel.abmessung || artikel.produktname;
    if (!form) return artikel.produktname;
    if (mass && mass.toLowerCase().includes(form.toLowerCase())) return mass;
    return mass ? `${form} ${mass}` : form;
};

/** Verrechnungseinheit kommt je nach Endpunkt als Text oder als Objekt. */
const einheitText = (einheit?: string | { name: string; anzeigename?: string }) => {
    if (!einheit) return '';
    return typeof einheit === 'object' ? (einheit.anzeigename || einheit.name) : einheit;
};

/** Fernbedienung fuer Bedienelemente ausserhalb der Liste (Aktualisieren-Knopf der Seite). */
export interface ArtikelSucheHandle {
    /** Laedt die aktuelle Trefferliste neu. */
    neuLaden: () => void;
}

export interface ArtikelSucheProps {
    /** Filterzustand in der URL spiegeln. Die Artikelverwaltung: true, ein Dialog: false. */
    urlSync?: boolean;
    /** Wird je Zeile gerendert. Fehlt sie, rendert die Komponente die Standardzeile der Artikelverwaltung. */
    zeilenAktion?: (artikel: Artikel) => React.ReactNode;
    /** Klick auf eine Zeile. Fehlt er, navigiert die Zeile zur Detailseite. */
    onZeilenKlick?: (artikel: Artikel) => void;
    /** Seitengroesse. Standard 12 wie bisher in der Artikelverwaltung. */
    seitenGroesse?: number;
    /** Meldet den Ladezustand nach aussen - fuer den Spinner im Aktualisieren-Knopf der Seite. */
    onLadezustand?: (laedt: boolean) => void;
    /** Zugriff auf {@link ArtikelSucheHandle}. */
    ref?: React.Ref<ArtikelSucheHandle>;
}

// ==================== MAIN COMPONENT ====================

export function ArtikelSuche({
    urlSync = false,
    zeilenAktion,
    onZeilenKlick,
    seitenGroesse = STANDARD_SEITENGROESSE,
    onLadezustand,
    ref,
}: ArtikelSucheProps) {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [artikelList, setArtikelList] = useState<Artikel[]>([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    // Vor der ersten Antwort ist `total` nur ein Platzhalter (0) - der Seiten-Guard
    // weiter unten duerfte daraus noch keine Schluesse ziehen.
    const [ersteAntwortDa, setErsteAntwortDa] = useState(false);

    // Startwerte kommen aus der Adresszeile (siehe filterAusUrl). Nur beim ersten
    // Rendern - danach fuehrt der State und schreibt die URL fort. Ohne urlSync
    // bleibt die Adresszeile aussen vor: Ein Dialog darf weder aus ihr lesen noch
    // in sie schreiben, sonst uebernaehme er die Suche der Seite dahinter.
    const [page, setPage] = useState(() => (urlSync ? Math.max(0, Number(searchParams.get("page")) || 0) : 0));

    // Modals state
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [showCategoryModal, setShowCategoryModal] = useState(false);

    // Sort state
    const [sortColumn, setSortColumn] = useState(() => (urlSync && searchParams.get("sort")) || SORT_STANDARD);
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>(
        () => (urlSync && searchParams.get("dir") === "desc" ? "desc" : "asc"));

    const [filters, setFilters] = useState<Filterzustand>(() => (urlSync ? filterAusUrl(searchParams) : LEERE_FILTER));

    const [werkstoffOptions, setWerkstoffOptions] = useState<string[]>([]);
    const [filterOptionen, setFilterOptionen] = useState<{
        herstellverfahren: Merkmalsoption[];
        fertigungszustand: Merkmalsoption[];
    }>({ herstellverfahren: [], fertigungszustand: [] });

    // Fetch initial data (Werkstoffe)
    useEffect(() => {
        fetch('/api/artikel/werkstoffe')
            .then(res => res.json())
            .then(data => setWerkstoffOptions(Array.isArray(data) ? data : []))
            .catch(err => console.error("Fehler beim Laden der Werkstoffe", err));
    }, []);

    // Auswahlmoeglichkeiten fuer Herstellverfahren und Zustand samt Klartext.
    useEffect(() => {
        fetch('/api/artikel/filteroptionen')
            .then(res => res.json())
            .then(data => setFilterOptionen({
                herstellverfahren: Array.isArray(data?.herstellverfahren) ? data.herstellverfahren : [],
                fertigungszustand: Array.isArray(data?.fertigungszustand) ? data.fertigungszustand : [],
            }))
            .catch(err => console.error("Fehler beim Laden der Filteroptionen", err));
    }, []);

    // Laufende Ladevorgänge durchnummerieren: Beim schnellen Tippen in den Filterfeldern
    // starten mehrere Requests gleichzeitig. Ohne diesen Zähler kann eine späte Antwort
    // auf eine alte Filter-/Seiten-Kombination die aktuelle Liste überschreiben.
    const ladeVorgangRef = useRef(0);

    // Fetch List
    const loadArtikel = useCallback(async () => {
        const ladeVorgang = ++ladeVorgangRef.current;
        const istAktuell = () => ladeVorgangRef.current === ladeVorgang;
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(seitenGroesse));
            params.set("sort", sortColumn);
            params.set("dir", sortDirection);

            if (filters.q) params.set("q", filters.q);
            if (filters.lieferant) params.set("lieferant", filters.lieferant);
            if (filters.produktlinie) params.set("produktlinie", filters.produktlinie);
            if (filters.werkstoff) params.set("werkstoff", filters.werkstoff);
            if (filters.kategorieId) params.set("kategorieId", String(filters.kategorieId));
            if (filters.herstellverfahren) params.set("herstellverfahren", filters.herstellverfahren);
            if (filters.fertigungszustand) params.set("fertigungszustand", filters.fertigungszustand);
            if (filters.verzinkbar) params.set("verzinkbar", filters.verzinkbar);
            if (filters.pulverbeschichtbar) params.set("pulverbeschichtbar", filters.pulverbeschichtbar);

            const res = await fetch(`/api/artikel?${params.toString()}`);
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data = await res.json();
            if (!istAktuell()) return;

            setArtikelList(data && Array.isArray(data.artikel) ? data.artikel : []);
            setTotal(data && typeof data.gesamt === "number" ? data.gesamt : 0);
        } catch (err) {
            console.error(err);
            if (!istAktuell()) return;
            setArtikelList([]);
            setTotal(0);
        } finally {
            if (istAktuell()) {
                setLoading(false);
                setErsteAntwortDa(true);
            }
        }
    }, [page, filters, sortColumn, sortDirection, seitenGroesse]);

    useEffect(() => {
        loadArtikel();
    }, [loadArtikel]);

    // Der Aktualisieren-Knopf steht in der Kopfzeile der Seite, die Liste eine Ebene
    // tiefer - deshalb die Fernbedienung nach aussen.
    useImperativeHandle(ref, () => ({ neuLaden: loadArtikel }), [loadArtikel]);

    useEffect(() => {
        onLadezustand?.(loading);
    }, [loading, onLadezustand]);

    // Suche in die Adresszeile spiegeln. Bewusst ohne neuen History-Eintrag: Sonst
    // muesste man sich nach dem Tippen durch jeden einzelnen Buchstaben zurueckklicken.
    const gewuenschteUrl = useMemo(
        () => urlAusFilter(filters, page, sortColumn, sortDirection).toString(),
        [filters, page, sortColumn, sortDirection],
    );
    useEffect(() => {
        // Ein Dialog darf die Adresszeile der dahinterliegenden Seite nicht anfassen.
        if (!urlSync) return;
        if (searchParams.toString() === gewuenschteUrl) return;
        setSearchParams(gewuenschteUrl, { replace: true });
    }, [urlSync, gewuenschteUrl, searchParams, setSearchParams]);

    // Jede Filter-Änderung springt zurück auf Seite 1: Sonst bliebe man z.B. auf
    // Seite 5 stehen, während die gefilterte Liste nur noch zwei Seiten hat – die
    // Treffer wären da, aber unsichtbar.
    const handleFilterChange = (key: string, value: string | number) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
        setPage(0);
    };

    // Gefiltert wird bereits live beim Tippen/Auswählen. Der Button ist nur noch
    // die vertraute Bestätigung – er darf keinen zweiten, konkurrierenden Request starten.
    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
    };

    const handleResetFilters = () => {
        setFilters(LEERE_FILTER);
        setPage(0);
    };

    // Eine neue Sortierung stellt die gesamte Liste um – Seite 3 zeigte danach
    // beliebige Artikel. Deshalb zurück auf die erste Seite.
    const handleSort = (column: string) => {
        if (sortColumn === column) {
            setSortDirection(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setSortColumn(column);
            setSortDirection('asc');
        }
        setPage(0);
    };

    const totalPages = Math.max(1, Math.ceil(total / seitenGroesse));

    // Zeigt die Seitenzahl hinter das Ergebnis (z.B. nachdem der letzte Eintrag einer
    // Seite gelöscht wurde), springen wir auf die letzte gültige Seite zurück – sonst
    // stünde man vor einer leeren Liste. Erst nach dem Laden, denn währenddessen ist
    // `total` noch der alte Wert – und vor der allerersten Antwort schlicht 0, was
    // eine aus der URL übernommene Seite sofort verschlucken würde.
    useEffect(() => {
        if (loading || !ersteAntwortDa) return;
        const letzteSeite = totalPages - 1;
        if (page > letzteSeite) setPage(letzteSeite);
    }, [loading, ersteAntwortDa, totalPages, page]);

    const statusText = useMemo(() => {
        if (loading) return 'Artikel werden geladen...';
        if (total === 0) return 'Keine Artikel gefunden.';
        const start = page * seitenGroesse + 1;
        const end = Math.min(start + artikelList.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Artikeln`;
    }, [loading, total, page, artikelList.length, seitenGroesse]);

    // Vermerkt, dass die Detailseite aus der Liste heraus geoeffnet wurde. Ihr
    // Zurueck-Button geht dann einen Schritt in der History zurueck - und landet
    // damit auf genau dieser Suche samt Treffern statt auf der leeren Liste.
    // Wer die Auswahl selbst verarbeitet (Auswahlfenster), bekommt sie stattdessen
    // gemeldet und bleibt, wo er ist.
    const oeffneArtikel = (artikel: Artikel) => {
        if (onZeilenKlick) {
            onZeilenKlick(artikel);
            return;
        }
        navigate(`/artikel/${artikel.id}`, { state: { vonListe: true } });
    };

    const handleSupplierSelect = (s: { id: number, name: string }) => {
        handleFilterChange('lieferant', s.name);
        setShowSupplierModal(false);
    };

    return (
        <>
            {/* Filter */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                    <div>
                        <Label htmlFor="filter-q" className="text-sm font-medium text-gray-700">Freitext</Label>
                        <Input id="filter-q" type="text" className="mt-1" placeholder="Name, Nummer..." value={filters.q} onChange={e => handleFilterChange('q', e.target.value)} />
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Lieferant</Label>
                        <div className="relative mt-1">
                            <div
                                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer flex items-center justify-between"
                                onClick={() => setShowSupplierModal(true)}
                            >
                                <span className={!filters.lieferant ? "text-slate-500" : ""}>{filters.lieferant || "Alle Lieferanten"}</span>
                                <Search className="w-4 h-4 text-slate-400" />
                            </div>
                            {filters.lieferant && (
                                <button
                                    type="button"
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600"
                                    onClick={(e) => { e.stopPropagation(); handleFilterChange('lieferant', ''); }}
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Kategorie</Label>
                        <div className="relative mt-1">
                            <div
                                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer flex items-center justify-between"
                                onClick={() => setShowCategoryModal(true)}
                            >
                                <span className={!filters.kategorieName ? "text-slate-500" : "truncate"}>{filters.kategorieName || "Alle Kategorien"}</span>
                                <Folder className="w-4 h-4 text-slate-400 ml-2 shrink-0" />
                            </div>
                            {filters.kategorieId !== 0 && (
                                <button
                                    type="button"
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600"
                                    onClick={(e) => { e.stopPropagation(); handleFilterChange('kategorieId', 0); handleFilterChange('kategorieName', ''); }}
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-werkstoff" className="text-sm font-medium text-gray-700">Werkstoff</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Alle Werkstoffe" }, ...werkstoffOptions.map(opt => ({ value: opt, label: opt }))]}
                                value={filters.werkstoff}
                                onChange={val => handleFilterChange('werkstoff', val)}
                                placeholder="Alle Werkstoffe"
                            />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-produktlinie" className="text-sm font-medium text-gray-700">Produktlinie</Label>
                        <Input id="filter-produktlinie" type="text" className="mt-1" placeholder="Produktlinie" value={filters.produktlinie} onChange={e => handleFilterChange('produktlinie', e.target.value)} />
                    </div>

                    {/* Technische Merkmale: "geschweißt, kaltgefertigt" sagt mehr als "EN 10219-2". */}
                    <div>
                        <Label htmlFor="filter-verfahren" className="text-sm font-medium text-gray-700">Herstellung</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Egal" },
                                    ...filterOptionen.herstellverfahren.map(o => ({ value: o.wert, label: o.name }))]}
                                value={filters.herstellverfahren}
                                onChange={val => handleFilterChange('herstellverfahren', val)}
                                placeholder="Egal"
                            />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-zustand" className="text-sm font-medium text-gray-700">Zustand</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Egal" },
                                    ...filterOptionen.fertigungszustand.map(o => ({ value: o.wert, label: o.name }))]}
                                value={filters.fertigungszustand}
                                onChange={val => handleFilterChange('fertigungszustand', val)}
                                placeholder="Egal"
                            />
                        </div>
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Oberfläche</Label>
                        <div className="mt-1 flex flex-wrap gap-2">
                            <button
                                type="button"
                                aria-pressed={filters.verzinkbar === 'true'}
                                onClick={() => handleFilterChange('verzinkbar', filters.verzinkbar === 'true' ? '' : 'true')}
                                className={cn(
                                    "px-3 py-2 min-h-[40px] rounded-lg border text-sm transition-colors",
                                    filters.verzinkbar === 'true'
                                        ? "bg-rose-600 text-white border-rose-600"
                                        : "border-slate-300 text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                verzinkbar
                            </button>
                            <button
                                type="button"
                                aria-pressed={filters.pulverbeschichtbar === 'true'}
                                onClick={() => handleFilterChange('pulverbeschichtbar', filters.pulverbeschichtbar === 'true' ? '' : 'true')}
                                className={cn(
                                    "px-3 py-2 min-h-[40px] rounded-lg border text-sm transition-colors",
                                    filters.pulverbeschichtbar === 'true'
                                        ? "bg-rose-600 text-white border-rose-600"
                                        : "border-slate-300 text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                pulverbeschichtbar
                            </button>
                        </div>
                    </div>

                    {/* Kein Filtern-Button: Gefiltert wird live bei jeder Eingabe. */}
                    <div className="flex items-end">
                        <button type="button" className="btn-secondary flex-1 px-4 py-2 border rounded-lg hover:bg-slate-50" onClick={handleResetFilters}>Filter zurücksetzen</button>
                    </div>
                </form>
                <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {seitenGroesse} Einträge auf einmal geladen.</p>
            </div>

            {/* Grid Content */}
            {loading ? (
                <div className="text-center py-8 text-slate-500">Artikel werden geladen...</div>
            ) : artikelList.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <Package className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Artikel gefunden.
                </div>
            ) : (
                <div className="bg-white rounded-2xl shadow-lg overflow-hidden border border-slate-100">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                                <tr>
                                    <SortableHeader label="Artikel-Nr." column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Bezeichnung" column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Werkstoff" column="werkstoffName" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <th className="px-4 py-3">Ausführung</th>
                                    <SortableHeader label="kg/m" column="kgProMeter" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <SortableHeader label="Bester Preis" column="preis" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <th className="px-4 py-3">Günstigster Anbieter</th>
                                    <th className="px-4 py-3 text-center w-12"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {artikelList.map((artikel) => (
                                    <tr
                                        key={artikel.id}
                                        className="group hover:bg-slate-50 transition-colors cursor-pointer"
                                        onClick={() => oeffneArtikel(artikel)}
                                        tabIndex={0}
                                        // Mit onZeilenKlick ist die Zeile kein Link mehr: Sie
                                        // navigiert nirgendwohin, sondern schaltet die Auswahl
                                        // um. role und Ansage muessen dazu passen, sonst
                                        // verspricht der Screenreader eine Detailseite, die der
                                        // Klick nie oeffnet. "an- oder abwaehlen" statt nur
                                        // "auswaehlen", damit sich das Label von der Checkbox
                                        // in der Zeilenaktion unterscheidet.
                                        role={onZeilenKlick ? "button" : "link"}
                                        aria-label={onZeilenKlick
                                            ? `${artikelBezeichnung(artikel)} an- oder abwählen`
                                            : `Details zu ${artikelBezeichnung(artikel)} öffnen`}
                                        onKeyDown={(e) => {
                                            if (e.key === 'Enter' || e.key === ' ') {
                                                e.preventDefault();
                                                oeffneArtikel(artikel);
                                            }
                                        }}
                                    >
                                        <td className="px-4 py-3 font-mono text-xs text-slate-600">{artikel.artikelnummer || '-'}</td>
                                        <td className="px-4 py-3">
                                            <div
                                                className="font-medium text-slate-900"
                                                title={artikel.profilform?.erklaerung}
                                            >
                                                {artikelBezeichnung(artikel)}
                                            </div>
                                            {artikel.massnorm && (
                                                <div className="text-xs text-slate-400">{artikel.massnorm}</div>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-slate-600">{artikel.werkstoffName || '-'}</td>
                                        <td className="px-4 py-3">
                                            {/* Klartext statt Normnummer - der Anwender soll sehen, was er bekommt. */}
                                            <div className="flex flex-wrap gap-1">
                                                {artikel.herstellverfahren && (
                                                    <span title={artikel.herstellverfahren.erklaerung}
                                                        className="inline-block rounded bg-slate-100 text-slate-700 text-xs px-1.5 py-0.5">
                                                        {artikel.herstellverfahren.anzeigename}
                                                    </span>
                                                )}
                                                {artikel.fertigungszustand && (
                                                    <span title={artikel.fertigungszustand.erklaerung}
                                                        className="inline-block rounded bg-slate-100 text-slate-700 text-xs px-1.5 py-0.5">
                                                        {artikel.fertigungszustand.anzeigename}
                                                    </span>
                                                )}
                                                {!artikel.herstellverfahren && !artikel.fertigungszustand && (
                                                    <span className="text-slate-400">-</span>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 text-right text-slate-600 tabular-nums">{formatKg(artikel.kgProMeter)}</td>
                                        <td className="px-4 py-3 text-right font-medium text-slate-900 tabular-nums">
                                            {artikel.guenstigsterPreis !== undefined && artikel.guenstigsterPreis !== null ? (
                                                <>
                                                    {formatCurrency(artikel.guenstigsterPreis)}
                                                    <span className="text-xs font-normal text-slate-400"> / {einheitText(artikel.verrechnungseinheit)}</span>
                                                </>
                                            ) : (
                                                <span className="text-slate-400 font-normal">kein Preis</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-slate-600">
                                            {artikel.guenstigsterLieferantName ? (
                                                <>
                                                    <div>{artikel.guenstigsterLieferantName}</div>
                                                    {(artikel.anzahlLieferanten ?? 0) > 1 && (
                                                        <div className="text-xs text-slate-400">
                                                            +{(artikel.anzahlLieferanten ?? 1) - 1} weitere{(artikel.anzahlLieferanten ?? 1) - 1 === 1 ? 'r' : ''}
                                                        </div>
                                                    )}
                                                </>
                                            ) : '-'}
                                        </td>
                                        {/* Ohne eigene Zeilenaktion zeigt der Pfeil an, dass die Zeile auf die Detailseite fuehrt. */}
                                        {/* Eine eigene Zeilenaktion gehoert sich selbst: Ihr Klick darf nicht
                                            zusaetzlich den Zeilen-Handler ausloesen, sonst uebernaehme das
                                            Auswahlfenster die Position doppelt - oder navigierte, mangels
                                            onZeilenKlick, aus dem Fenster heraus auf die Detailseite. Die Zeile
                                            faengt auch Enter/Leertaste ab, deshalb der Stopp fuer beide Wege.
                                            Ohne Zeilenaktion bleibt die Pfeil-Zelle bewusst durchlaessig: Dort
                                            ist der Klick Teil des Zeilen-Klicks. */}
                                        <td
                                            className="px-4 py-3 text-center"
                                            onClick={zeilenAktion ? (e) => e.stopPropagation() : undefined}
                                            onKeyDown={zeilenAktion ? (e) => e.stopPropagation() : undefined}
                                        >
                                            {zeilenAktion ? zeilenAktion(artikel) : <StandardZeilenAktion />}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Pagination */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <p className="text-sm text-gray-600">{statusText}</p>
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>
                        <ChevronLeft className="w-4 h-4" /> zurück
                    </Button>
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                        Weiter <ChevronRight className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {/* Modals */}
            {showSupplierModal && (
                <SupplierSelectModal
                    onSelect={handleSupplierSelect}
                    onClose={() => setShowSupplierModal(false)}
                />
            )}
            {showCategoryModal && (
                <CategoryTreeModal
                    onSelect={(id, name) => { handleFilterChange('kategorieId', id); handleFilterChange('kategorieName', name); setShowCategoryModal(false); }}
                    onClose={() => setShowCategoryModal(false)}
                />
            )}
        </>
    );
}

/** Standardanzeige der letzten Spalte: der Pfeil auf die Detailseite. */
function StandardZeilenAktion() {
    return (
        <ChevronRight
            className="w-4 h-4 inline-block text-slate-300 group-hover:text-rose-600 transition-colors"
            aria-hidden="true"
        />
    );
}

function SortableHeader({ label, column, currentSort, direction, onSort, className }: { label: string, column: string, currentSort: string, direction: string, onSort: (col: string) => void, className?: string }) {
    return (
        <th className={cn("px-4 py-3 cursor-pointer hover:bg-slate-100 transition-colors select-none group", className)} onClick={() => onSort(column)}>
            <div className={cn("flex items-center gap-1", className?.includes("text-right") && "justify-end", className?.includes("text-center") && "justify-center")}>
                {label}
                <span className="text-slate-400 w-4">
                    {currentSort === column && (direction === 'asc' ? '▲' : '▼')}
                </span>
            </div>
        </th>
    );
}
