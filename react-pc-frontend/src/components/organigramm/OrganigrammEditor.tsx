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
import type { Abteilung, Mitarbeiter, En1090Rolle } from '../../types';

const STORAGE_KEY = 'organigramm-state';

interface OrganigrammEditorProps {
    onBack: () => void;
}

function OrganigrammEditorInner({ onBack }: OrganigrammEditorProps) {
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

    // ─── localStorage persistence ────────────────────────────────────────

    useEffect(() => {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const { nodes: savedNodes, edges: savedEdges } = JSON.parse(saved);
                if (Array.isArray(savedNodes)) setNodes(savedNodes);
                if (Array.isArray(savedEdges)) setEdges(savedEdges);
            }
        } catch { /* ignore corrupt data */ }
    }, [setNodes, setEdges]);

    const handleSave = useCallback(() => {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify({ nodes, edges }));
            toast.success('Organigramm gespeichert');
        } catch {
            toast.error('Fehler beim Speichern');
        }
    }, [nodes, edges, toast]);

    // ─── Node Operations ─────────────────────────────────────────────────

    const onConnect = useCallback(
        (connection: Connection) => {
            setEdges(eds => addEdge(connection, eds));
        },
        [setEdges]
    );

    const handleDropNode = useCallback(
        (data: Record<string, unknown>, position: { x: number; y: number }) => {
            const entityType = data.entityType as string;
            const entityId = data.entityId as number;
            const nodeId = `org-${entityType}-${entityId}`;

            // Duplicate check
            if (nodes.some(n => n.id === nodeId)) {
                const label = entityType === 'abteilung' ? 'Diese Abteilung'
                    : entityType === 'mitarbeiter' ? 'Dieser Mitarbeiter'
                    : 'Diese Rolle';
                toast.warning(`${label} ist bereits im Organigramm`);
                return;
            }

            const newNode: Node = {
                id: nodeId,
                type: entityType,
                position,
                data: {
                    label: data.label as string,
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
        },
        [nodes, setNodes, toast]
    );

    const handleDeleteNode = useCallback(
        (id: string) => {
            setNodes(nds => nds.filter(n => n.id !== id));
            setEdges(eds => eds.filter(e => e.source !== id && e.target !== id));
            if (selectedNodeId === id) setSelectedNodeId(null);
        },
        [setNodes, setEdges, selectedNodeId]
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
                    onAutoLayout={handleAutoLayout}
                    onExportPng={handleExportPng}
                    onExportPdf={handleExportPdf}
                    onPrint={printOrganigramm}
                    onSave={handleSave}
                    onBack={onBack}
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
