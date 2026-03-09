import React, { useState, useMemo } from 'react';
import {
    Search,
    Clock,
    Mail,
    RefreshCw,
    Paperclip,
    ChevronRight,
    File,
    FileText,
    FileImage,
    FileSpreadsheet
} from 'lucide-react';
import { Input } from './ui/input';
import { DatePicker } from './ui/datepicker';
import { type Kommunikation } from '../types';

interface EmailHistoryProps {
    kommunikation: Kommunikation[];
}

const EmailHistory: React.FC<EmailHistoryProps> = ({ kommunikation }) => {
    const [searchTerm, setSearchTerm] = useState('');
    const [expandedEmailId, setExpandedEmailId] = useState<number | null>(null);
    const [emailBodies, setEmailBodies] = useState<Record<number, string>>({});
    const [loadingBody, setLoadingBody] = useState<number | null>(null);
    const [filterType, setFilterType] = useState<'ALL' | 'IN' | 'OUT'>('ALL');
    const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
    const [dateFilter, setDateFilter] = useState('');

    const filteredEmails = useMemo(() => {
        if (!kommunikation) return [];
        let result = [...kommunikation];

        // Filter by Type
        if (filterType !== 'ALL') {
            result = result.filter(e => e.direction === filterType);
        }

        // Filter by Search
        if (searchTerm.trim()) {
            const term = searchTerm.toLowerCase();
            result = result.filter(email =>
                email.subject?.toLowerCase().includes(term) ||
                (email.referenzName && email.referenzName.toLowerCase().includes(term)) ||
                (email.snippet && email.snippet.toLowerCase().includes(term)) ||
                (email.body && email.body.toLowerCase().includes(term)) ||
                email.absender?.toLowerCase().includes(term) ||
                email.empfaenger?.toLowerCase().includes(term)
            );
        }

        // Filter by Date
        if (dateFilter) {
            result = result.filter(email => email.zeitpunkt && email.zeitpunkt.startsWith(dateFilter));
        }

        // Sort
        result.sort((a, b) => {
            const dateA = new Date(a.zeitpunkt || 0).getTime();
            const dateB = new Date(b.zeitpunkt || 0).getTime();
            return sortOrder === 'desc' ? dateB - dateA : dateA - dateB;
        });

        return result;
    }, [kommunikation, searchTerm, filterType, sortOrder, dateFilter]);

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        try {
            const date = new Date(dateStr);
            return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' +
                date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
        } catch {
            return dateStr;
        }
    };

    const handleExpand = async (email: Kommunikation) => {
        if (expandedEmailId === email.id) {
            setExpandedEmailId(null);
            return;
        }

        setExpandedEmailId(email.id);

        if (!emailBodies[email.id]) {
            setLoadingBody(email.id);
            try {
                let url = '';
                if (email.referenzTyp === 'PROJEKT') {
                    url = `/api/projekte/${email.referenzId}/emails`;
                } else if (email.referenzTyp === 'ANGEBOT') {
                    url = `/api/emails/angebot/${email.referenzId}`;
                }

                if (url) {
                    const res = await fetch(url);
                    if (res.ok) {
                        const allEmails: any[] = await res.json();
                        const found = allEmails.find((e: any) => e.id === email.id);
                        if (found && found.body) {
                            setEmailBodies(prev => ({ ...prev, [email.id]: found.body }));
                        } else {
                            setEmailBodies(prev => ({ ...prev, [email.id]: email.snippet || '(Inhalt nicht verfügbar)' }));
                        }
                    } else {
                        setEmailBodies(prev => ({ ...prev, [email.id]: email.snippet || '(Konnte Inhalt nicht laden)' }));
                    }
                } else {
                    setEmailBodies(prev => ({ ...prev, [email.id]: email.snippet || '(Inhalt dauerhaft nicht verfügbar)' }));
                }
            } catch (err) {
                console.error('Failed to fetch full email body', err);
                setEmailBodies(prev => ({ ...prev, [email.id]: email.snippet || '(Fehler beim Laden)' }));
            } finally {
                setLoadingBody(null);
            }
        }
    };

    const renderAttachmentIcon = (filename: string) => {
        const ext = filename.split('.').pop()?.toLowerCase();
        switch (ext) {
            case 'pdf': return <FileText className="w-5 h-5 text-red-500" />;
            case 'jpg':
            case 'jpeg':
            case 'png':
            case 'gif': return <FileImage className="w-5 h-5 text-purple-500" />;
            case 'doc':
            case 'docx': return <FileText className="w-5 h-5 text-blue-500" />;
            case 'xls':
            case 'xlsx': return <FileSpreadsheet className="w-5 h-5 text-emerald-500" />;
            default: return <File className="w-5 h-5 text-slate-400" />;
        }
    };

    return (
        <div className="flex flex-col h-full gap-4">
            <div className="space-y-2 shrink-0">
                <div className="relative">
                    <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                    <Input
                        placeholder="Betreff, Name oder E-Mail suchen..."
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                        className="pl-9"
                    />
                </div>

                {/* Filter Chips */}
                <div className="flex items-center justify-between gap-2 overflow-x-auto pb-2">
                    <div className="flex gap-2">
                        {['ALL', 'IN', 'OUT'].map(type => (
                            <button
                                key={type}
                                onClick={() => setFilterType(type as any)}
                                className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${filterType === type
                                    ? 'bg-slate-900 text-white'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                    }`}
                            >
                                {type === 'ALL' ? 'Alle' : type === 'IN' ? 'Eingang' : 'Ausgang'}
                            </button>
                        ))}
                    </div>
                    <DatePicker
                        value={dateFilter}
                        onChange={setDateFilter}
                        placeholder="Datum filtern"
                        className="w-40"
                    />
                </div>

                <div className="flex items-center justify-between text-xs text-slate-500 px-1">
                    <button
                        onClick={() => setSortOrder(prev => prev === 'desc' ? 'asc' : 'desc')}
                        className="flex items-center gap-1 hover:text-slate-900"
                    >
                        <Clock className="w-3 h-3" />
                        {sortOrder === 'desc' ? 'Neueste zuerst' : 'Älteste zuerst'}
                    </button>
                    <span className="flex-1" />
                    <span className="text-sm text-slate-500">
                        {filteredEmails.length} Einträge
                    </span>
                </div>
            </div>

            {/* Email List */}
            {filteredEmails.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center bg-slate-50 rounded-xl p-6 text-center text-slate-500 border border-slate-100">
                    <Mail className="w-10 h-10 mb-2 text-slate-300" />
                    <p>Keine E-Mails gefunden.</p>
                </div>
            ) : (
                <div className="flex-1 min-h-0 overflow-y-auto space-y-2 pr-1">
                    {filteredEmails.map((email, idx) => (
                        <div
                            key={email.id || idx}
                            className="bg-slate-50 rounded-xl p-4 hover:bg-slate-100 transition-colors cursor-pointer border border-transparent hover:border-slate-200 relative group"
                            onClick={() => handleExpand(email)}
                        >
                            <div className="flex items-start justify-between gap-3">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1">
                                        <span className={`text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded font-medium ${email.direction === 'IN' ? 'bg-blue-50 text-blue-700 border border-blue-100' : 'bg-emerald-50 text-emerald-700 border border-emerald-100'
                                            }`}>
                                            {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
                                        </span>
                                        {email.referenzName && (
                                            <span className="text-xs text-slate-500 px-1.5 py-0.5 bg-white border border-slate-200 rounded">
                                                {email.referenzName}
                                            </span>
                                        )}
                                    </div>
                                    <p className="font-medium text-slate-900 truncate pr-4">
                                        {email.subject || '(Kein Betreff)'}
                                    </p>
                                    <p className="text-sm text-slate-500 truncate">
                                        {email.direction === 'IN' ? `Von: ${email.absender}` : `An: ${email.empfaenger}`}
                                    </p>
                                </div>
                                <div className="flex flex-col items-end gap-1">
                                    <div className="flex items-center gap-1.5 text-xs text-slate-400 whitespace-nowrap">
                                        <Clock className="w-3 h-3" />
                                        {formatDate(email.zeitpunkt)}
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {email.attachments && email.attachments.length > 0 && (
                                            <Paperclip className="w-3 h-3 text-slate-400" />
                                        )}
                                        <ChevronRight className={`w-3 h-3 text-slate-300 opacity-0 group-hover:opacity-100 transition-opacity ${expandedEmailId === email.id ? 'rotate-90 opacity-100' : ''}`} />
                                    </div>
                                </div>
                            </div>

                            {!expandedEmailId && email.snippet && (
                                <p className="mt-2 text-xs text-slate-400 line-clamp-2 pl-1 border-l-2 border-slate-200">
                                    {email.snippet.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().substring(0, 200)}
                                </p>
                            )}

                            {expandedEmailId === email.id && (
                                <div className="mt-3 pt-3 border-t border-slate-200 animate-in slide-in-from-top-1 duration-200" onClick={e => e.stopPropagation()}>
                                    {loadingBody === email.id ? (
                                        <p className="text-sm text-slate-400 flex items-center gap-2 py-4 justify-center">
                                            <RefreshCw className="w-4 h-4 animate-spin" /> Lade Inhalt...
                                        </p>
                                    ) : (
                                        <div
                                            className="text-sm text-slate-700 prose prose-sm max-w-none bg-white p-4 rounded-lg border border-slate-100 shadow-sm overflow-x-auto"
                                            dangerouslySetInnerHTML={{ __html: emailBodies[email.id] || email.snippet || '' }}
                                        />
                                    )}
                                    {email.attachments && email.attachments.length > 0 && (
                                        <div className="mt-4 border-t border-slate-100 pt-3">
                                            <p className="text-xs font-semibold text-slate-500 mb-2 uppercase tracking-wide">
                                                Anhänge ({email.attachments.length})
                                            </p>
                                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                                                {email.attachments.map((att: any) => (
                                                    <a
                                                        key={att.id}
                                                        href={att.url}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="flex flex-col items-center justify-center p-3 rounded-lg border border-slate-200 bg-slate-50 hover:bg-slate-100 hover:border-slate-300 transition-all text-center gap-2 group/att h-24"
                                                        onClick={e => e.stopPropagation()}
                                                    >
                                                        {renderAttachmentIcon(att.filename)}
                                                        <span className="text-xs text-slate-600 font-medium truncate w-full px-1 group-hover/att:text-slate-900">
                                                            {att.filename}
                                                        </span>
                                                    </a>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default EmailHistory;
