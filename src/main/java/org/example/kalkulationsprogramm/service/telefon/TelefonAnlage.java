package org.example.kalkulationsprogramm.service.telefon;

import java.util.List;

/**
 * Schnittstelle zur Telefonanlage. Der Rest des ERP kennt nur Anrufe und
 * Sprachnachrichten – nicht, ob dahinter eine FRITZ!Box oder etwas anderes steht.
 * Alle Methoden werfen {@link TelefonAnlageException} mit verständlichem Grund.
 */
public interface TelefonAnlage {

    /** Prüft die Verbindung und liefert eigene Rufnummern, Anrufbeantworter und Vorwahlen. */
    AnlagenInfo pruefeVerbindung(TelefonZugang zugang);

    /** Anrufe der letzten {@code tage} Tage (alle Rufnummern, ungefiltert). */
    List<AnlagenAnruf> ladeAnrufe(TelefonZugang zugang, int tage);

    /** Alle Nachrichten eines Anrufbeantworters. */
    List<AnlagenSprachnachricht> ladeSprachnachrichten(TelefonZugang zugang, int anrufbeantworter);

    /** Rohdaten der Aufnahme einer Nachricht. */
    byte[] ladeAudio(TelefonZugang zugang, AnlagenSprachnachricht nachricht);

    /** Namen der Telefone, die beim Anrufen aus dem ERP klingeln können (Softphone, DECT, Tischtelefon). */
    List<String> ladeTelefone(TelefonZugang zugang);

    /**
     * Lässt {@code telefon} klingeln; wird dort abgenommen, wählt die Anlage {@code nummer}.
     * Die Nummer ist bereits geprüft (nur Ziffern, siehe {@link TelefonWaehlService}).
     */
    void anrufen(TelefonZugang zugang, String telefon, String nummer);
}
