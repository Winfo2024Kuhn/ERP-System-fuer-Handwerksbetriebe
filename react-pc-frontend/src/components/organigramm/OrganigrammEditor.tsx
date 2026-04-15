import { useState, useCallback, useEffect, useRef } from 'react';
import {
    ReactFlowProvider,
    useNodesState,
    useEdgesState,
    addEdge,
    type Connection,
    type Node,
    type Edge,
} from '@xyflow/react';
import { RefreshCw, GitBranch } from 'lucide-react';
import OrganigrammToolbar from './OrganigrammToolbar';
import OrganigrammSidebar from './OrganigrammSidebar';
import OrganigrammPropertiesSidebar from './OrganigrammPropertiesSidebar';
import OrganigrammCanvas from './OrganigrammCanvas';
import { getLayoutedElements } from './useOrganigrammLayout';
import { exportAsPng, exportAsPdf, printOrganigramm } from './organigramm-export';
import { useToast } from '../ui/toast';
import { useFeatures } from '../../hooks/useFeatures';
import { useGroupDocking } from './useGroupDocking';
import {
    GROUP_WIDTH,
    GROUP_HEADER_HEIGHT,
    CHILD_PADDING_TOP,
    GROUP_PADDING_X,
    NODE_DIMENSIONS,
    CHILD_GAP,
} from './organigramm-constants';
import type { Abteilung, Mitarbeiter, En1090Rolle } from '../../types';

const STORAGE_VERSION = 2;

