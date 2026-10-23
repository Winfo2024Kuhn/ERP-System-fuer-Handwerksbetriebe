package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.junit.jupiter.api.Test;

class KategorieBaumTest {

    static Produktkategorie ordner(long id, String name, Produktkategorie parent) {
        Produktkategorie k = new Produktkategorie();
        k.setId(id);
        k.setBezeichnung(name);
        k.setUebergeordneteKategorie(parent);
        return k;
    }

    @Test
    void bautBaumMitPfadenUndUnterordnern() {
        Produktkategorie metallbau = ordner(1, "Metallbau", null);
        Produktkategorie gelaender = ordner(2, "Geländer", metallbau);
        Produktkategorie edelstahl = ordner(3, "Edelstahl", gelaender);
        Produktkategorie tore = ordner(4, "Tore", metallbau);
        Produktkategorie fliesen = ordner(5, "Fliesen", null);

        KategorieBaum baum = KategorieBaum.aus(List.of(edelstahl, metallbau, gelaender, tore, fliesen));

        assertThat(baum.hauptordner()).containsExactly(1L, 5L);
        assertThat(baum.kinder(1L)).containsExactly(2L, 4L);
        assertThat(baum.kinder(5L)).isEmpty();
        assertThat(baum.hatKinder(2L)).isTrue();
        assertThat(baum.hatKinder(3L)).isFalse();
        assertThat(baum.mitUnterordnern(1L)).containsExactly(1L, 2L, 4L, 3L);
        assertThat(baum.pfad(3L)).isEqualTo("Metallbau > Geländer > Edelstahl");
        assertThat(baum.get(4L)).isSameAs(tore);
        assertThat(baum.ids()).hasSize(5);
        assertThat(KategorieBaum.parentId(edelstahl)).isEqualTo(2L);
        assertThat(KategorieBaum.parentId(fliesen)).isNull();
    }

    @Test
    void ordnerMitUnbekanntemParentGiltAlsHauptordner() {
        Produktkategorie fremd = ordner(9, "Ohne Eltern", ordner(99, "nicht geladen", null));

        KategorieBaum baum = KategorieBaum.aus(List.of(fremd));

        assertThat(baum.hauptordner()).containsExactly(9L);
        assertThat(baum.pfad(9L)).isEqualTo("Ohne Eltern");
        assertThat(baum.pfad(123L)).isEmpty();
    }

    @Test
    void ringInAltdatenFuehrtNichtZuEndlosschleife() {
        Produktkategorie a = ordner(1, "A", null);
        Produktkategorie b = ordner(2, "B", a);
        a.setUebergeordneteKategorie(b);

        KategorieBaum baum = KategorieBaum.aus(List.of(a, b));

        assertThat(baum.pfad(1L)).contains("A").contains("B");
        assertThat(baum.mitUnterordnern(1L)).containsExactly(1L, 2L);
    }
}
