import { useNavigate } from 'react-router-dom';
import OrganigrammEditor from '../components/organigramm/OrganigrammEditor';

/**
 * Fullscreen page wrapper for the Organigramm editor.
 * Opens without MainLayout sidebar (like DocumentEditorPage).
 */
export default function OrganigrammPage() {
    const navigate = useNavigate();

    const handleClose = () => {
        if (window.history.length > 1) {
            navigate(-1);
        } else {
            window.close();
        }
    };

    return <OrganigrammEditor onClose={handleClose} />;
}
