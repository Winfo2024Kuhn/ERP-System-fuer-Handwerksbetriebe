import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from './button';
import { Card } from './card';

/** A failed request must remain distinguishable from a successfully loaded empty list. */
export function LadefehlerPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
    return <Card className="p-6 border-rose-200 bg-rose-50/50">
        <div role="alert" className="flex items-start gap-3 text-rose-800">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
            <div className="space-y-3">
                <p className="font-medium">{message}</p>
                <p className="text-sm text-slate-600">Die Daten konnten nicht geladen werden. Bitte erneut versuchen.</p>
                <Button variant="outline" onClick={onRetry}><RefreshCw className="h-4 w-4" />Erneut laden</Button>
            </div>
        </div>
    </Card>;
}
