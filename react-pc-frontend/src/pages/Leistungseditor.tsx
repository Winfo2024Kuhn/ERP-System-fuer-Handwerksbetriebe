import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronDown,
  ChevronRight,
  FilePlus,
  Folder,
  FolderOpen,
  Loader2,
  Plus,
  Save,
  Trash2,
  Type,
  X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { TiptapEditor } from '../components/TiptapEditor';
import { cn } from '../lib/utils';
import { type LeistungsFolder, type LeistungsService, type ProduktkategorieDto } from '../types';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

// Verrechnungseinheiten (matching backend enum Verrechnungseinheit)
const BILLING_UNITS = [
  { id: 'LAUFENDE_METER', name: 'Laufende Meter', short: 'm' },
  { id: 'QUADRATMETER', name: 'Quadratmeter', short: 'm²' },
  { id: 'KILOGRAMM', name: 'Kilogramm', short: 'kg' },
  { id: 'STUECK', name: 'Stück', short: 'Stk' }
];

const priceFormatter = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

const formatPrice = (value: number | string) => {
  const numeric = typeof value === 'number' ? value : Number.parseFloat(String(value).replace(',', '.')) || 0;
  return priceFormatter.format(numeric);
};

const getUnitShort = (unitId: string) => BILLING_UNITS.find((u) => u.id === unitId)?.short || '';

const stripHtml = (value: string) => value.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();

const getFolderPath = (folder: LeistungsFolder, folders: LeistungsFolder[]): string => {
  const parent = folders.find((f) => f.id === folder.parentId);
  if (parent) return `${getFolderPath(parent, folders)} / ${folder.name}`;
  return folder.name;
};

interface FolderDescriptionFormProps {
  folder: LeistungsFolder;
  folders: LeistungsFolder[];
  value: string;
  onChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
}

interface ServiceFormProps {
  service: LeistungsService;
  folders: LeistungsFolder[];
  isCreating: boolean;
  onSave: (service: LeistungsService) => void;
  onCancel: () => void;
}

const ServiceForm: React.FC<ServiceFormProps> = ({ service, folders, isCreating, onSave, onCancel }) => {
  const [name, setName] = useState(service.name);
  const [description, setDescription] = useState(service.description);
  const [price, setPrice] = useState(service.price.toString());
  const [unit, setUnit] = useState(service.unit);
  const [folderId, setFolderId] = useState(service.folderId);
  const [error, setError] = useState('');

  const folderLabel = useMemo(() => {
    const currentFolder = folders.find((f) => f.id === folderId);
    return currentFolder ? getFolderPath(currentFolder, folders) : 'Ordner auswählen';
  }, [folderId, folders]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setName(service.name);
    setDescription(service.description);
    setPrice(service.price.toString());
    setUnit(service.unit);
    setFolderId(service.folderId);
    setError('');
  }, [service]);

  const handleSave = () => {
    const parsedPrice = Number.parseFloat(price.replace(',', '.'));
    if (!name.trim() || !description.trim() || !stripHtml(description)) {
      setError('Bitte Name und Beschreibung ausfüllen.');
      return;
    }
    if (!Number.isFinite(parsedPrice)) {
      setError('Preis muss eine Zahl sein.');
      return;
    }
    onSave({
      ...service,
      name: name.trim(),
      description,
      price: parsedPrice,
      unit,
      folderId
    });
  };

  return (
    <Card className="border-rose-100 shadow-lg">
      <div className="p-4 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">{isCreating ? 'Neuanlage' : 'Bearbeitung'}</p>
            <h3 className="text-xl font-semibold text-slate-900">{isCreating ? 'Neue Leistung erstellen' : 'Leistung bearbeiten'}</h3>
          </div>
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100">
            {folderLabel}
          </span>
        </div>
        {error ? <div className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{error}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <Label>Leistungsname</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="z.B. IT-Beratung" />
          </div>
          <div>
            <Label>Ordner</Label>
            <Select
              value={folderId}
              onChange={(value) => setFolderId(value)}
              options={folders.map((folder) => ({
                value: folder.id,
                label: getFolderPath(folder, folders)
              }))}
              placeholder="Ordner auswählen"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <Label>Preis (EUR)</Label>
            <Input
              type="number"
              step="0.01"
              min="0"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              placeholder="0.00"
            />
          </div>
          <div>
            <Label>Verrechnungseinheit</Label>
            <Select
              value={unit}
              onChange={(value) => setUnit(value)}
              options={BILLING_UNITS.map((u) => ({
                value: u.id,
                label: `${u.name} (${u.short})`
              }))}
              placeholder="Einheit auswählen"
            />
            <p className="text-xs text-slate-500 mt-1">Einheiten werden später aus der Datenbank geladen</p>
          </div>
        </div>

        <div className="space-y-2">
          <Label>Beschreibung</Label>
          <TiptapEditor value={description} onChange={setDescription} />
        </div>

        <div className="border border-slate-100 rounded-lg p-3 bg-slate-50">
          <p className="text-slate-700 mb-2">Vorschau:</p>
          <div className="flex items-baseline gap-2 mb-3">
            <h4 className="text-slate-900">{name || 'Leistungsname'}</h4>
            <span className="text-rose-700 font-semibold">{formatPrice(price)}</span>
            <span className="text-slate-400">/</span>
            <span className="text-slate-600">{getUnitShort(unit)}</span>
          </div>
          <div
            className="prose prose-sm max-w-none text-slate-800"
            dangerouslySetInnerHTML={{
              __html: description?.trim() ? description : '<p class="text-slate-400">Keine Beschreibung</p>'
            }}
          />
        </div>

        <div className="flex gap-3 pt-2">
          <Button
            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
            size="sm"
            onClick={handleSave}
          >
            <Save className="w-4 h-4" /> Speichern
          </Button>
          <Button variant="outline" size="sm" onClick={onCancel}>
            <X className="w-4 h-4" /> Abbrechen
          </Button>
        </div>
      </div>
    </Card>
  );
};

