package org.example.kalkulationsprogramm.repository;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KundeRepository extends JpaRepository<Kunde, Long>, JpaSpecificationExecutor<Kunde>
{
    Optional<Kunde> findByKundennummerIgnoreCase(String kundennummer);

    @Query(value = "SELECT kundennummer FROM kunde ORDER BY CAST(kundennummer AS UNSIGNED) DESC LIMIT 1", nativeQuery = true)
    Optional<String> findMaxKundennummer();

    /**
     * Sucht Kunden nach Name, Ansprechpartner, Kundennummer oder E-Mail.
     */
    @Query("SELECT DISTINCT k FROM Kunde k LEFT JOIN k.kundenEmails e " +
           "WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(k.ansprechspartner) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(k.kundennummer) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(e) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Kunde> searchByNameOrAnsprechpartnerOrEmail(@Param("query") String query);

    /**
     * Prüft ob eine E-Mail-Adresse bei einem Kunden hinterlegt ist.
     */
    @Query("SELECT COUNT(k) > 0 FROM Kunde k JOIN k.kundenEmails e WHERE LOWER(e) = LOWER(:email)")
    boolean existsByKundenEmail(@Param("email") String email);

    /**
     * Findet Kunden, die diese E-Mail-Adresse hinterlegt haben.
     */
    @Query("SELECT DISTINCT k FROM Kunde k JOIN k.kundenEmails e WHERE LOWER(e) = LOWER(:email)")
    List<Kunde> findByKundenEmailIgnoreCase(@Param("email") String email);

    /**
     * Sucht potenzielle Duplikate über mehrere Felder gleichzeitig.
     * Liefert alle Kunden, die in mindestens einem der angegebenen Felder mit den
     * übergebenen Werten übereinstimmen. Die Werte sind bereits normalisiert
     * (E-Mail/Name lowercase, Telefon nur Ziffern).
     *
     * <p>{@code telefonDigits} und {@code mobilDigits} werden gegen einen
     * SQL-seitig normalisierten Vergleichsstring geprüft (alle Nicht-Ziffern raus,
     * mit führender 0 statt +49 / 0049). Nullwerte überspringen die jeweilige Bedingung.
     */
    @Query("SELECT DISTINCT k FROM Kunde k LEFT JOIN k.kundenEmails e WHERE " +
           "(:email IS NOT NULL AND LOWER(e) = :email) OR " +
           "(:telefonDigits IS NOT NULL AND k.telefon IS NOT NULL AND " +
           "  REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(k.telefon, ' ', ''), '-', ''), '/', ''), '(', ''), ')', ''), '.', ''), '+49', '0'), '0049', '0'), '+', '') = :telefonDigits) OR " +
           "(:mobilDigits IS NOT NULL AND k.mobiltelefon IS NOT NULL AND " +
           "  REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(k.mobiltelefon, ' ', ''), '-', ''), '/', ''), '(', ''), ')', ''), '.', ''), '+49', '0'), '0049', '0'), '+', '') = :mobilDigits) OR " +
           "(:name IS NOT NULL AND :plz IS NOT NULL AND LOWER(k.name) = :name AND k.plz = :plz) OR " +
           "(:name IS NOT NULL AND :strasse IS NOT NULL AND LOWER(k.name) = :name AND " +
           "  REPLACE(REPLACE(LOWER(k.strasse), 'straße', 'str.'), 'strasse', 'str.') = :strasse)")
    List<Kunde> findePotenzielleDuplikate(@Param("email") String emailLower,
                                          @Param("telefonDigits") String telefonDigits,
                                          @Param("mobilDigits") String mobilDigits,
                                          @Param("name") String nameLower,
                                          @Param("plz") String plz,
                                          @Param("strasse") String strasseLower);
}
