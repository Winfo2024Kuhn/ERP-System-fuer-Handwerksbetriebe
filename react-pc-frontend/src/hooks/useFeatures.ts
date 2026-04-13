import { useState, useEffect } from 'react';

interface Features {
    en1090: boolean;
    echeck: boolean;
    email: boolean;
    rag: boolean;
}

const DEFAULT_FEATURES: Features = {
    en1090: false,
    echeck: false,
    email: true,
    rag: false,
};

let cachedFeatures: Features | null = null;
let cacheTime = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 Minuten

/**
 * Lädt aktivierte Feature-Flags vom Backend.
 * Ergebnis wird 5 Minuten gecacht, um unnötige Anfragen zu vermeiden.
 */
export function useFeatures(): Features {
    const [features, setFeatures] = useState<Features>(cachedFeatures ?? DEFAULT_FEATURES);

    useEffect(() => {
        const now = Date.now();
        if (cachedFeatures && now - cacheTime < CACHE_TTL_MS) {
            setFeatures(cachedFeatures);
            return;
        }
        fetch('/api/features')
            .then((r) => r.json())
            .then((data: Features) => {
                cachedFeatures = data;
                cacheTime = Date.now();
                setFeatures(data);
            })
            .catch(() => {
                // Bei Fehler Defaults verwenden – keine Fehlermeldung nötig
            });
    }, []);

    return features;
}
