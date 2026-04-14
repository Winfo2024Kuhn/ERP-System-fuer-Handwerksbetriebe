# 🧪 Testing & Endpoint-Security

## Test-Anforderungen
- **Service-Schicht:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`). Ziel: ≥ 80% Coverage.
- **Controller-Schicht:** MockMvc (`@WebMvcTest`). Ziel: Alle Endpoints (Happy-Path + Fehlerfall).
- **Repository-Schicht:** H2 In-Memory (`@DataJpaTest`).
- **Frontend:** Vitest + Testing Library (neben Quell-Datei als `*.test.tsx`).
- **Daten:** Immer Dummy-Daten nutzen (DSGVO!). Utils erfordern 100% Coverage.

## Sicherheits-Pflichtcheckliste (Für JEDEN neuen Endpoint)
Bevor ein Endpoint als "fertig" markiert wird, müssen folgende Angriffsvektoren im Code/Test abgedeckt sein:
1. **SQL Injection:** `'; DROP TABLE x; --` in allen String-Parametern.
2. **XSS:** `<script>alert(1)</script>` in allen Textfeldern.
3. **Ungültige IDs:** Negative Werte, `Long.MAX_VALUE`, `0`.
4. **Limits:** Leere Pflichtfelder und überlange Eingaben (> 10.000 Zeichen) abfangen.
5. **Datei-Uploads/Downloads:** Path-Traversal verhindern (`../../etc/passwd`), gefährliche Dateitypen (`.exe`, `.bat`, `.js`) blockieren.