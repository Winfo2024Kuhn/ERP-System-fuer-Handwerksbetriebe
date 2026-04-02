import { useEffect, useState, useCallback, useMemo } from 'react';
import { Plus, Save, Trash2, Clock, Search, RefreshCw, X, Pencil } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { TiptapEditor } from '../components/TiptapEditor';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { PageLayout } from '../components/layout/PageLayout';
import { cn } from '../lib/utils';

interface Arbeitszeitart {
  id: number;
  bezeichnung: string;
  beschreibung?: string;
  stundensatz: number;
  aktiv: boolean;
  sortierung: number;
}

const priceFormatter = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

export default function ArbeitszeitartEditor() {
  const toast = useToast();
  const confirmDialog = useConfirm();
  const [items, setItems] = useState<Arbeitszeitart[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  // Formular-State
  const [bezeichnung, setBezeichnung] = useState('');
  const [beschreibung, setBeschreibung] = useState('');
  const [stundensatz, setStundensatz] = useState('');
  const [aktiv, setAktiv] = useState(true);
  const [sortierung, setSortierung] = useState(0);
  const [isCreating, setIsCreating] = useState(false);

  // Filtered items
  const filteredItems = useMemo(() => {
    if (!searchTerm.trim()) return items;
    const term = searchTerm.toLowerCase();
    return items.filter(i => i.bezeichnung.toLowerCase().includes(term));
  }, [items, searchTerm]);

  // Laden
  const loadItems = useCallback(async () => {
    try {
      const res = await fetch('/api/arbeitszeitarten/alle');
      if (res.ok) {
        const data = await res.json();
        setItems(data);
      }
    } catch (err) {
      console.error('Fehler beim Laden:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadItems();
  }, [loadItems]);

  // Item auswählen
  const selectItem = (item: Arbeitszeitart) => {
    setSelectedId(item.id);
    setBezeichnung(item.bezeichnung);
    setBeschreibung(item.beschreibung || '');
    setStundensatz(item.stundensatz.toString());
    setAktiv(item.aktiv);
    setSortierung(item.sortierung);
    setIsCreating(false);
  };

  // Neu erstellen
  const handleNew = () => {
    setSelectedId(null);
    setBezeichnung('');
    setBeschreibung('');
    setStundensatz('65.00');
    setAktiv(true);
    setSortierung(items.length);
    setIsCreating(true);
  };

  // Speichern
  const handleSave = async () => {
    if (!bezeichnung.trim()) {
      toast.warning('Bitte Bezeichnung eingeben');
      return;
    }

    const parsedPrice = parseFloat(stundensatz.replace(',', '.'));
    if (isNaN(parsedPrice) || parsedPrice <= 0) {
      toast.warning('Bitte gültigen Stundensatz eingeben');
      return;
    }

    setSaving(true);
    try {
      const body = {
        bezeichnung: bezeichnung.trim(),
        beschreibung,
        stundensatz: parsedPrice,
        aktiv,
        sortierung
      };

      let res;
      if (isCreating) {
        res = await fetch('/api/arbeitszeitarten', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
      } else {
        res = await fetch(`/api/arbeitszeitarten/${selectedId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
      }

      if (res.ok) {
        await loadItems();
        const saved = await res.json();
        selectItem(saved);
        toast.success(isCreating ? 'Arbeitszeitart erstellt' : 'Änderungen gespeichert');
      } else {
        toast.error('Fehler beim Speichern');
      }
    } catch (err) {
      console.error('Fehler:', err);
      toast.error('Fehler beim Speichern');
    } finally {
      setSaving(false);
    }
  };

  // Löschen
  const handleDelete = async () => {
    if (!selectedId) return;
    if (!await confirmDialog({ title: 'Arbeitszeitart löschen', message: 'Arbeitszeitart wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

    try {
      const res = await fetch(`/api/arbeitszeitarten/${selectedId}`, { method: 'DELETE' });
      if (res.ok) {
        setSelectedId(null);
        setIsCreating(false);
        setBezeichnung('');
        setBeschreibung('');
        setStundensatz('');
        await loadItems();
      }
    } catch (err) {
      console.error('Fehler:', err);
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-slate-500">Laden...</div>;
  }

  return (
    <PageLayout
      ribbonCategory="Arbeitsplanung"
      title="Arbeitszeitarten"
      subtitle="Stundensätze für Stundenabrechnung in Dokumenten"
      actions={
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={loadItems} disabled={loading}>
            <RefreshCw className={cn("w-4 h-4", loading && "animate-spin")} /> Aktualisieren
          </Button>
          <Button
            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
            size="sm"
            onClick={handleNew}
          >
            <Plus className="w-4 h-4" /> Neue Arbeitszeitart
          </Button>
        </div>
      }
    >
      {/* Suchleiste */}
      <Card className="p-4 border-0 shadow-sm rounded-xl">
        <div className="relative">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
          <Input
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Arbeitszeitarten durchsuchen..."
            className="pl-9"
          />
        </div>
      </Card>

      {/* 2-Column Grid */}
      <div className="grid grid-cols-1 xl:grid-cols-[1fr_2fr] gap-6">
        {/* Column 1: Liste */}
        <Card className="p-4 border-0 shadow-sm rounded-xl">
          <div className="mb-3">
            <p className="text-xs uppercase tracking-wide text-slate-500">Übersicht</p>
            <h4 className="text-lg font-semibold text-slate-900">
              {filteredItems.length} Arbeitszeitart{filteredItems.length !== 1 ? 'en' : ''}
            </h4>
          </div>

          <div className="space-y-2">
            {filteredItems.map((item) => {
              const isSelected = selectedId === item.id && !isCreating;
              return (
                <div
                  key={item.id}
                  onClick={() => selectItem(item)}
                  className={cn(
                    'group flex items-center justify-between gap-2 rounded-lg border px-3 py-2.5 cursor-pointer transition-colors',
                    'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                    isSelected && 'border-rose-500 bg-rose-50 shadow-sm',
                    !item.aktiv && 'opacity-50'
                  )}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <Clock className="w-4 h-4 text-rose-600 flex-shrink-0" />
                      <p className="text-sm font-semibold text-slate-900 truncate">{item.bezeichnung}</p>
                    </div>
                    <p className="text-sm text-rose-700 mt-0.5 ml-6">
                      {priceFormatter.format(item.stundensatz)} / Stunde
                      {!item.aktiv && <span className="ml-2 text-slate-400">(inaktiv)</span>}
                    </p>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    {isSelected && (
                      <Pencil className="w-4 h-4 text-rose-600" />
                    )}
                  </div>
                </div>
              );
            })}
            {filteredItems.length === 0 && (
              <div className="text-center text-slate-400 py-8">
                <Clock className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                {searchTerm ? 'Keine Treffer' : 'Noch keine Arbeitszeitarten angelegt'}
              </div>
            )}
          </div>
        </Card>

        {/* Column 2: Formular */}
        <Card className="p-6 border-0 shadow-sm rounded-xl">
          {(selectedId || isCreating) ? (
            <>
              <div className="flex items-center justify-between mb-6">
                <div>
                  <p className="text-xs uppercase tracking-wide text-slate-500">
                    {isCreating ? 'Neuanlage' : 'Bearbeiten'}
                  </p>
                  <h3 className="text-lg font-semibold text-slate-900">
                    {isCreating ? 'Neue Arbeitszeitart' : bezeichnung}
                  </h3>
                </div>
                <div className="flex gap-2">
                  {!isCreating && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleDelete}
                      className="text-rose-600 border-rose-200 hover:bg-rose-50"
                    >
                      <Trash2 className="w-4 h-4" /> Löschen
                    </Button>
                  )}
                  <Button
                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                    size="sm"
                    onClick={handleSave}
                    disabled={saving}
                  >
                    <Save className="w-4 h-4" /> {saving ? 'Speichert...' : 'Speichern'}
                  </Button>
                  {isCreating && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => { setIsCreating(false); setSelectedId(null); }}
                    >
                      <X className="w-4 h-4" /> Abbrechen
                    </Button>
                  )}
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <Label htmlFor="bezeichnung">Bezeichnung *</Label>
                  <Input
                    id="bezeichnung"
                    value={bezeichnung}
                    onChange={(e) => setBezeichnung(e.target.value)}
                    placeholder="z.B. Monteurstunde, Meisterstunde, Fahrtzeit"
                    className="mt-1"
                  />
                </div>

                <div>
                  <Label htmlFor="stundensatz">Stundensatz (€/h) *</Label>
                  <Input
                    id="stundensatz"
                    type="number"
                    step="0.01"
                    min="0"
                    value={stundensatz}
                    onChange={(e) => setStundensatz(e.target.value)}
                    placeholder="65.00"
                    className="mt-1 w-40 font-mono text-right"
                  />
                </div>

                <div>
                  <Label>Beschreibung (optional)</Label>
                  <div className="mt-1 border border-slate-200 rounded-lg overflow-hidden">
                    <TiptapEditor
                      value={beschreibung}
                      onChange={setBeschreibung}
                      compactMode
                    />
                  </div>
                </div>

                <div className="flex items-center gap-4">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={aktiv}
                      onChange={(e) => setAktiv(e.target.checked)}
                      className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                    />
                    <span className="text-sm text-slate-700">Aktiv (in Dokumenten auswählbar)</span>
                  </label>
                </div>
              </div>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-16 text-slate-400">
              <Clock className="w-10 h-10 mb-3 text-rose-200" />
              <p>Wählen Sie eine Arbeitszeitart aus oder erstellen Sie eine neue</p>
            </div>
          )}
        </Card>
      </div>
    </PageLayout>
  );
}
