package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests fuer die kanonische Form und den Hash eines Protokolleintrags.
 *
 * <p>Die kanonische Form ist die Grundlage der gesamten Manipulationssicherung.
 * Aendert sich ihr Aufbau unbemerkt, wird jede bereits gespeicherte Kette
 * ungueltig und der Verifier meldet auf einmal ueberall Manipulation, wo gar
 * keine war. Diese Tests sind deshalb bewusst streng: sie zaehlen sogar die
 * Felder.</p>
 *
 * <p>DSGVO: nur Dummy-Daten (Max Mustermann).</p>
 */
class BelegAuditCanonicalTest {

    /**
     * Anzahl der mit {@code |} getrennten Felder in der kanonischen Form.
     * Wer ein Feld hinzufuegt, muss diese Zahl bewusst mit anheben -- und
     * sich dabei klarmachen, dass alle bestehenden Ketten neu aufgebaut
     * werden muessen.
     */
    private static final int ERWARTETE_FELDANZAHL = 34;

    @Test
    @DisplayName("Kanonische Form hat genau die erwartete Feldanzahl")
    void feldanzahlIstStabil() {
        BelegAudit a = beispiel();

        String[] felder = a.canonicalForm().split("\\|", -1);

        assertThat(felder).hasSize(ERWARTETE_FELDANZAHL);
    }

    @Test
    @DisplayName("Zweimal dieselbe Eingabe ergibt denselben Hash")
    void hashIstDeterministisch() {
        BelegAudit a = beispiel();
        BelegAudit b = beispiel();

        assertThat(a.canonicalForm()).isEqualTo(b.canonicalForm());
        assertThat(a.computeEntryHash()).isEqualTo(b.computeEntryHash());
    }

    @Test
    @DisplayName("Geänderter Betrag ergibt einen anderen Hash")
    void betragsaenderungAendertHash() {
        BelegAudit original = beispiel();
        String vorher = original.computeEntryHash();

        original.setBetragBrutto(new BigDecimal("23.81"));

        assertThat(original.computeEntryHash()).isNotEqualTo(vorher);
    }

    @Test
    @DisplayName("Geänderter Vorgänger-Hash ergibt einen anderen Hash")
    void vorgaengerAenderungAendertHash() {
        BelegAudit a = beispiel();
        String vorher = a.computeEntryHash();

        a.setPreviousHash("f".repeat(64));

        assertThat(a.computeEntryHash()).isNotEqualTo(vorher);
    }

    @Test
    @DisplayName("Leere Felder werden als leerer String serialisiert, nicht als 'null'")
    void nullWirdZuLeeremString() {
        BelegAudit a = BelegAudit.neutral(BelegAuditAktion.KASSE_GEZAEHLT, null, null, null);

        String form = a.canonicalForm();

        assertThat(form).doesNotContain("null");
        assertThat(form).startsWith("||KASSE_GEZAEHLT|");
    }

    @Test
    @DisplayName("Zeitstempel wird auf Mikrosekunden gekürzt, damit die Datenbank ihn unverändert zurückgibt")
    void zeitstempelOhneNanosekunden() {
        BelegAudit a = BelegAudit.neutral(BelegAuditAktion.ERFASST, null, null, null);

        assertThat(a.getGeaendertAm().getNano() % 1000).isZero();
    }

    @Test
    @DisplayName("Zwei verschiedene Beträge ergeben verschiedene Datei-Hashes")
    void sha256UeberBytes() {
        assertThat(BelegAudit.sha256("hallo".getBytes()))
                .isNotEqualTo(BelegAudit.sha256("hallo!".getBytes()));
        assertThat(BelegAudit.sha256("hallo".getBytes())).hasSize(64);
    }

    @Test
    @DisplayName("Snapshot übernimmt die Kernangaben des Belegs")
    void snapshotAusBeleg() {
        Beleg b = new Beleg();
        b.setId(42L);
        b.setLaufendeNummer(7L);
        b.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        b.setStatus(BelegStatus.VALIDIERT);
        b.setBelegDatum(LocalDate.of(2026, 3, 14));
        b.setBetragBrutto(new BigDecimal("19.99"));
        b.setFestgeschrieben(true);
        b.setDateiHash("a".repeat(64));

        BelegAudit a = BelegAudit.fromBeleg(b, BelegAuditAktion.FESTGESCHRIEBEN, null, "Abschluss", null);

        assertThat(a.getBelegId()).isEqualTo(42L);
        assertThat(a.getLaufendeNummer()).isEqualTo(7L);
        assertThat(a.getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_AUSGABE);
        assertThat(a.isFestgeschrieben()).isTrue();
        assertThat(a.getDateiHash()).isEqualTo("a".repeat(64));
        assertThat(a.getAenderungsgrund()).isEqualTo("Abschluss");
    }

    // ===================== Hilfen =====================

    private BelegAudit beispiel() {
        BelegAudit a = new BelegAudit();
        a.setChainIndex(5L);
        a.setBelegId(42L);
        a.setAktion(BelegAuditAktion.FESTGESCHRIEBEN);
        a.setLaufendeNummer(17L);
        a.setBelegKategorie(BelegKategorie.KASSE_AUSGABE);
        a.setBelegStatus(BelegStatus.VALIDIERT);
        a.setBelegDatum(LocalDate.of(2026, 3, 14));
        a.setBelegNummer("BON-4711");
        a.setBeschreibung("Schrauben und Dübel");
        a.setBetragNetto(new BigDecimal("16.80"));
        a.setBetragBrutto(new BigDecimal("19.99"));
        a.setMwstSatz(new BigDecimal("19.00"));
        a.setZahlungsart("BAR");
        a.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);
        a.setSachkontoId(3L);
        a.setSachkontoNummer("4930");
        a.setIstUmbuchung(false);
        a.setGespeicherterDateiname("uuid_bon.jpg");
        a.setDateiHash("b".repeat(64));
        a.setFestgeschrieben(true);
        a.setPreviousHash("c".repeat(64));
        a.setGeaendertAm(LocalDateTime.of(2026, 4, 1, 9, 30, 0));
        a.setAenderungsgrund("Festgeschrieben mit Monatsabschluss März 2026");
        a.setIpAdresse("192.168.1.10");
        return a;
    }
}
