import { useEffect, useState, useCallback } from 'react';
import { Plus, Save, Trash2, Clock } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { TiptapEditor } from '../components/TiptapEditor';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

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

  // Formular-State
  const [bezeichnung, setBezeichnung] = useState('');
  const [beschreibung, setBeschreibung] = useState('');
  const [stundensatz, setStundensatz] = useState('');
  const [aktiv, setAktiv] = useState(true);
  const [sortierung, setSortierung] = useState(0);
  const [isCreating, setIsCreating] = useState(false);

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
    <div className="flex h-full bg-slate-50">
      {/* Liste links */}
      <div className="w-80 border-r border-slate-200 bg-white flex flex-col">
        <div className="p-4 border-b border-slate-200">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-slate-900 flex items-center gap-2">
              <Clock className="w-5 h-5" />
              Arbeitszeitarten
            </h2>
            <Button size="sm" onClick={handleNew} className="bg-rose-600 hover:bg-rose-700 text-white">
              <Plus className="w-4 h-4 mr-1" />
              Neu
            </Button>
          </div>
          <p className="text-xs text-slate-500">
            Stundensätze für Stundenabrechnung in Dokumenten
          </p>
        </div>
        
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {items.map((item) => (
            <button
              key={item.id}
              onClick={() => selectItem(item)}
              className={`w-full p-3 text-left rounded-lg transition-colors ${
                selectedId === item.id
                  ? 'bg-rose-50 border border-rose-200'
                  : 'hover:bg-slate-50 border border-transparent'
              } ${!item.aktiv ? 'opacity-50' : ''}`}
            >
              <div className="font-medium text-slate-900">{item.bezeichnung}</div>
              <div className="text-sm text-slate-500">
                {priceFormatter.format(item.stundensatz)} / Stunde
                {!item.aktiv && <span className="ml-2 text-rose-500">(inaktiv)</span>}
              </div>
            </button>
          ))}
          {items.length === 0 && (
            <div className="text-center text-slate-400 py-8">
              Noch keine Arbeitszeitarten angelegt
            </div>
          )}
        </div>
      </div>

      {/* Formular rechts */}
      <div className="flex-1 p-6 overflow-y-auto">
        {(selectedId || isCreating) ? (
          <Card className="max-w-2xl mx-auto p-6">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-slate-900">
                {isCreating ? 'Neue Arbeitszeitart' : 'Arbeitszeitart bearbeiten'}
              </h3>
              <div className="flex gap-2">
                {!isCreating && (
                  <Button 
                    variant="outline" 
                    size="sm" 
                    onClick={handleDelete}
                    className="text-rose-600 border-rose-200 hover:bg-rose-50"
                  >
                    <Trash2 className="w-4 h-4 mr-1" />
                    Löschen
                  </Button>
                )}
                <Button 
                  size="sm" 
                  onClick={handleSave} 
                  disabled={saving}
                  className="bg-rose-600 hover:bg-rose-700 text-white"
                >
                  <Save className="w-4 h-4 mr-1" />
                  {saving ? 'Speichern...' : 'Speichern'}
                </Button>
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
                  className="mt-1 w-40"
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

              <div>
                <Label htmlFor="sortierung">Sortierung</Label>
                <Input
                  id="sortierung"
                  type="number"
                  min="0"
                  value={sortierung}
                  onChange={(e) => setSortierung(parseInt(e.target.value) || 0)}
                  className="mt-1 w-24"
                />
                <p className="text-xs text-slate-500 mt-1">
                  Niedrigere Zahlen werden zuerst angezeigt
                </p>
              </div>
            </div>

            <div className="mt-6 p-4 bg-amber-50 border border-amber-200 rounded-lg">
              <p className="text-sm text-amber-800">
                <strong>Hinweis zum Snapshot-Prinzip:</strong><br />
                Änderungen an Stundensätzen haben keinen Einfluss auf bereits erstellte Dokumente. 
                Die Werte werden bei der Dokumenterstellung als Snapshot gespeichert.
              </p>
            </div>
          </Card>
        ) : (
          <div className="flex items-center justify-center h-full text-slate-400">
            Wählen Sie eine Arbeitszeitart aus oder erstellen Sie eine neue
          </div>
        )}
      </div>
    </div>
  );
}
