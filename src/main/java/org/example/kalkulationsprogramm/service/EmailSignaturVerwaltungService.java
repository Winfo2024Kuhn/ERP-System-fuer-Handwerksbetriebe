package org.example.kalkulationsprogramm.service;

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
 */
@Service
@RequiredArgsConstructor
public class EmailSignaturVerwaltungService {

    private final EmailSignatureRepository signaturRepository;
    private final FrontendUserProfileRepository profilRepository;
    private final OutOfOfficeScheduleRepository abwesenheitRepository;
    private final EmailSignatureService signaturService;

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

    /** Admin: alle Signaturen; Benutzer: nur eigene. Die zuletzt geänderte zuerst. */
    @Transactional(readOnly = true)
    public List<EmailSignaturDto> liste(Benutzer benutzer) {
        List<EmailSignature> signaturen;
        if (benutzer.admin()) {
            signaturen = signaturRepository.findAllMitBesitzer();
        } else if (benutzer.profilId() != null) {
            signaturen = signaturRepository.findAllByBesitzerIdOrderByUpdatedAtDesc(benutzer.profilId());
        } else {
            signaturen = List.of();
        }
        Long standardId = standardId(benutzer);
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
        return signaturRepository.findById(id).filter(s -> darfSehen(s, benutzer));
    }

    @Transactional(readOnly = true)
    public Optional<EmailSignaturDto> laden(Long id, Benutzer benutzer) {
        return sichtbare(id, benutzer).map(s -> zuDto(s, benutzer, standardId(benutzer)));
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
        return signaturRepository.findFirstByIsSystemDefaultTrue()
                .filter(s -> darfSehen(s, benutzer))
                .map(s -> zuDto(s, benutzer, standardId(benutzer)));
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
            signatur = sichtbare(request.id(), benutzer)
                    .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
            if (signatur.isSystemDefault() && !benutzer.admin()) {
                // Die System-Signatur steht unter allen automatischen Mails an Kunden.
                throw new IllegalArgumentException("Die System-Signatur kann nur ein Admin ändern.");
            }
        } else {
            if (!benutzer.admin() && benutzer.profilId() == null) {
                throw new IllegalArgumentException("Zu Ihrer Anmeldung gibt es kein Benutzerprofil.");
            }
            signatur = new EmailSignature();
            signatur.setBesitzer(benutzer.profilId() == null ? null : profil(benutzer.profilId()));
        }
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
        EmailSignature signatur = sichtbare(id, benutzer)
                .orElseThrow(() -> new NoSuchElementException("Signatur nicht gefunden"));
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

    static boolean darfSehen(EmailSignature signatur, Benutzer benutzer) {
        if (benutzer.admin()) {
            return true;
        }
        return benutzer.profilId() != null && signatur.getBesitzer() != null
                && Objects.equals(signatur.getBesitzer().getId(), benutzer.profilId());
    }

    private EmailSignaturDto zuDto(EmailSignature s, Benutzer benutzer, Long standardId) {
        return zuDto(s, benutzer, standardId, true);
    }

    private EmailSignaturDto zuDto(EmailSignature s, Benutzer benutzer, Long standardId, boolean mitVorlageDaten) {
        FrontendUserProfile besitzer = s.getBesitzer();
        Long besitzerId = besitzer == null ? null : besitzer.getId();
        List<EmailSignaturDto.Bild> bilder = Optional.ofNullable(s.getImages()).orElse(List.of()).stream()
                .map(i -> new EmailSignaturDto.Bild(i.getId(), i.getCid(), i.getOriginalFilename(),
                        i.getContentType(), i.getSizeBytes()))
                .toList();
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
                bilder);
    }
}
