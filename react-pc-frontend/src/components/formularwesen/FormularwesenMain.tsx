import { useState, useCallback, useEffect } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '../ui/button';
import { PageLayout } from '../layout/PageLayout';
import { useToast } from '../ui/toast';
import type { FormTemplate, FormTemplateListItem } from '../../types';
import TemplateGallery from './TemplateGallery';
import VorlagenEditor, { type OffeneVorlage } from './VorlagenEditor';
import { FesteTexteBereich } from './feste-texte/FesteTexteBereich';

type View = 'gallery' | 'editor';

/** Main Formularwesen page — Canva-like template management + editor */
export default function FormularwesenMain() {
    const [view, setView] = useState<View>('gallery');

    // Gallery state
    const [templates, setTemplates] = useState<FormTemplateListItem[]>([]);
    const [loadingTemplates, setLoadingTemplates] = useState(true);

    // Editor: welche Vorlage gerade offen ist (null = keine)
    const [offeneVorlage, setOffeneVorlage] = useState<OffeneVorlage | null>(null);
    const toast = useToast();

    // ─── Load Templates ──────────────────────────────────────────────────

    const loadTemplateList = useCallback(async () => {
        try {
            setLoadingTemplates(true);
            const res = await fetch('/api/formulare/templates');
            if (!res.ok) throw new Error();
            setTemplates(await res.json());
        } catch {
            toast.error('Vorlagen konnten nicht geladen werden.');
        } finally {
            setLoadingTemplates(false);
        }
    }, [toast]);

    useEffect(() => { loadTemplateList(); }, [loadTemplateList]);

    // ─── Template Operations ─────────────────────────────────────────────

    const openEditor = (vorlage: OffeneVorlage) => {
        setOffeneVorlage(vorlage);
        setView('editor');
    };

    const handleCreateNew = () => openEditor({ name: null, html: null, dokumenttypen: [] });

    const handleEditTemplate = async (name: string) => {
        try {
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`);
            if (!res.ok) throw new Error();
            const data: FormTemplate = await res.json();
            openEditor({ name: data.name || name, html: data.html ?? '', dokumenttypen: data.assignedDokumenttypen || [] });
        } catch {
            toast.error(`Vorlage „${name}“ konnte nicht geladen werden.`);
        }
    };

    const handleDuplicateTemplate = async (name: string) => {
        try {
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}/copy`, { method: 'POST' });
            if (!res.ok) throw new Error();
            toast.success(`Vorlage „${name}“ wurde dupliziert.`);
            loadTemplateList();
        } catch {
            // Rückfall: Vorlage laden und als Kopie speichern
            try {
                const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`);
                if (!res.ok) throw new Error();
                const data: FormTemplate = await res.json();
                const copyName = `${name} (Kopie)`;
                const saveRes = await fetch('/api/formulare/templates', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: copyName, html: data.html, assignedDokumenttypen: data.assignedDokumenttypen })
                });
                if (!saveRes.ok) throw new Error();
                toast.success(`Vorlage als „${copyName}“ dupliziert.`);
                loadTemplateList();
            } catch {
                toast.error('Vorlage konnte nicht dupliziert werden.');
            }
        }
    };

    const handleDeleteTemplate = async (name: string) => {
        try {
            const res = await fetch(`/api/formulare/templates/${encodeURIComponent(name)}`, { method: 'DELETE' });
            if (!res.ok) throw new Error();
            toast.success(`Vorlage „${name}“ wurde gelöscht.`);
            loadTemplateList();
        } catch {
            toast.error('Vorlage konnte nicht gelöscht werden.');
        }
    };

    const handleBack = () => {
        loadTemplateList();
        setOffeneVorlage(null);
        setView('gallery');
    };

    // ─── Render ──────────────────────────────────────────────────────────

    if (view === 'gallery') {
        return (
            <PageLayout
                ribbonCategory="Vorlagen-Center"
                title="FORMULARWESEN"
                subtitle="Erstellen und verwalten Sie Dokumentvorlagen für Anfragen, Rechnungen und mehr."
                actions={
                    <Button size="sm" onClick={handleCreateNew}>
                        <Plus className="w-4 h-4 mr-1" />Neue Vorlage
                    </Button>
                }
            >
                <FesteTexteBereich />
                <h2 className="mb-3 text-lg font-semibold text-slate-900">Briefvorlagen</h2>

                {loadingTemplates ? (
                    <div className="flex items-center justify-center py-24">
                        <div className="w-8 h-8 border-2 border-rose-600 border-t-transparent rounded-full animate-spin" />
                    </div>
                ) : (
                    <TemplateGallery
                        templates={templates}
                        onCreateNew={handleCreateNew}
                        onEdit={handleEditTemplate}
                        onDuplicate={handleDuplicateTemplate}
                        onDelete={handleDeleteTemplate}
                        onRefresh={loadTemplateList}
                    />
                )}
            </PageLayout>
        );
    }

    // ─── Editor View ─────────────────────────────────────────────────────

    if (!offeneVorlage) return null;
    return <VorlagenEditor key={offeneVorlage.name ?? 'neu'} vorlage={offeneVorlage} onZurueck={handleBack} />;
}
