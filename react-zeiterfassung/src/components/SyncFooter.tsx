import { RefreshCw, Loader2 } from 'lucide-react'

interface SyncFooterProps {
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

export default function SyncFooter({ syncStatus, onSync }: SyncFooterProps) {
    if (!onSync) return null;

    return (
        <footer className="bg-white border-t border-slate-200 px-4 py-3 safe-area-bottom mt-auto">
            <div className="flex items-center justify-center">
                {syncStatus === 'syncing' ? (
                    <div className="flex items-center gap-2 text-slate-500">
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span className="text-sm">Synchronisiere...</span>
                    </div>
                ) : syncStatus === 'error' ? (
                    <button
                        onClick={onSync}
                        className="flex items-center gap-2 text-amber-600 hover:text-amber-700"
                    >
                        <RefreshCw className="w-4 h-4" />
                        <span className="text-sm">Offline - Tippen zum Sync</span>
                    </button>
                ) : (
                    <button
                        onClick={onSync}
                        className="flex items-center gap-2 text-slate-500 hover:text-rose-600 transition-colors"
                    >
                        <RefreshCw className="w-4 h-4" />
                        <span className="text-sm">Daten aktualisieren</span>
                    </button>
                )}
            </div>
        </footer>
    )
}
