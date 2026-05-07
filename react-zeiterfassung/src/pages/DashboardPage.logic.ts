export function shouldIncludeOfflineCompletedMinutes(fromCache: boolean, pendingCount: number): boolean {
    return fromCache || pendingCount > 0
}