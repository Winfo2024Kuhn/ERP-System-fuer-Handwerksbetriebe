package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageAngebot;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.domain.PreisanfrageStatus;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageAngebotEintragenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfragePositionInputDto;
import org.example.kalkulationsprogramm.dto.Preisanfrage.PreisanfrageVergleichDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageAngebotRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.util.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zentraler Service fuer Preisanfragen an mehrere Lieferanten.
 * <p>
 * Lebenszyklus: {@code erstellen} &rarr; {@code versendeAnAlleLieferanten} &rarr;
 * manuelle Preiserfassung via {@code eintragen} &rarr; {@code vergebeAuftrag}
 * (umrouten der Bedarfspositionen auf den Gewinner).
 */
@Service
public class PreisanfrageService {

    private static final Logger log = LoggerFactory.getLogger(PreisanfrageService.class);

    /** Laenge des Lieferanten-spezifischen Tokens (ohne Praefix). */
    static final int TOKEN_LENGTH = 5;

    private final PreisanfrageRepository preisanfrageRepository;
    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    private final PreisanfragePositionRepository preisanfragePositionRepository;
    private final PreisanfrageAngebotRepository preisanfrageAngebotRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final Optional<PreisanfragePdfGenerator> pdfGenerator;
    private final EmailServiceFactory emailServiceFactory;

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromAddress;

    @Autowired
    public PreisanfrageService(
            PreisanfrageRepository preisanfrageRepository,
            PreisanfrageLieferantRepository preisanfrageLieferantRepository,
            PreisanfragePositionRepository preisanfragePositionRepository,
            PreisanfrageAngebotRepository preisanfrageAngebotRepository,
            LieferantenRepository lieferantenRepository,
            ProjektRepository projektRepository,
            ArtikelInProjektRepository artikelInProjektRepository,
            Optional<PreisanfragePdfGenerator> pdfGenerator,
            @Value("${smtp.host:}") String smtpHost,
            @Value("${smtp.port:465}") int smtpPort,
            @Value("${smtp.username:}") String smtpUsername,
            @Value("${smtp.password:}") String smtpPassword,
            @Value("${preisanfrage.from-address:${smtp.username:}}") String fromAddress) {
        this(preisanfrageRepository, preisanfrageLieferantRepository, preisanfragePositionRepository,
                preisanfrageAngebotRepository, lieferantenRepository, projektRepository,
                artikelInProjektRepository, pdfGenerator,
                smtpHost, smtpPort, smtpUsername, smtpPassword, fromAddress,
                EmailService::new);
    }

    /**
     * Konstruktor fuer Tests: erlaubt Injektion einer {@link EmailServiceFactory},
     * damit kein echter SMTP-Server benoetigt wird.
     */
    PreisanfrageService(
            PreisanfrageRepository preisanfrageRepository,
            PreisanfrageLieferantRepository preisanfrageLieferantRepository,
            PreisanfragePositionRepository preisanfragePositionRepository,
            PreisanfrageAngebotRepository preisanfrageAngebotRepository,
            LieferantenRepository lieferantenRepository,
            ProjektRepository projektRepository,
            ArtikelInProjektRepository artikelInProjektRepository,
            Optional<PreisanfragePdfGenerator> pdfGenerator,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            String fromAddress,
            EmailServiceFactory emailServiceFactory) {
        this.preisanfrageRepository = preisanfrageRepository;
        this.preisanfrageLieferantRepository = preisanfrageLieferantRepository;
        this.preisanfragePositionRepository = preisanfragePositionRepository;
        this.preisanfrageAngebotRepository = preisanfrageAngebotRepository;
        this.lieferantenRepository = lieferantenRepository;
        this.projektRepository = projektRepository;
        this.artikelInProjektRepository = artikelInProjektRepository;
        this.pdfGenerator = pdfGenerator == null ? Optional.empty() : pdfGenerator;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromAddress = fromAddress;
        this.emailServiceFactory = emailServiceFactory;
    }

