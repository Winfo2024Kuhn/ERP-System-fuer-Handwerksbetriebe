import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Fügt Tailwind-Klassen zusammen und löst Konflikte auf (shadcn-Standardhelfer). */
export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs))
}
