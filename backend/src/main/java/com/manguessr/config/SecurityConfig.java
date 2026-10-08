package com.manguessr.config;

import com.manguessr.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * JWT sans etat, sur le modele de revision-guide.
 *
 * Regle produit : on peut jouer sans compte (consultation du daily, parties anonymes),
 * mais enregistrer un score, un streak ou apparaitre au leaderboard demande d'etre connecte.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Inscription / connexion
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // Contenu consultable sans compte
                .requestMatchers(HttpMethod.GET, "/api/games").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/daily").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/autocomplete").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/catalog/stats").permitAll()
                // Jouable sans compte : la partie est alors anonyme et hors classement.
                // GameSessionService verifie que le demandeur est bien le proprietaire.
                .requestMatchers("/api/games", "/api/sessions", "/api/sessions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/leaderboard").permitAll()
                // Proxy medias : les tokens sont signes, ils portent leur propre autorisation
                .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
