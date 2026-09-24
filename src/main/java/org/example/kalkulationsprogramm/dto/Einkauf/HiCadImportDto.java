package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.List;
import java.util.UUID;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;

public final class HiCadImportDto {
    private HiCadImportDto() {}
    public record Zeile(int zeilennummer, String rohtext, PositionSnapshot vorschlag,
            List<Long> artikelKandidaten, boolean bereitsUebernommen, List<String> hinweise, List<BildVorschlag> bilder) {}
    public record BildVorschlag(Long dateiId, String dateiname, String mimeTyp, long byteAnzahl, String url) {}
    /** Kopfblock der HiCAD-Sägeliste (oberhalb der Überschriftenzeile); alle Werte optional. */
    public record Kopfdaten(String zeichnungsnummer, String auftragsnummer, String auftragstext, String kunde) {
        public static final Kopfdaten LEER = new Kopfdaten(null, null, null, null);
    }
    public record Vorschau(Long id, String dateiHash, boolean dateiSchonImportiert, List<Zeile> zeilen, Kopfdaten kopf) {
        public Vorschau(Long id, String dateiHash, boolean dateiSchonImportiert, List<Zeile> zeilen) {
            this(id, dateiHash, dateiSchonImportiert, zeilen, Kopfdaten.LEER);
        }
    }
    public record ImportFortschritt(Long id, long version, boolean duplikat, List<ZeilenFortschritt> zeilen) {}
    public record ZeilenFortschritt(int zeilennummer, java.math.BigDecimal gesamtmenge,
            java.math.BigDecimal uebernommeneMenge, java.math.BigDecimal verbleibendeMenge, boolean vollstaendigUebernommen) {}
    public record ZeilenAuswahl(int zeilennummer, java.math.BigDecimal menge, PositionSnapshot korrigiert,
            List<Long> bestaetigteBildDateiIds) {
        public ZeilenAuswahl(int zeilennummer, java.math.BigDecimal menge, PositionSnapshot korrigiert) {
            this(zeilennummer, menge, korrigiert, List.of());
        }
    }
    public record Uebernahme(long version, List<ZeilenAuswahl> zeilen, boolean duplikatBewusst, UUID idempotenzKey) {}
}
