import dagre from '@dagrejs/dagre';
import { type Node, type Edge } from '@xyflow/react';
import { NODE_DIMENSIONS, GROUP_WIDTH } from './organigramm-constants';

const DEFAULT_DIM = { width: 220, height: 100 };

export function getLayoutedElements(
    nodes: Node[],
    edges: Edge[],
    direction: 'TB' | 'LR' = 'TB'
): { nodes: Node[]; edges: Edge[] } {
    if (nodes.length === 0) return { nodes, edges };

    // Only layout top-level nodes (groups + free nodes, not docked children)
    const topLevelNodes = nodes.filter(n => !n.parentId);
    const childNodes = nodes.filter(n => !!n.parentId);

    // Only use edges between top-level nodes
    const topLevelIds = new Set(topLevelNodes.map(n => n.id));
    const topLevelEdges = edges.filter(
        e => topLevelIds.has(e.source) && topLevelIds.has(e.target)
    );

    if (topLevelNodes.length === 0) return { nodes, edges };

    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));
    g.setGraph({ rankdir: direction, nodesep: 60, ranksep: 80 });

    topLevelNodes.forEach((node) => {
        let dim: { width: number; height: number };

        if (node.type === 'abteilungGroup') {
            // Use actual dynamic height for groups
            dim = {
                width: (node.style?.width as number) ?? GROUP_WIDTH,
                height: (node.style?.height as number) ?? 92,
            };
        } else {
            dim = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIM;
        }

        g.setNode(node.id, { width: dim.width, height: dim.height });
    });

    topLevelEdges.forEach((edge) => {
        g.setEdge(edge.source, edge.target);
    });

    dagre.layout(g);

    const layoutedTopLevel = topLevelNodes.map((node) => {
        const pos = g.node(node.id);
        let dim: { width: number; height: number };

        if (node.type === 'abteilungGroup') {
            dim = {
                width: (node.style?.width as number) ?? GROUP_WIDTH,
                height: (node.style?.height as number) ?? 92,
            };
        } else {
            dim = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIM;
        }

        return {
            ...node,
            position: {
                x: pos.x - dim.width / 2,
                y: pos.y - dim.height / 2,
            },
        };
    });

    // Child nodes stay unchanged (relative positions within their parent)
    return { nodes: [...layoutedTopLevel, ...childNodes], edges };
}
