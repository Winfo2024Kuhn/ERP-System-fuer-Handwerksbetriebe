import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  Eye,
  FileText,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Search,
  Trash2,
  User,
  X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { cn } from '../lib/utils';
import { type Kunde, type TextTemplate } from '../types';
import { PageLayout } from '../components/layout/PageLayout';
import { TiptapEditor } from '../components/TiptapEditor';
import { useToast } from '../components/ui/toast';


const PLACEHOLDERS = [
  { token: '{{Anrede}}', label: 'Anrede' },
  { token: '{{KUNDENNAME}}', label: 'Kundenname' },
  { token: '{{Ansprechpartner}}', label: 'Ansprechpartner (Kunde)' },
  { token: '{{KUNDENNUMMER}}', label: 'Kundennummer' },
  { token: '{{KUNDENADRESSE}}', label: 'Kundenadresse' },
  { token: '{{DATUM}}', label: 'Datum (heute)' },
  { token: '{{ZAHLUNGSZIEL}}', label: 'Zahlungsziel (Datum)' },
  { token: '{{ZAHLUNGSZIEL_TAGE}}', label: 'Zahlungsziel (Tage)' },
  { token: '{{BAUVORHABEN}}', label: 'Bauvorhaben' },
  { token: '{{DOKUMENTNUMMER}}', label: 'Dokumentnummer' },
  { token: '{{PROJEKTNUMMER}}', label: 'Projektnummer' },
  { token: '{{DOKUMENTTYP}}', label: 'Dokumenttyp' },
  { token: '{{BEZUGSDOKUMENTNUMMER}}', label: 'Bezugsdok. Nummer' },
  { token: '{{BEZUGSDOKUMENTDATUM}}', label: 'Bezugsdok. Datum' },
  { token: '{{BEZUGSDOKUMENTTYP}}', label: 'Bezugsdok. Typ' },
];

const ANREDE_LABELS: Record<string, string> = {
  HERR: 'Sehr geehrter Herr',
  FRAU: 'Sehr geehrte Frau',
  FAMILIE: 'Sehr geehrte Familie',
  DAMEN_HERREN: 'Sehr geehrte Damen und Herren',
  FIRMA: 'Sehr geehrte Damen und Herren'
};

const PREVIEW_BADGE_CLASSES = 'inline-flex items-center px-2 py-0.5 bg-yellow-200 text-slate-900 font-mono text-sm rounded';
const DEFAULT_PAYMENT_DAYS = 8;

const EMPTY_TEMPLATE: TextTemplate = {
  id: '',
  name: '',
  docTypes: [],
  content: [
    '<p>Sehr geehrte Damen und Herren,</p>',
    '<p><br></p>',
    '<p>vielen Dank für Ihre Anfrage. Nachfolgend finden Sie unsere Anfragesdetails.</p>',
    '<p><br></p>',
    '<p><strong>Anrede:</strong> {{Anrede}}</p>',
    '<p><strong>Kunde:</strong> {{Kundenname}}</p>',
    '<p><strong>Ansprechpartner:</strong> {{Ansprechpartner}}</p>',
    '<p><strong>Zahlungsziel:</strong> {{Zahlungsziel}}</p>',
    '<p><br></p>',
    '<p>Mit freundlichen Grüßen</p>'
  ].join('')
};

function formatDate(date: Date) {
  try {
    return date.toLocaleDateString('de-DE');
  } catch {
    return '';
  }
}

