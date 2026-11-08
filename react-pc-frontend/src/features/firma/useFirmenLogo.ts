import { useMemo, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { meldungAusFehlertext } from './firmaHilfen';

const ERLAUBTE_TYPEN = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_GROESSE = 2 * 1024 * 1024;

/**
 * Firmenlogo hochladen und löschen. `logoDateiname` kommt aus den Firmendaten,
 * `geaendert` schreibt den neuen Namen dorthin zurück ('' = kein Logo).
 */
export function useFirmenLogo(logoDateiname: string | undefined, geaendert: (logoDateiname: string) => void) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    // Cache-Buster, damit das <img> nach Upload/Löschen neu lädt
    const [logoVersion, setLogoVersion] = useState(0);
    const [laedtHoch, setLaedtHoch] = useState(false);
    const logoSrc = useMemo(
        () => (logoDateiname ? `/api/firma/logo?v=${logoVersion}` : null),
        [logoDateiname, logoVersion]
    );

    const hochladen = async (file: File) => {
        if (!ERLAUBTE_TYPEN.includes(file.type)) {
            toast.error('Ungültiger Dateityp. Erlaubt sind PNG, JPEG und WebP.');
            return;
        }
        if (file.size > MAX_GROESSE) {
            toast.error('Datei zu groß. Erlaubt sind höchstens 2 MB.');
            return;
        }
        setLaedtHoch(true);
        try {
            const form = new FormData();
            form.append('datei', file);
            const res = await fetch('/api/firma/logo', { method: 'POST', body: form });
            if (res.ok) {
                const aktualisiert = await res.json();
                geaendert(aktualisiert.logoDateiname);
                setLogoVersion(v => v + 1);
                toast.success('Firmenlogo gespeichert.');
            } else {
                toast.error(meldungAusFehlertext(await res.text(), 'Upload fehlgeschlagen.', () => 'Upload fehlgeschlagen.'));
            }
        } catch (e) {
            console.error('Fehler beim Logo-Upload', e);
            toast.error('Netzwerkfehler beim Hochladen.');
        } finally {
            setLaedtHoch(false);
        }
    };

    const loeschen = async () => {
        if (!await confirmDialog({
            title: 'Firmenlogo löschen',
            message: 'Das hinterlegte Firmenlogo wirklich entfernen?',
            variant: 'danger',
            confirmLabel: 'Löschen'
        })) return;
        try {
            const res = await fetch('/api/firma/logo', { method: 'DELETE' });
            if (res.ok) {
                geaendert('');
                setLogoVersion(v => v + 1);
                toast.success('Firmenlogo entfernt.');
            } else {
                toast.error('Löschen fehlgeschlagen.');
            }
        } catch (e) {
            console.error('Fehler beim Löschen des Logos', e);
            toast.error('Netzwerkfehler beim Löschen.');
        }
    };

    return { logoSrc, laedtHoch, hochladen, loeschen };
}
