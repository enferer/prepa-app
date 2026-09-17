package app.prepa.infra;

import app.prepa.auth.JwtAuthFilter;
import app.prepa.auth.ServiceKeyAuthFilter;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Securite HTTP : stateless, deux filtres d'authentification, erreurs au format de l'API. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    /**
     * Origines autorisees a appeler l'API depuis un navigateur.
     *
     * <p>La liste depend du deploiement et ne peut donc pas etre figee dans le code : une
     * installation servie en clair sur un nom de machine ne ressemble ni au poste de
     * developpement, ni a un site en HTTPS. Le navigateur envoie un en-tete {@code Origin}
     * meme quand la page et l'API partagent la meme origine ; une liste qui ne la couvre
     * pas fait echouer la connexion avec un 403, la ou {@code curl} — qui n'envoie pas
     * cet en-tete — reussit.
     */
    private final List<String> originesAutorisees;

    public SecurityConfig(
            ObjectMapper objectMapper,
            @Value("${prepa.web.origines-autorisees:http://localhost:*,https://*}")
                    List<String> originesAutorisees) {
        this.objectMapper = objectMapper;
        this.originesAutorisees = originesAutorisees;
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, ServiceKeyAuthFilter serviceKeyFilter, JwtAuthFilter jwtFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(serviceKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, ServiceKeyAuthFilter.class)
                .exceptionHandling(e ->
                        e.authenticationEntryPoint(entryPoint()).accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(originesAutorisees);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) ->
                ecrire(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> ecrire(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Accès refusé");
    }

    private void ecrire(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String code, String msg)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, msg, Map.of()));
    }
}
