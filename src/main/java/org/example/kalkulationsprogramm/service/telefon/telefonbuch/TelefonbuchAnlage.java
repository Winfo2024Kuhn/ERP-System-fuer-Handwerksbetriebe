package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;

import java.util.Set;

/**
 * Schreibzugriff auf ein Telefonbuch der Telefonanlage. Das ERP verwaltet nur
 * sein eigenes Telefonbuch (über den Namen gefunden) – andere Telefonbücher
 * werden nie geändert. Der Rest des ERP kennt keine FRITZ!Box-Einzelheiten.
 * Alle Methoden werfen {@link TelefonAnlageException} mit verständlichem Grund.
 */
public interface TelefonbuchAnlage {

    /** Telefonbuch in der Anlage. {@code neu} = angelegt oder (wieder)gefunden, aber nicht unter der bekannten ID. */
    record Telefonbuch(String id, boolean neu) {
    }

    /**
     * Sucht das Telefonbuch {@code name} – zuerst unter {@code bekannteId}, sonst über den Namen –
     * und legt es an, wenn es fehlt.
     */
    Telefonbuch stelleTelefonbuchBereit(TelefonZugang zugang, String name, String bekannteId);

    /** Kennungen aller Einträge, die gerade in diesem Telefonbuch stehen. */
    Set<String> ladeEintragsIds(TelefonZugang zugang, String telefonbuchId);

    /**
     * Legt einen Eintrag an ({@code eintragsId == null}) oder überschreibt ihn.
     *
     * @return Kennung des Eintrags in der Anlage
     */
    String speichereEintrag(TelefonZugang zugang, String telefonbuchId, TelefonbuchEintrag eintrag, String eintragsId);

    /** Löscht einen Eintrag. Ist er schon weg, passiert nichts. */
    void loescheEintrag(TelefonZugang zugang, String telefonbuchId, String eintragsId);

    /**
     * Löscht das Telefonbuch {@code name} samt Einträgen – nur, wenn es wirklich so heißt:
     * zuerst unter {@code bekannteId} (am Namen bestätigt), sonst das Telefonbuch mit genau
     * diesem Namen. Ein anders benanntes Telefonbuch und das Haupttelefonbuch werden nie gelöscht.
     *
     * @return {@code true}, wenn ein Telefonbuch gelöscht wurde; {@code false}, wenn es keins (mehr) gab
     */
    boolean loescheTelefonbuch(TelefonZugang zugang, String name, String bekannteId);
}
