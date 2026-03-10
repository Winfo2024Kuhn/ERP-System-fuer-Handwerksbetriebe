import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Search, Trash2, StickyNote, Calendar } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Card } from './ui/card';
import type { LieferantNotiz } from '../types';
import { useConfirm } from './ui/confirm-dialog';

interface LieferantNotizenTabProps {
    lieferantId: number;
    notizen: LieferantNotiz[];
    onNotizenChange?: (notizen: LieferantNotiz[]) => void;
}

export function LieferantNotizenTab({ lieferantId, notizen: initialNotizen, onNotizenChange }: LieferantNotizenTabProps) {
    const confirmDialog = useConfirm();
    const [notizen, setNotizen] = useState<LieferantNotiz[]>(initialNotizen || []);
    const [searchQuery, setSearchQuery] = useState('');
    const [isAdding, setIsAdding] = useState(false);
    const [newText, setNewText] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setNotizen(initialNotizen || []);
    }, [initialNotizen]);

    const loadNotizen = useCallback(async (query?: string) => {
        try {
            const params = new URLSearchParams();
            if (query) params.set('q', query);
            const res = await fetch(`/api/lieferanten/${lieferantId}/notizen?${params.toString()}`);
            if (res.ok) {
                const data = await res.json();
                setNotizen(data);
            }
        } catch (err) {
            console.error('Fehler beim Laden der Notizen:', err);
        }
    }, [lieferantId]);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        loadNotizen(searchQuery);
    };

    const handleCreate = async () => {
        if (!newText.trim()) return;
        setSaving(true);
        try {
            const res = await fetch(`/api/lieferanten/${lieferantId}/notizen`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: newText.trim() })
            });
            if (res.ok) {
                const created = await res.json();
                const updated = [created, ...notizen];
                setNotizen(updated);
                onNotizenChange?.(updated);
                setNewText('');
                setIsAdding(false);
            }
        } catch (err) {
            console.error('Fehler beim Erstellen der Notiz:', err);
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (notizId: number) => {
        if (!await confirmDialog({ title: 'Notiz löschen', message: 'Möchten Sie diese Notiz wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/lieferanten/${lieferantId}/notizen/${notizId}`, {
                method: 'DELETE'
            });
            if (res.ok) {
                const updated = notizen.filter(n => n.id !== notizId);
                setNotizen(updated);
                onNotizenChange?.(updated);
            }
        } catch (err) {
            console.error('Fehler beim Löschen der Notiz:', err);
        }
    };

    const formatDate = (dateStr: string) => {
        try {
            const date = new Date(dateStr);
            return date.toLocaleDateString('de-DE', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return dateStr;
        }
    };

    return (
        <div className="space-y-4">
            {/* Header with Search and Add */}
            <div className="flex items-center gap-3">
                <form onSubmit={handleSearch} className="flex-1 flex gap-2">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                        <Input
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            placeholder="Notizen durchsuchen..."
                            className="pl-9"
                        />
                    </div>
                    <Button type="submit" variant="outline" size="sm">
                        Suchen
                    </Button>
                </form>
                <Button
                    onClick={() => setIsAdding(true)}
                    size="sm"
                    className="bg-rose-600 hover:bg-rose-700 text-white"
                >
                    <Plus className="w-4 h-4 mr-1" />
                    Neue Notiz
                </Button>
            </div>

            {/* Add New Note Form */}
            {isAdding && (
                <Card className="p-4 border-rose-200 bg-rose-50/50">
                    <div className="space-y-3">
                        <label className="text-sm font-medium text-slate-700">Neue Notiz</label>
                        <textarea
                            value={newText}
                            onChange={e => setNewText(e.target.value)}
                            placeholder="Notiztext eingeben..."
                            rows={4}
                            className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 resize-none"
                            autoFocus
                        />
                        <div className="flex justify-end gap-2">
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => { setIsAdding(false); setNewText(''); }}
                            >
                                Abbrechen
                            </Button>
                            <Button
                                size="sm"
                                onClick={handleCreate}
                                disabled={!newText.trim() || saving}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {saving ? 'Speichern...' : 'Speichern'}
                            </Button>
                        </div>
                    </div>
                </Card>
            )}

            {/* Notes List */}
            {notizen.length === 0 ? (
                <div className="text-center py-12 text-slate-500">
                    <StickyNote className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                    <p className="text-sm">Keine Notizen vorhanden.</p>
                    <p className="text-xs text-slate-400 mt-1">
                        Klicken Sie auf "Neue Notiz" um eine anzulegen.
                    </p>
                </div>
            ) : (
                <div className="space-y-3">
                    {notizen.map(notiz => (
                        <Card key={notiz.id} className="p-4 group hover:shadow-md transition-shadow">
                            <div className="flex justify-between items-start gap-4">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 text-xs text-slate-500 mb-2">
                                        <Calendar className="w-3.5 h-3.5" />
                                        {formatDate(notiz.erstelltAm)}
                                    </div>
                                    <p className="text-sm text-slate-700 whitespace-pre-wrap break-words">
                                        {notiz.text}
                                    </p>
                                </div>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => handleDelete(notiz.id)}
                                    className="opacity-0 group-hover:opacity-100 transition-opacity text-slate-400 hover:text-red-600 hover:bg-red-50 p-1.5 h-auto"
                                    title="Notiz löschen"
                                >
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}
