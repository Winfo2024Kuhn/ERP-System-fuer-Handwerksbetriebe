import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  Copy,
  Eye,
  FileText,
  Mail,
  Pencil,
  Plus,
  RotateCcw,
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
import { cn } from '../lib/utils';
import { PageLayout } from '../components/layout/PageLayout';
import { TiptapEditor } from '../components/TiptapEditor';
import { useToast } from '../components/ui/toast';

interface EmailTemplate {
  id?: number | string;
  dokumentTyp: string;
  name: string;
  subjectTemplate: string;
  htmlBody: string;
  aktiv?: boolean;
}

interface DokumentTypOption {
  value: string;
  label: string;
}

interface PlaceholderDef {
  token: string;
  label: string;
}

const PREVIEW_BADGE_CLASSES =
  'inline-flex items-center px-2 py-0.5 bg-yellow-200 text-slate-900 font-mono text-sm rounded';

const SAMPLE_CONTEXT: Record<string, string> = {
  ANREDE: 'Sehr geehrter Herr Mustermann',
  KUNDENNAME: 'Max Mustermann',
  BAUVORHABEN: 'Einfamilienhaus Musterstraße 1',
  PROJEKTNUMMER: '2026-05-00012',
  DOKUMENTNUMMER: 'RG-2026-0042',
  RECHNUNGSDATUM: '01.05.2026',
  FAELLIGKEITSDATUM: '15.05.2026',
  BETRAG: '1.234,56 €',
  BENUTZER: 'Thomas Kuhn',
  REVIEW_LINK: '<em>(Bewertungs-Link)</em>'
};

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
  return `<span class="${PREVIEW_BADGE_CLASSES}">${escapeHtml(token)}</span>`;
}

function renderPreview(html: string, useSampleData: boolean) {
  if (!html) return '';
  return html.replace(/\{\{\s*([A-Z0-9_]+)\s*\}\}/g, (match, key: string) => {
    if (useSampleData && SAMPLE_CONTEXT[key] !== undefined) {
      return SAMPLE_CONTEXT[key];
    }
    return highlightPlaceholder(match);
  });
}

function stripHtml(html: string) {
  const div = document.createElement('div');
  div.innerHTML = html;
  return div.textContent || '';
}

function emptyTemplate(dokumentTyp = 'RECHNUNG'): EmailTemplate {
  return {
    dokumentTyp,
    name: '',
    subjectTemplate: '',
    htmlBody: '<p>Sehr geehrte Damen und Herren,</p><p><br></p><p>...</p>',
    aktiv: true
  };
}

