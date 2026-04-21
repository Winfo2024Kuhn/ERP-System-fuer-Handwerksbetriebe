# Welding-Glyphs

Schematische Darstellungen für Schweißverfahren, Nahtarten und Schweißpositionen.

**Wichtig:** Diese Glyphen sind **nicht normgerecht** nach DIN EN ISO 6947 / 9692
— sie sind schematische Symbolbilder, die Wiedererkennung beim Nutzer auslösen
sollen (Wannenlage "liegt flach", V-Naht "hat eine V-Fuge" etc.).

Für die MVP-Version ausreichend. Für die produktionsreife Version ggf. durch
fachgeprüfte Illustrationen (z. B. DVS-Material, geprüfte technische Zeichnungen
oder Werkstatt-Fotos) ersetzen.

## Inhalt

- **Verfahren** (4): `VerfahrenMAG`, `VerfahrenMIG`, `VerfahrenWIG`, `VerfahrenMMA`
  + Dispatcher `VerfahrenGlyph` (Props: `letters: 'MAG' | 'MIG' | 'WIG' | 'MMA'`)
- **Nahtarten** (6): `NahtartStumpfI`, `NahtartStumpfV`, `NahtartStumpfX`,
  `NahtartKehl`, `NahtartHV`, `NahtartDHV` + Dispatcher `NahtGlyph`
  (Props: `kind: 'I' | 'V' | 'X' | 'Kehl' | 'HV' | 'DHV'`)
- **Positionen** (7): `PosPA` … `PosPG` + Dispatcher `PositionGlyph`
  (Props: `id: 'PA' | ... | 'PG'`)

Alle Komponenten nehmen ein optionales `className` entgegen und füllen ihren
Container (`width="100%" height="100%"` auf einem `viewBox="0 0 160 120"`).

## Herkunft

Original-SVGs aus dem Claude-Design-Prototyp (illustrations.jsx), 1:1
übernommen und in typisierte TSX-Komponenten extrahiert.