function addDays(date: Date, days: number) {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function escapeHtml(value: string) {
  return value.replace(/[&<"']/g, (char) => {
    switch (char) {
      case '&':
        return '&amp;';
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '"':
        return '&quot;';
      case "'":
        return '&#39;';
      default:
        return char;
    }
  });
}

function highlightPlaceholder(token: string) {
  return `<span class="${PREVIEW_BADGE_CLASSES}" data-preview-placeholder="true">${escapeHtml(token)}</span>`;
}

function formatAnrede(value: string | null | undefined) {
  if (!value) return '';
  const key = value.trim().toUpperCase();
  return ANREDE_LABELS[key] || value;
}

function formatAdresse(kunde: Kunde | null) {
  if (!kunde) return null;
  const plzOrt = [kunde.plz, kunde.ort].filter(Boolean).join(' ');
  const parts = [kunde.strasse, plzOrt].filter(Boolean);
  return parts.length > 0 ? parts.join(', ') : null;
}

function replacePlaceholders(html: string, kunde: Kunde | null, days: number) {
  const ansprechpartner = kunde?.ansprechpartner?.trim() || kunde?.ansprechspartner?.trim() || null;
  const replacements: Record<string, string | null> = {
    '{{Anrede}}': formatAnrede(kunde?.anrede) || null,
    '{{Kundenname}}': kunde?.name?.trim() || null,
    '{{KUNDENNAME}}': kunde?.name?.trim() || null,
    '{{Ansprechpartner}}': ansprechpartner,
    '{{ANSPRECHPARTNER}}': ansprechpartner,
    '{{KUNDENNUMMER}}': kunde?.kundennummer?.trim() || null,
    '{{KUNDENADRESSE}}': formatAdresse(kunde),
    '{{DATUM}}': formatDate(new Date()),
    '{{Zahlungsziel}}': formatDate(addDays(new Date(), days)) || null,
    '{{ZAHLUNGSZIEL}}': formatDate(addDays(new Date(), days)) || null,
    '{{ZAHLUNGSZIEL_TAGE}}': days ? String(days) : null,
    '{{BAUVORHABEN}}': null,
    '{{DOKUMENTNUMMER}}': null,
    '{{PROJEKTNUMMER}}': null,
    '{{SEITENZAHL}}': null,
    '{{DOKUMENTTYP}}': null,
    '{{BEZUGSDOKUMENTNUMMER}}': null,
    '{{BEZUGSDOKUMENTDATUM}}': null,
    '{{BEZUGSDOKUMENTTYP}}': null
  };

  let result = html || '';
  result = result.replace(/<button[^>]*data-placeholder-remove[^>]*>.*?<\/button>/g, '');
  result = result.replace(/<span[^>]*data-placeholder-chip[^>]*data-placeholder-token="([^"]+)"[^>]*>.*?<\/span>/g, '$1');
  result = result.replace(/<span[^>]*data-placeholder-chip[^>]*>(.*?)<\/span>/g, '$1');

  Object.entries(replacements).forEach(([token, value]) => {
    const regex = new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
    const replacement = value ? escapeHtml(value) : highlightPlaceholder(token);
    result = result.replace(regex, replacement);
  });

  result = result.replace(/{{[^}]+}}/g, (match) => highlightPlaceholder(match));
  return result;
}

function stripHtml(html: string) {
  const div = document.createElement('div');
  div.innerHTML = html;
  return div.textContent || '';
}

/* ─── Sidebar: Template List Item ─── */
function TemplateCard({
  template,
  active,
  onEdit,
  onDelete,
  onSelect
}: {
  template: TextTemplate;
  active: boolean;
  onEdit: () => void;
  onDelete: () => void;
  onSelect: () => void;
}) {
  const preview = stripHtml(template.content).trim();
  const docTypes = template.docTypes || [];

  return (
    <div
      className={cn(
        'group relative p-3 rounded-lg cursor-pointer transition-all border',
        active
          ? 'bg-rose-50 border-rose-200 shadow-sm'
          : 'bg-white border-slate-100 hover:border-slate-200 hover:shadow-sm'
      )}
      onClick={onSelect}
    >
      <div className="flex items-start gap-2.5">
        <div className={cn('flex-shrink-0 mt-0.5', active ? 'text-rose-600' : 'text-slate-400')}>
          <FileText className="w-4 h-4" />
        </div>
        <div className="flex-1 min-w-0">
          <p className={cn('text-sm font-medium truncate', active ? 'text-rose-900' : 'text-slate-900')}>
            {template.name || 'Unbenannt'}
          </p>
          <p className="text-xs text-slate-400 line-clamp-1 mt-0.5">{preview || 'Leer'}</p>
          {docTypes.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1.5">
              {docTypes.slice(0, 2).map((type) => (
                <span key={type} className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">
                  {type}
                </span>
              ))}
              {docTypes.length > 2 && (
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-400">
                  +{docTypes.length - 2}
                </span>
              )}
            </div>
          )}
        </div>
        <div className={cn(
          'flex items-center gap-0.5 flex-shrink-0 transition-opacity',
          active ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
        )}>
          <button
            type="button"
            className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
            onClick={(event) => { event.stopPropagation(); onEdit(); }}
            title="Bearbeiten"
          >
            <Pencil className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
            onClick={(event) => { event.stopPropagation(); onDelete(); }}
            title="Löschen"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}

