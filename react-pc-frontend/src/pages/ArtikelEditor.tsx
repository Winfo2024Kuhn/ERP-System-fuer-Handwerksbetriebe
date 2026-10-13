import { useRef, useState } from "react";
import { RefreshCw, FolderPlus, Plus, Percent } from "lucide-react";
import { Button } from "../components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "../components/ui/dialog";
import { cn } from "../lib/utils";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { ArtikelSuche, type ArtikelSucheHandle } from "../components/artikel/ArtikelSuche";
import { AufschlagPflege } from "../components/artikel/AufschlagPflege";
import { useDarfAufschlaegeAendern } from "../features/aufschlaege/useDarfAufschlaegeAendern";

// ==================== MAIN COMPONENT ====================

export default function ArtikelEditor() {
    // Filterleiste und Trefferliste stecken in ArtikelSuche - dieselbe Suche nutzt
    // das Auswahlfenster im Dokument-Editor. Hier bleibt nur das Seitengeruest.
    const sucheRef = useRef<ArtikelSucheHandle>(null);
    // Der Aktualisieren-Knopf steht in der Kopfzeile, die Liste eine Ebene tiefer:
    // Damit sein Spinner mitlaeuft, meldet die Suche ihren Ladezustand herauf.
    const [loading, setLoading] = useState(false);

    // Modals state
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    // Aufschläge sind eine Preisregel: Knopf und Fenster gibt es nur für Benutzer mit dem Recht „Aufschläge ändern“
    const darfAufschlaegeAendern = useDarfAufschlaegeAendern();
    const [showAufschlaege, setShowAufschlaege] = useState(false);

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Artikelübersicht"
            subtitle="Verwaltung der Artikel und Preise."
            actions={
                <>
                    <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => setShowCreateModal(true)}>
                        <Plus className="w-4 h-4 mr-2" />
                        Neu
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => setShowCategoryManagerModal(true)}
                    >
                        <FolderPlus className="w-4 h-4 mr-2" />
                        Kategorien anlegen
                    </Button>
                    {darfAufschlaegeAendern && (
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={() => setShowAufschlaege(true)}
                        >
                            <Percent className="w-4 h-4 mr-2" aria-hidden="true" />
                            Aufschläge
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={() => sucheRef.current?.neuLaden()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            {/* Die Artikelverwaltung haelt ihre Suche in der Adresszeile: Filter
                ueberleben einen Reload und lassen sich als Link weitergeben. */}
            <ArtikelSuche ref={sucheRef} urlSync onLadezustand={setLoading} />

            {/* Modals */}
            {showCreateModal && (
                <CreateArticleModal
                    onClose={() => setShowCreateModal(false)}
                    onSave={() => { sucheRef.current?.neuLaden(); setShowCreateModal(false); }}
                />
            )}
            {showCategoryManagerModal && (
                <CategoryTreeModal
                    mode="manage"
                    onClose={() => setShowCategoryManagerModal(false)}
                />
            )}
            {darfAufschlaegeAendern && (
                <Dialog open={showAufschlaege} onOpenChange={setShowAufschlaege} className="w-full max-w-5xl max-h-[90vh]" aria-labelledby="artikel-aufschlaege-titel">
                    <DialogContent>
                        <DialogHeader>
                            <DialogTitle id="artikel-aufschlaege-titel">Aufschläge</DialogTitle>
                            <DialogDescription>Wie viel Prozent auf den Einkaufspreis kommen, damit der Verkaufspreis stimmt.</DialogDescription>
                        </DialogHeader>
                        <div className="min-h-0 flex-1 overflow-y-auto -m-1 p-1">
                            {showAufschlaege && <AufschlagPflege />}
                        </div>
                        <DialogFooter>
                            <Button variant="outline" size="sm" onClick={() => setShowAufschlaege(false)}>Schließen</Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            )}
        </PageLayout>
    );
}
