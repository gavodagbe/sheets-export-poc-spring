package com.example.sheetsexport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Sécurité web.
 *
 * - la page {@code /} et {@code /api/session} sont publiques ;
 * - {@code /api/exports/**} exige une session authentifiée Google ;
 * - un appel non authentifié sur {@code /api/**} renvoie {@code 401} (et non une redirection),
 *   ce qui permet au front de déclencher lui-même {@code /oauth2/authorization/google} ;
 * - tout le reste (page de login Google, statiques, favicon) est libre.
 *
 * Le token d'accès Google est géré par Spring Security dans la session HTTP (en mémoire) :
 * aucune persistance, mais le front n'a jamais à le manipuler.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/exports/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // POC : appels fetch JSON depuis la même origine, auth par cookie de session.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")));
        return http.build();
    }
}
