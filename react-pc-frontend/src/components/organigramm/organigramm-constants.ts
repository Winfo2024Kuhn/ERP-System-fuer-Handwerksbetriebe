import { AbteilungGroupNode } from './AbteilungGroupNode';
import { AbteilungNode, MitarbeiterNode, En1090RolleNode } from './OrganigrammNodeTypes';
import { OrganigrammEdge } from './OrganigrammEdgeTypes';

export const nodeTypes = {
    abteilungGroup: AbteilungGroupNode,
    abteilung: AbteilungNode, // legacy — not created anymore, kept for migration
    mitarbeiter: MitarbeiterNode,
    en1090rolle: En1090RolleNode,
};

export const edgeTypes = {
    organigramm: OrganigrammEdge,
};

/** Uniform width for child nodes inside a group */
export const NODE_WIDTH = 260;

/** Group container width = NODE_WIDTH + 2 * GROUP_PADDING_X */
export const GROUP_WIDTH = 284;
export const GROUP_PADDING_X = 12;
export const GROUP_PADDING_BOTTOM = 12;
export const GROUP_HEADER_HEIGHT = 52;
export const CHILD_GAP = 8;
export const CHILD_PADDING_TOP = 12;

/** Proximity in px for dock detection when dragging near a group */
export const DOCK_PROXIMITY = 50;

export const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
    abteilungGroup: { width: GROUP_WIDTH, height: 92 },
    abteilung: { width: NODE_WIDTH, height: 70 }, // legacy
    mitarbeiter: { width: NODE_WIDTH, height: 100 },
    en1090rolle: { width: NODE_WIDTH, height: 90 },
};
