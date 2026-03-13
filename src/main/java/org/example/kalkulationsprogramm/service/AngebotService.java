package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.AngebotDokument;
import org.example.kalkulationsprogramm.domain.AngebotGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotErstellenDto;
import org.example.kalkulationsprogramm.dto.Angebot.AngebotResponseDto;
import org.example.kalkulationsprogramm.repository.AngebotDokumentRepository;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AngebotService {
    private final AngebotRepository angebotRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final AngebotDokumentRepository angebotDokumentRepository;
    private final KundeRepository kundeRepository;
    private final Path mailAttachmentBaseDir;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    public AngebotService(AngebotRepository angebotRepository,
            DateiSpeicherService dateiSpeicherService,
            AngebotDokumentRepository angebotDokumentRepository,
            KundeRepository kundeRepository,
            @Value("${file.mail-attachment-dir}") String mailAttachmentDir,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService) {
        this.angebotRepository = angebotRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.angebotDokumentRepository = angebotDokumentRepository;
        this.kundeRepository = kundeRepository;
        this.mailAttachmentBaseDir = (mailAttachmentDir == null || mailAttachmentDir.isBlank()) ? null
                : Path.of(mailAttachmentDir).toAbsolutePath().normalize().resolve("email");
        this.eventPublisher = eventPublisher;
        this.ausgangsGeschaeftsDokumentService = ausgangsGeschaeftsDokumentService;
    }

    public AngebotResponseDto erstelleAngebot(AngebotErstellenDto dto) {
        Angebot angebot = new Angebot();
        hydrateDtoFromKunde(dto, angebot);
        angebot.setBauvorhaben(dto.getBauvorhaben());
        // No redundant string fields setters
        angebot.setAnlegedatum(dto.getAnlegedatum() != null ? dto.getAnlegedatum() : LocalDate.now());
        angebot.setProjektStrasse(dto.getProjektStrasse());
        angebot.setProjektPlz(dto.getProjektPlz());
        angebot.setProjektOrt(dto.getProjektOrt());
        angebot.setKurzbeschreibung(dto.getKurzbeschreibung());
        if (dto.getAbgeschlossen() != null) {
            angebot.setAbgeschlossen(dto.getAbgeschlossen());
        }

        // Angebot-spezifische E-Mail-Adressen persistieren
        if (dto.getKundenEmails() != null && !dto.getKundenEmails().isEmpty()) {
            angebot.getKundenEmails().clear();
            angebot.getKundenEmails().addAll(dto.getKundenEmails());
        }

        angebotRepository.save(angebot);
        publishEmailBackfillEvent(angebot, dto, true);

        return mapToDto(angebot);
    }

    public AngebotResponseDto erstelleAngebot(AngebotErstellenDto dto, MultipartFile imageFile) {
        Angebot angebot = new Angebot();
        hydrateDtoFromKunde(dto, angebot);
        angebot.setBauvorhaben(dto.getBauvorhaben());

        // No redundant string fields setters
        angebot.setAnlegedatum(dto.getAnlegedatum() != null ? dto.getAnlegedatum() : LocalDate.now());
        angebot.setProjektStrasse(dto.getProjektStrasse());
        angebot.setProjektPlz(dto.getProjektPlz());
        angebot.setProjektOrt(dto.getProjektOrt());
        angebot.setKurzbeschreibung(dto.getKurzbeschreibung());
        if (dto.getAbgeschlossen() != null) {
            angebot.setAbgeschlossen(dto.getAbgeschlossen());
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            String bildWebPfad = dateiSpeicherService.speichereBild(imageFile);
            angebot.setBildUrl(bildWebPfad);
        }

        // Angebot-spezifische E-Mail-Adressen persistieren
        if (dto.getKundenEmails() != null && !dto.getKundenEmails().isEmpty()) {
            angebot.getKundenEmails().clear();
            angebot.getKundenEmails().addAll(dto.getKundenEmails());
        }

        angebotRepository.save(angebot);
        publishEmailBackfillEvent(angebot, dto, true);

        return mapToDto(angebot);
    }

    public List<AngebotResponseDto> alle() {
        return angebotRepository.findAllWithKundenEmails().stream()
                .filter(angebot -> angebot.getProjekt() == null)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AngebotResponseDto> suche(Integer jahr,
            String kundenname,
            String bauvorhaben,
            String angebotsnummer,
            String q,
            boolean nurOhneProjekt) {
        // If freitext query is provided, use the comprehensive search
        String freitext = trimToNull(q);
        if (freitext != null) {
            // First get all matching by freitext
            List<Angebot> freittextResults = angebotRepository.searchByBauvorhabenOrKundeOrEmail(freitext);

            // Then filter by year if provided
            LocalDate startDate = null;
            LocalDate endDate = null;
            if (jahr != null) {
                startDate = LocalDate.of(jahr, 1, 1);
                endDate = LocalDate.of(jahr, 12, 31);
            }

            final LocalDate finalStartDate = startDate;
            final LocalDate finalEndDate = endDate;

            return freittextResults.stream()
                    .filter(a -> {
                        if (nurOhneProjekt && a.getProjekt() != null) return false;
                        if (finalStartDate == null || finalEndDate == null)
                            return true;
                        LocalDate anlegedatum = a.getAnlegedatum();
                        if (anlegedatum == null)
                            return false;
                        return !anlegedatum.isBefore(finalStartDate) && !anlegedatum.isAfter(finalEndDate);
                    })
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }

        boolean noFilters = (jahr == null)
                && (kundenname == null || kundenname.isBlank())
                && (bauvorhaben == null || bauvorhaben.isBlank())
                && (angebotsnummer == null || angebotsnummer.isBlank());
        if (noFilters) {
            if (nurOhneProjekt) {
                return alle();
            }
            return angebotRepository.findAllWithKundenEmails().stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (jahr != null) {
            startDate = LocalDate.of(jahr, 1, 1);
            endDate = LocalDate.of(jahr, 12, 31);
        }

        String kn = trimToNull(kundenname);
        String bv = trimToNull(bauvorhaben);
        String anr = trimToNull(angebotsnummer);

        return angebotRepository.search(kn, bv, startDate, endDate, anr).stream()
                .filter(a -> !nurOhneProjekt || a.getProjekt() == null)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<Integer> verfuegbareAnlegeJahre() {
        List<Integer> jahre = angebotRepository.findDistinctAnlegedatumJahre();
        return jahre != null ? jahre : List.of();
    }

    private String trimToNull(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public Angebot finde(Long id) {
        return angebotRepository.findById(id).orElse(null);
    }

    public AngebotResponseDto findeDto(Long id) {
        ausgangsGeschaeftsDokumentService.aktualisiereAngebotPreisAusDokumenten(id);
        return angebotRepository.findById(id).map(this::mapToDto).orElse(null);
    }

    public AngebotResponseDto aktualisiereAngebot(Long id, AngebotErstellenDto dto) {
        return angebotRepository.findById(id).map(a -> {
            hydrateDtoFromKunde(dto, a);
            a.setBauvorhaben(dto.getBauvorhaben());
            a.setProjektStrasse(dto.getProjektStrasse());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektOrt(dto.getProjektOrt());
            a.setKurzbeschreibung(dto.getKurzbeschreibung());
            if (dto.getAbgeschlossen() != null) {
                a.setAbgeschlossen(dto.getAbgeschlossen());
            }

            if (dto.getAnlegedatum() != null) {
                a.setAnlegedatum(dto.getAnlegedatum());
            }

            // Angebot-spezifische E-Mail-Adressen aktualisieren
            if (dto.getKundenEmails() != null) {
                a.getKundenEmails().clear();
                a.getKundenEmails().addAll(dto.getKundenEmails());
            }

            angebotRepository.save(a);
            publishEmailBackfillEvent(a, dto, false);

            return mapToDto(a);
        }).orElse(null);
    }

    public AngebotResponseDto aktualisiereAngebot(Long id, AngebotErstellenDto dto, MultipartFile imageFile) {
        return angebotRepository.findById(id).map(a -> {
            hydrateDtoFromKunde(dto, a);
            a.setBauvorhaben(dto.getBauvorhaben());
            a.setProjektStrasse(dto.getProjektStrasse());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektOrt(dto.getProjektOrt());
            a.setKurzbeschreibung(dto.getKurzbeschreibung());
            if (dto.getAbgeschlossen() != null) {
                a.setAbgeschlossen(dto.getAbgeschlossen());
            }
            if (dto.getAnlegedatum() != null) {
                a.setAnlegedatum(dto.getAnlegedatum());
            }
            if (imageFile != null && !imageFile.isEmpty()) {
                if (a.getBildUrl() != null && !a.getBildUrl().isBlank()) {
                    try {
                        dateiSpeicherService.loescheBild(a.getBildUrl());
                    } catch (Exception ignored) {
                    }
                }
                String bildWebPfad = dateiSpeicherService.speichereBild(imageFile);
                a.setBildUrl(bildWebPfad);
            }

            // Angebot-spezifische E-Mail-Adressen aktualisieren
            if (dto.getKundenEmails() != null) {
                a.getKundenEmails().clear();
                a.getKundenEmails().addAll(dto.getKundenEmails());
            }

            angebotRepository.save(a);
            publishEmailBackfillEvent(a, dto, false);

            return mapToDto(a);
        }).orElse(null);
    }

    private void hydrateDtoFromKunde(AngebotErstellenDto dto, Angebot angebot) {
        if (dto == null || dto.getKundenId() == null || dto.getKundenId() <= 0) {
            return;
        }
        Kunde kunde = kundeRepository.findById(dto.getKundenId()).orElse(null);
        if (kunde == null) {
            // Kundenwahl ist bei Angeboten optional – wenn kein gültiger Kunde gewählt
            // wurde, einfach fortfahren.
            return;
        }
        // Setze die Kunde-Beziehung auf dem Angebot (foreign key)
        angebot.setKunde(kunde);

        if (!StringUtils.hasText(dto.getKunde())) {
            dto.setKunde(kunde.getName());
        }
        if (!StringUtils.hasText(dto.getKundennummer())) {
            dto.setKundennummer(kunde.getKundennummer());
        }
        if ((dto.getKundenEmails() == null || dto.getKundenEmails().isEmpty()) && kunde.getKundenEmails() != null) {
            dto.setKundenEmails(new ArrayList<>(kunde.getKundenEmails()));
        }
    }

    private void publishEmailBackfillEvent(Angebot angebot, AngebotErstellenDto dto, boolean isNew) {
        try {
            List<String> kundenEmails = dto.getKundenEmails();
            if (kundenEmails != null && !kundenEmails.isEmpty()) {
                if (isNew) {
                    eventPublisher.publishEvent(
                            org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forNewEntity(
                                    org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANGEBOT,
                                    angebot.getId(),
                                    kundenEmails));
                } else {
                    eventPublisher.publishEvent(
                            org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                                    org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANGEBOT,
                                    angebot.getId(),
                                    kundenEmails,
                                    kundenEmails));
                }
            }
        } catch (Exception e) {
            // Silent fallback – email backfill is non-critical
        }
    }

    public void speichereEmail(Angebot angebot, String email) {
        if (angebot.getKunde() != null && email != null && !email.isBlank()) {
            Kunde kunde = angebot.getKunde();
            if (!kunde.getKundenEmails().contains(email)) {
                kunde.getKundenEmails().add(email);
                kundeRepository.save(kunde); // Update Customer with new email
            }
        }
        angebot.setEmailVersandDatum(LocalDate.now());
        angebotRepository.save(angebot);
    }

    public Angebot speichere(Angebot angebot) {
        return angebotRepository.save(angebot);
    }

    public AngebotResponseDto updateAngebotKurzbeschreibung(Long id, String kurzbeschreibung) {
        return angebotRepository.findById(id).map(a -> {
            a.setKurzbeschreibung(kurzbeschreibung);
            angebotRepository.save(a);
            return mapToDto(a);
        }).orElse(null);
    }

    public boolean loesche(Long id) {
        return angebotRepository.findById(id).map(a -> {
            List<AngebotDokument> docs = angebotDokumentRepository.findByAngebotId(a.getId());
            for (AngebotDokument d : docs) {
                try {
                    dateiSpeicherService.loescheAngebotDatei(d.getId());
                } catch (Exception ignored) {
                }
            }
            Long projektId = a.getProjekt() != null ? a.getProjekt().getId() : null;
            deleteEmailAttachmentsDirectory(a.getId());
            angebotRepository.delete(a);
            if (projektId != null) {
                dateiSpeicherService.aktualisiereProjektFinanzstatus(projektId);
            }
            return true;
        }).orElse(false);
    }

    private AngebotResponseDto mapToDto(Angebot a) {
        AngebotResponseDto dto = new AngebotResponseDto();
        dto.setId(a.getId());
        if (a.getKunde() != null) {
            dto.setKundenId(a.getKunde().getId());
            dto.setKundenName(sanitize(a.getKunde().getName()));
            dto.setKundennummer(a.getKunde().getKundennummer());
            dto.setKundenEmails(a.getKunde().getKundenEmails());
            // Merge Angebot-spezifische E-Mails dazu
            if (a.getKundenEmails() != null && !a.getKundenEmails().isEmpty()) {
                java.util.Set<String> merged = new java.util.LinkedHashSet<>(dto.getKundenEmails() != null ? dto.getKundenEmails() : java.util.Collections.emptyList());
                merged.addAll(a.getKundenEmails());
                dto.setKundenEmails(new java.util.ArrayList<>(merged));
            }
            // Expanded
            dto.setKundenStrasse(a.getKunde().getStrasse());
            dto.setKundenPlz(a.getKunde().getPlz());
            dto.setKundenOrt(a.getKunde().getOrt());
            dto.setKundenTelefon(a.getKunde().getTelefon());
            dto.setKundenMobiltelefon(a.getKunde().getMobiltelefon());
            dto.setKundenAnsprechpartner(a.getKunde().getAnsprechspartner());
            // Anrede als String-Name des Enum übertragen
            dto.setKundenAnrede(a.getKunde().getAnrede() != null ? a.getKunde().getAnrede().name() : null);
        }
        dto.setBauvorhaben(sanitize(a.getBauvorhaben()));
        // dto values already set above based on getKunde()

        dto.setBetrag(a.getBetrag());
        dto.setEmailVersandDatum(a.getEmailVersandDatum());
        dto.setAnlegedatum(a.getAnlegedatum());
        dto.setBildUrl(a.getBildUrl());
        dto.setProjektStrasse(a.getProjektStrasse());
        dto.setProjektPlz(a.getProjektPlz());
        dto.setProjektOrt(a.getProjektOrt());
        dto.setKurzbeschreibung(a.getKurzbeschreibung());
        dto.setAbgeschlossen(a.isAbgeschlossen());
        dto.setCreatedAt(a.getCreatedAt());

        if (a.getProjekt() != null) {
            dto.setProjektId(a.getProjekt().getId());
        }
        angebotDokumentRepository.findByAngebotId(a.getId()).stream()
                .filter(AngebotGeschaeftsdokument.class::isInstance)
                .map(AngebotGeschaeftsdokument.class::cast)
                .filter(d -> d.getGeschaeftsdokumentart() != null
                        && d.getGeschaeftsdokumentart().toLowerCase().contains("angebot"))
                .findFirst()
                .ifPresent(d -> dto.setAngebotsnummer(d.getDokumentid()));

        // Angebotsnummer aus AusgangsGeschaeftsDokument ableiten (hat Priorität)
        String angebotsnummerNeu = ausgangsGeschaeftsDokumentService.resolveAngebotsnummer(a.getId());
        if (angebotsnummerNeu != null) {
            dto.setAngebotsnummer(angebotsnummerNeu);
        }

        // Legacy lookup code removed
        return dto;
    }

    private String sanitize(String s) {
        if (s == null)
            return null;
        return s.replace("ß", "ss").replace("\uFFFD", "ss").replace("?", "");
    }

    // resolveKundenId removed

    private void deleteEmailAttachmentsDirectory(Long angebotId) {
        if (angebotId == null || mailAttachmentBaseDir == null) {
            return;
        }
        Path directory = mailAttachmentBaseDir.resolve(String.valueOf(angebotId));
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
