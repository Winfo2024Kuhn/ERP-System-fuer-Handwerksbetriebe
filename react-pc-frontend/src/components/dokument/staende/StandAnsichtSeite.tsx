import { useEffect, useState, type CSSProperties } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import DocumentEditor from '../../DocumentEditor';
import { useToast } from '../../ui/toast';
import { StandAnsichtBand } from './StandAnsichtBand';
import { useStandAnsicht } from './useStandAnsicht';
import { useStandWiederherstellen } from './useStandWiederherstellen';
import { wiederherstellenSperrgrund } from './standFormat';

interface Props {
    dokumentId: number;
    standId: number;
}

/** Höhe des Bands, damit der (fest positionierte) Editor darunter beginnt. */
function useBandHoehe() {
    const [element, setElement] = useState<HTMLDivElement | null>(null);
    const [hoehe, setHoehe] = useState(0);
    useEffect(() => {
        if (!element) return undefined;
        const messen = () => setHoehe(element.getBoundingClientRect().height);
        messen();
        const beobachter = new ResizeObserver(messen);
        beobachter.observe(element);
        return () => beobachter.disconnect();
    }, [element]);
    return { setElement, hoehe };
}

/**
 * Ansicht eines festgehaltenen Stands im eigenen Tab (`/dokument-editor?dokumentId=X&stand=Y`):
 * ohne Bearbeiten-Sperre, schreibgeschützt, ohne automatisches Speichern. Nach dem
 * Wiederherstellen wechselt der Tab auf den aktuellen, bearbeitbaren Inhalt.
 */
export function StandAnsichtSeite({ dokumentId, standId }: Props) {
    const toast = useToast();
    const navigate = useNavigate();
    const [, setSearchParams] = useSearchParams();
    const { stand, fehler } = useStandAnsicht(dokumentId, standId);
    const { wiederherstellen, laeuftFuer } = useStandWiederherstellen({ dokumentId, erfolgMelden: false });
    const { setElement: setBandElement, hoehe: bandHoehe } = useBandHoehe();

    const beimWiederherstellen = async () => {
        if (!stand || !(await wiederherstellen(stand))) return;
        toast.success(`Stand ${stand.nummer} wurde wiederhergestellt. Das Dokument ist neu geladen.`);
        setSearchParams({ dokumentId: String(dokumentId) });
    };

    const schliessen = () => {
        if (window.history.length > 1) navigate(-1);
        else window.close();
    };

    return (
        <div className="fixed inset-0 z-50 flex flex-col" style={{ '--lock-leiste-hoehe': `${bandHoehe}px` } as CSSProperties}>
            <StandAnsichtBand
                ref={setBandElement}
                dokumentId={dokumentId}
                stand={stand}
                fehler={fehler}
                sperrgrund={stand ? wiederherstellenSperrgrund(stand.dokumentBearbeitbar, stand) : null}
                laeuft={stand != null && laeuftFuer === stand.id}
                onWiederherstellen={() => void beimWiederherstellen()}
            />
            {stand && (
                <div data-testid="dokument-editor-flaeche">
                    <DocumentEditor dokumentId={dokumentId} onClose={schliessen} readOnly standAnsicht={stand} />
                </div>
            )}
        </div>
    );
}
