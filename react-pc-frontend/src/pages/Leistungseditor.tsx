import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronDown,
  ChevronRight,
  FilePlus,
  FileText,
  Folder,
  FolderOpen,
  Loader2,
  Pencil,
  Plus,
  Save,
  Search,
  Trash2,
  X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { TiptapEditor } from '../components/TiptapEditor';
import { PageLayout } from '../components/layout/PageLayout';
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
  serviceCounts: Record<string, number>;
}

const FolderTreeNode: React.FC<FolderTreeNodeProps> = ({ folder, level, selectedId, onSelect, onChildrenLoaded, serviceCounts }) => {
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
          {(serviceCounts[folder.id] ?? 0) > 0 && (
            <span className="text-xs font-normal px-1.5 py-0.5 rounded-full bg-rose-50 text-rose-600 flex-shrink-0">
              {serviceCounts[folder.id]}
            </span>
          )}
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
              serviceCounts={serviceCounts}
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
  serviceCounts: Record<string, number>;
}

const FolderTree: React.FC<FolderTreeProps> = ({ folders, selectedId, onSelect, onChildrenLoaded, serviceCounts }) => {
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
          serviceCounts={serviceCounts}
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
      <div className="py-8 text-center">
        <FilePlus className="w-10 h-10 mx-auto mb-2 text-slate-200" />
        <p className="text-sm text-slate-400">Keine Leistungen in diesem Ordner</p>
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      {services.map((service) => (
        <div
          key={service.id}
          className="group p-3 rounded-lg border border-slate-100 bg-white hover:border-rose-200 hover:shadow-sm cursor-pointer transition-all"
          onClick={() => onEdit(service)}
        >
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="flex-shrink-0 w-8 h-8 rounded-md bg-rose-50 flex items-center justify-center">
                <FileText className="w-4 h-4 text-rose-600" />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-slate-900 truncate">{service.name}</p>
                <p className="text-xs text-slate-500">
                  {formatPrice(service.price)}
                  <span className="text-slate-300 mx-1">/</span>
                  {getUnitShort(service.unit)}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-0.5 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                type="button"
                className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                onClick={(e) => { e.stopPropagation(); onEdit(service); }}
                title="Bearbeiten"
              >
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                onClick={(e) => { e.stopPropagation(); onDelete(service.id); }}
                title="Löschen"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
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
  const [searchQuery, setSearchQuery] = useState('');

  const filteredServices = useMemo(() => {
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return services.filter(
        (s) => s.name.toLowerCase().includes(q) || stripHtml(s.description).toLowerCase().includes(q)
      );
    }
    if (selectedFolderId) {
      return services.filter((s) => s.folderId === selectedFolderId);
    }
    return services;
  }, [services, selectedFolderId, searchQuery]);

  const serviceCounts = useMemo(() => {
    const directCounts: Record<string, number> = {};
    for (const s of services) {
      directCounts[s.folderId] = (directCounts[s.folderId] || 0) + 1;
    }
    const totalCounts: Record<string, number> = {};
    const getCount = (folderId: string): number => {
      if (totalCounts[folderId] !== undefined) return totalCounts[folderId];
      const childFolders = folders.filter((f) => f.parentId === folderId);
      let count = directCounts[folderId] || 0;
      for (const child of childFolders) {
        count += getCount(child.id);
      }
      totalCounts[folderId] = count;
      return count;
    };
    for (const f of folders) getCount(f.id);
    return totalCounts;
  }, [services, folders]);

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
          const normalized = data.map((s: any) => ({
            ...s,
            id: String(s.id),
            folderId: String(s.folderId),
            unit: typeof s.unit === 'object' && s.unit?.name ? s.unit.name : (s.unit || '')
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
          folderId: String(savedService.folderId),
          unit: typeof (savedService as any).unit === 'object' && (savedService as any).unit?.name
            ? (savedService as any).unit.name
            : (savedService.unit || '')
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
    <PageLayout
      ribbonCategory="Textverwaltung"
      title="LEISTUNGSEDITOR"
      subtitle="Verwaltung von Leistungen und Beschreibungen nach Kategorien."
      actions={
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            className="border-rose-300 text-rose-700 hover:bg-rose-50"
            onClick={handleEditFolderDescription}
            disabled={!selectedFolderId}
          >
            <FileText className="w-4 h-4" /> Beschreibung
          </Button>
          <Button
            size="sm"
            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
            onClick={handleCreateService}
            disabled={!isSelectedFolderLeaf}
            title={!isSelectedFolderLeaf ? 'Leistungen nur in unterster Ordnerebene möglich' : undefined}
          >
            <Plus className="w-4 h-4" /> Neue Leistung
          </Button>
        </div>
      }
    >
      <div className="grid grid-cols-1 xl:grid-cols-[280px_280px_1fr] gap-6">
        {/* Column 1: Folder Tree */}
        <Card className="p-4 border-rose-100 shadow-lg">
          <div className="mb-3">
            <p className="text-xs uppercase tracking-wide text-slate-500">Kategorien</p>
            <h4 className="text-base font-semibold text-slate-900">Ordnerstruktur</h4>
          </div>
          {loadingFolders ? (
            <div className="flex items-center gap-2 text-slate-500 text-sm py-6">
              <Loader2 className="w-4 h-4 animate-spin" /> Wird geladen...
            </div>
          ) : folderError ? (
            <div className="text-red-600 text-sm py-6">{folderError}</div>
          ) : (
            <div className="max-h-[calc(100vh-300px)] overflow-y-auto pr-1">
              <FolderTree
                folders={folders}
                selectedId={selectedFolderId}
                onSelect={(id) => setSelectedFolderId(id)}
                onChildrenLoaded={handleChildrenLoaded}
                serviceCounts={serviceCounts}
              />
            </div>
          )}
        </Card>

        {/* Column 2: Service List */}
        <Card className="p-4 border-rose-100 shadow-lg">
          <div className="mb-3">
            <p className="text-xs uppercase tracking-wide text-slate-500">Leistungen</p>
            <h4 className="text-base font-semibold text-slate-900 truncate">
              {selectedFolder?.name || 'Auswählen'}
              {filteredServices.length > 0 && (
                <span className="ml-2 text-xs font-normal px-1.5 py-0.5 rounded-full bg-rose-50 text-rose-600">
                  {filteredServices.length}
                </span>
              )}
            </h4>
          </div>
          <div className="relative mb-3">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Leistung suchen…"
              className="w-full pl-8 pr-8 py-1.5 text-sm rounded-lg border border-slate-200 focus:border-rose-300 focus:ring-1 focus:ring-rose-200 outline-none transition-colors"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          <div className="max-h-[calc(100vh-300px)] overflow-y-auto pr-1">
            <ServiceList services={filteredServices} onEdit={handleEditService} onDelete={handleDeleteService} />
          </div>
        </Card>

        {/* Column 3: Editor / Detail */}
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
            <Card className="p-16 h-full border-dashed border-slate-200 text-center shadow-inner">
              <FilePlus className="w-12 h-12 text-slate-200 mx-auto mb-3" />
              <p className="text-slate-500">Wählen Sie eine Leistung aus oder erstellen Sie eine neue.</p>
            </Card>
          )}
        </div>
      </div>
    </PageLayout>
  );
};

export default Leistungseditor;
