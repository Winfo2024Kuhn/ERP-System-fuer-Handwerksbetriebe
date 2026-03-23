export function shouldIncludeOfflineCompletedMinutes(fromCache: boolean, pendingCount: number): boolean {
    return fromCache || pendingCount > 0
}

export function shouldIncludeCurrentSessionMinutes(
    fromCache: boolean,
    pendingCount: number,
    hasRecentStartSync: boolean,
): boolean {
    return fromCache || pendingCount > 0 || hasRecentStartSync
}