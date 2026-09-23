package org.example.kalkulationsprogramm.domain;

/**
 * Fachliche Gruppierung der E-Mail-Textvorlagen im UI. Erlaubt es dem Nutzer
 * auf einen Blick zu sehen, welche Vorlage wofür gedacht ist — statt einer
 * flachen Liste, in der "WEBSITE_ANFRAGE_BESTAETIGUNG" optisch neben einer
 * Rechnung steht.
 *
 * <p>Reihenfolge der Konstanten = Reihenfolge in der UI (Dokumente zuerst,
 * danach Mahnstufen, dann Webseiten-Automatik, zuletzt sonstige System-Mails).</p>
 */
public enum EmailTextTemplateKategorie {
    DOKUMENT("Dokumente"),
    MAHNWESEN("Mahnwesen"),
    WEBSITE("Webseite & Anfragen"),
    EINKAUF("Einkauf"),
    SYSTEM("System");

    private final String label;

    EmailTextTemplateKategorie(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
