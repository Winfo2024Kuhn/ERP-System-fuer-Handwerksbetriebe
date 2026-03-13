package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AusgangsGeschaeftsDokumentRepository extends JpaRepository<AusgangsGeschaeftsDokument, Long> {

    /**
     * Findet alle Dokumente für ein Projekt, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByProjektIdOrderByDatumDesc(Long projektId);

    /**
     * Findet alle Dokumente für ein Anfrage, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByAnfrageIdOrderByDatumDesc(Long anfrageId);

    /**
     * Findet alle Dokumente für einen Kunden, sortiert nach Datum absteigend
     */
    List<AusgangsGeschaeftsDokument> findByKundeIdOrderByDatumDesc(Long kundeId);

    /**
     * Findet Dokument anhand der Dokumentnummer
     */
    Optional<AusgangsGeschaeftsDokument> findByDokumentNummer(String dokumentNummer);

    /**
     * Findet alle Nachfolger-Dokumente eines Vorgängers, sortiert nach Erstellungszeitpunkt
     */
    List<AusgangsGeschaeftsDokument> findByVorgaengerIdOrderByErstelltAmAsc(Long vorgaengerId);

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

    /**
     * Prüft ob bereits ein Basisdokument (ohne Vorgänger) für ein Projekt existiert.
     */
    boolean existsByProjektIdAndVorgaengerIsNull(Long projektId);

    /**
     * Prüft ob bereits ein Basisdokument (ohne Vorgänger) für ein Anfrage existiert.
     */
    boolean existsByAnfrageIdAndVorgaengerIsNull(Long anfrageId);

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
}
