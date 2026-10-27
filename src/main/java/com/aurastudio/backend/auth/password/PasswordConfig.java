package com.aurastudio.backend.auth.password;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * New hashes: {@code {bcrypt}...}. Hashes without a {@code {id}} prefix are the
 * Next.js scrypt format and are verified by {@link NodeScryptPasswordEncoder};
 * {@code upgradeEncoding} then tells the login flow to re-hash them.
 */
@Configuration
public class PasswordConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		var encoders = Map.<String, PasswordEncoder>of("bcrypt", new BCryptPasswordEncoder());
		var delegating = new DelegatingPasswordEncoder("bcrypt", encoders);
		delegating.setDefaultPasswordEncoderForMatches(new NodeScryptPasswordEncoder());
		return delegating;
	}
}