/* ─── Sidebar Card ─── */
function TemplateCard({
  template,
  active,
  dokumenttypLabel,
  onSelect,
  onEdit,
  onDelete
}: {
  template: EmailTemplate;
  active: boolean;
  dokumenttypLabel: string;
  onSelect: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const preview = stripHtml(template.htmlBody).trim();
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
          <Mail className="w-4 h-4" />
        </div>
        <div className="flex-1 min-w-0">
          <p className={cn('text-sm font-medium truncate', active ? 'text-rose-900' : 'text-slate-900')}>
            {template.name || dokumenttypLabel}
          </p>
          <p className="text-xs text-slate-400 line-clamp-1 mt-0.5">{preview || 'Leer'}</p>
          <div className="flex flex-wrap gap-1 mt-1.5">
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">
              {dokumenttypLabel}
            </span>
            {template.aktiv === false && (
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-400">
                inaktiv
              </span>
            )}
          </div>
        </div>
        <div
          className={cn(
            'flex items-center gap-0.5 flex-shrink-0 transition-opacity',
            active ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
          )}
        >
          <button
            type="button"
            className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
            onClick={(event) => {
              event.stopPropagation();
              onEdit();
            }}
            title="Bearbeiten"
          >
            <Pencil className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors"
            onClick={(event) => {
              event.stopPropagation();
              onDelete();
            }}
            title="Löschen"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}

/* ─── Editor: Create / Edit ─── */
function TemplateEditorPanel({
  template,
  onChange,
  onSave,
  onCancel,
  dokumenttypOptions,
  placeholders,
  isNew
}: {
  template: EmailTemplate;
  onChange: (next: EmailTemplate) => void;
  onSave: (tpl: EmailTemplate) => void;
  onCancel: () => void;
  dokumenttypOptions: DokumentTypOption[];
  placeholders: PlaceholderDef[];
  isNew: boolean;
}) {
  const editorRef = useRef<{
    chain: () => { focus: () => { insertContent: (content: string) => { run: () => void } } };
  } | null>(null);

  const subjectInputRef = useRef<HTMLInputElement | null>(null);
  const [insertTarget, setInsertTarget] = useState<'subject' | 'body'>('body');

  const insertPlaceholder = (token: string) => {
    if (insertTarget === 'subject') {
      const input = subjectInputRef.current;
      if (input) {
        const start = input.selectionStart ?? template.subjectTemplate.length;
        const end = input.selectionEnd ?? template.subjectTemplate.length;
        const next =
          template.subjectTemplate.substring(0, start) +
          token +
          template.subjectTemplate.substring(end);
        onChange({ ...template, subjectTemplate: next });
        requestAnimationFrame(() => {
          input.focus();
          const pos = start + token.length;
          input.setSelectionRange(pos, pos);
        });
      } else {
        onChange({ ...template, subjectTemplate: (template.subjectTemplate || '') + token });
      }
      return;
    }
    if (editorRef.current) {
      editorRef.current.chain().focus().insertContent(token).run();
    } else {
      onChange({ ...template, htmlBody: (template.htmlBody || '') + token });
    }
  };

  return (
    <Card className="border-rose-100 shadow-lg overflow-hidden">
      <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">
              {isNew ? 'Neue E-Mail-Vorlage' : 'Vorlage bearbeiten'}
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
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-2">
            <Label htmlFor="email-template-name">Anzeigename</Label>
            <Input
              id="email-template-name"
              value={template.name}
              onChange={(event) => onChange({ ...template, name: event.target.value })}
              placeholder="z. B. Schlussrechnung Kurzfassung"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="email-template-doktyp">Dokumenttyp</Label>
            <Select
              value={template.dokumentTyp}
              onChange={(value) => onChange({ ...template, dokumentTyp: value })}
              options={dokumenttypOptions.map((option) => ({
                value: option.value,
                label: option.label
              }))}
              placeholder="Dokumenttyp wählen"
            />
            <p className="text-xs text-slate-400">
              Pro Dokumenttyp kann genau eine aktive Vorlage existieren.
            </p>
          </div>
        </div>

        {/* Subject */}
        <div className="space-y-2">
          <Label htmlFor="email-template-subject">Betreff</Label>
          <Input
            id="email-template-subject"
            ref={subjectInputRef}
            value={template.subjectTemplate}
            onChange={(event) => onChange({ ...template, subjectTemplate: event.target.value })}
            onFocus={() => setInsertTarget('subject')}
            placeholder="z. B. Rechnung: (BV: {{BAUVORHABEN}}) Rechnungsnummer: {{DOKUMENTNUMMER}}"
          />
        </div>

        {/* Placeholder bar */}
        <div className="bg-rose-50 border border-rose-100 rounded-lg px-3 py-2 flex flex-wrap items-center gap-2">
          <span className="text-xs text-rose-600 font-semibold uppercase tracking-wide">
            Platzhalter einfügen in:
          </span>
          <div className="inline-flex rounded border border-rose-200 overflow-hidden text-xs">
            <button
              type="button"
              className={cn(
                'px-2 py-1',
                insertTarget === 'subject'
                  ? 'bg-rose-600 text-white'
                  : 'bg-white text-rose-700 hover:bg-rose-100'
              )}
              onClick={() => setInsertTarget('subject')}
            >
              Betreff
            </button>
            <button
              type="button"
              className={cn(
                'px-2 py-1 border-l border-rose-200',
                insertTarget === 'body'
                  ? 'bg-rose-600 text-white'
                  : 'bg-white text-rose-700 hover:bg-rose-100'
              )}
              onClick={() => setInsertTarget('body')}
            >
              Nachricht
            </button>
          </div>
          <div className="w-px h-5 bg-rose-200 mx-1" />
          {placeholders.map((placeholder) => (
            <Button
              key={placeholder.token}
              variant="outline"
              size="sm"
              className="bg-white border-rose-200 text-rose-700 hover:bg-rose-100 h-7 text-xs"
              onClick={() => insertPlaceholder(placeholder.token)}
              title={placeholder.label}
            >
              {placeholder.token.replace(/[{}]/g, '')}
            </Button>
          ))}
        </div>

        {/* Editor */}
        <div className="space-y-2">
          <Label>Nachricht (HTML)</Label>
          <div onFocus={() => setInsertTarget('body')}>
            <TiptapEditor
              value={template.htmlBody}
              onChange={(html) => onChange({ ...template, htmlBody: html })}
              onEditorReady={(editor) => {
                editorRef.current = editor;
              }}
            />
          </div>
          <p className="text-xs text-slate-400">
            Tipp: Klicken Sie auf einen Platzhalter-Button, um ihn an der Cursor-Position
            einzufügen.
          </p>
        </div>

        {/* Aktiv-Schalter */}
        <label className="inline-flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
          <input
            type="checkbox"
            className="w-4 h-4 accent-rose-600"
            checked={template.aktiv !== false}
            onChange={(event) => onChange({ ...template, aktiv: event.target.checked })}
          />
          Vorlage aktiv (wird beim Versand verwendet)
        </label>
      </div>
    </Card>
  );
}

/* ─── View Mode: Preview ─── */
function TemplateView({
  template,
  dokumenttypLabel,
  preview,
  rawPreview,
  useSampleData,
  onToggleSample,
  copied,
  onCopy,
  onEdit,
  onDelete
}: {
  template: EmailTemplate;
  dokumenttypLabel: string;
  preview: string;
  rawPreview: string;
  useSampleData: boolean;
  onToggleSample: () => void;
  copied: 'text' | 'html' | null;
  onCopy: (type: 'text' | 'html', content: string) => Promise<void>;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const subjectPreview = useMemo(
    () =>
      template.subjectTemplate.replace(/\{\{\s*([A-Z0-9_]+)\s*\}\}/g, (match, key: string) => {
        if (useSampleData && SAMPLE_CONTEXT[key] !== undefined) {
          return SAMPLE_CONTEXT[key];
        }
        return match;
      }),
    [template.subjectTemplate, useSampleData]
  );

  return (
    <div className="space-y-4">
      {/* Header */}
      <Card className="border-rose-100 shadow-lg overflow-hidden">
        <div className="px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-rose-50 flex items-center justify-center">
              <Mail className="w-5 h-5 text-rose-600" />
            </div>
            <div className="min-w-0">
              <h3 className="text-lg font-semibold text-slate-900 truncate">{template.name}</h3>
              <div className="flex flex-wrap gap-1 mt-0.5">
                <span className="text-xs px-2 py-0.5 rounded-full bg-rose-50 text-rose-600 font-medium">
                  {dokumenttypLabel}
                </span>
                {template.aktiv === false && (
                  <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-500">
                    inaktiv
                  </span>
                )}
              </div>
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

      {/* Preview Toggle */}
      <Card className="border-slate-100 shadow-sm overflow-hidden">
        <div className="px-5 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Eye className="w-4 h-4 text-slate-400" />
            <span className="text-sm font-medium text-slate-700">Vorschau-Modus</span>
          </div>
          <div className="inline-flex rounded border border-slate-200 overflow-hidden text-xs">
            <button
              type="button"
              className={cn(
                'px-3 py-1.5',
                useSampleData
                  ? 'bg-rose-600 text-white'
                  : 'bg-white text-slate-600 hover:bg-slate-50'
              )}
              onClick={() => {
                if (!useSampleData) onToggleSample();
              }}
            >
              Mit Beispieldaten
            </button>
            <button
              type="button"
              className={cn(
                'px-3 py-1.5 border-l border-slate-200',
                !useSampleData
                  ? 'bg-rose-600 text-white'
                  : 'bg-white text-slate-600 hover:bg-slate-50'
              )}
              onClick={() => {
                if (useSampleData) onToggleSample();
              }}
            >
              Platzhalter sichtbar
            </button>
          </div>
        </div>
      </Card>

      {/* Preview */}
      <Card className="border-slate-100 shadow-lg overflow-hidden">
        <div className="px-6 py-3 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-xs uppercase tracking-wide text-slate-400 flex-shrink-0">
              Betreff:
            </span>
            <span className="text-sm font-medium text-slate-700 truncate">
              {subjectPreview || '–'}
            </span>
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs"
              onClick={() => onCopy('text', stripHtml(rawPreview || ''))}
            >
              {copied === 'text' ? (
                <Check className="w-3.5 h-3.5 text-green-600" />
              ) : (
                <Copy className="w-3.5 h-3.5" />
              )}
              {copied === 'text' ? 'Kopiert!' : 'Text'}
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs"
              onClick={() => onCopy('html', rawPreview || '')}
            >
              {copied === 'html' ? (
                <Check className="w-3.5 h-3.5 text-green-600" />
              ) : (
                <Copy className="w-3.5 h-3.5" />
              )}
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

export default function EmailTextvorlagenEditor() {
  const toast = useToast();
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [selectedId, setSelectedId] = useState<number | string | null>(null);
  const [editing, setEditing] = useState<EmailTemplate | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [copied, setCopied] = useState<'text' | 'html' | null>(null);
  const [useSampleData, setUseSampleData] = useState(true);
  const [dokumenttypOptions, setDokumenttypOptions] = useState<DokumentTypOption[]>([]);
  const [placeholders, setPlaceholders] = useState<PlaceholderDef[]>([]);

  const dokumenttypLabel = useCallback(
    (value: string) => dokumenttypOptions.find((option) => option.value === value)?.label || value,
    [dokumenttypOptions]
  );

  const fetchTemplates = useCallback(async () => {
    try {
      const response = await fetch('/api/email-textvorlagen');
      const data = response.ok ? await response.json() : [];
      const mapped: EmailTemplate[] = Array.isArray(data) ? data : [];
      setTemplates(mapped);
      setSelectedId((current) => {
        if (current && mapped.some((tpl) => String(tpl.id) === String(current))) return current;
        return mapped[0]?.id ?? null;
      });
    } catch (error) {
      console.warn('E-Mail-Vorlagen konnten nicht geladen werden', error);
    }
  }, []);

  const fetchMeta = useCallback(async () => {
    try {
      const [doktypRes, plRes] = await Promise.all([
        fetch('/api/email-textvorlagen/dokumenttypen'),
        fetch('/api/email-textvorlagen/placeholders')
      ]);
      if (doktypRes.ok) {
        const data = await doktypRes.json();
        if (Array.isArray(data)) setDokumenttypOptions(data);
      }
      if (plRes.ok) {
        const data = await plRes.json();
        if (Array.isArray(data)) setPlaceholders(data);
      }
    } catch (error) {
      console.warn('Metadaten konnten nicht geladen werden', error);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchTemplates();
    fetchMeta();
  }, [fetchTemplates, fetchMeta]);

  const saveTemplate = async (template: EmailTemplate) => {
    if (!template.name.trim()) {
      toast.warning('Bitte einen Anzeigenamen eingeben.');
      return;
    }
    if (!template.dokumentTyp) {
      toast.warning('Bitte einen Dokumenttyp wählen.');
      return;
    }
    if (!template.subjectTemplate.trim()) {
      toast.warning('Bitte einen Betreff angeben.');
      return;
    }

    const payload = {
      dokumentTyp: template.dokumentTyp,
      name: template.name.trim(),
      subjectTemplate: template.subjectTemplate,
      htmlBody: template.htmlBody,
      aktiv: template.aktiv !== false
    };

    const isUpdate = Boolean(template.id);
    const url = isUpdate
      ? `/api/email-textvorlagen/${template.id}`
      : '/api/email-textvorlagen';
    const method = isUpdate ? 'PUT' : 'POST';

    try {
      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        if (response.status === 409 || response.status === 500) {
          toast.error(
            'Speichern fehlgeschlagen. Möglicherweise existiert bereits eine Vorlage für diesen Dokumenttyp.'
          );
        } else {
          toast.error('Die Vorlage konnte nicht gespeichert werden.');
        }
        return;
      }
      const saved = await response.json().catch(() => null);
      setEditing(null);
      await fetchTemplates();
      if (saved?.id) setSelectedId(saved.id);
      toast.success('Vorlage gespeichert.');
    } catch (error) {
      console.warn('Speichern fehlgeschlagen', error);
      toast.error('Speichern fehlgeschlagen.');
    }
  };

  const deleteTemplate = async (template: EmailTemplate) => {
    if (!template.id) return;
    if (!window.confirm(`Vorlage "${template.name}" wirklich löschen?`)) return;
    try {
      const response = await fetch(
        `/api/email-textvorlagen/${encodeURIComponent(String(template.id))}`,
        { method: 'DELETE' }
      );
      if (!response.ok) {
        toast.error('Die Vorlage konnte nicht gelöscht werden.');
        return;
      }
      if (selectedId === template.id) setSelectedId(null);
      await fetchTemplates();
      toast.success('Vorlage gelöscht.');
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

  const activeTemplate = useMemo(
    () => templates.find((tpl) => String(tpl.id) === String(selectedId)) || null,
    [templates, selectedId]
  );

  const previewHtml = useMemo(
    () => (activeTemplate ? renderPreview(activeTemplate.htmlBody, useSampleData) : ''),
    [activeTemplate, useSampleData]
  );

  const filteredTemplates = useMemo(() => {
    if (!searchQuery.trim()) return templates;
    const q = searchQuery.toLowerCase();
    return templates.filter((tpl) => {
      const label = dokumenttypLabel(tpl.dokumentTyp).toLowerCase();
      return (
        tpl.name.toLowerCase().includes(q) ||
        label.includes(q) ||
        stripHtml(tpl.htmlBody).toLowerCase().includes(q)
      );
    });
  }, [templates, searchQuery, dokumenttypLabel]);

  const usedDokumentTypen = useMemo(
    () => new Set(templates.map((tpl) => tpl.dokumentTyp)),
    [templates]
  );

  const availableDokumentTypenForNew = dokumenttypOptions.filter(
    (option) => !usedDokumentTypen.has(option.value)
  );

  const startNewTemplate = () => {
    const firstFree = availableDokumentTypenForNew[0]?.value || dokumenttypOptions[0]?.value || 'RECHNUNG';
    setEditing(emptyTemplate(firstFree));
    setSelectedId(null);
  };

  return (
    <PageLayout
      ribbonCategory="Kommunikation"
      title="E-MAIL-TEXTVORLAGEN"
      subtitle="Verwalten Sie die E-Mail-Texte, die beim Versand von Rechnungen, Aufträgen und Anfragen verwendet werden."
      actions={
        <Button
          size="sm"
          className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
          onClick={startNewTemplate}
          disabled={availableDokumentTypenForNew.length === 0}
          title={
            availableDokumentTypenForNew.length === 0
              ? 'Für jeden Dokumenttyp existiert bereits eine Vorlage.'
              : undefined
          }
        >
          <Plus className="w-4 h-4" /> Neue Vorlage
        </Button>
      }
    >
      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] xl:grid-cols-[320px_1fr] gap-6">
        {/* Sidebar */}
        <div className="space-y-3">
          <Card className="p-4 border-rose-100 shadow-lg">
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

            {filteredTemplates.length === 0 ? (
              <div className="py-8 text-center">
                <FileText className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                <p className="text-sm text-slate-400">
                  {templates.length === 0 ? 'Noch keine Vorlagen.' : 'Keine Treffer.'}
                </p>
              </div>
            ) : (
              <div className="space-y-1.5 max-h-[calc(100vh-340px)] overflow-y-auto pr-1">
                {filteredTemplates.map((template) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    active={String(activeTemplate?.id) === String(template.id) && !editing}
                    dokumenttypLabel={dokumenttypLabel(template.dokumentTyp)}
                    onSelect={() => {
                      setSelectedId(template.id ?? null);
                      setEditing(null);
                    }}
                    onEdit={() => setEditing({ ...template })}
                    onDelete={() => deleteTemplate(template)}
                  />
                ))}
              </div>
            )}
          </Card>

          {/* Hint Card */}
          <Card className="p-4 border-slate-100 bg-slate-50/50 shadow-sm">
            <div className="flex gap-2">
              <RotateCcw className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-slate-600 space-y-1">
                <p className="font-medium text-slate-700">Wie funktioniert das?</p>
                <p>
                  Pro Dokumenttyp (Rechnung, Anfrage, Auftrag, ...) können Sie genau eine
                  E-Mail-Vorlage hinterlegen. Beim Versand werden Platzhalter wie{' '}
                  <code className="px-1 bg-white rounded text-rose-700">{'{{KUNDENNAME}}'}</code>{' '}
                  automatisch durch die echten Daten ersetzt.
                </p>
              </div>
            </div>
          </Card>
        </div>

        {/* Main */}
        <div>
          {editing ? (
            <TemplateEditorPanel
              template={editing}
              onChange={(tpl) => setEditing(tpl)}
              onSave={saveTemplate}
              onCancel={() => setEditing(null)}
              dokumenttypOptions={
                editing.id
                  ? dokumenttypOptions
                  : [
                      ...availableDokumentTypenForNew,
                      ...(editing.dokumentTyp &&
                      !availableDokumentTypenForNew.some((d) => d.value === editing.dokumentTyp)
                        ? [
                            {
                              value: editing.dokumentTyp,
                              label: dokumenttypLabel(editing.dokumentTyp)
                            }
                          ]
                        : [])
                    ]
              }
              placeholders={placeholders}
              isNew={!editing.id}
            />
          ) : activeTemplate ? (
            <TemplateView
              template={activeTemplate}
              dokumenttypLabel={dokumenttypLabel(activeTemplate.dokumentTyp)}
              preview={previewHtml}
              rawPreview={previewHtml}
              useSampleData={useSampleData}
              onToggleSample={() => setUseSampleData((v) => !v)}
              copied={copied}
              onCopy={handleCopy}
              onEdit={() => setEditing({ ...activeTemplate })}
              onDelete={() => deleteTemplate(activeTemplate)}
            />
          ) : (
            <Card className="p-16 text-center border-dashed border-slate-200 shadow-inner">
              <Mail className="w-12 h-12 text-slate-200 mx-auto mb-3" />
              <p className="text-slate-500">
                Wählen Sie eine Vorlage aus oder erstellen Sie eine neue.
              </p>
            </Card>
          )}
        </div>
      </div>
    </PageLayout>
  );
}
