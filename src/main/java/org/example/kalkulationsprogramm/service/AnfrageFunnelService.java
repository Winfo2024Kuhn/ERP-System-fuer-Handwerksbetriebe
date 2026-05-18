package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.example.kalkulationsprogramm.repository.AnfrageNotizRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verarbeitet Funnel-Anfragen von der öffentlichen Marketing-Webseite.
 * <p>
 * Eingang läuft S2S vom Webseiten-Server über Cloudflare-Tunnel (+ Access
 * Service Token). Es wird ein Kunde (oder ein passender Bestandskunde) und
 * eine Anfrage angelegt. Bilder + Funnel-Text landen in einer
 * {@code AnfrageNotiz} mit dem System-Mitarbeiter „Webseite" als Ersteller –
 * wird die Anfrage später in ein Projekt umgewandelt, transferiert das System
 * Notiz + Bilder automatisch ins Bautagebuch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnfrageFunnelService {

    /**
     * Login-Token des System-Mitarbeiters „Webseite". Stabil per Flyway-Migration
     * V221 angelegt.
     */
    public static final String SYSTEM_MITARBEITER_TOKEN = "__SYSTEM_FUNNEL__";

    private static final DateTimeFormatter DATUM_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATUM_ZEIT_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final KundeRepository kundeRepository;
    private final AnfrageRepository anfrageRepository;
    private final AnfrageNotizRepository anfrageNotizRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final KundennummerService kundennummerService;
    private final DateiSpeicherService dateiSpeicherService;
    private final AnfrageFunnelSpamFilterService spamFilterService;
    private final AnfrageBestaetigungVersandService anfrageBestaetigungVersandService;
    private final WebPushService webPushService;
    private final TaskExecutor taskExecutor;

    @Transactional
    public Anfrage verarbeiteFunnelAnfrage(AnfrageFunnelRequestDto dto, List<MultipartFile> bilder) {
        Mitarbeiter systemMitarbeiter = mitarbeiterRepository.findByLoginToken(SYSTEM_MITARBEITER_TOKEN)
                .orElseThrow(() -> new IllegalStateException(
                        "System-Mitarbeiter 'Webseite' nicht gefunden. Migration V221 ausgeführt?"));

        String email = normalizeEmail(dto.getEmail());

        Kunde kunde = findeOderErstelleKunde(dto, email);

        Anfrage anfrage = erstelleAnfrage(dto, kunde, email);
        anfrage = anfrageRepository.save(anfrage);

        AnfrageNotiz notiz = erstelleNotiz(dto, anfrage, systemMitarbeiter, bilder);
        anfrageNotizRepository.save(notiz);

        log.info("Funnel-Anfrage angelegt: anfrageId={}, kundeId={}, bilder={}",
                anfrage.getId(), kunde.getId(), bilder != null ? bilder.size() : 0);

        // Spam-Check asynchron nach DB-Commit — blockiert die Website-Antwort nicht.
        // Erkannte Spam-Anfragen werden geloggt (anfrageId) und können manuell
        // gelöscht werden. Der Check läuft nur, wenn Gemini konfiguriert ist;
        // ein Ausfall hat keinen Einfluss auf die Persistenz.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final long anfrageId = anfrage.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(() -> pruefSpamAsync(anfrageId, dto));
                }
            });
        } else {
            log.warn("Spam-Check übersprungen — keine aktive TX-Synchronisation für anfrageId={}",
                    anfrage.getId());
        }

        // Bestaetigungsmail an den Lead — fire & forget. Der Service schluckt
        // alle Exceptions, sodass ein SMTP-Ausfall die Funnel-Persistenz nicht
        // rollbacked. Tonalitaet/Inhalt sind ueber die DB-Vorlage
        // "WEBSITE_ANFRAGE_BESTAETIGUNG" im UI Kommunikation → E-Mail-Textvorlagen
        // editierbar.
        anfrageBestaetigungVersandService.versendeBestaetigung(
                anfrage, dto.getVorname(), dto.getNachname(), dto.getNachricht());

        // Sperrbildschirm-Push aufs Handy fuer alle Mitarbeiter, deren Abteilung
        // darfWebseitenAnfragenPushen=true hat. Fire & forget: Push-Probleme
        // duerfen die Funnel-Persistenz nie blockieren. Klick auf den Push
        // oeffnet die Mobile-PWA-Anfragenseite (react-zeiterfassung/AnfragenPage).
        try {
            String kundenName = (dto.getVorname() == null ? "" : dto.getVorname().trim())
                    + " " + (dto.getNachname() == null ? "" : dto.getNachname().trim());
            kundenName = kundenName.trim();
            if (kundenName.isEmpty()) kundenName = "Unbekannt";
            String body = anfrage.getBauvorhaben() == null || anfrage.getBauvorhaben().isBlank()
                    ? "Neue Anfrage über die Webseite"
                    : anfrage.getBauvorhaben();
            // Query-Param "id" passt zu AnfragenPage.tsx, die per searchParams.get('id')
            // die Anfrage in der Liste auto-selektiert.
            webPushService.notifyWebseitenAnfrage(
                    "Neue Anfrage: " + kundenName,
                    body,
                    "/zeiterfassung/anfragen?id=" + anfrage.getId());
        } catch (Exception e) {
            // Push darf den Funnel nie blockieren — aber wir wollen wissen, wenn
            // er kaputt geht (z.B. Lazy-Init, RuntimeException aus Mitarbeiter-Lookup).
            log.warn("Sperrbildschirm-Push fuer anfrageId={} fehlgeschlagen: {}",
                    anfrage.getId(), e.getMessage());
        }

        return anfrage;
    }

    private Kunde findeOderErstelleKunde(AnfrageFunnelRequestDto dto, String email) {
        List<Kunde> bestand = kundeRepository.findByKundenEmailIgnoreCase(email);
        if (!bestand.isEmpty()) {
            Kunde existing = bestand.get(0);
            if (!StringUtils.hasText(existing.getTelefon()) && StringUtils.hasText(dto.getTelefon())) {
                existing.setTelefon(dto.getTelefon().trim());
            }
            return kundeRepository.save(existing);
        }

        Kunde neu = new Kunde();
        neu.setKundennummer(kundennummerService.reserviereNaechsteKundennummer());
        neu.setName((dto.getVorname().trim() + " " + dto.getNachname().trim()).trim());
        neu.setAnsprechspartner(dto.getVorname().trim() + " " + dto.getNachname().trim());
        if (StringUtils.hasText(dto.getTelefon())) {
            neu.setTelefon(dto.getTelefon().trim());
        }
        AdressTeile rechnungsadresse = AdressTeile.parse(rechnungsAnschriftRoh(dto));
        neu.setStrasse(rechnungsadresse.strasse);
        neu.setPlz(rechnungsadresse.plz);
        neu.setOrt(rechnungsadresse.ort);

        List<String> emails = new ArrayList<>();
        emails.add(email);
        neu.setKundenEmails(emails);

        return kundeRepository.save(neu);
    }

    /**
     * Liefert die Rechnungs-/Hauptadresse für den Kunden – entweder die explizit
     * angegebene Rechnungsadresse oder die Projekt-Anschrift, wenn der Kunde
     * im Funnel „Rechnungsadresse = Projektadresse" angekreuzt hat.
     */
    private String rechnungsAnschriftRoh(AnfrageFunnelRequestDto dto) {
        if (dto.isRechnungsAnschriftGleichProjekt()) {
            return dto.getProjektAnschrift();
        }
        return dto.getRechnungsAnschrift();
    }

    private Anfrage erstelleAnfrage(AnfrageFunnelRequestDto dto, Kunde kunde, String email) {
        Anfrage anfrage = new Anfrage();
        anfrage.setKunde(kunde);
        anfrage.setBauvorhaben(buildBauvorhaben(dto));
        anfrage.setKurzbeschreibung(buildKurzbeschreibung(dto));
        anfrage.setAnlegedatum(LocalDate.now());

        AdressTeile adresse = AdressTeile.parse(dto.getProjektAnschrift());
        anfrage.setProjektStrasse(adresse.strasse);
        anfrage.setProjektPlz(adresse.plz);
        anfrage.setProjektOrt(adresse.ort);

        List<String> anfrageEmails = new ArrayList<>();
        anfrageEmails.add(email);
        anfrage.setKundenEmails(anfrageEmails);

        return anfrage;
    }

    private AnfrageNotiz erstelleNotiz(AnfrageFunnelRequestDto dto, Anfrage anfrage,
                                       Mitarbeiter systemMitarbeiter, List<MultipartFile> bilder) {
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setAnfrage(anfrage);
        notiz.setMitarbeiter(systemMitarbeiter);
        notiz.setNotiz(buildNotizText(dto));
        notiz.setMobileSichtbar(true);
        notiz.setNurFuerErsteller(false);

        if (bilder != null) {
            for (MultipartFile bild : bilder) {
                if (bild == null || bild.isEmpty()) {
                    continue;
                }
                String url = dateiSpeicherService.speichereBild(bild);
                String gespeicherterName = url.substring(url.lastIndexOf('/') + 1);

                AnfrageNotizBild notizBild = new AnfrageNotizBild();
                notizBild.setNotiz(notiz);
                notizBild.setGespeicherterDateiname(gespeicherterName);
                notizBild.setOriginalDateiname(bild.getOriginalFilename());
                notizBild.setDateityp(bild.getContentType());
                notiz.getBilder().add(notizBild);
            }
        }
        return notiz;
    }

    private String buildBauvorhaben(AnfrageFunnelRequestDto dto) {
        String service = dto.getServiceTyp() == null ? "" : dto.getServiceTyp().trim();
        String projektarten = joinProjektarten(dto.getProjektarten());
        if (projektarten.isEmpty()) {
            return service;
        }
        if (service.isEmpty()) {
            return projektarten;
        }
        return service + " - " + projektarten;
    }

    private String buildKurzbeschreibung(AnfrageFunnelRequestDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildBauvorhaben(dto));
        if (StringUtils.hasText(dto.getNachricht())) {
            sb.append("\n\n").append(dto.getNachricht().trim());
        }
        String text = sb.toString();
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private String buildNotizText(AnfrageFunnelRequestDto dto) {
        LocalDateTime jetzt = LocalDateTime.now();
        StringBuilder sb = new StringBuilder();
        sb.append("Anfrage über Webseite vom ").append(DATUM_DE.format(jetzt.toLocalDate())).append("\n\n");

        sb.append("Kontakt:\n");
        sb.append("- ").append(dto.getVorname().trim()).append(" ").append(dto.getNachname().trim()).append("\n");
        sb.append("- ").append(normalizeEmail(dto.getEmail())).append("\n");
        if (StringUtils.hasText(dto.getTelefon())) {
            sb.append("- Tel.: ").append(dto.getTelefon().trim()).append("\n");
        }
        if (StringUtils.hasText(dto.getProjektAnschrift())) {
            sb.append("- Projekt-Anschrift: ").append(dto.getProjektAnschrift().trim()).append("\n");
        }
        if (dto.isRechnungsAnschriftGleichProjekt()) {
            sb.append("- Rechnungs-Anschrift: identisch mit Projekt-Anschrift\n");
        } else if (StringUtils.hasText(dto.getRechnungsAnschrift())) {
            sb.append("- Rechnungs-Anschrift: ").append(dto.getRechnungsAnschrift().trim()).append("\n");
        }

        sb.append("\nService: ").append(dto.getServiceTyp().trim()).append("\n");
        String projektarten = joinProjektarten(dto.getProjektarten());
        if (!projektarten.isEmpty()) {
            sb.append("Projektarten: ").append(projektarten).append("\n");
        }

        sb.append("\nNachricht:\n").append(dto.getNachricht().trim()).append("\n");

        sb.append("\nDatenschutz akzeptiert: Ja (am ").append(DATUM_ZEIT_DE.format(jetzt));
        if (StringUtils.hasText(dto.getConsentIp())) {
            sb.append(", IP: ").append(dto.getConsentIp().trim());
        }
        if (StringUtils.hasText(dto.getDatenschutzVersion())) {
            sb.append(", Version: ").append(dto.getDatenschutzVersion().trim());
        }
        sb.append(")");

        String text = sb.toString();
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }

    private String joinProjektarten(List<String> projektarten) {
        if (projektarten == null || projektarten.isEmpty()) {
            return "";
        }
        return projektarten.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.GERMAN);
    }

    /**
     * Trennt eine freie Anschrift-Eingabe wie „Kleistraße 11, 97072 Würzburg"
     * in Straße / PLZ / Ort. Best-Effort, falls das Format abweicht, landet
     * alles in {@code strasse}.
     */
    private record AdressTeile(String strasse, String plz, String ort) {
        static AdressTeile parse(String anschrift) {
            if (!StringUtils.hasText(anschrift)) {
                return new AdressTeile(null, null, null);
            }
            String[] teile = anschrift.split(",", 2);
            String strasse = teile[0].trim();
            if (teile.length < 2) {
                return new AdressTeile(emptyToNull(strasse), null, null);
            }
            String rest = teile[1].trim();
            int spaceIdx = rest.indexOf(' ');
            if (spaceIdx < 0) {
                return new AdressTeile(emptyToNull(strasse), null, emptyToNull(rest));
            }
            String maybePlz = rest.substring(0, spaceIdx).trim();
            String maybeOrt = rest.substring(spaceIdx + 1).trim();
            if (maybePlz.matches("\\d{4,5}")) {
                return new AdressTeile(emptyToNull(strasse), emptyToNull(maybePlz), emptyToNull(maybeOrt));
            }
            return new AdressTeile(emptyToNull(strasse), null, emptyToNull(rest));
        }

        private static String emptyToNull(String value) {
            return StringUtils.hasText(value) ? value : null;
        }
    }

    private void pruefSpamAsync(long anfrageId, AnfrageFunnelRequestDto dto) {
        try {
            AnfrageFunnelSpamFilterService.Result spamCheck = spamFilterService.pruefe(dto);
            if (spamCheck.spam()) {
                log.warn("Spam-Nachprüfung: anfrageId={} als Spam erkannt – {}",
                        anfrageId, spamCheck.grund());
            }
        } catch (Exception e) {
            log.warn("Async-Spam-Check für anfrageId={} fehlgeschlagen: {}", anfrageId, e.getMessage());
        }
    }
}
