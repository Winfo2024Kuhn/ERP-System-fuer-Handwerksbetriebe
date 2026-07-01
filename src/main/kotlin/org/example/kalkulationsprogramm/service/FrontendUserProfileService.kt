package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.EmailAbsender
import org.example.kalkulationsprogramm.domain.EmailSignature
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.domain.FrontendUserRole
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository
import org.example.kalkulationsprogramm.repository.EmailSignatureRepository
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.Optional

@Service
class FrontendUserProfileService(
    private val repository: FrontendUserProfileRepository,
    private val emailSignatureRepository: EmailSignatureRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val emailAbsenderRepository: EmailAbsenderRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional(readOnly = true)
    fun list(): List<FrontendUserProfile> = repository.findAll()

    @Transactional(readOnly = true)
    fun findById(id: Long?): Optional<FrontendUserProfile> =
        if (id == null) Optional.empty() else repository.findById(id)

    @Transactional(readOnly = true)
    fun findByDisplayName(displayName: String?): Optional<FrontendUserProfile> {
        val normalized = displayName?.trim()
        if (normalized.isNullOrEmpty()) {
            return Optional.empty()
        }
        return repository.findByDisplayNameIgnoreCase(normalized)
    }

    @Transactional(readOnly = true)
    fun findByUsername(username: String?): Optional<FrontendUserProfile> {
        val normalized = normalizeUsername(username) ?: return Optional.empty()
        return repository.findByUsernameIgnoreCase(normalized)
    }

    @Transactional
    fun saveOrUpdate(
        profile: FrontendUserProfile,
        defaultSignatureId: Long?,
        mitarbeiterId: Long?,
    ): FrontendUserProfile =
        saveOrUpdate(profile, defaultSignatureId, mitarbeiterId, profile.username, null, profile.roleSet, null, null)

    @Transactional
    fun saveOrUpdate(
        profile: FrontendUserProfile,
        defaultSignatureId: Long?,
        mitarbeiterId: Long?,
        username: String?,
        rawPassword: String?,
        roles: Set<FrontendUserRole>?,
        active: Boolean?,
    ): FrontendUserProfile =
        saveOrUpdate(profile, defaultSignatureId, mitarbeiterId, username, rawPassword, roles, active, null)

    @Transactional
    fun saveOrUpdate(
        profile: FrontendUserProfile,
        defaultSignatureId: Long?,
        mitarbeiterId: Long?,
        username: String?,
        rawPassword: String?,
        roles: Set<FrontendUserRole>?,
        active: Boolean?,
        emailAbsenderId: Long?,
    ): FrontendUserProfile {
        val signature: EmailSignature? = defaultSignatureId?.let { emailSignatureRepository.findById(it).orElseThrow() }
        val mitarbeiter: Mitarbeiter? = mitarbeiterId?.let { mitarbeiterRepository.findById(it).orElse(null) }
        val emailAbsender: EmailAbsender? = emailAbsenderId?.let {
            emailAbsenderRepository.findById(it)
                .orElseThrow { IllegalArgumentException("E-Mail-Absender nicht gefunden: $it") }
        }

        val usernameProvided = !username.isNullOrBlank()
        val normalizedUsername = if (usernameProvided) normalizeUsername(username) else null

        val creating = profile.id == null
        val target = if (profile.id != null) {
            repository.findById(profile.id!!).orElseThrow().also {
                it.displayName = profile.displayName
                it.shortCode = profile.shortCode
            }
        } else {
            profile
        }

        if (creating || usernameProvided) {
            ensureUsernameAvailable(normalizedUsername, target.id)
        }

        val normalizedRoles = normalizeRoleSet(roles, if (creating) null else target.roleSet)
        val targetActive = active ?: (creating || target.isActive())

        ensureAdminSafetyOnUpdate(target, normalizedRoles, targetActive)

        if (creating || usernameProvided) {
            target.username = normalizedUsername
        }
        if (!rawPassword.isNullOrBlank()) {
            validatePassword(rawPassword)
            target.passwordHash = passwordEncoder.encode(rawPassword)
        }
        target.roleSet = normalizedRoles.toMutableSet()
        target.active = targetActive

        if (creating && target.passwordHash.isNullOrBlank() && normalizedUsername != null) {
            throw IllegalArgumentException("Für neue Benutzer mit Login ist ein Passwort erforderlich.")
        }

        target.defaultSignature = signature
        target.mitarbeiter = mitarbeiter
        target.emailAbsender = emailAbsender

        if (target.shortCode.isNullOrBlank()) {
            target.shortCode = generateShortCode(target.displayName)
        }

        return repository.save(target)
    }

    @Transactional
    fun register(displayName: String?, username: String?, rawPassword: String?): FrontendUserProfile {
        if (displayName.isNullOrBlank()) {
            throw IllegalArgumentException("Anzeigename ist erforderlich.")
        }

        val normalizedUsername = normalizeUsername(username)
            ?: throw IllegalArgumentException("Benutzername ist erforderlich.")

        validatePassword(rawPassword)
        ensureUsernameAvailable(normalizedUsername, null)

        val profile = FrontendUserProfile()
        profile.displayName = displayName.trim()
        profile.shortCode = generateShortCode(displayName)
        profile.username = normalizedUsername
        profile.passwordHash = passwordEncoder.encode(rawPassword)
        profile.active = true
        profile.roleSet = linkedSetOf(FrontendUserRole.USER)
        return repository.save(profile)
    }

    @Transactional
    fun updateCredentials(profileId: Long, username: String?, rawPassword: String?): FrontendUserProfile {
        val profile = repository.findById(profileId).orElseThrow()

        val normalizedUsername = normalizeUsername(username)
        if (normalizedUsername != null && !normalizedUsername.equals(profile.username, ignoreCase = true)) {
            ensureUsernameAvailable(normalizedUsername, profileId)
            profile.username = normalizedUsername
        }

        if (!rawPassword.isNullOrBlank()) {
            validatePassword(rawPassword)
            profile.passwordHash = passwordEncoder.encode(rawPassword)
        }

        return repository.save(profile)
    }

    @Transactional
    fun setDefaultSignature(profileId: Long, signatureId: Long?): FrontendUserProfile {
        val profile = repository.findById(profileId).orElseThrow()
        val signature = signatureId?.let { emailSignatureRepository.findById(it).orElseThrow() }
        profile.defaultSignature = signature
        return repository.save(profile)
    }

    @Transactional
    fun delete(id: Long) {
        val profile = repository.findById(id).orElseThrow()
        if (profile.isActive() && profile.hasRole(FrontendUserRole.ADMIN) && countOtherActiveAdmins(id) == 0L) {
            throw IllegalStateException("Mindestens ein aktiver Admin muss bestehen bleiben.")
        }
        repository.deleteById(id)
    }

    private fun ensureAdminSafetyOnUpdate(
        existingProfile: FrontendUserProfile,
        newRoles: Set<FrontendUserRole>,
        newActiveState: Boolean,
    ) {
        val id = existingProfile.id ?: return

        val currentlyActiveAdmin = existingProfile.isActive() && existingProfile.hasRole(FrontendUserRole.ADMIN)
        val remainsActiveAdmin = newActiveState && newRoles.contains(FrontendUserRole.ADMIN)

        if (currentlyActiveAdmin && !remainsActiveAdmin && countOtherActiveAdmins(id) == 0L) {
            throw IllegalStateException("Mindestens ein aktiver Admin muss bestehen bleiben.")
        }
    }

    private fun countOtherActiveAdmins(excludedProfileId: Long): Long =
        repository.countActiveByRoleExcludingId(FrontendUserRole.ADMIN, excludedProfileId)

    private fun normalizeRoleSet(
        requestedRoles: Set<FrontendUserRole>?,
        fallback: Set<FrontendUserRole>?,
    ): Set<FrontendUserRole> {
        val normalized = LinkedHashSet<FrontendUserRole>()
        if (requestedRoles != null) {
            normalized.addAll(requestedRoles)
        }
        if (normalized.isEmpty() && fallback != null) {
            normalized.addAll(fallback)
        }
        if (normalized.isEmpty()) {
            normalized.add(FrontendUserRole.USER)
        }
        return normalized
    }

    private fun ensureUsernameAvailable(normalizedUsername: String?, currentProfileId: Long?) {
        if (normalizedUsername == null) {
            return
        }

        val existing = repository.findByUsernameIgnoreCase(normalizedUsername)
        if (existing.isPresent && (currentProfileId == null || existing.get().id != currentProfileId)) {
            throw IllegalArgumentException("Benutzername ist bereits vergeben.")
        }
    }

    private fun normalizeUsername(username: String?): String? {
        val normalized = username?.trim()?.lowercase(Locale.ROOT)
        return if (normalized.isNullOrBlank()) null else normalized
    }

    private fun validatePassword(rawPassword: String?) {
        if (rawPassword.isNullOrBlank()) {
            throw IllegalArgumentException("Passwort ist erforderlich.")
        }
        if (rawPassword.length < 8) {
            throw IllegalArgumentException("Passwort muss mindestens 8 Zeichen lang sein.")
        }
    }

    private fun generateShortCode(displayName: String?): String? {
        if (displayName.isNullOrBlank()) {
            return null
        }
        val builder = StringBuilder()
        for (part in displayName.trim().split(Regex("\\s+"))) {
            if (part.isNotBlank()) {
                builder.append(part[0].uppercaseChar())
            }
            if (builder.length >= 4) {
                break
            }
        }
        return builder.takeIf { it.isNotEmpty() }?.toString()
    }
}
