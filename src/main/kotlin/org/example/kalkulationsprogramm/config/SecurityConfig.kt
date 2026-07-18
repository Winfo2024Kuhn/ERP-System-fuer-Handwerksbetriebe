package org.example.kalkulationsprogramm.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val objectMapper: ObjectMapper,
    private val frontendUserDetailsService: FrontendUserDetailsService,
    private val cloudflareAccessJwtFilter: CloudflareAccessJwtFilter,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    @Order(0)
    @Throws(Exception::class)
    fun funnelFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/internal/**")
            .csrf { it.disable() }
            .addFilterBefore(cloudflareAccessJwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .headers { headers -> headers.frameOptions { frame -> frame.sameOrigin() } }
        return http.build()
    }

    @Bean
    @Order(1)
    @Throws(Exception::class)
    fun zeiterfassungFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/zeiterfassung",
                "/zeiterfassung/**",
                "/api/zeiterfassung/**",
                "/api/mitarbeiter/by-token/**",
                "/api/urlaub/**",
                "/api/kalender/mobile/**",
                "/api/push/**",
                "/api/dokumente/**",
                "/api/images/**",
                "/api/projekte/**",
                "/api/anfragen/**",
                "/api/kunden/**",
                "/api/lieferanten/**",
                "/api/produktkategorien/**",
                "/api/arbeitsgaenge/**",
                "/api/abwesenheit/**",
                "/api/buchhaltung/mobile/**",
            )
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(2)
    @Throws(Exception::class)
    fun staticResourcesFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/",
                "/index.html",
                "/favicon.ico",
                "/app-icon.png",
                "/assets/**",
                "/static/**",
                "/manifest.json",
                "/sw.js",
                "/dokument-editor",
                "/dokument-editor/**",
                "/login",
                "/login/**",
                "/onboarding",
                "/onboarding/**",
                "/error",
                "/error/**",
            )
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(3)
    @Throws(Exception::class)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .csrf { csrf ->
                val requestHandler = CsrfTokenRequestAttributeHandler()
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
                    .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout")
            }
            .addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
            .userDetailsService(frontendUserDetailsService)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/register", "/api/auth/bootstrap-status").permitAll()
                    .requestMatchers("/api/auth/me", "/api/auth/me/credentials").authenticated()
                    .requestMatchers("/api/firma/**", "/api/settings/**", "/api/frontend-users/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/email/signatures/*/system-default").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/verrechnungslohn/uebernehmen").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/mahnwesen/lauf").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/emails/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .formLogin { form ->
                form
                    .loginProcessingUrl("/api/auth/login")
                    .successHandler { _, response, _ ->
                        writeJson(response, HttpServletResponse.SC_OK, mapOf("success" to true, "message" to "Login erfolgreich."))
                    }
                    .failureHandler { _, response, _ ->
                        writeJson(
                            response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            mapOf("success" to false, "message" to "Benutzername oder Passwort ist falsch."),
                        )
                    }
                    .permitAll()
            }
            .logout { logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler { _, response, _ ->
                        writeJson(response, HttpServletResponse.SC_OK, mapOf("success" to true, "message" to "Erfolgreich abgemeldet."))
                    }
            }
            .exceptionHandling { ex ->
                ex
                    .authenticationEntryPoint { _, response, _ ->
                        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, mapOf("success" to false, "message" to "Nicht authentifiziert."))
                    }
                    .accessDeniedHandler { _, response, _ ->
                        writeJson(response, HttpServletResponse.SC_FORBIDDEN, mapOf("success" to false, "message" to "Zugriff verweigert."))
                    }
            }
            .httpBasic { it.disable() }
            .headers { headers -> headers.frameOptions { frame -> frame.sameOrigin() } }

        return http.build()
    }

    @Bean
    @Order(4)
    @Throws(Exception::class)
    fun catchAllFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf ->
                val requestHandler = CsrfTokenRequestAttributeHandler()
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
            }
            .userDetailsService(frontendUserDetailsService)
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .anyRequest().authenticated()
            }
            .headers { headers -> headers.frameOptions { frame -> frame.sameOrigin() } }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, mapOf("success" to false, "message" to "Nicht authentifiziert."))
                }
            }
        return http.build()
    }

    private fun writeJson(response: HttpServletResponse, status: Int, body: Map<String, Any>) {
        try {
            response.status = status
            response.contentType = "application/json;charset=UTF-8"
            objectMapper.writeValue(response.writer, body)
        } catch (_: IOException) {
            response.status = status
        }
    }

    private class CsrfCookieFilter : OncePerRequestFilter() {
        @Throws(ServletException::class, IOException::class)
        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain,
        ) {
            val csrfToken = request.getAttribute(CsrfToken::class.java.name) as CsrfToken?
            csrfToken?.token
            filterChain.doFilter(request, response)
        }
    }
}
