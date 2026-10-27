package com.aurastudio.backend.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.aurastudio.backend.config.AuraProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** HS256 JWT encoder/decoder (Nimbus, via Spring Security's resource server). */
@Configuration
public class JwtConfig {

	private final AuraProperties.Jwt props;

	public JwtConfig(AuraProperties properties) {
		this.props = properties.jwt();
		if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("aura.jwt.secret (JWT_SECRET) must be at least 32 bytes");
		}
	}

	private SecretKey key() {
		return new SecretKeySpec(props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key()));
	}

	/** Checks signature, exp/nbf (with clock skew), issuer and audience. */
	@Bean
	JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
		var audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.contains(props.audience()));
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuer()), audience));
		return decoder;
	}
}
