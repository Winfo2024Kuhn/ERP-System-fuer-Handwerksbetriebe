import { useState } from 'react';
import { useToast } from '../../../components/ui/toast';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';

export type AdressZiel = 'kunde' | 'projekt' | 'anfrage';

interface AdresseSpeichernOptionen {
    kundeId?: number;
    zuordnung: EmailZuordnung | null;
    /** Nach der Entscheidung (speichern oder nicht): Formular schließen. */
    fertig: () => void;
}

/**
 * Rückfrage nach dem Senden an eine neue Adresse: beim Kunden bzw. beim
 * Projekt/der Anfrage speichern oder nicht. Danach schließt das Formular.
 */
export function useAdresseSpeichern({ kundeId, zuordnung, fertig }: AdresseSpeichernOptionen) {
    const [adresse, setAdresse] = useState('');
    const [offen, setOffen] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const toast = useToast();

    const anbieten = (neueAdresse: string) => {
        setAdresse(neueAdresse);
        setOffen(true);
    };

    const speichern = async (ziel: AdressZiel) => {
        if (!adresse) return;
        setSpeichert(true);
        try {
            let url = '';
            if (ziel === 'kunde' && kundeId) url = `/api/kunden/${kundeId}/emails`;
            else if (ziel === 'projekt' && zuordnung?.typ === 'PROJEKT') url = `/api/projekte/${zuordnung.id}/emails`;
            else if (ziel === 'anfrage' && zuordnung?.typ === 'ANFRAGE') url = `/api/anfragen/${zuordnung.id}/emails`;
            if (url) {
                const res = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: adresse }),
                });
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
            }
        } catch (err) {
            console.error('E-Mail-Adresse konnte nicht gespeichert werden:', err);
            toast.error('Adresse konnte nicht gespeichert werden. Bitte beim Kunden von Hand eintragen.');
        } finally {
            setSpeichert(false);
            setOffen(false);
            fertig();
        }
    };

    const ueberspringen = () => {
        setOffen(false);
        fertig();
    };

    return { adresse, offen, speichert, anbieten, speichern, ueberspringen };
}

export type AdresseSpeichern = ReturnType<typeof useAdresseSpeichern>;
