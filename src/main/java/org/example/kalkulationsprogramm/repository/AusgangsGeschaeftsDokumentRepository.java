package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AusgangsGeschaeftsDokumentRepository extends JpaRepository<AusgangsGeschaeftsDokument, Long> {

    /**
     * Findet alle Dokumente für ein Projekt, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByProjektIdOrderByDatumDesc(Long projektId);

    boolean existsByProjektId(Long projektId);

    /**
     * Findet alle Dokumente für ein Anfrage, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByAnfrageIdOrderByDatumDesc(Long anfrageId);

    /**
     * Findet alle Dokumente für einen Kunden, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByKundeIdOrderByDatumDesc(Long kundeId);

    /** Datenschutz-Prüfung eines Kunden: Dokumente aller seiner Projekte in einer Abfrage. */
    List<AusgangsGeschaeftsDokument> findByProjektIdIn(java.util.Collection<Long> projektIds);

    /** Datenschutz-Prüfung eines Kunden: Dokumente aller seiner Anfragen in einer Abfrage. */
    List<AusgangsGeschaeftsDokument> findByAnfrageIdIn(java.util.Collection<Long> anfrageIds);

    /**
     * Findet Dokument anhand der Dokumentnummer
     */
    Optional<AusgangsGeschaeftsDokument> findByDokumentNummer(String dokumentNummer);

    /**
     * Findet alle Nachfolger-Dokumente eines Vorgängers, sortiert nach Erstellungszeitpunkt
     */
    List<AusgangsGeschaeftsDokument> findByVorgaengerIdOrderByErstelltAmAsc(Long vorgaengerId);

    /**
     * Sperrt die Rechnung, solange eine Gutschrift zu ihr angelegt, geändert oder gebucht wird
     * oder die Rechnung storniert wird: Zwei gleichzeitige Vorgänge dürfen den Rechnungsbetrag
     * nicht gemeinsam überschreiten bzw. eine neue Gutschrift übersehen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM AusgangsGeschaeftsDokument d WHERE d.id = :id")
    Optional<AusgangsGeschaeftsDokument> sperreZumGutschreiben(@Param("id") Long id);

    /**
     * Zählt Abschlagsrechnungen zu einem Vorgänger-Dokument (für Nummerierung)
     */
    @Query("SELECT COUNT(d) FROM AusgangsGeschaeftsDokument d WHERE d.vorgaenger.id = :vorgaengerId AND d.typ = :typ")
    int countByVorgaengerIdAndTyp(Long vorgaengerId, AusgangsGeschaeftsDokumentTyp typ);

    /**
     * Findet das (eindeutige) Dokument eines bestimmten Typs für ein Anfrage.
     * z.B. das ANFRAGE-Dokument, aus dem die Anfragesnummer abgeleitet wird.
     */
    Optional<AusgangsGeschaeftsDokument> findFirstByAnfrageIdAndTyp(Long anfrageId, AusgangsGeschaeftsDokumentTyp typ);

    /** Anfrage und Dokumentnummer, siehe {@link #findDokumentnummernJeAnfrage}. */
    interface AnfrageDokumentnummer {
        Long getAnfrageId();
        String getDokumentNummer();
    }

    /**
     * Nicht stornierte Dokumentnummern eines Typs für mehrere Anfragen auf
     * einmal – neuestes Dokument zuerst.
     */
    @Query("SELECT d.anfrage.id AS anfrageId, d.dokumentNummer AS dokumentNummer FROM AusgangsGeschaeftsDokument d "
            + "WHERE d.anfrage.id IN :anfrageIds AND d.typ = :typ AND d.storniert = false ORDER BY d.id DESC")
    List<AnfrageDokumentnummer> findDokumentnummernJeAnfrage(@Param("anfrageIds") Collection<Long> anfrageIds,
                                                             @Param("typ") AusgangsGeschaeftsDokumentTyp typ);

    /**
     * Prüft ob bereits ein aktives Basisdokument (ohne Vorgänger, nicht storniert)
     * für ein Projekt existiert. Eine stornierte Rechnung ohne Vorgänger zählt nicht:
     * Sonst ließe sich im Vorgang nach dem Storno keine neue Rechnung mehr anlegen
     * (Issue #262). Stornorechnungen selbst haben das Original als Vorgänger und
     * sind damit nie Basisdokument.
     */
    boolean existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(Long projektId);

    /**
     * Prüft ob bereits ein aktives Basisdokument (ohne Vorgänger, nicht storniert)
     * für eine Anfrage existiert. Siehe {@link #existsByProjektIdAndVorgaengerIsNullAndStorniertFalse}.
     */
    boolean existsByAnfrageIdAndVorgaengerIsNullAndStorniertFalse(Long anfrageId);

    /**
     * Prüft ob für ein Projekt bereits ein aktives Basisdokument (ohne Vorgänger,
     * nicht storniert) eines bestimmten Typs existiert — z.B. das ursprüngliche
     * ANGEBOT, das ein NACHTRAGSANGEBOT voraussetzt.
     */
    boolean existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(Long projektId, AusgangsGeschaeftsDokumentTyp typ);

    /**
     * Prüft ob für eine Anfrage bereits ein aktives Basisdokument eines bestimmten
     * Typs existiert. Siehe {@link #existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse}.
     */
    boolean existsByAnfrageIdAndVorgaengerIsNullAndTypAndStorniertFalse(Long anfrageId, AusgangsGeschaeftsDokumentTyp typ);

    /**
     * Findet alle Dokumente, die über ein Anfrage mit einem Projekt verknüpft sind
     * (anfrage.projekt_id = projektId), aber kein direktes projekt_id gesetzt haben.
     */
    @Query("SELECT d FROM AusgangsGeschaeftsDokument d WHERE d.anfrage.projekt.id = :projektId AND d.projekt IS NULL")
    List<AusgangsGeschaeftsDokument> findByAnfrageProjektIdAndProjektIsNull(Long projektId);

    /**
     * Findet die höchste Dokumentnummer für einen Monat (für neue Nummer)
     */
    @Query("SELECT MAX(d.dokumentNummer) FROM AusgangsGeschaeftsDokument d WHERE d.dokumentNummer LIKE :prefix%")
    Optional<String> findMaxDokumentNummerByPrefix(String prefix);

    /**
     * Findet alle Dokumente in einem Datumsbereich (für Dokumentübersicht).
     */
    List<AusgangsGeschaeftsDokument> findByDatumBetweenOrderByDatumDesc(java.time.LocalDate start, java.time.LocalDate end);

    /**
     * Findet alle Dokumente sortiert nach Datum absteigend (für Dokumentübersicht ohne Datumsfilter).
     */
    List<AusgangsGeschaeftsDokument> findAllByOrderByDatumDesc();

    /**
     * Liefert für eine Liste von Anfrage-IDs ein Mapping
     * [AusgangsGeschaeftsDokument.id, Anfrage.id], damit der DokumentFreigabeService
     * den jüngsten Freigabe-Status pro Anfrage auch für Dokumente aus dem neuen
     * AusgangsGeschaeftsDokument-System finden kann.
     */
    @Query("SELECT d.id, d.anfrage.id FROM AusgangsGeschaeftsDokument d WHERE d.anfrage.id IN :anfrageIds")
    List<Object[]> findIdAnfrageIdMappingByAnfrageIds(@org.springframework.data.repository.query.Param("anfrageIds") List<Long> anfrageIds);

    /**
     * Pendant zu {@link #findIdAnfrageIdMappingByAnfrageIds} für Projekte.
     */
    @Query("SELECT d.id, d.projekt.id FROM AusgangsGeschaeftsDokument d WHERE d.projekt.id IN :projektIds")
    List<Object[]> findIdProjektIdMappingByProjektIds(@org.springframework.data.repository.query.Param("projektIds") List<Long> projektIds);

    /**
     * Liefert die vom User bearbeiteten Rechnungsanschriften aller Dokumente
     * eines Projekts (direkt oder über die Anfrage verknüpft), zuletzt
     * geänderte zuerst. Quelle für die Adress-Übernahme beim Erstellen
     * neuer Dokumente im selben Vorgang.
     */
    @Query("SELECT d.rechnungsadresseOverride FROM AusgangsGeschaeftsDokument d " +
            "LEFT JOIN d.projekt p LEFT JOIN d.anfrage a LEFT JOIN a.projekt ap " +
            "WHERE (p.id = :projektId OR ap.id = :projektId) " +
            "AND d.rechnungsadresseOverride IS NOT NULL AND TRIM(d.rechnungsadresseOverride) <> '' " +
            "ORDER BY d.geaendertAm DESC NULLS LAST")
    List<String> findRechnungsadresseOverridesByProjektId(
            @org.springframework.data.repository.query.Param("projektId") Long projektId);

    /**
     * Pendant zu {@link #findRechnungsadresseOverridesByProjektId} für
     * Anfragen ohne Projekt-Verknüpfung.
     */
    @Query("SELECT d.rechnungsadresseOverride FROM AusgangsGeschaeftsDokument d " +
            "WHERE d.anfrage.id = :anfrageId " +
            "AND d.rechnungsadresseOverride IS NOT NULL AND TRIM(d.rechnungsadresseOverride) <> '' " +
            "ORDER BY d.geaendertAm DESC NULLS LAST")
    List<String> findRechnungsadresseOverridesByAnfrageId(
            @org.springframework.data.repository.query.Param("anfrageId") Long anfrageId);

    /** Buchhaltungsübersicht: keine Entwürfe/Angebote, Beziehungen ohne N+1 laden. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"projekt", "kunde", "anfrage", "anfrage.projekt"})
    @Query("""
            SELECT d FROM AusgangsGeschaeftsDokument d
            WHERE d.typ IN :typen
              AND (d.gebucht = true OR d.versandDatum IS NOT NULL OR d.storniert = true)
              AND (CAST(:start AS LocalDate) IS NULL OR d.datum >= :start)
              AND (CAST(:end AS LocalDate) IS NULL OR d.datum <= :end)
            ORDER BY d.datum DESC, d.id DESC
            """)
    List<AusgangsGeschaeftsDokument> findRechnungenFuerUebersicht(
            @org.springframework.data.repository.query.Param("typen") java.util.Set<AusgangsGeschaeftsDokumentTyp> typen,
            @org.springframework.data.repository.query.Param("start") java.time.LocalDate start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDate end);

    /**
     * Grobe Vorauswahl über den Positionstext, z. B. {@code %"leistungId":12%} in drei Schreibweisen
     * (kompakt, mit Leerzeichen, ID als Text). Die genaue Prüfung macht der Aufrufer, weil die
     * Muster auch 120 oder 1234 treffen. Neueste zuerst.
     */
    @Query("SELECT d FROM AusgangsGeschaeftsDokument d LEFT JOIN FETCH d.projekt "
            + "WHERE CAST(d.positionenJson AS String) LIKE :kompakt "
            + "OR CAST(d.positionenJson AS String) LIKE :mitLeerzeichen "
            + "OR CAST(d.positionenJson AS String) LIKE :alsText "
            + "ORDER BY d.datum DESC NULLS LAST, d.id DESC")
    List<AusgangsGeschaeftsDokument> findeMitLeistungInPositionen(@Param("kompakt") String kompakt,
            @Param("mitLeerzeichen") String mitLeerzeichen, @Param("alsText") String alsText);
}
