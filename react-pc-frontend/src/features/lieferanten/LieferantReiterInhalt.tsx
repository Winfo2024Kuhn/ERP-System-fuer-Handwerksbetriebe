import type { ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { EmailsTab } from '../../components/EmailsTab';
import LieferantDokumenteTab from '../../components/LieferantDokumenteTab';
import { LieferantNotizenTab } from '../../components/LieferantNotizenTab';
import { LieferantReklamationenTab } from '../../components/LieferantReklamationenTab';
import { KontaktAnrufeTab } from '../telefon/KontaktAnrufeTab';
import { LieferantAnsprechpartnerTab } from './LieferantAnsprechpartnerTab';
import type { LieferantTab } from './lieferantHilfen';
import type { LieferantDetail, LieferantNotiz } from '../../types';

const ReiterLaedt = ({ text }: { text: string }) => (
    <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-10 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 motion-safe:animate-spin text-rose-600" />
        {text}
    </div>
);

/** Jeder Reiter scrollt in seinem eigenen Bereich. */
const Bereich = ({ children }: { children: ReactNode }) => (
    <div className="absolute inset-0 overflow-y-auto pr-2">{children}</div>
);

interface Props {
    lieferant: LieferantDetail;
    aktiv: LieferantTab;
    notizen: LieferantNotiz[] | undefined;
    onNotizenChange: (notizen: LieferantNotiz[]) => void;
    onAnsprechpartnerAnzahl: (anzahl: number) => void;
}

/** Inhalt des offenen Reiters der Lieferanten-Detailansicht. */
export function LieferantReiterInhalt({ lieferant, aktiv, notizen, onNotizenChange, onAnsprechpartnerAnzahl }: Props) {
    const id = Number(lieferant.id);
    return (
        <div className="flex-1 min-h-0 relative">
            {aktiv === 'emails' && (
                <Bereich>
                    <EmailsTab bezug="lieferant" bezugId={id} showComposeButton={false} showReplyButton={false} />
                </Bereich>
            )}
            {aktiv === 'ansprechpartner' && (
                <Bereich>
                    <LieferantAnsprechpartnerTab lieferantId={id} onAnzahl={onAnsprechpartnerAnzahl} />
                </Bereich>
            )}
            {aktiv === 'anrufe' && lieferant.id && (
                <Bereich>
                    <KontaktAnrufeTab typ="LIEFERANT" kontaktId={id} />
                </Bereich>
            )}
            {aktiv === 'dokumente' && (
                <Bereich>
                    <LieferantDokumenteTab
                        lieferantId={lieferant.id}
                        lieferantName={lieferant.lieferantenname}
                        dokumente={lieferant.dokumente}
                    />
                </Bereich>
            )}
            {aktiv === 'notizen' && (
                <Bereich>
                    {notizen === undefined ? <ReiterLaedt text="Notizen werden geladen…" /> : (
                        <LieferantNotizenTab lieferantId={id} notizen={notizen} onNotizenChange={onNotizenChange} />
                    )}
                </Bereich>
            )}
            {aktiv === 'reklamationen' && (
                <Bereich>
                    <LieferantReklamationenTab
                        lieferantId={id}
                        lieferantName={lieferant.lieferantenname}
                        lieferantEmails={lieferant.kundenEmails}
                    />
                </Bereich>
            )}
        </div>
    );
}
