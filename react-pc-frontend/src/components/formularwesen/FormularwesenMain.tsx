import { useState, useCallback, useEffect, useRef } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '../ui/button';
import { PageLayout } from '../layout/PageLayout';
import type { FormBlock, FormBlockType, FormTemplate, FormTemplateListItem, FormBlockStyles, Kunde, Projekt } from '../../types';
import TemplateGallery from './TemplateGallery';
import EditorToolbar from './EditorToolbar';
import BlocksSidebar from './BlocksSidebar';
import PropertiesSidebar from './PropertiesSidebar';
import EditorCanvas from './EditorCanvas';
import PreviewModal from './PreviewModal';
import TextbausteinDefaultsModal from './TextbausteinDefaultsModal';
import {
    uid, DEFAULT_ITEMS, SIZE_DEFAULTS, STYLE_DEFAULTS, SAMPLE_DATA
} from './constants';

type View = 'gallery' | 'editor';

/** Main Formularwesen page — Canva-like template management + editor */
export default function FormularwesenMain() {
    const [view, setView] = useState<View>('gallery');

    // Gallery state
    const [templates, setTemplates] = useState<FormTemplateListItem[]>([]);
    const [loadingTemplates, setLoadingTemplates] = useState(true);

    // Editor state
    const [items, setItemsRaw] = useState<FormBlock[]>([]);
    const historyRef = useRef<FormBlock[][]>([]);
    const skipHistoryRef = useRef(false);
    const MAX_HISTORY = 50;

    /** Wrapped setItems that records history for undo */
    const setItems: typeof setItemsRaw = (action) => {
        if (!skipHistoryRef.current) {
            setItemsRaw(prev => {
                // Push current state onto history before applying the update
                historyRef.current = [...historyRef.current.slice(-(MAX_HISTORY - 1)), prev];
                const next = typeof action === 'function' ? (action as (p: FormBlock[]) => FormBlock[])(prev) : action;
                return next;
            });
        } else {
            setItemsRaw(action);
        }
    };

    const handleUndo = () => {
        if (historyRef.current.length === 0) return;
        const previous = historyRef.current[historyRef.current.length - 1];
        historyRef.current = historyRef.current.slice(0, -1);
        skipHistoryRef.current = true;
        setItemsRaw(previous);
        skipHistoryRef.current = false;
        setSelectedId(null);
    };
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [activePage, setActivePage] = useState(1);
    const [currentTemplateName, setCurrentTemplateName] = useState<string | null>(null);
    const [selectedDokumenttypen, setSelectedDokumenttypen] = useState<string[]>([]);
    const [backgroundImage, setBackgroundImage] = useState<string | null>(null);
    const [backgroundImagePage2, setBackgroundImagePage2] = useState<string | null>(null);
    const [showPreview, setShowPreview] = useState(false);
    const [showSaveModal, setShowSaveModal] = useState(false);
    const [showTextbausteinDefaults, setShowTextbausteinDefaults] = useState(false);
    const [saveNameInput, setSaveNameInput] = useState('');
    const [status, setStatus] = useState<{ message: string; type: 'info' | 'success' | 'error' } | null>(null);
    const [liveData, setLiveData] = useState<Record<string, string>>(SAMPLE_DATA);

    // ─── Load Live Data (random Kunde + Projekt for preview) ─────────

    const loadLiveData = useCallback(async () => {
        try {
            const [kundenRes, projekteRes] = await Promise.all([
                fetch('/api/kunden?size=500').then(r => r.ok ? r.json() : []).catch(() => []),
                fetch('/api/projekte/simple?size=500').then(r => r.ok ? r.json() : []).catch(() => [])
            ]);

            const kunden: Kunde[] = Array.isArray(kundenRes) ? kundenRes :
                (kundenRes?.content ?? kundenRes?.data ?? []);
            const projekte: Projekt[] = Array.isArray(projekteRes) ? projekteRes :
                (projekteRes?.content ?? projekteRes?.data ?? []);

            // Filter out entries without meaningful data
            const validKunden = kunden.filter(k => k.name && k.name.trim());
            const validProjekte = projekte.filter(p => p.bauvorhaben && p.bauvorhaben.trim());

            if (validKunden.length === 0 && validProjekte.length === 0) {
                setLiveData(SAMPLE_DATA);
                return;
            }

            const kunde = validKunden.length > 0
                ? validKunden[Math.floor(Math.random() * validKunden.length)]
                : null;
            const projekt = validProjekte.length > 0
                ? validProjekte[Math.floor(Math.random() * validProjekte.length)]
                : null;

            // Build address from Kunde fields, filter null values
            const adressParts = [
                kunde?.name,
                kunde?.ansprechpartner || kunde?.ansprechspartner,
                kunde?.strasse,
                [kunde?.plz, kunde?.ort].filter(Boolean).join(' ')
            ].filter(Boolean);

            const data: Record<string, string> = {
                ...SAMPLE_DATA,
                ...(kunde?.kundennummer ? { kundennummer: kunde.kundennummer } : {}),
                ...(kunde?.name ? { kundenname: kunde.name } : {}),
                ...(adressParts.length > 0 ? { kundenadresse: adressParts.join('\n') } : {}),
                ...(kunde?.anrede ? { anrede: kunde.anrede } : {}),
                ...((kunde?.ansprechpartner || kunde?.ansprechspartner) ? { ansprechpartner: kunde.ansprechpartner || kunde.ansprechspartner || '' } : {}),
                ...(projekt?.bauvorhaben ? { bauvorhaben: projekt.bauvorhaben } : {}),
                ...(projekt?.auftragsnummer ? { dokumentnummer: projekt.auftragsnummer } : {}),
                ...(projekt?.id ? { projektnummer: `PRJ-${projekt.id}` } : {}),
                ...(projekt?.anlegedatum ? { datum: new Date(projekt.anlegedatum).toLocaleDateString('de-DE') } : { datum: new Date().toLocaleDateString('de-DE') }),
            };

            setLiveData(data);
        } catch {
            setLiveData(SAMPLE_DATA);
        }
    }, []);

    // ─── Load Templates ──────────────────────────────────────────────────

    const loadTemplateList = useCallback(async () => {
        try {
            setLoadingTemplates(true);
            const res = await fetch('/api/formulare/templates');
            if (res.ok) setTemplates(await res.json());
        } catch { /* ignore */ } finally {
            setLoadingTemplates(false);
        }
    }, []);

    useEffect(() => { loadTemplateList(); }, [loadTemplateList]);

    // Load live data when entering the editor view
    useEffect(() => {
        if (view === 'editor') loadLiveData();
    }, [view, loadLiveData]);

    // ─── Status ──────────────────────────────────────────────────────────

    const showStatus = (message: string, type: 'info' | 'success' | 'error' = 'info') => {
        setStatus({ message, type });
        setTimeout(() => setStatus(null), 3000);
    };

    // ─── Serialization ──────────────────────────────────────────────────

    const serialize = useCallback(() => {
        const css = `body{font-family:Inter,Arial,sans-serif;}`;
        let metadata = `<meta name="page-format" content="A4" />`;
        if (backgroundImage) metadata += `<meta name="background-image" content="${encodeURIComponent(backgroundImage)}" />`;
        if (backgroundImagePage2) metadata += `<meta name="background-image-page2" content="${encodeURIComponent(backgroundImagePage2)}" />`;
        const htmlPages = [1, 2].map(page => {
            const blocks = items.filter(i => i.page === page).sort((a, b) => a.z - b.z).map(i => {
                const encodedContent = i.content ? ` data-content="${encodeURIComponent(i.content)}"` : '';
                const encodedStyle = i.styles ? ` data-style="${encodeURIComponent(JSON.stringify(i.styles))}"` : '';
                return `<div data-block-type="${i.type}" data-page="${i.page}" data-x="${i.x}" data-y="${i.y}" data-z="${i.z}" data-width="${i.width}" data-height="${i.height}"${encodedContent}${encodedStyle}></div>`;
            }).join('');
            return `<div class="page">${blocks}</div>`;
        }).join('');
        return `${metadata}<style>${css}</style>${htmlPages}`;
    }, [items, backgroundImage, backgroundImagePage2]);

    const deserialize = useCallback((html: string): FormBlock[] => {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html || '';
        const found = wrapper.querySelectorAll('[data-block-type]');
        if (!found.length && !html.includes('<div class="page">')) return DEFAULT_ITEMS.map(i => ({ ...i, id: uid() }));
        if (!found.length) return [];
        return Array.from(found).map((el, idx) => {
            const dataset = (el as HTMLElement).dataset;
            const type = dataset.blockType as FormBlockType;
            let styles: FormBlockStyles | undefined;
            if (dataset.style) {
                try { styles = JSON.parse(decodeURIComponent(dataset.style)); } catch { /* ignore */ }
            }
            return {
                id: uid(),
                type,
                page: Number(dataset.page || 1),
                x: Number(dataset.x || 32),
                y: Number(dataset.y || 32),
                z: Number(dataset.z || idx + 1),
                width: Number(dataset.width) || SIZE_DEFAULTS[type]?.width || 200,
                height: Number(dataset.height) || SIZE_DEFAULTS[type]?.height || 80,
                content: dataset.content ? decodeURIComponent(dataset.content) : undefined,
                styles: styles || STYLE_DEFAULTS[type]
            };
        });
    }, []);

    const parseMetadata = useCallback((html: string) => {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = html || '';
        const result: { backgroundImage?: string | null; backgroundImagePage2?: string | null } = {};
        const bgMeta = wrapper.querySelector('meta[name="background-image"]');
        const bg2Meta = wrapper.querySelector('meta[name="background-image-page2"]');
        if (bgMeta) {
            const bg = bgMeta.getAttribute('content');
            if (bg) try { result.backgroundImage = decodeURIComponent(bg); } catch { result.backgroundImage = bg; }
        }
        if (bg2Meta) {
            const bg2 = bg2Meta.getAttribute('content');
            if (bg2) try { result.backgroundImagePage2 = decodeURIComponent(bg2); } catch { result.backgroundImagePage2 = bg2; }
        }
        return result;
    }, []);

    // ─── Template Operations ─────────────────────────────────────────────

    const openEditor = (blocks: FormBlock[], name: string | null, doktypen: string[], bg: string | null, bg2: string | null) => {
        setItems(blocks);
        setCurrentTemplateName(name);
        setSelectedDokumenttypen(doktypen);
        setBackgroundImage(bg);
        setBackgroundImagePage2(bg2);
        setSelectedId(null);
        setActivePage(1);
        setView('editor');
    };

    const handleCreateNew = () => {
        openEditor(
            DEFAULT_ITEMS.map(i => ({ ...i, id: uid() })),
            null,
            [],
            null,
            null
        );
    };

    const handleEditTemplate = async (name: string) => {
        try {
            showStatus('Vorlage wird geladen...', 'info');
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`);
            if (!res.ok) throw new Error();
            const data: FormTemplate = await res.json();
            const blocks = deserialize(data.html);
            const meta = parseMetadata(data.html);
            openEditor(
                blocks,
                data.name || name,
                data.assignedDokumenttypen || [],
                meta.backgroundImage || null,
                meta.backgroundImagePage2 || null
            );
            showStatus(`Vorlage "${name}" geladen.`, 'success');
        } catch {
            showStatus('Vorlage konnte nicht geladen werden.', 'error');
        }
    };

    const handleDuplicateTemplate = async (name: string) => {
        try {
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}/copy`, { method: 'POST' });
            if (!res.ok) throw new Error();
            showStatus(`Vorlage "${name}" wurde dupliziert.`, 'success');
            loadTemplateList();
        } catch {
            // Fallback: load template and save as copy
            try {
                const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`);
                if (!res.ok) throw new Error();
                const data: FormTemplate = await res.json();
                const copyName = `${name} (Kopie)`;
                const saveRes = await fetch('/api/formulare/templates', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: copyName, html: data.html, assignedDokumenttypen: data.assignedDokumenttypen })
                });
                if (!saveRes.ok) throw new Error();
                showStatus(`Vorlage als "${copyName}" dupliziert.`, 'success');
                loadTemplateList();
            } catch {
                showStatus('Vorlage konnte nicht dupliziert werden.', 'error');
            }
        }
    };

    const handleDeleteTemplate = async (name: string) => {
        try {
            await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`, { method: 'DELETE' });
            showStatus(`Vorlage "${name}" wurde gelöscht.`, 'success');
            loadTemplateList();
        } catch {
            showStatus('Vorlage konnte nicht gelöscht werden.', 'error');
        }
    };

    const handleSave = async () => {
        if (currentTemplateName) {
            await saveNamedTemplate(currentTemplateName);
        } else {
            setShowSaveModal(true);
        }
    };

    const saveNamedTemplate = async (name: string) => {
        try {
            showStatus('Speichern...', 'info');
            const res = await fetch('/api/formulare/templates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, html: serialize(), assignedDokumenttypen: selectedDokumenttypen })
            });
            if (!res.ok) throw new Error();
            const data = await res.json();
            setCurrentTemplateName(data.name || name);
            showStatus(`Vorlage "${name}" gespeichert.`, 'success');
            setShowSaveModal(false);
            setSaveNameInput('');
        } catch {
            showStatus('Speichern fehlgeschlagen.', 'error');
        }
    };

    const handleRename = async (newName: string) => {
        if (currentTemplateName && currentTemplateName !== newName) {
            // Try backend rename first
            try {
                const res = await fetch(`/api/formulare/templates/${encodeURIComponent(currentTemplateName)}/rename`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ newName })
                });
                if (res.ok) {
                    setCurrentTemplateName(newName);
                    showStatus(`Umbenannt zu "${newName}".`, 'success');
                    return;
                }
            } catch { /* fallback below */ }
            // Fallback: save under new name
            setCurrentTemplateName(newName);
        } else {
            setCurrentTemplateName(newName);
        }
    };

    const handleReset = () => {
        setItems(DEFAULT_ITEMS.map(i => ({ ...i, id: uid() })));
        setSelectedId(null);
        setBackgroundImage(null);
        setBackgroundImagePage2(null);
        setActivePage(1);
    };

    // Keyboard shortcuts: Ctrl+Z (undo), Ctrl+D (duplicate)
    useEffect(() => {
        const handler = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
                e.preventDefault();
                handleUndo();
            }
            if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
                e.preventDefault();
                duplicateBlock();
            }
        };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    });  // no deps: always uses latest selectedId/items via closure

    const handleBack = () => {
        loadTemplateList();
        setView('gallery');
    };

    // ─── Block Operations ────────────────────────────────────────────────

    const addBlock = (block: FormBlock) => {
        setItems(prev => [...prev, block]);
        setSelectedId(block.id);
    };

    const updateBlock = (id: string, updates: Partial<FormBlock>) => {
        setItems(prev => prev.map(i => i.id === id ? { ...i, ...updates } : i));
    };

    const deleteBlock = (id: string) => {
        setItems(prev => prev.filter(i => i.id !== id));
        if (selectedId === id) setSelectedId(null);
    };

    const duplicateBlock = () => {
        const source = items.find(i => i.id === selectedId);
        if (!source) return;
        const newId = uid();
        const clone: FormBlock = {
            ...source,
            id: newId,
            x: source.x + 20,
            y: source.y + 20,
            z: Math.max(...items.map(i => i.z), 0) + 1
        };
        setItems(prev => [...prev, clone]);
        setSelectedId(newId);
    };

    const selectBlock = (id: string | null) => setSelectedId(id);

    const selectBlockWithPage = (id: string, page: number) => {
        setSelectedId(id);
        setActivePage(page);
    };

    const selectedItem = items.find(i => i.id === selectedId) || null;

    // ─── Render ──────────────────────────────────────────────────────────

    if (view === 'gallery') {
        return (
            <PageLayout
                ribbonCategory="Vorlagen-Center"
                title="FORMULARWESEN"
                subtitle="Erstelle und verwalte Dokumentvorlagen für Anfragen, Rechnungen und mehr."
                actions={
                    <Button size="sm" onClick={handleCreateNew}>
                        <Plus className="w-4 h-4 mr-1" />Neue Vorlage
                    </Button>
                }
            >
                {/* Status Toast */}
                {status && (
                    <div className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-xl shadow-lg text-sm font-medium transition-all duration-300 ${status.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200'
                        : status.type === 'error' ? 'bg-red-50 text-red-700 border border-red-200'
                            : 'bg-slate-50 text-slate-700 border border-slate-200'
                        }`}>
                        {status.message}
                    </div>
                )}

                {loadingTemplates ? (
                    <div className="flex items-center justify-center py-24">
                        <div className="w-8 h-8 border-2 border-rose-600 border-t-transparent rounded-full animate-spin" />
                    </div>
                ) : (
                    <TemplateGallery
                        templates={templates}
                        onCreateNew={handleCreateNew}
                        onEdit={handleEditTemplate}
                        onDuplicate={handleDuplicateTemplate}
                        onDelete={handleDeleteTemplate}
                        onRefresh={loadTemplateList}
                    />
                )}
            </PageLayout>
        );
    }

    // ─── Editor View ─────────────────────────────────────────────────────

    return (
        <div className="h-screen flex flex-col bg-slate-50 overflow-hidden">
            {/* Status Toast */}
            {status && (
                <div className={`fixed top-4 left-1/2 -translate-x-1/2 z-50 px-4 py-3 rounded-xl shadow-lg text-sm font-medium transition-all duration-300 ${status.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200'
                    : status.type === 'error' ? 'bg-red-50 text-red-700 border border-red-200'
                        : 'bg-slate-50 text-slate-700 border border-slate-200'
                    }`}>
                    {status.message}
                </div>
            )}

            {/* Toolbar */}
            <div className="flex-shrink-0 p-3 pb-0">
                <EditorToolbar
                    templateName={currentTemplateName}
                    onBack={handleBack}
                    onSave={handleSave}
                    onPreview={() => setShowPreview(true)}
                    onReset={handleReset}
                    onRename={handleRename}
                    onUndo={handleUndo}
                    canUndo={historyRef.current.length > 0}
                    onOpenTextbausteinDefaults={() => setShowTextbausteinDefaults(true)}
                    textbausteinDefaultsDisabled={!currentTemplateName}
                />
            </div>

            {/* Main Editor Area — 3 column layout */}
            <div className="flex-1 flex gap-3 p-3 min-h-0">
                {/* Left Sidebar */}
                <div className="w-64 flex-shrink-0 hidden lg:block">
                    <BlocksSidebar
                        items={items}
                        selectedId={selectedId}
                        activePage={activePage}
                        onSelectBlock={selectBlockWithPage}
                        onAddBlock={addBlock}
                        onInsertPlaceholder={(ph) => {
                            if (!selectedId) return;
                            const block = items.find(i => i.id === selectedId);
                            if (!block || block.type === 'table') return;
                            const current = block.content || '';
                            updateBlock(selectedId, { content: current + ph });
                        }}
                    />
                </div>

                {/* Canvas */}
                <div className="flex-1 min-w-0">
                    <EditorCanvas
                        items={items}
                        activePage={activePage}
                        selectedId={selectedId}
                        backgroundImage={backgroundImage}
                        backgroundImagePage2={backgroundImagePage2}
                        liveData={liveData}
                        onSelectBlock={selectBlock}
                        onUpdateBlock={updateBlock}
                        onDeleteBlock={deleteBlock}
                        onSetActivePage={setActivePage}
                        onSetBackgroundImage={setBackgroundImage}
                        onSetBackgroundImagePage2={setBackgroundImagePage2}
                    />
                </div>

                {/* Right Sidebar */}
                <div className="w-64 flex-shrink-0 hidden lg:block">
                    <PropertiesSidebar
                        selectedItem={selectedItem}
                        selectedDokumenttypen={selectedDokumenttypen}
                        onUpdateBlock={updateBlock}
                        onDeleteBlock={deleteBlock}
                        onUpdateDokumenttypen={setSelectedDokumenttypen}
                    />
                </div>
            </div>

            {/* Preview Modal */}
            {showPreview && (
                <PreviewModal
                    items={items}
                    backgroundImage={backgroundImage}
                    backgroundImagePage2={backgroundImagePage2}
                    liveData={liveData}
                    onClose={() => setShowPreview(false)}
                />
            )}

            {/* Standard-Texte je Dokumenttyp */}
            {showTextbausteinDefaults && (
                <TextbausteinDefaultsModal
                    templateName={currentTemplateName}
                    assignedDokumenttypen={selectedDokumenttypen}
                    onClose={() => setShowTextbausteinDefaults(false)}
                    onSaved={() => showStatus('Standard-Texte gespeichert.', 'success')}
                />
            )}

            {/* Save Modal */}
            {showSaveModal && (
                <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
                        <h3 className="text-lg font-bold text-slate-900 mb-1">Vorlage speichern</h3>
                        <p className="text-sm text-slate-500 mb-4">Gib deiner Vorlage einen Namen.</p>
                        <input
                            type="text"
                            value={saveNameInput}
                            onChange={e => setSaveNameInput(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter' && saveNameInput) saveNamedTemplate(saveNameInput); }}
                            placeholder="z.B. Rechnungsvorlage Standard"
                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 mb-4"
                            autoFocus
                        />
                        <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" onClick={() => { setShowSaveModal(false); setSaveNameInput(''); }}>
                                Abbrechen
                            </Button>
                            <Button size="sm" onClick={() => saveNameInput && saveNamedTemplate(saveNameInput)} disabled={!saveNameInput}>
                                Speichern
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
