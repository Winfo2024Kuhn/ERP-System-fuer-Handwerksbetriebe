import { useState, useEffect } from 'react'
import { Wifi, WifiOff, RefreshCw, AlertCircle } from 'lucide-react'

interface NetworkStatusBadgeProps {
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
    compact?: boolean
}

export default function NetworkStatusBadge({ syncStatus, onSync, compact = false }: NetworkStatusBadgeProps) {
    const [isOnline, setIsOnline] = useState(navigator.onLine)

    useEffect(() => {
        const handleOnline = () => setIsOnline(true)
        const handleOffline = () => setIsOnline(false)

        window.addEventListener('online', handleOnline)
        window.addEventListener('offline', handleOffline)

        return () => {
            window.removeEventListener('online', handleOnline)
            window.removeEventListener('offline', handleOffline)
        }
    }, [])

    // Compact mode: just show a small indicator
    if (compact) {
        return (
            <div className={`w-2 h-2 rounded-full ${isOnline ? 'bg-green-500' : 'bg-red-500'}`}
                title={isOnline ? 'Online' : 'Offline'} />
        )
    }

    // Offline state
    if (!isOnline) {
        return (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-red-100 text-red-700 rounded-full text-sm font-medium">
                <WifiOff className="w-4 h-4" />
                <span>Offline</span>
            </div>
        )
    }

    // Syncing state
    if (syncStatus === 'syncing') {
        return (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-amber-100 text-amber-700 rounded-full text-sm font-medium">
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Synchronisiere...</span>
            </div>
        )
    }

    // Sync error state
    if (syncStatus === 'error') {
        return (
            <button
                onClick={onSync}
                className="flex items-center gap-2 px-3 py-1.5 bg-red-100 text-red-700 rounded-full text-sm font-medium hover:bg-red-200 transition-colors"
            >
                <AlertCircle className="w-4 h-4" />
                <span>Sync-Fehler</span>
            </button>
        )
    }

    // Online & synced state
    return (
        <button
            onClick={onSync}
            className="flex items-center gap-2 px-3 py-1.5 bg-green-100 text-green-700 rounded-full text-sm font-medium hover:bg-green-200 transition-colors"
        >
            <Wifi className="w-4 h-4" />
            <span>Online</span>
        </button>
    )
}
