import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { ArrowLeft, Building2, ChevronLeft, ChevronRight, Plus, RefreshCw } from "lucide-react";
import { Button } from "../components/ui/button";
import { PageLayout } from "../components/layout/PageLayout";
import { useToast } from "../components/ui/toast";
import { cn } from "../lib/utils";
import type { Lieferant, LieferantDetail } from "../types";
import { LieferantCard } from "../features/lieferanten/LieferantCard";
import { LieferantDetailView } from "../features/lieferanten/LieferantDetailView";
import { LieferantenFilterLeiste } from "../features/lieferanten/LieferantenFilterLeiste";
import { LieferantModal } from "../features/lieferanten/LieferantModal";
import { istGueltigerTab, type LieferantTab } from "../features/lieferanten/lieferantHilfen";
import { useLieferantenListe } from "../features/lieferanten/useLieferantenListe";

/**
 * Seite „Lieferanten“ (/lieferanten): Übersicht mit Filtern oder – mit
 * `?lieferantId=` in der Adresse – die Detailansicht mit Reitern. Die Seite
 * setzt nur zusammen; Teile und Logik liegen in `features/lieferanten/`.
 */
const DETAIL_LADEFEHLER = 'Lieferant konnte nicht geladen werden. Bitte Seite neu laden.';