/* ─── Editor: Create / Edit Template ─── */
function TemplateEditor({
  template,
  onChange,
  onSave,
  onCancel,
  docTypeOptions
}: {
  template: TextTemplate;
  onChange: (tpl: TextTemplate) => void;
  onSave: (tpl: TextTemplate) => void;
  onCancel: () => void;
  docTypeOptions: string[];
}) {
  const editorRef = useRef<{ chain: () => { focus: () => { insertContent: (content: string) => { run: () => void } } } } | null>(null);

  const addDocType = (value: string) => {
    const val = value.trim();
    if (!val) return;
    const current = template.docTypes || [];
    if (current.some((v) => v.toLowerCase() === val.toLowerCase())) {
      return;
    }
    onChange({ ...template, docTypes: [...current, val] });
  };

  const removeDocType = (value: string) => {
    onChange({ ...template, docTypes: (template.docTypes || []).filter((v) => v !== value) });
  };

  const availableSuggestions = docTypeOptions.filter(
    (option) => !(template.docTypes || []).some((selected) => selected.toLowerCase() === option.toLowerCase())
  );

  return (
    <Card className="border-rose-100 shadow-lg overflow-hidden">
      {/* Editor Header */}
      <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">
              {template.id ? 'Vorlage bearbeiten' : 'Neue Vorlage'}
            </p>
            <h3 className="text-lg font-semibold text-slate-900">
              {template.name || 'Unbenannt'}
            </h3>
          </div>
          <div className="flex gap-2">
            <Button
              size="sm"
              className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
              onClick={() => onSave(template)}
            >
              <Save className="w-4 h-4" /> Speichern
            </Button>
            <Button variant="outline" size="sm" onClick={onCancel}>
              <X className="w-4 h-4" /> Abbrechen
            </Button>
          </div>
        </div>
      </div>

      <div className="p-6 space-y-5">
        {/* Name + DocTypes row */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-2">
            <Label htmlFor="template-name">Vorlagen-Name</Label>
            <Input
              id="template-name"
              value={template.name}
              onChange={(event) => onChange({ ...template, name: event.target.value })}
              placeholder="z.B. Rechnung, Angebot, Mahnung..."
            />
          </div>

          <div className="space-y-2">
            <Label>Dokumenttypen <span className="text-slate-400 font-normal">(optional)</span></Label>
            <div className="flex flex-wrap gap-1.5 min-h-[40px] items-center p-2 border border-slate-200 rounded-md bg-white">
              {(template.docTypes || []).map((type) => (
                <span key={type} className="inline-flex items-center gap-1 rounded-full bg-rose-50 text-rose-700 px-2.5 py-0.5 text-xs font-medium">
                  {type}
                  <button type="button" onClick={() => removeDocType(type)} className="text-rose-400 hover:text-rose-700">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
              {(template.docTypes || []).length === 0 && (
                <span className="text-sm text-slate-400 px-1">Keine Zuordnung</span>
              )}
            </div>
            {availableSuggestions.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {availableSuggestions.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    className="text-xs px-2 py-1 rounded border border-dashed border-slate-300 text-slate-500 hover:border-rose-300 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                    onClick={() => addDocType(suggestion)}
                  >
                    + {suggestion}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Placeholder buttons bar */}
        <div className="bg-rose-50 border border-rose-100 rounded-lg px-3 py-2 flex flex-wrap items-center gap-2">
          <span className="text-xs text-rose-600 font-semibold uppercase tracking-wide">Platzhalter einfügen:</span>
          {PLACEHOLDERS.map((placeholder) => (
            <Button
              key={placeholder.token}
              variant="outline"
              size="sm"
              className="bg-white border-rose-200 text-rose-700 hover:bg-rose-100 h-7 text-xs"
              onClick={() => {
                if (editorRef.current) {
                  editorRef.current.chain().focus().insertContent(placeholder.token).run();
                } else {
                  const current = template.content || '';
                  onChange({ ...template, content: current + placeholder.token });
                }
              }}
            >
              {placeholder.label}
            </Button>
          ))}
        </div>

        {/* Rich Text Editor */}
        <TiptapEditor
          value={template.content}
          onChange={(html) => onChange({ ...template, content: html })}
          onEditorReady={(editor) => { editorRef.current = editor; }}
        />
        <p className="text-xs text-slate-400">Bilder per Drag & Drop oder Einfügen (Ctrl+V) hinzufügen. Klicken Sie auf ein Bild, um es zu bearbeiten.</p>
      </div>
    </Card>
  );
}

/* ─── View Mode: Template Info + Preview ─── */
function TemplateView({
  template,
  kunden,
  selectedKunde,
  onSelectKunde,
  onReset,
  preview,
  copied,
  onCopy,
  onEdit,
  onDelete
}: {
  template: TextTemplate;
  kunden: Kunde[];
  selectedKunde: string | number | null;
  onSelectKunde: (id: string | number) => void;
  onReset: () => void;
  preview: string;
  copied: 'text' | 'html' | null;
  onCopy: (type: 'text' | 'html', content: string) => Promise<void>;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const activeKunde = useMemo(() => kunden.find((kunde) => kunde.id === selectedKunde) || null, [kunden, selectedKunde]);
  const tage = activeKunde?.zahlungsziel ?? DEFAULT_PAYMENT_DAYS;
  const docTypes = template.docTypes || [];
  const [kundenPanelOpen, setKundenPanelOpen] = useState(true);

  return (
    <div className="space-y-4">
      {/* Template Header Card */}
      <Card className="border-rose-100 shadow-lg overflow-hidden">
        <div className="px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-rose-50 flex items-center justify-center">
              <FileText className="w-5 h-5 text-rose-600" />
            </div>
            <div className="min-w-0">
              <h3 className="text-lg font-semibold text-slate-900 truncate">{template.name}</h3>
              {docTypes.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-0.5">
                  {docTypes.map((type) => (
                    <span key={type} className="text-xs px-2 py-0.5 rounded-full bg-rose-50 text-rose-600 font-medium">
                      {type}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <Button
              variant="outline"
              size="sm"
              className="border-rose-300 text-rose-700 hover:bg-rose-50"
              onClick={onEdit}
            >
              <Pencil className="w-4 h-4" /> Bearbeiten
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-red-500 hover:text-red-700 hover:bg-red-50"
              onClick={onDelete}
            >
              <Trash2 className="w-4 h-4" />
            </Button>
          </div>
        </div>
      </Card>

      {/* Kundendaten Panel (collapsible) */}
      <Card className="border-slate-100 shadow-sm overflow-hidden">
        <button
          type="button"
          className="w-full px-5 py-3 flex items-center justify-between text-left hover:bg-slate-50 transition-colors"
          onClick={() => setKundenPanelOpen(!kundenPanelOpen)}
        >
          <div className="flex items-center gap-2">
            <User className="w-4 h-4 text-slate-400" />
            <span className="text-sm font-medium text-slate-700">
              Kundendaten für Vorschau
            </span>
            {activeKunde && (
              <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-500">
                {activeKunde.name}
              </span>
            )}
          </div>
          {kundenPanelOpen
            ? <ChevronUp className="w-4 h-4 text-slate-400" />
            : <ChevronDown className="w-4 h-4 text-slate-400" />
          }
        </button>

        {kundenPanelOpen && (
          <div className="px-5 pb-4 space-y-3 border-t border-slate-100 pt-3">
            <div className="flex items-end gap-3">
              <div className="flex-1">
                <Label htmlFor="kunde-select" className="text-xs text-slate-500">Kunde auswählen</Label>
                <Select
                  value={String(selectedKunde || '')}
                  onChange={(value) => onSelectKunde(value)}
                  options={kunden.map((kunde) => ({
                    value: String(kunde.id),
                    label: `${kunde.name}${(kunde.ansprechpartner || kunde.ansprechspartner) ? ` (${kunde.ansprechpartner || kunde.ansprechspartner})` : ''}`
                  }))}
                  placeholder="Kunde auswählen"
                  className="mt-1"
                />
              </div>
              <Button variant="ghost" size="sm" onClick={onReset} title="Zurücksetzen" className="text-slate-400 hover:text-slate-600">
                <RotateCcw className="w-4 h-4" />
              </Button>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              <div className="rounded-md bg-slate-50 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wide text-slate-400 mb-0.5">Kunde</p>
                <p className="text-sm text-slate-800 truncate">{activeKunde?.name || '–'}</p>
              </div>
              <div className="rounded-md bg-slate-50 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wide text-slate-400 mb-0.5">Anrede</p>
                <p className="text-sm text-slate-800 truncate">{formatAnrede(activeKunde?.anrede) || '–'}</p>
              </div>
              <div className="rounded-md bg-slate-50 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wide text-slate-400 mb-0.5">Ansprechpartner</p>
                <p className="text-sm text-slate-800 truncate">{activeKunde?.ansprechpartner || activeKunde?.ansprechspartner || '–'}</p>
              </div>
              <div className="rounded-md bg-slate-50 px-3 py-2">
                <p className="text-[10px] uppercase tracking-wide text-slate-400 mb-0.5">Zahlungsziel</p>
                <p className="text-sm text-slate-800">{tage} Tage ({formatDate(addDays(new Date(), tage))})</p>
              </div>
            </div>
          </div>
        )}
      </Card>

      {/* Preview Card */}
      <Card className="border-slate-100 shadow-lg overflow-hidden">
        <div className="px-6 py-3 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Eye className="w-4 h-4 text-slate-400" />
            <span className="text-sm font-medium text-slate-700">Vorschau</span>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => onCopy('text', stripHtml(preview || ''))}>
              {copied === 'text' ? <Check className="w-3.5 h-3.5 text-green-600" /> : <Copy className="w-3.5 h-3.5" />}
              {copied === 'text' ? 'Kopiert!' : 'Text'}
            </Button>
            <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => onCopy('html', preview || '')}>
              {copied === 'html' ? <Check className="w-3.5 h-3.5 text-green-600" /> : <Copy className="w-3.5 h-3.5" />}
              {copied === 'html' ? 'Kopiert!' : 'HTML'}
            </Button>
          </div>
        </div>
        <div
          className="min-h-[400px] p-6 bg-white prose prose-slate max-w-none"
          dangerouslySetInnerHTML={{ __html: preview || '' }}
        />
      </Card>
    </div>
  );
}

export default function TextbausteinEditor() {
  const toast = useToast();
  const [templates, setTemplates] = useState<TextTemplate[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState<TextTemplate | null>(null);
  const [kunden, setKunden] = useState<Kunde[]>([]);
  const [selectedKunde, setSelectedKunde] = useState<string | number | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const [copied, setCopied] = useState<'text' | 'html' | null>(null);
  const [docTypeOptions, setDocTypeOptions] = useState<string[]>([]);

  const fetchTemplates = useCallback(async () => {
    try {
      const response = await fetch('/api/textbausteine');
      const data = response.ok ? await response.json() : [];
      const mapped: TextTemplate[] = Array.isArray(data)
        ? data.map((template: { id: number | string; name?: string; html?: string; dokumenttypen?: unknown[] }) => ({
          id: String(template.id),
          name: template.name || '',
          content: template.html || '',
          docTypes: Array.isArray(template.dokumenttypen) ? template.dokumenttypen.map(String) : []
        }))
        : [];

      setTemplates(mapped);
      if (mapped.length === 0) {
        setSelectedId(null);
        return;
      }
      if (!selectedId || !mapped.some((tpl) => tpl.id === selectedId)) {
        setSelectedId(mapped[0].id);
      }
    } catch (error) {
      console.warn('Templates konnten nicht geladen werden', error);
    }
  }, [selectedId]);

  const fetchKunden = useCallback(async () => {
    try {
      const response = await fetch('/api/kunden');
      const data = await response.json();
      const mapped: Kunde[] = Array.isArray(data?.kunden)
        ? data.kunden.map((kunde: { id?: number; kundenId?: number; kundennummer?: number; name?: string; kunde?: string; ansprechpartner?: string; ansprechspartner?: string; anrede?: string; zahlungsziel?: number }) => ({
          id: String(kunde.id ?? kunde.kundenId ?? kunde.kundennummer ?? Math.random()),
          name: kunde.name || kunde.kunde || '',
          ansprechpartner: kunde.ansprechpartner || kunde.ansprechspartner || '',
          anrede: kunde.anrede || '',
          zahlungsziel: typeof kunde.zahlungsziel === 'number' ? kunde.zahlungsziel : DEFAULT_PAYMENT_DAYS
        }))
        : [];
      setKunden(mapped);
      if (mapped[0]) {
        setSelectedKunde((current) => current ?? mapped[0].id);
      }
    } catch (error) {
      console.warn('Kunden konnten nicht geladen werden', error);
    }
  }, []);

  const fetchDokumenttypen = useCallback(async () => {
    try {
      const response = await fetch('/api/textbausteine/dokumenttypen');
      const data = await response.json();
      const list: string[] = Array.isArray(data) ? data.map((d) => String(d)) : [];
      setDocTypeOptions(list);
    } catch (error) {
      console.warn('Dokumenttypen konnten nicht geladen werden', error);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchTemplates();
    fetchKunden();
    fetchDokumenttypen();
  }, [fetchTemplates, fetchKunden, fetchDokumenttypen]);

  const saveTemplate = async (template: TextTemplate) => {
    if (!template.name.trim()) {
      toast.warning('Bitte einen Vorlagennamen eingeben.');
      return;
    }

    const payload = {
      name: template.name,
      typ: 'FREITEXT',
      beschreibung: '',
      html: template.content,
      placeholders: PLACEHOLDERS.map((placeholder) => placeholder.token),
      dokumenttypen: template.docTypes || []
    };
    const isUpdate = Boolean(template.id);
    const url = isUpdate ? `/api/textbausteine/${template.id}` : '/api/textbausteine';
    const method = isUpdate ? 'PUT' : 'POST';

    try {
      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        toast.error('Die Vorlage konnte nicht gespeichert werden.');
        return;
      }
      const saved = await response.json().catch(() => null);
      setEditing(null);
      await fetchTemplates();
      if (saved?.id) {
        setSelectedId(String(saved.id));
      }
    } catch (error) {
      console.warn('Speichern fehlgeschlagen', error);
    }
  };

  const deleteTemplate = async (template: TextTemplate) => {
    try {
      const response = await fetch(`/api/textbausteine/${template.id}`, { method: 'DELETE' });
      if (!response.ok) {
        toast.error('Die Vorlage konnte nicht gelöscht werden.');
        return;
      }
      if (selectedId === template.id) {
        setSelectedId(null);
      }
      await fetchTemplates();
    } catch (error) {
      console.error('Löschen fehlgeschlagen', error);
    }
  };

  const handleCopy = async (type: 'text' | 'html', content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(type);
      setTimeout(() => setCopied(null), 2000);
    } catch (error) {
      console.warn('Copy failed', error);
    }
  };

  const activeTemplate = useMemo(() => templates.find((template) => template.id === selectedId) || null, [templates, selectedId]);
  const effectiveKundeId = selectedKunde ?? (kunden[0]?.id ?? null);
  const activeKunde = useMemo(() => kunden.find((kunde) => kunde.id === effectiveKundeId) || null, [kunden, effectiveKundeId]);
  const activeTage = activeKunde?.zahlungsziel ?? DEFAULT_PAYMENT_DAYS;
  const previewHtml = useMemo(
    () => (activeTemplate ? replacePlaceholders(activeTemplate.content, activeKunde, activeTage) : ''),
    [activeTemplate, activeKunde, activeTage]
  );

  const filteredTemplates = useMemo(() => {
    if (!searchQuery.trim()) return templates;
    const q = searchQuery.toLowerCase();
    return templates.filter((tpl) =>
      tpl.name.toLowerCase().includes(q) ||
      stripHtml(tpl.content).toLowerCase().includes(q)
    );
  }, [templates, searchQuery]);

  return (
    <PageLayout
      ribbonCategory="Vorlagen & Stammdaten"
      title="TEXTVORLAGEN"
      subtitle="Verwaltung und Bearbeitung von Textbausteinen für Formulare."
      actions={
        <Button
          size="sm"
          className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
          onClick={() => { setEditing({ ...EMPTY_TEMPLATE }); setSelectedId(null); }}
        >
          <Plus className="w-4 h-4" /> Neue Vorlage
        </Button>
      }
    >
      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] xl:grid-cols-[320px_1fr] gap-6">
        {/* ─── Sidebar ─── */}
        <div className="space-y-3">
          <Card className="p-4 border-rose-100 shadow-lg">
            {/* Sidebar Header */}
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-xs uppercase tracking-wide text-slate-500">Übersicht</p>
                <h4 className="text-base font-semibold text-slate-900">
                  Vorlagen
                  {templates.length > 0 && (
                    <span className="ml-2 text-xs font-normal px-1.5 py-0.5 rounded-full bg-rose-50 text-rose-600">
                      {templates.length}
                    </span>
                  )}
                </h4>
              </div>
            </div>

            {/* Search */}
            {templates.length > 3 && (
              <div className="relative mb-3">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                <Input
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Vorlage suchen..."
                  className="pl-8 h-8 text-sm"
                />
              </div>
            )}

            {/* Template List */}
            {filteredTemplates.length === 0 ? (
              <div className="py-8 text-center">
                <FileText className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                <p className="text-sm text-slate-400">
                  {templates.length === 0 ? 'Noch keine Vorlagen erstellt.' : 'Keine Treffer.'}
                </p>
              </div>
            ) : (
              <div className="space-y-1.5 max-h-[calc(100vh-340px)] overflow-y-auto pr-1">
                {filteredTemplates.map((template) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    active={activeTemplate?.id === template.id && !editing}
                    onSelect={() => {
                      setSelectedId(template.id);
                      setEditing(null);
                    }}
                    onEdit={() => setEditing({ ...template })}
                    onDelete={() => deleteTemplate(template)}
                  />
                ))}
              </div>
            )}
          </Card>
        </div>

        {/* ─── Main Content ─── */}
        <div>
          {editing ? (
            <TemplateEditor
              template={editing}
              onChange={(tpl) => setEditing(tpl)}
              onSave={saveTemplate}
              onCancel={() => setEditing(null)}
              docTypeOptions={docTypeOptions}
            />
          ) : activeTemplate ? (
            <TemplateView
              template={activeTemplate}
              kunden={kunden}
              selectedKunde={selectedKunde}
              onSelectKunde={(id) => setSelectedKunde(id)}
              onReset={() => {
                if (kunden[0]) setSelectedKunde(kunden[0].id);
              }}
              preview={previewHtml}
              copied={copied}
              onCopy={handleCopy}
              onEdit={() => setEditing({ ...activeTemplate })}
              onDelete={() => deleteTemplate(activeTemplate)}
            />
          ) : (
            <Card className="p-16 text-center border-dashed border-slate-200 shadow-inner">
              <FileText className="w-12 h-12 text-slate-200 mx-auto mb-3" />
              <p className="text-slate-500">Wählen Sie eine Vorlage aus oder erstellen Sie eine neue.</p>
            </Card>
          )}
        </div>
      </div>
    </PageLayout>
  );
}