const FolderDescriptionForm: React.FC<FolderDescriptionFormProps> = ({ folder, folders, value, onChange, onSave, onCancel }) => {
  const path = getFolderPath(folder, folders);

  return (
    <Card className="border-rose-100 shadow-lg">
      <div className="p-4 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">Ordnerbeschreibung</p>
            <h3 className="text-xl font-semibold text-slate-900">Beschreibung für „{folder.name}"</h3>
            <p className="text-sm text-slate-500 mt-1">{path}</p>
          </div>
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100">
            {folder.name}
          </span>
        </div>

        <div className="space-y-2">
          <Label>Beschreibung (Rich-Text)</Label>
          <TiptapEditor value={value} onChange={onChange} />
        </div>

        <div className="border border-slate-100 rounded-lg p-3 bg-slate-50">
          <p className="text-slate-700 mb-2">Vorschau:</p>
          <div
            className="prose prose-sm max-w-none text-slate-800"
            dangerouslySetInnerHTML={{
              __html: value?.trim() ? value : '<p class="text-slate-400">Keine Beschreibung hinterlegt.</p>'
            }}
          />
        </div>

        <div className="flex gap-3 pt-2">
          <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" size="sm" onClick={onSave}>
            <Save className="w-4 h-4" /> Speichern
          </Button>
          <Button variant="outline" size="sm" onClick={onCancel}>
            <X className="w-4 h-4" /> Abbrechen
          </Button>
        </div>
      </div>
    </Card>
  );
};

interface FolderTreeNodeProps {
  folder: LeistungsFolder;
  level: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onChildrenLoaded: (parentId: string, children: LeistungsFolder[], descriptions: Record<string, string>) => void;
}

