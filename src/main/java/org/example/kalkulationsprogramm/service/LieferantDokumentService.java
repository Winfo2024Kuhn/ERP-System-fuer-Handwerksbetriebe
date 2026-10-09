package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LieferantDokumentService {

        private final LieferantDokumentRepository dokumentRepository;
        private final AbteilungDokumentBerechtigungRepository berechtigungRepository;
        private final LieferantenRepository lieferantenRepository;
        private final ProjektRepository projektRepository;
        private final MitarbeiterRepository mitarbeiterRepository;
        private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
        private final EmailAttachmentRepository emailAttachmentRepository;
        @Lazy
        private final GeminiDokumentAnalyseService geminiService;
        @Lazy
        private final BelegZuordnungService belegZuordnungService;
        private final LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;
        private final LieferantVorauskasseAutoAssigner vorauskasseAutoAssigner;

        @Value("${upload.path:uploads}")
        private String uploadPath;

        /**
         * Ermittelt die sichtbaren und scanbaren Dokumenttypen für einen Mitarbeiter
         * basierend auf seinen Abteilungszugehörigkeiten.
         */
        @Transactional(readOnly = true)
        public LieferantDokumentDto.BerechtigungenResponse getBerechtigungen(Long mitarbeiterId) {
                Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

                List<Long> abteilungIds = mitarbeiter.getAbteilungen().stream()
                                .map(Abteilung::getId)
                                .collect(Collectors.toList());

                if (abteilungIds.isEmpty()) {
                        return LieferantDokumentDto.BerechtigungenResponse.builder()
                                        .sichtbareTypen(List.of())
                                        .scanbarTypen(List.of())
                                        .build();
                }

                List<LieferantDokumentTyp> sichtbar = berechtigungRepository
                                .findSichtbareTypenByAbteilungIds(abteilungIds)
                                .stream()
                                .map(this::safeValueOf)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());

                List<LieferantDokumentTyp> scanbar = berechtigungRepository
                                .findScanbarTypenByAbteilungIds(abteilungIds)
                                .stream()
                                .map(this::safeValueOf)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());

                return LieferantDokumentDto.BerechtigungenResponse.builder()
                                .sichtbareTypen(sichtbar)
                                .scanbarTypen(scanbar)
                                .build();
        }

        private LieferantDokumentTyp safeValueOf(String typeName) {
                if (typeName == null)
                        return null;
                // Legacy-Mapping
                if ("EINGANGSRECHNUNG".equalsIgnoreCase(typeName)) {
                        return LieferantDokumentTyp.RECHNUNG;
                }
                try {
                        return LieferantDokumentTyp.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                        return null;
                }
        }

        /**
         * Lädt Dokumente eines Lieferanten, beschränkt auf die übergebenen sichtbaren
         * Dokumenttypen (siehe {@code LieferantDokumentZugriffService}).
         */
        @Transactional(readOnly = true)
        public List<LieferantDokumentDto.Response> getDokumenteFiltered(Long lieferantId,
                        Set<LieferantDokumentTyp> sichtbareTypen, LieferantDokumentTyp typFilter) {
                if (sichtbareTypen.isEmpty()) {
                        return List.of();
                }

                List<LieferantDokument> dokumente;
                if (typFilter != null) {
                        if (!sichtbareTypen.contains(typFilter)) {
                                return List.of();
                        }
                        dokumente = dokumentRepository.findByLieferantIdAndTypOrderByUploadDatumDesc(lieferantId,
                                        typFilter);
                } else {
                        dokumente = dokumentRepository.findByLieferantIdAndTypIn(lieferantId,
                                        List.copyOf(sichtbareTypen));
                }

                return dokumente.stream().map(this::toDto).collect(Collectors.toList());
        }

        /**
         * Findet ein Dokument anhand der ID.
         */
        @Transactional(readOnly = true)
        public LieferantDokument findById(Long dokumentId) {
                return dokumentRepository.findById(dokumentId).orElse(null);
        }

        /**
         * Findet ein Dokument anhand der ID und gibt das DTO zurück.
         */
        @Transactional(readOnly = true)
        public LieferantDokumentDto.Response getDokumentById(Long dokumentId) {
                var dok = dokumentRepository.findById(dokumentId).orElse(null);
                if (dok == null) {
                        return null;
                }
                return toDto(dok);
        }

        @Transactional(readOnly = true)
        public long zaehleDokumente(Long lieferantId) {
                return dokumentRepository.zaehleByLieferantId(lieferantId);
        }

        /**
         * Welche der E-Mail-Anhänge sind als Lieferanten-Dokument eines der übergebenen Typen
         * abgelegt? Dient dazu, Anhänge nicht sichtbarer Dokumenttypen auszublenden.
         */
        @Transactional(readOnly = true)
        public Set<Long> findAnhangIdsMitTyp(Collection<Long> anhangIds, Set<LieferantDokumentTyp> typen) {
                if (anhangIds.isEmpty() || typen.isEmpty()) {
                        return Set.of();
                }
                return new HashSet<>(dokumentRepository.findAnhangIdsMitDokumentTyp(anhangIds, typen));
        }

        /** Zählt nur Dokumente der übergebenen (sichtbaren) Typen. */
        @Transactional(readOnly = true)
        public long zaehleDokumente(Long lieferantId, Set<LieferantDokumentTyp> sichtbareTypen) {
                if (sichtbareTypen.isEmpty()) {
                        return 0;
                }
                return dokumentRepository.zaehleByLieferantIdAndTypIn(lieferantId, List.copyOf(sichtbareTypen));
        }

        /**
         * Interner Loader für alle Dokumente eines Lieferanten (ohne Berechtigungsfilter).
         * Vor der Auslieferung muss der Zugriffservice die Antwort auf sichtbare Typen begrenzen.
         */
        @Transactional(readOnly = true)
        public List<LieferantDokumentDto.Response> getDokumenteByLieferant(Long lieferantId,
                        LieferantDokumentTyp typFilter) {
                List<LieferantDokument> dokumente;
                if (typFilter != null) {
                        dokumente = dokumentRepository.findByLieferantIdAndTypOrderByUploadDatumDesc(lieferantId,
                                        typFilter);
                } else {
                        dokumente = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId);
                }
                return dokumente.stream().map(this::toDto).collect(Collectors.toList());
        }

        /**
         * Speichert ein neues Lieferanten-Dokument (manueller Upload).
         * Führt SYNCHRON die AI-Analyse durch und speichert Dokument + Geschäftsdaten
         * zusammen.
         */
        @Transactional
        public LieferantDokumentDto.Response uploadDokument(
                        Long lieferantId,
                        MultipartFile datei,
                        LieferantDokumentDto.UploadRequest request,
                        Long mitarbeiterId,
                        boolean useProModel) throws IOException {

                // Berechtigungsprüfung
                LieferantDokumentDto.BerechtigungenResponse berechtigungen = getBerechtigungen(mitarbeiterId);
                if (!berechtigungen.getScanbarTypen().contains(request.getTyp())) {
                        throw new RuntimeException("Keine Berechtigung zum Hochladen von " + request.getTyp());
                }

                Lieferanten lieferant = lieferantenRepository.findById(lieferantId)
                                .orElseThrow(() -> new RuntimeException("Lieferant nicht gefunden"));

                Mitarbeiter uploadedBy = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);

                // Datei speichern
                String originalFilename = Path.of(StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()))).getFileName().toString();
                String storedFilename = UUID.randomUUID() + "_" + originalFilename;

                Path lieferantDir = Path.of(uploadPath, "lieferanten", lieferantId.toString()).toAbsolutePath().normalize();
                Files.createDirectories(lieferantDir);
                Path targetPath = lieferantDir.resolve(storedFilename).normalize();
                if (!targetPath.startsWith(lieferantDir)) {
                    throw new SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt");
                }
                Files.copy(datei.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // Entity erstellen
                LieferantDokument dokument = new LieferantDokument();
                dokument.setLieferant(lieferant);
                dokument.setTyp(request.getTyp());
                dokument.setOriginalDateiname(originalFilename);
                dokument.setGespeicherterDateiname(storedFilename);
                dokument.setUploadDatum(LocalDateTime.now());
                dokument.setUploadedBy(uploadedBy);

                // Verknüpfte Dokumente
                if (request.getVerknuepfteIds() != null && !request.getVerknuepfteIds().isEmpty()) {
                        Set<LieferantDokument> verknuepfte = new HashSet<>(
                                        dokumentRepository.findAllById(request.getVerknuepfteIds()));
                        dokument.setVerknuepfteDokumente(verknuepfte);
                }

                // Dokument ERST speichern um ID zu bekommen
                dokument = dokumentRepository.saveAndFlush(dokument);

                // SYNCHRONE AI-Analyse durchführen
                try {
                        log.info("Starte synchrone Analyse für Dokument {}", dokument.getId());
                        var analyzeResult = geminiService.analyzeFile(targetPath, originalFilename, useProModel);

                        if (analyzeResult != null) {
                                log.info("Analyse erfolgreich: Typ={}, Nummer={}, Betrag={}",
                                                analyzeResult.getDokumentTyp(),
                                                analyzeResult.getDokumentNummer(),
                                                analyzeResult.getBetragBrutto());

                                // Geschäftsdaten erstellen und verknüpfen
                                LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
                                gd.setDokument(dokument);
                                gd.setDokumentNummer(analyzeResult.getDokumentNummer());
                                gd.setDokumentDatum(analyzeResult.getDokumentDatum());
                                gd.setBetragNetto(analyzeResult.getBetragNetto());
                                gd.setBetragBrutto(analyzeResult.getBetragBrutto());
                                gd.setMwstSatz(analyzeResult.getMwstSatz());
                                gd.setZahlungsziel(analyzeResult.getZahlungsziel());
                                gd.setBestellnummer(analyzeResult.getBestellnummer());
                                gd.setReferenzNummer(analyzeResult.getReferenzNummer());
                                gd.setSkontoTage(analyzeResult.getSkontoTage());
                                gd.setSkontoProzent(analyzeResult.getSkontoProzent());
                                gd.setNettoTage(analyzeResult.getNettoTage());
                                gd.setBereitsGezahlt(analyzeResult.getBereitsGezahlt());
                                gd.setZahlungsart(analyzeResult.getZahlungsart());
                                gd.setAiConfidence(analyzeResult.getAiConfidence());
                                gd.setAiRawJson(analyzeResult.getAiRawJson());
                                gd.setAnalysiertAm(LocalDateTime.now());

                                // Dokumenttyp aus Analyse übernehmen, falls erkannt
                                if (analyzeResult.getDokumentTyp() != null) {
                                        dokument.setTyp(analyzeResult.getDokumentTyp());
                                }

                                // Geschäftsdaten speichern und verknüpfen
                                gd = geschaeftsdokumentRepository.saveAndFlush(gd);
                                dokument.setGeschaeftsdaten(gd);
                                dokument = dokumentRepository.saveAndFlush(dokument);

                                standardKostenstelleAutoAssigner.applyIfApplicable(dokument);
                                // Vorauskasse-Lieferant? Dann ist die Rechnung schon bezahlt und
                                // darf nicht in den Offenen Posten stehen.
                                vorauskasseAutoAssigner.applyIfApplicable(dokument);
                                // Wie beim E-Mail-Import: Vorgänger und Nachfolger in der
                                // Dokumentenkette (Angebot ↔ AB ↔ Lieferschein ↔ Rechnung) suchen.
                                belegZuordnungService.ordneEin(dokument);
                        } else {
                                log.warn("Analyse ergab keine Ergebnisse für Dokument {}", dokument.getId());
                        }
                } catch (Exception e) {
                        log.error("Fehler bei synchroner Analyse für Dokument {}: {}", dokument.getId(), e.getMessage(),
                                        e);
                        // Dokument trotzdem speichern, nur ohne Geschäftsdaten
                }

                return toDto(dokument);
        }

        /** Größte erlaubte Papierrechnung – wie beim Belegscanner. */
        static final long MAX_RECHNUNG_BYTES = 25L * 1024 * 1024;
        private static final Set<String> RECHNUNG_ENDUNGEN = Set.of(".pdf", ".jpg", ".jpeg", ".png");
        private static final Set<String> RECHNUNG_MIME_TYPEN = Set.of("application/pdf", "image/jpeg", "image/png");

        /**
         * Papierrechnung zu einer Bestellung: legt für den Lieferanten des
         * Bestelldokuments eine Rechnung an, hängt sie sofort an das Bestelldokument
         * und liest sie danach im Hintergrund aus (ZUGFeRD/XML/KI). Das Auslesen
         * ordnet sie zusätzlich weiteren passenden Lieferscheinen zu.
         *
         * @param bestellDokumentId Auftragsbestätigung oder Lieferschein
         * @param hochgeladenVon    wer hochlädt, darf fehlen
         * @return die neue Rechnung (noch ohne Geschäftsdaten)
         * @throws java.util.NoSuchElementException wenn das Bestelldokument fehlt
         * @throws IllegalArgumentException         bei falscher Datei oder falschem Dokumenttyp
         */
        @Transactional
        public LieferantDokument rechnungZuBestellungHochladen(Long bestellDokumentId, MultipartFile datei,
                        Mitarbeiter hochgeladenVon) throws IOException {
                LieferantDokument bestellung = dokumentRepository.findById(bestellDokumentId).orElseThrow();
                if (!RechnungsVorschlagService.istBestellDokument(bestellung)) {
                        throw new BelegAbgelehntException(
                                        "Eine Rechnung lässt sich nur an eine Auftragsbestätigung oder einen Lieferschein hängen.");
                }
                Lieferanten lieferant = bestellung.getLieferant();
                if (lieferant == null || lieferant.getId() == null) {
                        throw new BelegAbgelehntException("Die Bestellung hat keinen Lieferanten.");
                }
                String originalFilename = pruefeRechnungsdatei(datei);

                Path lieferantDir = Path.of(uploadPath, "lieferanten", lieferant.getId().toString())
                                .toAbsolutePath().normalize();
                Files.createDirectories(lieferantDir);
                String storedFilename = UUID.randomUUID() + "_" + originalFilename;
                Path targetPath = lieferantDir.resolve(storedFilename).normalize();
                if (!targetPath.startsWith(lieferantDir)) {
                        throw new SecurityException("Ungültiger Dateipfad: Verzeichnistraversal erkannt");
                }
                try (var eingabe = datei.getInputStream()) {
                        Files.copy(eingabe, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                        LieferantDokument rechnung = legeRechnungAn(bestellung, lieferant, originalFilename,
                                        storedFilename, hochgeladenVon);
                        // Rollt die Transaktion später noch zurück, darf keine verwaiste Datei bleiben.
                        loescheDateiBeiRollback(targetPath);
                        belegZuordnungService.analysiereUndOrdneEinNachCommit(rechnung.getId());
                        // Nur IDs (DSGVO)
                        log.info("[Bestellübersicht] Rechnung {} zu Bestelldokument {} hochgeladen",
                                        rechnung.getId(), bestellDokumentId);
                        return rechnung;
                } catch (RuntimeException e) {
                        loescheDateiStill(targetPath);
                        throw e;
                }
        }

        private LieferantDokument legeRechnungAn(LieferantDokument bestellung, Lieferanten lieferant,
                        String originalFilename, String storedFilename, Mitarbeiter hochgeladenVon) {
                LieferantDokument rechnung = new LieferantDokument();
                rechnung.setLieferant(lieferant);
                rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
                rechnung.setOriginalDateiname(originalFilename);
                rechnung.setGespeicherterDateiname(storedFilename);
                rechnung.setUploadDatum(LocalDateTime.now());
                rechnung.setUploadedBy(hochgeladenVon);
                // Sofort an die Bestellung: Der Nutzer hat sie von der Karte aus hochgeladen.
                rechnung.getVerknuepfteDokumente().add(bestellung);
                bestellung.getVerknuepftVon().add(rechnung);
                return dokumentRepository.saveAndFlush(rechnung);
        }

        private static void loescheDateiBeiRollback(Path datei) {
                if (!org.springframework.transaction.support.TransactionSynchronizationManager
                                .isSynchronizationActive()) {
                        return;
                }
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                        @Override
                                        public void afterCompletion(int status) {
                                                if (status == STATUS_ROLLED_BACK) {
                                                        loescheDateiStill(datei);
                                                }
                                        }
                                });
        }

        private static void loescheDateiStill(Path datei) {
                try {
                        Files.deleteIfExists(datei);
                } catch (IOException e) {
                        log.warn("[Bestellübersicht] Hochgeladene Datei konnte nicht entfernt werden: {}",
                                        e.getClass().getSimpleName());
                }
        }

        /**
         * Prüft Größe und Art der Datei und liefert einen bereinigten Dateinamen.
         * Erlaubt sind nur PDF, JPG und PNG – Dateiendung UND Inhaltstyp.
         */
        static String pruefeRechnungsdatei(MultipartFile datei) {
                if (datei == null || datei.isEmpty()) {
                        throw new BelegAbgelehntException("Datei fehlt.");
                }
                if (datei.getSize() > MAX_RECHNUNG_BYTES) {
                        throw new BelegAbgelehntException("Datei zu groß (höchstens 25 MB).");
                }
                String roh = datei.getOriginalFilename();
                if (roh == null || roh.isBlank() || roh.length() > 255) {
                        throw new BelegAbgelehntException("Ungültiger Dateiname.");
                }
                String name;
                try {
                        Path nurName = Path.of(roh.replace('\\', '/')).getFileName();
                        if (nurName == null) {
                                // "/" oder "\\" – kein Dateiname übrig
                                throw new BelegAbgelehntException("Ungültiger Dateiname.");
                        }
                        name = nurName.toString().replaceAll("[\\\\/:*?\"<>|]", "_");
                } catch (java.nio.file.InvalidPathException e) {
                        throw new BelegAbgelehntException("Ungültiger Dateiname.");
                }
                String klein = name.toLowerCase(java.util.Locale.ROOT);
                int punkt = klein.lastIndexOf('.');
                String endung = punkt >= 0 ? klein.substring(punkt) : "";
                String mime = datei.getContentType() != null
                                ? datei.getContentType().toLowerCase(java.util.Locale.ROOT) : "";
                if (name.isBlank() || name.startsWith(".") || !RECHNUNG_ENDUNGEN.contains(endung)
                                || !RECHNUNG_MIME_TYPEN.contains(mime)) {
                        throw new BelegAbgelehntException("Nur PDF, JPG oder PNG erlaubt.");
                }
                if (!inhaltPasstZurEndung(datei, endung)) {
                        // Endung und Typ lassen sich fälschen – der Dateianfang nicht so leicht.
                        throw new BelegAbgelehntException("Nur PDF, JPG oder PNG erlaubt.");
                }
                return name;
        }

        /** Prüft die ersten Bytes: %PDF, JPEG FF D8 FF, PNG 89 50 4E 47. */
        private static boolean inhaltPasstZurEndung(MultipartFile datei, String endung) {
                byte[] anfang = new byte[4];
                int gelesen;
                try (var eingabe = datei.getInputStream()) {
                        gelesen = eingabe.readNBytes(anfang, 0, anfang.length);
                } catch (IOException e) {
                        return false;
                }
                int[] erwartet = switch (endung) {
                        case ".pdf" -> new int[] { 0x25, 0x50, 0x44, 0x46 };
                        case ".png" -> new int[] { 0x89, 0x50, 0x4E, 0x47 };
                        default -> new int[] { 0xFF, 0xD8, 0xFF };
                };
                if (gelesen < erwartet.length) {
                        return false;
                }
                for (int i = 0; i < erwartet.length; i++) {
                        if ((anfang[i] & 0xFF) != erwartet[i]) {
                                return false;
                        }
                }
                return true;
        }

        /**
         * Ordnet ein Dokument prozentual Projekten zu.
         */
        @Transactional
        public LieferantDokumentDto.Response zuordnenProjekte(Long dokumentId,
                        LieferantDokumentDto.ProjektZuordnungRequest request) {
                LieferantDokument dokument = dokumentRepository.findById(dokumentId)
                                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden"));

                // Validierung: Summe der Prozente muss 100 sein
                int summe = request.getAnteile().stream()
                                .mapToInt(LieferantDokumentDto.ProjektAnteil::getProzent)
                                .sum();
                if (summe != 100) {
                        throw new RuntimeException("Die Summe der Prozente muss 100 ergeben, ist aber " + summe);
                }

                // Bruttobetrag für Berechnung holen
                BigDecimal betragBrutto = null;
                if (dokument.getGeschaeftsdaten() != null) {
                        betragBrutto = dokument.getGeschaeftsdaten().getBetragBrutto();
                }

                // Alte Zuordnungen entfernen
                dokument.getProjektAnteile().clear();

                // Neue Zuordnungen erstellen
                for (LieferantDokumentDto.ProjektAnteil anteil : request.getAnteile()) {
                        Projekt projekt = projektRepository.findById(anteil.getProjektId())
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Projekt nicht gefunden: " + anteil.getProjektId()));

                        LieferantDokumentProjektAnteil pa = new LieferantDokumentProjektAnteil();
                        pa.setDokument(dokument);
                        pa.setProjekt(projekt);
                        pa.setProzent(anteil.getProzent());
                        pa.setBeschreibung(anteil.getBeschreibung());

                        // Betrag berechnen falls vorhanden
                        if (betragBrutto != null) {
                                pa.berechneAnteil(betragBrutto);
                        }

                        dokument.getProjektAnteile().add(pa);
                }

                dokument = dokumentRepository.save(dokument);
                return toDto(dokument);
        }

        /**
         * Fügt Verknüpfungen zu einem Dokument hinzu.
         */
        @Transactional
        public LieferantDokumentDto.Response addVerknuepfungen(Long dokumentId, Set<Long> verknuepfteIds) {
                LieferantDokument dokument = dokumentRepository.findById(dokumentId)
                                .orElseThrow(() -> new RuntimeException("Dokument nicht gefunden"));

                Set<LieferantDokument> neueVerknuepfungen = new HashSet<>(
                                dokumentRepository.findAllById(verknuepfteIds));
                dokument.getVerknuepfteDokumente().addAll(neueVerknuepfungen);

                dokument = dokumentRepository.save(dokument);
                return toDto(dokument);
        }

        private LieferantDokumentDto.Response toDto(LieferantDokument dok) {
                // URL konstruieren aus Attachment-Referenz
                String url = null;
                if (dok.getAttachment() != null && dok.getAttachment().getEmail() != null) {
                        // Dokument aus Email-Attachment - verwende den UnifiedEmailController Endpunkt
                        Long emailId = dok.getAttachment().getEmail().getId();
                        Long attachmentId = dok.getAttachment().getId();
                        url = "/api/emails/" + emailId + "/attachments/" + attachmentId;
                } else if (dok.getGespeicherterDateiname() != null) {
                        // Manuell hochgeladenes Dokument oder Vendor-Invoice
                        url = "/api/lieferanten/" + dok.getLieferant().getId() + "/dokumente/" + dok.getId()
                                        + "/download";
                }

                LieferantDokumentDto.Response.ResponseBuilder builder = LieferantDokumentDto.Response.builder()
                                .id(dok.getId())
                                .lieferantId(dok.getLieferant().getId())
                                .lieferantName(dok.getLieferant().getLieferantenname())
                                .typ(dok.getTyp())
                                .originalDateiname(dok.getEffektiverDateiname())
                                .gespeicherterDateiname(dok.getEffektiverGespeicherterDateiname())
                                .uploadDatum(dok.getUploadDatum())
                                .url(url)
                                .uploadedByName(dok.getUploadedBy() != null
                                                ? dok.getUploadedBy().getVorname() + " "
                                                                + dok.getUploadedBy().getNachname()
                                                : null)

                                .projektAnteile(dok.getProjektAnteile().stream()
                                                .filter(pa -> pa.getProjekt() != null || pa.getKostenstelle() != null)
                                                .map(pa -> {
                                                        LieferantDokumentDto.ProjektAnteilRef.ProjektAnteilRefBuilder b =
                                                                LieferantDokumentDto.ProjektAnteilRef.builder()
                                                                        .id(pa.getId())
                                                                        .prozent(pa.getProzent())
                                                                        .berechneterBetrag(pa.getBerechneterBetrag())
                                                                        .beschreibung(pa.getBeschreibung())
                                                                        .zugeordnetAm(pa.getZugeordnetAm());
                                                        if (pa.getZugeordnetVon() != null) {
                                                                b.zugeordnetVonName(pa.getZugeordnetVon().getDisplayName());
                                                        }
                                                        if (pa.getProjekt() != null) {
                                                                b.projektId(pa.getProjekt().getId())
                                                                 .projektName(pa.getProjekt().getBauvorhaben())
                                                                 .auftragsnummer(pa.getProjekt().getAuftragsnummer());
                                                        }
                                                        if (pa.getKostenstelle() != null) {
                                                                b.kostenstelleId(pa.getKostenstelle().getId())
                                                                 .kostenstelleName(pa.getKostenstelle().getBezeichnung());
                                                        }
                                                        return b.build();
                                                })
                                                .collect(Collectors.toList()))
                                .verknuepfteDokumente(dok.getVerknuepfteDokumente().stream()
                                                .map(v -> LieferantDokumentDto.VerknuepftesDoc.builder()
                                                                .id(v.getId())
                                                                .typ(v.getTyp())
                                                                .originalDateiname(v.getEffektiverDateiname())
                                                                .uploadDatum(v.getUploadDatum())
                                                                .build())
                                                .collect(Collectors.toList()));

                // Geschäftsdaten hinzufügen falls vorhanden
                if (dok.getGeschaeftsdaten() != null) {
                        LieferantGeschaeftsdokument gd = dok.getGeschaeftsdaten();
                        builder.geschaeftsdaten(LieferantDokumentDto.GeschaeftsdatenRef.builder()
                                        .dokumentNummer(gd.getDokumentNummer())
                                        .dokumentDatum(gd.getDokumentDatum())
                                        .betragNetto(gd.getBetragNetto())
                                        .betragBrutto(gd.getBetragBrutto())
                                        .liefertermin(gd.getLiefertermin())
                                        .bestellnummer(gd.getBestellnummer())
                                        .referenzNummer(gd.getReferenzNummer())
                                        .aiConfidence(gd.getAiConfidence())
                                        // Zahlungsstatus
                                        .zahlungsziel(gd.getZahlungsziel())
                                        .bezahlt(gd.getBezahlt())
                                        .bezahltAm(gd.getBezahltAm())
                                        .bereitsGezahlt(gd.getBereitsGezahlt())
                                        .zahlungsart(gd.getZahlungsart())
                                        // Skonto-Konditionen
                                        .skontoTage(gd.getSkontoTage())
                                        .skontoProzent(gd.getSkontoProzent())
                                        .nettoTage(gd.getNettoTage())
                                        .tatsaechlichGezahlt(gd.getTatsaechlichGezahlt())
                                        .mitSkonto(gd.getMitSkonto())
                                        // Neu: Flag für manuelle Prüfung
                                        .manuellePruefungErforderlich(gd.getManuellePruefungErforderlich())
                                        .datenquelle(gd.getDatenquelle())
                                        .build());
                }

                return builder.build();
        }

        /**
         * Löscht ein Lieferantendokument physisch. RECHNUNG und GUTSCHRIFT sind
         * aus GoBD-Gründen gesperrt — zuerst Typ auf SONSTIG ändern.
         *
         * @throws jakarta.persistence.EntityNotFoundException wenn das Dokument nicht existiert
         * @throws IllegalArgumentException wenn der Dokumenttyp GoBD-geschützt ist
         */
        @Transactional
        public void loescheDokument(Long id, String adminUsername) {
                LieferantDokument dokument = dokumentRepository.findById(id)
                        .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Dokument nicht gefunden: " + id));

                if (LieferantDokumentTyp.RECHNUNG == dokument.getTyp()
                        || LieferantDokumentTyp.GUTSCHRIFT == dokument.getTyp()) {
                        throw new IllegalArgumentException(
                                "Dokument vom Typ " + dokument.getTyp() + " kann nicht gelöscht werden (GoBD-Aufbewahrungspflicht). "
                                + "Falls es eine Fehlklassifikation ist, bitte zuerst den Typ auf 'Sonstiges' ändern.");
                }

                log.info("Admin '{}' löscht Lieferantendokument ID={}", adminUsername, id);

                // EmailAttachment entkoppeln – aiProcessed NICHT auf false setzen,
                // damit der nächste Verarbeitungslauf das Dokument nicht neu anlegt.
                emailAttachmentRepository.findByLieferantDokumentId(id).forEach(att -> {
                        att.setLieferantDokument(null);
                        emailAttachmentRepository.save(att);
                });

                // Physische Datei nur löschen, wenn sie exklusiv diesem Dokument gehört
                // (manueller Upload). Bei Email-Attachment oder mobilem Beleg liegt die
                // Datei im jeweils anderen Modul und wird dort weiter benötigt.
                if (dokument.getAttachment() == null && dokument.getBeleg() == null
                                && dokument.getGespeicherterDateiname() != null
                                && dokument.getLieferant() != null) {
                        Path lieferantDir = Path.of(uploadPath, "lieferanten", dokument.getLieferant().getId().toString())
                                        .toAbsolutePath().normalize();
                        Path filePath = lieferantDir.resolve(dokument.getGespeicherterDateiname()).normalize();
                        if (filePath.startsWith(lieferantDir)) {
                                try {
                                        Files.deleteIfExists(filePath);
                                } catch (IOException e) {
                                        log.warn("Physische Datei konnte nicht gelöscht werden: {}", filePath, e);
                                }
                        }
                }

                // Kettenverknüpfungen lösen
                dokument.getVerknuepfteDokumente().clear();
                dokument.getVerknuepftVon().forEach(vd -> vd.getVerknuepfteDokumente().remove(dokument));
                dokument.getProjektAnteile().clear();
                dokument.setAttachment(null);
                dokumentRepository.saveAndFlush(dokument);

                dokumentRepository.delete(dokument);
        }
}
