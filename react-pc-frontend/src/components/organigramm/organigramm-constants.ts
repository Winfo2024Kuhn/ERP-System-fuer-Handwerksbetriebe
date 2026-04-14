import { AbteilungNode, MitarbeiterNode, En1090RolleNode } from './OrganigrammNodeTypes';

export const nodeTypes = {
    abteilung: AbteilungNode,
    mitarbeiter: MitarbeiterNode,
    en1090rolle: En1090RolleNode,
};

/** Node dimensions per type (used by dagre layout) */
export const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
    abteilung: { width: 220, height: 80 },
    mitarbeiter: { width: 240, height: 110 },
    en1090rolle: { width: 200, height: 90 },
};
