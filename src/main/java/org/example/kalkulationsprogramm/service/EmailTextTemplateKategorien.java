package org.example.kalkulationsprogramm.service;

import java.util.Map;
import org.example.kalkulationsprogramm.domain.EmailTextTemplateKategorie;

/**
 * Zentrale Zuordnung Dokumenttyp → Kategorie. Wird sowohl beim Auflisten der
 * verfügbaren Dokumenttypen im Controller verwendet als auch als Fallback im
 * DTO-Mapper, falls eine Vorlage ohne explizit gesetzte Kategorie aus der DB
 * kommt (z.B. weil sie über eine ältere Frontend-Version angelegt wurde).
 *
 * <p>Single Source of Truth — wenn ein neuer Dokumenttyp hinzukommt, hier
 * ergänzen, dann taucht er im UI automatisch unter der richtigen Gruppe auf.</p>
 */
public final class EmailTextTemplateKategorien {

    private static final Map<String, EmailTextTemplateKategorie> ZUORDNUNG = Map.ofEntries(
            // Verkaufs- und Buchhaltungsdokumente an den Kunden
            Map.entry("ANGEBOT", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("NACHTRAGSANGEBOT", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("AUFTRAGSBESTAETIGUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("ZEICHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("RECHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("TEILRECHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("ABSCHLAGSRECHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("SCHLUSSRECHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("GUTSCHRIFT", EmailTextTemplateKategorie.DOKUMENT),
            Map.entry("STORNORECHNUNG", EmailTextTemplateKategorie.DOKUMENT),
            // Mahnstufen — alles, was nach Faelligkeit automatisch rausgeht
            Map.entry("ZAHLUNGSERINNERUNG", EmailTextTemplateKategorie.MAHNWESEN),
            Map.entry("ERSTE_MAHNUNG", EmailTextTemplateKategorie.MAHNWESEN),
            Map.entry("ZWEITE_MAHNUNG", EmailTextTemplateKategorie.MAHNWESEN),
            Map.entry("MAHNUNG", EmailTextTemplateKategorie.MAHNWESEN),
            // Webseiten-Funnel / Lead-Automatik
            Map.entry("WEBSITE_ANFRAGE_BESTAETIGUNG", EmailTextTemplateKategorie.WEBSITE),
            Map.entry("EINKAUF_ANFRAGE", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_BESTELLUNG", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_DIREKTBESTELLUNG", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_NACHFRAGE", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_ZEUGNIS_NACHFORDERUNG", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_BESTAETIGUNG_NACHFRAGE", EmailTextTemplateKategorie.EINKAUF),
            Map.entry("EINKAUF_LIEFERUNG_NACHFRAGE", EmailTextTemplateKategorie.EINKAUF)
    );

    private EmailTextTemplateKategorien() {
        // utility
    }

    /**
     * Liefert die Kategorie für einen bekannten Dokumenttyp; unbekannte Typen
     * landen in {@link EmailTextTemplateKategorie#SYSTEM}, damit sie im UI
     * sichtbar bleiben und der Nutzer sie umkategorisieren kann.
     */
    public static EmailTextTemplateKategorie kategorieFuer(String dokumentTyp) {
        if (dokumentTyp == null) {
            return EmailTextTemplateKategorie.SYSTEM;
        }
        return ZUORDNUNG.getOrDefault(dokumentTyp.trim().toUpperCase(),
                EmailTextTemplateKategorie.SYSTEM);
    }
}
