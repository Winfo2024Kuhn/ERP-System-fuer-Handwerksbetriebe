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

    const ladeLohnabrechnungen = useCallback(() => fetch(lohnabrechnungenUrl(steuerberaterFilter, jahr))
        .then(async (res) => {
            if (!res.ok) throw new Error('Lohnabrechnungen konnten nicht geladen werden.');
            setLohnabrechnungen(await res.json());
        })
        .catch((e) => {
            console.error('Fehler beim Laden der Lohnabrechnungen', e);
            showError('Lohnabrechnungen konnten nicht geladen werden.');
        }), [jahr, steuerberaterFilter, showError]);

    const ladeBwaListe = useCallback(() => fetch(`/api/bwa/jahr/${jahr}`)
        .then(async (res) => {
            if (!res.ok) throw new Error('Auswertungen konnten nicht geladen werden.');
            setBwaListe(await res.json());
        })
        .catch((e) => {
            console.error('Fehler beim Laden der BWA-Liste', e);
            showError('Auswertungen konnten nicht geladen werden.');
        }), [jahr, showError]);

    useEffect(() => {
        if (!sichtbar) return;
        if (unterreiter === 'lohnabrechnungen') void ladeLohnabrechnungen();
        if (unterreiter === 'bwa') void ladeBwaListe();
    }, [sichtbar, unterreiter, ladeLohnabrechnungen, ladeBwaListe]);

    return {
        jahr, setJahr, verfuegbareJahre, steuerberaterFilter, setSteuerberaterFilter,
        lohnabrechnungen, bwaListe, ladeJahre,
    };
}
