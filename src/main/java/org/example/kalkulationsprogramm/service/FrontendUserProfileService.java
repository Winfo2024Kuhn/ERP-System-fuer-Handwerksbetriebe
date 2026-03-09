package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FrontendUserProfileService {

    private final FrontendUserProfileRepository repository;
    private final EmailSignatureRepository emailSignatureRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    @Transactional(readOnly = true)
    public List<FrontendUserProfile> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<FrontendUserProfile> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendUserProfile> findByDisplayName(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }
        String normalized = displayName.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByDisplayNameIgnoreCase(normalized);
    }

    @Transactional
    public FrontendUserProfile saveOrUpdate(FrontendUserProfile profile, Long defaultSignatureId, Long mitarbeiterId) {
        EmailSignature signature = null;
        if (defaultSignatureId != null) {
            signature = emailSignatureRepository.findById(defaultSignatureId).orElseThrow();
        }
        Mitarbeiter mitarbeiter = null;
        if (mitarbeiterId != null) {
            mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId).orElse(null);
        }
        FrontendUserProfile target;
        if (profile.getId() != null) {
            target = repository.findById(profile.getId()).orElseThrow();
            target.setDisplayName(profile.getDisplayName());
            target.setShortCode(profile.getShortCode());
        } else {
            target = profile;
        }
        target.setDefaultSignature(signature);
        target.setMitarbeiter(mitarbeiter);
        return repository.save(target);
    }

    @Transactional
    public FrontendUserProfile setDefaultSignature(Long profileId, Long signatureId) {
        FrontendUserProfile profile = repository.findById(profileId).orElseThrow();
        EmailSignature signature = null;
        if (signatureId != null) {
            signature = emailSignatureRepository.findById(signatureId).orElseThrow();
        }
        profile.setDefaultSignature(signature);
        return repository.save(profile);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
