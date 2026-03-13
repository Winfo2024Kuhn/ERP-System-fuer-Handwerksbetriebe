import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Check,
  Copy,
  FileText,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Trash2,
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
  { token: '{{Kundenname}}', label: 'Kundenname' },
  { token: '{{Ansprechpartner}}', label: 'Ansprechpartner' },
  { token: '{{Zahlungsziel}}', label: 'Zahlungsziel' }
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

function replacePlaceholders(html: string, kunde: Kunde | null, days: number) {
  const replacements: Record<string, string | null> = {
    '{{Anrede}}': formatAnrede(kunde?.anrede) || null,
    '{{Kundenname}}': kunde?.name?.trim() || null,
    '{{Ansprechpartner}}': kunde?.ansprechpartner?.trim() || kunde?.ansprechspartner?.trim() || null,
    '{{ANSPRECHPARTNER}}': kunde?.ansprechpartner?.trim() || kunde?.ansprechspartner?.trim() || null,
    '{{Zahlungsziel}}': formatDate(addDays(new Date(), days)) || null
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

  return (
    <Card
      className={cn(
        'p-4 flex items-start gap-3 cursor-pointer transition-all hover:shadow-md',
        active ? 'ring-2 ring-rose-500 bg-rose-50' : 'border-slate-200'
      )}
      onClick={onSelect}
    >
      <div className="flex-shrink-0 mt-0.5 text-rose-600">
        <FileText className="w-5 h-5" />
      </div>
      <div className="flex-1 min-w-0">
        <h3 className="text-slate-900 truncate">{template.name || 'Unbenannt'}</h3>
        <p className="text-sm text-slate-500 line-clamp-2 mt-1">{preview || 'Keine Vorschau verfügbar.'}</p>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        <Button variant="ghost" size="sm" onClick={(event) => { event.stopPropagation(); onEdit(); }}>
          <Pencil className="w-4 h-4" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="text-red-600 hover:text-red-700"
          onClick={(event) => {
            event.stopPropagation();
            onDelete();
          }}
        >
          <Trash2 className="w-4 h-4" />
        </Button>
      </div>
    </Card>
  );
}

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
  const editorRef = useRef<any>(null);

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
    <Card className="p-6 space-y-4">
      <div>
        <h2 className="text-slate-900 mb-1">Vorlage bearbeiten</h2>
        <p className="text-slate-600 text-sm">Formatieren Sie den Inhalt, fügen Sie Bilder ein und nutzen Sie die festen Platzhalter.</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="template-name">Vorlagen-Name</Label>
        <Input
          id="template-name"
          value={template.name}
          onChange={(event) => onChange({ ...template, name: event.target.value })}
          placeholder="z.B. Rechnung"
        />
      </div>

      <div className="space-y-2">
        <Label>Dokumenttypen (optional)</Label>
        <div className="flex flex-wrap gap-2">
          {(template.docTypes || []).map((type) => (
            <span key={type} className="inline-flex items-center gap-1 rounded-full bg-rose-50 text-rose-700 px-3 py-1 text-sm">
              {type}
              <button type="button" onClick={() => removeDocType(type)} className="text-rose-700 hover:text-rose-900">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
          {(template.docTypes || []).length === 0 && <span className="text-sm text-slate-500">Noch keine Zuordnung.</span>}
        </div>
        {availableSuggestions.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {availableSuggestions.map((suggestion) => (
              <Button key={suggestion} variant="ghost" size="sm" onClick={() => addDocType(suggestion)}>
                {suggestion}
              </Button>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-2">
        <Label>Inhalt</Label>
        {/* Placeholder buttons bar */}
        <div className="bg-rose-50 border border-rose-100 rounded-lg px-3 py-2 flex flex-wrap items-center gap-2">
          <span className="text-sm text-rose-700 font-medium">Platzhalter:</span>
          {PLACEHOLDERS.map((placeholder) => (
            <Button
              key={placeholder.token}
              variant="outline"
              size="sm"
              className="bg-white border-rose-200 text-rose-700 hover:bg-rose-100"
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
        <TiptapEditor
          value={template.content}
          onChange={(html) => onChange({ ...template, content: html })}
          onEditorReady={(editor) => { editorRef.current = editor; }}
        />
        <p className="text-sm text-slate-500 mt-2">Tipp: Bilder per Drag & Drop oder Einfügen (Ctrl+V) hinzufügen. Klicken Sie auf ein Bild, um es zu bearbeiten.</p>
      </div>

      <div className="flex gap-3 pt-4">
        <Button className="flex-1" onClick={() => onSave(template)}>
          <Save className="w-4 h-4" /> Speichern
        </Button>
        <Button variant="outline" onClick={onCancel}>
          <X className="w-4 h-4" /> Abbrechen
        </Button>
      </div>
    </Card>
  );
}

function TemplateFiller({
  template,
  kunden,
  selectedKunde,
  onSelectKunde,
  onReset,
  preview,
  copied,
  onCopy
}: {
  template: TextTemplate;
  kunden: Kunde[];
  selectedKunde: string | number | null;
  onSelectKunde: (id: string | number) => void;
  onReset: () => void;
  preview: string;
  copied: 'text' | 'html' | null;
  onCopy: (type: 'text' | 'html', content: string) => Promise<void>;
}) {
  const activeKunde = useMemo(() => kunden.find((kunde) => kunde.id === selectedKunde) || null, [kunden, selectedKunde]);
  const tage = activeKunde?.zahlungsziel ?? DEFAULT_PAYMENT_DAYS;

  return (
    <>
      <Card className="p-6 space-y-4">
        <div>
          <h2 className="text-slate-900">{template.name}</h2>
          <p className="text-slate-600 text-sm mb-4">Füllen Sie die Platzhalter mit Kundendaten und Zahlungsziel.</p>
        </div>

        <div className="space-y-4">
          <div>
            <Label htmlFor="kunde-select">Kunde auswählen</Label>
            <Select
              value={String(selectedKunde || '')}
              onChange={(value) => onSelectKunde(value)}
              options={kunden.map((kunde) => ({
                value: String(kunde.id),
                label: `${kunde.name}${(kunde.ansprechpartner || kunde.ansprechspartner) ? ` (${kunde.ansprechpartner || kunde.ansprechspartner})` : ''}`
              }))}
              placeholder="Kunde auswählen"
              className="mt-1.5"
            />
            <p className="text-sm text-slate-500 mt-1">Daten werden später aus der Datenbank geladen.</p>
          </div>

          <div className="grid grid-cols-2 gap-4 bg-slate-50 p-4 rounded-lg border border-slate-200">
            <div>
              <p className="text-sm text-slate-600">Kunde</p>
              <p className="text-slate-900">{activeKunde?.name || '-'}</p>
            </div>
            <div>
              <p className="text-sm text-slate-600">Anrede</p>
              <p className="text-slate-900">{formatAnrede(activeKunde?.anrede) || '-'}</p>
            </div>
            <div>
              <p className="text-sm text-slate-600">Ansprechpartner</p>
              <p className="text-slate-900">{activeKunde?.ansprechpartner || activeKunde?.ansprechspartner || '-'}</p>
            </div>
            <div>
              <p className="text-sm text-slate-600">Zahlungsziel</p>
              <p className="text-slate-900">{tage} Tage ({formatDate(addDays(new Date(), tage))})</p>
            </div>
          </div>

          <Button variant="outline" size="sm" onClick={onReset}>
            <RotateCcw className="w-4 h-4" /> Zurücksetzen
          </Button>
        </div>
      </Card>

      <Card className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-slate-900">Vorschau</h3>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => onCopy('text', stripHtml(preview || ''))}>
              {copied === 'text' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              {copied === 'text' ? 'Kopiert!' : 'Text kopieren'}
            </Button>
            <Button variant="outline" size="sm" onClick={() => onCopy('html', preview || '')}>
              {copied === 'html' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              {copied === 'html' ? 'Kopiert!' : 'HTML kopieren'}
            </Button>
          </div>
        </div>

        <div
          className="min-h-[400px] p-6 border border-slate-200 rounded-lg bg-white prose prose-slate max-w-none"
          dangerouslySetInnerHTML={{ __html: preview || '' }}
        />
      </Card>
    </>
  );
}

export default function TextbausteinEditor() {
    const toast = useToast();
  const [templates, setTemplates] = useState<TextTemplate[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState<TextTemplate | null>(null);
  const [kunden, setKunden] = useState<Kunde[]>([]);
  const [selectedKunde, setSelectedKunde] = useState<string | number | null>(null);

  const [copied, setCopied] = useState<'text' | 'html' | null>(null);
  const [docTypeOptions, setDocTypeOptions] = useState<string[]>([]);

  const fetchTemplates = useCallback(async () => {
    try {
      const response = await fetch('/api/textbausteine');
      const data = response.ok ? await response.json() : [];
      const mapped: TextTemplate[] = Array.isArray(data)
        ? data.map((template: any) => ({
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
        ? data.kunden.map((kunde: any) => ({
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

  return (
    <PageLayout
      ribbonCategory="Vorlagen & Stammdaten"
      title="TEXTVORLAGEN"
      subtitle="Verwaltung und Bearbeitung von Textbausteinen für Formulare."
      actions={
        <div className="flex gap-2">
          {!editing && (
            <Button size="sm" onClick={() => { setEditing({ ...EMPTY_TEMPLATE }); setSelectedId(null); }} className="bg-rose-600 text-white hover:bg-rose-700">
              <Plus className="w-4 h-4 mr-2" />
              Neue Vorlage
            </Button>
          )}
        </div>
      }
    >
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <div className="lg:col-span-1">
          <div className="flex items-center justify-between mb-4 lg:hidden">
            <h2 className="text-slate-900">Vorlagen</h2>
            <Button size="sm" onClick={() => { setEditing({ ...EMPTY_TEMPLATE }); setSelectedId(null); }}>
              <Plus className="w-4 h-4" /> Neu
            </Button>
          </div>

          {templates.length === 0 ? (
            <Card className="p-12 text-center text-slate-500 flex flex-col items-center justify-center gap-3">
              <FileText className="w-12 h-12 text-slate-300" />
              <p>Keine Vorlagen vorhanden.</p>
            </Card>
          ) : (
            <div className="space-y-2">
              {templates.map((template) => (
                <TemplateCard
                  key={template.id}
                  template={template}
                  active={activeTemplate?.id === template.id}
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
        </div>

        <div className="lg:col-span-3 space-y-6">
          {editing ? (
            <TemplateEditor
              template={editing}
              onChange={(tpl) => setEditing(tpl)}
              onSave={saveTemplate}
              onCancel={() => setEditing(null)}
              docTypeOptions={docTypeOptions}
            />
          ) : activeTemplate ? (
            <TemplateFiller
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
            />
          ) : (
            <Card className="p-12 text-center text-slate-500">
              Wählen Sie eine Vorlage aus oder erstellen Sie eine neue Vorlage.
            </Card>
          )}
        </div>
      </div>
    </PageLayout>
  );
}
