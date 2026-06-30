package org.example.kalkulationsprogramm.service

import jakarta.annotation.PostConstruct
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

@Component
class LieferantEmailResolver(
    private val lieferantenRepository: LieferantenRepository,
) {
    private val cache = AtomicReference(Cache.empty())

    private data class Cache(
        val addresses: Map<String, Long>,
        val domains: Map<String, Long>,
        val searchTerms: List<String>,
    ) {
        companion object {
            fun empty(): Cache = Cache(emptyMap(), emptyMap(), emptyList())
        }
    }

    @PostConstruct
    fun init() {
        refresh()
    }

    @Transactional(readOnly = true)
    fun refresh() {
        val lieferanten = lieferantenRepository.findAllWithEmails()
        val addressAssignments = HashMap<String, Long>()
        val domainAssignments = HashMap<String, Long>()
        val ambiguousAddresses = HashSet<String>()
        val ambiguousDomains = HashSet<String>()
        val addressTokens = LinkedHashSet<String>()

        for (lieferant in lieferanten) {
            val kundenEmails = lieferant.value<Collection<String>>("getKundenEmails") ?: continue
            val lieferantId = safeId(lieferant) ?: continue
            for (raw in kundenEmails) {
                val normalized = normalize(raw) ?: continue
                if (!ambiguousAddresses.contains(normalized)) {
                    val existing = addressAssignments.putIfAbsent(normalized, lieferantId)
                    if (existing != null && existing != lieferantId) {
                        ambiguousAddresses.add(normalized)
                        addressAssignments.remove(normalized)
                    }
                }
                addressTokens.add(normalized)
                val domain = domain(normalized)
                if (domain != null && !ambiguousDomains.contains(domain)) {
                    val existingDomain = domainAssignments.putIfAbsent(domain, lieferantId)
                    if (existingDomain != null && existingDomain != lieferantId) {
                        ambiguousDomains.add(domain)
                        domainAssignments.remove(domain)
                    }
                }
            }
        }

        cache.set(
            Cache(
                Collections.unmodifiableMap(addressAssignments),
                Collections.unmodifiableMap(domainAssignments),
                Collections.unmodifiableList(ArrayList(addressTokens)),
            ),
        )
    }

    fun getSearchTerms(): List<String> =
        cache.get().searchTerms

    fun resolve(addresses: Collection<String>?): Optional<Long> {
        if (addresses.isNullOrEmpty()) {
            return Optional.empty()
        }
        val data = cache.get()
        for (raw in addresses) {
            val normalized = normalize(raw) ?: continue
            var supplierId = data.addresses[normalized]
            if (supplierId != null) {
                return Optional.of(supplierId)
            }
            val domain = domain(normalized)
            if (domain != null) {
                supplierId = data.domains[domain]
                if (supplierId != null) {
                    return Optional.of(supplierId)
                }
            }
        }
        return Optional.empty()
    }

    private fun safeId(lieferant: Lieferanten): Long? =
        lieferant.value("getId")

    private fun normalize(email: String?): String? {
        if (email == null) {
            return null
        }
        val trimmed = email.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            return null
        }
        return trimmed
    }

    private fun domain(email: String?): String? {
        if (email == null) {
            return null
        }
        val at = email.indexOf('@')
        if (at < 0 || at == email.length - 1) {
            return null
        }
        return email.substring(at + 1)
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T
}
