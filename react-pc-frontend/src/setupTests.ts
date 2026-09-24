import '@testing-library/jest-dom/vitest';

// jsdom does not implement the geometry methods ProseMirror uses to place the
// cursor. Keep these stubs shared so every editor test gets the same DOM API.
if (!document.elementFromPoint) {
    document.elementFromPoint = () => null;
}
if (!Range.prototype.getBoundingClientRect) {
    Range.prototype.getBoundingClientRect = () => ({
        bottom: 0, height: 0, left: 0, right: 0, top: 0, width: 0, x: 0, y: 0, toJSON: () => {},
    });
}
if (!Range.prototype.getClientRects) {
    Range.prototype.getClientRects = () => ({
        length: 0,
        item: () => null,
        [Symbol.iterator]: function* () {},
    }) as unknown as DOMRectList;
}

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
