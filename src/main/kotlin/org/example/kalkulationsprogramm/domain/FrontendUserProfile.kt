package org.example.kalkulationsprogramm.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "frontend_user_profile",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_frontend_user_profile_username", columnNames = ["username"]),
    ],
)
open class FrontendUserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "display_name", nullable = false, length = 200)
    open var displayName: String? = null

    @Column(name = "short_code", length = 50)
    open var shortCode: String? = null

    @Column(name = "username", length = 120)
    open var username: String? = null

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    open var passwordHash: String? = null

    @Column(name = "active", nullable = false)
    open var active: Boolean = true

    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "frontend_user_profile_role", joinColumns = [JoinColumn(name = "frontend_user_profile_id")])
    @Column(name = "role_name", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    open var roleSet: MutableSet<FrontendUserRole> = linkedSetOf()
        set(value) {
            field = LinkedHashSet(value)
        }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_signature_id")
    open var defaultSignature: EmailSignature? = null

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "email_absender_id")
    open var emailAbsender: EmailAbsender? = null

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mitarbeiter_id")
    @JsonIgnoreProperties(
        value = [
            "abteilungen",
            "zeitkonten",
            "buchungen",
            "antraege",
            "dokumente",
            "hibernateLazyInitializer",
            "handler",
        ],
    )
    open var mitarbeiter: Mitarbeiter? = null

    @get:Transient
    @get:JsonProperty("roles")
    open val roles: List<String>
        get() {
            val roles = linkedSetOf<String>()
            roleSet.mapTo(roles) { it.name }
            val mitarbeiterAbteilungen = readMitarbeiterAbteilungen(mitarbeiter)
            for (abteilung in mitarbeiterAbteilungen) {
                val name = readName(abteilung)
                if (name != null) {
                    roles.add(name)
                }
            }
            return ArrayList(roles)
        }

    open fun isActive(): Boolean = active

    open fun hasRole(role: FrontendUserRole?): Boolean =
        role != null && roleSet.contains(role)

    private fun readMitarbeiterAbteilungen(mitarbeiter: Mitarbeiter?): Iterable<*> {
        if (mitarbeiter == null) {
            return emptyList<Any>()
        }
        val value = runCatching {
            mitarbeiter.javaClass.getMethod("getAbteilungen").invoke(mitarbeiter)
        }.getOrNull()
        return value as? Iterable<*> ?: emptyList<Any>()
    }

    private fun readName(value: Any?): String? =
        runCatching { value?.javaClass?.getMethod("getName")?.invoke(value) as? String }.getOrNull()
}
