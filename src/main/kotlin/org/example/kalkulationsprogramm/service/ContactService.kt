package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.dto.ContactDto
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Service
class ContactService(
    private val kundeRepository: KundeRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val projektRepository: ProjektRepository,
    private val anfrageRepository: AnfrageRepository,
) {
    @Transactional(readOnly = true)
    fun searchContacts(query: String?): List<ContactDto> {
        if (query == null || query.length < 2) {
            return Collections.emptyList()
        }

        val results = ArrayList<ContactDto>()

        kundeRepository.searchByNameOrAnsprechpartnerOrEmail(query).forEach { k ->
            k.value<Collection<String>>("getKundenEmails")?.forEach { email ->
                val context = k.stringValue("getAnsprechspartner")?.let {
                    "$it (${k.stringValue("getKundennummer")})"
                } ?: k.stringValue("getKundennummer")
                results.add(
                    ContactDto.builder()
                        .id("KUNDE_${k.longValue("getId")}")
                        .name(k.stringValue("getName"))
                        .email(email)
                        .type("KUNDE")
                        .context(context)
                        .build(),
                )
            }
        }

        lieferantenRepository.searchByNameOrEmail(query).forEach { l ->
            l.value<Collection<String>>("getKundenEmails")?.forEach { email ->
                results.add(
                    ContactDto.builder()
                        .id("LIEFERANT_${l.longValue("getId")}")
                        .name(l.stringValue("getLieferantenname"))
                        .email(email)
                        .type("LIEFERANT")
                        .context(l.stringValue("getLieferantenTyp"))
                        .build(),
                )
            }
        }

        projektRepository.searchByBauvorhabenOrKundeOrEmail(query).forEach { p ->
            p.value<Collection<String>>("getKundenEmails")?.forEach { email ->
                results.add(
                    ContactDto.builder()
                        .id("PROJEKT_${p.longValue("getId")}")
                        .name(p.stringValue("getKunde") ?: "Unbekannt")
                        .email(email)
                        .type("PROJEKT")
                        .context(p.stringValue("getBauvorhaben"))
                        .build(),
                )
            }
        }

        anfrageRepository.searchByBauvorhabenOrKundeOrEmail(query).forEach { a ->
            val kunde = a.value<Any>("getKunde")
            val anfrageName = kunde?.stringValue("getName") ?: "Unbekannt"
            if (kunde != null) {
                kunde.value<Collection<String>>("getKundenEmails")?.forEach { email ->
                    results.add(
                        ContactDto.builder()
                            .id("ANFRAGE_${a.longValue("getId")}")
                            .name(anfrageName)
                            .email(email)
                            .type("ANFRAGE")
                            .context(a.stringValue("getBauvorhaben"))
                            .build(),
                    )
                }
            }
            a.value<Collection<String>>("getKundenEmails")?.forEach { email ->
                results.add(
                    ContactDto.builder()
                        .id("ANFRAGE_${a.longValue("getId")}")
                        .name(anfrageName)
                        .email(email)
                        .type("ANFRAGE")
                        .context(a.stringValue("getBauvorhaben"))
                        .build(),
                )
            }
        }

        val seen = ConcurrentHashMap.newKeySet<String>()
        return results
            .asSequence()
            .filter { seen.add(it.email) }
            .take(30)
            .toList()
    }

    private inline fun <reified T> Any.value(getter: String): T? =
        javaClass.getMethod(getter).invoke(this) as? T

    private fun Any.longValue(getter: String): Long? =
        javaClass.getMethod(getter).invoke(this) as? Long

    private fun Any.stringValue(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as? String
}
