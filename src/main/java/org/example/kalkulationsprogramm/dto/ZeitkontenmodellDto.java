package org.example.kalkulationsprogramm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.example.kalkulationsprogramm.domain.Zeitkontenmodell;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;

public record ZeitkontenmodellDto(Long id, Long version, String bezeichnung, Arbeitszeit arbeitszeit) {
    public record Create(@NotBlank @Size(max = 255) String bezeichnung,
            @NotNull @Valid Arbeitszeit arbeitszeit) {}
    public record Update(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 255) String bezeichnung, @NotNull @Valid Arbeitszeit arbeitszeit) {}
    /** Vollständige explizite Wochenwerte; null bei Uhrzeiten hebt Einschränkungen auf. */
    public record Arbeitszeit(
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal montagStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal dienstagStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal mittwochStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal donnerstagStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal freitagStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal samstagStunden,
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal sonntagStunden,
            LocalTime buchungStartZeit, LocalTime buchungEndeZeit) {
        public static Arbeitszeit from(Zeitkontenmodell v) {
            return new Arbeitszeit(v.getMontagStunden(), v.getDienstagStunden(), v.getMittwochStunden(), v.getDonnerstagStunden(), v.getFreitagStunden(), v.getSamstagStunden(), v.getSonntagStunden(), v.getBuchungStartZeit(), v.getBuchungEndeZeit());
        }
        public static Arbeitszeit from(ZeitkontoVersion v) {
            return new Arbeitszeit(v.getMontagStunden(), v.getDienstagStunden(), v.getMittwochStunden(), v.getDonnerstagStunden(), v.getFreitagStunden(), v.getSamstagStunden(), v.getSonntagStunden(), v.getBuchungStartZeit(), v.getBuchungEndeZeit());
        }
        public void kopiereNach(Zeitkontenmodell v) {
            v.setMontagStunden(montagStunden);
            v.setDienstagStunden(dienstagStunden);
            v.setMittwochStunden(mittwochStunden);
            v.setDonnerstagStunden(donnerstagStunden);
            v.setFreitagStunden(freitagStunden);
            v.setSamstagStunden(samstagStunden);
            v.setSonntagStunden(sonntagStunden);
            v.setBuchungStartZeit(buchungStartZeit);
            v.setBuchungEndeZeit(buchungEndeZeit);
        }
        public void kopiereNach(ZeitkontoVersion v) {
            v.setMontagStunden(montagStunden);
            v.setDienstagStunden(dienstagStunden);
            v.setMittwochStunden(mittwochStunden);
            v.setDonnerstagStunden(donnerstagStunden);
            v.setFreitagStunden(freitagStunden);
            v.setSamstagStunden(samstagStunden);
            v.setSonntagStunden(sonntagStunden);
            v.setBuchungStartZeit(buchungStartZeit);
            v.setBuchungEndeZeit(buchungEndeZeit);
        }
    }
    public static ZeitkontenmodellDto from(Zeitkontenmodell v) {
        return new ZeitkontenmodellDto(v.getId(), v.getVersion(), v.getBezeichnung(), Arbeitszeit.from(v));
    }
}
