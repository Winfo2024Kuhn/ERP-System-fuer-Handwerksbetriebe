import { useCallback, useEffect, useState } from 'react';
import {
    Banknote, ArrowDownToLine, ArrowUpFromLine, UserSquare2,
    Settings, Coins, AlertTriangle,
} from 'lucide-react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';
import type { Sachkonto } from '../../types';
import { formatEuro } from './belegFormat';
import { BankAbhebungModal, EinfacheKasseModal, LohnZahlungModal, type SaldoInfo } from './NeueBuchungDialog';
import { KasseEinstellungenDialog } from './KasseEinstellungenDialog';

// Saldo-Bar + 4 Shortcut-Buttons + Settings (Issue #59).
//
// Die Komponente kapselt alle vier Buchungs-Modale (Bank-Abhebung,
// Ehegattengehalt, Privateinlage, Privatentnahme) plus das Settings-Modal.
// Sie ruft `onChanged()` nach erfolgreicher Buchung auf, damit der Parent
// die Beleg-Liste und den Kassenbuch-View neu lädt.

interface KasseShortcutsProps {
    sachkonten: Sachkonto[];
    onChanged: () => void;
}

export function KasseShortcuts({ sachkonten, onChanged }: KasseShortcutsProps) {
    const [saldo, setSaldo] = useState<SaldoInfo | null>(null);
    const [openModal, setOpenModal] = useState<null | 'bank' | 'lohn' | 'einlage' | 'entnahme' | 'settings'>(null);
    const [toast, setToast] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null);

    const loadSaldo = useCallback(async () => {
        try {
            const res = await fetch('/api/buchhaltung/kasse/saldo');
            if (res.ok) setSaldo(await res.json());
        } catch (e) {
            console.error('Saldo laden fehlgeschlagen', e);
        }
    }, []);

    // Initial-Load via async-Wrapper, damit der set-state-in-effect-Lint
    // nicht anschlägt — Standard-Pattern für „fetch on mount".
    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await fetch('/api/buchhaltung/kasse/saldo');
                if (res.ok && !cancelled) setSaldo(await res.json());
            } catch (e) {
                console.error('Saldo laden fehlgeschlagen', e);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    // Toast nach 4s automatisch ausblenden — kein alert(), kein blocking dialog.
    useEffect(() => {
        if (!toast) return;
        const t = setTimeout(() => setToast(null), 4000);
        return () => clearTimeout(t);
    }, [toast]);

    const refreshAlles = useCallback(() => {
        loadSaldo();
        onChanged();
    }, [loadSaldo, onChanged]);

    const showToast = (kind: 'ok' | 'err', text: string) => setToast({ kind, text });

    const saldoUnterMindestbestand = saldo != null && saldo.saldo < saldo.mindestbestand;

    return (
        <Card className="p-4 bg-gradient-to-r from-rose-50 to-white border-rose-200">
            <div className="flex flex-wrap items-center gap-4">
                <div className="flex items-center gap-3 mr-4">
                    <div className="bg-rose-100 text-rose-700 rounded-lg p-2">
                        <Coins className="w-5 h-5" />
                    </div>
                    <div>
                        <div className="text-xs uppercase tracking-wide text-rose-700 font-semibold">Aktueller Kassenstand</div>
                        <div className="flex items-baseline gap-2">
                            <span className={`text-2xl font-bold ${saldoUnterMindestbestand ? 'text-red-700' : 'text-slate-900'}`}>
                                {saldo ? `${formatEuro(saldo.saldo)} €` : '–'}
                            </span>
                            {saldo && saldo.mindestbestand > 0 && (
                                <span className="text-xs text-slate-500">
                                    Mindestbestand: {formatEuro(saldo.mindestbestand)} €
                                </span>
                            )}
                            {saldoUnterMindestbestand && (
                                <span className="inline-flex items-center gap-1 text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-0.5">
                                    <AlertTriangle className="w-3 h-3" /> unter Mindestbestand
                                </span>
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 ml-auto">
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('bank')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Bargeld von der Bank in die Kasse legen">
                        <Banknote className="w-4 h-4 mr-2" /> Bank → Kasse
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('lohn')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Ehegattengehalt aus der Kasse auszahlen">
                        <UserSquare2 className="w-4 h-4 mr-2" /> Ehegattengehalt
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('einlage')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Privates Geld in die Firma einlegen">
                        <ArrowDownToLine className="w-4 h-4 mr-2" /> Privateinlage
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('entnahme')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Bargeld aus der Firma ins Private nehmen">
                        <ArrowUpFromLine className="w-4 h-4 mr-2" /> Privatentnahme
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setOpenModal('settings')}
                        className="text-rose-700 hover:bg-rose-100"
                        title="Mindestbestand & Automatik einstellen">
                        <Settings className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {openModal === 'bank' && (
                <BankAbhebungModal
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                    saldo={saldo}
                />
            )}
            {openModal === 'lohn' && (
                <LohnZahlungModal
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'einlage' && (
                <EinfacheKasseModal
                    titel="Privateinlage buchen"
                    endpoint="/api/buchhaltung/kasse/privateinlage"
                    defaultBeschreibung="Privateinlage"
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'entnahme' && (
                <EinfacheKasseModal
                    titel="Privatentnahme buchen"
                    endpoint="/api/buchhaltung/kasse/privatentnahme"
                    defaultBeschreibung="Privatentnahme"
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'settings' && (
                <KasseEinstellungenDialog
                    sachkonten={sachkonten}
                    onClose={() => setOpenModal(null)}
                    onSaved={() => { setOpenModal(null); refreshAlles(); showToast('ok', 'Einstellungen gespeichert'); }}
                    onError={(m) => showToast('err', m)}
                />
            )}

            {toast && (
                <div className={`mt-3 text-sm px-3 py-2 rounded-lg border ${
                    toast.kind === 'ok'
                        ? 'bg-rose-50 border-rose-200 text-rose-800'
                        : 'bg-red-50 border-red-200 text-red-800'
                }`}>
                    {toast.text}
                </div>
            )}
        </Card>
    );
}
