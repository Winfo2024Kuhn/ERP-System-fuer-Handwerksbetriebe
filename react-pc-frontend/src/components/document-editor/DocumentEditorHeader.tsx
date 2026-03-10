import { X, Save, Upload, Download, FileText, Wrench, Clock, Printer, Minus, FolderOpen, Percent, Mail } from 'lucide-react';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import type { AusgangsGeschaeftsDokument } from './types';

interface DocumentEditorHeaderProps {
    dokumentNummer: string;
    kontextInfo: string;
    isLocked: boolean;
    saving: boolean;
    saveSuccess: boolean;
    hasUnsavedChanges: boolean;
    previewLoading: boolean;
    dokument: AusgangsGeschaeftsDokument | null;
    emailLoading: boolean;
    onClose: () => void;
    onSave: () => void;
    onOpenTextbausteinPicker: () => void;
    onOpenLeistungPicker: () => void;
    onOpenStundensatzPicker: () => void;
    onAddSeparator: () => void;
    onAddSectionHeader: () => void;
    onOpenRabattDialog: () => void;
    onExport: () => void;
    onPrint: () => void;
    onSendEmail: () => void;
    onGaebImport: () => void;
    fileInputRef: React.RefObject<HTMLInputElement | null>;
    onFileChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

export function DocumentEditorHeader({
    dokumentNummer,
    kontextInfo,
    isLocked,
    saving,
    saveSuccess,
    hasUnsavedChanges,
    dokument,
    emailLoading,
    onClose,
    onSave,
    onOpenTextbausteinPicker,
    onOpenLeistungPicker,
    onOpenStundensatzPicker,
    onAddSeparator,
    onAddSectionHeader,
    onOpenRabattDialog,
    onExport,
    onPrint,
    onSendEmail,
    onGaebImport,
    fileInputRef,
    onFileChange,
}: DocumentEditorHeaderProps) {
    return (
        <div className="bg-white border-b border-slate-200 px-3 h-11 flex items-center justify-between gap-2 flex-shrink-0">
            {/* Left: close + doc info */}
            <div className="flex items-center gap-2 min-w-0">
                <button
                    onClick={onClose}
                    className="p-1.5 hover:bg-slate-100 rounded-md transition-colors flex-shrink-0"
                >
                    <X className="w-4 h-4 text-slate-400" />
                </button>
                <div className="h-4 w-px bg-slate-200 flex-shrink-0" />
                <h1 className="text-sm font-bold text-slate-800 truncate">
                    {dokumentNummer || 'Neues Dokument'}
                </h1>
                {/* Status badges */}
                {isLocked && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded-full text-[10px] font-semibold flex-shrink-0">
                        <svg className="w-2.5 h-2.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" /></svg>
                        Gebucht
                    </span>
                )}
                {dokument?.storniert && (
                    <span className="inline-flex items-center px-2 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full text-[10px] font-semibold flex-shrink-0">
                        Storniert
                    </span>
                )}
                {hasUnsavedChanges && !isLocked && (
                    <span className="flex items-center gap-1 text-[10px] text-slate-400 flex-shrink-0">
                        <span className="w-1.5 h-1.5 bg-amber-400 rounded-full animate-pulse" />
                        Ungespeichert
                    </span>
                )}
                <span className="text-[11px] text-slate-400 truncate hidden lg:block">
                    {kontextInfo}
                </span>
            </div>

            {/* Center: action buttons */}
            {!isLocked && (
                <div className="flex items-center gap-0.5 flex-shrink-0">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onOpenTextbausteinPicker}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <FileText className="w-3 h-3" />
                        Textbaustein
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onOpenLeistungPicker}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <Wrench className="w-3 h-3" />
                        Leistung
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onOpenStundensatzPicker}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <Clock className="w-3 h-3" />
                        Stundensätze
                    </Button>
                    <div className="w-px h-5 bg-slate-200 mx-0.5" />
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onAddSectionHeader}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <FolderOpen className="w-3 h-3" />
                        Bauabschnitt
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onAddSeparator}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <Minus className="w-3 h-3" />
                        Trennlinie
                    </Button>
                    <div className="w-px h-5 bg-slate-200 mx-0.5" />
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onOpenRabattDialog}
                        disabled={isLocked}
                        className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                    >
                        <Percent className="w-3 h-3" />
                        Rabatt
                    </Button>
                </div>
            )}

            {/* Right: actions */}
            <div className="flex items-center gap-1 flex-shrink-0">
                <input
                    type="file"
                    ref={fileInputRef}
                    onChange={onFileChange}
                    className="hidden"
                    accept=".xml,.x83,.gaeb"
                />
                {!isLocked && (
                    <>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={onGaebImport}
                            disabled={isLocked}
                            className="h-7 px-2 text-[11px] text-slate-500 gap-1 hover:text-slate-700"
                        >
                            <Upload className="w-3 h-3" />
                            GAEB
                        </Button>
                        <div className="w-px h-5 bg-slate-200" />
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={onSave}
                            disabled={saving || isLocked}
                            className={cn(
                                "h-7 px-2 text-[11px] gap-1 rounded-md transition-all duration-300",
                                saveSuccess
                                    ? "text-emerald-600 bg-emerald-50"
                                    : "text-slate-500 hover:text-slate-700"
                            )}
                        >
                            {saving ? (
                                <div className="w-3 h-3 border-2 border-slate-300 border-t-rose-500 rounded-full animate-spin" />
                            ) : saveSuccess ? (
                                <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                                </svg>
                            ) : (
                                <Save className="w-3 h-3" />
                            )}
                            {saveSuccess ? 'Gespeichert' : saving ? '…' : 'Speichern'}
                        </Button>
                    </>
                )}
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onPrint}
                    className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
                >
                    <Printer className="w-3 h-3" />
                    Drucken
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onSendEmail}
                    disabled={emailLoading}
                    className="h-7 px-2 text-[11px] gap-1 rounded-md text-slate-500 hover:text-rose-600"
                    title="Dokument per E-Mail senden"
                >
                    {emailLoading ? (
                        <div className="w-3 h-3 border-2 border-slate-300 border-t-rose-500 rounded-full animate-spin" />
                    ) : (
                        <Mail className="w-3 h-3" />
                    )}
                    E-Mail
                </Button>
                <Button
                    size="sm"
                    onClick={onExport}
                    className="h-7 px-3 text-[11px] bg-rose-600 hover:bg-rose-700 text-white gap-1 shadow-sm"
                >
                    <Download className="w-3 h-3" />
                    PDF
                </Button>
            </div>
        </div>
    );
}
