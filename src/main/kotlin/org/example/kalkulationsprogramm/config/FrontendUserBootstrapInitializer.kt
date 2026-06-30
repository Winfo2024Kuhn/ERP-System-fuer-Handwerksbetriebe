package org.example.kalkulationsprogramm.config

import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.domain.FrontendUserRole
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashSet
import java.util.Locale

@Component
class FrontendUserBootstrapInitializer(
    private val repository: FrontendUserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.admin.username:}")
    private val adminUsername: String?,
    @Value("\${app.admin.password:}")
    private val adminPassword: String?,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (repository.countByUsernameIsNotNull() > 0) {
            return
        }

        val normalizedUsername =
            if (adminUsername.isNullOrBlank()) "admin" else adminUsername.trim().lowercase(Locale.ROOT)

        val passwordGenerated = adminPassword.isNullOrBlank()
        val bootstrapPassword = if (passwordGenerated) generateSecurePassword() else adminPassword

        val admin = FrontendUserProfile()
        admin.displayName = "Administrator"
        admin.shortCode = "ADM"
        admin.username = normalizedUsername
        admin.passwordHash = passwordEncoder.encode(bootstrapPassword)
        admin.active = true
        admin.roleSet = LinkedHashSet(setOf(FrontendUserRole.ADMIN, FrontendUserRole.USER))

        repository.save(admin)

        if (passwordGenerated) {
            log.warn("==========================================================")
            log.warn("Bootstrap-Admin angelegt: username={}", normalizedUsername)
            log.warn("Einmaliges Passwort (bitte sofort aendern): {}", bootstrapPassword)
            log.warn("Setzen Sie APP_ADMIN_PASS, um ein eigenes Passwort zu verwenden.")
            log.warn("==========================================================")
        } else {
            log.info("Bootstrap-Admin fuer Frontend-Login wurde angelegt: username={}", normalizedUsername)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FrontendUserBootstrapInitializer::class.java)

        private fun generateSecurePassword(): String {
            val bytes = ByteArray(18)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
