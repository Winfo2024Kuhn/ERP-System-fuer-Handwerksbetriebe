import { useCallback, useState } from 'react';
import { DetailLayout } from '../../components/DetailLayout';
import { useKontaktAnrufAnzahl } from '../telefon/useKontaktAnrufAnzahl';
import { useTelefonBerechtigung } from '../telefon/useTelefonBerechtigung';
import { LieferantDetailKopf } from './LieferantDetailKopf';
import { LieferantKontaktSpalte } from './LieferantKontaktSpalte';
import { LieferantReiterInhalt } from './LieferantReiterInhalt';
import { LieferantReiterLeiste } from './LieferantReiterLeiste';
import { useLieferantNachladen } from './useLieferantNachladen';
import type { LieferantTab } from './lieferantHilfen';
import type { LieferantDetail } from '../../types';

interface Props {
    lieferant: LieferantDetail;
    /** Wird nach dem Speichern hochgezählt, damit Kennzahlen und Verlauf neu geladen werden. */
    ladeVersion: number;
    activeTab: LieferantTab;
    onTabChange: (tab: LieferantTab) => void;
    onBack: () => void;
    onEdit: () => void;
}

/**
 * Zahl der Ansprechpartner am Reiter: zuerst vom Server (Detaildaten), nach
 * Anlegen oder Löschen die Zahl, die der Reiter meldet. Gilt nur für den
 * Lieferanten und Ladestand, für den sie gemeldet wurde.
 */
function useAnsprechpartnerAnzahl(lieferant: LieferantDetail, ladeVersion: number) {
    const schluessel = `${lieferant.id}-${ladeVersion}`;
    const [gemeldet, setGemeldet] = useState<{ fuer: string; anzahl: number } | null>(null);
    const melden = useCallback((anzahl: number) => setGemeldet({ fuer: schluessel, anzahl }), [schluessel]);
    const anzahl = gemeldet?.fuer === schluessel ? gemeldet.anzahl : lieferant.ansprechpartnerAnzahl ?? 0;
    return { anzahl, melden };
}

/** Detailansicht eines Lieferanten: Kopf, Reiter und Kontaktdaten. */
export function LieferantDetailView({ lieferant, ladeVersion, activeTab: angefragterTab, onTabChange, onBack, onEdit }: Props) {
    // Reiter "Anrufe" nur mit dem Abteilungs-Recht "Anrufe & Anrufbeantworter";
    // ohne Recht fällt ein ?tab=anrufe auf den E-Mail-Verlauf zurück.
    const darfTelefon = useTelefonBerechtigung() === true;
    const nachgeladen = useLieferantNachladen(lieferant.id, ladeVersion);
    const statistik = nachgeladen.statistik ?? lieferant.statistik;
    const statistikLaedt = nachgeladen.statistik === undefined && !lieferant.statistik;
    const anrufAnzahl = useKontaktAnrufAnzahl('LIEFERANT', Number(lieferant.id) || null, darfTelefon);
    const ansprechpartner = useAnsprechpartnerAnzahl(lieferant, ladeVersion);
    const activeTab: LieferantTab = angefragterTab === 'anrufe' && !darfTelefon ? 'emails' : angefragterTab;

    return (
        <DetailLayout
            header={<LieferantDetailKopf lieferant={lieferant} statistik={statistik} statistikLaedt={statistikLaedt} onBack={onBack} onEdit={onEdit} />}
            mainContent={
                <>
                    <LieferantReiterLeiste
                        aktiv={activeTab}
                        onWechsel={onTabChange}
                        mitAnrufen={darfTelefon}
                        zaehler={{
                            emails: lieferant.emailAnzahl ?? 0,
                            ansprechpartner: ansprechpartner.anzahl,
                            anrufe: anrufAnzahl,
                            dokumente: lieferant.dokumenteAnzahl ?? lieferant.dokumente?.length ?? 0,
                            notizen: nachgeladen.notizen?.length ?? lieferant.notizenAnzahl ?? 0,
                        }}
                    />
                    <LieferantReiterInhalt
                        lieferant={lieferant}
                        aktiv={activeTab}
                        notizen={nachgeladen.notizen}
                        onNotizenChange={nachgeladen.setNotizen}
                        onAnsprechpartnerAnzahl={ansprechpartner.melden}
                    />
                </>
            }
            sideContent={<LieferantKontaktSpalte lieferant={lieferant} darfTelefon={darfTelefon} />}
        />
    );
}
