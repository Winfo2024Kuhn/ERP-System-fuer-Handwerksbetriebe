package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Organigramm;
import org.example.kalkulationsprogramm.repository.OrganigrammRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganigrammService {

    private final OrganigrammRepository repo;

    public OrganigrammService(OrganigrammRepository repo) {
        this.repo = repo;
    }

    public List<Organigramm> findAll() {
        return repo.findAll();
    }

    public Optional<Organigramm> findByName(String name) {
        return repo.findByName(name);
    }

    /**
     * Create or update an Organigramm by name.
     * If one with the same name exists, its content is updated.
     */
    public Organigramm save(String name, String content) {
        Organigramm org = repo.findByName(name).orElseGet(() -> {
            Organigramm o = new Organigramm();
            o.setName(name);
            return o;
        });
        org.setContent(content);
        return repo.save(org);
    }

    public Organigramm rename(String oldName, String newName) {
        Organigramm org = repo.findByName(oldName)
                .orElseThrow(() -> new IllegalArgumentException("Organigramm nicht gefunden: " + oldName));
        org.setName(newName);
        return repo.save(org);
    }

    public void deleteByName(String name) {
        repo.deleteByName(name);
    }
}
