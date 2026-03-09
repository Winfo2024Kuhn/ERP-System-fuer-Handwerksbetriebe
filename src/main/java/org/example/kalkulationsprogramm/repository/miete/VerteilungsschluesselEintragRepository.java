package org.example.kalkulationsprogramm.repository.miete;

import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.domain.miete.VerteilungsschluesselEintrag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerteilungsschluesselEintragRepository extends JpaRepository<VerteilungsschluesselEintrag, Long> {
    List<VerteilungsschluesselEintrag> findByVerteilungsschluessel(Verteilungsschluessel verteilungsschluessel);

    List<VerteilungsschluesselEintrag> findByMietpartei(Mietpartei mietpartei);
}
