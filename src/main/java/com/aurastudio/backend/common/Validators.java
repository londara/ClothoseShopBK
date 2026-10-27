package com.aurastudio.backend.common;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared normalisation + validation rules (docs/API_AND_DATABASE.md §12). */
public final class Validators {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern PHONE = Pattern.compile("^\\d{8,15}$");
	private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
	private static final Pattern UPLOAD_PATH = Pattern.compile("^/uploads/([a-z0-9-]+\\.(jpg|png|webp|gif|avif))$");
	/** https only; no whitespace, quotes, angle brackets or backslashes (nothing that can break out of HTML). */
	private static final Pattern REMOTE_URL = Pattern.compile("^https://[^\\s\"'<>\\\\`]+$", Pattern.CASE_INSENSITIVE);

	public static final int MAX_URL = 2048;
	public static final int MAX_EMAIL = 254;
	/** bcrypt only uses the first 72 bytes of a password. */
	public static final int MAX_PASSWORD_BYTES = 72;

	private Validators() {
	}

	/** Trimmed text, or "" for null. */
	public static String text(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * A single-line text field: trimmed, no control characters, at most {@code max}
	 * characters (the database column size), else a 400 on {@code field}.
	 */
	public static String text(String value, String field, int max) {
		return checked(text(value), field, max, false);
	}

	/** Like {@link #text(String, String, int)} but newlines and tabs are allowed (descriptions). */
	public static String multiline(String value, String field, int max) {
		return checked(text(value), field, max, true);
	}

	private static String checked(String value, String field, int max, boolean multiline) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			boolean allowed = multiline && (c == '\n' || c == '\r' || c == '\t');
			// NUL makes PostgreSQL fail; other control / line-separator characters only cause trouble.
			if (!allowed && (Character.isISOControl(c) || c == ' ' || c == ' ')) {
				throw ApiException.validation(field, Messages.INVALID_CHARACTERS);
			}
		}
		if (value.codePointCount(0, value.length()) > max) {
			throw ApiException.validation(field, Messages.tooLong(max));
		}
		return value;
	}

	/** A list from the request: null → empty, more than {@code max} items → 400 on {@code field}. */
	public static <T> java.util.List<T> list(java.util.List<T> value, String field, int max) {
		if (value == null) {
			return java.util.List.of();
		}
		if (value.size() > max) {
			throw ApiException.validation(field, Messages.TOO_MANY_ITEMS);
		}
		return value;
	}

	/** Passwords longer than bcrypt's 72 bytes are refused instead of silently cut. */
	public static boolean passwordTooLong(String password) {
		return password != null
				&& password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

	/** Trimmed text, or null when blank. */
	public static String blankToNull(String value) {
		String t = text(value);
		return t.isEmpty() ? null : t;
	}

	public static String normalizeEmail(String value) {
		return text(value).toLowerCase(Locale.ROOT);
	}

	/** Digits only; Cambodia's +855 / 855 prefix becomes a leading 0. */
	public static String normalizePhone(String value) {
		String digits = text(value).replaceAll("\\D", "");
		if (digits.startsWith("855") && digits.length() >= 11) {
			digits = "0" + digits.substring(3);
		}
		return digits;
	}

	public static boolean isEmail(String value) {
		return value.length() <= MAX_EMAIL && EMAIL.matcher(value).matches();
	}

	public static boolean isPhone(String normalizedPhone) {
		return PHONE.matcher(normalizedPhone).matches();
	}

	public static boolean isHexColor(String value) {
		return value != null && HEX.matcher(value).matches();
	}

	/** {@code /uploads/{file}} → file name, else null. */
	public static String uploadFileName(String url) {
		var m = UPLOAD_PATH.matcher(text(url));
		return m.matches() ? m.group(1) : null;
	}

	/** An https image/link URL that is safe to put in an {@code src}/{@code href} attribute. */
	public static boolean isRemoteUrl(String url) {
		String value = text(url);
		if (value.length() > MAX_URL || !REMOTE_URL.matcher(value).matches()) {
			return false;
		}
		try {
			var uri = new java.net.URI(value);
			return uri.getHost() != null && !uri.getHost().isBlank();
		}
		catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	/** "Login identifier": contains "@" → email, otherwise phone (§5.2). */
	public static String normalizeIdentifier(String identifier) {
		String value = text(identifier);
		return value.contains("@") ? normalizeEmail(value) : normalizePhone(value);
	}

	/**
	 * Optional email + phone of an account: at least one, both valid when given.
	 * Returns the normalised pair.
	 */
	public static Contact contact(String email, String phone, String missingMessage) {
		String e = blankToNull(email);
		String p = blankToNull(phone);
		if (e == null && p == null) {
			throw ApiException.validation("email", missingMessage);
		}
		if (e != null) {
			e = normalizeEmail(e);
			if (!isEmail(e)) {
				throw ApiException.validation("email", Messages.EMAIL_INVALID);
			}
		}
		if (p != null) {
			p = normalizePhone(p);
			if (!isPhone(p)) {
				throw ApiException.validation("phone", Messages.PHONE_INVALID);
			}
		}
		return new Contact(e, p);
	}

	/** Password + confirmation for new accounts (≥ 8 chars, equal). */
	public static void newPassword(String password, String confirm) {
		if (password == null || password.length() < 8) {
			throw ApiException.validation("password", Messages.PASSWORD_TOO_SHORT);
		}
		if (passwordTooLong(password)) {
			throw ApiException.validation("password", Messages.PASSWORD_TOO_LONG);
		}
		if (!password.equals(confirm)) {
			throw ApiException.validation("confirmPassword", Messages.PASSWORDS_DIFFER);
		}
	}

	public record Contact(String email, String phone) {
	}
}
