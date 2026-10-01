import '@testing-library/jest-dom/vitest';

// localStorage/sessionStorage In-Memory-Polyfill für die Test-Umgebung.
// jsdom liefert beide normalerweise mit; in unserer Pipeline werden sie aber durch
// die `--localstorage-file`-Flag des Test-Runners stillgelegt, sodass setItem
// nicht aufrufbar ist. Diese minimale Storage-Implementierung reicht für Unit-
// Tests (kein Persistence-Verhalten, dafür berechenbarer Roundtrip).
function createMemoryStorage(): Storage {
    const store = new Map<string, string>();
    return {
        get length() { return store.size; },
        clear: () => store.clear(),
        getItem: (key: string) => store.has(key) ? store.get(key)! : null,
        setItem: (key: string, value: string) => { store.set(key, String(value)); },
        removeItem: (key: string) => { store.delete(key); },
        key: (index: number) => Array.from(store.keys())[index] ?? null,
    } as Storage;
}
if (typeof window !== 'undefined') {
    const needsLocal = typeof window.localStorage === 'undefined'
        || typeof window.localStorage.setItem !== 'function';
    if (needsLocal) {
        Object.defineProperty(window, 'localStorage', {
            configurable: true,
            value: createMemoryStorage(),
        });
    }
    const needsSession = typeof window.sessionStorage === 'undefined'
        || typeof window.sessionStorage.setItem !== 'function';
    if (needsSession) {
        Object.defineProperty(window, 'sessionStorage', {
            configurable: true,
            value: createMemoryStorage(),
        });
    }
}

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

// Mock IntersectionObserver
class MockIntersectionObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  value: MockIntersectionObserver,
});

// Mock ResizeObserver
class MockResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: MockResizeObserver,
});

// Layout-Geometrie für ProseMirror/tiptap.
// jsdom (aktuell v28) kennt weder document.elementFromPoint noch
// Range.getClientRects/getBoundingClientRect (Element hat beide). ProseMirror
// braucht sie, um Cursor-Positionen zu berechnen. tiptaps focus() ruft im
// nächsten Animation-Frame scrollIntoView auf -> coordsAtPos -> Range.getClientRects.
// Ohne Stub wirft dieser Frame, sobald er feuert, solange der Editor noch lebt
// (oft erst nach den Assertions), einen unbehandelten TypeError und macht den
// Lauf je nach Timing rot, obwohl alle Tests grün sind.
// Ohne Layout gibt es keine Rechtecke: leere Liste bzw. Null-Rechteck, wie bei
// Element.getClientRects/getBoundingClientRect in jsdom.
if (!document.elementFromPoint) {
  document.elementFromPoint = () => null;
}
if (!Range.prototype.getBoundingClientRect) {
  Range.prototype.getBoundingClientRect = () => new DOMRect();
}
if (!Range.prototype.getClientRects) {
  Range.prototype.getClientRects = () => ({
    length: 0,
    item: () => null,
    [Symbol.iterator]: function* () {},
  }) as unknown as DOMRectList;
}
