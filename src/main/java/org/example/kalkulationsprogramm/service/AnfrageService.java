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

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnfrageService {
    private final AnfrageRepository anfrageRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final KundeRepository kundeRepository;
    private final Path mailAttachmentBaseDir;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    public AnfrageService(AnfrageRepository anfrageRepository,
            DateiSpeicherService dateiSpeicherService,
            AnfrageDokumentRepository anfrageDokumentRepository,
            KundeRepository kundeRepository,
            @Value("${file.mail-attachment-dir}") String mailAttachmentDir,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService) {
        this.anfrageRepository = anfrageRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.anfrageDokumentRepository = anfrageDokumentRepository;
        this.kundeRepository = kundeRepository;
        this.mailAttachmentBaseDir = (mailAttachmentDir == null || mailAttachmentDir.isBlank()) ? null
                : Path.of(mailAttachmentDir).toAbsolutePath().normalize().resolve("email");
        this.eventPublisher = eventPublisher;
        this.ausgangsGeschaeftsDokumentService = ausgangsGeschaeftsDokumentService;
    }

    public AnfrageResponseDto erstelleAnfrage(AnfrageErstellenDto dto) {
        Anfrage anfrage = new Anfrage();
        hydrateDtoFromKunde(dto, anfrage);
        anfrage.setBauvorhaben(dto.getBauvorhaben());
        // No redundant string fields setters
        anfrage.setAnlegedatum(dto.getAnlegedatum() != null ? dto.getAnlegedatum() : LocalDate.now());
        anfrage.setProjektStrasse(dto.getProjektStrasse());
        anfrage.setProjektPlz(dto.getProjektPlz());
        anfrage.setProjektPlz(dto.getProjektPlz());
        anfrage.setProjektOrt(dto.getProjektOrt());
        anfrage.setKurzbeschreibung(dto.getKurzbeschreibung());
        if (dto.getAbgeschlossen() != null) {
            anfrage.setAbgeschlossen(dto.getAbgeschlossen());
        }

        // Anfrage-spezifische E-Mail-Adressen persistieren
        if (dto.getKundenEmails() != null && !dto.getKundenEmails().isEmpty()) {
            anfrage.getKundenEmails().clear();
            anfrage.getKundenEmails().addAll(dto.getKundenEmails());
        }

        anfrageRepository.save(anfrage);

        // E-Mail-Backfill Trigger
        try {
            List<String> kundenEmails = dto.getKundenEmails();
            if (kundenEmails != null && !kundenEmails.isEmpty()) {
                eventPublisher
                        .publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forNewEntity(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANFRAGE,
                                anfrage.getId(),
                                kundenEmails));
            }
        } catch (Exception e) {
            // log
        }

        return mapToDto(anfrage);
    }

    public AnfrageResponseDto erstelleAnfrage(AnfrageErstellenDto dto, MultipartFile imageFile) {
        Anfrage anfrage = new Anfrage();
        hydrateDtoFromKunde(dto, anfrage);
        anfrage.setBauvorhaben(dto.getBauvorhaben());

        // No redundant string fields setters
        anfrage.setAnlegedatum(dto.getAnlegedatum() != null ? dto.getAnlegedatum() : LocalDate.now());
        anfrage.setProjektStrasse(dto.getProjektStrasse());
        anfrage.setProjektPlz(dto.getProjektPlz());
        anfrage.setProjektPlz(dto.getProjektPlz());
        anfrage.setProjektOrt(dto.getProjektOrt());
        anfrage.setKurzbeschreibung(dto.getKurzbeschreibung());
        if (dto.getAbgeschlossen() != null) {
            anfrage.setAbgeschlossen(dto.getAbgeschlossen());
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            String bildWebPfad = dateiSpeicherService.speichereBild(imageFile);
            anfrage.setBildUrl(bildWebPfad);
        }

        // Anfrage-spezifische E-Mail-Adressen persistieren
        if (dto.getKundenEmails() != null && !dto.getKundenEmails().isEmpty()) {
            anfrage.getKundenEmails().clear();
            anfrage.getKundenEmails().addAll(dto.getKundenEmails());
        }

        anfrageRepository.save(anfrage);

        // E-Mail-Backfill Trigger
        try {
            List<String> kundenEmails = dto.getKundenEmails();
            if (kundenEmails != null && !kundenEmails.isEmpty()) {
                eventPublisher
                        .publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forNewEntity(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANFRAGE,
                                anfrage.getId(),
                                kundenEmails));
            }
        } catch (Exception e) {
            // log
        }

        return mapToDto(anfrage);
    }

    public List<AnfrageResponseDto> alle() {
        return anfrageRepository.findAll().stream()
                .filter(anfrage -> anfrage.getProjekt() == null)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<AnfrageResponseDto> suche(Integer jahr,
            String kundenname,
            String bauvorhaben,
            String anfragesnummer,
            String q,
            boolean nurOhneProjekt) {
        // If freitext query is provided, use the comprehensive search
        String freitext = trimToNull(q);
        if (freitext != null) {
            // First get all matching by freitext
            List<Anfrage> freittextResults = anfrageRepository.searchByBauvorhabenOrKundeOrEmail(freitext);

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
                && (anfragesnummer == null || anfragesnummer.isBlank());
        if (noFilters) {
            if (nurOhneProjekt) {
                return alle();
            }
            return anfrageRepository.findAll().stream()
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
        String anr = trimToNull(anfragesnummer);

        return anfrageRepository.search(kn, bv, startDate, endDate, anr).stream()
                .filter(a -> !nurOhneProjekt || a.getProjekt() == null)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<Integer> verfuegbareAnlegeJahre() {
        List<Integer> jahre = anfrageRepository.findDistinctAnlegedatumJahre();
        return jahre != null ? jahre : List.of();
    }

    private String trimToNull(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public Anfrage finde(Long id) {
        return anfrageRepository.findById(id).orElse(null);
    }

    public AnfrageResponseDto findeDto(Long id) {
        ausgangsGeschaeftsDokumentService.aktualisiereAnfragePreisAusDokumenten(id);
        return anfrageRepository.findById(id).map(this::mapToDto).orElse(null);
    }

    public AnfrageResponseDto aktualisiereAnfrage(Long id, AnfrageErstellenDto dto) {
        return anfrageRepository.findById(id).map(a -> {
            hydrateDtoFromKunde(dto, a);
            a.setBauvorhaben(dto.getBauvorhaben());
            a.setProjektStrasse(dto.getProjektStrasse());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektOrt(dto.getProjektOrt());
            a.setKurzbeschreibung(dto.getKurzbeschreibung());
            if (dto.getBetrag() != null) {
                a.setBetrag(dto.getBetrag());
            }
            if (dto.getAbgeschlossen() != null) {
                a.setAbgeschlossen(dto.getAbgeschlossen());
            }
            // Email updates are ignored for now as they belong to Kunde.
            // Only backfill if we want to re-scan for this customer.
            boolean invokeBackfill = true;

            if (dto.getAnlegedatum() != null) {
                a.setAnlegedatum(dto.getAnlegedatum());
            }

            // Anfrage-spezifische E-Mail-Adressen aktualisieren
            if (dto.getKundenEmails() != null) {
                a.getKundenEmails().clear();
                a.getKundenEmails().addAll(dto.getKundenEmails());
            }

            anfrageRepository.save(a);

            // E-Mail-Backfill Trigger
            if (invokeBackfill) {
                try {
                    List<String> kundenEmails = dto.getKundenEmails();
                    if (kundenEmails != null && !kundenEmails.isEmpty()) {
                        eventPublisher.publishEvent(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                                        org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANFRAGE,
                                        a.getId(),
                                        kundenEmails,
                                        kundenEmails));
                    }
                } catch (Exception e) {
                    // log
                }
            }

            return mapToDto(a);
        }).orElse(null);
    }

    public AnfrageResponseDto aktualisiereAnfrage(Long id, AnfrageErstellenDto dto, MultipartFile imageFile) {
        return anfrageRepository.findById(id).map(a -> {
            hydrateDtoFromKunde(dto, a);
            a.setBauvorhaben(dto.getBauvorhaben());
            a.setProjektStrasse(dto.getProjektStrasse());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektPlz(dto.getProjektPlz());
            a.setProjektOrt(dto.getProjektOrt());
            a.setKurzbeschreibung(dto.getKurzbeschreibung());
            if (dto.getBetrag() != null) {
                a.setBetrag(dto.getBetrag());
            }
            if (dto.getAbgeschlossen() != null) {
                a.setAbgeschlossen(dto.getAbgeschlossen());
            }
            boolean invokeBackfill = true;
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

            // Anfrage-spezifische E-Mail-Adressen aktualisieren
            if (dto.getKundenEmails() != null) {
                a.getKundenEmails().clear();
                a.getKundenEmails().addAll(dto.getKundenEmails());
            }

            anfrageRepository.save(a);

            // E-Mail-Backfill Trigger
            if (invokeBackfill) {
                try {
                    List<String> kundenEmails = dto.getKundenEmails();
                    if (kundenEmails != null && !kundenEmails.isEmpty()) {
                        eventPublisher.publishEvent(
                                org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                                        org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.ANFRAGE,
                                        a.getId(),
                                        kundenEmails,
                                        kundenEmails));
                    }
                } catch (Exception e) {
                    // log
                }
            }

            return mapToDto(a);
        }).orElse(null);
    }

    private void hydrateDtoFromKunde(AnfrageErstellenDto dto, Anfrage anfrage) {
        if (dto == null || dto.getKundenId() == null || dto.getKundenId() <= 0) {
            return;
        }
        Kunde kunde = kundeRepository.findById(dto.getKundenId()).orElse(null);
        if (kunde == null) {
            // Kundenwahl ist bei Anfragenn optional – wenn kein gültiger Kunde gewählt
            // wurde, einfach fortfahren.
            return;
        }
        // Setze die Kunde-Beziehung auf dem Anfrage (foreign key)
        anfrage.setKunde(kunde);

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

    public void speichereEmail(Anfrage anfrage, String email) {
        if (anfrage.getKunde() != null && email != null && !email.isBlank()) {
            Kunde kunde = anfrage.getKunde();
            if (!kunde.getKundenEmails().contains(email)) {
                kunde.getKundenEmails().add(email);
                kundeRepository.save(kunde); // Update Customer with new email
            }
        }
        anfrage.setEmailVersandDatum(LocalDate.now());
        anfrageRepository.save(anfrage);
    }

    public Anfrage speichere(Anfrage anfrage) {
        return anfrageRepository.save(anfrage);
    }

    public AnfrageResponseDto updateAnfrageKurzbeschreibung(Long id, String kurzbeschreibung) {
        return anfrageRepository.findById(id).map(a -> {
            a.setKurzbeschreibung(kurzbeschreibung);
            anfrageRepository.save(a);
            return mapToDto(a);
        }).orElse(null);
    }

    public boolean loesche(Long id) {
        return anfrageRepository.findById(id).map(a -> {
            List<AnfrageDokument> docs = anfrageDokumentRepository.findByAnfrageId(a.getId());
            for (AnfrageDokument d : docs) {
                try {
                    dateiSpeicherService.loescheAnfrageDatei(d.getId());
                } catch (Exception ignored) {
                }
            }
            Long projektId = a.getProjekt() != null ? a.getProjekt().getId() : null;
            deleteEmailAttachmentsDirectory(a.getId());
            anfrageRepository.delete(a);
            if (projektId != null) {
                dateiSpeicherService.aktualisiereProjektFinanzstatus(projektId);
            }
            return true;
        }).orElse(false);
    }

    private AnfrageResponseDto mapToDto(Anfrage a) {
        AnfrageResponseDto dto = new AnfrageResponseDto();
        dto.setId(a.getId());
        if (a.getKunde() != null) {
            dto.setKundenId(a.getKunde().getId());
            dto.setKundenName(sanitize(a.getKunde().getName()));
            dto.setKundennummer(a.getKunde().getKundennummer());
            dto.setKundenEmails(a.getKunde().getKundenEmails());
            // Merge Anfrage-spezifische E-Mails dazu
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
        anfrageDokumentRepository.findByAnfrageId(a.getId()).stream()
                .filter(AnfrageGeschaeftsdokument.class::isInstance)
                .map(AnfrageGeschaeftsdokument.class::cast)
                .filter(d -> d.getGeschaeftsdokumentart() != null
                        && d.getGeschaeftsdokumentart().toLowerCase().contains("angebot"))
                .findFirst()
                .ifPresent(d -> dto.setAnfragesnummer(d.getDokumentid()));

        // Anfragesnummer aus AusgangsGeschaeftsDokument ableiten (hat Priorität)
        String anfragesnummerNeu = ausgangsGeschaeftsDokumentService.resolveAnfragesnummer(a.getId());
        if (anfragesnummerNeu != null) {
            dto.setAnfragesnummer(anfragesnummerNeu);
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

    private void deleteEmailAttachmentsDirectory(Long anfrageId) {
        if (anfrageId == null || mailAttachmentBaseDir == null) {
            return;
        }
        Path directory = mailAttachmentBaseDir.resolve(String.valueOf(anfrageId));
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
