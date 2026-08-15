import { useRef, useState } from "react";
import { RefreshCw, FolderPlus, Plus } from "lucide-react";
import { Button } from "../components/ui/button";
import { cn } from "../lib/utils";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { ArtikelSuche, type ArtikelSucheHandle } from "../components/artikel/ArtikelSuche";

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
        </PageLayout>
    );
}
