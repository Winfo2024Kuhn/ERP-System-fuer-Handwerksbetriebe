import { useState, useCallback, useEffect } from 'react';
import { Plus, Search, GitBranch, Pencil, Copy, Trash2, Calendar, RefreshCw } from 'lucide-react';
import { Button } from '../ui/button';
import { PageLayout } from '../layout/PageLayout';
import { useConfirm } from '../ui/confirm-dialog';
import OrganigrammEditor from './OrganigrammEditor';

interface OrganigrammListItem {
    name: string;
    created: string;
    updated: string;
}

type View = 'gallery' | 'editor';

export default function OrganigrammMain() {
    const [view, setView] = useState<View>('gallery');
    const [items, setItems] = useState<OrganigrammListItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [currentName, setCurrentName] = useState<string | null>(null);

    // Status toast
    const [status, setStatus] = useState<{ message: string; type: 'info' | 'success' | 'error' } | null>(null);
    const showStatus = (message: string, type: 'info' | 'success' | 'error' = 'info') => {
        setStatus({ message, type });
        setTimeout(() => setStatus(null), 3000);
    };

    // ─── Load List ──────────────────────────────────────────────────────

    const loadList = useCallback(async () => {
        try {
            setLoading(true);
            const res = await fetch('/api/organigramme');
            if (res.ok) setItems(await res.json());
        } catch { /* ignore */ } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadList(); }, [loadList]);

    // ─── Operations ─────────────────────────────────────────────────────

    const handleCreateNew = () => {
        setCurrentName(null);
        setView('editor');
    };

    const handleEdit = (name: string) => {
        setCurrentName(name);
        setView('editor');
    };

    const handleDuplicate = async (name: string) => {
        try {
            const res = await fetch(`/api/organigramme/${encodeURIComponent(name)}`);
            if (!res.ok) throw new Error();
            const data = await res.json();
            const copyName = `${name} (Kopie)`;
            const saveRes = await fetch('/api/organigramme', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: copyName, content: data.content }),
            });
            if (!saveRes.ok) throw new Error();
            showStatus(`Organigramm als "${copyName}" dupliziert.`, 'success');
            loadList();
        } catch {
            showStatus('Duplizieren fehlgeschlagen.', 'error');
        }
    };

    const confirm = useConfirm();

    const handleDelete = async (name: string) => {
        const ok = await confirm({
            title: 'Organigramm löschen',
            message: `Möchtest du „${name}" wirklich löschen? Das kann nicht rückgängig gemacht werden.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;

        try {
            await fetch(`/api/organigramme/${encodeURIComponent(name)}`, { method: 'DELETE' });
            showStatus(`„${name}" wurde gelöscht.`, 'success');
            loadList();
        } catch {
            showStatus('Löschen fehlgeschlagen.', 'error');
        }
    };

    const handleBack = () => {
        loadList();
        setView('gallery');
    };

    // ─── Filter ─────────────────────────────────────────────────────────

    const filtered = items.filter(i =>
        !search || i.name.toLowerCase().includes(search.toLowerCase())
    );

    // ─── Gallery View ───────────────────────────────────────────────────

    if (view === 'gallery') {
        return (
            <PageLayout
                ribbonCategory="Firma & Organisation"
                title="ORGANIGRAMME"
                subtitle="Erstelle und verwalte die Organisationsstruktur deines Betriebs."
                actions={
                    <Button size="sm" onClick={handleCreateNew}>
                        <Plus className="w-4 h-4 mr-1" />Neues Organigramm
                    </Button>
                }
            >
                {/* Status Toast */}
                {status && (
                    <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-xl shadow-lg text-sm font-medium transition-all duration-300 ${
                        status.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200'
                        : status.type === 'error' ? 'bg-red-50 text-red-700 border border-red-200'
                        : 'bg-slate-50 text-slate-700 border border-slate-200'
                    }`}>
                        {status.message}
                    </div>
                )}

                {loading ? (
                    <div className="flex items-center justify-center py-24">
                        <RefreshCw className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : (
                    <div className="space-y-6">
                        {/* Search */}
                        <div className="relative max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                type="text"
                                value={search}
                                onChange={e => setSearch(e.target.value)}
                                placeholder="Organigramme durchsuchen..."
                                className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all"
                            />
                        </div>

                        {/* Cards Grid */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
                            {/* Create New Card */}
                            <button
                                onClick={handleCreateNew}
                                className="group border-2 border-dashed border-slate-200 rounded-2xl p-8 flex flex-col items-center justify-center gap-3 hover:border-rose-300 hover:bg-rose-50/30 transition-all min-h-[200px] cursor-pointer"
                            >
                                <div className="w-14 h-14 rounded-2xl bg-slate-100 group-hover:bg-rose-100 flex items-center justify-center transition-colors">
                                    <Plus className="w-7 h-7 text-slate-400 group-hover:text-rose-500 transition-colors" />
                                </div>
                                <div className="text-center">
                                    <p className="text-sm font-semibold text-slate-700 group-hover:text-rose-700">Neues Organigramm</p>
                                    <p className="text-xs text-slate-400 mt-0.5">Leeres Diagramm erstellen</p>
                                </div>
                            </button>

                            {/* Saved Organigramm Cards */}
                            {filtered.map(item => (
                                <div
                                    key={item.name}
                                    className="group bg-white border border-slate-200 rounded-2xl overflow-hidden hover:shadow-md hover:border-rose-200 transition-all cursor-pointer"
                                    onClick={() => handleEdit(item.name)}
                                >
                                    {/* Visual Preview Area */}
                                    <div className="h-28 bg-gradient-to-br from-rose-50 to-slate-50 flex items-center justify-center border-b border-slate-100">
                                        <GitBranch className="w-10 h-10 text-rose-200" />
                                    </div>

                                    {/* Info */}
                                    <div className="p-4">
                                        <div className="flex items-start justify-between gap-2">
                                            <div className="min-w-0">
                                                <p className="text-sm font-bold text-slate-900 truncate">{item.name}</p>
                                                <div className="flex items-center gap-1 mt-1 text-[11px] text-slate-400">
                                                    <Calendar className="w-3 h-3" />
                                                    <span>{formatDate(item.updated)}</span>
                                                </div>
                                            </div>

                                            {/* Actions */}
                                            <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); handleEdit(item.name); }}
                                                    className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
                                                    title="Bearbeiten"
                                                >
                                                    <Pencil className="w-3.5 h-3.5" />
                                                </button>
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); handleDuplicate(item.name); }}
                                                    className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
                                                    title="Duplizieren"
                                                >
                                                    <Copy className="w-3.5 h-3.5" />
                                                </button>
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); handleDelete(item.name); }}
                                                    className="p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500 transition-colors"
                                                    title="Löschen"
                                                >
                                                    <Trash2 className="w-3.5 h-3.5" />
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {/* Empty state */}
                        {filtered.length === 0 && !loading && (
                            <div className="text-center py-16">
                                <GitBranch className="w-12 h-12 text-slate-200 mx-auto mb-3" />
                                <p className="text-sm font-medium text-slate-500">
                                    {search ? 'Keine Organigramme gefunden.' : 'Noch keine Organigramme vorhanden.'}
                                </p>
                                <p className="text-xs text-slate-400 mt-1">
                                    {search ? 'Versuche einen anderen Suchbegriff.' : 'Erstelle dein erstes Organigramm mit dem Button oben rechts.'}
                                </p>
                            </div>
                        )}
                    </div>
                )}
            </PageLayout>
        );
    }

    // ─── Editor View (fullscreen) ───────────────────────────────────────

    return (
        <OrganigrammEditor
            onClose={handleBack}
            initialName={currentName}
        />
    );
}

function formatDate(dateStr: string): string {
    try {
        const d = new Date(dateStr);
        return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
        return dateStr;
    }
}
