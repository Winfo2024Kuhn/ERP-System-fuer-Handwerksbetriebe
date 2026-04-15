import { useState, useCallback, useRef, useEffect } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { MouseEvent } from 'react';
import {
    GROUP_WIDTH,
    GROUP_PADDING_X,
    GROUP_HEADER_HEIGHT,
    CHILD_GAP,
    CHILD_PADDING_TOP,
    GROUP_PADDING_BOTTOM,
    DOCK_PROXIMITY,
    NODE_DIMENSIONS,
} from './organigramm-constants';

// ─── Types ──────────────────────────────────────────────────────────────────

export interface DockPreview {
    groupId: string;
    insertIndex: number;
}

type SetNodes = (updater: Node[] | ((nodes: Node[]) => Node[])) => void;
type SetEdges = (updater: Edge[] | ((edges: Edge[]) => Edge[])) => void;

// ─── Helpers ────────────────────────────────────────────────────────────────

function getNodeHeight(node: Node): number {
    return NODE_DIMENSIONS[node.type ?? '']?.height ?? 80;
}

/** Ensure parent nodes come before their children in the array (React Flow requirement) */
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

/** Calculate the height of a group container based on its children */
function calcGroupHeight(children: Node[]): number {
    if (children.length === 0) return 92; // empty group with placeholder

    let h = GROUP_HEADER_HEIGHT + CHILD_PADDING_TOP;
    for (let i = 0; i < children.length; i++) {
        h += getNodeHeight(children[i]);
        if (i < children.length - 1) h += CHILD_GAP;
    }
    h += GROUP_PADDING_BOTTOM;
    return h;
}

/** Check if a point is within/near a group's bounding box */
function isNearGroup(
    nodeX: number,
    nodeY: number,
    nodeW: number,
    nodeH: number,
    group: Node,
): boolean {
    const gw = (group.style?.width as number) ?? GROUP_WIDTH;
    const gh = (group.style?.height as number) ?? 92;
    const gx = group.position.x;
    const gy = group.position.y;

    // Check overlap with proximity margin
    const margin = DOCK_PROXIMITY;
    return !(
        nodeX + nodeW < gx - margin ||
        nodeX > gx + gw + margin ||
        nodeY + nodeH < gy - margin ||
        nodeY > gy + gh + margin
    );
}

// ─── Hook ───────────────────────────────────────────────────────────────────

