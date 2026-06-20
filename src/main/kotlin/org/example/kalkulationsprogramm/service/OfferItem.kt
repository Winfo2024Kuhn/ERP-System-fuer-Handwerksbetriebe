package org.example.kalkulationsprogramm.service
import java.math.BigDecimal
/**
 * Simple representation of an offer position with external code, unit and price.
 */
data class OfferItem(
    val code: String,
    val unit: String?,
    val price: BigDecimal,
    val norm: String?,
    val name: String
) {
    fun code(): String = code
    fun unit(): String? = unit
    fun price(): BigDecimal = price
    fun norm(): String? = norm
    fun name(): String = name
}
