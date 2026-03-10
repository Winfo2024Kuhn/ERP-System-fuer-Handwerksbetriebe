package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterDokument;
import org.example.kalkulationsprogramm.domain.Qualifikation;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterDokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Service
@RequiredArgsConstructor
public class MitarbeiterService {

    private final MitarbeiterRepository repository;
    private final MitarbeiterDokumentRepository dokumentRepository;
    private final org.example.kalkulationsprogramm.repository.MitarbeiterNotizRepository notizRepository;
    private final AbteilungRepository abteilungRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${zeiterfassung.base-url:http://localhost:8080}")
    private String zeiterfassungBaseUrl;

    @Transactional(readOnly = true)
    public List<MitarbeiterDto> list() {
        return repository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<MitarbeiterDto> findById(Long id) {
        return repository.findById(id).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Optional<MitarbeiterDto> findByToken(String token) {
        // Nur aktive Mitarbeiter dürfen sich einloggen
        return repository.findByLoginTokenAndAktivTrue(token).map(this::mapToDto);
    }

    @Transactional
    public MitarbeiterDto save(Long id, MitarbeiterErstellenDto dto) {
        Mitarbeiter entity;
        if (id != null) {
            entity = repository.findById(id).orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));
        } else {
            entity = new Mitarbeiter();
        }
        entity.setVorname(dto.getVorname());
        entity.setNachname(dto.getNachname());
        entity.setStrasse(dto.getStrasse());
        entity.setPlz(dto.getPlz());
        entity.setOrt(dto.getOrt());
        entity.setEmail(dto.getEmail());
        entity.setTelefon(dto.getTelefon());
        entity.setFestnetz(dto.getFestnetz());
        entity.setQualifikation(Qualifikation.fromString(dto.getQualifikation()));
        entity.setStundenlohn(dto.getStundenlohn());
        entity.setGeburtstag(dto.getGeburtstag());
        entity.setEintrittsdatum(dto.getEintrittsdatum());
        entity.setJahresUrlaub(dto.getJahresUrlaub());
        entity.setAktiv(dto.getAktiv() != null ? dto.getAktiv() : true);

        // Abteilungen zuweisen (N:M)
        if (dto.getAbteilungIds() != null && !dto.getAbteilungIds().isEmpty()) {
            Set<Abteilung> abteilungen = new HashSet<>();
            for (Long abteilungId : dto.getAbteilungIds()) {
                Abteilung abteilung = abteilungRepository.findById(abteilungId)
                        .orElseThrow(() -> new RuntimeException("Abteilung nicht gefunden: " + abteilungId));
                abteilungen.add(abteilung);
            }
            entity.setAbteilungen(abteilungen);
        } else {
            entity.getAbteilungen().clear();
        }

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public MitarbeiterDokumentResponseDto uploadDokument(Long mitarbeiterId, MultipartFile file,
            DokumentGruppe gruppe) {
        Mitarbeiter mitarbeiter = repository.findById(mitarbeiterId)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        try {
            Path uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
            String savedFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            Path targetLocation = uploadPath.resolve(savedFileName);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            MitarbeiterDokument dok = new MitarbeiterDokument();
            dok.setOriginalDateiname(originalFileName);
            dok.setGespeicherterDateiname(savedFileName);
            dok.setDateityp(file.getContentType());
            dok.setDateigroesse(file.getSize());
            dok.setUploadDatum(LocalDate.now());
            dok.setDokumentGruppe(gruppe != null ? gruppe : DokumentGruppe.DIVERSE_DOKUMENTE);
            dok.setMitarbeiter(mitarbeiter);

            mitarbeiter.getDokumente().add(dok);
            MitarbeiterDokument saved = dokumentRepository.save(dok);

            return mapToDokumentDto(saved);
        } catch (IOException ex) {
            throw new RuntimeException("Konnte Datei nicht speichern", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<MitarbeiterDokumentResponseDto> listDokumente(Long mitarbeiterId) {
        return dokumentRepository.findByMitarbeiterId(mitarbeiterId).stream()
                .map(this::mapToDokumentDto)
                .collect(Collectors.toList());
    }

    private MitarbeiterDto mapToDto(Mitarbeiter m) {
        MitarbeiterDto dto = new MitarbeiterDto();
        dto.setId(m.getId());
        dto.setVorname(m.getVorname());
        dto.setNachname(m.getNachname());
        dto.setStrasse(m.getStrasse());
        dto.setPlz(m.getPlz());
        dto.setOrt(m.getOrt());
        dto.setEmail(m.getEmail());
        dto.setTelefon(m.getTelefon());
        dto.setFestnetz(m.getFestnetz());
        dto.setQualifikation(m.getQualifikation() != null ? m.getQualifikation().getBezeichnung() : null);
        dto.setStundenlohn(m.getStundenlohn());
        dto.setGeburtstag(m.getGeburtstag());
        dto.setEintrittsdatum(m.getEintrittsdatum());
        dto.setJahresUrlaub(m.getJahresUrlaub());
        dto.setAktiv(m.getAktiv());
        dto.setLoginToken(m.getLoginToken());

        // Abteilungen (N:M)
        if (m.getAbteilungen() != null && !m.getAbteilungen().isEmpty()) {
            dto.setAbteilungIds(m.getAbteilungen().stream()
                    .map(Abteilung::getId)
                    .collect(Collectors.toList()));
            dto.setAbteilungNames(m.getAbteilungen().stream()
                    .map(Abteilung::getName)
                    .collect(Collectors.joining(", ")));
        }
        return dto;
    }

    // ==================== QR-CODE METHODS ====================

    @Transactional
    public String generateLoginToken(Long id) {
        Mitarbeiter mitarbeiter = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));
        String token = UUID.randomUUID().toString();
        mitarbeiter.setLoginToken(token);
        repository.save(mitarbeiter);
        return token;
    }

    public byte[] generateQrCode(Long id, int width, int height) {
        Mitarbeiter mitarbeiter = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        String token = mitarbeiter.getLoginToken();
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Kein Login-Token vorhanden. Bitte erst Token generieren.");
        }

        // QR-Code Inhalt: Web-URL für Zeiterfassungs-App
        String qrContent = zeiterfassungBaseUrl + "/zeiterfassung/?token=" + token;

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Fehler beim Generieren des QR-Codes", e);
        }
    }

    private MitarbeiterDokumentResponseDto mapToDokumentDto(MitarbeiterDokument d) {
        MitarbeiterDokumentResponseDto dto = new MitarbeiterDokumentResponseDto();
        dto.setId(d.getId());
        dto.setOriginalDateiname(d.getOriginalDateiname());
        dto.setDateityp(d.getDateityp());
        dto.setDateigroesse(d.getDateigroesse());
        dto.setUploadDatum(d.getUploadDatum());
        dto.setDokumentGruppe(d.getDokumentGruppe());
        // URL für Download/Vorschau
        dto.setUrl("/api/dokumente/" + d.getGespeicherterDateiname());
        return dto;
    }
    // ==================== NOTIZEN METHODS ====================

    @Transactional(readOnly = true)
    public List<org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto> listNotizen(Long mitarbeiterId) {
        return notizRepository.findByMitarbeiterIdOrderByErstelltAmDesc(mitarbeiterId).stream()
                .map(n -> new org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto(
                        n.getId(),
                        n.getInhalt(),
                        n.getErstelltAm(),
                        n.getMitarbeiter().getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto createNotiz(Long mitarbeiterId,
            String inhalt) {
        Mitarbeiter mitarbeiter = repository.findById(mitarbeiterId)
                .orElseThrow(() -> new RuntimeException("Mitarbeiter nicht gefunden"));

        org.example.kalkulationsprogramm.domain.MitarbeiterNotiz notiz = new org.example.kalkulationsprogramm.domain.MitarbeiterNotiz();
        notiz.setInhalt(inhalt);
        notiz.setMitarbeiter(mitarbeiter);

        org.example.kalkulationsprogramm.domain.MitarbeiterNotiz saved = notizRepository.save(notiz);

        return new org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto(
                saved.getId(),
                saved.getInhalt(),
                saved.getErstelltAm(),
                saved.getMitarbeiter().getId());
    }

    @Transactional
    public void deleteNotiz(Long notizId) {
        notizRepository.deleteById(notizId);
    }
}
