package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturDto;
import org.example.kalkulationsprogramm.dto.Email.EmailSignaturSpeichernRequest;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.util.SignaturHtmlPruefung;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Wer sieht und bearbeitet welche E-Mail-Signatur.
 *
 * <p>Jede Signatur gehört einem Benutzer. Benutzer sehen und bearbeiten nur ihre eigenen,
 * Admins ({@code ROLE_ADMIN}) sehen alle – auch die ohne Besitzer – und dürfen den Besitzer
 * festlegen. Fremde Signaturen gelten für Benutzer als nicht vorhanden (404), damit nicht
 * einmal ihre IDs bestätigt werden. Der Benutzer kommt ausschließlich aus der Anmeldung.</p>
 *
 * <p>Ausnahme: Die aktuelle Standard-Signatur eines Benutzers sieht er immer – auch wenn sie
 * ihm nicht gehört (früher hat der Admin sie in der Benutzerverwaltung zugewiesen). Sie hängt
 * weiter an seinen Mails, ändern oder löschen kann er sie aber nicht ({@code nurLesen}).</p>
 *
 * <p>Firmen-Vorlagen legt der Admin fest; jeder Benutzer sieht sie als Ausgangspunkt für seine
 * eigene Signatur. Neue Signaturen von Benutzern entstehen aus einer Firmen-Vorlage, sobald es
 * eine gibt. Bestehende Signaturen bleiben, wie sie sind.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailSignaturVerwaltungService {

    private final EmailSignatureRepository signaturRepository;
    private final FrontendUserProfileRepository profilRepository;
    private final OutOfOfficeScheduleRepository abwesenheitRepository;
    private final EmailSignatureService signaturService;

    private static final ObjectMapper JSON = JsonMapper.builderWithJackson2Defaults().build();

    /**
     * Angaben zur Person im Formularzustand des Studios. Die Telefonnummer gehört nicht dazu –
     * sie ist in der Vorlage die Firmennummer, die Mitarbeiter als Vorbelegung übernehmen.
     */
    private static final List<String> PERSOENLICHE_FELDER =
            List.of("name", "position", "abteilung", "mobil", "email", "fotoUrl");

    static final String MELDUNG_NUR_LESEN =
            "Diese Signatur hat der Betrieb für Sie festgelegt. Ändern kann sie nur ein Admin.";
    static final String MELDUNG_FIRMEN_VORLAGE_PFLICHT =
            "Bitte erstellen Sie Ihre Signatur aus der Firmen-Vorlage.";
    static final String MELDUNG_VORLAGE_OHNE_FORMULAR =
            "Firmen-Vorlage kann nur eine Signatur werden, die mit dem Formular gestaltet wurde.";
    static final String MELDUNG_FIRMEN_VORLAGE_FEHLT =
            "Die gewählte Firmen-Vorlage gibt es nicht mehr.";

    /** Angemeldeter Benutzer; {@code profilId == null}, wenn kein Benutzerprofil dahintersteht. */
    public record Benutzer(Long profilId, boolean admin) {
        public static final Benutzer KEINER = new Benutzer(null, false);
    }

    /** Ermittelt den Benutzer aus der Anmeldung – nie aus Angaben des Clients. */
    @Transactional(readOnly = true)
    public Benutzer benutzer(Authentication authentication) {
        return new Benutzer(signaturService.angemeldeteProfilId(authentication).orElse(null),
                EmailSignatureService.istAdmin(authentication));
    }

    /**
     * Admin: alle Signaturen; Benutzer: eigene, die zuletzt geänderte zuerst – davor seine
     * Standard-Signatur, falls sie ihm nicht gehört.
     */
    @Transactional(readOnly = true)
    public List<EmailSignaturDto> liste(Benutzer benutzer) {
        Long standardId = standardId(benutzer);
        List<EmailSignature> signaturen;
        if (benutzer.admin()) {
            signaturen = signaturRepository.findAllMitBesitzer();
        } else if (benutzer.profilId() != null) {
            signaturen = new ArrayList<>(signaturRepository.findAllByBesitzerIdOrderByUpdatedAtDesc(benutzer.profilId()));
            if (standardId != null && signaturen.stream().noneMatch(s -> standardId.equals(s.getId()))) {
                signaturRepository.findById(standardId).ifPresent(s -> signaturen.add(0, s));
            }
        } else {
            signaturen = List.of();
        }
        // Die Formulardaten (mit eingebetteten Bildern bis zu mehreren MB) braucht erst das Studio,
        // das die Signatur einzeln lädt – die Liste bleibt schlank.
        return signaturen.stream().map(s -> zuDto(s, benutzer, standardId, false)).toList();
    }

    /** Signatur, wenn der Benutzer sie sehen darf – sonst leer (auch wenn es sie gibt). */
    @Transactional(readOnly = true)
    public Optional<EmailSignature> sichtbare(Long id, Benutzer benutzer) {
        if (id == null) {
            return Optional.empty();
        }
        Long standardId = standardId(benutzer);
        return signaturRepository.findById(id).filter(s -> darfSehen(s, benutzer, standardId));
    }

    /**
     * Signatur, die der Benutzer ändern darf.
     *
     * @throws NoSuchElementException   wenn er sie nicht sehen darf
     * @throws IllegalArgumentException wenn er sie nur sehen, aber nicht ändern darf
     */
    @Transactional(readOnly = true)
    public EmailSignature bearbeitbare(Long id, Benutzer benutzer) {
        EmailSignature signatur = sichtbare(id, benutzer)
                .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
        if (!darfAendern(signatur, benutzer)) {
            throw new IllegalArgumentException(MELDUNG_NUR_LESEN);
        }
        return signatur;
    }

    @Transactional(readOnly = true)
    public Optional<EmailSignaturDto> laden(Long id, Benutzer benutzer) {
        return sichtbare(id, benutzer).map(s -> zuDto(s, benutzer, standardId(benutzer)));
    }

    /**
     * Alle Firmen-Vorlagen mit Formulardaten, alphabetisch. Jeder angemeldete Benutzer sieht sie;
     * wem eine Vorlage gehört, erfahren nur Admins.
     */
    @Transactional(readOnly = true)
    public List<EmailSignaturDto> firmenVorlagen(Benutzer benutzer) {
        List<EmailSignature> vorlagen = signaturRepository.findAllByFirmenVorlageTrueOrderByNameAsc();
        if (benutzer.admin()) {
            Long standardId = standardId(benutzer);
            return vorlagen.stream().map(s -> zuDto(s, benutzer, standardId)).toList();
        }
        return vorlagen.stream().map(EmailSignaturVerwaltungService::alsVorlageFuerBenutzer).toList();
    }

    /**
     * Macht die Signatur zur Firmen-Vorlage oder nimmt sie zurück (nur Admin, siehe SecurityConfig).
     *
     * @throws IllegalArgumentException wenn die Signatur keine Formulardaten hat
     */
    @Transactional
    public EmailSignaturDto alsFirmenVorlage(Long id, boolean firmenVorlage, Benutzer benutzer) {
        EmailSignature signatur = sichtbare(id, benutzer)
                .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
        if (firmenVorlage && (signatur.getVorlageDaten() == null || signatur.getVorlageDaten().isBlank())) {
            // Mitarbeiter übernehmen das Design aus den Formulardaten – reines HTML taugt dafür nicht.
            throw new IllegalArgumentException(MELDUNG_VORLAGE_OHNE_FORMULAR);
        }
        signatur.setFirmenVorlage(firmenVorlage);
        if (firmenVorlage) {
            // Eine Vorlage stammt selbst aus keiner anderen.
            signatur.setFirmenVorlageId(null);
        }
        return zuDto(signaturRepository.save(signatur), benutzer, standardId(benutzer));
    }

    /**
     * Standard-Signatur zum Vorbelegen einer neuen Mail. Benutzer bekommen immer ihre eigene,
     * egal was der Client mitschickt; nur Admins dürfen nach der eines anderen Benutzers fragen.
     */
    @Transactional(readOnly = true)
    public Optional<EmailSignaturDto> standardSignatur(Benutzer benutzer, Long frontendUserId, String frontendUserName) {
        boolean fremdeAbfrage = benutzer.admin() && (frontendUserId != null || frontendUserName != null);
        Long profilId = fremdeAbfrage ? frontendUserId : benutzer.profilId();
        String profilName = fremdeAbfrage ? frontendUserName : null;
        if (profilId == null && profilName == null) {
            return Optional.empty();
        }
        return signaturService.getDefaultForFrontendUser(profilId, profilName)
                .map(s -> zuDto(s, benutzer, s.getId()));
    }

    /** Die aktuelle System-Signatur, wenn der Benutzer sie sehen darf. */
    @Transactional(readOnly = true)
    public Optional<EmailSignaturDto> systemSignatur(Benutzer benutzer) {
        Long standardId = standardId(benutzer);
        return signaturRepository.findFirstByIsSystemDefaultTrue()
                .filter(s -> darfSehen(s, benutzer, standardId))
                .map(s -> zuDto(s, benutzer, standardId));
    }

    /**
     * Legt eine Signatur an oder ändert sie.
     *
     * @throws IllegalArgumentException bei ungültigen Eingaben (Meldung für den Benutzer)
     * @throws NoSuchElementException   wenn die zu ändernde Signatur nicht sichtbar ist
     */
    @Transactional
    public EmailSignaturDto speichern(EmailSignaturSpeichernRequest request, Benutzer benutzer) {
        if (request == null) {
            throw new IllegalArgumentException("Es wurden keine Daten übermittelt.");
        }
        String name = SignaturHtmlPruefung.pruefeName(request.name());
        String html = SignaturHtmlPruefung.bereinige(request.html());
        String vorlageDaten = SignaturHtmlPruefung.pruefeVorlageDaten(request.vorlageDaten());

        EmailSignature signatur;
        if (request.id() != null) {
            signatur = bearbeitbare(request.id(), benutzer);
            if (signatur.isSystemDefault() && !benutzer.admin()) {
                // Die System-Signatur steht unter allen automatischen Mails an Kunden.
                throw new IllegalArgumentException("Die System-Signatur kann nur ein Admin ändern.");
            }
        } else {
            if (!benutzer.admin() && benutzer.profilId() == null) {
                throw new IllegalArgumentException("Zu Ihrer Anmeldung gibt es kein Benutzerprofil.");
            }
            if (!benutzer.admin() && request.firmenVorlageId() == null && signaturRepository.existsByFirmenVorlageTrue()) {
                // Das Design legt der Betrieb fest; bestehende Signaturen bleiben davon unberührt.
                throw new IllegalArgumentException(MELDUNG_FIRMEN_VORLAGE_PFLICHT);
            }
            signatur = new EmailSignature();
            signatur.setBesitzer(benutzer.profilId() == null ? null : profil(benutzer.profilId()));
        }
        signatur.setFirmenVorlageId(signatur.isFirmenVorlage() ? null : pruefeFirmenVorlage(request.firmenVorlageId(), signatur));
        if (benutzer.admin() && Boolean.TRUE.equals(request.besitzerFestlegen())) {
            FrontendUserProfile neuerBesitzer = request.besitzerId() == null ? null : profil(request.besitzerId());
            if (signatur.getId() != null && !gleicherBenutzer(signatur.getBesitzer(), neuerBesitzer)) {
                loeseStandardBeiAnderen(signatur.getId(), neuerBesitzer);
            }
            signatur.setBesitzer(neuerBesitzer);
        }
        signatur.setName(name);
        signatur.setHtml(html);
        signatur.setVorlageDaten(vorlageDaten);
        signatur = signaturRepository.save(signatur);

        if (Boolean.TRUE.equals(request.alsStandard())) {
            setzeStandard(signatur, benutzer);
        }
        return zuDto(signatur, benutzer, standardId(benutzer));
    }

    /** Macht die Signatur zur Standard-Signatur des angemeldeten Benutzers. */
    @Transactional
    public EmailSignaturDto alsStandard(Long id, Benutzer benutzer) {
        EmailSignature signatur = sichtbare(id, benutzer)
                .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
        setzeStandard(signatur, benutzer);
        return zuDto(signatur, benutzer, signatur.getId());
    }

    /** Der angemeldete Benutzer hat danach keine Standard-Signatur mehr. */
    @Transactional
    public void standardEntfernen(Benutzer benutzer) {
        if (benutzer.profilId() == null) {
            return;
        }
        profilRepository.findById(benutzer.profilId()).ifPresent(p -> {
            p.setDefaultSignature(null);
            profilRepository.save(p);
        });
    }

    /** ID einer bestehenden Firmen-Vorlage (nicht die Signatur selbst) oder {@code null}. */
    private Long pruefeFirmenVorlage(Long firmenVorlageId, EmailSignature signatur) {
        if (firmenVorlageId == null) {
            return null;
        }
        boolean gueltig = !firmenVorlageId.equals(signatur.getId())
                && signaturRepository.findById(firmenVorlageId).map(EmailSignature::isFirmenVorlage).orElse(false);
        if (!gueltig) {
            throw new IllegalArgumentException(MELDUNG_FIRMEN_VORLAGE_FEHLT);
        }
        return firmenVorlageId;
    }

    /** Markiert die Signatur als System-Signatur für automatische Mails (nur Admin, siehe SecurityConfig). */
    @Transactional
    public EmailSignaturDto alsSystemSignatur(Long id, Benutzer benutzer) {
        EmailSignature signatur = sichtbare(id, benutzer)
                .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
        return zuDto(signaturService.setSystemDefault(signatur.getId()), benutzer, standardId(benutzer));
    }

    /**
     * Löscht eine sichtbare Signatur. Vorher wird sie bei Benutzern (Standard-Signatur) und
     * Abwesenheitsnotizen gelöst – sonst blockieren die Fremdschlüssel das Löschen.
     *
     * @throws IllegalArgumentException bei der System-Signatur (ohne sie gingen automatische Mails ohne Gruß raus)
     */
    @Transactional
    public void loeschen(Long id, Benutzer benutzer) {
        EmailSignature signatur = bearbeitbare(id, benutzer);
        if (signatur.isSystemDefault()) {
            throw new IllegalArgumentException(
                    "Die System-Signatur kann nicht gelöscht werden. Bitte zuerst eine andere Signatur als System-Signatur festlegen.");
        }
        profilRepository.entferneStandardSignatur(signatur.getId());
        abwesenheitRepository.entferneSignatur(signatur.getId());
        signaturService.delete(signatur.getId());
    }

    /** Nach einem Besitzerwechsel ist die Signatur nur noch beim neuen Besitzer Standard-Signatur. */
    private void loeseStandardBeiAnderen(Long signaturId, FrontendUserProfile neuerBesitzer) {
        for (FrontendUserProfile p : profilRepository.findByDefaultSignatureId(signaturId)) {
            if (!gleicherBenutzer(p, neuerBesitzer)) {
                p.setDefaultSignature(null);
                profilRepository.save(p);
            }
        }
    }

    private static boolean gleicherBenutzer(FrontendUserProfile a, FrontendUserProfile b) {
        Long idA = a == null ? null : a.getId();
        Long idB = b == null ? null : b.getId();
        return Objects.equals(idA, idB);
    }

    private void setzeStandard(EmailSignature signatur, Benutzer benutzer) {
        if (benutzer.profilId() == null) {
            throw new IllegalArgumentException("Zu Ihrer Anmeldung gibt es kein Benutzerprofil.");
        }
        FrontendUserProfile profil = profilRepository.findById(benutzer.profilId())
                .orElseThrow(() -> new IllegalArgumentException("Zu Ihrer Anmeldung gibt es kein Benutzerprofil."));
        profil.setDefaultSignature(signatur);
        profilRepository.save(profil);
    }

    private FrontendUserProfile profil(Long id) {
        return profilRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Den gewählten Benutzer gibt es nicht."));
    }

    private Long standardId(Benutzer benutzer) {
        if (benutzer.profilId() == null) {
            return null;
        }
        return profilRepository.findById(benutzer.profilId())
                .map(FrontendUserProfile::getDefaultSignature)
                .map(EmailSignature::getId)
                .orElse(null);
    }

    /** Admins sehen alles, Benutzer ihre eigenen und ihre aktuelle Standard-Signatur. */
    static boolean darfSehen(EmailSignature signatur, Benutzer benutzer, Long standardId) {
        return benutzer.admin() || istBesitzer(signatur, benutzer)
                || (benutzer.profilId() != null && signatur.getId() != null && signatur.getId().equals(standardId));
    }

    /**
     * Ändern und löschen dürfen Admins und der Besitzer – eine Firmen-Vorlage aber nur Admins,
     * denn ihr Design gilt für den ganzen Betrieb.
     */
    static boolean darfAendern(EmailSignature signatur, Benutzer benutzer) {
        return benutzer.admin() || (istBesitzer(signatur, benutzer) && !signatur.isFirmenVorlage());
    }

    private static boolean istBesitzer(EmailSignature signatur, Benutzer benutzer) {
        return benutzer.profilId() != null && signatur.getBesitzer() != null
                && Objects.equals(signatur.getBesitzer().getId(), benutzer.profilId());
    }

    /**
     * Firmen-Vorlage, wie ein Benutzer ohne Admin-Rechte sie sieht: nur das Design. Besitzer,
     * fertiges HTML, alte Bilder und die persönlichen Angaben dessen, der sie angelegt hat,
     * bleiben auf dem Server.
     */
    private static EmailSignaturDto alsVorlageFuerBenutzer(EmailSignature s) {
        return new EmailSignaturDto(s.getId(), s.getName(), "", ohnePersoenlicheAngaben(s.getVorlageDaten()), false,
                s.isSystemDefault(), null, null, false, s.getCreatedAt(), s.getUpdatedAt(), List.of(),
                true, null, true);
    }

    /** Leert die persönlichen Felder im Formularzustand; {@code null}, wenn er kein JSON-Objekt ist. */
    static String ohnePersoenlicheAngaben(String vorlageDaten) {
        if (vorlageDaten == null || vorlageDaten.isBlank()) {
            return null;
        }
        try {
            JsonNode knoten = JSON.readTree(vorlageDaten);
            if (!(knoten instanceof ObjectNode daten)) {
                return null;
            }
            PERSOENLICHE_FELDER.forEach(feld -> daten.put(feld, ""));
            return JSON.writeValueAsString(daten);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static List<EmailSignaturDto.Bild> bilder(EmailSignature s) {
        return Optional.ofNullable(s.getImages()).orElse(List.of()).stream()
                .map(i -> new EmailSignaturDto.Bild(i.getId(), i.getCid(), i.getOriginalFilename(),
                        i.getContentType(), i.getSizeBytes()))
                .toList();
    }

    private EmailSignaturDto zuDto(EmailSignature s, Benutzer benutzer, Long standardId) {
        return zuDto(s, benutzer, standardId, true);
    }

    private EmailSignaturDto zuDto(EmailSignature s, Benutzer benutzer, Long standardId, boolean mitVorlageDaten) {
        FrontendUserProfile besitzer = s.getBesitzer();
        Long besitzerId = besitzer == null ? null : besitzer.getId();
        return new EmailSignaturDto(
                s.getId(),
                s.getName(),
                s.getHtml(),
                mitVorlageDaten ? s.getVorlageDaten() : null,
                s.getId() != null && Objects.equals(s.getId(), standardId),
                s.isSystemDefault(),
                besitzerId,
                besitzer == null ? null : besitzer.getDisplayName(),
                besitzerId != null && Objects.equals(besitzerId, benutzer.profilId()),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                bilder(s),
                s.isFirmenVorlage(),
                s.getFirmenVorlageId(),
                !darfAendern(s, benutzer));
    }
}