    /**
     * Erzeugt eine neue Preisanfrage inkl. Positionen und pro Lieferant einen
     * {@link PreisanfrageLieferant}-Eintrag mit eindeutigem Token. Versand erfolgt
     * separat via {@link #versendeAnAlleLieferanten(Long)}.
     */
    @Transactional
    public Preisanfrage erstellen(PreisanfrageErstellenDto dto) {
        validateErstellenDto(dto);

        Preisanfrage pa = new Preisanfrage();
        pa.setNummer(naechsteNummer());
        pa.setBauvorhaben(dto.getBauvorhaben());
        pa.setAntwortFrist(dto.getAntwortFrist());
        pa.setNotiz(dto.getNotiz());
        pa.setStatus(PreisanfrageStatus.OFFEN);

        if (dto.getProjektId() != null) {
            Projekt projekt = projektRepository.findById(dto.getProjektId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Projekt nicht gefunden: " + dto.getProjektId()));
            pa.setProjekt(projekt);
            if (pa.getBauvorhaben() == null || pa.getBauvorhaben().isBlank()) {
                pa.setBauvorhaben(projekt.getBauvorhaben());
            }
        }

        int reihenfolge = 0;
        for (PreisanfragePositionInputDto pdto : dto.getPositionen()) {
            PreisanfragePosition pos = new PreisanfragePosition();
            pos.setReihenfolge(pdto.getReihenfolge() != null ? pdto.getReihenfolge() : reihenfolge++);
            pos.setExterneArtikelnummer(pdto.getExterneArtikelnummer());
            pos.setProduktname(pdto.getProduktname());
            pos.setProdukttext(pdto.getProdukttext());
            pos.setWerkstoffName(pdto.getWerkstoffName());
            pos.setMenge(pdto.getMenge());
            pos.setEinheit(pdto.getEinheit());
            pos.setKommentar(pdto.getKommentar());
            if (pdto.getArtikelInProjektId() != null) {
                ArtikelInProjekt aip = artikelInProjektRepository.findById(pdto.getArtikelInProjektId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Bedarfs-Position nicht gefunden: " + pdto.getArtikelInProjektId()));
                pos.setArtikelInProjekt(aip);
            }
            pa.addPosition(pos);
        }

        for (Long lieferantId : dto.getLieferantIds()) {
            Lieferanten lieferant = lieferantenRepository.findById(lieferantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Lieferant nicht gefunden: " + lieferantId));
            PreisanfrageLieferant pal = new PreisanfrageLieferant();
            pal.setLieferant(lieferant);
            pal.setStatus(PreisanfrageLieferantStatus.VORBEREITET);
            pal.setToken(generiereEindeutigenToken(pa.getNummer()));
            pa.addLieferant(pal);
        }

        return preisanfrageRepository.save(pa);
    }

    /**
     * Versendet die Preisanfrage an alle Lieferanten, deren Status noch
     * {@link PreisanfrageLieferantStatus#VORBEREITET} ist. Bereits versendete
     * Eintraege werden uebersprungen (Retry via
     * {@link #versendeAnEinzelnenLieferanten(Long)}).
     */
    @Transactional
    public void versendeAnAlleLieferanten(Long preisanfrageId) {
        Preisanfrage pa = preisanfrageRepository.findById(requirePositiveId(preisanfrageId, "preisanfrageId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preisanfrage nicht gefunden: " + preisanfrageId));
        for (PreisanfrageLieferant pal : pa.getLieferanten()) {
            if (pal.getStatus() == PreisanfrageLieferantStatus.VORBEREITET) {
                versendeIntern(pal);
            }
        }
    }

    /**
     * Versendet einen einzelnen Lieferanten-Eintrag. Erlaubt Retry.
     */
    @Transactional
    public void versendeAnEinzelnenLieferanten(Long preisanfrageLieferantId) {
        PreisanfrageLieferant pal = preisanfrageLieferantRepository
                .findById(requirePositiveId(preisanfrageLieferantId, "preisanfrageLieferantId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreisanfrageLieferant nicht gefunden: " + preisanfrageLieferantId));
        versendeIntern(pal);
    }

    /**
     * Liefert die Vergleichsmatrix fuer das UI. Pro Position wird der
     * guenstigste Preis markiert.
     */
    @Transactional(readOnly = true)
    public PreisanfrageVergleichDto getVergleich(Long preisanfrageId) {
        Preisanfrage pa = preisanfrageRepository.findById(requirePositiveId(preisanfrageId, "preisanfrageId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preisanfrage nicht gefunden: " + preisanfrageId));

        PreisanfrageVergleichDto out = new PreisanfrageVergleichDto();
        out.setPreisanfrageId(pa.getId());
        out.setNummer(pa.getNummer());
        out.setBauvorhaben(pa.getBauvorhaben());

        List<PreisanfrageLieferant> lieferanten = preisanfrageLieferantRepository
                .findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(pa.getId());
        for (PreisanfrageLieferant pal : lieferanten) {
            PreisanfrageVergleichDto.LieferantSpalte sp = new PreisanfrageVergleichDto.LieferantSpalte();
            sp.setPreisanfrageLieferantId(pal.getId());
            sp.setLieferantId(pal.getLieferant() != null ? pal.getLieferant().getId() : null);
            sp.setLieferantenname(pal.getLieferant() != null ? pal.getLieferant().getLieferantenname() : null);
            sp.setStatus(pal.getStatus() != null ? pal.getStatus().name() : null);
            out.getLieferanten().add(sp);
        }

        List<PreisanfragePosition> positionen = preisanfragePositionRepository
                .findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId());
        List<PreisanfrageAngebot> alleAngebote = preisanfrageAngebotRepository
                .findAllByPreisanfrageId(pa.getId());
        Map<Long, Map<Long, PreisanfrageAngebot>> angebotIndex = new HashMap<>();
        for (PreisanfrageAngebot a : alleAngebote) {
            Long posId = a.getPreisanfragePosition().getId();
            Long palId = a.getPreisanfrageLieferant().getId();
            angebotIndex.computeIfAbsent(posId, k -> new HashMap<>()).put(palId, a);
        }

        for (PreisanfragePosition pos : positionen) {
            PreisanfrageVergleichDto.PositionZeile zeile = new PreisanfrageVergleichDto.PositionZeile();
            zeile.setPreisanfragePositionId(pos.getId());
            zeile.setProduktname(pos.getProduktname());
            zeile.setMenge(pos.getMenge());
            zeile.setEinheit(pos.getEinheit());

            Map<Long, PreisanfrageAngebot> proPal = angebotIndex.getOrDefault(pos.getId(), Map.of());
            Long guenstigsterPalId = findeGuenstigsten(proPal);

            for (PreisanfrageVergleichDto.LieferantSpalte sp : out.getLieferanten()) {
                PreisanfrageVergleichDto.AngebotsZelle zelle = new PreisanfrageVergleichDto.AngebotsZelle();
                zelle.setPreisanfrageLieferantId(sp.getPreisanfrageLieferantId());
                PreisanfrageAngebot a = proPal.get(sp.getPreisanfrageLieferantId());
                if (a != null) {
                    zelle.setEinzelpreis(a.getEinzelpreis());
                    zelle.setGesamtpreis(a.getGesamtpreis());
                    zelle.setMwstProzent(a.getMwstProzent());
                    zelle.setLieferzeitTage(a.getLieferzeitTage());
                    zelle.setBemerkung(a.getBemerkung());
                    zelle.setGuenstigster(Objects.equals(sp.getPreisanfrageLieferantId(), guenstigsterPalId));
                }
                zeile.getZellen().add(zelle);
            }
            zeile.setGuenstigsterPreisanfrageLieferantId(guenstigsterPalId);
            out.getPositionen().add(zeile);
        }

        return out;
    }

    /**
     * Traegt (oder aktualisiert) ein Angebot pro Position. Ist alle Positionen
     * eines Lieferanten beantwortet, setzt den Status auf
     * {@link PreisanfrageLieferantStatus#BEANTWORTET} und aktualisiert ggf.
     * den Gesamt-Status der Preisanfrage.
     */
    @Transactional
    public PreisanfrageAngebot eintragen(PreisanfrageAngebotEintragenDto dto) {
        validateEintragenDto(dto);

        PreisanfrageLieferant pal = preisanfrageLieferantRepository.findById(dto.getPreisanfrageLieferantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreisanfrageLieferant nicht gefunden: " + dto.getPreisanfrageLieferantId()));
        PreisanfragePosition pos = preisanfragePositionRepository.findById(dto.getPreisanfragePositionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Position nicht gefunden: " + dto.getPreisanfragePositionId()));

        if (!Objects.equals(pal.getPreisanfrage().getId(), pos.getPreisanfrage().getId())) {
            throw new IllegalArgumentException(
                    "Lieferant und Position gehoeren zu unterschiedlichen Preisanfragen");
        }

        PreisanfrageAngebot angebot = preisanfrageAngebotRepository
                .findByPreisanfrageLieferantId(pal.getId()).stream()
                .filter(a -> Objects.equals(a.getPreisanfragePosition().getId(), pos.getId()))
                .findFirst()
                .orElseGet(PreisanfrageAngebot::new);
        angebot.setPreisanfrageLieferant(pal);
        angebot.setPreisanfragePosition(pos);
        angebot.setEinzelpreis(dto.getEinzelpreis());
        angebot.setGesamtpreis(dto.getGesamtpreis());
        angebot.setMwstProzent(dto.getMwstProzent());
        angebot.setLieferzeitTage(dto.getLieferzeitTage());
        angebot.setGueltigBis(dto.getGueltigBis());
        angebot.setBemerkung(dto.getBemerkung());
        angebot.setErfasstDurch("manuell");
        PreisanfrageAngebot gespeichert = preisanfrageAngebotRepository.save(angebot);

        aktualisiereStatus(pal);
        return gespeichert;
    }

    /**
     * Vergibt den Auftrag an den Gewinner-Lieferanten: alle verknuepften
     * {@link ArtikelInProjekt}-Zeilen werden auf diesen Lieferanten umgeroutet
     * und bekommen den Angebotspreis als {@code preisProStueck}. Es werden
     * <b>keine</b> neuen Bestell-Zeilen erzeugt (Design-Entscheidung aus Tracker).
     */
    @Transactional
    public void vergebeAuftrag(Long preisanfrageId, Long preisanfrageLieferantId) {
        requirePositiveId(preisanfrageId, "preisanfrageId");
        requirePositiveId(preisanfrageLieferantId, "preisanfrageLieferantId");
        Preisanfrage pa = preisanfrageRepository.findById(preisanfrageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preisanfrage nicht gefunden: " + preisanfrageId));
        PreisanfrageLieferant gewinner = preisanfrageLieferantRepository.findById(preisanfrageLieferantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreisanfrageLieferant nicht gefunden: " + preisanfrageLieferantId));

        if (!Objects.equals(gewinner.getPreisanfrage().getId(), pa.getId())) {
            throw new IllegalArgumentException(
                    "Gewinner-Lieferant gehoert nicht zu dieser Preisanfrage");
        }

        List<PreisanfrageAngebot> gewinnerAngebote = preisanfrageAngebotRepository
                .findByPreisanfrageLieferantId(gewinner.getId());
        Map<Long, PreisanfrageAngebot> angeboteProPosition = new HashMap<>();
        for (PreisanfrageAngebot a : gewinnerAngebote) {
            angeboteProPosition.put(a.getPreisanfragePosition().getId(), a);
        }

        List<PreisanfragePosition> positionen = preisanfragePositionRepository
                .findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId());
        for (PreisanfragePosition pos : positionen) {
            ArtikelInProjekt aip = pos.getArtikelInProjekt();
            if (aip == null) {
                continue;
            }
            aip.setLieferant(gewinner.getLieferant());
            PreisanfrageAngebot a = angeboteProPosition.get(pos.getId());
            if (a != null && a.getEinzelpreis() != null) {
                aip.setPreisProStueck(a.getEinzelpreis().setScale(2, java.math.RoundingMode.HALF_UP));
            }
            artikelInProjektRepository.save(aip);
        }

        pa.setVergebenAn(gewinner);
        pa.setStatus(PreisanfrageStatus.VERGEBEN);
        preisanfrageRepository.save(pa);
    }

    // ------------------------------------------------------------
    // interne Helfer
    // ------------------------------------------------------------

    private void versendeIntern(PreisanfrageLieferant pal) {
        PreisanfragePdfGenerator generator = pdfGenerator.orElseThrow(() -> new IllegalStateException(
                "Kein PreisanfragePdfGenerator registriert - Versand nicht moeglich"));
        Path pdf = generator.generatePdfForPreisanfrage(pal.getId());

        String empfaenger = erstenEmpfaengerErmitteln(pal.getLieferant());
        if (empfaenger == null) {
            throw new IllegalStateException(
                    "Lieferant hat keine hinterlegte E-Mail-Adresse: "
                            + pal.getLieferant().getLieferantenname());
        }

        String nummer = pal.getPreisanfrage().getNummer();
        String token = pal.getToken();
        String subject = "Preisanfrage " + nummer + " [" + token + "]";
        String htmlBody = baueMailBody(pal);

        EmailService service = emailServiceFactory.create(smtpHost, smtpPort, smtpUsername, smtpPassword);
        try {
            String messageId = service.sendEmailAndReturnMessageId(
                    empfaenger,
                    null,
                    fromAddress,
                    subject,
                    htmlBody,
                    pdf != null ? pdf.toString() : null,
                    pdf != null ? "Preisanfrage_" + nummer + "_" + token + ".pdf" : null);
            pal.setOutgoingMessageId(messageId);
            pal.setVersendetAn(empfaenger);
            pal.setVersendetAm(LocalDateTime.now());
            pal.setStatus(PreisanfrageLieferantStatus.VERSENDET);
            preisanfrageLieferantRepository.save(pal);
        } catch (jakarta.mail.MessagingException | IOException e) {
            log.warn("Versand der Preisanfrage fehlgeschlagen: pal={} lieferant={}",
                    pal.getId(),
                    pal.getLieferant() != null ? pal.getLieferant().getLieferantenname() : null, e);
            throw new IllegalStateException("Mail-Versand fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String baueMailBody(PreisanfrageLieferant pal) {
        String lieferantenname = pal.getLieferant() != null
                ? pal.getLieferant().getLieferantenname()
                : "Lieferant";
        String nummer = pal.getPreisanfrage().getNummer();
        return "<p>Guten Tag " + htmlEscape(lieferantenname) + ",</p>"
                + "<p>wir bitten um Ihr Angebot zur Preisanfrage <b>" + htmlEscape(nummer) + "</b>."
                + " Details entnehmen Sie bitte dem angehaengten PDF.</p>"
                + "<p>Bitte geben Sie bei Ihrer Antwort den folgenden Rueckmelde-Code an,"
                + " damit wir Ihr Angebot automatisch zuordnen koennen:</p>"
                + "<p style=\"font-size:16px;\"><b>" + htmlEscape(pal.getToken()) + "</b></p>"
                + "<p>Vielen Dank und freundliche Gruesse</p>";
    }

    private static String htmlEscape(String in) {
        if (in == null) {
            return "";
        }
        return in.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String erstenEmpfaengerErmitteln(Lieferanten lieferant) {
        if (lieferant == null || lieferant.getKundenEmails() == null) {
            return null;
        }
        return lieferant.getKundenEmails().stream()
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String naechsteNummer() {
        String jahr = String.valueOf(Year.now().getValue());
        String prefix = "PA-" + jahr + "-";
        int max = preisanfrageRepository.findMaxLfdNrByPrefix(prefix);
        return prefix + String.format(Locale.ROOT, "%03d", max + 1);
    }

    private String generiereEindeutigenToken(String nummer) {
        String suffix = TokenGenerator.generateUnique(
                TOKEN_LENGTH,
                kandidat -> preisanfrageLieferantRepository.existsByToken(nummer + "-" + kandidat));
        return nummer + "-" + suffix;
    }

    private Long findeGuenstigsten(Map<Long, PreisanfrageAngebot> proPal) {
        return proPal.values().stream()
                .filter(a -> a.getEinzelpreis() != null)
                .min(Comparator.comparing(PreisanfrageAngebot::getEinzelpreis))
                .map(a -> a.getPreisanfrageLieferant().getId())
                .orElse(null);
    }

    private void aktualisiereStatus(PreisanfrageLieferant pal) {
        List<PreisanfragePosition> positionen = preisanfragePositionRepository
                .findByPreisanfrageIdOrderByReihenfolgeAsc(pal.getPreisanfrage().getId());
        List<PreisanfrageAngebot> angebote = preisanfrageAngebotRepository
                .findByPreisanfrageLieferantId(pal.getId());
        boolean alleBeantwortet = !positionen.isEmpty()
                && positionen.stream().allMatch(pos ->
                        angebote.stream().anyMatch(a ->
                                Objects.equals(a.getPreisanfragePosition().getId(), pos.getId())
                                        && a.getEinzelpreis() != null));
        if (alleBeantwortet && pal.getStatus() != PreisanfrageLieferantStatus.BEANTWORTET) {
            pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);
            if (pal.getAntwortErhaltenAm() == null) {
                pal.setAntwortErhaltenAm(LocalDateTime.now());
            }
            preisanfrageLieferantRepository.save(pal);
        }

        Preisanfrage pa = pal.getPreisanfrage();
        if (pa.getStatus() == PreisanfrageStatus.VERGEBEN
                || pa.getStatus() == PreisanfrageStatus.ABGEBROCHEN) {
            return;
        }
        List<PreisanfrageLieferant> alle = preisanfrageLieferantRepository
                .findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(pa.getId());
        long beantwortete = alle.stream()
                .filter(x -> x.getStatus() == PreisanfrageLieferantStatus.BEANTWORTET)
                .count();
        PreisanfrageStatus neu;
        if (beantwortete == 0) {
            neu = PreisanfrageStatus.OFFEN;
        } else if (beantwortete < alle.size()) {
            neu = PreisanfrageStatus.TEILWEISE_BEANTWORTET;
        } else {
            neu = PreisanfrageStatus.VOLLSTAENDIG;
        }
        if (pa.getStatus() != neu) {
            pa.setStatus(neu);
            preisanfrageRepository.save(pa);
        }
    }

    // ------------------------------------------------------------
    // Validierung / Guards
    // ------------------------------------------------------------

    private static void validateErstellenDto(PreisanfrageErstellenDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PreisanfrageErstellenDto darf nicht null sein");
        }
        if (dto.getLieferantIds() == null || dto.getLieferantIds().isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Lieferant ist erforderlich");
        }
        if (dto.getLieferantIds().stream().distinct().count() != dto.getLieferantIds().size()) {
            throw new IllegalArgumentException("Lieferanten duerfen nicht doppelt vorkommen");
        }
        for (Long id : dto.getLieferantIds()) {
            requirePositiveId(id, "lieferantId");
        }
        if (dto.getPositionen() == null || dto.getPositionen().isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Position ist erforderlich");
        }
        for (PreisanfragePositionInputDto pdto : dto.getPositionen()) {
            if (pdto == null) {
                throw new IllegalArgumentException("Position-Eintrag ist null");
            }
            if ((pdto.getProduktname() == null || pdto.getProduktname().isBlank())
                    && pdto.getArtikelId() == null
                    && pdto.getArtikelInProjektId() == null) {
                throw new IllegalArgumentException(
                        "Position benoetigt Produktname, artikelId oder artikelInProjektId");
            }
            if (pdto.getMenge() != null && pdto.getMenge().signum() < 0) {
                throw new IllegalArgumentException("Menge darf nicht negativ sein");
            }
        }
        if (dto.getProjektId() != null) {
            requirePositiveId(dto.getProjektId(), "projektId");
        }
    }

    private static void validateEintragenDto(PreisanfrageAngebotEintragenDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PreisanfrageAngebotEintragenDto darf nicht null sein");
        }
        requirePositiveId(dto.getPreisanfrageLieferantId(), "preisanfrageLieferantId");
        requirePositiveId(dto.getPreisanfragePositionId(), "preisanfragePositionId");
        if (dto.getEinzelpreis() != null && dto.getEinzelpreis().signum() < 0) {
            throw new IllegalArgumentException("Einzelpreis darf nicht negativ sein");
        }
        if (dto.getGesamtpreis() != null && dto.getGesamtpreis().signum() < 0) {
            throw new IllegalArgumentException("Gesamtpreis darf nicht negativ sein");
        }
        if (dto.getMwstProzent() != null
                && (dto.getMwstProzent().signum() < 0
                        || dto.getMwstProzent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("MwSt muss zwischen 0 und 100 liegen");
        }
        if (dto.getLieferzeitTage() != null && dto.getLieferzeitTage() < 0) {
            throw new IllegalArgumentException("Lieferzeit darf nicht negativ sein");
        }
    }

    private static long requirePositiveId(Long id, String feldname) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(feldname + " muss positiv sein, war: " + id);
        }
        return id;
    }

    /**
     * Factory zur Erzeugung eines {@link EmailService} pro Versand-Aufruf.
     * Wird im Test durch eine stub-Implementation ersetzt, damit keine echten
     * SMTP-Verbindungen aufgebaut werden.
     */
    @FunctionalInterface
    interface EmailServiceFactory {
        EmailService create(String host, int port, String username, String password);
    }

    /** Liefert die Liste der Lieferanten fuer Ueberblick-Ansichten. */
    @Transactional(readOnly = true)
    public List<PreisanfrageLieferant> listeLieferanten(Long preisanfrageId) {
        return new ArrayList<>(preisanfrageLieferantRepository
                .findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(
                        requirePositiveId(preisanfrageId, "preisanfrageId")));
    }
}
