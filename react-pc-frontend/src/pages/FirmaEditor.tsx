import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { Card } from '../components/ui/card';
import { PageLayout } from '../components/layout/PageLayout';
import { SteuerpruefungExport } from '../components/firma/SteuerpruefungExport';
import { LohnStammdatenPanel } from '../components/firma/LohnStammdatenPanel';
import { FirmaReiterLeiste } from '../features/firma/FirmaReiterLeiste';
import { FirmendatenReiter } from '../features/firma/FirmendatenReiter';
import { SteuerberaterDialog } from '../features/firma/SteuerberaterDialog';
import { SteuerberaterReiter } from '../features/firma/SteuerberaterReiter';
import { UnfallversicherungReiter } from '../features/firma/UnfallversicherungReiter';
import { useFirmaDaten } from '../features/firma/useFirmaDaten';
import { useFirmenLogo } from '../features/firma/useFirmenLogo';
import { useSteuerberaterKontakte } from '../features/firma/useSteuerberaterKontakte';
import { useSteuerberaterUnterlagen } from '../features/firma/useSteuerberaterUnterlagen';
import type { FirmaReiter, SteuerberaterUnterreiter } from '../features/firma/firmaTypen';

/** Seite „Firma“ (/firma): setzt Reiter, Daten-Hooks und Steuerberater-Dialog zusammen. */
export default function FirmaEditor() {
    const [reiter, setReiter] = useState<FirmaReiter>('firma');
    const [unterreiter, setUnterreiter] = useState<SteuerberaterUnterreiter>('kontakte');
    const [loading, setLoading] = useState(true);

    const daten = useFirmaDaten();
    const kontakte = useSteuerberaterKontakte();
    const unterlagen = useSteuerberaterUnterlagen(reiter === 'steuerberater', unterreiter);
    const { firma, aendern } = daten;
    const logo = useFirmenLogo(firma?.logoDateiname, logoDateiname => aendern({ logoDateiname }));

    const { ladeFirma, ladeGewerke } = daten;
    const ladeSteuerberater = kontakte.laden;
    const ladeJahre = unterlagen.ladeJahre;
    // Der Ladezustand startet mit true; er endet, wenn alle Grunddaten da sind.
    useEffect(() => {
        void Promise.all([ladeFirma(), ladeSteuerberater(), ladeJahre(), ladeGewerke()])
            .finally(() => setLoading(false));
    }, [ladeFirma, ladeSteuerberater, ladeJahre, ladeGewerke]);

    return (
        <PageLayout
            ribbonCategory="Vorlagen & Stammdaten"
            title="FIRMENINFORMATIONEN"
            subtitle="Firmendaten, Steuerberater und Systemkonfiguration"
        >
            {loading ? (
                <div className="flex items-center justify-center py-20">
                    <RefreshCw className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            ) : (
                <>
                    <FirmaReiterLeiste aktiv={reiter} onWechsel={setReiter} steuerberaterAnzahl={kontakte.steuerberater.length} />

                    {reiter === 'firma' && firma && (
                        <FirmendatenReiter
                            firma={firma}
                            aendern={aendern}
                            speichert={daten.speichert}
                            onSpeichern={() => void daten.speichern()}
                            logo={logo}
                        />
                    )}
                    {reiter === 'steuerberater' && (
                        <SteuerberaterReiter
                            unterreiter={unterreiter}
                            onUnterreiter={setUnterreiter}
                            kontakte={kontakte}
                            unterlagen={unterlagen}
                        />
                    )}
                    {reiter === 'steuerpruefung' && <SteuerpruefungExport />}
                    {reiter === 'lohn-stammdaten' && (
                        <Card className="p-6">
                            <LohnStammdatenPanel />
                        </Card>
                    )}
                    {reiter === 'unfallversicherung' && firma && (
                        <UnfallversicherungReiter
                            firma={firma}
                            gewerke={daten.gewerke}
                            aendern={aendern}
                            speichert={daten.speichert}
                            onSpeichern={() => void daten.speichern()}
                        />
                    )}
                </>
            )}

            <SteuerberaterDialog kontakte={kontakte} />
        </PageLayout>
    );
}
