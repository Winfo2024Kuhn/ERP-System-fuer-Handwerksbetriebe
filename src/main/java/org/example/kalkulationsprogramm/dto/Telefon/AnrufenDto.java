package org.example.kalkulationsprogramm.dto.Telefon;

/** Anrufen aus dem ERP: {@code telefon} klingelt zuerst, danach wird {@code nummer} gewählt. */
public record AnrufenDto(String telefon, String nummer) {
}
