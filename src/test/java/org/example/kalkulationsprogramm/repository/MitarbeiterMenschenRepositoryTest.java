package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class MitarbeiterMenschenRepositoryTest {
    @Autowired MitarbeiterRepository repository;

    @Test
    void menschenFilterErhaltenAusgeschiedeneUndTrennenHeutigeKontofuehrung() {
        Mitarbeiter aktiv = person("aktiv", MitarbeiterArt.MENSCH, true, true);
        Mitarbeiter ohneKonto = person("ohne-konto", MitarbeiterArt.MENSCH, true, false);
        Mitarbeiter ausgeschieden = person("ausgeschieden", MitarbeiterArt.MENSCH, false, true);
        Mitarbeiter system = person("system", MitarbeiterArt.SYSTEM, true, false);
        person("system-inaktiv", MitarbeiterArt.SYSTEM, false, false);

        assertThat(repository.findMenschen()).containsExactlyInAnyOrder(aktiv, ohneKonto, ausgeschieden);
        assertThat(repository.findAktiveMenschen()).containsExactlyInAnyOrder(aktiv, ohneKonto);
        assertThat(repository.findAktiveMenschenMitZeitkonto()).containsExactly(aktiv);
        // Die alte API behält bewusst ihre bisherige Semantik.
        assertThat(repository.findByAktivTrue()).containsExactlyInAnyOrder(aktiv, ohneKonto, system);
    }

    @Test
    void tokenLoginMitUndOhneLockSchliesstSystemAusFunnelLookupBleibtUngefiltert() {
        Mitarbeiter mensch = person("mensch", MitarbeiterArt.MENSCH, true, false);
        Mitarbeiter system = person("system", MitarbeiterArt.SYSTEM, true, false);
        person("ausgeschieden", MitarbeiterArt.MENSCH, false, true);
        assertThat(repository.findByLoginToken("system")).contains(system);
        for (String token : new String[]{"system", "ausgeschieden", "unbekannt", "'; DROP TABLE mitarbeiter; --"}) {
            assertThat(repository.findByLoginTokenAndAktivTrue(token)).isEmpty();
            assertThat(repository.findByLoginTokenAndAktivTrueForUpdate(token)).isEmpty();
        }
        assertThat(repository.findByLoginTokenAndAktivTrue("mensch")).contains(mensch);
        assertThat(repository.findByLoginTokenAndAktivTrueForUpdate("mensch")).contains(mensch);
    }

    private Mitarbeiter person(String token, MitarbeiterArt art, boolean aktiv, boolean konto) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setLoginToken(token);
        m.setArt(art);
        m.setAktiv(aktiv);
        m.setFuehrtZeitkonto(konto);
        return repository.saveAndFlush(m);
    }
}