const FolderTreeNode: React.FC<FolderTreeNodeProps> = ({ folder, level, selectedId, onSelect, onChildrenLoaded }) => {
  const [expanded, setExpanded] = useState(false);
  const [children, setChildren] = useState<LeistungsFolder[]>([]);
  const [loading, setLoading] = useState(false);
  const loaded = useRef(false);

  const isSelected = selectedId === folder.id;
  const isLeaf = folder.leaf === true;

  const handleToggle = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isLeaf) return;
    if (expanded) {
      setExpanded(false);
      return;
    }
    setExpanded(true);
    if (!loaded.current) {
      setLoading(true);
      try {
        const res = await fetch(`/api/produktkategorien/${folder.id}/unterkategorien?light=true`);
        if (res.ok) {
          const data: ProduktkategorieDto[] = await res.json();
          const childFolders = (Array.isArray(data) ? data : []).map((cat) => ({
            id: String(cat.id),
            name: cat.bezeichnung || cat.pfad || 'Kategorie',
            parentId: folder.id,
            leaf: cat.leaf ?? true
          }));
          const descs: Record<string, string> = {};
          data.forEach((cat) => {
            if (cat.beschreibung) descs[String(cat.id)] = cat.beschreibung;
          });
          setChildren(childFolders);
          onChildrenLoaded(folder.id, childFolders, descs);
          loaded.current = true;
        }
      } catch (err) {
        console.error('Unterkategorien laden fehlgeschlagen', err);
      } finally {
        setLoading(false);
      }
    }
  };

  return (
    <div>
      <div
        className={cn(
          'flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
          'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
          isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
        )}
        style={{ marginLeft: level * 12 }}
        onClick={() => onSelect(folder.id)}
      >
        <div className="flex items-center gap-2 min-w-0">
          {!isLeaf ? (
            <button
              type="button"
              className="p-1 rounded text-slate-500 hover:text-rose-600 hover:bg-rose-50"
              onClick={handleToggle}
            >
              {loading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : expanded ? (
                <ChevronDown className="w-4 h-4" />
              ) : (
                <ChevronRight className="w-4 h-4" />
              )}
            </button>
          ) : (
            <span className="w-4" />
          )}
          {expanded ? <FolderOpen className="w-4 h-4 text-rose-600" /> : <Folder className="w-4 h-4 text-rose-600" />}
          <span className="text-sm font-semibold text-slate-900 truncate">{folder.name}</span>
        </div>
      </div>
      {expanded && children.length > 0 && (
        <div className="mt-2 space-y-2">
          {children.map((child) => (
            <FolderTreeNode
              key={child.id}
              folder={child}
              level={level + 1}
              selectedId={selectedId}
              onSelect={onSelect}
              onChildrenLoaded={onChildrenLoaded}
            />
          ))}
        </div>
      )}
    </div>
  );
};

