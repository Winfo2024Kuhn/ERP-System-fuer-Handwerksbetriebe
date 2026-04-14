import { AbteilungNode, MitarbeiterNode, En1090RolleNode } from './OrganigrammNodeTypes';

export const nodeTypes = {
    abteilung: AbteilungNode,
    mitarbeiter: MitarbeiterNode,
    en1090rolle: En1090RolleNode,
};

/** Uniform width for all nodes; height varies by content */
export const NODE_WIDTH = 260;

export const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
    abteilung: { width: NODE_WIDTH, height: 70 },
    mitarbeiter: { width: NODE_WIDTH, height: 100 },
    en1090rolle: { width: NODE_WIDTH, height: 90 },
};
