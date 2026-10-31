# Lieferanten-Shops über IDS-Connect

Stand: 31.10.2026. Benutzersicht: [`benutzer/rechnungen.md`](benutzer/rechnungen.md#material-aus-dem-lieferanten-shop-einfügen) und [`benutzer/einstellungen.md`](benutzer/einstellungen.md#lieferanten-shops-ids-anbinden).

## Wozu

Material aus dem Lager mit den richtigen Einkaufspreisen verrechnen, ohne den Artikelstamm vorher zu pflegen: Im Dokumenteditor öffnet das ERP den Online-Shop des Lieferanten (z. B. Würth), der Nutzer stellt dort einen Warenkorb zusammen, der Shop schickt ihn über den Browser zurück. Daraus werden Positionen (einzeln oder als eine Sammelposition) mit frei wählbarem Aufschlag; auf Wunsch werden Preise im Artikelstamm nachgezogen oder Artikel angelegt.

## Ablauf

```
Dokumenteditor          ERP-Backend                      Browser-Tab              Shop
     │ POST /api/ids/vorgaenge ─▶ IdsShopBesuchService.starte
     │                          Vorgang (WARTET), Schlüssel (256 Bit, nur Hash in DB)
     │ ◀─ Formular (action=WKE, kndnr, name_kunde, pw_kunde, hookurl=…/api/ids/rueckgabe/{schluessel})
     │ ────────────── öffnet Tab, schickt Formular per POST ──────────────────────────▶ Login
     │                                                            Nutzer klickt Warenkorb zusammen
     │                          POST /api/ids/rueckgabe/{schluessel} ◀── Feld „warenkorb“ (XML)
     │                          nimmRueckgabeAn → EMPFANGEN, Positionen
     │                          303 → /ids/warenkorb-angekommen ──▶ „Tab schließen“ + BroadcastChannel
     │ GET /api/ids/vorgaenge/{id} (Polling alle 3 s) ─▶ Warenkorb + Artikelstamm-Treffer + Aufschlag
     │ Positionen einfügen (nur Frontend, positionenJson)
     │ POST /api/ids/vorgaenge/{id}/artikelstamm ─▶ IdsArtikelstammAbgleich
```

## Protokoll (IDS-Connect 2.5, ITEK 02.11.2020)

- **Hinweg:** `multipart/form-data` an die Shop-Adresse: `action=WKE` (Warenkorb empfangen, ohne Bestellung), `version=2.5`, `kndnr`, `name_kunde`, `pw_kunde`, `hookurl`. Leere Zugangsfelder werden weggelassen.
- **Rückweg:** Formular (meist multipart) an `hookurl`, Feld `warenkorb` = XML mit Wurzel `Warenkorb`. Gelesen werden `WarenkorbInfo/RueckgabeKZ` („… mit Bestellung“ → Warnhinweis) und je `Order/OrderItem`: `ArtNo`, `Qty`, `QU` (Pflicht), `Kurztext`, `Langtext`, `NetPrice`, `PriceBasis`, `Hinweis`, `Fehlertext`, `EAN`. Einkaufspreis je Einheit: **`NetPrice` ist der Preis der ganzen Zeile** – die Spezifikation sagt, er „bezieht sich immer auf die Anfragemenge“, und Würth schickt es so (24 Kartuschen: 115,20 → 4,80 je Stück). Also `NetPrice / Qty`; nur bei `PriceBasis` > 1 (Preis je 100 Stück) `NetPrice / PriceBasis` (4 Stellen). Bewusste Entscheidung, belegt mit Würth (31.10.2026). Schickt ein anderer Shop den Stückpreis mit `PriceBasis` 1, wäre der Einkaufspreis um den Faktor Menge zu klein – dann einen eigenen `IdsProtokoll`-Wert mit Adapter anlegen (oder einen Schalter je Zugang), nicht die Regel für alle ändern. Der Roh-Warenkorb in `ids_vorgang.rohdaten` zeigt, was der Shop geschickt hat. Würth hängt außerdem die Verpackungseinheit mit Füll-Leerzeichen an die Artikelnummer („08925304     24“); sie wird abgeschnitten, gerechnet wird je Stück. Preise sind seit 2.5 optional → `null`. Unbekannte Elemente werden übergangen.
- **XML** nur über `util/SicheresXml` (kein DOCTYPE, keine externen Entities); Multipart als Rohbytes, damit die Kodierung aus der XML-Deklaration gilt.
- **Variante „Nur Zugangsfelder“ (`IDS_NUR_ZUGANGSFELDER`, `IdsNurZugangsfelderAdapter`):** schickt beim Öffnen nur `kndnr`, `name_kunde`, `pw_kunde`, `hookurl` – so steht es im IDS-Zugangsblatt von Würth (Einstieg `…/ViewIDSCatalogService-IDSInBound`). Der Rückweg ist derselbe. Die Variante ist je Zugang wählbar, damit man beim Test ohne Code-Änderung umschalten kann.
- **Würth:** nutzt nach bisherigem Stand denselben Standard (kleingeschriebene Feldnamen, multipart). Ein Test mit echtem Würth-Zugang steht noch aus; der frühere Versuch auf `feature/en1090-echeck` schickte kein `action` und las ein erfundenes Rückgabeformat.

## Sicherheit

- **Rücksprung ohne Anmeldung:** Die Rückgabe ist eine Navigation von der Shop-Seite; das Sitzungs-Cookie (SameSite=Lax) kommt dabei nicht mit. Eigene Filterkette `idsRueckgabeFilterChain` (`@Order(-1)`, stateless, nur POST, CSRF für genau diesen Pfad ausgenommen, `Referrer-Policy: no-referrer`) und `IdsRueckgabeGroessenFilter` (höchstens 1 MB, ohne `Content-Length` → 411).
- **Schlüssel:** 32 Byte `SecureRandom`, Base64-URL (43 Zeichen), in der DB nur SHA-256. Einmal einlösbar (danach Status ≠ `WARTET`), 60 Minuten gültig.
- **Warenkorb abholen** nur mit Sitzung + CSRF und nur durch den Benutzer, der den Shop geöffnet hat (`frontend_user_id`), sonst 404.
- **Passwort:** verschlüsselt mit `MailSecretService` in `ids_zugang.passwort_verschluesselt`; die Start-Antwort enthält es zwangsläufig im Klartext (der Browser muss es an den Shop schicken) → `Cache-Control: no-store`, `toString` der Records blendet es aus.
- **Wer das Shop-Passwort sehen kann:** Das Startformular muss das Passwort an den Browser geben; jeder angemeldete PC-Benutzer kann es deshalb über die Entwicklerwerkzeuge des Browsers sehen. Empfehlung: beim Lieferanten einen eigenen IDS-Zugang ohne Bestellrechte anlegen lassen.
- **Was der Shop erfährt:** Die `hookurl` enthält die Adresse des ERP, wie der Browser sie nutzt (z. B. den Tailscale-Namen). Sie ist für den Shop nicht erreichbar – der Rücksprung läuft über den Browser im Firmennetz.
- **Zahlen aus dem Shop** werden begrenzt gelesen (höchstens 20 Zeichen, kein Exponent, Menge < 10⁹, Preis < 10⁹); bei Punkt und Komma zählt das letzte als Dezimaltrenner. Unlesbares wird zur Zeile mit Hinweis statt zum Fehler. Gleichzeitige Rücksprünge mit demselben Schlüssel werden per Zeilensperre nacheinander verarbeitet; der zweite sieht „schon eingelöst“.
- **Preis-Herkunft im Artikelstamm:** Unverändert übernommener Shop-Preis → `IDS_SHOP`; im Dialog geändert oder nachgetragen → `MANUELL`.
- **Rücksprung-Adresse** = `Origin` des Start-Aufrufs (die Adresse, die der Browser benutzt, auch hinter Tailscale), sonst die Adresse der Anfrage.
- **Prod-Kopie:** `scripts/prodkopie/abschotten.sql` setzt `ids.aktiv=false` und leert `ids_zugang`.
- **Rechte:** Einrichtung (`/api/ids/einstellungen/**`) nur Admin; Shop öffnen jeder angemeldete PC-Benutzer.

## Datenmodell (V444)

| Tabelle | Inhalt |
|---|---|
| `ids_zugang` | je Lieferant höchstens ein Zugang: `aktiv`, `protokoll` (`IDS_CONNECT_2_5` oder `IDS_NUR_ZUGANGSFELDER`), `shop_url`, `kundennummer`, `benutzername`, `passwort_verschluesselt` |
| `ids_vorgang` | ein Shop-Besuch: `frontend_user_id`, `protokoll`, `schluessel_hash`, `status`, `gueltig_bis`, `mit_bestellung`, `fehlertext`; `rohdaten` = Warenkorb, wie der Shop ihn geschickt hat (nur zur Fehlersuche, nie angezeigt; kann Lieferadresse/Ansprechpartner enthalten); alles nach 30 Tagen beim nächsten Shop-Besuch weggeräumt – wird der Shop nicht mehr genutzt, bleiben die letzten Besuche stehen, bis man die Anbindung wieder nutzt |
| `ids_warenkorb_position` | zurückgeschickte Artikel, `position_index` ab 0 |
| `system_setting` `ids.aktiv` | Grundschalter |

## Erweitern

- **Neuer Großhändler mit eigener Spielart:** Wert in `IdsProtokoll` + `@Component`, das `IdsProtokollAdapter` umsetzt (Formular bauen, Warenkorb lesen). `IdsShopBesuchService` findet ihn über das Protokoll des Zugangs; keine weitere Änderung nötig. Die Auswahl im Einstellungs-Formular um den Anzeigenamen ergänzen.
- **Beschaffung / Bestellungen:** Einen Shop-Besuch starten und abholen geht genauso wie im Dokumenteditor (`IdsShopBesuchService`, Frontend `components/ids/useIdsShopBesuch.ts`); aus `IdsVorgang.positionen` dann eine `Bestellung` bauen. `IdsArtikelstammAbgleich` lässt sich unverändert für den Preisabgleich nutzen. Ein Abrufen mit `action=WKS` (Warenkorb an den Shop senden) wäre ein weiterer Adapter-Aufruf.

## Bedienung im Dokumenteditor (Stand 31.10.2026)

- Einstieg über „Material“ → Menü „Aus Artikelstamm“ / „Aus Lieferanten-Shop“ (Menü nur, wenn `GET /api/ids/verfuegbar` aktiv ist).
- Schritt „Preise prüfen“: Zeile entfernen (mit Rückgängig), „Weitere Artikel aus dem Shop holen“ startet einen neuen Vorgang und hängt dessen Zeilen an; jede Zeile kennt `{vorgangId, index}`.
- Schritt „Artikelstamm“: Preis aktualisieren je Vorgang über `POST /api/ids/vorgaenge/{id}/artikelstamm` (`aktualisieren`); neue Artikel über das bestehende `CreateArticleModal` mit Vorbelegung und `POST /api/artikel` mit `preisQuelle: "IDS_SHOP"` (nur `MANUELL`/`IDS_SHOP` erlaubt). Das Feld `neuAnlegen` des IDS-Endpunkts bleibt für spätere Verwender (z. B. Beschaffung).
- Kundentext einer Einzelposition = Bezeichnung (+ Langtext), ohne Lieferanten-Artikelnummer und -name.
- Was Würth schickt (echter Warenkorb): `ArtNo` (mit VE-Anhang), `EAN`, `Qty`, `QU`, `Kurztext`, `OfferPrice` (je Stück), `NetPrice` (Zeile), `PriceBasis`, `VAT` – keine Bilder, Langtexte oder Datenblätter. Automatischer nächtlicher Preisabgleich: Issue #389 (Datanorm/Comnorm).

## Lokal testen ohne echten Shop

`scripts/ids-testshop/testshop.py` ist ein Übungs-Shop auf `http://localhost:9000` (nur Python-Standardbibliothek). Er nimmt das Anmeldeformular an, zeigt die empfangenen Felder (Passwort nur als gesetzt/leer), lässt Beispielartikel anklicken und schickt den Warenkorb als IDS-XML (multipart, Feld `warenkorb`) an die `hookurl` zurück – wahlweise auch „mit Bestellung“ oder kaputt, um die Fehlerseiten zu sehen. Als Shop-Adresse im Zugang `http://localhost:9000/ids` eintragen (`http://` ist nur für localhost erlaubt). Mit echtem Shop auf der Prod-Kopie: `scripts/prodkopie/prodkopie-starten.sh --wegwerf-schluessel` (sonst lassen sich keine Passwörter speichern); die Abschottung leert die Zugänge bei jedem Start.
