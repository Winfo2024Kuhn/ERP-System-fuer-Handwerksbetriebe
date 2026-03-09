# Beitragen zum Handwerkerprogramm

Danke für dein Interesse an diesem Projekt! 🎉

## Wie du beitragen kannst

1. **Fork** dieses Repository
2. **Feature-Branch** anlegen: `git checkout -b feature/mein-feature`
3. **Änderungen committen:** `git commit -m 'Neues Feature hinzugefügt'`
4. **Branch pushen:** `git push origin feature/mein-feature`
5. **Pull Request** erstellen

## Entwicklungsrichtlinien

Alle Coding-Standards, Test-Anforderungen, Design-Guidelines und Sicherheitsregeln findest du in [.github/DEVELOPMENT.md](.github/DEVELOPMENT.md).

## Voraussetzungen

- Java 23 (JDK)
- Node.js 18+
- MySQL 8
- Maven (Wrapper enthalten)

## Lokale Entwicklung

```bash
# Backend starten
./mvnw spring-boot:run

# Frontend starten
cd react-pc-frontend && npm install && npm run dev
```

## Pull Request Checkliste

- [ ] Tests geschrieben und alle Tests bestehen (`./mvnw test`)
- [ ] Frontend baut ohne Fehler (`npm run build`)
- [ ] Keine Secrets oder Passwörter im Code
- [ ] Deutsche UI-Texte verwendet
- [ ] DEVELOPMENT.md aktualisiert bei neuen UI-Patterns

## Fragen?

Einfach ein Issue öffnen!
