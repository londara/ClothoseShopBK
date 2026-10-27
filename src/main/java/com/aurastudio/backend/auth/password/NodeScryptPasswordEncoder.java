package com.aurastudio.backend.auth.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies password hashes created by the Next.js app ({@code src/lib/password.ts}):
 * {@code "<saltHex>:<hashHex>"} = Node {@code crypto.scrypt(password, saltHexString, 64)}
 * with Node's defaults N=16384, r=8, p=1. The salt is used as the hex STRING (UTF-8 bytes).
 * <p>
 * Only used to check migrated hashes; {@link PasswordConfig} re-hashes them with BCrypt
 * on the next successful login.
 */
public class NodeScryptPasswordEncoder implements PasswordEncoder {

	private static final int N = 16384;
	private static final int R = 8;
	private static final int P = 1;
	private static final int KEY_LENGTH = 64;

	@Override
	public String encode(CharSequence rawPassword) {
		throw new UnsupportedOperationException("Legacy scrypt hashes are verify-only; new hashes use BCrypt");
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		if (rawPassword == null || encodedPassword == null) {
			return false;
		}
		int colon = encodedPassword.indexOf(':');
		if (colon <= 0 || colon == encodedPassword.length() - 1) {
			return false;
		}
		String salt = encodedPassword.substring(0, colon);
		byte[] expected;
		try {
			expected = HexFormat.of().parseHex(encodedPassword.substring(colon + 1));
		}
		catch (IllegalArgumentException e) {
			return false;
		}
		byte[] actual = SCrypt.generate(rawPassword.toString().getBytes(StandardCharsets.UTF_8),
				salt.getBytes(StandardCharsets.UTF_8), N, R, P, KEY_LENGTH);
		return MessageDigest.isEqual(expected, actual);
	}

	@Override
	public boolean upgradeEncoding(String encodedPassword) {
		return true;
	}
}
