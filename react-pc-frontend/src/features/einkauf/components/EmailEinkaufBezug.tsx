import type { EmailItem } from '../../email/emailCenterModel';
import { useCallback, useEffect, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';

interface Props { email: EmailItem }

/** Zeigt Einkaufszuordnung neben der bestehenden Lieferantenzuordnung. */
export function EmailEinkaufBezug({ email }: Props) {
    const toast = useToast();
    const [versand, setVersand] = useState<{ id: number; version: number; status: string; fehlerCode?: string } | null>(null);
    const [beleg, setBeleg] = useState('');
    const laden = useCallback(async () => {
        if (email.kontoId !== 'EINKAUF') return;
        try {
            const response = await fetch(`/api/einkauf/mail/${email.id}/antwortstatus`);
            if (response.status === 204) { setVersand(null); return; }
            if (!response.ok) throw new Error('Versandstatus konnte nicht geladen werden.');
            setVersand(await response.json());
        } catch { /* Der Vorgangsbezug bleibt auch ohne Versandstatus verfügbar. */ }
    }, [email.id, email.kontoId]);
    useEffect(() => {
        void laden();
        const handler = () => void laden();
        window.addEventListener('einkauf-mailantwort-aktualisieren', handler);
        return () => window.removeEventListener('einkauf-mailantwort-aktualisieren', handler);
    }, [laden]);
    const erneut = async () => {
        if (!versand) return;
        const response = await fetch(`/api/einkauf/mail/${email.id}/antworten/${versand.id}/erneut`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: versand.version }),
        });
        if (!response.ok) { toast.error('Antwort konnte nicht erneut beauftragt werden.'); return; }
        toast.success('Antwort wurde erneut beauftragt.'); void laden();
    };
    const klaeren = async (entscheidung: 'BEREITS_ANGENOMMEN' | 'NACHWEISLICH_NICHT_GESENDET') => {
        if (!versand || !beleg.trim()) { toast.error('Bitte einen Nachweis angeben.'); return; }
        const response = await fetch(`/api/einkauf/mail/${email.id}/antworten/${versand.id}/klaeren`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ version: versand.version, entscheidung, beleg: beleg.trim() }),
        });
        if (!response.ok) { toast.error('Versand konnte nicht geklärt werden.'); return; }
        setBeleg(''); toast.success('Versandstatus wurde geklärt.'); void laden();
    };
    if (email.kontoId !== 'EINKAUF' && !email.einkaufTyp) return null;
    const state = email.zuordnungPruefen ? 'Zuordnung prüfen' : email.einkaufNummer || `${email.einkaufTyp ?? 'Einkauf'} ${email.einkaufVorgangId ?? ''}`;
    const statusLabels: Record<string, string> = {
        AUTOMATISCHE_ANTWORT: 'Automatische Antwort', UNZUSTELLBAR: 'Nicht zugestellt', ABSAGE: 'Absage',
        ANGEBOT: 'Angebot eingegangen', VERSENDET: 'Antwort angenommen', PRUEFEN: 'Zuordnung prüfen',
    };
    return <div data-testid="einkauf-mail-bezug" className="border-b border-rose-100 bg-rose-50 px-4 py-2 text-sm text-slate-700">
        <span className="font-medium text-rose-800">Einkauf:</span>{' '}
        {!email.zuordnungPruefen && ['ANFRAGE', 'BESTELLUNG'].includes(email.einkaufTyp ?? '') && email.einkaufVorgangId
            ? <a className="font-medium text-rose-700 underline underline-offset-2" href={`/${email.einkaufTyp === 'BESTELLUNG' ? 'bestellungen' : 'einkaufsanfragen'}/${email.einkaufVorgangId}`}>{state}</a>
            : state}
        {email.lieferantName && <span className="ml-3">Lieferant: {email.lieferantName}</span>}
        {email.einkaufNachrichtStatus && <span className="ml-3 text-slate-600">{statusLabels[email.einkaufNachrichtStatus] ?? email.einkaufNachrichtStatus}</span>}
        {email.zustellStatus === 'UNZUSTELLBAR' && <span className="ml-3 text-rose-700" title={email.zustellFehler}>Nicht zugestellt</span>}
        {versand && <span data-testid="einkauf-antwortstatus" className="ml-3 font-medium">Antwort: {versand.status}</span>}
        {versand?.status === 'FEHLGESCHLAGEN' && <Button size="sm" variant="outline" className="ml-3 h-7" onClick={() => void erneut()}>Erneut versuchen</Button>}
        {versand?.status === 'UNKLAR' && <span className="mt-2 flex w-full flex-wrap items-center gap-2">
            <Input aria-label="Nachweis zur Versandklärung" value={beleg} onChange={event => setBeleg(event.target.value)} className="h-8 max-w-sm bg-white" placeholder="Nachweis eintragen" />
            <Button size="sm" variant="outline" className="h-7" onClick={() => void klaeren('BEREITS_ANGENOMMEN')}>Bereits angenommen</Button>
            <Button size="sm" variant="outline" className="h-7" onClick={() => void klaeren('NACHWEISLICH_NICHT_GESENDET')}>Nicht gesendet</Button>
        </span>}
    </div>;
}
