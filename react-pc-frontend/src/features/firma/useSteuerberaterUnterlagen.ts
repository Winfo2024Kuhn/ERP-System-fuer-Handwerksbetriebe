import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { jahreZusammenfuehren, lohnabrechnungenUrl } from './firmaHilfen';
import type { BwaUploadDto, LohnabrechnungDto, SteuerberaterUnterreiter } from './firmaTypen';

/**
 * Lohnabrechnungen und BWA des Steuerberater-Reiters: verfügbare Jahre, Filter
 * und die Liste des gewählten Unterreiters (geladen, sobald er sichtbar ist).
 */
export function useSteuerberaterUnterlagen(sichtbar: boolean, unterreiter: SteuerberaterUnterreiter) {
    const toast = useToast();
    const showError = toast.error;
    const [jahr, setJahr] = useState<number>(new Date().getFullYear());
    const [verfuegbareJahre, setVerfuegbareJahre] = useState<number[]>([new Date().getFullYear()]);
    const [steuerberaterFilter, setSteuerberaterFilter] = useState<string>('ALL');
    const [lohnabrechnungen, setLohnabrechnungen] = useState<LohnabrechnungDto[]>([]);
    const [bwaListe, setBwaListe] = useState<BwaUploadDto[]>([]);

    const ladeJahre = useCallback(() => Promise.all([
        fetch('/api/lohnabrechnungen/jahre'),
        fetch('/api/bwa/jahre')
    ])
        .then(async ([lohnJahreRes, bwaJahreRes]) => {
            if (!lohnJahreRes.ok || !bwaJahreRes.ok) throw new Error('Abrechnungsjahre konnten nicht geladen werden.');
            const lohnJahre = await lohnJahreRes.json();
            const bwaJahre = await bwaJahreRes.json();
            setVerfuegbareJahre(jahreZusammenfuehren(new Date().getFullYear(), lohnJahre, bwaJahre));
        })
        .catch((e) => {
            console.error('Fehler beim Laden der Jahre', e);
            showError('Abrechnungsjahre konnten nicht geladen werden.');
        }), [showError]);

    // Laden im Effekt: Jahr-, Filter- oder Reiterwechsel bricht die alte Anfrage ab,
    // damit ihre späte Antwort die neuere Liste nicht überschreibt.
    useEffect(() => {
        if (!sichtbar || (unterreiter !== 'lohnabrechnungen' && unterreiter !== 'bwa')) return;
        const abbruch = new AbortController();
        const lohn = unterreiter === 'lohnabrechnungen';
        const fehlertext = lohn ? 'Lohnabrechnungen konnten nicht geladen werden.' : 'Auswertungen konnten nicht geladen werden.';
        const url = lohn ? lohnabrechnungenUrl(steuerberaterFilter, jahr) : `/api/bwa/jahr/${jahr}`;
        fetch(url, { signal: abbruch.signal })
            .then(async (res) => {
                if (!res.ok) throw new Error(fehlertext);
                const liste = await res.json();
                if (abbruch.signal.aborted) return;
                if (lohn) setLohnabrechnungen(liste); else setBwaListe(liste);
            })
            .catch((e) => {
                if (abbruch.signal.aborted) return;
                console.error(fehlertext, e);
                showError(fehlertext);
            });
        return () => abbruch.abort();
    }, [sichtbar, unterreiter, jahr, steuerberaterFilter, showError]);

    return {
        jahr, setJahr, verfuegbareJahre, steuerberaterFilter, setSteuerberaterFilter,
        lohnabrechnungen, bwaListe, ladeJahre,
    };
}
