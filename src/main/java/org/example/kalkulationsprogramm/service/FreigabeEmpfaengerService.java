package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hält fest, an wen eine Mail mit Freigabe-Link tatsächlich ging.
 *
 * <p>Die Freigabe entsteht schon beim Erzeugen der Mail-Vorlage – mit der Adresse, die der
 * Versand-Dialog vorschlägt. Ändert das Büro den Empfänger im Dialog (anderer Ansprechpartner,
 * Architekt) oder hatte der Kunde noch gar keine Adresse, stünde in der Freigabe die falsche
 * oder keine. Daran hängen aber der Bestätigungscode ({@link FreigabeCodeService}) und die
 * automatische Auftragsbestätigung. Deshalb liest der Mail-Versand nach dem Senden die
 * Freigabe-Links aus dem Text und schreibt die echten Empfänger (An und CC) hierher.</p>
 *
 * <p>Nur einmal je Freigabe, beim ersten echten Versand: Leitet das Büro die Angebots-Mail
 * später weiter (Subunternehmer, Kollege), darf der neue Empfänger nicht den Code bekommen.
 * Der Aufrufer übergibt deshalb nur Geschäftsdokument-Mails, die keine Weiterleitung sind, und
 * {@link DokumentFreigabe#isKundeEmailAusVersand()} sperrt jede weitere Übernahme. Ein neu
 * verschicktes Angebot bekommt ohnehin eine neue Freigabe.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FreigabeEmpfaengerService
{
    /** Länge der Spalte {@code dokument_freigabe.kunde_email}. */
    static final int MAX_LAENGE = 255;

    private final DokumentFreigabeRepository repository;

    @Value("${freigabe.public-base-url:}")
    private String publicBaseUrl;

    /**
     * Übernimmt die Empfänger für jede noch offene Freigabe, deren Link im Mail-Text steht.
     * Läuft in eigener Transaktion: Ein Fehler hier darf den bereits erfolgten Versand nicht
     * als gescheitert erscheinen lassen – der Aufrufer fängt ihn ab und loggt nur.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void uebernehmeVersandEmpfaenger(String htmlBody, Collection<String> empfaenger)
    {
        String adressen = verbinde(empfaenger);
        if (adressen.isEmpty())
        {
            return;
        }
        for (String uuid : freigabeUuidsIn(htmlBody))
        {
            // Mit Zeilensperre: Läuft gleichzeitig eine Code-Anforderung, überschreibt das
            // Zurückschreiben der Zeile sonst deren Zähler.
            repository.findByUuidZumSperren(uuid)
                    .filter(f -> f.getStatus() == FreigabeStatus.PENDING)
                    .filter(f -> !f.isKundeEmailAusVersand())
                    .ifPresent(f -> setzeEmpfaenger(f, adressen));
        }
    }

    private void setzeEmpfaenger(DokumentFreigabe freigabe, String adressen)
    {
        freigabe.setKundeEmail(adressen);
        freigabe.setKundeEmailAusVersand(true);
        repository.save(freigabe);
        // Ohne Adressen geloggt (DSGVO) – die UUID reicht zum Wiederfinden.
        log.info("Empfänger der Freigabe {} aus dem tatsächlichen Versand übernommen", freigabe.getUuid());
    }

    /** UUIDs aller Links auf die eigene Freigabe-Seite im Mail-Text, ohne Dubletten. */
    Set<String> freigabeUuidsIn(String htmlBody)
    {
        Set<String> uuids = new LinkedHashSet<>();
        if (htmlBody == null || publicBaseUrl == null || publicBaseUrl.isBlank())
        {
            return uuids;
        }
        String basis = publicBaseUrl.strip().replaceAll("/+$", "");
        Matcher m = Pattern.compile(Pattern.quote(basis) + "/freigabe/([0-9a-fA-F-]{36})").matcher(htmlBody);
        while (m.find())
        {
            uuids.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return uuids;
    }

    /**
     * Verbindet die Adressen mit ", " – ohne leere Einträge und Dubletten. Passt nicht alles
     * in die Spalte, kommen nur so viele vollständige Adressen hinein, wie Platz haben.
     */
    static String verbinde(Collection<String> empfaenger)
    {
        Set<String> eindeutig = new LinkedHashSet<>();
        if (empfaenger != null)
        {
            for (String eintrag : empfaenger)
            {
                if (eintrag == null) continue;
                for (String adresse : eintrag.split("[,;]"))
                {
                    String sauber = adresse.strip();
                    if (!sauber.isEmpty()) eindeutig.add(sauber);
                }
            }
        }
        StringBuilder ergebnis = new StringBuilder();
        for (String adresse : eindeutig)
        {
            int neueLaenge = ergebnis.length() + (ergebnis.isEmpty() ? 0 : 2) + adresse.length();
            if (neueLaenge > MAX_LAENGE) break;
            if (!ergebnis.isEmpty()) ergebnis.append(", ");
            ergebnis.append(adresse);
        }
        return ergebnis.toString();
    }
}
