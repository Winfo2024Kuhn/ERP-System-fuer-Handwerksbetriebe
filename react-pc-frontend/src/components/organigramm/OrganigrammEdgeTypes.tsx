import { memo } from 'react';
import {
    BaseEdge,
    EdgeLabelRenderer,
    getSmoothStepPath,
    type EdgeProps,
} from '@xyflow/react';
import { Scissors } from 'lucide-react';

/**
 * Benutzerdefinierter Kanten-Typ für das Organigramm.
 *
 * Beim Anklicken der Kante wird diese selektiert und ein "Trennen"-Button
 * erscheint in der Mitte der Kante, mit dem die Verbindung gelöst werden kann.
 */
const OrganigrammEdgeComponent = ({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    selected,
    data,
    style,
    markerEnd,
}: EdgeProps) => {
    const [edgePath, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        targetX,
        targetY,
        sourcePosition,
        targetPosition,
    });

    const onDeleteEdge = data?.onDeleteEdge as ((id: string) => void) | undefined;

    return (
        <>
            <BaseEdge
                path={edgePath}
                markerEnd={markerEnd}
                style={{
                    ...style,
                    stroke: selected ? '#60a5fa' : '#94a3b8',
                    strokeWidth: selected ? 2.5 : 2,
                }}
            />
            {selected && onDeleteEdge && (
                <EdgeLabelRenderer>
                    <div
                        style={{
                            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                        }}
                        className="absolute pointer-events-auto nodrag nopan z-50"
                    >
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onDeleteEdge(id);
                            }}
                            className="flex items-center gap-1 px-2 py-1 bg-white border border-red-200 rounded-full text-red-500 hover:bg-red-50 hover:border-red-400 shadow-sm text-[11px] font-medium transition-colors cursor-pointer"
                            title="Verbindung trennen"
                        >
                            <Scissors className="w-3 h-3" />
                            Trennen
                        </button>
                    </div>
                </EdgeLabelRenderer>
            )}
        </>
    );
};

export const OrganigrammEdge = memo(OrganigrammEdgeComponent);
