import { useSearchParams, useNavigate } from 'react-router-dom';
import DocumentEditor from '../components/DocumentEditor';
import { KiHilfeChat } from '../components/KiHilfeChat';

/**
 * Page wrapper for DocumentEditor that reads projektId/anfrageId from URL params.
 * Opens as fullscreen page (no MainLayout sidebar).
 */
export default function DocumentEditorPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const projektId = searchParams.get('projektId');
    const anfrageId = searchParams.get('anfrageId');
    const dokumentId = searchParams.get('dokumentId');
    const dokumentTyp = searchParams.get('dokumentTyp');

    const handleClose = () => {
        // Navigate back or close tab
        if (window.history.length > 1) {
            navigate(-1);
        } else {
            window.close();
        }
    };

    return (
        <>
            <DocumentEditor
                projektId={projektId ? parseInt(projektId) : undefined}
                anfrageId={anfrageId ? parseInt(anfrageId) : undefined}
                dokumentId={dokumentId ? parseInt(dokumentId) : undefined}
                initialDokumentTyp={dokumentTyp as import('../types').AusgangsGeschaeftsDokumentTyp | undefined}
                onClose={handleClose}
            />
            <KiHilfeChat />
        </>
    );
}
