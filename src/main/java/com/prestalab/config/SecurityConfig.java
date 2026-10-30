package com.prestalab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Seguridad stateless con JWT HS256 (OAuth2 Resource Server de Spring Security, implementación Nimbus).
 * No depende de ningún paquete de la aplicación (D7): solo configura Spring.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String EMISOR = "prestalab";
    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey claveJwt(@Value("${prestalab.jwt.secret:}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("PRESTALAB_JWT_SECRET debe tener al menos " + LONGITUD_MINIMA_SECRETO
                    + " bytes (genera uno con: openssl rand -base64 48)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    /** Valida firma HS256, expiración y emisor. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey claveJwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(EMISOR)));
        return decoder;
    }

    /** El claim "rol" (ESTUDIANTE o ADMIN) se convierte en la autoridad ROLE_ESTUDIANTE o ROLE_ADMIN. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(autoridades);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipos/**").hasAnyRole("ESTUDIANTE", "ADMIN")
                        .requestMatchers("/api/v1/equipos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/prestamos").hasRole("ESTUDIANTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/prestamos/mios").hasRole("ESTUDIANTE")
                        .requestMatchers("/api/v1/prestamos/**", "/api/v1/dashboard/**", "/api/v1/usuarios/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Sin cookies ni sesión: el token viaja en la cabecera Authorization, así que no hay CSRF posible.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(recurso -> recurso
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, ex) -> escribirProblema(objectMapper, request,
                                response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                                "Se requiere un token JWT válido en la cabecera Authorization")))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, ex) -> escribirProblema(objectMapper, request,
                                response, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                                "Se requiere un token JWT válido en la cabecera Authorization"))
                        .accessDeniedHandler((request, response, ex) -> escribirProblema(objectMapper, request,
                                response, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                                "Tu rol no tiene permiso para esta operación")));
        return http.build();
    }

    /** Mismo formato ProblemDetail que GlobalExceptionHandler (RFC 9457). */
    private static void escribirProblema(ObjectMapper objectMapper, HttpServletRequest request,
                                         HttpServletResponse response, HttpStatus estado, String codigo,
                                         String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("https://prestalab.local/errores/" + codigo.toLowerCase(Locale.ROOT)));
        problema.setTitle(estado.getReasonPhrase());
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", codigo);
        problema.setProperty("timestamp", OffsetDateTime.now(ZONA).toString());
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
