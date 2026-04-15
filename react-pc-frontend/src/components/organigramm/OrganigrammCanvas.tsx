import { useCallback, type DragEvent, type MouseEvent } from 'react';
import {
    ReactFlow,
    Background,
    Controls,
    MiniMap,
    ConnectionMode,
    ConnectionLineType,
    type Node,
    type Edge,
    type OnNodesChange,
    type OnEdgesChange,
    type Connection,
    useReactFlow,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes, edgeTypes } from './organigramm-constants';
import type { DockPreview } from './useGroupDocking';

interface OrganigrammCanvasProps {
    nodes: Node[];
    edges: Edge[];
    onNodesChange: OnNodesChange;
    onEdgesChange: OnEdgesChange;
    onConnect: (connection: Connection) => void;
    onDropNode: (data: Record<string, unknown>, position: { x: number; y: number }) => void;
    onNodeClick: (event: MouseEvent, node: Node) => void;
    onPaneClick: () => void;
    onDeleteNode: (id: string) => void;
    onDeleteEdge: (id: string) => void;
    dockPreview: DockPreview | null;
    onNodeDrag: (event: MouseEvent, node: Node) => void;
    onNodeDragStop: (event: MouseEvent, node: Node) => void;
    isValidConnection: (connection: Connection | Edge) => boolean;
}

export default function OrganigrammCanvas({
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    onConnect,
    onDropNode,
    onNodeClick,
    onPaneClick,
    onDeleteNode,
    onDeleteEdge,
    dockPreview,
    onNodeDrag,
    onNodeDragStop,
    isValidConnection,
}: OrganigrammCanvasProps) {
    const reactFlowInstance = useReactFlow();

    const nodesWithMeta = nodes.map(n => ({
        ...n,
        data: {
            ...n.data,
            onDelete: onDeleteNode,
            isDockTarget: dockPreview?.groupId === n.id,
        },
    }));

    const edgesWithDelete = edges.map(e => ({
        ...e,
        data: { ...e.data, onDeleteEdge },
    }));

    const handleDragOver = useCallback((e: DragEvent) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    }, []);

    const handleDrop = useCallback(
        (e: DragEvent) => {
            e.preventDefault();
            const raw = e.dataTransfer.getData('application/orgnode');
            if (!raw) return;

            try {
                const data = JSON.parse(raw);
                const position = reactFlowInstance.screenToFlowPosition({
                    x: e.clientX,
                    y: e.clientY,
                });
                onDropNode(data, position);
            } catch { /* ignore invalid data */ }
        },
        [reactFlowInstance, onDropNode]
    );

    return (
        <div className="h-full bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <ReactFlow
                nodes={nodesWithMeta}
                edges={edgesWithDelete}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onDrop={handleDrop}
                onDragOver={handleDragOver}
                onNodeClick={onNodeClick}
                onPaneClick={onPaneClick}
                onNodeDrag={onNodeDrag}
                onNodeDragStop={onNodeDragStop}
                isValidConnection={isValidConnection}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                fitView
                connectionMode={ConnectionMode.Strict}
                connectionLineType={ConnectionLineType.SmoothStep}
                defaultEdgeOptions={{
                    type: 'organigramm',
                    style: { stroke: '#94a3b8', strokeWidth: 2 },
                    animated: false,
                }}
                proOptions={{ hideAttribution: true }}
                className="organigramm-flow"
            >
                <Background color="#e2e8f0" gap={20} size={1} />
                <Controls
                    showInteractive={false}
                    className="!bg-white !border-slate-200 !rounded-xl !shadow-sm"
                />
                <MiniMap
                    nodeColor={(n) => {
                        if (n.type === 'abteilungGroup' || n.type === 'abteilung') return '#f43f5e';
                        if (n.type === 'mitarbeiter') return '#64748b';
                        return '#f59e0b';
                    }}
                    maskColor="rgba(241, 245, 249, 0.7)"
                    className="!bg-white !border-slate-200 !rounded-xl !shadow-sm"
                />
            </ReactFlow>
        </div>
    );
}