interface FolderTreeProps {
  folders: LeistungsFolder[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onChildrenLoaded: (parentId: string, children: LeistungsFolder[], descriptions: Record<string, string>) => void;
}

const FolderTree: React.FC<FolderTreeProps> = ({ folders, selectedId, onSelect, onChildrenLoaded }) => {
  const roots = folders.filter((f) => f.parentId === null);

  if (!roots.length) {
    return (
      <Card className="p-8 text-center text-slate-500 border-dashed">
        <Folder className="w-10 h-10 mx-auto mb-2 text-rose-200" />
        Keine Ordner vorhanden
      </Card>
    );
  }

  return (
    <div className="space-y-2">
      {roots.map((root) => (
        <FolderTreeNode
          key={root.id}
          folder={root}
          level={0}
          selectedId={selectedId}
          onSelect={onSelect}
          onChildrenLoaded={onChildrenLoaded}
        />
      ))}
    </div>
  );
};

interface ServiceListProps {
  services: LeistungsService[];
  onEdit: (service: LeistungsService) => void;
  onDelete: (id: string) => void;
}

const ServiceList: React.FC<ServiceListProps> = ({ services, onEdit, onDelete }) => {
  if (!services.length) {
    return (
      <Card className="p-10 text-center text-slate-500">
        <FilePlus className="w-10 h-10 mx-auto mb-3 text-rose-200" />
        Keine Leistungen in diesem Ordner
      </Card>
    );
  }

  return (
    <div className="space-y-2">
      {services.map((service) => (
        <Card
          key={service.id}
          className="p-3 flex items-start justify-between gap-3 hover:border-rose-200 hover:shadow-sm cursor-pointer"
          onClick={() => onEdit(service)}
        >
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-rose-50 text-rose-700">
              <FilePlus className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-slate-900 truncate">{service.name}</p>
              <p className="text-sm text-rose-700">
                {formatPrice(service.price)} <span className="text-slate-400">/</span> <span className="text-slate-600">{getUnitShort(service.unit)}</span>
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" className="text-rose-700" onClick={(e) => { e.stopPropagation(); onEdit(service); }}>
              <Type className="w-4 h-4" /> Bearbeiten
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-rose-700"
              onClick={(e) => {
                e.stopPropagation();
                onDelete(service.id);
              }}
            >
              <Trash2 className="w-4 h-4" />
            </Button>
          </div>
        </Card>
      ))}
    </div>
  );
};

export const Leistungseditor: React.FC = () => {
    const toast = useToast();
    const confirmDialog = useConfirm();
  const [folders, setFolders] = useState<LeistungsFolder[]>([]);
  const [services, setServices] = useState<LeistungsService[]>([]);
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [editingService, setEditingService] = useState<LeistungsService | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [loadingFolders, setLoadingFolders] = useState(false);
  const [folderError, setFolderError] = useState<string | null>(null);
  const [folderDescriptions, setFolderDescriptions] = useState<Record<string, string>>({});
  const [editingFolderId, setEditingFolderId] = useState<string | null>(null);
  const [folderDescriptionDraft, setFolderDescriptionDraft] = useState('');

  const filteredServices = useMemo(
    () => services.filter((s) => !selectedFolderId || s.folderId === selectedFolderId),
    [services, selectedFolderId]
  );

  const ensureSelection = useCallback(
    (nextFolders: LeistungsFolder[]) => {
      if (nextFolders.some((f) => f.id === selectedFolderId)) return;
      const root = nextFolders.find((f) => f.parentId === null) || nextFolders[0];
      setSelectedFolderId(root ? root.id : null);
    },
    [selectedFolderId]
  );

  useEffect(() => {
    ensureSelection(folders);
  }, [folders, ensureSelection]);

  const handleChildrenLoaded = useCallback((_parentId: string, children: LeistungsFolder[], descriptions: Record<string, string>) => {
    setFolders((prev) => {
      const existingIds = new Set(prev.map((f) => f.id));
      const newFolders = children.filter((c) => !existingIds.has(c.id));
      return newFolders.length ? [...prev, ...newFolders] : prev;
    });
    if (Object.keys(descriptions).length) {
      setFolderDescriptions((prev) => ({ ...prev, ...descriptions }));
    }
  }, []);

  const loadRootFolders = useCallback(async () => {
    setLoadingFolders(true);
    setFolderError(null);
    try {
      const res = await fetch('/api/produktkategorien/haupt?light=true');
      if (!res.ok) throw new Error('Kategorien konnten nicht geladen werden.');
      const data: ProduktkategorieDto[] = await res.json();
      const roots: LeistungsFolder[] = (Array.isArray(data) ? data : []).map((cat) => ({
        id: String(cat.id),
        name: cat.bezeichnung || cat.pfad || 'Kategorie',
        parentId: null,
        leaf: cat.leaf ?? true
      }));
      const descs: Record<string, string> = {};
      data.forEach((cat) => {
        if (cat.beschreibung) descs[String(cat.id)] = cat.beschreibung;
      });
      setFolders(roots);
      setFolderDescriptions(descs);
      setSelectedFolderId(roots[0]?.id ?? null);
    } catch (error) {
      console.warn('Produktkategorien konnten nicht geladen werden', error);
      setFolderError('Produktkategorien konnten nicht geladen werden.');
      setFolders([]);
      setSelectedFolderId(null);
    } finally {
      setLoadingFolders(false);
    }
  }, []);

  useEffect(() => {
    loadRootFolders();
    fetch('/api/leistungen')
      .then((res) => res.json())
      .then((data) => {
        if (Array.isArray(data)) {
          // Normalize backend response to frontend types (string ids)
          const normalized = data.map((s: LeistungsService) => ({
            ...s,
            id: String(s.id),
            folderId: String(s.folderId)
          }));
          setServices(normalized);
        } else {
          setServices([]);
        }
      })
      .catch((err) => console.error('Leistungen konnten nicht geladen werden', err));
  }, [loadRootFolders]);

  useEffect(() => {
    if (editingFolderId && !folders.some((f) => f.id === editingFolderId)) {
      setEditingFolderId(null);
      setFolderDescriptionDraft('');
    }
  }, [editingFolderId, folders]);



  const buildSuggestedDescription = useCallback(
    (folderId: string | null) => {
      if (!folderId) return '';
      const chain: string[] = [];
      const findFolder = (id: string | null) => (id ? folders.find((f) => f.id === id) || null : null);
      let current = findFolder(folderId);
      while (current) {
        const desc = folderDescriptions[current.id];
        if (desc && desc.trim()) {
          chain.push(desc.trim());
        }
        current = findFolder(current.parentId);
      }
      return chain.length ? chain.reverse().join('<p><br></p>') : '';
    },
    [folderDescriptions, folders]
  );

  const selectedFolder = useMemo(() => folders.find((f) => f.id === selectedFolderId) ?? null, [folders, selectedFolderId]);
  const isSelectedFolderLeaf = selectedFolder?.leaf === true;

  const handleCreateService = () => {
    if (!selectedFolderId) {
      toast.warning('Bitte zuerst einen Ordner auswählen.');
      return;
    }
    if (!isSelectedFolderLeaf) {
      toast.warning('Leistungen können nur in der untersten Ordnerebene angelegt werden.');
      return;
    }
    const suggestion = buildSuggestedDescription(selectedFolderId);
    setEditingService({
      id: '',
      name: '',
      description: suggestion,
      price: 0,
      unit: BILLING_UNITS[0]?.id || '',
      folderId: selectedFolderId
    });
    setIsCreating(true);
    setEditingFolderId(null);
  };

  const handleSaveService = (service: LeistungsService) => {
    const isNew = isCreating || !service.id;
    const method = isNew ? 'POST' : 'PUT';
    const url = isNew ? '/api/leistungen' : `/api/leistungen/${service.id}`;

    const payload = {
      name: service.name,
      description: service.description,
      price: service.price,
      unit: service.unit,
      folderId: service.folderId ? Number(service.folderId) : null
    };

    fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(res => {
        if (!res.ok) throw new Error('Speichern fehlgeschlagen');
        return res.json();
      })
      .then((savedService: LeistungsService) => {
        // Normalize backend response to frontend types (string ids)
        const normalized: LeistungsService = {
          ...savedService,
          id: String(savedService.id),
          folderId: String(savedService.folderId)
        };
        if (isNew) {
          setServices((prev) => [...prev, normalized]);
        } else {
          setServices((prev) => prev.map((s) => (s.id === normalized.id ? normalized : s)));
        }
        setSelectedFolderId(normalized.folderId);
        setEditingService(null);
        setIsCreating(false);
      })
      .catch(err => {
        console.error(err);
        toast.error('Leistung konnte nicht gespeichert werden.');
      });
  };

  const handleEditService = (service: LeistungsService) => {
    setEditingFolderId(null);
    setEditingService(service);
    setIsCreating(false);
  };

  const handleDeleteService = async (id: string) => {
    if (!await confirmDialog({ title: 'Leistung löschen', message: 'Leistung wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

    fetch(`/api/leistungen/${id}`, { method: 'DELETE' })
      .then((res) => {
        if (!res.ok) throw new Error('Löschen fehlgeschlagen');
        setServices((prev) => prev.filter((s) => s.id !== id));
        if (editingService?.id === id) {
          setEditingService(null);
          setIsCreating(false);
        }
      })
      .catch((err) => {
        console.error(err);
        toast.error('Leistung konnte nicht gelöscht werden.');
      });
  };

  const handleCancelEdit = () => {
    setEditingService(null);
    setIsCreating(false);
  };

  const handleEditFolderDescription = () => {
    if (!selectedFolderId) {
      toast.warning('Bitte zuerst einen Ordner auswählen.');
      return;
    }
    const folder = folders.find((f) => f.id === selectedFolderId);
    if (!folder) return;
    setEditingFolderId(folder.id);
    setFolderDescriptionDraft(folderDescriptions[folder.id] || '');
    setEditingService(null);
    setIsCreating(false);
  };

  const handleSaveFolderDescription = () => {
    if (!editingFolderId) return;
    const payload = { beschreibung: folderDescriptionDraft };
    fetch(`/api/produktkategorien/${editingFolderId}/beschreibung`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error('Speichern fehlgeschlagen');
        }
        return res.json().catch(() => null);
      })
      .then((dto) => {
        setFolderDescriptions((prev) => ({ ...prev, [editingFolderId]: dto?.beschreibung ?? folderDescriptionDraft }));
      })
      .catch(() => {
        setFolderDescriptions((prev) => ({ ...prev, [editingFolderId]: folderDescriptionDraft }));
      })
      .finally(() => {
        setEditingFolderId(null);
        setFolderDescriptionDraft('');
      });
  };

  const handleCancelFolderDescription = () => {
    setEditingFolderId(null);
    setFolderDescriptionDraft('');
  };

  const activeFolder = editingFolderId
    ? folders.find((f) => f.id === editingFolderId) || null
    : selectedFolderId
      ? folders.find((f) => f.id === selectedFolderId) || null
      : null;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
        <div>
          <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Textverwaltung</p>
          <h1 className="text-3xl font-bold text-slate-900">LEISTUNGSEDITOR</h1>
          <p className="text-slate-500 mt-1">Verwaltung von Leistungen und Beschreibungen nach Kategorien.</p>
        </div>
      </div>
      <div className="grid grid-cols-1 xl:grid-cols-[0.9fr_0.9fr_2.2fr] gap-6">
        <Card className="p-6 border-rose-100 shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-500">Ordner</p>
              <h4 className="text-lg font-semibold text-slate-900">Struktur</h4>
            </div>
            <div className="flex gap-2">
              <Button
                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                size="sm"
                onClick={handleEditFolderDescription}
                disabled={!selectedFolderId}
              >
                <Plus className="w-4 h-4" /> Beschreibung anlegen
              </Button>
            </div>
          </div>
          {loadingFolders ? (
            <div className="text-slate-500 text-sm py-6">Produktkategorien werden geladen...</div>
          ) : folderError ? (
            <div className="text-red-600 text-sm py-6">{folderError}</div>
          ) : (
            <FolderTree
              folders={folders}
              selectedId={selectedFolderId}
              onSelect={(id) => setSelectedFolderId(id)}
              onChildrenLoaded={handleChildrenLoaded}
            />
          )}
        </Card>

        <Card className="p-6 border-rose-100 shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-500">Leistungen</p>
              <h4 className="text-lg font-semibold text-slate-900">Aktuelle Auswahl</h4>
            </div>
            <Button
              className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
              size="sm"
              onClick={handleCreateService}
              disabled={!isSelectedFolderLeaf}
              title={!isSelectedFolderLeaf ? 'Leistungen nur in unterster Ordnerebene möglich' : undefined}
            >
              <Plus className="w-4 h-4" /> Neu
            </Button>
          </div>
          <ServiceList services={filteredServices} onEdit={handleEditService} onDelete={handleDeleteService} />
        </Card>

        <div className="min-h-full">
          {editingFolderId && activeFolder ? (
            <FolderDescriptionForm
              folder={activeFolder}
              folders={folders}
              value={folderDescriptionDraft}
              onChange={setFolderDescriptionDraft}
              onSave={handleSaveFolderDescription}
              onCancel={handleCancelFolderDescription}
            />
          ) : editingService ? (
            <ServiceForm
              service={editingService}
              folders={folders}
              isCreating={isCreating}
              onSave={handleSaveService}
              onCancel={handleCancelEdit}
            />
          ) : (
            <Card className="p-10 h-full border-dashed border-slate-200 text-center text-slate-500 shadow-inner">
              Wählen Sie eine Leistung aus oder erstellen Sie eine neue Leistung.
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default Leistungseditor;
