import dagre from '@dagrejs/dagre';
import { type Node, type Edge } from '@xyflow/react';
import { NODE_DIMENSIONS } from './organigramm-constants';

const DEFAULT_DIM = { width: 220, height: 100 };

export function getLayoutedElements(
    nodes: Node[],
    edges: Edge[],
    direction: 'TB' | 'LR' = 'TB'
): { nodes: Node[]; edges: Edge[] } {
    if (nodes.length === 0) return { nodes, edges };

    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));
    g.setGraph({ rankdir: direction, nodesep: 60, ranksep: 80 });

    nodes.forEach((node) => {
        const dim = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIM;
        g.setNode(node.id, { width: dim.width, height: dim.height });
    });

    edges.forEach((edge) => {
        g.setEdge(edge.source, edge.target);
    });

    dagre.layout(g);

    const layoutedNodes = nodes.map((node) => {
        const pos = g.node(node.id);
        const dim = NODE_DIMENSIONS[node.type ?? ''] ?? DEFAULT_DIM;
        return {
            ...node,
            position: {
                x: pos.x - dim.width / 2,
                y: pos.y - dim.height / 2,
            },
        };
    });

    return { nodes: layoutedNodes, edges };
}