interface OrganigrammEditorProps {
    onClose: () => void;
    /** Name of an existing organigramm to load, or null for a new one */
    initialName?: string | null;
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function ensureParentOrder(nodes: Node[]): Node[] {
    const groups: Node[] = [];
    const docked: Node[] = [];
    const free: Node[] = [];

    for (const n of nodes) {
        if (n.type === 'abteilungGroup') groups.push(n);
        else if (n.parentId) docked.push(n);
        else free.push(n);
    }

    return [...groups, ...docked, ...free];
}

/** Migrate v1 localStorage (old abteilung nodes + edges) → v2 (abteilungGroup + parentId) */
function migrateState(saved: Record<string, unknown>): { nodes: Node[]; edges: Edge[] } {
    const version = (saved.version as number) ?? 1;
    let nodes = (saved.nodes as Node[]) ?? [];
    let edges = (saved.edges as Edge[]) ?? [];

    if (version >= STORAGE_VERSION) return { nodes, edges };

    // v1 → v2: Convert abteilung nodes to abteilungGroup
    nodes = nodes.map(n => {
        if (n.type === 'abteilung') {
            return {
                ...n,
                type: 'abteilungGroup',
                style: { width: GROUP_WIDTH, height: 92 },
            };
        }
        return n;
    });

    // Find edges from group to non-group → convert to parentId
    const intraGroupEdges = new Set<string>();
    for (const edge of edges) {
        const source = nodes.find(n => n.id === edge.source);
        const target = nodes.find(n => n.id === edge.target);

        // Group → child
        if (source?.type === 'abteilungGroup' && target && target.type !== 'abteilungGroup') {
            target.parentId = edge.source;
            target.data = { ...target.data, isDocked: true };
            intraGroupEdges.add(edge.id);
        }
        // child → group
        if (target?.type === 'abteilungGroup' && source && source.type !== 'abteilungGroup') {
            source.parentId = edge.target;
            source.data = { ...source.data, isDocked: true };
            intraGroupEdges.add(edge.id);
        }
    }

    // Recalculate child positions within groups
    const groups = nodes.filter(n => n.type === 'abteilungGroup');
    for (const group of groups) {
        const children = nodes.filter(n => n.parentId === group.id);
        let y = GROUP_HEADER_HEIGHT + CHILD_PADDING_TOP;
        for (const child of children) {
            child.position = { x: GROUP_PADDING_X, y };
            y += (NODE_DIMENSIONS[child.type ?? '']?.height ?? 80) + CHILD_GAP;
        }
    }

    // Remove intra-group edges, keep inter-group edges
    edges = edges.filter(e => !intraGroupEdges.has(e.id));

    return { nodes: ensureParentOrder(nodes), edges };
}

// ─── Main Component ─────────────────────────────────────────────────────────

function OrganigrammEditorInner({ onClose, initialName }: OrganigrammEditorProps) {
    const toast = useToast();
    const features = useFeatures();
    const canvasRef = useRef<HTMLDivElement>(null);

    // Data from APIs
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const [en1090Rollen, setEn1090Rollen] = useState<En1090Rolle[]>([]);
    const [loading, setLoading] = useState(true);

    // ReactFlow state
    const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
    const [currentName, setCurrentName] = useState<string | null>(initialName ?? null);

    // Save modal state
    const [showSaveModal, setShowSaveModal] = useState(false);
    const [saveNameInput, setSaveNameInput] = useState('');

    const selectedNode = nodes.find(n => n.id === selectedNodeId) ?? null;

    // ─── Load API Data ───────────────────────────────────────────────────

    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const fetches = [
                fetch('/api/abteilungen').then(r => r.ok ? r.json() : []),
                fetch('/api/mitarbeiter').then(r => r.ok ? r.json() : []),
            ];
            if (features.en1090) {
                fetches.push(fetch('/api/en1090/rollen').then(r => r.ok ? r.json() : []));
            }
            const results = await Promise.all(fetches);
            setAbteilungen(Array.isArray(results[0]) ? results[0] : []);
            setMitarbeiter(Array.isArray(results[1]) ? results[1] : []);
            if (features.en1090 && results[2]) {
                setEn1090Rollen(Array.isArray(results[2]) ? results[2] : []);
            }
        } catch (e) {
            console.error('Fehler beim Laden der Daten', e);
        } finally {
            setLoading(false);
        }
    }, [features.en1090]);

    useEffect(() => { loadData(); }, [loadData]);

    // ─── Load Organigramm from Backend ────────────────────────────────────

    useEffect(() => {
        if (!initialName) return;
        (async () => {
            try {
                const res = await fetch(`/api/organigramme/${encodeURIComponent(initialName)}`);
                if (!res.ok) return;
                const data = await res.json();
                const parsed = JSON.parse(data.content);
                const { nodes: savedNodes, edges: savedEdges } = migrateState(parsed);
                if (Array.isArray(savedNodes)) setNodes(savedNodes);
                if (Array.isArray(savedEdges)) setEdges(savedEdges);
            } catch { /* ignore corrupt data */ }
        })();
    }, [initialName, setNodes, setEdges]);

    const saveToBackend = useCallback(async (name: string) => {
        try {
            const content = JSON.stringify({ nodes, edges, version: STORAGE_VERSION });
            const res = await fetch('/api/organigramme', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, content }),
            });
            if (!res.ok) throw new Error();
            const saved = await res.json();
            setCurrentName(saved.name);
            toast.success(`„${saved.name}" gespeichert`);
            setShowSaveModal(false);
            setSaveNameInput('');
        } catch {
            toast.error('Fehler beim Speichern');
        }
    }, [nodes, edges, toast]);

    const handleSave = useCallback(() => {
        if (currentName) {
            saveToBackend(currentName);
        } else {
            setShowSaveModal(true);
        }
    }, [currentName, saveToBackend]);

    // ─── Group Docking System ───────────────────────────────────────────

    const { onNodeDrag, onNodeDragStop, dockPreview } =
        useGroupDocking(nodes, edges, setNodes, setEdges);

    // ─── Edge Operations ──────────────────────────────────────────────────

    const handleDeleteEdge = useCallback(
        (id: string) => {
            setEdges(eds => eds.filter(e => e.id !== id));
        },
        [setEdges]
    );

    const onConnect = useCallback(
        (connection: Connection) => {
            // Only allow connections between top-level nodes (groups or free nodes)
            const sourceNode = nodes.find(n => n.id === connection.source);
            const targetNode = nodes.find(n => n.id === connection.target);

            if (sourceNode?.parentId || targetNode?.parentId) {
                toast.warning('Verbindungen nur zwischen Gruppen oder freien Elementen möglich');
                return;
            }

            if (connection.source === connection.target) return;

            setEdges(eds => addEdge(connection, eds));
        },
        [nodes, setEdges, toast]
    );

    // ─── Drop Node ──────────────────────────────────────────────────────

    const handleDropNode = useCallback(
        (data: Record<string, unknown>, position: { x: number; y: number }) => {
            const entityType = data.entityType as string;
            const entityId = data.entityId as number;

            if (entityType === 'abteilung') {
                // Always create as group container
                const nodeId = `org-abteilungGroup-${entityId}`;

                if (nodes.some(n => n.id === nodeId)) {
                    toast.warning('Diese Abteilung ist bereits im Organigramm');
                    return;
                }

                const newNode: Node = {
                    id: nodeId,
                    type: 'abteilungGroup',
                    position,
                    style: { width: GROUP_WIDTH, height: 92 },
                    data: {
                        label: data.label as string,
                        abteilungId: entityId,
                        childCount: 0,
                    },
                };

                setNodes(nds => ensureParentOrder([...nds, newNode]));
                return;
            }

            // Mitarbeiter / EN1090 Rolle
            const nodeId = entityType === 'mitarbeiter'
                ? `org-mitarbeiter-${entityId}-${Date.now()}`
                : `org-${entityType}-${entityId}`;

            if (entityType !== 'mitarbeiter' && nodes.some(n => n.id === nodeId)) {
                toast.warning('Diese Rolle ist bereits im Organigramm');
                return;
            }

            // Check if dropped on/near a group
            const groups = nodes.filter(n => n.type === 'abteilungGroup');
            let targetGroup: Node | null = null;
            const nodeW = NODE_DIMENSIONS[entityType]?.width ?? 260;
            const nodeH = NODE_DIMENSIONS[entityType]?.height ?? 80;

            for (const group of groups) {
                const gw = (group.style?.width as number) ?? GROUP_WIDTH;
                const gh = (group.style?.height as number) ?? 92;
                const margin = 50;

                if (
                    position.x + nodeW >= group.position.x - margin &&
                    position.x <= group.position.x + gw + margin &&
                    position.y + nodeH >= group.position.y - margin &&
                    position.y <= group.position.y + gh + margin
                ) {
                    targetGroup = group;
                    break;
                }
            }

            if (targetGroup) {
                // Dock into group
                const children = nodes.filter(n => n.parentId === targetGroup!.id);
                let y = GROUP_HEADER_HEIGHT + CHILD_PADDING_TOP;
                for (const child of children.sort((a, b) => a.position.y - b.position.y)) {
                    y += (NODE_DIMENSIONS[child.type ?? '']?.height ?? 80) + CHILD_GAP;
                }

                const newNode: Node = {
                    id: nodeId,
                    type: entityType,
                    position: { x: GROUP_PADDING_X, y },
                    parentId: targetGroup.id,
                    data: {
                        label: data.label as string,
                        isDocked: true,
                        ...(entityType === 'mitarbeiter' && {
                            qualifikation: data.qualifikation ?? null,
                            en1090RolleNames: data.en1090RolleNames ?? null,
                            aktiv: data.aktiv ?? true,
                        }),
                        ...(entityType === 'en1090rolle' && {
                            beschreibung: data.beschreibung ?? null,
                        }),
                    },
                };

                setNodes(nds => ensureParentOrder([...nds, newNode]));
            } else {
                // Free-standing node
                const newNode: Node = {
                    id: nodeId,
                    type: entityType,
                    position,
                    data: {
                        label: data.label as string,
                        isDocked: false,
                        ...(entityType === 'mitarbeiter' && {
                            qualifikation: data.qualifikation ?? null,
                            en1090RolleNames: data.en1090RolleNames ?? null,
                            aktiv: data.aktiv ?? true,
                        }),
                        ...(entityType === 'en1090rolle' && {
                            beschreibung: data.beschreibung ?? null,
                        }),
                    },
                };

                setNodes(nds => [...nds, newNode]);
            }
        },
        [nodes, setNodes, toast]
    );

    // ─── Delete Node ────────────────────────────────────────────────────

    const handleDeleteNode = useCallback(
        (id: string) => {
            const node = nodes.find(n => n.id === id);

            if (node?.type === 'abteilungGroup') {
                // Free all children instead of deleting them
                setNodes(nds => ensureParentOrder(
                    nds
                        .filter(n => n.id !== id)
                        .map(n => {
                            if (n.parentId !== id) return n;
                            return {
                                ...n,
                                parentId: undefined,
                                extent: undefined,
                                position: {
                                    x: (node.position.x ?? 0) + n.position.x,
                                    y: (node.position.y ?? 0) + n.position.y,
                                },
                                data: { ...n.data, isDocked: false },
                            };
                        })
                ));
            } else {
                setNodes(nds => nds.filter(n => n.id !== id));
            }

            setEdges(eds => eds.filter(e => e.source !== id && e.target !== id));
            if (selectedNodeId === id) setSelectedNodeId(null);
        },
        [nodes, setNodes, setEdges, selectedNodeId]
    );

    // ─── Layout ──────────────────────────────────────────────────────────

    const handleAutoLayout = useCallback(
        (direction: 'TB' | 'LR') => {
            const { nodes: layouted, edges: layoutedEdges } = getLayoutedElements(nodes, edges, direction);
            setNodes([...layouted]);
            setEdges([...layoutedEdges]);
        },
        [nodes, edges, setNodes, setEdges]
    );

    // ─── Export ──────────────────────────────────────────────────────────

    const getExportElement = (): HTMLElement | null => {
        const el = canvasRef.current?.querySelector('.react-flow__viewport') as HTMLElement | null;
        return el;
    };

    const handleExportPng = useCallback(async () => {
        const el = getExportElement();
        if (!el) { toast.error('Export fehlgeschlagen'); return; }
        try {
            await exportAsPng(el);
            toast.success('Bild heruntergeladen');
        } catch { toast.error('Export fehlgeschlagen'); }
    }, [toast]);

    const handleExportPdf = useCallback(async () => {
        const el = getExportElement();
        if (!el) { toast.error('Export fehlgeschlagen'); return; }
        try {
            await exportAsPdf(el);
            toast.success('PDF heruntergeladen');
        } catch { toast.error('Export fehlgeschlagen'); }
    }, [toast]);

    // ─── Connection Validation ──────────────────────────────────────────

    const isValidConnection = useCallback(
        (connection: Connection | Edge) => {
            const sourceNode = nodes.find(n => n.id === connection.source);
            const targetNode = nodes.find(n => n.id === connection.target);

            // Docked children cannot have edges
            if (sourceNode?.parentId || targetNode?.parentId) return false;

            // No self-connections
            if (connection.source === connection.target) return false;

            return true;
        },
        [nodes]
    );

    // ─── Render ──────────────────────────────────────────────────────────

    if (loading) {
        return (
            <div className="h-screen flex items-center justify-center bg-slate-50">
                <RefreshCw className="w-8 h-8 animate-spin text-rose-600" />
            </div>
        );
    }

    return (
        <div className="h-screen flex flex-col bg-slate-50 overflow-hidden">
            {/* Toolbar */}
            <div className="flex-shrink-0 p-3 pb-0">
                <OrganigrammToolbar
                    name={currentName}
                    onAutoLayout={handleAutoLayout}
                    onExportPng={handleExportPng}
                    onExportPdf={handleExportPdf}
                    onPrint={printOrganigramm}
                    onSave={handleSave}
                    onClose={onClose}
                />
            </div>

            {/* Main 3-column layout */}
            <div className="flex-1 flex gap-3 p-3 min-h-0">
                {/* Left Sidebar */}
                <div className="w-64 flex-shrink-0 hidden lg:block">
                    <OrganigrammSidebar
                        abteilungen={abteilungen}
                        mitarbeiter={mitarbeiter}
                        en1090Rollen={en1090Rollen}
                        showEn1090={features.en1090}
                    />
                </div>

                {/* Canvas */}
                <div className="flex-1 min-w-0" ref={canvasRef}>
                    <OrganigrammCanvas
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onEdgesChange={onEdgesChange}
                        onConnect={onConnect}
                        onDropNode={handleDropNode}
                        onNodeClick={(_e, node) => setSelectedNodeId(node.id)}
                        onPaneClick={() => setSelectedNodeId(null)}
                        onDeleteNode={handleDeleteNode}
                        onDeleteEdge={handleDeleteEdge}
                        dockPreview={dockPreview}
                        onNodeDrag={onNodeDrag}
                        onNodeDragStop={onNodeDragStop}
                        isValidConnection={isValidConnection}
                    />
                </div>

                {/* Right Sidebar */}
                <div className="w-64 flex-shrink-0 hidden lg:block">
                    <OrganigrammPropertiesSidebar
                        selectedNode={selectedNode}
                        onDeleteNode={handleDeleteNode}
                    />
                </div>
            </div>

            {/* Empty state hint */}
            {nodes.length === 0 && (
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-10">
                    <div className="bg-white/90 backdrop-blur-sm border border-slate-200 rounded-2xl shadow-lg p-8 text-center max-w-sm pointer-events-none">
                        <GitBranch className="w-10 h-10 text-rose-300 mx-auto mb-3" />
                        <h3 className="text-lg font-bold text-slate-800 mb-1">Organigramm erstellen</h3>
                        <p className="text-sm text-slate-500">
                            Ziehe Abteilungen, Mitarbeiter oder Rollen aus der linken Seitenleiste auf die Zeichenfläche. Verbinde sie durch Ziehen von einem Punkt zum anderen.
                        </p>
                    </div>
                </div>
            )}

            {/* Save Modal */}
            {showSaveModal && (
                <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
                        <h3 className="text-lg font-bold text-slate-900 mb-1">Organigramm speichern</h3>
                        <p className="text-sm text-slate-500 mb-4">Gib deinem Organigramm einen Namen.</p>
                        <input
                            type="text"
                            value={saveNameInput}
                            onChange={e => setSaveNameInput(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter' && saveNameInput.trim()) saveToBackend(saveNameInput.trim()); }}
                            placeholder="z.B. Fertigung & Qualitätssicherung"
                            className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 mb-4"
                            autoFocus
                        />
                        <div className="flex justify-end gap-2">
                            <button
                                onClick={() => { setShowSaveModal(false); setSaveNameInput(''); }}
                                className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
                            >
                                Abbrechen
                            </button>
                            <button
                                onClick={() => saveNameInput.trim() && saveToBackend(saveNameInput.trim())}
                                disabled={!saveNameInput.trim()}
                                className="px-4 py-2 text-sm font-medium text-white bg-rose-600 hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
                            >
                                Speichern
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

/** Wrapper that provides ReactFlowProvider context */
export default function OrganigrammEditor(props: OrganigrammEditorProps) {
    return (
        <ReactFlowProvider>
            <OrganigrammEditorInner {...props} />
        </ReactFlowProvider>
    );
}
