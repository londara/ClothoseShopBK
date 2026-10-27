package com.aurastudio.backend.auth.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.aurastudio.backend.config.AuraProperties;
import com.aurastudio.backend.user.UserRow;

/**
 * Issues access tokens. Claims (docs/API_AND_DATABASE.md §5.1):
 * {@code sub} user id, {@code shop} shop id, {@code role}, {@code pa} platform admin,
 * plus {@code iss}, {@code aud}, {@code iat}, {@code exp}, {@code jti}.
 */
@Service
public class JwtService {

	public static final String CLAIM_SHOP = "shop";
	public static final String CLAIM_ROLE = "role";
	public static final String CLAIM_PLATFORM_ADMIN = "pa";

	private final JwtEncoder encoder;
	private final AuraProperties.Jwt props;

	public JwtService(JwtEncoder encoder, AuraProperties properties) {
		this.encoder = encoder;
		this.props = properties.jwt();
	}

	public String issueAccessToken(UserRow user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(props.issuer())
				.audience(List.of(props.audience()))
				.subject(user.id().toString())
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(props.accessTokenTtl()))
				.claim(CLAIM_SHOP, user.shopId().toString())
				.claim(CLAIM_ROLE, user.role().db())
				.claim(CLAIM_PLATFORM_ADMIN, user.platformAdmin())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTokenSeconds() {
		return props.accessTokenTtl().toSeconds();
	}

	public long refreshTokenSeconds() {
		return props.refreshTokenTtl().toSeconds();
	}
}
