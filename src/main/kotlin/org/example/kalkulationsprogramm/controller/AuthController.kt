package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.domain.FrontendUserRole
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository
import org.example.kalkulationsprogramm.service.FrontendUserProfileService
import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val profileService: FrontendUserProfileService,
    private val profileRepository: FrontendUserProfileRepository,
    private val settingsService: SystemSettingsService,
) {
    @GetMapping("/bootstrap-status")
    fun bootstrapStatus(): ResponseEntity<BootstrapStatusResponse> {
        val hasLoginUsers = profileRepository.countByUsernameIsNotNull() > 0
        return ResponseEntity.ok(
            BootstrapStatusResponse(
                hasLoginUsers,
                settingsService.isInitialConfigurationRequired,
            )
        )
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<*> {
        if (profileRepository.countByUsernameIsNotNull() > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("message" to "Registrierung ist nur waehrend der Einrichtungsphase moeglich."))
        }
        return try {
            val created = profileService.register(request.displayName, request.username, request.password)
            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "id" to created.id,
                    "username" to created.username,
                    "displayName" to created.displayName,
                )
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    @GetMapping("/me")
    fun me(authentication: Authentication?): ResponseEntity<*> {
        val principal = extractPrincipal(authentication)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        val profile = profileService.findById(principal.id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        val isAdmin = profile.hasRole(FrontendUserRole.ADMIN)
        val requiresInitialSetup = isAdmin && settingsService.isInitialConfigurationRequired
        val mitarbeiter = profile.mitarbeiter?.let {
            MitarbeiterInfo(
                readLongProperty(it, "id"),
                readStringProperty(it, "loginToken"),
            )
        }

        return ResponseEntity.ok(
            MeResponse(
                profile.id,
                profile.displayName,
                profile.username,
                profile.active,
                profile.roles,
                isAdmin,
                requiresInitialSetup,
                mitarbeiter,
            )
        )
    }

    @PutMapping("/me/credentials")
    fun updateOwnCredentials(
        @RequestBody request: CredentialsUpdateRequest,
        authentication: Authentication?,
    ): ResponseEntity<*> {
        val principal = extractPrincipal(authentication)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        if (request.username.isNullOrBlank() && request.password.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte Benutzername oder Passwort angeben."))
        }

        return try {
            val updated = profileService.updateCredentials(principal.id!!, request.username, request.password)
            ResponseEntity.ok(
                mapOf(
                    "id" to updated.id,
                    "username" to updated.username,
                    "displayName" to updated.displayName,
                )
            )
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
    }

    private fun extractPrincipal(authentication: Authentication?): FrontendUserPrincipal? {
        val principal = authentication?.principal ?: return null
        return principal as? FrontendUserPrincipal
    }

    data class RegisterRequest(val displayName: String?, val username: String?, val password: String?)
    data class CredentialsUpdateRequest(val username: String?, val password: String?)
    data class BootstrapStatusResponse(val hasLoginUsers: Boolean, val setupRequired: Boolean)
    data class MitarbeiterInfo(val id: Long?, val loginToken: String?)
    data class MeResponse(
        val id: Long?,
        val displayName: String?,
        val username: String?,
        val active: Boolean,
        val roles: List<String>,
        val admin: Boolean,
        val requiresInitialSetup: Boolean,
        val mitarbeiter: MitarbeiterInfo?,
    )

    companion object {
        private fun readLongProperty(target: Any, property: String): Long? {
            val value = readProperty(target, property)
            return value as? Long
        }

        private fun readStringProperty(target: Any, property: String): String? {
            val value = readProperty(target, property)
            return value as? String
        }

        private fun readProperty(target: Any, property: String): Any? {
            val getter = "get" + property.replaceFirstChar { it.uppercaseChar() }
            return target.javaClass.methods
                .firstOrNull { it.name == getter && it.parameterCount == 0 }
                ?.invoke(target)
        }
    }
}
