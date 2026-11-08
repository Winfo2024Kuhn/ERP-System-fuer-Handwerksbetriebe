import { useCallback, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { meldungAusFehlertext } from './firmaHilfen';
import { neuerSteuerberater } from './steuerberaterEntwurf';
import type { SteuerberaterEntwurf, SteuerberaterKontakt } from './firmaTypen';

/** Steuerberater-Kontakte laden, im Dialog anlegen/bearbeiten und löschen. */
export function useSteuerberaterKontakte() {
    const toast = useToast();
    const showError = toast.error;
    const confirmDialog = useConfirm();
    const [steuerberater, setSteuerberater] = useState<SteuerberaterKontakt[]>([]);
    const [dialogOffen, setDialogOffen] = useState(false);
    const [entwurf, setEntwurf] = useState<SteuerberaterEntwurf | null>(null);

    const laden = useCallback(() => fetch('/api/firma/steuerberater')
        .then(async (res) => {
            if (!res.ok) throw new Error('Steuerberater konnten nicht geladen werden.');
            setSteuerberater(await res.json());
        })
        .catch((e) => {
            console.error('Fehler beim Laden der Steuerberater', e);
            showError('Steuerberater konnten nicht geladen werden.');
        }), [showError]);

    const neuAnlegen = () => {
        setEntwurf(neuerSteuerberater(new Date()));
        setDialogOffen(true);
    };

    const bearbeiten = (sb: SteuerberaterKontakt) => {
        setEntwurf(sb);
        setDialogOffen(true);
    };

    const speichern = async () => {
        if (!entwurf) return;
        try {
            const method = entwurf.id ? 'PUT' : 'POST';
            const url = entwurf.id
                ? `/api/firma/steuerberater/${entwurf.id}`
                : '/api/firma/steuerberater';
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(entwurf)
            });
            if (res.ok) {
                await laden();
                setDialogOffen(false);
                setEntwurf(null);
            } else {
                // Meist JSON { message: "..." }, sonst Klartext
                toast.error(meldungAusFehlertext(await res.text(), 'Fehler beim Speichern', text => 'Fehler beim Speichern: ' + text));
            }
        } catch (e) {
            console.error('Fehler beim Speichern', e);
            toast.error('Netzwerkfehler beim Speichern');
        }
    };

    const loeschen = async (id: number) => {
        if (!await confirmDialog({ title: 'Steuerberater löschen', message: 'Steuerberater wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/firma/steuerberater/${id}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('Steuerberater konnte nicht gelöscht werden.');
            await laden();
        } catch (e) {
            console.error('Fehler beim Löschen', e);
            toast.error('Steuerberater konnte nicht gelöscht werden.');
        }
    };

    return { steuerberater, dialogOffen, setDialogOffen, entwurf, setEntwurf, laden, neuAnlegen, bearbeiten, speichern, loeschen };
}
