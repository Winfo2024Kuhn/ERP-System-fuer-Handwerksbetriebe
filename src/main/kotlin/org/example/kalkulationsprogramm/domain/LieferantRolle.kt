package org.example.kalkulationsprogramm.domain

enum class LieferantRolle(val anzeigename: String) {
    STAHLHANDEL("Stahlhandel"),
    SCHRAUBEN_NORMTEILE("Schrauben & Normteile"),
    BESCHICHTUNG_VERZINKEN("Beschichtung / Verzinkerei"),
    LACKIERER("Lackierer"),
    FERTIGTEILE_ZUKAUF("Fertigteile & Zukauf"),
    ALUMINIUM_NE("Aluminium / NE-Metalle"),
    EDELSTAHL("Edelstahl"),
    WERKZEUG_VERBRAUCH("Werkzeug & Verbrauch"),
    IT("IT"),
    SONSTIGER("Sonstiger / Dienstleister"),
}
