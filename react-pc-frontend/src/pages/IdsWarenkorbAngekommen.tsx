import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, X } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { IDS_KANAL } from '../components/ids/useIdsShopBesuch';

/**
 * Rücksprung aus dem Lieferanten-Shop (IDS).
 *
 * <p>Der Shop schickt den Warenkorb ans Programm; das Programm leitet den
 * Shop-Tab danach hierher um. Diese Seite sagt groß und klar, ob es geklappt
 * hat und was jetzt zu tun ist – und meldet dem Dokument-Tab über den Kanal
 * `ids-warenkorb`, dass der Warenkorb da ist, damit er sofort weitermacht.</p>
 */

type Ergebnis = 'ok' | 'abgelaufen' | 'ungueltig' | 'leer' | 'fehler';

const TEXTE: Record<Exclude<Ergebnis, 'ok'>, { titel: string; text: string }> = {
    abgelaufen: {
        titel: 'Der Shop-Besuch ist abgelaufen',
        text: 'Der Shop-Besuch war länger als 60 Minuten offen. Bitte im Dokument erneut auf „Aus Lieferanten-Shop“ klicken.',
    },
    ungueltig: {
        titel: 'Warenkorb nicht zugeordnet',
        text: 'Dieser Warenkorb konnte keinem Shop-Besuch zugeordnet werden.',
    },
    leer: {
        titel: 'Der Warenkorb war leer',
        text: 'Der Warenkorb war leer. Bitte im Shop Artikel in den Warenkorb legen und ihn dann übernehmen.',
    },
    fehler: {
        titel: 'Das hat nicht geklappt',
        text: 'Der Warenkorb konnte nicht gelesen werden. Bitte erneut versuchen.',
    },
};

function leseErgebnis(wert: string | null): Ergebnis {
    return wert === 'ok' || wert === 'abgelaufen' || wert === 'ungueltig' || wert === 'leer' ? wert : 'fehler';
}

export default function IdsWarenkorbAngekommen() {
    const [parameter] = useSearchParams();
    const ergebnis = leseErgebnis(parameter.get('ergebnis'));
    const vorgangId = Number(parameter.get('vorgang'));

    useEffect(() => {
        if (ergebnis !== 'ok' || !Number.isSafeInteger(vorgangId) || vorgangId <= 0) return;
        if (typeof BroadcastChannel === 'undefined') return;
        const kanal = new BroadcastChannel(IDS_KANAL);
        kanal.postMessage({ vorgangId });
        kanal.close();
    }, [ergebnis, vorgangId]);

    const ok = ergebnis === 'ok';
    const titel = ok ? 'Warenkorb übernommen' : TEXTE[ergebnis].titel;

    return (
        <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
            <Card className="w-full max-w-lg p-8 text-center">
                <div className={ok
                    ? 'mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100'
                    : 'mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100'}
                >
                    {ok
                        ? <CheckCircle2 aria-hidden="true" className="h-8 w-8 text-emerald-600" />
                        : <AlertTriangle aria-hidden="true" className="h-8 w-8 text-amber-600" />}
                </div>
                <p className="mt-5 text-sm font-semibold uppercase tracking-wide text-rose-600">Lieferanten-Shop</p>
                <h1 className="mt-1 text-2xl font-bold text-slate-900">{titel}</h1>
                <p role="status" className="mt-3 text-base text-slate-600">
                    {ok
                        ? 'Du kannst diesen Tab jetzt schließen und im Dokument weitermachen.'
                        : TEXTE[ergebnis].text}
                </p>
                <Button className="mt-6" onClick={() => window.close()}>
                    <X aria-hidden="true" className="h-4 w-4" />
                    Tab schließen
                </Button>
                <p className="mt-3 text-xs text-slate-500">Geht der Tab nicht zu, einfach oben am Reiter auf das X klicken.</p>
            </Card>
        </main>
    );
}