export default function LieferantenEditor() {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');
    const [selectedLieferant, setSelectedLieferant] = useState<LieferantDetail | null>(null);
    const liste = useLieferantenListe(viewMode === 'list');
    const { setLoading } = liste;

    const [editingLieferant, setEditingLieferant] = useState<Lieferant | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [ladeVersion, setLadeVersion] = useState(0);

    // Welcher Reiter offen ist, steht in der Adresszeile. Ein unbekannter oder
    // fehlender Wert fällt auf den E-Mail-Verlauf zurück — eine kaputte URL
    // soll die Seite nicht leer lassen.
    const tabParam = searchParams.get('tab');
    const activeTab: LieferantTab = istGueltigerTab(tabParam) ? tabParam : 'emails';

    /**
     * Reiterwechsel bewusst mit `replace` statt `push`: Sonst müsste man den
     * Zurück-Knopf für jeden angetippten Reiter einmal extra drücken, um die
     * Detailansicht überhaupt zu verlassen. Die Adresse bleibt trotzdem
     * teilbar und ein Lesezeichen landet im richtigen Reiter.
     */
    const wechsleTab = useCallback((tab: LieferantTab) => {
        setSearchParams((vorherige) => {
            const naechste = new URLSearchParams(vorherige);
            naechste.set('tab', tab);
            return naechste;
        }, { replace: true });
    }, [setSearchParams]);

    // Deep-Link: Die Adresszeile ist die Wahrheit — `?lieferantId=123` öffnet
    // die Detailansicht, ihr Verschwinden schließt sie wieder.
    const lastProcessedLieferantId = useRef<string | null>(null);
    const lieferantIdAusAdresse = searchParams.get('lieferantId');
    // Browser-Zurück aus der Detailansicht heraus. Ohne diesen Zweig
    // bliebe die Detailansicht offen, obwohl die Adresse längst die
    // Liste zeigt.
    const [vorherigeLieferantIdAusAdresse, setVorherigeLieferantIdAusAdresse] = useState(lieferantIdAusAdresse);
    if (vorherigeLieferantIdAusAdresse !== lieferantIdAusAdresse) {
        setVorherigeLieferantIdAusAdresse(lieferantIdAusAdresse);
        if (!lieferantIdAusAdresse) {
            setSelectedLieferant(null);
            setViewMode('list');
        }
    }
    useEffect(() => {
        const lieferantIdParam = searchParams.get('lieferantId');
        if (!lieferantIdParam) {
            lastProcessedLieferantId.current = null;
            return;
        }
        if (lastProcessedLieferantId.current === lieferantIdParam) return;
        const lieferantId = Number(lieferantIdParam);
        if (isNaN(lieferantId) || !lieferantId) return;
        lastProcessedLieferantId.current = lieferantIdParam;
        (async () => {
            try {
                setLoading(true);
                const res = await fetch(`/api/lieferanten/${lieferantId}?nurStammdaten=true`);
                if (!res.ok) throw new Error('Fehler beim Laden der Details');
                const data: LieferantDetail = await res.json();
                setSelectedLieferant(data);
                setViewMode('detail');
            } catch (err) {
                console.error('Deep-link load error', err);
                toast.error(DETAIL_LADEFEHLER);
            } finally {
                setLoading(false);
            }
        })();
    }, [searchParams, setLoading, toast]);

    const handleCreate = () => {
        setEditingLieferant({
            id: "",
            lieferantenTyp: "",
            lieferantenname: "",
            aliasName: "",
            eigeneKundennummer: "",
            kundenEmails: []
        });
        setIsModalOpen(true);
    };

    const handleEdit = (lieferant: Lieferant) => {
        setEditingLieferant({ ...lieferant });
        setIsModalOpen(true);
    };

    /**
     * Lädt die Detaildaten neu, ohne die Adresszeile anzufassen — der offene
     * Reiter bleibt also stehen. Genau das braucht man nach dem Speichern.
     */
    const aktualisiereDetail = async (id: string | number) => {
        try {
            setLoading(true);
            const res = await fetch(`/api/lieferanten/${id}?nurStammdaten=true`);
            if (!res.ok) throw new Error("Fehler beim Laden der Details");
            setSelectedLieferant(await res.json() as LieferantDetail);
        } catch (err) {
            console.error("Detail reload error", err);
            toast.error(DETAIL_LADEFEHLER);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Öffnet einen Lieferanten aus der Liste heraus.
     *
     * <p>Bewusst ein echter History-Eintrag (kein `replace`): Der Zurück-Knopf
     * des Browsers soll aus der Detailansicht zurück in die Liste führen, nicht
     * gleich die ganze Seite verlassen.</p>
     *
     * <p>Die Kennung wird vorab vermerkt, damit der Deep-Link-Effekt die
     * gerade geladenen Daten nicht ein zweites Mal vom Server holt.</p>
     */
    const handleDetail = async (lieferant: Lieferant) => {
        const oeffne = (detail: LieferantDetail, id: string | number) => {
            lastProcessedLieferantId.current = String(id);
            setSelectedLieferant(detail);
            setViewMode('detail');
            setSearchParams({ lieferantId: String(id) });
        };
        try {
            setLoading(true);
            const res = await fetch(`/api/lieferanten/${lieferant.id}?nurStammdaten=true`);
            if (!res.ok) throw new Error("Fehler beim Laden der Details");
            const data: LieferantDetail = await res.json();

            oeffne(data, data.id);
        } catch (err) {
            console.error("Detail load error", err);
            toast.error(DETAIL_LADEFEHLER);
            oeffne(lieferant as LieferantDetail, lieferant.id);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (data: Lieferant) => {
        try {
            const isEdit = !!data.id;
            const url = isEdit ? `/api/lieferanten/${data.id}` : "/api/lieferanten";
            const method = isEdit ? "PUT" : "POST";

            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data),
            });

            if (!res.ok) {
                const errData = await res.json();
                toast.error(errData.message || "Speichern fehlgeschlagen");
                return;
            }

            setIsModalOpen(false);
            setEditingLieferant(null);

            if (viewMode === 'detail' && selectedLieferant?.id === data.id) {
                // Nur die Daten auffrischen, nicht neu "öffnen": Sonst fiele der
                // gerade offene Reiter aus der Adresszeile und der Nutzer stünde
                // nach dem Speichern unvermittelt wieder im E-Mail-Verlauf.
                await aktualisiereDetail(data.id);
                setLadeVersion(v => v + 1);
            } else {
                liste.neuLaden();
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    /**
     * Zurück zur Liste: Es reicht, die Kennung aus der Adresszeile zu nehmen —
     * der Deep-Link-Effekt oben schließt die Detailansicht daraufhin selbst.
     * So gibt es nur eine Stelle, die über "Liste oder Detail?" entscheidet.
     */
    const zurueckZurListe = useCallback(() => {
        setSearchParams({});
    }, [setSearchParams]);

    if (viewMode === 'detail' && selectedLieferant) {
        return (
            <PageLayout
                ribbonCategory="Stammdaten"
                title="LIEFERANTENDETAILS"
                subtitle={selectedLieferant.lieferantenname}
                actions={
                    <Button variant="outline" size="sm" onClick={zurueckZurListe}>
                        <ArrowLeft className="w-4 h-4 mr-2" /> Zurück
                    </Button>
                }
            >
                <LieferantDetailView
                    lieferant={selectedLieferant}
                    ladeVersion={ladeVersion}
                    activeTab={activeTab}
                    onTabChange={wechsleTab}
                    onBack={zurueckZurListe}
                    onEdit={() => handleEdit(selectedLieferant)}
                />
                {isModalOpen && editingLieferant && (
                    <LieferantModal
                        lieferant={editingLieferant}
                        onClose={() => setIsModalOpen(false)}
                        onSave={handleSave}
                    />
                )}
            </PageLayout>
        );
    }

    return (
        <PageLayout
            ribbonCategory="Stammdaten"
            title="LIEFERANTENÜBERSICHT"
            subtitle="Übersicht und Verwaltung Ihrer Lieferanten."
            actions={
                <>
                    <Button size="sm" onClick={handleCreate}>
                        <Plus className="w-4 h-4 mr-2" />
                        Neuer Lieferant
                    </Button>
                    <Button variant="outline" size="sm" onClick={liste.neuLaden}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", liste.loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            <LieferantenFilterLeiste
                filters={liste.filters}
                onAendern={liste.filterAendern}
                onZuruecksetzen={liste.filterZuruecksetzen}
                onAbschicken={() => liste.setPage(0)}
            />

            {/* Grid Content */}
            {liste.loading ? (
                <div className="text-center py-8 text-slate-500">Lieferanten werden geladen...</div>
            ) : liste.lieferanten.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <Building2 className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Lieferanten gefunden.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-4">
                    {liste.lieferanten.map((lieferant) => (
                        <LieferantCard
                            key={lieferant.id}
                            lieferant={lieferant}
                            onClick={() => handleDetail(lieferant)}
                            onEdit={(e) => {
                                e.stopPropagation();
                                handleEdit(lieferant);
                            }}
                        />
                    ))}
                </div>
            )}

            {/* Pagination */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <p className="text-sm text-slate-600">{liste.statusText}</p>
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" size="sm" disabled={liste.page === 0} onClick={() => liste.setPage(p => Math.max(0, p - 1))}>
                        <ChevronLeft className="w-4 h-4" /> Zurück
                    </Button>
                    <Button variant="outline" size="sm" disabled={liste.page >= liste.totalPages - 1} onClick={() => liste.setPage(p => p + 1)}>
                        Weiter <ChevronRight className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {/* Create/Edit Modal */}
            {isModalOpen && editingLieferant && (
                <LieferantModal
                    lieferant={editingLieferant}
                    onClose={() => setIsModalOpen(false)}
                    onSave={handleSave}
                />
            )}
        </PageLayout>
    );
}
