package org.example.kalkulationsprogramm.service.ki;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Anbieter ohne Netz: merkt sich, was er bekommt, und antwortet fest. */
class TestAnbieter implements KiAnbieter {

    record Aufruf(KiAnfrage anfrage, String modell, String schluessel) {
    }

    private final KiAnbieterTyp typ;
    private final Set<KiFaehigkeit> faehigkeiten;
    private final boolean verschluesselt;
    final List<Aufruf> aufrufe = new ArrayList<>();
    List<KiModellInfo> modelle = List.of();
    int modellAbrufe;
    KiAnbieterFehler fehlerBeimLaden;
    /** Eigenes Modell für Ton (wie Voxtral bei Mistral); {@code null} = keins. */
    String tonModell;
    /** Formate, die dieser Anbieter trotz passender Fähigkeit nicht liest (wie MP4-Ton bei Mistral). */
    Set<String> nichtLesbareFormate = Set.of();

    TestAnbieter(KiAnbieterTyp typ, boolean verschluesselt, KiFaehigkeit... faehigkeiten) {
        this.typ = typ;
        this.verschluesselt = verschluesselt;
        this.faehigkeiten = faehigkeiten.length == 0 ? EnumSet.noneOf(KiFaehigkeit.class)
                : EnumSet.of(faehigkeiten[0], faehigkeiten);
    }

    static TestAnbieter mistral() {
        TestAnbieter mistral = new TestAnbieter(KiAnbieterTyp.MISTRAL, true, KiFaehigkeit.TEXT, KiFaehigkeit.BILD,
                KiFaehigkeit.PDF, KiFaehigkeit.AUDIO, KiFaehigkeit.WERKZEUGE);
        mistral.tonModell = "mistral-sprache";
        mistral.nichtLesbareFormate = Set.of("audio/mp4", "audio/aac");
        return mistral;
    }

    /** Ein Anbieter, der nur Text, Bilder und PDFs kann – für Ersatz und „aus“. */
    static TestAnbieter mistralNurDokumente() {
        return new TestAnbieter(KiAnbieterTyp.MISTRAL, true, KiFaehigkeit.TEXT, KiFaehigkeit.BILD, KiFaehigkeit.PDF);
    }

    static TestAnbieter gemini() {
        return new TestAnbieter(KiAnbieterTyp.GEMINI, false, KiFaehigkeit.values());
    }

    @Override
    public KiAnbieterTyp typ() {
        return typ;
    }

    @Override
    public String name() {
        return typ == KiAnbieterTyp.MISTRAL ? "Mistral" : "Google Gemini";
    }

    @Override
    public String beschreibung() {
        return "Test";
    }

    @Override
    public KiDatenstandort datenstandort() {
        return typ == KiAnbieterTyp.MISTRAL ? KiDatenstandort.EU : KiDatenstandort.WELTWEIT;
    }

    @Override
    public Set<KiFaehigkeit> faehigkeiten() {
        return faehigkeiten;
    }

    @Override
    public boolean schluesselVerschluesselt() {
        return verschluesselt;
    }

    @Override
    public Map<KiModellStufe, String> standardModelle() {
        Map<KiModellStufe, String> modelle = new EnumMap<>(KiModellStufe.class);
        for (KiModellStufe stufe : KiModellStufe.values()) {
            modelle.put(stufe, typ.kuerzel() + "-" + stufe.name().toLowerCase());
        }
        return modelle;
    }

    @Override
    public java.util.Optional<String> modellFuer(Set<KiFaehigkeit> gebraucht) {
        return tonModell != null && gebraucht.contains(KiFaehigkeit.AUDIO)
                ? java.util.Optional.of(tonModell) : java.util.Optional.empty();
    }

    @Override
    public boolean liest(KiTeil.Datei datei) {
        return !nichtLesbareFormate.contains(datei.mimeType());
    }

    @Override
    public KiErgebnis sende(KiAnfrage anfrage, String modell, String schluessel) {
        aufrufe.add(new Aufruf(anfrage, modell, schluessel));
        return new KiErgebnis("antwort", false, typ, modell);
    }

    @Override
    public List<KiModellInfo> ladeModelle(String schluessel) {
        modellAbrufe++;
        if (fehlerBeimLaden != null) {
            throw fehlerBeimLaden;
        }
        return modelle;
    }
}
