import { useEffect, useState } from 'react';
import { Plug, ExternalLink } from 'lucide-react';
import { Select } from '../../ui/select-custom';
import { Button } from '../../ui/button';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { LieferantIdsKonfigTab } from '../../LieferantIdsKonfigTab';
import { IdsLieferantenAuswahlModal } from '../../IdsLieferantenAuswahlModal';
import { SettingsCard, SectionLoading } from '../settingsUi';

/** Reuses the supplier form from EN1090; settings and supplier edit share one implementation. */
export function IdsSettingsSection() {
    const toast = useToast();
    const [suppliers, setSuppliers] = useState<{ id: number; name: string }[]>([]);
    const [selected, setSelected] = useState('');
    const [loading, setLoading] = useState(true);
    const [testOpen, setTestOpen] = useState(false);
    useEffect(() => {
        let active = true;
        fetch('/api/admin/ids/lieferanten').then(async res => {
            if (!res.ok) throw new Error('Lieferanten konnten nicht geladen werden.');
            const list: { id: number; name: string }[] = await res.json();
            if (active) { setSuppliers(list); setSelected(list[0] ? String(list[0].id) : ''); }
        }).catch(() => { if (active) toast.error('IDS-Lieferanten konnten nicht geladen werden.'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [toast]);
    const supplier = suppliers.find(item => String(item.id) === selected);
    return (
        <SettingsCard icon={<Plug className="w-5 h-5 text-rose-600" />} title="IDS-Schnittstelle einrichten"
            description="Zugang zum Lieferanten-Shop hinterlegen und Warenkörbe mit aktuellen Preisen übernehmen.">
            {loading ? <SectionLoading /> : <div className="space-y-5">
                <div className="flex flex-wrap items-end gap-4">
                    <div className="w-80 max-w-full"><Label htmlFor="ids-lieferant">Lieferant</Label>
                        <Select id="ids-lieferant" value={selected} onChange={setSelected} options={suppliers.map(item => ({ value: String(item.id), label: item.name }))} placeholder="Lieferant auswählen" />
                    </div>
                    <Button variant="outline" disabled={!supplier} onClick={() => setTestOpen(true)}><ExternalLink className="w-4 h-4 mr-2" />Gespeicherten Zugang testen</Button>
                </div>
                {supplier ? <LieferantIdsKonfigTab key={supplier.id} lieferantId={supplier.id} lieferantName={supplier.name} /> : <p className="text-sm text-slate-500">Noch kein IDS-Lieferant verfügbar.</p>}
            </div>}
            <IdsLieferantenAuswahlModal isOpen={testOpen} onClose={() => setTestOpen(false)} />
        </SettingsCard>
    );
}
