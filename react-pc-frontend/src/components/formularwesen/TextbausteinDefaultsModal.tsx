import { X, Plus, ArrowUp, ArrowDown, Trash2, FileText, Save, AlertCircle } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Button } from '../ui/button';
import { TextbausteinPickerModal, type TextbausteinPickerItem } from '../textbaustein/TextbausteinPickerModal';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
import type { AusgangsGeschaeftsDokumentTyp } from '../../types';

/** Backend-DTO: pro Dokumenttyp je eine Liste Vortext-IDs und Nachtext-IDs. */
interface DefaultsEntryDto {
    dokumenttyp: string;
    vortextIds: number[];
    nachtextIds: number[];
}
interface DefaultsDto {
    entries: DefaultsEntryDto[];
}

interface Props {
    templateName: string | null;
    /** Dokumenttypen, denen die Vorlage zugeordnet ist (Labels). */
    assignedDokumenttypen: string[];
    onClose: () => void;
    onSaved?: () => void;
}

/** Findet den enum-Wert (Value) zu einem Label. */
function valueFromLabel(label: string): AusgangsGeschaeftsDokumentTyp | undefined {
    return AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.label === label || t.value === label)?.value;
}

/** Modal zur Konfiguration der Default-Textbausteine pro Dokumenttyp einer Vorlage. */
export default function TextbausteinDefaultsModal({
    templateName,
    assignedDokumenttypen,
    onClose,
    onSaved,
}: Props) {
    const [allTextbausteine, setAllTextbausteine] = useState<TextbausteinPickerItem[]>([]);
    /** Map dokumenttyp-label -> { vor: number[], nach: number[] } */
    const [defaults, setDefaults] = useState<Map<string, { vor: number[]; nach: number[] }>>(new Map());
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    /** Picker-State: welcher Dokumenttyp und welche Position bekommen den naechsten Baustein? */
    const [pickerTarget, setPickerTarget] = useState<{ dokumenttyp: string; position: 'VOR' | 'NACH' } | null>(null);

    const tbById = useMemo(() => {
        const map = new Map<number, TextbausteinPickerItem>();
        allTextbausteine.forEach(tb => map.set(tb.id, tb));
        return map;
    }, [allTextbausteine]);

    // Initial laden: Textbausteine + bestehende Defaults
    useEffect(() => {
        let aborted = false;
        async function load() {
            setLoading(true);
            setError(null);
            try {
                const [tbRes, dfRes] = await Promise.all([
                    fetch('/api/textbausteine'),
                    templateName
                        ? fetch(`/api/formulare/templates/${encodeURIComponent(templateName)}/textbaustein-defaults`)
                        : Promise.resolve(null),
                ]);
                if (aborted) return;
                if (tbRes.ok) {
                    const data = await tbRes.json();
                    if (Array.isArray(data)) setAllTextbausteine(data);
                }
                if (dfRes && dfRes.ok) {
                    const data: DefaultsDto = await dfRes.json();
                    const map = new Map<string, { vor: number[]; nach: number[] }>();
                    (data.entries || []).forEach(e => {
                        map.set(e.dokumenttyp, {
                            vor: [...(e.vortextIds || [])],
                            nach: [...(e.nachtextIds || [])],
                        });
                    });
                    setDefaults(map);
                }
            } catch {
                if (!aborted) setError('Konfiguration konnte nicht geladen werden.');
            } finally {
                if (!aborted) setLoading(false);
            }
        }
        load();
        return () => { aborted = true; };
    }, [templateName]);

    function getList(dokumenttyp: string, position: 'VOR' | 'NACH'): number[] {
        const entry = defaults.get(dokumenttyp);
        return entry ? (position === 'VOR' ? entry.vor : entry.nach) : [];
    }

    function setList(dokumenttyp: string, position: 'VOR' | 'NACH', ids: number[]) {
        setDefaults(prev => {
            const next = new Map(prev);
            const entry = next.get(dokumenttyp) || { vor: [], nach: [] };
            const updated = position === 'VOR'
                ? { ...entry, vor: ids }
                : { ...entry, nach: ids };
            next.set(dokumenttyp, updated);
            return next;
        });
    }

    function removeAt(dokumenttyp: string, position: 'VOR' | 'NACH', index: number) {
        const list = getList(dokumenttyp, position);
        setList(dokumenttyp, position, list.filter((_, i) => i !== index));
    }

    function moveItem(dokumenttyp: string, position: 'VOR' | 'NACH', index: number, direction: -1 | 1) {
        const list = getList(dokumenttyp, position);
        const target = index + direction;
        if (target < 0 || target >= list.length) return;
        const next = [...list];
        [next[index], next[target]] = [next[target], next[index]];
        setList(dokumenttyp, position, next);
    }

    function addBaustein(tb: TextbausteinPickerItem) {
        if (!pickerTarget) return;
        const list = getList(pickerTarget.dokumenttyp, pickerTarget.position);
        if (!list.includes(tb.id)) {
            setList(pickerTarget.dokumenttyp, pickerTarget.position, [...list, tb.id]);
        }
        setPickerTarget(null);
    }

    async function handleSave() {
        if (!templateName) return;
        setSaving(true);
        setError(null);
        try {
            const entries: DefaultsEntryDto[] = [];
            defaults.forEach((value, dokumenttyp) => {
                if (value.vor.length === 0 && value.nach.length === 0) return;
                entries.push({
                    dokumenttyp,
                    vortextIds: value.vor,
                    nachtextIds: value.nach,
                });
            });
            const res = await fetch(
                `/api/formulare/templates/${encodeURIComponent(templateName)}/textbaustein-defaults`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ entries }),
                },
            );
            if (!res.ok) throw new Error();
            onSaved?.();
            onClose();
        } catch {
            setError('Speichern fehlgeschlagen.');
        } finally {
            setSaving(false);
        }
    }

    return (
        <>
            <div className="fixed inset-0 z-[55] bg-black/40 backdrop-blur-sm flex items-center justify-center" onClick={onClose}>
                <div
                    className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl mx-4 border border-slate-100 flex flex-col max-h-[88vh] animate-in fade-in zoom-in-95 duration-200"
                    onClick={e => e.stopPropagation()}
                >
                    {/* Header */}
                    <div className="flex items-start justify-between gap-4 px-6 py-5 border-b border-slate-100 flex-shrink-0">
                        <div>
                            <p className="text-xs font-semibold text-rose-600 uppercase tracking-wide">Vorlage</p>
                            <h2 className="text-xl font-bold text-slate-900 mt-0.5">Standard-Texte je Dokumenttyp</h2>
                            <p className="text-sm text-slate-500 mt-1">
                                Diese Texte werden beim Anlegen oder Umwandeln eines Dokuments automatisch
                                vor und nach den Leistungen eingefügt.
                            </p>
                        </div>
                        <button
                            type="button"
                            aria-label="Schließen"
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                        >
                            <X className="w-4 h-4 text-slate-400" />
                        </button>
                    </div>

                    {/* Body */}
                    <div className="flex-1 overflow-y-auto px-6 py-5 min-h-0">
                        {loading ? (
                            <div className="flex items-center justify-center py-16">
                                <div className="w-7 h-7 border-2 border-rose-600 border-t-transparent rounded-full animate-spin" />
                            </div>
                        ) : !templateName ? (
                            <div className="flex items-start gap-3 p-4 rounded-xl bg-amber-50 border border-amber-200 text-sm text-amber-800">
                                <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                <p>Bitte zuerst die Vorlage speichern. Standard-Texte können nur für gespeicherte Vorlagen festgelegt werden.</p>
                            </div>
                        ) : assignedDokumenttypen.length === 0 ? (
                            <div className="flex items-start gap-3 p-4 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-600">
                                <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                <p>Diese Vorlage ist noch keinem Dokumenttyp zugeordnet. Wähle in der rechten Seitenleiste mindestens einen Dokumenttyp aus, um Standard-Texte zu hinterlegen.</p>
                            </div>
                        ) : (
                            <div className="space-y-4">
                                {assignedDokumenttypen.map(dokTypLabel => {
                                    const docValue = valueFromLabel(dokTypLabel);
                                    return (
                                        <div key={dokTypLabel} className="border border-slate-200 rounded-2xl p-4 bg-white">
                                            <div className="flex items-center gap-2 mb-3">
                                                <div className="p-1.5 bg-rose-50 rounded-lg">
                                                    <FileText className="w-3.5 h-3.5 text-rose-600" />
                                                </div>
                                                <h3 className="text-sm font-bold text-slate-900">{dokTypLabel}</h3>
                                            </div>

                                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                <Column
                                                    label="Vor den Leistungen"
                                                    description="Begrüßung, Bauvorhaben, Einleitung"
                                                    ids={getList(dokTypLabel, 'VOR')}
                                                    tbById={tbById}
                                                    onAdd={() => setPickerTarget({ dokumenttyp: dokTypLabel, position: 'VOR' })}
                                                    onRemove={(idx) => removeAt(dokTypLabel, 'VOR', idx)}
                                                    onMove={(idx, dir) => moveItem(dokTypLabel, 'VOR', idx, dir)}
                                                />
                                                <Column
                                                    label="Nach den Leistungen"
                                                    description="Schlussformel, Zahlungsziel, Hinweise"
                                                    ids={getList(dokTypLabel, 'NACH')}
                                                    tbById={tbById}
                                                    onAdd={() => setPickerTarget({ dokumenttyp: dokTypLabel, position: 'NACH' })}
                                                    onRemove={(idx) => removeAt(dokTypLabel, 'NACH', idx)}
                                                    onMove={(idx, dir) => moveItem(dokTypLabel, 'NACH', idx, dir)}
                                                />
                                            </div>

                                            {!docValue && (
                                                <p className="text-xs text-amber-700 mt-3">
                                                    Hinweis: Dieser Dokumenttyp ist nicht im DocumentEditor verfügbar.
                                                    Standard-Texte werden nur bei Ausgangsdokumenten automatisch eingefügt.
                                                </p>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}

                        {error && (
                            <div className="mt-4 flex items-start gap-2 p-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
                                <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                <p>{error}</p>
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-slate-100 flex-shrink-0">
                        <Button variant="outline" size="sm" onClick={onClose} disabled={saving}>
                            Abbrechen
                        </Button>
                        <Button
                            size="sm"
                            onClick={handleSave}
                            disabled={saving || loading || !templateName}
                        >
                            <Save className="w-3.5 h-3.5 mr-1" />
                            {saving ? 'Speichert…' : 'Speichern'}
                        </Button>
                    </div>
                </div>
            </div>

            {pickerTarget && (
                <TextbausteinPickerModal
                    textbausteine={allTextbausteine}
                    onSelect={addBaustein}
                    onClose={() => setPickerTarget(null)}
                    dokumentTyp={valueFromLabel(pickerTarget.dokumenttyp)}
                    title={pickerTarget.position === 'VOR' ? 'Vor-Text auswählen' : 'Nach-Text auswählen'}
                    hideIds={new Set(getList(pickerTarget.dokumenttyp, pickerTarget.position))}
                />
            )}
        </>
    );
}

// ─── Spalte (Vor- bzw. Nach-Liste) ────────────────────────────────────────

function Column({
    label,
    description,
    ids,
    tbById,
    onAdd,
    onRemove,
    onMove,
}: {
    label: string;
    description: string;
    ids: number[];
    tbById: Map<number, TextbausteinPickerItem>;
    onAdd: () => void;
    onRemove: (index: number) => void;
    onMove: (index: number, direction: -1 | 1) => void;
}) {
    return (
        <div className="bg-slate-50 rounded-xl p-3 border border-slate-150">
            <div className="mb-2">
                <p className="text-xs font-semibold text-slate-700">{label}</p>
                <p className="text-[11px] text-slate-400">{description}</p>
            </div>

            {ids.length > 0 ? (
                <div className="space-y-1.5 mb-2">
                    {ids.map((id, idx) => {
                        const tb = tbById.get(id);
                        return (
                            <div
                                key={`${id}-${idx}`}
                                className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5"
                            >
                                <div className="flex flex-col">
                                    <button
                                        type="button"
                                        aria-label="Nach oben"
                                        onClick={() => onMove(idx, -1)}
                                        disabled={idx === 0}
                                        className="p-0.5 text-slate-300 hover:text-rose-600 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                    >
                                        <ArrowUp className="w-3 h-3" />
                                    </button>
                                    <button
                                        type="button"
                                        aria-label="Nach unten"
                                        onClick={() => onMove(idx, 1)}
                                        disabled={idx === ids.length - 1}
                                        className="p-0.5 text-slate-300 hover:text-rose-600 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                    >
                                        <ArrowDown className="w-3 h-3" />
                                    </button>
                                </div>
                                <span className="flex-1 min-w-0 text-xs text-slate-700 truncate">
                                    {tb ? tb.name : `Textbaustein #${id} (gelöscht)`}
                                </span>
                                <button
                                    type="button"
                                    aria-label="Entfernen"
                                    onClick={() => onRemove(idx)}
                                    className="p-1 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded cursor-pointer"
                                >
                                    <Trash2 className="w-3 h-3" />
                                </button>
                            </div>
                        );
                    })}
                </div>
            ) : (
                <p className="text-[11px] text-slate-400 italic mb-2">Noch keine Texte gewählt.</p>
            )}

            <button
                type="button"
                onClick={onAdd}
                className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-xs font-medium text-rose-700 bg-white border border-dashed border-rose-300 rounded-lg hover:bg-rose-50 hover:border-rose-400 transition-colors cursor-pointer"
            >
                <Plus className="w-3 h-3" />
                Text hinzufügen
            </button>
        </div>
    );
}
