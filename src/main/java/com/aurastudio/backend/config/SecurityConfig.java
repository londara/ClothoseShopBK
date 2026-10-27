package com.aurastudio.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.aurastudio.backend.common.ApiError;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless JWT security. Public: login/refresh/logout, shop sign-up, the
 * storefront GETs, uploaded images, Swagger and health. Everything else needs
 * {@code Authorization: Bearer <accessToken>}; role and shop checks happen in
 * {@link com.aurastudio.backend.tenant.ShopContextResolver} and the services.
 */
@Configuration
public class SecurityConfig {

	private final JsonMapper json;
	private final AuraProperties properties;

	public SecurityConfig(JsonMapper json, AuraProperties properties) {
		this.json = json;
		this.properties = properties;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
								"/api/v1/auth/logout", "/api/v1/shops")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/shop-types", "/api/v1/shops", "/api/v1/shops/**",
								"/api/v1/featured", "/uploads/**")
						.permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health",
								"/error")
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler()))
				.exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler()));
		return http.build();
	}

	/** 401 with {@code TOKEN_EXPIRED} (call /auth/refresh) or {@code TOKEN_INVALID} (log in again). */
	@Bean
	AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, ex) -> {
			boolean expired = isExpired(ex);
			write(response, expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.TOKEN_INVALID,
					expired ? Messages.TOKEN_EXPIRED : Messages.TOKEN_INVALID);
		};
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, ex) -> write(response, ErrorCode.FORBIDDEN, Messages.FORBIDDEN);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setExposedHeaders(List.of("Retry-After"));
		config.setAllowCredentials(true);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private static boolean isExpired(AuthenticationException ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().toLowerCase().contains("expired")) {
				return true;
			}
		}
		return false;
	}

	private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json.writeValueAsString(ApiError.of(code, message, null)));
	}
}
