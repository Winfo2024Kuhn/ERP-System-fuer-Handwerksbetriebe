import { useEffect, useMemo, useState } from 'react';
import type { ProjektDetail } from '../../../types';
import type { EmailZuordnung } from '../../../components/EmailZuordnungSearchModal';
import type { EmailComposeFormProps } from './emailComposeTypen';

type VorgangProps = Pick<EmailComposeFormProps, 'projektId' | 'projekt' | 'anfrageId' | 'anfrage'>;

function fuegeHinzu(ziel: string[], quelle: (string | null | undefined)[] | null | undefined) {
    quelle?.forEach(adresse => { if (adresse && !ziel.includes(adresse)) ziel.push(adresse); });
}

/**
 * Daten des verknüpften Vorgangs (Projekt oder Anfrage): Name für Titel und KI,
 * Kunden-Adressen als Empfänger-Vorschläge. Lädt beim Öffnen und nach jedem
 * Wechsel der Zuordnung vom Backend nach; Props gelten nur, solange die
 * ursprüngliche Zuordnung steht.
 */
export function useVorgangDaten(zuordnung: EmailZuordnung | null, { projektId, projekt, anfrageId, anfrage }: VorgangProps) {
    const isAnfrageContext = zuordnung?.typ === 'ANFRAGE';
    const entityId = zuordnung?.id;
    const [fetchedEmails, setFetchedEmails] = useState<string[]>([]);
    const [fetchedProjekt, setFetchedProjekt] = useState<ProjektDetail | null>(null);
    /** Bauvorhaben des verknüpften Vorgangs, sobald es vom Backend da ist. */
    const [fetchedVorgangName, setFetchedVorgangName] = useState('');

    const propProjekt = zuordnung?.typ === 'PROJEKT' && zuordnung.id === projektId ? projekt : undefined;
    const propAnfrage = zuordnung?.typ === 'ANFRAGE' && zuordnung.id === anfrageId ? anfrage : undefined;
    const effectiveProjekt = propProjekt || fetchedProjekt;
    const entityName = effectiveProjekt?.bauvorhaben || propAnfrage?.bauvorhaben
        || zuordnung?.titel || fetchedVorgangName || '';

    useEffect(() => {
        let abgebrochen = false;
        const loadLinkedData = async () => {
            const emails: string[] = [];
            let geladenesProjekt: ProjektDetail | null = null;
            let vorgangName = '';
            try {
                if (entityId && !isAnfrageContext) {
                    const res = await fetch(`/api/projekte/${entityId}`);
                    if (res.ok) {
                        const data = await res.json();
                        fuegeHinzu(emails, data.kundenEmails);
                        fuegeHinzu(emails, data.kundeDto?.kundenEmails);
                        geladenesProjekt = data;
                        vorgangName = data.bauvorhaben || '';
                    }
                } else if (entityId && isAnfrageContext) {
                    const res = await fetch(`/api/anfragen/${entityId}`);
                    if (res.ok) {
                        const data = await res.json();
                        fuegeHinzu(emails, data.kundenEmails);
                        vorgangName = data.bauvorhaben || '';
                    }
                }
            } catch {
                // Fallback auf Props
            }
            if (abgebrochen) return;
            setFetchedProjekt(geladenesProjekt);
            setFetchedVorgangName(vorgangName);
            setFetchedEmails(emails);
        };
        loadLinkedData();
        return () => { abgebrochen = true; };
    }, [entityId, isAnfrageContext]);

    // Merge: Props + Backend (dedupliziert)
    const projektKundenEmails = effectiveProjekt?.kundenEmails;
    const kundeDtoKundenEmails = effectiveProjekt?.kundeDto?.kundenEmails;
    const anfrageKundenEmails = propAnfrage?.kundenEmails;
    const kundenEmails = useMemo(() => {
        const emails: string[] = [];
        fuegeHinzu(emails, projektKundenEmails);
        fuegeHinzu(emails, kundeDtoKundenEmails);
        fuegeHinzu(emails, anfrageKundenEmails);
        fuegeHinzu(emails, fetchedEmails);
        return emails;
    }, [projektKundenEmails, kundeDtoKundenEmails, anfrageKundenEmails, fetchedEmails]);

    // Vorschläge ohne Doppelte in anderer Schreibweise.
    const availableEmails = useMemo(() => {
        const emails: string[] = [];
        kundenEmails.forEach(e => {
            if (e && !emails.some(existing => existing.toLowerCase() === e.toLowerCase())) emails.push(e);
        });
        return emails;
    }, [kundenEmails]);

    return { isAnfrageContext, entityId, entityName, kundenEmails, availableEmails };
}