export function useGroupDocking(
    nodes: Node[],
    edges: Edge[],
    setNodes: SetNodes,
    setEdges: SetEdges,
) {
    const [dockPreview, setDockPreview] = useState<DockPreview | null>(null);
    const prevHeightsRef = useRef<Map<string, number>>(new Map());

    // Refs for latest values (avoid stale closures)
    const nodesRef = useRef(nodes);
    const edgesRef = useRef(edges);

    useEffect(() => { nodesRef.current = nodes; }, [nodes]);
    useEffect(() => { edgesRef.current = edges; }, [edges]);

    // ─── Auto-Resize: recalculate group heights when nodes change ───────

    useEffect(() => {
        const currentNodes = nodes;
        const groups = currentNodes.filter(n => n.type === 'abteilungGroup');
        if (groups.length === 0) return;

        let needsUpdate = false;
        const updatedMap = new Map<string, { height: number; children: Node[] }>();

        for (const group of groups) {
            const children = currentNodes.filter(n => n.parentId === group.id);
            const newHeight = calcGroupHeight(children);
            const prevHeight = prevHeightsRef.current.get(group.id) ?? -1;

            if (Math.abs(newHeight - prevHeight) > 1) {
                needsUpdate = true;
            }
            updatedMap.set(group.id, { height: newHeight, children });
        }

        if (!needsUpdate) return;

        // Update heights cache
        for (const [id, { height }] of updatedMap) {
            prevHeightsRef.current.set(id, height);
        }

        setNodes((nds: Node[]) => {
            let changed = false;
            const result = nds.map(n => {
                if (n.type === 'abteilungGroup') {
                    const info = updatedMap.get(n.id);
                    if (!info) return n;

                    const currentH = (n.style?.height as number) ?? 92;
                    const childCount = info.children.length;

                    if (Math.abs(info.height - currentH) > 1 || (n.data as Record<string, unknown>).childCount !== childCount) {
                        changed = true;
                        return {
                            ...n,
                            style: { ...n.style, width: GROUP_WIDTH, height: info.height },
                            data: { ...n.data, childCount },
                        };
                    }
                    return n;
                }

                // Re-position children within their group
                if (n.parentId) {
                    const info = updatedMap.get(n.parentId);
                    if (!info) return n;

                    const childrenSorted = [...info.children].sort(
                        (a, b) => a.position.y - b.position.y
                    );
                    const idx = childrenSorted.findIndex(c => c.id === n.id);
                    if (idx < 0) return n;

                    let y = GROUP_HEADER_HEIGHT + CHILD_PADDING_TOP;
                    for (let i = 0; i < idx; i++) {
                        y += getNodeHeight(childrenSorted[i]) + CHILD_GAP;
                    }

                    if (Math.abs(n.position.y - y) > 1 || Math.abs(n.position.x - GROUP_PADDING_X) > 1) {
                        changed = true;
                        return {
                            ...n,
                            position: { x: GROUP_PADDING_X, y },
                        };
                    }
                }

                return n;
            });

            return changed ? ensureParentOrder(result) : nds;
        });
    }, [nodes, setNodes]);

    // ─── onNodeDrag: dock preview + undock detection ────────────────────

    const onNodeDrag = useCallback(
        (_event: MouseEvent, draggedNode: Node) => {
            const currentNodes = nodesRef.current;

            // Only process mitarbeiter / en1090rolle nodes
            if (draggedNode.type === 'abteilungGroup') {
                setDockPreview(null);
                return;
            }

            const isDocked = !!draggedNode.parentId;
            const nodeW = NODE_DIMENSIONS[draggedNode.type ?? '']?.width ?? 260;
            const nodeH = getNodeHeight(draggedNode);

            if (isDocked) {
                // ── Undock detection: if dragged far from parent boundaries ──
                const parent = currentNodes.find(n => n.id === draggedNode.parentId);
                if (parent) {
                    const absX = parent.position.x + draggedNode.position.x;
                    const absY = parent.position.y + draggedNode.position.y;
                    const gw = (parent.style?.width as number) ?? GROUP_WIDTH;
                    const gh = (parent.style?.height as number) ?? 92;

                    const outsideX = absX + nodeW < parent.position.x - 20 || absX > parent.position.x + gw + 20;
                    const outsideY = absY + nodeH < parent.position.y - 20 || absY > parent.position.y + gh + 20;

                    if (outsideX || outsideY) {
                        // Undock: convert to absolute position and remove parentId
                        setNodes((nds: Node[]) => {
                            const parentNode = nds.find(n => n.id === draggedNode.parentId);
                            if (!parentNode) return nds;

                            return ensureParentOrder(
                                nds.map(n => {
                                    if (n.id !== draggedNode.id) return n;
                                    return {
                                        ...n,
                                        parentId: undefined,
                                        extent: undefined,
                                        position: {
                                            x: parentNode.position.x + n.position.x,
                                            y: parentNode.position.y + n.position.y,
                                        },
                                        data: { ...n.data, isDocked: false },
                                    };
                                })
                            );
                        });
                        setDockPreview(null);
                    }
                }
                return;
            }

            // ── Free node: check if near any group for dock preview ──
            const groups = currentNodes.filter(n => n.type === 'abteilungGroup');
            let bestGroup: string | null = null;
            let bestDist = Infinity;

            for (const group of groups) {
                if (isNearGroup(draggedNode.position.x, draggedNode.position.y, nodeW, nodeH, group)) {
                    const gCenterX = group.position.x + ((group.style?.width as number) ?? GROUP_WIDTH) / 2;
                    const gCenterY = group.position.y + ((group.style?.height as number) ?? 92) / 2;
                    const nCenterX = draggedNode.position.x + nodeW / 2;
                    const nCenterY = draggedNode.position.y + nodeH / 2;
                    const dist = Math.sqrt((gCenterX - nCenterX) ** 2 + (gCenterY - nCenterY) ** 2);

                    if (dist < bestDist) {
                        bestDist = dist;
                        bestGroup = group.id;
                    }
                }
            }

            if (bestGroup) {
                const children = currentNodes.filter(n => n.parentId === bestGroup);
                setDockPreview({ groupId: bestGroup, insertIndex: children.length });
            } else {
                setDockPreview(null);
            }
        },
        [setNodes]
    );

    // ─── onNodeDragStop: finalize docking ───────────────────────────────

    const onNodeDragStop = useCallback(
        (_event: MouseEvent, draggedNode: Node) => {
            const currentNodes = nodesRef.current;

            // Only process mitarbeiter / en1090rolle nodes that are free
            if (draggedNode.type === 'abteilungGroup' || draggedNode.parentId) {
                setDockPreview(null);
                return;
            }

            const nodeW = NODE_DIMENSIONS[draggedNode.type ?? '']?.width ?? 260;
            const nodeH = getNodeHeight(draggedNode);

            // Find nearest group for docking
            const groups = currentNodes.filter(n => n.type === 'abteilungGroup');
            let bestGroup: Node | null = null;
            let bestDist = Infinity;

            for (const group of groups) {
                if (isNearGroup(draggedNode.position.x, draggedNode.position.y, nodeW, nodeH, group)) {
                    const gCenterX = group.position.x + ((group.style?.width as number) ?? GROUP_WIDTH) / 2;
                    const gCenterY = group.position.y + ((group.style?.height as number) ?? 92) / 2;
                    const nCenterX = draggedNode.position.x + nodeW / 2;
                    const nCenterY = draggedNode.position.y + nodeH / 2;
                    const dist = Math.sqrt((gCenterX - nCenterX) ** 2 + (gCenterY - nCenterY) ** 2);

                    if (dist < bestDist) {
                        bestDist = dist;
                        bestGroup = group;
                    }
                }
            }

            if (bestGroup) {
                const groupId = bestGroup.id;
                const children = currentNodes.filter(n => n.parentId === groupId);
                // Calculate relative Y position at the end of the children stack
                let y = GROUP_HEADER_HEIGHT + CHILD_PADDING_TOP;
                for (const child of children.sort((a, b) => a.position.y - b.position.y)) {
                    y += getNodeHeight(child) + CHILD_GAP;
                }

                // Dock the node
                setNodes((nds: Node[]) =>
                    ensureParentOrder(
                        nds.map(n => {
                            if (n.id !== draggedNode.id) return n;
                            return {
                                ...n,
                                parentId: groupId,
                                position: { x: GROUP_PADDING_X, y },
                                data: { ...n.data, isDocked: true },
                            };
                        })
                    )
                );

                // Remove any edges that involved the docked node
                setEdges((eds: Edge[]) =>
                    eds.filter(e => e.source !== draggedNode.id && e.target !== draggedNode.id)
                );
            }

            setDockPreview(null);
        },
        [setNodes, setEdges]
    );

    return { onNodeDrag, onNodeDragStop, dockPreview };
}
