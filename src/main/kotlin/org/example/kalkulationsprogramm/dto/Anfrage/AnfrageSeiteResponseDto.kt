package org.example.kalkulationsprogramm.dto.Anfrage

/**
 * Antwort für den paginierten Anfragen-Listenendpunkt
 * (analog zu {@code ProjektSearchResponseDto}). Wird ausgeliefert, sobald der
 * Aufrufer einen {@code page}-Query-Parameter setzt.
 */
data class AnfrageSeiteResponseDto(
    val anfragen: List<AnfrageResponseDto>,
    val gesamt: Long,
    val seite: Int,
    val seitenGroesse: Int
) {
    fun anfragen(): List<AnfrageResponseDto> = anfragen
    fun gesamt(): Long = gesamt
    fun seite(): Int = seite
    fun seitenGroesse(): Int = seitenGroesse
}
